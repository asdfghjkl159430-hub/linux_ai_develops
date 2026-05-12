package com.devops.agent.agent;

import com.devops.agent.entity.Server;
import com.devops.agent.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DeployAgent implements BaseAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public DeployAgent(LlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getAgentType() {
        return "deploy_agent";
    }

    @Override
    public AgentPlan plan(String userInput, Server server) {
        String systemPrompt = "你是一个部署专家。根据用户的部署需求，生成部署步骤。\n" +
                "返回 JSON: {\"commands\": [\"命令1\", \"命令2\"], \"explanation\": \"部署说明\"}\n" +
                "只返回 JSON。";

        String response = llmService.chat(systemPrompt,
                "部署需求: " + userInput + "\n目标服务器: " + server.getName() + " (" + server.getHost() + ")");
        return parseDeployResponse(response);
    }

    @Override
    public AgentPlan nextStep(String previousOutput, String previousReasoning) {
        String systemPrompt = "你是一个部署专家。根据上一步部署的输出，决定下一步操作。\n" +
                "如果部署完成: {\"command\": \"__DONE__\", \"explanation\": \"部署完成\"}\n" +
                "否则: {\"command\": \"下一步命令\", \"explanation\": \"为什么\"}\n" +
                "只返回 JSON。";

        String response = llmService.chat(systemPrompt,
                "部署输出:\n" + previousOutput + "\n\n之前分析: " + previousReasoning);
        return parseCommandResponse(response);
    }

    @Override
    public String summarize(List<String> commandOutputs) {
        String systemPrompt = "你是部署专家。根据以下部署步骤的输出，总结部署结果。\n" +
                "简要说明部署是否成功，以及需要注意的事项。不超过 200 字。";

        StringBuilder sb = new StringBuilder("部署步骤输出:\n");
        for (int i = 0; i < commandOutputs.size(); i++) {
            sb.append("步骤").append(i + 1).append(": ").append(commandOutputs.get(i)).append("\n\n");
        }
        return llmService.chat(systemPrompt, sb.toString());
    }

    private AgentPlan parseDeployResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            if (node.has("commands") && node.get("commands").isArray()) {
                List<String> cmds = new ArrayList<>();
                node.get("commands").forEach(c -> cmds.add(c.asText()));
                String explanation = node.has("explanation") ? node.get("explanation").asText() : "";
                AgentPlan plan = AgentPlan.multi(cmds);
                plan.setExplanation(explanation);
                return plan;
            }

            // fallback to single command format
            return parseCommandResponse(response);
        } catch (Exception e) {
            log.warn("Failed to parse deploy response", e);
            return AgentPlan.single(response.trim(), "direct response");
        }
    }

    private AgentPlan parseCommandResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            String command = node.has("command") ? node.get("command").asText() : "";
            String explanation = node.has("explanation") ? node.get("explanation").asText() : "";
            if ("__DONE__".equals(command)) {
                return null;
            }
            return AgentPlan.single(command, explanation);
        } catch (Exception e) {
            return AgentPlan.single(response.trim(), "direct response");
        }
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
