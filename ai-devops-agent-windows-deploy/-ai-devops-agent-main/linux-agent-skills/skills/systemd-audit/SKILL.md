# Skill: systemd 与定时任务审计

## 触发关键词
systemd、服务、失败单元、定时器、开机自启、systemctl 等。

## 行为
执行 `runbooks/systemd_audit.sh`，调用：
- `systemctl --failed`
- `systemctl list-units --type=service --state=running`
- `systemctl list-unit-files --type=service --state=enabled`
- `systemctl list-timers`
- `systemd-analyze time` / `blame`（若可用）

## 适用环境
systemd 管理的 Linux 发行版。
