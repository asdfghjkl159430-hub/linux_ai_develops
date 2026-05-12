package com.devops.agent.service;

import com.devops.agent.agent.MultiAgentCoordinator;
import com.devops.agent.agent.TaskOrchestrator;
import com.devops.agent.common.PageRequest;
import com.devops.agent.common.PageResult;
import com.devops.agent.entity.Server;
import com.devops.agent.entity.Task;
import com.devops.agent.entity.TaskLog;
import com.devops.agent.mapper.AgentMapper;
import com.devops.agent.mapper.ServerMapper;
import com.devops.agent.mapper.TaskLogMapper;
import com.devops.agent.mapper.TaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final ServerMapper serverMapper;
    private final AgentMapper agentMapper;
    private final TaskOrchestrator orchestrator;
    private final SshService sshService;
    private MultiAgentCoordinator multiAgentCoordinator;
    private AsyncTaskService asyncTaskService;

    public TaskService(TaskMapper taskMapper,
                       TaskLogMapper taskLogMapper,
                       ServerMapper serverMapper,
                       AgentMapper agentMapper,
                       TaskOrchestrator orchestrator,
                       SshService sshService) {
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.serverMapper = serverMapper;
        this.agentMapper = agentMapper;
        this.orchestrator = orchestrator;
        this.sshService = sshService;
    }

    @Autowired(required = false)
    public void setMultiAgentCoordinator(MultiAgentCoordinator multiAgentCoordinator) {
        this.multiAgentCoordinator = multiAgentCoordinator;
    }

    @Autowired(required = false)
    public void setAsyncTaskService(AsyncTaskService asyncTaskService) {
        this.asyncTaskService = asyncTaskService;
    }

    /**
     * Submit a new task and execute it asynchronously.
     */
    @Transactional
    public Task submitTask(String userInput, Long serverId, String agentType) {
        Server server = serverMapper.selectById(serverId);
        if (server == null) {
            throw new IllegalArgumentException("server not found: " + serverId);
        }
        if (server.getStatus() != 1) {
            throw new IllegalArgumentException("server is disabled: " + server.getName());
        }

        Task task = new Task();
        task.setUserInput(userInput);
        task.setServerId(serverId);
        task.setAgentType(agentType != null ? agentType : "command_executor");
        task.setStatus(1); // running
        task.setStartedAt(LocalDateTime.now());
        taskMapper.insert(task);

        return task;
    }

    /**
     * Execute a task synchronously (for immediate feedback).
     */
    public Task executeTaskSync(String userInput, Long serverId, String agentType) {
        Task task = submitTask(userInput, serverId, agentType);

        try {
            Server server = serverMapper.selectById(serverId);
            String summary = orchestrator.executeTask(task.getId(), task.getAgentType(), server);
            task.setResultSummary(summary);
            task.setStatus(2); // success
        } catch (Exception e) {
            log.error("Task {} execution failed", task.getId(), e);
            task.setErrorMessage(e.getMessage());
            task.setStatus(3); // failed
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateResult(task);
        }

        return taskMapper.selectById(task.getId());
    }

    /**
     * Get task with its execution logs.
     */
    public TaskDetail getTaskDetail(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task not found: " + taskId);
        }
        List<TaskLog> logs = taskLogMapper.selectByTaskId(taskId);
        return new TaskDetail(task, logs);
    }

    /**
     * Paginated task history query.
     */
    public PageResult<Task> listTasks(Integer status, Long serverId, String agentType, PageRequest pageRequest) {
        List<Task> tasks = taskMapper.selectByCondition(status, serverId, agentType,
                pageRequest.getOffset(), pageRequest.getSize());
        long total = taskMapper.countByCondition(status, serverId, agentType);
        return new PageResult<>(tasks, total, pageRequest.getPage(), pageRequest.getSize());
    }

    /**
     * Get all registered agents.
     */
    public List<com.devops.agent.entity.Agent> listAgents() {
        return agentMapper.selectAll();
    }

    /**
     * Quick diagnostic: run a battery of system checks on a server.
     */
    public Task runDiagnostics(Long serverId) {
        String[] diagCommands = {
                "uptime",
                "free -h",
                "df -h",
                "top -bn1 | head -20",
                "docker ps --format 'table {{.Names}}\\t{{.Status}}' 2>/dev/null || echo 'Docker not available'",
                "systemctl list-units --failed --no-pager 2>/dev/null | head -10 || echo 'systemctl unavailable'"
        };

        Task task = new Task();
        task.setUserInput("system diagnostics - check memory, CPU, disk, Docker, system logs");
        task.setServerId(serverId);
        task.setIntent("diagnostics");
        task.setAgentType("log_analyzer");
        task.setStatus(1);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.insert(task);

        try {
            Server server = serverMapper.selectById(serverId);
            List<SshResult> results = sshService.executeCommands(server, List.of(diagCommands));

            List<String> outputs = new java.util.ArrayList<>();
            for (int i = 0; i < diagCommands.length; i++) {
                SshResult result = results.get(i);
                TaskLog taskLog = new TaskLog();
                taskLog.setTaskId(task.getId());
                taskLog.setStep(i + 1);
                taskLog.setCommand(diagCommands[i]);
                taskLog.setOutput(result.output());
                taskLog.setExitCode(result.exitCode());
                taskLog.setStatus(result.isSuccess() ? 1 : 2);
                taskLogMapper.insert(taskLog);
                outputs.add("[" + diagCommands[i] + "]\n" + result.output());
            }

            task.setResultSummary(String.join("\n", outputs));
            task.setStatus(2);
        } catch (Exception e) {
            task.setErrorMessage(e.getMessage());
            task.setStatus(3);
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateResult(task);
        }

        return taskMapper.selectById(task.getId());
    }

    /**
     * Execute a multi-agent workflow task asynchronously.
     */
    @Async("taskExecutor")
    @Transactional
    public void executeMultiAgentWorkflowAsync(Long taskId, Long serverId) {
        Task task = taskMapper.selectById(taskId);
        Server server = serverMapper.selectById(serverId);
        if (task == null) {
            log.error("Multi-agent workflow: task not found: {}", taskId);
            return;
        }
        if (server == null) {
            log.error("Multi-agent workflow: server not found: {}", serverId);
            task.setErrorMessage("server not found: " + serverId);
            task.setStatus(3);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateResult(task);
            return;
        }

        try {
            task.setStatus(1); // running
            task.setStartedAt(LocalDateTime.now());
            taskMapper.updateStatus(taskId, 1);

            if (multiAgentCoordinator != null) {
                String summary = multiAgentCoordinator.executeMultiAgentTask(taskId, server,
                        new AsyncTaskService.TaskContext());
                task.setResultSummary(summary);
                task.setStatus(2); // success
            } else {
                // Fallback to single agent
                String summary = orchestrator.executeTask(taskId, "command_executor", server);
                task.setResultSummary(summary);
                task.setStatus(2);
            }
        } catch (Exception e) {
            log.error("Multi-agent task {} execution failed", taskId, e);
            task.setErrorMessage(e.getMessage());
            task.setStatus(3); // failed
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateResult(task);
        }
    }


    public record TaskDetail(Task task, List<TaskLog> logs) {}
}
