# AI Agent 轻量 DevOps 自动运维系统

> 衍生自开源仓库：<https://github.com/kfdkdjdjsulaiman854-code/-ai-devops-agent>  
> Windows 控制台部署说明另见：**[docs/WINDOWS_DEPLOY.md](docs/WINDOWS_DEPLOY.md)**

## 项目概述
基于 Spring Boot + MyBatis 构建的轻量级 DevOps 自动运维系统，通过自然语言驱动实现服务器自动化管理。

**仓库集成**：本仓库 **以 GitHub Spring Boot 项目为主工程**，子目录 **`linux-agent-skills/`** 为课设 Shell 巡检技能包（`agent-router.sh` + runbooks）。通过 Agent 类型 **`linux_skills`** 经 SSH 在远端执行该脚本。详见 **[docs/INTEGRATION-linux-agent-skills.md](docs/INTEGRATION-linux-agent-skills.md)**。  
已存在的数据库请执行 **`sql/migration_add_linux_skills_agent.sql`** 注册新 Agent。

**核心痛点解决：**
- 重复操作多
- 问题排查慢  
- 命令记忆成本高

## 核心功能
- 自然语言任务解析与意图识别
- 多步操作自动拆解与指令生成
- SSH 远程执行 Linux 命令
- AI 驱动的日志分析与决策推理
- 统一返回结构与全局异常处理
- 任务日志记录与历史追踪
- 基础权限隔离机制
- 分页查询支持
- **异步任务执行与取消**
- **WebSocket 实时进度推送**
- **命令安全控制（白名单/黑名单）**
- **多 Agent 协作编排**
- **自动意图识别与 Agent 选择**
- **linux_skills Agent**：远端调用 `linux-agent-skills/bin/agent-router.sh` 执行磁盘/日志/网络/systemd 等 **受控 runbook**（与 LLM 生成命令解耦）

## 系统架构
```
用户输入 → Agent 解析 → 操作拆解 → 指令生成 → SSH 执行 → 结果返回 → AI 分析 → 继续决策
```

## 技术栈
- **后端框架**: Spring Boot 3.2.5 + MyBatis 3.0.3
- **数据库**: MySQL 8.0
- **SSH 连接**: JSch 0.2.17
- **HTTP 客户端**: OkHttp
- **WebSocket**: Spring WebSocket + STOMP
- **AI 接口**: 兼容 OpenAI API 格式（支持通义千问等）

## 项目结构
```
linux-agent-skills/           # 课设 Shell 技能包（需同步到 SSH 目标机，见 docs/INTEGRATION-linux-agent-skills.md）
├── bin/agent-router.sh
├── runbooks/*.sh
└── ...

src/main/java/com/devops/agent/
├── agent/                     # Agent 核心实现
│   ├── BaseAgent.java         # Agent 基础接口
│   ├── AgentPlan.java         # 执行计划
│   ├── CommandExecutorAgent   # 命令执行 Agent
│   ├── LogAnalyzerAgent       # 日志分析 Agent
│   ├── DeployAgent            # 部署 Agent
│   ├── TaskOrchestrator       # 任务编排器
│   └── MultiAgentCoordinator  # 多 Agent 协调器
├── config/                    # 配置类
│   ├── AsyncTaskConfig        # 异步任务配置
│   └── WebSocketConfig        # WebSocket 配置
├── controller/                # REST API
│   ├── ApiController          # 主要 API
│   └── SystemController       # 系统监控 API
├── service/                   # 业务服务
│   ├── SshService             # SSH 执行服务
│   ├── LlmService             # LLM 调用服务
│   ├── TaskService            # 任务管理服务
│   ├── ServerService          # 服务器管理服务
│   ├── AsyncTaskService       # 异步任务服务
│   ├── IntentRecognizer       # 意图识别服务
│   ├── CommandSecurityService # 命令安全服务
│   └── TaskProgressBroadcaster# 进度广播服务
├── entity/                    # 数据实体
├── mapper/                    # MyBatis Mapper
├── dto/                       # 数据传输对象
└── common/                    # 通用类
```

## API 接口

