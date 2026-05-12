package com.devops.agent.service;

import com.devops.agent.entity.Agent;
import com.devops.agent.mapper.AgentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Intent recognition service.
 * Uses LLM to analyze user input and determine the most appropriate agent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRecognizer {

    private final LlmService llmService;
    private final AgentMapper agentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 关键词 → agent_type（有序：先匹配更具体的 linux_skills / deploy，再匹配宽泛的 log）。
     */
    private static final java.util.Map<String, String> KEYWORD_MAPPINGS = new java.util.LinkedHashMap<>();

    static {
        // linux-agent-skills（runbook 巡检，无需 LLM）
        KEYWORD_MAPPINGS.put("runbook", "linux_skills");
        KEYWORD_MAPPINGS.put("技能包", "linux_skills");
        KEYWORD_MAPPINGS.put("巡检脚本", "linux_skills");
        KEYWORD_MAPPINGS.put("磁盘空间", "linux_skills");
        KEYWORD_MAPPINGS.put("磁盘巡检", "linux_skills");
        KEYWORD_MAPPINGS.put("网络端口", "linux_skills");
        KEYWORD_MAPPINGS.put("失败单元", "linux_skills");
        KEYWORD_MAPPINGS.put("systemd", "linux_skills");
        KEYWORD_MAPPINGS.put("iostat", "linux_skills");
        KEYWORD_MAPPINGS.put("挂载", "linux_skills");
        KEYWORD_MAPPINGS.put("发行版", "linux_skills");
        KEYWORD_MAPPINGS.put("内核版本", "linux_skills");
        // 部署 / 容器
        KEYWORD_MAPPINGS.put("deploy", "deploy_agent");
        KEYWORD_MAPPINGS.put("部署", "deploy_agent");
        KEYWORD_MAPPINGS.put("安装", "deploy_agent");
        KEYWORD_MAPPINGS.put("docker", "deploy_agent");
        KEYWORD_MAPPINGS.put("container", "deploy_agent");
        KEYWORD_MAPPINGS.put("容器", "deploy_agent");
        // 日志 / 诊断（LLM）
        KEYWORD_MAPPINGS.put("log", "log_analyzer");
        KEYWORD_MAPPINGS.put("日志", "log_analyzer");
        KEYWORD_MAPPINGS.put("诊断", "log_analyzer");
        KEYWORD_MAPPINGS.put("排查", "log_analyzer");
        KEYWORD_MAPPINGS.put("检查", "log_analyzer");
        KEYWORD_MAPPINGS.put("error", "log_analyzer");
        KEYWORD_MAPPINGS.put("错误", "log_analyzer");
        KEYWORD_MAPPINGS.put("问题", "log_analyzer");
        KEYWORD_MAPPINGS.put("analyze", "log_analyzer");
        KEYWORD_MAPPINGS.put("分析", "log_analyzer");
    }

    /**
     * Recognize the intent from user input and return the appropriate agent type.
     */
    public IntentResult recognizeIntent(String userInput) {
        // 优先：linux-agent-skills 意图（与 agent-router 关键词对齐）
        if (matchesLinuxSkillsRouterIntent(userInput)) {
            return new IntentResult("linux_skills", "Matched runbook/ops keywords (linux-agent-skills)", 0.88);
        }
        // 其次：通用关键词表
        String keywordMatch = matchByKeywords(userInput);
        if (keywordMatch != null) {
            return new IntentResult(keywordMatch, "Matched by keywords", 0.8);
        }

        // Use LLM for more sophisticated intent recognition
        return recognizeByLLM(userInput);
    }

    /**
     * Quick keyword-based matching.
     */
    private String matchByKeywords(String input) {
        String lowerInput = input.toLowerCase();
        for (java.util.Map.Entry<String, String> entry : KEYWORD_MAPPINGS.entrySet()) {
            if (lowerInput.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 与课设 {@code agent-router.sh} 中关键词大致一致，用于自动选中 {@code linux_skills} Agent。
     */
    private boolean matchesLinuxSkillsRouterIntent(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String q = input.toLowerCase();
        if (q.contains("日志") || q.contains("log") || q.contains("syslog") || q.contains("journal")
                || q.contains("审计") || q.contains("auth") || q.contains("分析日志")) {
            return true;
        }
        if (q.contains("网络") || q.contains("端口") || q.contains("socket") || q.contains("监听")
                || q.contains("路由") || q.contains("ping") || q.contains("连通") || q.contains("dns")
                || q.contains("防火墙") || q.contains("网卡") || q.contains("iptables") || q.contains("nft")
                || q.contains("ip route") || q.contains("丢包")) {
            return true;
        }
        if (q.contains("systemd") || q.contains("服务状态") || q.contains("失败单元") || q.contains("开机自启")
                || q.contains("定时器") || q.contains("timer") || q.contains("查看服务") || q.contains("服务列表")
                || q.contains("systemctl")) {
            return true;
        }
        if (q.contains("iostat") || q.contains("io延迟") || q.contains("await") || q.contains("lsblk")
                || q.contains("块设备") || q.contains("磁盘io") || q.contains("磁盘 io") || q.contains("读写延迟")
                || q.contains("磁盘性能") || (q.contains("sar") && q.contains("磁盘"))) {
            return true;
        }
        if (q.contains("磁盘") || q.contains("空间") || q.contains("df") || q.contains("inode")
                || q.contains("挂载") || q.contains("mount")) {
            if (!q.contains("磁盘io") && !q.contains("磁盘 io")) {
                return true;
            }
        }
        if (q.contains("发行") || q.contains("内核") || q.contains("版本") || q.contains("主机名")
                || q.contains("hostname") || q.contains("os-release") || q.contains("系统版本") || q.contains("系统信息")
                || q.contains("distro") || q.contains("uname") || q.contains("发行版")) {
            return true;
        }
        return q.contains("进程") || q.contains("负载") || q.contains("cpu") || q.contains("内存")
                || q.contains("vmstat") || q.contains("load") || q.contains("占用");
    }

    /**
     * Use LLM to recognize intent when keywords don't match.
     */
    private IntentResult recognizeByLLM(String userInput) {
        List<Agent> agents = agentMapper.selectAll();
        if (agents.isEmpty()) {
            return new IntentResult("command_executor", "No agents configured", 0.5);
        }

        StringBuilder agentDescriptions = new StringBuilder();
        for (Agent agent : agents) {
            agentDescriptions.append("- ")
                    .append(agent.getAgentType())
                    .append(": ")
                    .append(agent.getDescription())
                    .append("\n");
        }

        String systemPrompt = """
                你是一个意图识别专家。根据用户的输入，判断应该使用哪个 Agent 来处理。
                
                可用的 Agent 类型：
                %s
                
                请返回 JSON 格式：
                {"agent_type": "选中的agent类型", "confidence": 0.0-1.0的置信度, "reasoning": "简短说明为什么选择这个agent"}
                
                只返回 JSON，不要其他内容。
                """.formatted(agentDescriptions.toString());

        try {
            String response = llmService.chat(systemPrompt, "用户输入: " + userInput);
            return parseIntentResponse(response);
        } catch (Exception e) {
            log.warn("LLM intent recognition failed, using default agent", e);
            return new IntentResult("command_executor", "LLM failed, using default", 0.5);
        }
    }

    private IntentResult parseIntentResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            String agentType = node.has("agent_type") ? node.get("agent_type").asText() : "command_executor";
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.7;
            String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";

            // Validate agent type exists
            Agent agent = agentMapper.selectByType(agentType);
            if (agent == null) {
                log.warn("LLM returned unknown agent type: {}, using default", agentType);
                return new IntentResult("command_executor", reasoning, 0.5);
            }

            return new IntentResult(agentType, reasoning, confidence);
        } catch (Exception e) {
            log.warn("Failed to parse intent response: {}", response, e);
            return new IntentResult("command_executor", "Parse failed", 0.5);
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

    public record IntentResult(
            String agentType,
            String reasoning,
            double confidence
    ) {}
}
