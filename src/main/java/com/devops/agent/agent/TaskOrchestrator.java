package com.devops.agent.agent;

import com.devops.agent.entity.Agent;
import com.devops.agent.entity.Server;
import com.devops.agent.entity.Task;
import com.devops.agent.entity.TaskLog;
import com.devops.agent.mapper.AgentMapper;
import com.devops.agent.mapper.TaskLogMapper;
import com.devops.agent.mapper.TaskMapper;
import com.devops.agent.service.AsyncTaskService;
import com.devops.agent.service.CommandSecurityService;
import com.devops.agent.service.SshResult;
import com.devops.agent.service.SshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskOrchestrator {

    private final Map<String, BaseAgent> agentRegistry = new HashMap<>();
    private final SshService sshService;
    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final AgentMapper agentMapper;
    private CommandSecurityService securityService;

    public TaskOrchestrator(List<BaseAgent> agents,
                            SshService sshService,
                            TaskMapper taskMapper,
                            TaskLogMapper taskLogMapper,
                            AgentMapper agentMapper) {
        this.sshService = sshService;
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.agentMapper = agentMapper;
        for (BaseAgent agent : agents) {
            agentRegistry.put(agent.getAgentType(), agent);
        }
    }

    @Autowired(required = false)
    public void setSecurityService(CommandSecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * Execute a task: dispatch to the appropriate agent, run commands via SSH,
     * log each step, and return a summary.
     */
    public String executeTask(Long taskId, String agentType, Server server) {
        return executeTaskWithContext(taskId, agentType, server, null);
    }

    /**
     * Execute a task with cancellation support via TaskContext.
     */
    public String executeTaskWithContext(Long taskId, String agentType, Server server, AsyncTaskService.TaskContext context) {
        Task task = taskMapper.selectById(taskId);
        BaseAgent agent = agentRegistry.get(agentType);

        if (agent == null) {
            return "unknown agent type: " + agentType;
        }

        Agent agentConfig = agentMapper.selectByType(agentType);
        String explanation = agentConfig != null ? agentConfig.getDescription() : "";

        // Step 1: initial plan
        AgentPlan plan = agent.plan(task.getUserInput(), server);
        List<String> outputs = new ArrayList<>();
        int step = 0;

        while (plan != null) {
            // Check for cancellation
            if (context != null && context.isCancelled()) {
                log.info("Task {} cancelled at step {}", taskId, step);
                break;
            }

            step++;

            // Update context for progress tracking
            if (context != null) {
                context.setCurrentStep(step);
            }

            // handle multi-command plan
            if (plan.getMultiCommands() != null && !plan.getMultiCommands().isEmpty()) {
                List<String> commands = plan.getMultiCommands();
                List<SshResult> results = sshService.executeCommands(server, commands);
                for (int i = 0; i < results.size(); i++) {
                    String cmd = commands.get(i);
                    
                    // Security check
                    CommandSecurityService.ValidationResult securityResult = validateCommand(cmd);
                    if (!securityResult.permitted()) {
                        log.warn("Command blocked by security policy: {}", cmd);
                        SshResult blockedResult = SshResult.failure(-1, "Command blocked: " + securityResult.denialReason());
                        TaskLog taskLog = buildTaskLog(taskId, step + i, cmd, blockedResult, "Security blocked");
                        taskLogMapper.insert(taskLog);
                        outputs.add("[BLOCKED] " + securityResult.denialReason());
                        continue;
                    }
                    
                    if (context != null) {
                        context.setCurrentCommand(cmd);
                    }
                    SshResult result = sshService.executeCommand(server, cmd);
                    TaskLog taskLog = buildTaskLog(taskId, step + i, cmd,
                            result, plan.getExplanation() + (securityResult.hasWarning() ? " [Warning: " + securityResult.warning() + "]" : ""));
                    taskLogMapper.insert(taskLog);
                    outputs.add(result.output());
                }
            } else if (plan.getCommand() != null && !plan.getCommand().isEmpty()) {
                String cmd = plan.getCommand();
                
                // Security check
                CommandSecurityService.ValidationResult securityResult = validateCommand(cmd);
                if (!securityResult.permitted()) {
                    log.warn("Command blocked by security policy: {}", cmd);
                    SshResult blockedResult = SshResult.failure(-1, "Command blocked: " + securityResult.denialReason());
                    TaskLog taskLog = buildTaskLog(taskId, step, cmd, blockedResult, "Security blocked");
                    taskLogMapper.insert(taskLog);
                    outputs.add("[BLOCKED] " + securityResult.denialReason());
                    plan = agent.nextStep("[BLOCKED] " + securityResult.denialReason(), plan.getExplanation());
                    continue;
                }
                
                if (context != null) {
                    context.setCurrentCommand(cmd);
                }
                SshResult result = sshService.executeCommand(server, cmd);
                TaskLog taskLog = buildTaskLog(taskId, step, cmd,
                        result, plan.getExplanation() + (securityResult.hasWarning() ? " [Warning: " + securityResult.warning() + "]" : ""));
                taskLogMapper.insert(taskLog);

                if (!result.isSuccess()) {
                    log.warn("Step {} command failed: {} -> {}", step, plan.getCommand(), result.error());
                }
                outputs.add(result.output());

                // Check cancellation before next step
                if (context != null && context.isCancelled()) {
                    break;
                }

                // Step: let agent decide next action based on output
                String agentInput = result.output() + (result.error() != null ? "\n[stderr] " + result.error() : "");
                plan = agent.nextStep(agentInput, plan.getExplanation());
                continue;
            }

            if (plan == null || (plan.getMultiCommands() == null)) {
                plan = agent.nextStep(outputs.isEmpty() ? "no output" : outputs.get(outputs.size() - 1),
                        plan != null ? plan.getExplanation() : "");
            }

            // safety limit
            if (step >= 20) {
                log.warn("Task {} reached max steps (20), stopping", taskId);
                break;
            }
        }

        return agent.summarize(outputs);
    }

    private TaskLog buildTaskLog(Long taskId, int step, String command, SshResult result, String reasoning) {
        TaskLog taskLog = new TaskLog();
        taskLog.setTaskId(taskId);
        taskLog.setStep(step);
        taskLog.setCommand(command);
        taskLog.setOutput(truncate(result.isSuccess() ? result.output() : (result.output() + (result.error() != null && !result.error().isEmpty() ? "\n[错误] " + result.error() : "")), 50000));
        taskLog.setExitCode(result.exitCode());
        taskLog.setAiReasoning(reasoning);
        taskLog.setStatus(result.isSuccess() ? 1 : 2);
        return taskLog;
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "\n... [truncated]";
    }

    /**
     * Validate command using security service.
     */
    private CommandSecurityService.ValidationResult validateCommand(String command) {
        if (securityService != null) {
            return securityService.validateCommand(command);
        }
        return CommandSecurityService.ValidationResult.allowed();
    }
}
