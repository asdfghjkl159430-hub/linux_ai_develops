package com.devops.agent.service;

import com.devops.agent.agent.TaskOrchestrator;
import com.devops.agent.dto.TaskProgressMessage;
import com.devops.agent.entity.Server;
import com.devops.agent.entity.Task;
import com.devops.agent.entity.TaskLog;
import com.devops.agent.mapper.ServerMapper;
import com.devops.agent.mapper.TaskLogMapper;
import com.devops.agent.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async task execution service.
 * Supports task cancellation and progress tracking.
 */
@Slf4j
@Service
public class AsyncTaskService {

    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final ServerMapper serverMapper;
    private final TaskOrchestrator orchestrator;
    private final SshService sshService;
    private TaskProgressBroadcaster progressBroadcaster;

    // Track running tasks for cancellation
    private final Map<Long, TaskContext> runningTasks = new ConcurrentHashMap<>();

    public AsyncTaskService(TaskMapper taskMapper,
                           TaskLogMapper taskLogMapper,
                           ServerMapper serverMapper,
                           TaskOrchestrator orchestrator,
                           SshService sshService) {
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.serverMapper = serverMapper;
        this.orchestrator = orchestrator;
        this.sshService = sshService;
    }

    @Autowired(required = false)
    public void setProgressBroadcaster(TaskProgressBroadcaster progressBroadcaster) {
        this.progressBroadcaster = progressBroadcaster;
    }

    public static class TaskContext {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Thread executionThread;
        private volatile int currentStep = 0;
        private volatile String currentCommand;

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            cancelled.set(true);
            if (executionThread != null) {
                executionThread.interrupt();
            }
        }

        public void setExecutionThread(Thread thread) {
            this.executionThread = thread;
        }

        public int getCurrentStep() {
            return currentStep;
        }

        public void setCurrentStep(int step) {
            this.currentStep = step;
        }

        public String getCurrentCommand() {
            return currentCommand;
        }

        public void setCurrentCommand(String command) {
            this.currentCommand = command;
        }
    }

    /**
     * Execute a task asynchronously.
     */
    @Async("taskExecutor")
    @Transactional
    public void executeTaskAsync(Long taskId, String agentType, Long serverId) {
        TaskContext context = new TaskContext();
        context.setExecutionThread(Thread.currentThread());
        runningTasks.put(taskId, context);

        Task task = taskMapper.selectById(taskId);
        Server server = serverMapper.selectById(serverId);

        try {
            task.setStatus(1); // running
            task.setStartedAt(LocalDateTime.now());
            taskMapper.updateStatus(taskId, 1);
            
            broadcastProgress(TaskProgressMessage.started(taskId, "Task started with agent: " + agentType));

            if (context.isCancelled()) {
                task.setStatus(4); // cancelled
                task.setErrorMessage("Task cancelled by user");
                taskMapper.updateResult(task);
                broadcastProgress(TaskProgressMessage.cancelled(taskId));
                return;
            }

            String summary = orchestrator.executeTaskWithContext(taskId, agentType, server, context);

            if (context.isCancelled()) {
                task.setStatus(4);
                task.setErrorMessage("Task cancelled by user");
                broadcastProgress(TaskProgressMessage.cancelled(taskId));
            } else {
                task.setResultSummary(summary);
                task.setStatus(2); // success
                broadcastProgress(TaskProgressMessage.completed(taskId, summary));
            }
        } catch (Exception e) {
            log.error("Task {} execution failed", taskId, e);
            task.setErrorMessage(e.getMessage());
            task.setStatus(3); // failed
            broadcastProgress(TaskProgressMessage.failed(taskId, e.getMessage()));
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateResult(task);
            runningTasks.remove(taskId);
        }
    }

    private void broadcastProgress(TaskProgressMessage message) {
        if (progressBroadcaster != null) {
            progressBroadcaster.broadcast(message);
        }
    }

    /**
     * Cancel a running task.
     */
    public boolean cancelTask(Long taskId) {
        TaskContext context = runningTasks.get(taskId);
        if (context != null) {
            context.cancel();
            log.info("Task {} cancellation requested", taskId);
            return true;
        }
        return false;
    }

    /**
     * Get task execution progress.
     */
    public TaskProgress getTaskProgress(Long taskId) {
        TaskContext context = runningTasks.get(taskId);
        if (context == null) {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                return null;
            }
            return new TaskProgress(task.getStatus(), 0, null, task.getFinishedAt() != null);
        }
        return new TaskProgress(1, context.getCurrentStep(), context.getCurrentCommand(), false);
    }

    /**
     * Check if a task is currently running.
     */
    public boolean isTaskRunning(Long taskId) {
        return runningTasks.containsKey(taskId);
    }

    /**
     * Get count of currently running tasks.
     */
    public int getRunningTasksCount() {
        return runningTasks.size();
    }

    public record TaskProgress(
            int status,
            int currentStep,
            String currentCommand,
            boolean completed
    ) {}
}
