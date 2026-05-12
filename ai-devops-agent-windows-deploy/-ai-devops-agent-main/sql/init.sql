-- =====================================================
-- AI DevOps Agent - Database Init Script
-- =====================================================

CREATE DATABASE IF NOT EXISTS devops_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE devops_agent;

-- -----------------------------------------------------
-- Server table - managed server inventory
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `server` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL COMMENT 'server display name',
    `host` VARCHAR(128) NOT NULL COMMENT 'IP or hostname',
    `port` INT DEFAULT 22 COMMENT 'SSH port',
    `username` VARCHAR(64) NOT NULL COMMENT 'SSH username',
    `password` VARCHAR(256) NOT NULL COMMENT 'SSH password (encrypted in production)',
    `os_type` VARCHAR(32) DEFAULT 'Linux' COMMENT 'OS type',
    `tags` VARCHAR(255) DEFAULT '' COMMENT 'comma-separated tags',
    `status` TINYINT DEFAULT 1 COMMENT '1=active, 0=disabled',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_host_port` (`host`, `port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='managed server inventory';

-- -----------------------------------------------------
-- Task table - user-submitted natural language tasks
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `task` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_input` TEXT NOT NULL COMMENT 'original user natural language input',
    `intent` VARCHAR(100) DEFAULT '' COMMENT 'parsed intent category',
    `server_id` BIGINT DEFAULT NULL COMMENT 'target server',
    `status` TINYINT DEFAULT 0 COMMENT '0=pending,1=running,2=success,3=failed,4=cancelled',
    `agent_type` VARCHAR(50) DEFAULT 'command_executor' COMMENT 'agent that handles this task',
    `result_summary` TEXT COMMENT 'AI-generated summary of final result',
    `error_message` TEXT COMMENT 'error info if failed',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    KEY `idx_status` (`status`),
    KEY `idx_server` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='task records';

-- -----------------------------------------------------
-- Task log table - step-by-step execution log
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id` BIGINT NOT NULL COMMENT 'parent task id',
    `step` INT NOT NULL COMMENT 'step sequence number',
    `command` TEXT COMMENT 'executed shell command',
    `output` LONGTEXT COMMENT 'command stdout+stderr',
    `exit_code` INT DEFAULT -1 COMMENT 'command exit code',
    `ai_reasoning` TEXT COMMENT 'AI decision reasoning for this step',
    `status` TINYINT DEFAULT 0 COMMENT '0=running,1=success,2=failed',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='task step execution logs';

-- -----------------------------------------------------
-- Agent table - registered agent definitions
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL COMMENT 'agent display name',
    `agent_type` VARCHAR(50) NOT NULL UNIQUE COMMENT 'agent type identifier',
    `description` TEXT COMMENT 'what this agent does',
    `system_prompt` LONGTEXT NOT NULL COMMENT 'system prompt for LLM',
    `status` TINYINT DEFAULT 1 COMMENT '1=active, 0=disabled',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agent definitions';

-- -----------------------------------------------------
-- Seed data - default agents
-- -----------------------------------------------------
INSERT INTO `agent` (`name`, `agent_type`, `description`, `system_prompt`) VALUES
('Command Executor', 'command_executor',
 'Executes shell commands on target servers based on natural language instructions',
 '你是一个 Linux 运维专家。根据用户的自然语言指令，生成对应的 Linux 命令。
你每次返回一个 JSON 对象，格式如下：
{"command": "具体的shell命令", "explanation": "简短说明为什么要执行这个命令"}
只返回 JSON，不要其他内容。'),

('Log Analyzer', 'log_analyzer',
 'Analyzes system logs and command output to diagnose issues',
 '你是一个日志分析专家。分析下面的日志或命令输出，找出潜在的问题并给出建议。
请以以下 JSON 格式返回：
{"status": "normal|warning|error", "issues": ["问题1", "问题2"], "suggestions": ["建议1", "建议2"], "summary": "整体情况总结"}
只返回 JSON，不要其他内容。'),

 ('Deploy Agent', 'deploy_agent',
  'Handles application deployment workflows including Docker services',
  '你是一个部署专家。根据用户的部署需求，生成完整的部署步骤。
你每次返回一个 JSON 对象：
{"commands": [{"command": "命令1", "description": "步骤说明"}, {"command": "命令2", "description": "步骤说明"}], "notes": "部署注意事项"}
只返回 JSON，不要其他内容。'),

('Linux Agent Skills', 'linux_skills',
 'Runs bundled shell runbooks on the target host via bin/agent-router.sh (disk, log, network, systemd, etc.)',
 '此 Agent 不使用本字段中的 LLM 提示词；由 Java 侧生成 bash 调用远端 agent-router.sh。');

-- -----------------------------------------------------
-- Task execution configuration
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(100) NOT NULL UNIQUE,
    `config_value` TEXT NOT NULL,
    `description` VARCHAR(255) DEFAULT '',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='task execution configuration';

-- Default configurations
INSERT INTO `task_config` (`config_key`, `config_value`, `description`) VALUES
('max_execution_steps', '20', 'Maximum steps per task execution'),
('command_timeout', '30000', 'Default command timeout in milliseconds'),
('enable_security_check', 'true', 'Enable command security validation'),
('security_mode', 'permissive', 'Security mode: strict, permissive, disabled');
