# Linux 运维 Agent Skills：巡检与诊断技能包

《Linux 系统管理与Shell编程》课程项目 · 方向 **4.AI Agent Skills**  
仓库路径：`/opt/kcsj/linux-agent-skills`

## 一、项目简介

将「运维意图」映射为 **受控 Shell runbook**：磁盘巡检、日志关键字审计、进程与 systemd 健康快照；入口脚本 `bin/agent-router.sh` 负责关键词路由并将输出 `tee` 到 `logs/`。

## 二、快速开始

```bash
cd /opt/kcsj/linux-agent-skills
chmod +x bin/agent-router.sh runbooks/*.sh tests/run_all_tests.sh scripts/export_report.sh lib/safe_exec.py
./tests/run_all_tests.sh
./bin/agent-router.sh "检查磁盘空间"
```

**高读权限模式（需本机已配置免密 sudo，仅建议实验环境）：**

```bash
export AGENT_USE_SUDO=1
./bin/agent-router.sh "分析系统日志错误"
```

## 三、目录结构

| 路径 | 说明 |
|------|------|
| `bin/agent-router.sh` | Agent 路由入口 |
| `runbooks/` | Shell 巡检流水线（含 `network_diag`、`systemd_audit`、`disk_io`、`distro_info` 等） |
| `lib/tools_catalog.json` | 已接入工具与 runbook 清单（供 `GET /api/tools`） |
| `lib/common.sh` | 公共函数与可选 sudo 读文件 |
| `lib/safe_exec.py` | Python 白名单 subprocess |
| `skills/` | Agent Skills 规格（Markdown） |
| `docs/` | 规划书、需求、设计、使用说明、测试、AI 声明、答辩提纲 |
| `docs/report/` | 课程项目报告（Markdown 源） |
| `tests/` | 一键测试脚本 |
| `docs/crontab.example` | 定时任务示例 |
| `web/` | **Web 控制台**：`/api/run` 支持安全巡检 / 自动中文意图 / Shell 实验 |
| `lib/intent_nl.py` | 中文自然语言 → 单行命令（路径受 `AGENT_ALLOWED_ROOTS` 约束） |
| `lib/shell_guard.py` | Shell 实验模式黑名单校验（非绝对安全） |
| `requirements-web.txt` | Flask 依赖（Web 用） |
| `scripts/start_web.sh` | 创建 venv、安装依赖并启动 Web |

## 四、Web 交互界面（新增）

```bash
cd /opt/kcsj/linux-agent-skills
chmod +x scripts/start_web.sh
./scripts/start_web.sh
# 浏览器访问 http://127.0.0.1:8765/ ，输入如「检查磁盘空间」后点击执行
```

浏览器或自动化脚本可请求 **`GET /api/tools`** 获取 `lib/tools_catalog.json`（已接入的 runbook 与常用命令列表），便于外部 Agent 编排调用。

说明：**安全巡检**（默认 `mode=skills`）仍以参数形式调用 `bin/agent-router.sh`，并禁止 shell 元字符。**自动识别**（`mode=auto`）与 **Shell 实验**（`mode=shell`）会经 `bash -lc` 执行单行命令，需前端勾选「确认风险」；自动模式可解析如「在 opt 目录下创建一个 ceshi 文件夹」等句式，Shell 模式用于其它单行 Linux 命令，二者均经 `shell_guard` 黑名单过滤，且自然语言创建目录等操作仍受 `AGENT_ALLOWED_ROOTS`（默认含 `/opt/kcsj`、`/opt`、`/tmp` 与用户主目录）限制。**勿对公网开放**，黑名单不等于绝对安全。

## 五、课程规范对照（自检）

| 规范条款 | 本项目体现 |
|----------|------------|
| Shell 脚本 | `bin` + `runbooks` + `tests` |
| Linux 命令 | `df`/`ps`/`uptime`/`vmstat`/`systemctl`/`tail`… |
| grep/sed/awk | `log_audit.sh`、`disk_check.sh` |
| 管道与重定向 | 各 runbook 内多处管道 |
| 进程管理 | `proc_health.sh` |
| 日志分析 | `log_audit.sh` |
| crontab | `docs/crontab.example` |
| Python 调用 Linux | `lib/safe_exec.py` |
| Git | 本仓库提交历史 |
| Agent 工具/Skills | `skills/*.md` + 路由编排 |
| Web 辅助管理 | `web/app.py` + 浏览器控制台 |

## 六、小组（5 人）分工

见 `docs/01-项目规划书.md` 中的分工表；`git log` 用于佐证成员参与（请在真实组内将 author 配置为各自信息）。

## 七、辅助脚本

```bash
chmod +x bin/health-all.sh lib/validate_env.sh
bash lib/validate_env.sh          # 依赖自检
./bin/health-all.sh               # 顺序跑完全部 runbook
python3 lib/safe_exec.py list   # 查看白名单
python3 lib/safe_exec.py run -- df -h
```

## 八、文档索引

- 规划书：`docs/01-项目规划书.md`  
- 使用说明：`docs/使用说明.md`  
- 测试记录：`docs/测试记录.md`  
- AI 使用声明：`docs/AI使用声明.md`  
- 报告正文：`docs/report/课程项目报告.md`  

## 九、Git 与提交记录

若系统未安装 Git，请先执行 `sudo apt install -y git`，详见 `docs/GIT版本管理说明.md`。

```bash
cd /opt/kcsj/linux-agent-skills
git init
git add .
git commit -m "chore: init linux-agent-skills"
git log --oneline --decorate > docs/git-log.txt
```

在尚未初始化仓库前，可用 `docs/git-log-示例.txt` 作为格式参考（**提交前请替换为真实 log**）。

**当前 Shell/Python/awk 源码行数约 740+**（`find ... | wc -l` 自检），与多脚本协同、长文档共同构成工作量。

## 十、许可证与合规

课程教学用途；若引用系统日志请注意隐私与授权。
