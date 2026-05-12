"""
中文自然语言 → 单行安全 Shell（受路径白名单约束）。
用于「自动模式」：例如「在 opt 目录下创建一个 ceshi 文件夹」→ mkdir -p /opt/ceshi
"""
from __future__ import annotations

import re
from pathlib import Path
from typing import Optional, Tuple

from shell_guard import assert_path_under_roots, normalize_roots


def _norm_base(s: str) -> str:
    s = s.strip().strip("「」\"'“”")
    for suf in ("目录下", "文件夹下", "目录", "文件夹"):
        if s.endswith(suf) and len(s) > len(suf):
            s = s[: -len(suf)]
            break
    low = s.lower()
    mapping = {
        "opt": "/opt",
        "/opt": "/opt",
        "tmp": "/tmp",
        "/tmp": "/tmp",
        "临时目录": "/tmp",
        "用户主目录": str(Path.home()),
        "主目录": str(Path.home()),
        "家目录": str(Path.home()),
        "~": str(Path.home()),
    }
    if low in mapping:
        return mapping[low]
    if s.startswith("~/"):
        return str(Path.home() / s[2:])
    if s.startswith("/"):
        return str(Path(s).resolve())
    # 相对路径 → 用户主目录下
    return str((Path.home() / s).resolve())


def translate_nl(q: str) -> Tuple[Optional[str], str]:
    """
    若可识别则返回 (shell_line, explanation)；否则 (None, 原因)。
    """
    q0 = q.strip()
    if not q0:
        return None, "空输入"
    roots = normalize_roots()

    m = re.search(
        r"在\s*([^\s，,。.]+?)\s*(目录|文件夹)\s*下\s*创建\s*(?:一个|几个|若干|多个)?\s*([\w.\-]+)\s*(文件夹|目录)?",
        q0,
    )
    if m:
        base = _norm_base(m.group(1))
        name = m.group(3).strip().strip("「」\"'“”")
        if not re.match(r"^[\w.\-]+$", name):
            return None, "文件夹名仅允许字母数字下划线横线点"
        target = str(Path(base) / name)
        assert_path_under_roots(str(Path(base).resolve()), roots)
        assert_path_under_roots(target, roots)
        return f'mkdir -p -- "{target}"', f"识别为：在 {base} 下创建目录 {name}"

    m2 = re.search(
        r"创建\s*(?:一个|几个|若干|多个)?\s*(文件夹|目录)?\s*([\w.\-]+)\s*(文件夹|目录)?\s*在\s*([^\s，,。.]+?)\s*(目录|文件夹)\s*下",
        q0,
    )
    if m2:
        name = m2.group(2).strip().strip("「」\"'“”")
        base = _norm_base(m2.group(4))
        if not re.match(r"^[\w.\-]+$", name):
            return None, "文件夹名不合法"
        target = str(Path(base) / name)
        assert_path_under_roots(str(Path(base).resolve()), roots)
        assert_path_under_roots(target, roots)
        return f'mkdir -p -- "{target}"', f"识别为：创建目录 {name} 于 {base}"

    m3 = re.search(r"列出\s*(目录|文件夹)?\s*(.+)", q0)
    if m3:
        path = _norm_base(m3.group(2).strip())
        assert_path_under_roots(path, roots)
        return f'ls -la -- "{path}"', f"识别为：列出目录 {path}"

    m4 = re.search(r"查看文件\s*(.+)", q0)
    if m4:
        path = _norm_base(m4.group(1).strip())
        assert_path_under_roots(path, roots)
        return f'head -n 200 -- "{path}"', f"识别为：查看文件前 200 行 {path}"

    return None, "未匹配内置中文意图，可改用「Shell 实验模式」或「安全巡检」"


def looks_like_direct_shell(q: str) -> bool:
    """若整句像一行 shell（以常见命令开头），交给 shell_guard + bash -lc。"""
    q0 = q.strip()
    if not q0 or len(q0) > 4000:
        return False
    first = re.split(r"\s+", q0, maxsplit=1)[0].lower()
    starters = (
        "ls",
        "cd",
        "pwd",
        "mkdir",
        "rmdir",
        "rm",
        "cp",
        "mv",
        "touch",
        "cat",
        "head",
        "tail",
        "grep",
        "awk",
        "sed",
        "find",
        "du",
        "df",
        "ps",
        "top",
        "echo",
        "date",
        "hostname",
        "whoami",
        "id",
        "uname",
        "wc",
        "sort",
        "uniq",
        "cut",
        "chmod",
        "chown",
        "tree",
        "sudo",
        "env",
        "which",
        "ping",
        "ss",
        "ip",
        "curl",
        "wget",
    )
    return any(first == s or first.startswith(s + ":") for s in starters)
