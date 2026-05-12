# 贡献与协作约定

## 提交规范

- `feat:` 新功能  
- `fix:` 修复  
- `docs:` 文档  
- `test:` 测试  

## Code Review（课堂轻量）

合并到 `main` 前至少一名其他成员浏览 diff，重点检查：

1. 是否引入未转义的用户输入拼接进 `bash -c`  
2. `sudo` 使用是否被默认开启  

## 配置 Git 作者（5 人组每位成员本地设置）

```bash
git config user.name "成员X姓名"
git config user.email "memberX@school.edu"
```
