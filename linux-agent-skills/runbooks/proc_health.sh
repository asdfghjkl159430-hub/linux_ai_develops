#!/usr/bin/env bash
# 进程与负载健康快照
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

agent_log "runbook proc_health start"

echo "========== uptime =========="
uptime

echo
echo "========== CPU/内存摘要（ps aux 按内存排序 TOP15） =========="
if have_cmd ps; then
  ps aux --sort=-%mem 2>/dev/null | head -n 16 || ps aux | head -n 16
fi

echo
echo "========== loadavg（/proc/loadavg） =========="
cat /proc/loadavg 2>/dev/null || true
if have_cmd nproc; then
  echo "逻辑 CPU 数: $(nproc)"
fi

echo
echo "========== vmstat（若存在，采样 3 次） =========="
if have_cmd vmstat; then
  vmstat 1 3 2>/dev/null || vmstat 1 2
else
  echo "vmstat 未安装，跳过"
fi

echo
echo "========== systemd 失败单元（systemctl --failed，若存在） =========="
if have_cmd systemctl; then
  systemctl --failed --no-pager 2>/dev/null | head -n 30 || true
else
  echo "非 systemd 环境或未安装 systemctl"
fi

agent_log "runbook proc_health done"
echo OK
