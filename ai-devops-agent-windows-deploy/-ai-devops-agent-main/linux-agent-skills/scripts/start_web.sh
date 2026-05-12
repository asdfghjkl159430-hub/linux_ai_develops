#!/usr/bin/env bash
# 启动 Web 控制台（需已安装 Flask，见 requirements-web.txt）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 python3" >&2
  exit 1
fi

VENV="${ROOT}/.venv-web"
if [[ ! -d "$VENV" ]]; then
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install -q -r requirements-web.txt
fi

chmod +x "$ROOT/bin/agent-router.sh" "$ROOT/runbooks"/*.sh 2>/dev/null || true

export AGENT_WEB_HOST="${AGENT_WEB_HOST:-0.0.0.0}"
export AGENT_WEB_PORT="${AGENT_WEB_PORT:-8765}"
echo "启动 Web: http://${AGENT_WEB_HOST}:${AGENT_WEB_PORT}/"
exec "$VENV/bin/python" "$ROOT/web/app.py"
