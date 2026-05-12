#!/usr/bin/env bash
# 网络诊断：ip / ss / 路由 / 本机连通性（主动调用常见运维工具）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/lib/common.sh"

agent_log "runbook network_diag start"

echo "========== 主机名与 DNS（getent / resolvectl，若存在） =========="
hostname -f 2>/dev/null || hostname || true
if have_cmd getent; then
  echo "hosts 中本机: $(getent hosts "$(hostname)" 2>/dev/null | head -n 1 || true)"
fi
if have_cmd resolvectl; then
  resolvectl status 2>/dev/null | head -n 25 || true
fi

echo
echo "========== 接口摘要（ip -br link / addr） =========="
if have_cmd ip; then
  ip -br link 2>/dev/null || true
  echo
  ip -br a 2>/dev/null || ip addr 2>/dev/null | head -n 40 || true
else
  echo "ip 未安装，跳过"
fi

echo
echo "========== 默认路由 =========="
if have_cmd ip; then
  ip route 2>/dev/null | head -n 20 || true
elif have_cmd route; then
  route -n 2>/dev/null | head -n 15 || true
fi

echo
echo "========== 监听端口（ss -tuln，截断） =========="
if have_cmd ss; then
  ss -tulnH 2>/dev/null | head -n 50 || ss -tuln 2>/dev/null | head -n 50 || true
else
  echo "ss 未安装，可安装 iproute2"
fi

echo
echo "========== 本机环回连通（ping -c 1 127.0.0.1） =========="
if have_cmd ping; then
  ping -c 1 -W 1 127.0.0.1 2>/dev/null || ping -c 1 127.0.0.1 2>/dev/null || true
else
  echo "ping 未安装"
fi

echo
echo "========== 统计摘要（ss -s，若支持） =========="
if have_cmd ss; then
  ss -s 2>/dev/null || true
fi

agent_log "runbook network_diag done"
echo OK
