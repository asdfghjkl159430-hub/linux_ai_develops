-- 若数据库已由旧版 init.sql 初始化，执行本脚本注册 linux_skills Agent
USE devops_agent;

INSERT IGNORE INTO `agent` (`name`, `agent_type`, `description`, `system_prompt`, `status`) VALUES
('Linux Agent Skills', 'linux_skills',
 'Runs bundled shell runbooks on the target host via bin/agent-router.sh (disk, log, network, systemd, etc.)',
 '此 Agent 不使用本字段中的 LLM 提示词；由 Java 侧生成 bash 调用远端 agent-router.sh。',
 1);
