#!/usr/bin/env bash
# 磁盘与挂载巡检：df / inode / findmnt
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

agent_log "runbook disk_check start"

echo "========== 磁盘空间（df -hT） =========="
df -hT 2>/dev/null || df -h

echo
echo "========== inode 使用率（df -i） =========="
df -i 2>/dev/null || true

if have_cmd findmnt; then
  echo
  echo "========== 挂载点（findmnt） =========="
  findmnt -o TARGET,SOURCE,FSTYPE,OPTIONS,SIZE,USED,AVAIL,USE% 2>/dev/null | head -n 40 || findmnt
fi

echo
echo "========== 阈值提示（启发式） =========="
df -P 2>/dev/null | awk 'NR==1{next} /^Filesystem/{next} {
  gsub(/%/,"",$5); use=$5+0;
  if(use>=90) print "WARN 分区", $6, "已用", use"%";
  else if(use>=80) print "INFO 分区", $6, "已用", use"%";
}' || true

agent_log "runbook disk_check done"
echo OK
