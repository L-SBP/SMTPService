@echo off
chcp 65001
echo ==========================================
echo       Mailbox Backend Server Launcher
echo ==========================================

echo [1/4] 检查环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未检测到 Java 环境，请确保已安装 JDK 17+ 并配置环境变量。
    pause
    exit /b 1
)

call mvn -v >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未检测到 Maven 环境，请确保已安装 Maven 并配置环境变量。
    pause
    exit /b 1
)

echo [2/4] 清理并构建项目...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] 构建失败，请检查错误日志。
    pause
    exit /b 1
)

echo [3/4] 检查数据库连接配置...
echo 请确保本地 MySQL 已启动，且存在数据库: mailbox_prod
echo 账号: root
echo 密码: root_secure_password_123
echo 请确保本地 Redis 已启动 (端口 6379)

echo [4/4] 启动服务...
echo 服务端口: 8081
echo SMTP端口: 25
echo POP3端口: 110
echo ------------------------------------------
java -jar target/mailbox-backend-1.0.0.jar

pause