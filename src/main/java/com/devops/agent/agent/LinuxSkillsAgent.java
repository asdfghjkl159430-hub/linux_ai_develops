package com.devops.agent.agent;

import com.devops.agent.config.LinuxSkillsProperties;
import com.devops.agent.entity.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 集成课设项目 linux-agent-skills：在目标机上执行 {@code bin/agent-router.sh}，
 * 按关键词路由到磁盘 / 日志 / 网络 / systemd 等 Shell runbook（无需 LLM 即可巡检）。
 */
@Slf4j
@Component
public class LinuxSkillsAgent implements BaseAgent {

    private final LinuxSkillsProperties properties;

    public LinuxSkillsAgent(LinuxSkillsProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getAgentType() {
        return "linux_skills";
    }

    @Override
    public AgentPlan plan(String userInput, Server server) {
        String root = properties.getRemotePath();
        if (root == null || root.isBlank()) {
            root = "/opt/linux-agent-skills";
        }
        root = root.trim().replaceAll("/+$", "");
        String router = root + "/bin/agent-router.sh";
        String cmd = "bash " + bashSingleQuoted(router) + " " + bashSingleQuoted(userInput != null ? userInput : "");
        log.debug("linux_skills command for server {}: {}", server.getHost(), cmd);
        return AgentPlan.single(cmd, "Linux Agent Skills：远端执行 agent-router.sh（runbook 关键词：磁盘/日志/网络/systemd/磁盘IO/系统版本/进程等）");
    }

    /**
     * Bash 单引号转义：' -> '\''。
     */
    static String bashSingleQuoted(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    @Override
    public AgentPlan nextStep(String previousOutput, String previousReasoning) {
        return null;
    }

    @Override
    public String summarize(List<String> commandOutputs) {
        if (commandOutputs == null || commandOutputs.isEmpty()) {
            return "无输出";
        }
        return String.join("\n---\n", commandOutputs);
    }
}
