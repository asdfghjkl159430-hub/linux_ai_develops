package com.devops.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 目标机器上 linux-agent-skills（runbook + agent-router.sh）的安装路径。
 * 需将本仓库内 {@code linux-agent-skills/} 目录部署到该路径（见 README 集成说明）。
 */
@Data
@ConfigurationProperties(prefix = "linux-skills")
public class LinuxSkillsProperties {

    /**
     * SSH 目标机上包含 {@code bin/agent-router.sh} 的根目录绝对路径。
     */
    private String remotePath = "/opt/linux-agent-skills";
}
