#!/usr/bin/env bash
# 公共函数：日志、颜色、退出码约定（被各 runbook source）
# shellcheck shell=bash

set -euo pipefail

export LC_ALL=C

: "${AGENT_LOG_DIR:=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/logs}"
mkdir -p "$AGENT_LOG_DIR"
chmod 0777 "$AGENT_LOG_DIR" 2>/dev/null || true

agent_ts() { date +%Y%m%d-%H%M%S; }

agent_log() {
  local msg="$*"
  echo "[$(date -Iseconds)] $msg" | tee -a "${AGENT_LOG_DIR}/agent-$(agent_ts).log" 2>/dev/null || echo "[$(date -Iseconds)] $msg"
}

die() { agent_log "ERROR: $*"; echo "ERROR: $*" >&2; exit 1; }

have_cmd() { command -v "$1" >/dev/null 2>&1; }

# 若 AGENT_USE_SUDO=1 且本机已配置免密 sudo，则用 sudo 读取不可读文件（课设演示可选）
agent_maybe_sudo_cat() {
  local f="$1"
  if [[ -r "$f" ]]; then
    cat -- "$f"
    return 0
  fi
  if [[ "${AGENT_USE_SUDO:-0}" == "1" ]] && sudo -n true 2>/dev/null; then
    agent_log "elevated read via sudo: $f"
    sudo -n cat -- "$f" 2>/dev/null && return 0
  fi
  return 1
}

agent_maybe_sudo_tail() {
  local n="$1"
  local f="$2"
  if [[ -r "$f" ]]; then
    tail -n "$n" -- "$f"
    return 0
  fi
  if [[ "${AGENT_USE_SUDO:-0}" == "1" ]] && sudo -n true 2>/dev/null; then
    agent_log "elevated tail via sudo: $f"
    sudo -n tail -n "$n" -- "$f" 2>/dev/null && return 0
  fi
  return 1
}
