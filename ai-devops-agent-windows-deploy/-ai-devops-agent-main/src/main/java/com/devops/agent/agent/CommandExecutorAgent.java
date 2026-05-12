package com.devops.agent.agent;

import com.devops.agent.entity.Agent;
import com.devops.agent.entity.Server;
import com.devops.agent.service.LlmService;
import com.devops.agent.service.LlmMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CommandExecutorAgent implements BaseAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public CommandExecutorAgent(LlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getAgentType() {
        return "command_executor";
    }

    @Override
    public AgentPlan plan(String userInput, Server server) {
        String systemPrompt = "你是一个 Linux 运维专家。根据用户的自然语言指令，生成对应的 Linux 命令。\n" +
                "你每次返回一个 JSON 对象，格式如下：\n" +
                "{\"command\": \"具体的shell命令\", \"explanation\": \"简短说明为什么要执行这个命令\"}\n" +
                "只返回 JSON，不要其他内容。\n" +
                "目标服务器信息: " + server.getName() + " (" + server.getHost() + "), OS: " + server.getOsType();

        String response = llmService.chat(systemPrompt, "用户任务: " + userInput);
        return parseCommandResponse(response);
    }

    @Override
    public AgentPlan nextStep(String previousOutput, String previousReasoning) {
        String systemPrompt = "你是一个 Linux 运维专家。根据上一步命令的输出结果，决定下一步操作。\n" +
                "如果任务已经完成，返回: {\"command\": \"__DONE__\", \"explanation\": \"任务完成\"}\n" +
                "如果需要继续操作，返回: {\"command\": \"下一步的shell命令\", \"explanation\": \"为什么要执行这个命令\"}\n" +
                "只返回 JSON，不要其他内容。";

        String userMessage = "上一步命令输出:\n" + previousOutput + "\n\n" +
                "之前的分析: " + previousReasoning + "\n\n" +
                "请决定下一步操作。";

        String response = llmService.chat(systemPrompt, userMessage);
        return parseCommandResponse(response);
    }

    @Override
    public String summarize(List<String> commandOutputs) {
        String systemPrompt = "你是一个运维助手。根据以下命令执行结果，用简洁的中文总结操作结果。不超过 200 字。";
        StringBuilder sb = new StringBuilder("执行结果:\n");
        for (int i = 0; i < commandOutputs.size(); i++) {
            sb.append("第").append(i + 1).append("步: ").append(commandOutputs.get(i)).append("\n");
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
            log.warn("Failed to parse LLM response as JSON, returning raw response as command", e);
            return AgentPlan.single(response.trim(), "LLM direct response");
        }
    }

    private String extractJson(String text) {
        // handle markdown code block wrapping
        int start = text.indexOf("```");
        if (start >= 0) {
            int jsonStart = text.indexOf("{", start);
            int jsonEnd = text.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                return text.substring(jsonStart, jsonEnd + 1);
            }
        }
        // try direct parse
        int first = text.indexOf("{");
        int last = text.lastIndexOf("}");
        if (first >= 0 && last > first) {
            return text.substring(first, last + 1);
        }
        return text;
    }
}
