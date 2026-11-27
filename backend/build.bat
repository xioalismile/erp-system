@echo off
chcp 65001 >nul
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8"
cd /d "d:\erp\智能ERP-项目完善系统全部页面后端加文档逻辑完善 (1)\智能ERP-项目\backend"
mvn compile -DskipTests -Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8
exit /b %ERRORLEVEL%
