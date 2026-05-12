#!/usr/bin/env bash
# 小型 I/O 与字符串工具（被扩展 runbook source，增加代码规模与可复用性）
# shellcheck shell=bash

io_trim() {
  local s="$1"
  echo "$s" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

io_lower() {
  echo "$1" | tr '[:upper:]' '[:lower:]'
}

io_has() {
  local hay="$1"
  local needle="$2"
  [[ "$hay" == *"$needle"* ]]
}

io_join_lines() {
  local sep="$1"
  shift
  local first=1
  local a
  for a in "$@"; do
    if [[ $first -eq 1 ]]; then
      printf '%s' "$a"
      first=0
    else
      printf '%s%s' "$sep" "$a"
    fi
  done
  printf '\n'
}

io_file_nonempty() {
  local f="$1"
  [[ -s "$f" ]]
}

io_safe_filename() {
  echo "$1" | sed 's/[^a-zA-Z0-9._-]/_/g' | head -c 120
}

io_repeat_char() {
  local ch="$1"
  local n="${2:-1}"
  local i
  for ((i = 0; i < n; i++)); do
    printf '%s' "$ch"
  done
  printf '\n'
}

io_banner() {
  local title="$1"
  local w="${2:-60}"
  local line
  line="$(io_repeat_char '#' "$w")"
  echo "$line"
  echo "# $title"
  echo "$line"
}

io_now_epoch() {
  date +%s
}

io_human_bytes() {
  local b="$1"
  awk -v b="$b" 'BEGIN{
    split("B KB MB GB TB",u," ");
    x=b+0;
    i=1;
    while(x>1024 && i<5){x/=1024;i++}
    printf("%.2f %s\n", x, u[i]);
  }'
}

io_count_lines() {
  local f="$1"
  [[ -r "$f" ]] || return 1
  wc -l <"$f" | tr -d ' '
}

io_tail_safe() {
  local n="$1"
  local f="$2"
  [[ -r "$f" ]] || return 1
  tail -n "$n" -- "$f"
}

io_grep_count() {
  local pat="$1"
  local f="$2"
  [[ -r "$f" ]] || return 1
  grep -cE "$pat" "$f" || true
}

io_awk_top_ip() {
  local f="$1"
  [[ -r "$f" ]] || return 1
  awk '{print $1}' "$f" | sort | uniq -c | sort -nr | head -n 10
}

io_is_int() {
  local v="$1"
  [[ "$v" =~ ^-?[0-9]+$ ]]
}

io_clamp() {
  local v="$1"
  local lo="$2"
  local hi="$3"
  awk -v v="$v" -v lo="$lo" -v hi="$hi" 'BEGIN{
    if (v<lo) v=lo;
    if (v>hi) v=hi;
    print v;
  }'
}

io_escape_sed() {
  echo "$1" | sed 's/[[\.*^$()+?{|]/\\&/g'
}

io_tmpfile() {
  mktemp "${TMPDIR:-/tmp}/agent-io-XXXXXX"
}

io_dir_size_mb() {
  local d="$1"
  [[ -d "$d" ]] || return 1
  du -sm -- "$d" 2>/dev/null | awk '{print $1}'
}

io_list_nonempty_files() {
  local d="$1"
  find "$d" -maxdepth 1 -type f -size +0c 2>/dev/null | head -n 50
}

io_shell_ok() {
  [[ -n "${BASH_VERSION:-}" ]]
}

io_assert_file() {
  local f="$1"
  [[ -e "$f" ]] || return 1
}

io_assert_dir() {
  local d="$1"
  [[ -d "$d" ]] || return 1
}

io_read_kv_file() {
  local f="$1"
  local key="$2"
  [[ -r "$f" ]] || return 1
  grep -E "^${key}=" "$f" | head -n 1 | cut -d= -f2- | tr -d '"' || true
}

io_truncate_middle() {
  local s="$1"
  local max="${2:-40}"
  local n=${#s}
  if (( n <= max )); then
    echo "$s"
    return
  fi
  local keep=$((max / 2 - 2))
  echo "${s:0:keep}...${s: -keep}"
}

io_split_csv_line() {
  local line="$1"
  echo "$line" | awk -F, '{for(i=1;i<=NF;i++) print i, $i}'
}

io_strip_crlf() {
  tr -d '\r'
}

io_bool01() {
  local v="${1,,}"
  case "$v" in
    1|true|yes|on) echo 1 ;;
    *) echo 0 ;;
  esac
}

io_quote_for_grep() {
  printf '%s' "$1" | sed 's/[].[^$\\*+?{}|()]/\\&/g'
}
