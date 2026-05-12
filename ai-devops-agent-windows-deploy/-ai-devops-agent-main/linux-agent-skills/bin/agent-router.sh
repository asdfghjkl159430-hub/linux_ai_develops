#!/usr/bin/env bash
# Agent 路由：自然语言/关键词 -> runbook（课设核心：执行逻辑编排）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

usage() {
  cat <<'EOF'
用法:
  agent-router.sh "检查磁盘空间"
  agent-router.sh --dry-run "分析日志"
  echo "进程负载" | agent-router.sh

可识别意图（关键词示例）:
  磁盘/空间/df、磁盘IO/iostat/lsblk、日志、网络/端口/ss、
  systemd/服务/定时器、系统版本/内核/发行版、进程/负载/cpu

环境变量:
  AGENT_USE_SUDO=1   日志 runbook 在免密 sudo 可用时提升读权限
  AGENT_LOG_DIR      日志目录（默认项目下 logs/）
EOF
}

DRY=0
QUERY=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --dry-run) DRY=1; shift ;;
    *) QUERY+=("$1"); shift ;;
  esac
done

q="${QUERY[*]:-}"
if [[ -z "${q// }" ]] && ! tty -s; then
  q="$(cat || true)"
fi
if [[ -z "${q// }" ]]; then
  usage
  exit 2
fi

agent_log "router query=$q dry=$DRY"

norm() { echo "$1" | tr '[:upper:]' '[:lower:]'; }
q_lc="$(norm "$q")"

pick_runbook() {
  # 顺序：先匹配更具体的意图，再匹配宽泛的
  if echo "$q_lc" | grep -Eq '日志|log|auth|syslog|journal|审计|error|分析日志'; then
    echo log
    return
  fi
  if echo "$q_lc" | grep -Eq '网络|端口|socket|监听|路由|ping|连通|dns|防火墙|网卡|nft|iptables|tcp|udp|ss |ip route|丢包'; then
    echo network
    return
  fi
  if echo "$q_lc" | grep -Eq 'systemd|服务状态|失败单元|开机自启|定时器|timer|单元\.service|systemctl|查看服务|服务列表'; then
    echo systemd
    return
  fi
  if echo "$q_lc" | grep -Eq 'iostat|io延迟|await|lsblk|块设备|磁盘io|磁盘.*io|读写延迟|sar |磁盘性能'; then
    echo disk_io
    return
  fi
  if echo "$q_lc" | grep -Eq '磁盘|空间|df|inode|挂载|mount'; then
    echo disk
    return
  fi
  if echo "$q_lc" | grep -Eq '发行|内核|版本|主机名|hostname|os-release|系统版本|系统信息|distro|uname|发行版'; then
    echo sysinfo
    return
  fi
  if echo "$q_lc" | grep -Eq '进程|负载|cpu|内存|mem|top|vmstat|load|占用.*高'; then
    echo proc
    return
  fi
  echo ""
}

rb="$(pick_runbook || true)"
if [[ -z "$rb" ]]; then
  echo "未识别意图，请包含关键词：磁盘、日志、网络、systemd/服务、磁盘IO、系统版本、进程/负载 等" >&2
  exit 3
fi

case "$rb" in
  disk)     TARGET="$ROOT_DIR/runbooks/disk_check.sh" ;;
  disk_io)  TARGET="$ROOT_DIR/runbooks/disk_io.sh" ;;
  log)      TARGET="$ROOT_DIR/runbooks/log_audit.sh" ;;
  network)  TARGET="$ROOT_DIR/runbooks/network_diag.sh" ;;
  systemd)  TARGET="$ROOT_DIR/runbooks/systemd_audit.sh" ;;
  sysinfo)  TARGET="$ROOT_DIR/runbooks/distro_info.sh" ;;
  proc)     TARGET="$ROOT_DIR/runbooks/proc_health.sh" ;;
  *) die "internal" ;;
esac

TS="$(agent_ts)"
OUT_LOG="${AGENT_LOG_DIR:-$ROOT_DIR/logs}/router-${TS}.log"
mkdir -p "$(dirname "$OUT_LOG")"
chmod 0777 "$(dirname "$OUT_LOG")" 2>/dev/null || true

if [[ "$DRY" == "1" ]]; then
  echo "[dry-run] 将执行: bash $TARGET"
  exit 0
fi

echo "==> 选中 runbook: $TARGET"
set +e
bash "$TARGET" 2>&1 | tee "$OUT_LOG"
ec="${PIPESTATUS[0]}"
set -e
agent_log "router finished exit=$ec log=$OUT_LOG"
exit "$ec"
