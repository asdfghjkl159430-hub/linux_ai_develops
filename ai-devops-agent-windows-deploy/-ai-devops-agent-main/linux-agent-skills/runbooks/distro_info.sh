#!/usr/bin/env bash
# 发行版与内核信息（grep/sed 读取 /etc/os-release）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/io_helpers.sh"

agent_log "distro_info start"

echo "========== /etc/os-release（节选） =========="
if [[ -r /etc/os-release ]]; then
  grep -E '^(NAME|VERSION|ID|PRETTY_NAME)=' /etc/os-release || true
else
  echo "不可读 /etc/os-release"
fi

echo
echo "========== 内核 uname =========="
uname -a || true

echo
echo "========== 运行时间（uptime） =========="
uptime || true

echo
echo "========== PATH 前五个目录 =========="
echo "$PATH" | awk -F: '{for(i=1;i<=5 && i<=NF;i++) print i, $i}'

agent_log "distro_info done"
echo OK
