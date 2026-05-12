# 与课设 linux-agent-skills 的集成说明

本仓库（**主工程**）为 Spring Boot **AI DevOps Agent**。子目录 **`linux-agent-skills/`** 来自课设「Shell 巡检 + agent-router.sh」，通过 **SSH 在远端 Linux 上执行** `bin/agent-router.sh`，与本地是否安装 Python/Flask 无关。

## 架构关系

1. **Java 后端**：多 Agent 编排、SSH、LLM、任务持久化、WebSocket。
2. **linux_skills Agent**：生成命令  
   `bash /{远端路径}/bin/agent-router.sh '用户自然语言'`，在目标机上走 **Bash runbook**（磁盘、网络、systemd、日志等）。
3. **配置**：`application.yml` 中 `linux-skills.remote-path`（环境变量 `LINUX_SKILLS_REMOTE_PATH`）必须与 **SSH 目标机上的实际目录** 一致。

## 部署步骤（目标 Linux 服务器）

将本仓库中的 `linux-agent-skills/` 同步到服务器，例如：

```bash
# 在开发机或 CI 中
rsync -av --exclude '.venv-web' --exclude 'logs' ./linux-agent-skills/ user@server:/opt/linux-agent-skills/
ssh user@server 'chmod +x /opt/linux-agent-skills/bin/agent-router.sh /opt/linux-agent-skills/runbooks/*.sh'
```

默认远端路径为 **`/opt/linux-agent-skills`**。

## API 使用

- 同步任务：`POST /api/tasks`，`"agentType": "linux_skills"`，`userInput` 填写与课设路由一致的关键词（如「检查一下磁盘空间」「网络端口和路由」）。
- 异步 + 自动意图：`POST /api/tasks/async`，`"agentType": "auto"` —— **`IntentRecognizer` 会按关键词优先路由到 `linux_skills`**（与 `agent-router.sh` 意图接近时）。
- 工具清单（静态 JSON，供前端或大模型参考）：`GET /api/linux-skills/catalog`

## 依赖说明

- 目标机需 **Bash**、`bash` 可执行 runbook 中涉及的命令（`ip`、`ss`、`systemctl` 等，未安装时 runbook 会跳过相应段落）。
- **不**要求在目标机运行本项目的 Flask Web；仅脚本与 runbook 即可。

## 安全

与课设一致：`agent-router.sh` 仅接受自然语言参数，**不在此路径注入任意 Shell 元字符**；若通过其他 Agent（如 `command_executor`）由 LLM 生成命令，仍受 `CommandSecurityService` 约束。
