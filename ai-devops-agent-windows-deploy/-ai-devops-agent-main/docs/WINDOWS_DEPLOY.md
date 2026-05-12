# Windows 主机部署指南（控制台）+ Linux 作为被 SSH 运维机

## 0. 解压说明

若压缩包内顶层文件夹名为 **`-ai-devops-agent-main`**（以 `-` 开头），在 Windows 资源管理器中可能不便于操作，**建议解压后重命名为** `ai-devops-agent`，以下路径均按重命名后为例。

## 1. 本包内容

- Spring Boot 后端源码（`pom.xml`、Java 源码）
- 子目录 **`linux-agent-skills/`**：需同步到 **被管的 Linux 机** 上（见下文第 5 节）
- 数据库脚本：`sql/init.sql`

## 2. Windows 环境要求

| 软件 | 说明 |
|------|------|
| **JDK 17+** | 安装后 `java -version` 应为 17 或更高 |
| **Maven 3.8+** | 安装后 `mvn -v` 可用 |
| **MySQL 8.0** | 本地安装，创建库并执行 `sql/init.sql` |

## 3. 解压与编译

```bat
cd ai-devops-agent
copy sql\init.sql 到 MySQL 客户端执行，或：
mysql -u root -p < sql\init.sql
```

修改 `src\main\resources\application.yml`：

- `spring.datasource.url` / `username` / `password`：指向你本机 MySQL
- `llm.api-key`、`llm.base-url`、`llm.model`：填写你的大模型（如通义千问）

编译打包：

```bat
mvn -DskipTests package
```

生成可执行包路径：

```text
target\devops-agent-1.0.0.jar
```

## 4. 启动（Windows）

双击项目根目录下的 **`start-devops-agent.bat`**，或命令行：

```bat
java -jar target\devops-agent-1.0.0.jar
```

默认端口 **8080**。浏览器或 Postman 访问：`http://localhost:8080/api/`

若防火墙弹出，请允许 **专用网络** 访问 Java。

## 5. 把「当前这台 Linux」登记为被运维机

在 **Linux 被管机**上：

1. 开启 **SSH 服务**（`sshd`），并保证与 Windows **网络互通**（同一局域网或公网 IP + 安全组放行 22）。
2. 部署巡检脚本（与课设一致），例如：

```bash
sudo mkdir -p /opt/linux-agent-skills
sudo cp -r /path/to/ai-devops-agent/linux-agent-skills/* /opt/linux-agent-skills/
sudo chmod +x /opt/linux-agent-skills/bin/agent-router.sh /opt/linux-agent-skills/runbooks/*.sh
```

3. 在 **Windows 上调用 API** 添加服务器（把 `host` 换成 **Linux 的实际 IP**）：

```bat
curl -X POST http://localhost:8080/api/servers -H "Content-Type: application/json" -d "{\"name\":\"my-linux\",\"host\":\"192.168.x.x\",\"port\":22,\"username\":\"你的用户\",\"password\":\"密码\",\"osType\":\"Linux\"}"
```

4. 测试连接：`POST http://localhost:8080/api/servers/1/test`（id 以实际返回为准）。

4.1 快速诊断（推荐，参数为 **serverId**）：

```bat
curl -X POST http://localhost:8080/api/servers/1/diagnostics
```

（兼容旧接口：`POST http://localhost:8080/api/tasks/1/diagnostics`，路径中的数字同样表示 **serverId**。）

5. 执行任务示例（`linux_skills` 走 agent-router）：

```bat
curl -X POST http://localhost:8080/api/tasks -H "Content-Type: application/json" -d "{\"userInput\":\"检查一下磁盘空间\",\"serverId\":1,\"agentType\":\"linux_skills\"}"
```

若在 Linux 上把脚本放在别的目录，请在 **`application.yml`** 中设置：

```yaml
linux-skills:
  remote-path: /你的路径/linux-agent-skills
```

或环境变量：`LINUX_SKILLS_REMOTE_PATH`。

## 6. 常见问题

- **SSH 连不上**：检查 Linux 防火墙 `ufw`/云安全组、sshd 是否监听 22、用户名密码是否正确。  
- **agent-router 报找不到**：确认 `remote-path` 与 Linux 上实际路径一致，且 `bin/agent-router.sh` 已 `chmod +x`。  
- **数据库错误**：确认已执行 `init.sql`，且 `application.yml` 中库名、账号密码正确。
