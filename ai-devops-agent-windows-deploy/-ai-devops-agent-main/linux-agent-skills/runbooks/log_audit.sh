#!/usr/bin/env bash
# 日志审计：grep / awk / 可选 sudo 提升读权限（AGENT_USE_SUDO=1）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

DEFAULT_FILES=(
  /var/log/syslog
  /var/log/auth.log
  /var/log/kern.log
)

LOG_FILES=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --file|-f) LOG_FILES+=("$2"); shift 2 ;;
    *) LOG_FILES+=("$1"); shift ;;
  esac
done

if [[ ${#LOG_FILES[@]} -eq 0 ]]; then
  LOG_FILES=("${DEFAULT_FILES[@]}")
fi

agent_log "runbook log_audit files=${LOG_FILES[*]} AGENT_USE_SUDO=${AGENT_USE_SUDO:-0}"

echo "========== 日志可读性检测 =========="
for f in "${LOG_FILES[@]}"; do
  if [[ -e "$f" ]]; then
    if [[ -r "$f" ]]; then
      echo "OK 可读: $f"
    elif [[ "${AGENT_USE_SUDO:-0}" == "1" ]] && sudo -n true 2>/dev/null; then
      echo "ELEVATED sudo 可读: $f"
    else
      echo "SKIP 无读权限: $f （可设置 AGENT_USE_SUDO=1 且配置免密 sudo 后重试）"
    fi
  else
    echo "MISS 不存在: $f"
  fi
done

echo
echo "========== 关键字扫描（ERROR|FAIL|CRITICAL）最近 400 行内计数 =========="
for f in "${LOG_FILES[@]}"; do
  [[ -e "$f" ]] || continue
  echo "--- $f ---"
  if agent_maybe_sudo_tail 400 "$f" 2>/dev/null | grep -E 'ERROR|FAIL|CRITICAL|error|fail' | head -n 20; then
    :
  else
    agent_maybe_sudo_tail 400 "$f" 2>/dev/null | head -n 5 || echo "(无输出或无法读取)"
  fi
  echo -n "计数: "
  cnt="$(agent_maybe_sudo_tail 2000 "$f" 2>/dev/null | grep -cE 'ERROR|FAIL|CRITICAL|error|fail' || true)"
  echo "${cnt:-0}"
  echo
done

echo "========== awk 按小时粗略聚合（若日志含时间戳前缀） =========="
for f in "${LOG_FILES[@]}"; do
  [[ -e "$f" ]] || continue
  echo "--- $f ---"
  agent_maybe_sudo_tail 800 "$f" 2>/dev/null \
    | awk '/ERROR|FAIL|CRITICAL|error|fail/ {c[$1]++} END {for (k in c) print k, c[k]}' \
    | head -n 15 || true
  echo
done

agent_log "runbook log_audit done"
echo OK
