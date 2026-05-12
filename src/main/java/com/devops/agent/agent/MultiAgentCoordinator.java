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
import com.devops.agent.service.LlmService;
import com.devops.agent.service.SshResult;
import com.devops.agent.service.SshService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Multi-Agent Coordinator for complex workflows.
 * Orchestrates multiple agents to work together on complex tasks.
 */
@Slf4j
@Service
public class MultiAgentCoordinator {

    private final Map<String, BaseAgent> agentRegistry = new HashMap<>();
    private final LlmService llmService;
    private final SshService sshService;
    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final AgentMapper agentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CommandSecurityService securityService;

    public MultiAgentCoordinator(List<BaseAgent> agents,
                                 LlmService llmService,
                                 SshService sshService,
                                 TaskMapper taskMapper,
                                 TaskLogMapper taskLogMapper,
                                 AgentMapper agentMapper) {
        this.llmService = llmService;
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
     * Workflow step definition.
     */
    public record WorkflowStep(
            String agentType,
            String task,
            String description,
            List<String> dependencies
    ) {}

    /**
     * Workflow plan from LLM.
     */
    public record WorkflowPlan(
            List<WorkflowStep> steps,
            String reasoning
    ) {}

    /**
     * Execute a complex task using multiple agents.
     */
    public String executeMultiAgentTask(Long taskId, Server server, AsyncTaskService.TaskContext context) {
        Task task = taskMapper.selectById(taskId);
        
        // Step 1: Generate workflow plan using LLM
        WorkflowPlan plan = generateWorkflowPlan(task.getUserInput(), server);
        log.info("Generated workflow plan with {} steps for task {}", plan.steps().size(), taskId);

        List<String> allOutputs = new ArrayList<>();
        Map<String, String> stepOutputs = new HashMap<>();
        int stepNumber = 0;

        // Step 2: Execute workflow steps in dependency order
        Set<String> completedSteps = new HashSet<>();
        
        while (completedSteps.size() < plan.steps().size()) {
            // Find next executable step
            WorkflowStep nextStep = findNextStep(plan.steps(), completedSteps, stepOutputs);
            
            if (nextStep == null) {
                log.warn("No more executable steps, breaking workflow");
                break;
            }

            // Check cancellation
            if (context != null && context.isCancelled()) {
                log.info("Multi-agent task {} cancelled", taskId);
                break;
            }

            stepNumber++;
            if (context != null) {
                context.setCurrentStep(stepNumber);
                context.setCurrentCommand("[Multi-Agent] " + nextStep.agentType() + ": " + nextStep.description());
            }

            // Execute step
            String stepOutput = executeWorkflowStep(taskId, stepNumber, nextStep, server, stepOutputs, context);
            stepOutputs.put(nextStep.agentType() + "_" + stepNumber, stepOutput);
            allOutputs.add("=== " + nextStep.description() + " ===\n" + stepOutput);
            completedSteps.add(nextStep.agentType() + "_" + stepNumber);

            // Safety limit
            if (stepNumber >= 30) {
                log.warn("Multi-agent task {} reached max steps (30)", taskId);
                break;
            }
        }

        // Step 3: Generate final summary
        return generateFinalSummary(task.getUserInput(), allOutputs);
    }

    /**
     * Generate a workflow plan using LLM.
     */
    private WorkflowPlan generateWorkflowPlan(String userInput, Server server) {
        List<Agent> agents = agentMapper.selectAll();
        
        StringBuilder agentList = new StringBuilder();
        for (Agent agent : agents) {
            agentList.append("- ").append(agent.getAgentType()).append(": ").append(agent.getDescription()).append("\n");
        }

        String systemPrompt = """
                你是一个多Agent协作编排专家。根据用户的复杂任务需求，设计一个多Agent协作工作流。
                
                可用的Agent类型：
                %s
                
                目标服务器: %s (%s)
                
                请返回JSON格式的工作流计划：
                {
                  "steps": [
                    {
                      "agent_type": "agent类型",
                      "task": "该agent需要完成的具体任务描述",
                      "description": "步骤说明",
                      "dependencies": ["依赖的其他步骤的agent_type，如果没有则为空数组"]
                    }
                  ],
                  "reasoning": "为什么这样安排工作流"
                }
                
                规则：
                1. 每个步骤必须指定一个agent_type
                2. dependencies表示该步骤依赖哪些其他agent的执行结果
                3. 合理安排步骤顺序，确保依赖关系正确
                4. 对于简单任务，可能只需要一个步骤
                5. 只返回JSON，不要其他内容
                """.formatted(agentList.toString(), server.getName(), server.getHost());

        try {
            String response = llmService.chat(systemPrompt, "用户任务: " + userInput);
            return parseWorkflowPlan(response);
        } catch (Exception e) {
            log.error("Failed to generate workflow plan", e);
            // Fallback to single agent
            return new WorkflowPlan(
                    List.of(new WorkflowStep("command_executor", userInput, "Execute user task", List.of())),
                    "Fallback to single agent due to planning error"
            );
        }
    }

    private WorkflowPlan parseWorkflowPlan(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            
            List<WorkflowStep> steps = new ArrayList<>();
            JsonNode stepsNode = node.get("steps");
            if (stepsNode != null && stepsNode.isArray()) {
                for (JsonNode stepNode : stepsNode) {
                    String agentType = stepNode.has("agent_type") ? stepNode.get("agent_type").asText() : "command_executor";
                    String task = stepNode.has("task") ? stepNode.get("task").asText() : "";
                    String description = stepNode.has("description") ? stepNode.get("description").asText() : "";
                    List<String> dependencies = new ArrayList<>();
                    if (stepNode.has("dependencies") && stepNode.get("dependencies").isArray()) {
                        for (JsonNode dep : stepNode.get("dependencies")) {
                            dependencies.add(dep.asText());
                        }
                    }
                    steps.add(new WorkflowStep(agentType, task, description, dependencies));
                }
            }
            
            String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
            return new WorkflowPlan(steps, reasoning);
        } catch (Exception e) {
            log.warn("Failed to parse workflow plan: {}", response, e);
            return new WorkflowPlan(
                    List.of(new WorkflowStep("command_executor", "", "Default execution", List.of())),
                    "Parse error fallback"
            );
        }
    }

