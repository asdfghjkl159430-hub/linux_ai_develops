@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

REM 一键启动：phpStudy MySQL 5.7 + AI DevOps Agent（需已 mvn package）
REM MySQL 路径若不同请改下面三行。

set MYSQL_EXE=F:\phpstudy\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysqld.exe
set MYSQL_INI=F:\phpstudy\phpstudy_pro\Extensions\MySQL5.7.26\my.ini
set MYSQL_CLI=F:\phpstudy\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe
set MYSQL_USER=root
set MYSQL_PWD=root

cd /d "%~dp0"

if not exist "target\devops-agent-1.0.0.jar" (
  echo [错误] 找不到 target\devops-agent-1.0.0.jar ，请先执行: mvn -DskipTests package
  pause & exit /b 1
)
if not exist "%MYSQL_EXE%" (
  echo [错误] 未找到 mysqld: %MYSQL_EXE%
  pause & exit /b 1
)

netstat -an | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto need_mysql
echo [MySQL] 3306 已在监听，跳过启动。
goto agent_start

:need_mysql
echo [MySQL] 启动中...
start "" /B "%MYSQL_EXE%" --defaults-file="%MYSQL_INI%"
set /a tries=0
:mysql_wait
"%MYSQL_CLI%" -u"%MYSQL_USER%" -p"%MYSQL_PWD%" -e "SELECT 1" >nul 2>&1
if not errorlevel 1 goto mysql_ok
set /a tries+=1
if !tries! GEQ 35 goto mysql_fail
timeout /t 1 /nobreak >nul
goto mysql_wait
:mysql_fail
echo [错误] MySQL 长时间未就绪，请检查端口或 phpStudy。
pause & exit /b 1
:mysql_ok
echo [MySQL] 已就绪.

:agent_start
echo [Agent] http://localhost:8080/
echo Ctrl+C 可停后端进程；本次若由本脚本拉起的 mysqld 会继续在后台（可用 mysqladmin shutdown 关闭）
java -jar "target\devops-agent-1.0.0.jar"

endlocal
