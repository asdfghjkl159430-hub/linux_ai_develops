---
name: process-health
description: 采集负载、内存占用前列进程、vmstat 与 systemd 失败单元，用于「卡顿/CPU/内存/服务异常」类意图。
---

# 进程与健康检查 Skill

## 何时使用

- 用户提到：进程、负载、CPU、内存、top、vmstat、systemd、失败服务等。

## 执行逻辑

1. 调用 `runbooks/proc_health.sh`。
2. 依次输出：`uptime`、`ps` Top 进程、`/proc/loadavg`、`vmstat`（若存在）、`systemctl --failed`（若存在）。

## 安全与权限

- 只读查询；`systemctl --failed` 为状态读取。

## 路由关键词

`进程|负载|cpu|内存|mem|top|vmstat|systemd|失败单元`
