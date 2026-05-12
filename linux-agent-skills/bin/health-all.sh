#!/usr/bin/env bash
# 顺序执行全部 runbook（用于一次性巡检/演示）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export AGENT_LOG_DIR="${AGENT_LOG_DIR:-$ROOT_DIR/logs}"
mkdir -p "$AGENT_LOG_DIR"
chmod 0777 "$AGENT_LOG_DIR" 2>/dev/null || true

OUT="$AGENT_LOG_DIR/health-all-$(date +%Y%m%d-%H%M%S).log"
{
  echo "#### disk_check ####"
  bash "$ROOT_DIR/runbooks/disk_check.sh"
  echo
  echo "#### proc_health ####"
  bash "$ROOT_DIR/runbooks/proc_health.sh"
  echo
  echo "#### log_audit (default paths) ####"
  AGENT_USE_SUDO="${AGENT_USE_SUDO:-0}" bash "$ROOT_DIR/runbooks/log_audit.sh" || true
} 2>&1 | tee "$OUT"
echo "已写入: $OUT"
