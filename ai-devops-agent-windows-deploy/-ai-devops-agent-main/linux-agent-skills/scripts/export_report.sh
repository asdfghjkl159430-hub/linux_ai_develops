#!/usr/bin/env bash
# 若已安装 pandoc，可将 Markdown 报告导出为 PDF
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MD="$ROOT/docs/report/课程项目报告.md"
OUT="$ROOT/docs/report/课程项目报告.pdf"
if command -v pandoc >/dev/null 2>&1; then
  pandoc "$MD" -o "$OUT" --pdf-engine=xelatex -V CJKmainfont="Noto Sans CJK SC" 2>/dev/null || pandoc "$MD" -o "$OUT"
  echo "OK: $OUT"
else
  echo "未检测到 pandoc，请安装后重试，或使用 Word 打开 Markdown 另存为 PDF。"
  exit 1
fi
