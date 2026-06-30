@echo off
chcp 65001 > nul
echo ========================================
echo 智能ERP系统 - 测试执行脚本
echo ========================================
echo.

cd /d "%~dp0"

echo [1/3] 检查Maven安装...
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: Maven未安装或未配置环境变量
    echo 请先安装Maven并配置PATH
    pause
    exit /b 1
)

echo [2/3] 编译测试代码...
call mvn test-compile -DskipTests
if %errorlevel% neq 0 (
    echo 编译失败!
    pause
    exit /b 1
)

echo [3/3] 运行测试...
echo.
echo ---------------------------------------------------
echo 请选择要运行的测试:
echo ---------------------------------------------------
echo [1] 全部测试
echo [2] 客户管理模块测试
echo [3] Service层测试
echo [4] Controller层测试
echo [0] 退出
echo ---------------------------------------------------
set /p choice=请输入选择:

if "%choice%"=="1" goto all_tests
if "%choice%"=="2" goto customer_tests
if "%choice%"=="3" goto service_tests
if "%choice%"=="4" goto controller_tests
if "%choice%"=="0" exit /b 0

:all_tests
call mvn test
goto end

:customer_tests
call mvn test -Dtest=CustomerServiceTest,CustomerControllerTest
goto end

:service_tests
call mvn test -Dtest=*ServiceTest
goto end

:controller_tests
call mvn test -Dtest=*ControllerTest
goto end

:end
echo.
echo ========================================
echo 测试执行完成
echo ========================================
pause