### 服务器管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/servers | 添加服务器 |
| PUT | /api/servers | 更新服务器 |
| DELETE | /api/servers/{id} | 删除服务器 |
| GET | /api/servers | 服务器列表 |
| GET | /api/servers/{id} | 服务器详情 |
| POST | /api/servers/{id}/test | 测试连接 |
| POST | /api/servers/{serverId}/diagnostics | **快速诊断**（对指定已登记服务器批量执行 uptime / 内存 / 磁盘等） |

### 任务管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/tasks | 同步执行任务 |
| POST | /api/tasks/async | 异步执行任务 |
| POST | /api/tasks/workflow | 多 Agent 工作流任务 |
| POST | /api/tasks/{id}/cancel | 取消任务 |
| GET | /api/tasks/{id}/progress | 任务进度 |
| GET | /api/tasks | 任务列表（分页） |
| GET | /api/tasks/{id} | 任务详情（含日志） |
| POST | /api/tasks/{serverId}/diagnostics | 快速诊断（兼容旧路径，参数实为 `serverId`；推荐用上一节 `/api/servers/{serverId}/diagnostics`） |

### 意图识别
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/intent/recognize | 识别用户意图 |

### Linux Agent Skills（课设 runbook 集成）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/linux-skills/catalog | 返回已接入的 runbook/工具 JSON（ Classpath 静态资源） |

### 系统监控
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/system/health | 健康检查 |
| GET | /api/system/status | 系统状态 |
| GET | /api/system/metrics | 系统指标 |

### WebSocket
- **端点**: `/ws` (SockJS)
- **订阅主题**:
  - `/topic/task/{taskId}` - 单任务进度
  - `/topic/tasks` - 全局任务事件

## 配置说明

### application.yml
```yaml
# LLM 配置
llm:
  api-key: ${LLM_API_KEY:sk-your-api-key}
  base-url: ${LLM_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
  model: ${LLM_MODEL:qwen-plus}
  max-tokens: 4000
  temperature: 0.3

# SSH 配置
ssh:
  default-port: 22
  timeout: 30000

# 安全配置
security:
  command:
    mode: permissive  # strict, permissive, disabled
    blacklist: ""     # 逗号分隔的命令黑名单
    whitelist: ""     # 逗号分隔的命令白名单（strict 模式）
```

## 应用场景
- 个人 VPS 运维
- Docker 服务部署
- AI 接口测试
- 开发环境管理
- 系统故障诊断
- 日志分析排查

## 多 Agent 协作扩展
- **CommandExecutor**: 执行 Shell 命令
- **LogAnalyzer**: 日志分析与诊断
- **DeployAgent**: 应用部署
- **MultiAgentCoordinator**: 复杂工作流编排

## 快速开始

1. 创建数据库并执行 `sql/init.sql`
2. 配置 `application.yml` 中的数据库和 LLM 参数
3. 启动应用: `mvn spring-boot:run` 或 `java -jar target/devops-agent-1.0.0.jar`
4. **浏览器控制台 UI**：`http://localhost:8080/`（管理主机、下发任务、查看日志）
5. 访问 API 前缀: `http://localhost:8080/api/`

## 示例请求

### 添加服务器
```bash
curl -X POST http://localhost:8080/api/servers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-server",
    "host": "192.168.1.100",
    "port": 22,
    "username": "root",
    "password": "password"
  }'
```

### 执行任务
```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "查看服务器内存使用情况",
    "serverId": 1,
    "agentType": "command_executor"
  }'
```

### 异步执行任务（自动选择 Agent）
```bash
curl -X POST http://localhost:8080/api/tasks/async \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "分析系统日志找出错误",
    "serverId": 1,
    "agentType": "auto"
  }'
```

### 使用 linux_skills 在远端执行 agent-router（需已部署 linux-agent-skills 目录）
```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "检查一下磁盘空间和网络端口",
    "serverId": 1,
    "agentType": "linux_skills"
  }'
```

### 拉取课设工具目录 JSON
```bash
curl -s http://localhost:8080/api/linux-skills/catalog
```

## 安全机制
- 命令安全校验（危险命令拦截）
- 白名单/黑名单控制
- SQL 注入防护
- 敏感信息过滤
