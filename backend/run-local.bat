@echo off
chcp 65001 >nul
echo ============================================
echo  本地开发模式启动 ERP 后端（所有限制关闭）
echo  - 跳过 License 校验
echo  - 关闭 AI 写操作人工确认
echo  - 端口: 8080
echo  - 数据库: localhost:5432/erp_db
echo ============================================

REM ===== 所有限制关闭 =====
set ERP_LICENSE_ENABLED=false
set ERP_AI_GATEWAY_WRITE_CONFIRM_REQUIRED=false

REM 可选：覆盖其他本地配置（如不需要可注释掉）
REM set ERP_DB_URL=jdbc:postgresql://localhost:5432/erp_db
REM set ERP_DB_USERNAME=erp_user
REM set ERP_DB_PASSWORD=Erp@2026

cd /d "%~dp0"
echo 正在启动后端服务...
java -jar target/smart-erp-1.0.0.jar

pause
