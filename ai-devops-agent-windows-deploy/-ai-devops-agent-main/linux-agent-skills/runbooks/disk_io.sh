#!/usr/bin/env bash
# 磁盘 I/O 与块设备快照（iostat/sar/lsblk）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

agent_log "runbook disk_io start"

echo "========== 块设备（lsblk） =========="
if have_cmd lsblk; then
  lsblk -o NAME,SIZE,FSTYPE,MOUNTPOINT,MODEL -e 7 2>/dev/null || lsblk || true
else
  echo "lsblk 未安装"
fi

echo
echo "========== 磁盘 I/O（iostat，若 sysstat 已安装） =========="
if have_cmd iostat; then
  iostat -xz 1 2 2>/dev/null || iostat -x 1 2 2>/dev/null || iostat 1 2 || true
else
  echo "iostat 未安装（Ubuntu: apt install sysstat）"
fi

echo
echo "========== 近期 CPU/磁盘压力（sar -d，若可用） =========="
if have_cmd sar; then
  sar -d 1 1 2>/dev/null | head -n 30 || true
else
  echo "sar 未安装可跳过"
fi

agent_log "runbook disk_io done"
echo OK