    /**
     * Find the next step that can be executed (all dependencies satisfied).
     */
    private WorkflowStep findNextStep(List<WorkflowStep> steps, Set<String> completedSteps, Map<String, String> stepOutputs) {
        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            String stepKey = step.agentType() + "_" + (i + 1);
            
            if (completedSteps.contains(stepKey)) {
                continue;
            }
            
            // Check if all dependencies are satisfied
            boolean allDepsSatisfied = true;
            for (String dep : step.dependencies()) {
                boolean found = completedSteps.stream().anyMatch(k -> k.startsWith(dep + "_"));
                if (!found) {
                    allDepsSatisfied = false;
                    break;
                }
            }
            
            if (allDepsSatisfied) {
                return step;
            }
        }
        return null;
    }

    /**
     * Execute a single workflow step.
     */
    private String executeWorkflowStep(Long taskId, int stepNumber, WorkflowStep step, 
                                       Server server, Map<String, String> previousOutputs,
                                       AsyncTaskService.TaskContext context) {
        BaseAgent agent = agentRegistry.get(step.agentType());
        if (agent == null) {
            log.warn("Unknown agent type: {}", step.agentType());
            return "Error: Unknown agent type " + step.agentType();
        }

        // Build context from previous outputs
        StringBuilder contextBuilder = new StringBuilder();
        if (!previousOutputs.isEmpty()) {
            contextBuilder.append("之前的执行结果:\n");
            previousOutputs.forEach((key, value) -> {
                if (step.dependencies().stream().anyMatch(key::startsWith)) {
                    contextBuilder.append("[").append(key).append("]\n").append(value).append("\n\n");
                }
            });
        }

        String enhancedTask = contextBuilder.length() > 0 
                ? contextBuilder + "\n当前任务: " + step.task()
                : step.task();

        // Execute agent
        AgentPlan plan = agent.plan(enhancedTask, server);
        List<String> outputs = new ArrayList<>();
        int subStep = 0;

        while (plan != null && subStep < 10) {
            if (context != null && context.isCancelled()) {
                break;
            }
            subStep++;

            if (plan.getCommand() != null && !plan.getCommand().isEmpty()) {
                String cmd = plan.getCommand();
                
                // Security check
                if (securityService != null) {
                    CommandSecurityService.ValidationResult securityResult = securityService.validateCommand(cmd);
                    if (!securityResult.permitted()) {
                        log.warn("Command blocked: {}", cmd);
                        outputs.add("[BLOCKED] " + securityResult.denialReason());
                        plan = agent.nextStep("[BLOCKED] " + securityResult.denialReason(), plan.getExplanation());
                        continue;
                    }
                }
                
                SshResult result = sshService.executeCommand(server, cmd);
                
                TaskLog taskLog = new TaskLog();
                taskLog.setTaskId(taskId);
                taskLog.setStep(stepNumber * 100 + subStep);
                taskLog.setCommand(cmd);
                taskLog.setOutput(result.output());
                taskLog.setExitCode(result.exitCode());
                taskLog.setAiReasoning("[" + step.agentType() + "] " + plan.getExplanation());
                taskLog.setStatus(result.isSuccess() ? 1 : 2);
                taskLogMapper.insert(taskLog);
                
                outputs.add(result.output());
                
                String agentInput = result.output() + (result.error() != null ? "\n[stderr] " + result.error() : "");
                plan = agent.nextStep(agentInput, plan.getExplanation());
            } else {
                break;
            }
        }

        return String.join("\n", outputs);
    }

    /**
     * Generate final summary using LLM.
     */
    private String generateFinalSummary(String originalTask, List<String> outputs) {
        String systemPrompt = "你是一个运维专家。根据以下多Agent协作执行的结果，总结整个任务的完成情况。\n" +
                "请简要说明：1) 执行了哪些操作 2) 结果如何 3) 是否需要注意的事项。\n" +
                "不超过300字。";

        StringBuilder sb = new StringBuilder("原始任务: ").append(originalTask).append("\n\n执行结果:\n");
        for (String output : outputs) {
            sb.append(output).append("\n");
        }

        return llmService.chat(systemPrompt, sb.toString());
    }

    private String extractJson(String text) {
        int start = text.indexOf("```");
        if (start >= 0) {
            int jsonStart = text.indexOf("{", start);
            int jsonEnd = text.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                return text.substring(jsonStart, jsonEnd + 1);
            }
        }
        int first = text.indexOf("{");
        int last = text.lastIndexOf("}");
        if (first >= 0 && last > first) {
            return text.substring(first, last + 1);
        }
        return text;
    }
}
