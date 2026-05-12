"""
Shell 命令黑名单校验（实验模式「近似任意命令」前的最后一道闸）。
无法保证 100% 安全；生产环境禁止使用本模式。
"""
from __future__ import annotations

import re
from typing import List

# 命中任一正则则拒绝执行
_BLOCKLIST: List[str] = [
    r"rm\s+(-[a-zA-Z]*f|--force)\s+.*/\s*$",  # rm -rf /
    r"rm\s+.*/\s*;?",  # rm ... /
    r">\s*/dev/",
    r"dd\s+.*\bof=/dev/",
    r"mkfs\.",
    r":\s*\(\)\s*\{",  # fork bomb 雏形
    r"\beval\s+",
    r"\bexec\s+",
    r"curl\s+[^|]*\|\s*(ba)?sh",
    r"wget\s+[^|]*\|\s*(ba)?sh",
    r"base64\s+[^|]*\|\s*(ba)?sh",
    r"\bchmod\s+-R\s+777\s+/",
    r"\bchown\s+-R\s+",
    r">\s*/etc/",
    r">\s*/sys/",
    r">\s*/proc/",
    r"\$\(\s*curl",
    r"`\s*curl",
]


def assert_shell_allowed(cmd: str, *, max_len: int = 4000) -> None:
    if not isinstance(cmd, str):
        raise ValueError("命令必须为字符串")
    c = cmd.strip()
    if not c:
        raise ValueError("命令不能为空")
    if len(c) > max_len:
        raise ValueError(f"命令长度不能超过 {max_len}")
    if "\x00" in c:
        raise ValueError("禁止空字节")
    # 仅允许单行（防多语句滥用）；允许分号链但仍在黑名单约束下
    if "\n" in c or "\r" in c:
        raise ValueError("禁止换行（请使用单行命令）")
    low = c.lower()
    for pat in _BLOCKLIST:
        if re.search(pat, low, flags=re.IGNORECASE | re.DOTALL):
            raise ValueError(f"命令命中安全黑名单规则: {pat!r}")


def normalize_roots() -> List[str]:
    import os
    from pathlib import Path

    raw = os.environ.get("AGENT_ALLOWED_ROOTS", "/opt/kcsj:/opt:/tmp")
    roots = []
    for p in raw.split(":"):
        p = p.strip()
        if not p:
            continue
        roots.append(str(Path(p).resolve()))
    home = str(Path.home().resolve())
    if home not in roots:
        roots.append(home)
    return roots


def assert_path_under_roots(path: str, roots: List[str]) -> None:
    from pathlib import Path

    try:
        rp = Path(path).resolve()
    except OSError as e:
        raise ValueError(f"非法路径: {e}") from e
    rs = str(rp)
    for root in roots:
        if rs == root or rs.startswith(root.rstrip("/") + "/"):
            return
    raise ValueError(f"路径不在允许根目录内: {rs} 允许: {roots}")
