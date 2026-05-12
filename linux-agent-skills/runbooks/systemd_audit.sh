#!/usr/bin/env bash
# systemd 与定时任务快照：失败单元、运行中服务、定时器
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

agent_log "runbook systemd_audit start"

if ! have_cmd systemctl; then
  echo "systemctl 不存在（可能非 systemd 环境）"
  agent_log "runbook systemd_audit skip no systemctl"
  echo OK
  exit 0
fi

echo "========== 失败单元（systemctl --failed） =========="
systemctl --failed --no-pager 2>/dev/null | head -n 40 || true

echo
echo "========== 运行中的服务单元（节选） =========="
systemctl list-units --type=service --state=running --no-pager 2>/dev/null | head -n 28 || true

echo
echo "========== 已启用单元（service，节选） =========="
systemctl list-unit-files --type=service --state=enabled --no-pager 2>/dev/null | head -n 28 || true

echo
echo "========== 定时器（systemctl list-timers，节选） =========="
systemctl list-timers --all --no-pager 2>/dev/null | head -n 28 || true

echo
echo "========== 启动耗时（systemd-analyze，若可用） =========="
if have_cmd systemd-analyze; then
  systemd-analyze time 2>/dev/null || true
  systemd-analyze blame 2>/dev/null | head -n 15 || true
else
  echo "systemd-analyze 未安装"
fi

agent_log "runbook systemd_audit done"
echo OK
