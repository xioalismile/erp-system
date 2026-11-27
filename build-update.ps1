# =====================================================
#  Smart ERP - 本地构建更新包脚本
#  用法：右键 -> 使用 PowerShell 运行
#  功能：构建前端 -> 打包成 update.zip
# =====================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $ProjectRoot "frontend"
$DistDir = Join-Path $FrontendDir "dist"
$OutputZip = Join-Path $ProjectRoot "update.zip"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Smart ERP - 构建更新包" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: 构建前端 ---
Write-Host "[1/2] 构建前端..." -ForegroundColor Yellow
if (-not (Test-Path $FrontendDir)) {
    Write-Host "  [错误] 找不到 frontend 目录: $FrontendDir" -ForegroundColor Red
    pause
    exit 1
}

Push-Location $FrontendDir
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [错误] 前端构建失败!" -ForegroundColor Red
        pause
        exit 1
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $DistDir)) {
    Write-Host "  [错误] 构建产物 dist 目录不存在!" -ForegroundColor Red
    pause
    exit 1
}

Write-Host "  构建完成." -ForegroundColor Green

# --- Step 2: 打包成 zip ---
Write-Host "[2/2] 打包更新包..." -ForegroundColor Yellow
if (Test-Path $OutputZip) {
    Remove-Item $OutputZip -Force
}

Compress-Archive -Path "$DistDir\*" -DestinationPath $OutputZip -CompressionLevel Optimal

Write-Host "  更新包已生成: $OutputZip" -ForegroundColor Green

Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  构建完成!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  下一步操作:" -ForegroundColor Cyan
Write-Host "  1. 通过远程桌面把 update.zip 复制到云端服务器 C:\erp\"
Write-Host "  2. 在云端服务器双击运行 update-cloud.ps1"
Write-Host ""
Write-Host "  更新包位置: $OutputZip" -ForegroundColor Gray
Write-Host ""

# 打开更新包所在文件夹
explorer.exe $ProjectRoot

pause
