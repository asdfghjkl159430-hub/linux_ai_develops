# lib/parsers.awk — 日志行轻量解析（课设 awk 扩展示例）
# 用法示例: tail -n 200 file | awk -f lib/parsers.awk
BEGIN {
  err = 0; warn = 0; info = 0; other = 0;
}
{
  line = $0;
  if (line ~ /ERROR|ERR|FATAL|CRITICAL|失败/) { err++; bucket["ERROR"]++; next; }
  if (line ~ /WARN|WARNING|警告/) { warn++; bucket["WARN"]++; next; }
  if (line ~ /INFO|信息/) { info++; bucket["INFO"]++; next; }
  other++;
}
END {
  print "=== parsers.awk 汇总 ===";
  print "ERROR级:", err;
  print "WARN级:", warn;
  print "INFO级:", info;
  print "其他行:", other;
  print "--- bucket TOP ---";
  n = asorti(bucket, keys);
  for (i = 1; i <= n; i++) {
    k = keys[i];
    print k, bucket[k];
  }
}
