#!/usr/bin/env bash
# 功能测试入口（课设：测试与验证）
set -uo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==[1] disk_check 冒烟 =="
bash runbooks/disk_check.sh | head -n 25 || true

echo "==[2] proc_health 冒烟 =="
bash runbooks/proc_health.sh | head -n 35 || true

echo "==[3] log_audit 使用临时可读日志 =="
TMPF=$(mktemp)
trap 'rm -f "$TMPF"' EXIT
{
  echo "2026-01-01T00:00:00 INFO start"
  echo "2026-01-01T00:01:00 ERROR disk full simulation"
  echo "2026-01-01T00:02:00 FAIL network timeout"
} >"$TMPF"
chmod 0644 "$TMPF"
AGENT_USE_SUDO=0 bash runbooks/log_audit.sh "$TMPF" | head -n 40 || true

echo "==[4] agent-router 干跑 =="
bash bin/agent-router.sh --dry-run "检查一下磁盘空间"
bash bin/agent-router.sh --dry-run "分析系统日志错误"
bash bin/agent-router.sh --dry-run "看看进程和负载"
bash bin/agent-router.sh --dry-run "检查网络端口和路由"
bash bin/agent-router.sh --dry-run "systemd 失败服务与定时器"
bash bin/agent-router.sh --dry-run "用 iostat 看磁盘 IO"
bash bin/agent-router.sh --dry-run "查看系统版本和内核"

echo "==[5] agent-router 实跑磁盘（截断） =="
bash bin/agent-router.sh "磁盘 df" | head -n 20 || true

echo "==[6] safe_exec 白名单 =="
python3 lib/safe_exec.py list | head -n 15 || true
python3 lib/safe_exec.py run -- df -h | head -n 5 || true
! python3 lib/safe_exec.py run -- rm -rf / >/dev/null 2>&1
echo "(预期拒绝 rm)"

echo "==[7] validate_env =="
bash lib/validate_env.sh | head -n 25 || true

echo "==[8] distro_info =="
bash runbooks/distro_info.sh | head -n 30 || true

echo "==[8b] 新 runbooks 冒烟（截断）==="
bash runbooks/network_diag.sh 2>/dev/null | head -n 20 || true
bash runbooks/systemd_audit.sh 2>/dev/null | head -n 20 || true

echo "==[9] Web API（若已安装 .venv-web）==="
if [[ -x "$ROOT_DIR/.venv-web/bin/python" ]]; then
  export AGENT_TEST_ROOT="$ROOT_DIR"
  "$ROOT_DIR/.venv-web/bin/python" <<'PY'
import importlib.util
import os
import shutil
from pathlib import Path
root = Path(os.environ["AGENT_TEST_ROOT"])
spec = importlib.util.spec_from_file_location("app", root / "web" / "app.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
c = m.app.test_client()
r_tools = c.get("/api/tools")
assert r_tools.status_code == 200 and r_tools.json.get("ok") is True
assert len(r_tools.json.get("catalog", {}).get("runbooks", [])) >= 5
r = c.post("/api/run", json={"query": "检查磁盘空间", "dry_run": True})
assert r.status_code == 200 and r.json.get("ok") is True
r2 = c.post("/api/run", json={"query": "x;y"})
assert r2.status_code == 400
# 自动模式需确认
r3 = c.post("/api/run", json={"mode": "auto", "query": "在tmp目录下创建一个agent_nl_web_test", "confirm_risk": False})
assert r3.status_code == 400
tdir = Path("/tmp/agent_nl_web_test")
if tdir.exists():
    shutil.rmtree(tdir)
r4 = c.post(
    "/api/run",
    json={"mode": "auto", "query": "在tmp目录下创建一个agent_nl_web_test", "confirm_risk": True},
)
assert r4.status_code == 200 and r4.json.get("ok") is True
assert "mkdir" in (r4.json.get("executed") or "")
assert tdir.is_dir()
shutil.rmtree(tdir)
# Shell 黑名单
r5 = c.post("/api/run", json={"mode": "shell", "query": "rm -rf /", "confirm_risk": True})
assert r5.status_code in (400, 500) and r5.json.get("ok") is not True
print("web api OK")
PY
else
  echo "跳过（未找到 .venv-web，可先运行 scripts/start_web.sh 自动生成）"
fi

echo "ALL TESTS DONE"
