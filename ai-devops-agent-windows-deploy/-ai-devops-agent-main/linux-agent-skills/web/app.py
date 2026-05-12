#!/usr/bin/env python3
"""
Linux Agent Skills — Web 控制台
- mode=skills：受控巡检（agent-router.sh）
- mode=auto：中文意图解析 → 单行安全命令；若未识别且像 shell，则按实验 shell 执行（需 confirm_risk）
- mode=shell：实验模式，单行 shell（黑名单过滤 + 需 confirm_risk）
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path

from flask import Flask, jsonify, render_template, request

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ROUTER = PROJECT_ROOT / "bin" / "agent-router.sh"
LIB_DIR = str(PROJECT_ROOT / "lib")
if LIB_DIR not in sys.path:
    sys.path.insert(0, LIB_DIR)

from intent_nl import looks_like_direct_shell, translate_nl  # noqa: E402
from shell_guard import assert_shell_allowed  # noqa: E402

app = Flask(
    __name__,
    template_folder=str(Path(__file__).parent / "templates"),
    static_folder=str(Path(__file__).parent / "static"),
)
app.config["MAX_CONTENT_LENGTH"] = 512 * 1024


def _sanitize_skills_query(q: str) -> str:
    """安全巡检：禁止 shell 元字符。"""
    if not isinstance(q, str):
        raise ValueError("query 必须为字符串")
    q = q.strip()
    if not q:
        raise ValueError("query 不能为空")
    if len(q) > 512:
        raise ValueError("query 长度不能超过 512 字符")
    if re.search(r"[`\$;|&\n\r\x00<>]", q):
        raise ValueError("query 包含不允许的字符（安全巡检模式禁止 shell 元字符）")
    return q


def _sanitize_auto_text(q: str) -> str:
    """自动模式：允许中文标点，仍禁止反引号、美元、换行。"""
    if not isinstance(q, str):
        raise ValueError("query 必须为字符串")
    q = q.strip()
    if not q:
        raise ValueError("query 不能为空")
    if len(q) > 2000:
        raise ValueError("query 长度不能超过 2000 字符")
    if re.search(r"[`\$\n\r\x00]", q):
        raise ValueError("自动模式禁止反引号、美元符与换行")
    return q


def _sanitize_shell_line(q: str) -> str:
    """实验 shell：单行，长度限制；具体危险模式由 shell_guard 拦截。"""
    if not isinstance(q, str):
        raise ValueError("command 必须为字符串")
    q = q.strip()
    if not q:
        raise ValueError("command 不能为空")
    if len(q) > 4000:
        raise ValueError("命令长度不能超过 4000 字符")
    if "\n" in q or "\r" in q or "\x00" in q:
        raise ValueError("禁止换行与空字节")
    return q


def _run_router(query: str, *, dry_run: bool, use_sudo: bool) -> subprocess.CompletedProcess[str]:
    if not ROUTER.is_file():
        raise RuntimeError(f"未找到路由脚本: {ROUTER}")
    cmd = ["bash", str(ROUTER)]
    if dry_run:
        cmd.append("--dry-run")
    cmd.append(query)
    env = os.environ.copy()
    env["AGENT_USE_SUDO"] = "1" if use_sudo else "0"
    env.setdefault("AGENT_LOG_DIR", str(PROJECT_ROOT / "logs"))
    return subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=300,
        cwd=str(PROJECT_ROOT),
        env=env,
    )


def _run_bash_lc(
    shell_line: str,
    *,
    use_sudo: bool,
    timeout: int = 300,
) -> subprocess.CompletedProcess[str]:
    assert_shell_allowed(shell_line)
    env = os.environ.copy()
    env["AGENT_USE_SUDO"] = "1" if use_sudo else "0"
    cwd = os.environ.get("AGENT_SHELL_CWD", str(PROJECT_ROOT))
    return subprocess.run(
        ["bash", "-lc", shell_line],
        capture_output=True,
        text=True,
        timeout=timeout,
        cwd=cwd,
        env=env,
    )


@app.get("/")
def index():
    return render_template("index.html")


@app.get("/api/tools")
def api_tools():
    """列出已接入的运维 runbook 与关联工具（供前端 / 外部 Agent 编排）。"""
    cat = PROJECT_ROOT / "lib" / "tools_catalog.json"
    if not cat.is_file():
        return jsonify({"ok": False, "error": "未找到 tools_catalog.json"}), 500
    try:
        data = json.loads(cat.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    return jsonify({"ok": True, "catalog": data})


@app.post("/api/run")
def api_run():
    data = request.get_json(silent=True) or {}
    mode = str(data.get("mode", "skills")).strip().lower()
    dry_run = bool(data.get("dry_run"))
    use_sudo = bool(data.get("use_sudo"))
    confirm_risk = bool(data.get("confirm_risk"))

    try:
        if mode == "skills":
            query = _sanitize_skills_query(str(data.get("query", "")))
            proc = _run_router(query, dry_run=dry_run, use_sudo=use_sudo)
            return jsonify(
                {
                    "ok": True,
                    "mode": "skills",
                    "query": query,
                    "dry_run": dry_run,
                    "use_sudo": use_sudo,
                    "returncode": proc.returncode,
                    "stdout": proc.stdout or "",
                    "stderr": proc.stderr or "",
                }
            )

        if mode not in ("auto", "shell"):
            return jsonify({"ok": False, "error": f"未知 mode: {mode}"}), 400

        if mode in ("auto", "shell") and not confirm_risk:
            return jsonify(
                {
                    "ok": False,
                    "error": "自动/Shell 模式需勾选「我已知晓风险并确认执行」",
                }
            ), 400

        raw = str(data.get("query", ""))

        if mode == "shell":
            line = _sanitize_shell_line(raw)
            assert_shell_allowed(line)
            proc = _run_bash_lc(line, use_sudo=use_sudo)
            return jsonify(
                {
                    "ok": True,
                    "mode": "shell",
                    "executed": line,
                    "explanation": "实验 Shell：已做黑名单校验（非完全任意）",
                    "use_sudo": use_sudo,
                    "returncode": proc.returncode,
                    "stdout": proc.stdout or "",
                    "stderr": proc.stderr or "",
                }
            )

        # auto
        text = _sanitize_auto_text(raw)
        cmd, expl = translate_nl(text)
        if cmd is None and looks_like_direct_shell(text):
            cmd = text
            expl = "未命中中文模板，按「一行 Shell」执行（仍经黑名单校验）"
        if cmd is None:
            q2 = _sanitize_skills_query(text)
            proc = _run_router(q2, dry_run=dry_run, use_sudo=use_sudo)
            return jsonify(
                {
                    "ok": True,
                    "mode": "auto",
                    "fallback": "skills",
                    "query": q2,
                    "dry_run": dry_run,
                    "use_sudo": use_sudo,
                    "returncode": proc.returncode,
                    "stdout": proc.stdout or "",
                    "stderr": proc.stderr or "",
                    "note": expl,
                }
            )

        assert_shell_allowed(cmd)
        proc = _run_bash_lc(cmd, use_sudo=use_sudo)
        return jsonify(
            {
                "ok": True,
                "mode": "auto",
                "executed": cmd,
                "explanation": expl,
                "use_sudo": use_sudo,
                "returncode": proc.returncode,
                "stdout": proc.stdout or "",
                "stderr": proc.stderr or "",
            }
        )

    except ValueError as e:
        return jsonify({"ok": False, "error": str(e)}), 400
    except subprocess.TimeoutExpired:
        return jsonify({"ok": False, "error": "执行超时"}), 504
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


def main() -> None:
    host = os.environ.get("AGENT_WEB_HOST", "0.0.0.0")
    port = int(os.environ.get("AGENT_WEB_PORT", "8765"))
    debug = os.environ.get("AGENT_WEB_DEBUG", "0") == "1"
    print(f"Linux Agent Web 控制台: http://{host}:{port}/")
    app.run(host=host, port=port, debug=debug, threaded=True)


if __name__ == "__main__":
    main()
