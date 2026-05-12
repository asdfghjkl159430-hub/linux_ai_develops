@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist "target\devops-agent-1.0.0.jar" (
  echo [错误] 未找到 target\devops-agent-1.0.0.jar
  echo 请先在项目根目录执行: mvn -DskipTests package
  pause
  exit /b 1
)
echo 启动 AI DevOps Agent: http://localhost:8080/
echo 按 Ctrl+C 停止
java -jar "target\devops-agent-1.0.0.jar"
endlocal
