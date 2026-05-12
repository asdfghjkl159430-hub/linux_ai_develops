#!/usr/bin/env bash
# 打 zip 包供拷贝到 Windows（排除构建产物与大文件）
# 注意：目录名若以 - 开头，zip 须使用 ./目录名 形式，否则会当成命令行选项。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/../ai-devops-agent-windows-deploy.zip}"
cd "$(dirname "$ROOT")"
NAME="$(basename "$ROOT")"
rm -f "$OUT"
zip -r "$OUT" "./$NAME" \
  -x "./$NAME/target/*" \
  -x "./$NAME/linux-agent-skills/.venv-web/*" \
  -x "./$NAME/linux-agent-skills/logs/*"
echo "已生成: $OUT"
ls -lh "$OUT"
