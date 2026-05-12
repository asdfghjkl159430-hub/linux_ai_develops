#!/usr/bin/env python3
"""
白名单方式调用外部命令（满足「Python 调用 Linux」）。
仅允许预置命令前缀，防止任意命令注入。
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from typing import List, Sequence

ALLOWED_PREFIXES: Sequence[str] = (
    "df ",
    "df\t",
    "uptime",
    "ps ",
    "vmstat ",
    "findmnt ",
    "systemctl ",
    "grep ",
    "awk ",
    "tail ",
    "head ",
    "cat ",
    "sort ",
    "uniq ",
    "wc ",
    "ss ",
    "ip ",
    "lsblk ",
    "iostat ",
    "ping ",
)


def is_allowed(argv: List[str]) -> bool:
    if not argv:
        return False
    bin_name = argv[0]
    if shutil.which(bin_name) is None:
        return False
    if bin_name in ("uptime", "nproc"):
        return True
    joined = " ".join(argv)
    if bin_name == "df":
        return True
    return any(joined.startswith(p.strip()) for p in ALLOWED_PREFIXES)


def cmd_run(args: argparse.Namespace) -> int:
    argv = list(args.cmd or [])
    if argv and argv[0] == "--":
        argv = argv[1:]
    if not argv:
        print("用法: safe_exec.py run -- <cmd> [args...]", file=sys.stderr)
        return 2
    if not is_allowed(argv):
        print(f"拒绝执行（不在白名单）: {argv!r}", file=sys.stderr)
        return 1
    try:
        p = subprocess.run(argv, check=False, timeout=int(args.timeout))
        return int(p.returncode)
    except subprocess.TimeoutExpired:
        print("命令超时", file=sys.stderr)
        return 124


def cmd_list(_: argparse.Namespace) -> int:
    print("允许的命令前缀 / 特例：")
    for p in ALLOWED_PREFIXES:
        print(" ", repr(p))
    print("  特例二进制: uptime, nproc")
    print("  df: 允许任意参数组合（仍须系统存在 df）")
    return 0


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description="safe_exec: 白名单 subprocess 执行（课设示例）")
    sp = ap.add_subparsers(dest="action", required=True)

    p_run = sp.add_parser("run", help="执行一条白名单命令")
    p_run.add_argument("cmd", nargs=argparse.REMAINDER, help="命令及参数")
    p_run.add_argument("--timeout", type=int, default=60, help="超时秒数")
    p_run.set_defaults(func=cmd_run)

    p_ls = sp.add_parser("list", help="列出白名单规则")
    p_ls.set_defaults(func=cmd_list)

    return ap


def main() -> None:
    ap = build_parser()
    args = ap.parse_args()
    rc = int(args.func(args))
    raise SystemExit(rc)


if __name__ == "__main__":
    main()
