#!/usr/bin/env bash
# 运行前环境自检：依赖命令是否存在（课设：可维护性）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

check_one() {
  local c="$1"
  if command -v "$c" >/dev/null 2>&1; then
    echo "OK  $c -> $(command -v "$c")"
  else
    echo "MISS $c"
  fi
}

echo "========== 依赖命令检测 =========="
for c in bash awk grep sed tail head cut sort uniq wc df ps uptime vmstat findmnt systemctl journalctl sudo; do
  check_one "$c" || true
done

echo
echo "========== /proc 可读 =========="
test -r /proc/loadavg && echo OK /proc/loadavg || echo FAIL /proc/loadavg

echo
echo "========== 项目路径 =========="
echo "$ROOT_DIR"

agent_log "validate_env done"
