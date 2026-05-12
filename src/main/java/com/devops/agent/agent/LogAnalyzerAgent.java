package com.devops.agent.agent;

import com.devops.agent.entity.Server;
import com.devops.agent.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class LogAnalyzerAgent implements BaseAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public LogAnalyzerAgent(LlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getAgentType() {
        return "log_analyzer";
    }

    @Override
    public AgentPlan plan(String userInput, Server server) {
        String systemPrompt = "你是一个日志分析专家。根据用户的自然语言描述，生成用于诊断的 Linux 命令。\n" +
                "返回 JSON: {\"command\": \"诊断命令\", \"explanation\": \"为什么执行此命令\"}\n" +
                "只返回 JSON。";

        String response = llmService.chat(systemPrompt,
                "用户描述: " + userInput + "\n目标服务器: " + server.getName() + " (" + server.getHost() + ")");
        return parseCommandResponse(response);
    }

    @Override
    public AgentPlan nextStep(String previousOutput, String previousReasoning) {
        String systemPrompt = "你是一个日志分析专家。分析下面的诊断输出，判断是否需要进一步检查。\n" +
                "如果需要继续诊断: {\"command\": \"下一步命令\", \"explanation\": \"为什么\"}\n" +
                "如果分析完成: {\"command\": \"__DONE__\", \"explanation\": \"分析完成\"}\n" +
                "只返回 JSON。";

        String response = llmService.chat(systemPrompt,
                "诊断输出:\n" + previousOutput + "\n\n之前分析: " + previousReasoning);
        return parseCommandResponse(response);
    }

    @Override
    public String summarize(List<String> commandOutputs) {
        String systemPrompt = "你是一个日志分析专家。分析以下系统诊断输出，找出潜在问题并给出建议。\n" +
                "请以以下 JSON 格式返回:\n" +
                "{\"status\": \"normal|warning|error\", \"issues\": [\"问题\"], \"suggestions\": [\"建议\"], \"summary\": \"整体情况总结\"}\n" +
                "只返回 JSON。";

        StringBuilder sb = new StringBuilder("诊断输出:\n");
        for (int i = 0; i < commandOutputs.size(); i++) {
            sb.append("--- 第").append(i + 1).append("步 ---\n").append(commandOutputs.get(i)).append("\n\n");
        }

        return llmService.chat(systemPrompt, sb.toString());
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
            log.warn("Failed to parse LLM response, returning raw response", e);
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
