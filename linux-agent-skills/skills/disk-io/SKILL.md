# Skill: 磁盘 I/O 与块设备

## 触发关键词
iostat、磁盘 IO、块设备、lsblk、await、io 延迟、sar 等。

## 行为
执行 `runbooks/disk_io.sh`，调用：
- `lsblk`
- `iostat`（需 `sysstat` 包）
- `sar`（可选）

## 说明
未安装 `sysstat` 时脚本会提示安装方式，不视为失败。
