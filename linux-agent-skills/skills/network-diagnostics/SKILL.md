# Skill: 网络诊断（network_diag）

## 触发关键词
网络、端口、监听、路由、ping、防火墙、socket、ss、ip 等（见 `bin/agent-router.sh` 中 `network` 规则）。

## 行为
执行 `runbooks/network_diag.sh`，主动调用：
- `hostname` / `getent hosts`
- `resolvectl`（若存在）
- `ip -br link` / `ip -br a`、`ip route`
- `ss -tuln`（监听端口摘要）
- `ping` 本机环回
- `ss -s` 协议栈统计

## 输出
标准输出由 `agent-router.sh` 写入 `logs/router-*.log`。

## 依赖
推荐安装 `iproute2`（提供 `ip`、`ss`）。
