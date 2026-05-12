# Git 版本管理说明

本课设仓库设计为 **Git 管理**；若实验机未安装 Git，请先安装：

```bash
sudo apt update
sudo apt install -y git
```

## 初始化与首次提交（在项目根目录执行）

```bash
cd /opt/kcsj/linux-agent-skills
git init
git config user.name "你的姓名"
git config user.email "你的学号@学校域名"
git add .
git status
git commit -m "chore: 初始化 Linux Agent Skills 课设仓库"
```

## 建议的过程性提交（示例）

```bash
git add runbooks lib bin
git commit -m "feat: 磁盘/日志/进程 runbook 与公共库"

git add skills
git commit -m "docs: Agent Skills 规格 Markdown"

git add tests docs/01-项目规划书.md docs/02-需求分析.md docs/03-系统设计.md
git commit -m "test: 冒烟测试与需求设计文档"

git add docs/report README.md
git commit -m "docs: 课程报告与 README 定稿"
```

## 导出提交记录（提交到课程服务器前）

```bash
git log --oneline --decorate --graph > docs/git-log.txt
```

当前仓库若尚未执行 `git init`，`docs/git-log-示例.txt` 仅作格式参考，**答辩前请替换为真实 `git log` 输出**。
