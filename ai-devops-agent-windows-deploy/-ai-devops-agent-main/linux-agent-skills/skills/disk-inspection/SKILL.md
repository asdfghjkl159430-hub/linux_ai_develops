---
name: disk-inspection
description: Linux 磁盘空间、inode 与挂载点巡检（df/findmnt），供 Agent 在「空间不足/磁盘告警」类意图下调用。
---

# 磁盘巡检 Skill

## 何时使用

- 用户提到：磁盘、空间、df、inode、挂载、根分区满、扩容前检查等。

## 执行逻辑

1. 记录意图到 `logs/`（由 `lib/common.sh` 完成）。
2. 调用 runbook：`runbooks/disk_check.sh`。
3. 输出人类可读的 `df`/`findmnt` 摘要，并用 `awk` 对使用率 ≥80% 的分区给出提示。

## 安全与权限

- 只读系统信息，不修改磁盘。
- 不需要 root；若需查看受限路径，仍保持只读。

## 与本项目路由的映射关键词

`磁盘|空间|df|inode|挂载|mount|disk`
