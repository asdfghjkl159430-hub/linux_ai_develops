# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (skip tests)
mvn -DskipTests package

# Run
mvn spring-boot:run
# or
java -jar target/devops-agent-1.0.0.jar

# Windows quick-start
start-devops-agent.bat
```

- **Java 17+** required, **Maven 3.8+**, **MySQL 8.0**
- Default port: `http://localhost:8080/`, API prefix: `/api/`
- Browser console UI at `http://localhost:8080/`

## Architecture

This is a two-part system:

1. **Spring Boot 3.2.5 backend** (main project) — AI-driven DevOps agent that accepts natural language tasks, uses LLM to plan/execute/reason, runs commands on remote Linux servers via SSH (JSch), and streams progress via WebSocket (STOMP/SockJS).
2. **`linux-agent-skills/` subdirectory** — standalone Shell runbook package. Must be deployed separately to each target Linux server at `/opt/linux-agent-skills/` (configurable via `linux-skills.remote-path`). The Java side invokes `bash bin/agent-router.sh '<query>'` over SSH; the router dispatches to runbooks by keyword.

### Agent dispatch pipeline

```
User input → IntentRecognizer (keyword match → LLM fallback) → agent_type
  → TaskOrchestrator dispatches to the matching BaseAgent bean
  → Agent.plan() returns AgentPlan (command or multiCommands)
  → CommandSecurityService validates → SshService executes via JSch
  → Agent.nextStep() loops until null or max 20 steps → Agent.summarize()
```

### Key agent types (registered in `agent` DB table + `init.sql` seed data)

| agent_type | Class | Behavior |
|---|---|---|
| `command_executor` | `CommandExecutorAgent` | LLM generates shell commands from NL |
| `log_analyzer` | `LogAnalyzerAgent` | LLM analyzes command output for issues |
| `deploy_agent` | `DeployAgent` | LLM generates multi-step deploy plans |
| `linux_skills` | `LinuxSkillsAgent` | No LLM; builds `bash agent-router.sh 'query'` command for remote runbook execution |

### Multi-agent workflows

`POST /api/tasks/workflow` triggers `MultiAgentCoordinator`, which:
1. Asks LLM to produce a `WorkflowPlan` (ordered steps with dependency graph)
2. Executes steps in dependency order, each through the appropriate `BaseAgent`
3. Generates a final LLM summary

### Database

MySQL `devops_agent` database. Tables: `server` (SSH targets), `task` (user-submitted tasks), `task_log` (step-by-step command output), `agent` (agent definitions + system prompts), `task_config` (key-value settings). Initialize with `sql/init.sql`. For adding the `linux_skills` agent to an existing DB, run `sql/migration_add_linux_skills_agent.sql`.

### Configuration (`application.yml`)

- `llm.api-key` / `llm.base-url` / `llm.model` — OpenAI-compatible API (defaults point to Aliyun DashScope/qwen-plus)
- `ssh.default-port` (22) / `ssh.timeout` (30000ms)
- `security.command.mode` — `permissive` | `strict` | `disabled`; `blacklist`/`whitelist` are comma-separated
- `linux-skills.remote-path` — target server path for the runbook directory (env: `LINUX_SKILLS_REMOTE_PATH`)

### SSH command security (`CommandSecurityService`)

Three modes, configured in `application.yml`:
- **permissive** (default): blocks hardcoded dangerous commands/patterns (rm -rf /, mkfs, fork bombs, shutdown, iptables -F, etc.), warns on risky operations
- **strict**: additionally requires commands start with a whitelist prefix
- **disabled**: no checks

### WebSocket

Endpoint `/ws` (SockJS). Topics: `/topic/task/{taskId}` (single task progress), `/topic/tasks` (global events). `TaskProgressBroadcaster` sends `TaskProgressMessage` via `SimpMessagingTemplate`.

### linux-agent-skills runbook dispatch rules

`agent-router.sh` keyword matching order (same order in `IntentRecognizer.matchesLinuxSkillsRouterIntent()`):
log → network → systemd → disk_io → disk → sysinfo → proc

Each keyword group maps to a runbook in `runbooks/`. The Java `LinuxSkillsAgent.bashSingleQuoted()` properly escapes user input before passing it as a single-quoted argument.
