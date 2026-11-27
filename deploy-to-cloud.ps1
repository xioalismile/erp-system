# =====================================================
#  Smart ERP - 一键部署到云端（SSH 方式）
#  用法：右键 -> 使用 PowerShell 运行
#  功能：构建前端 -> 打包 -> SCP上传 -> SSH远程更新重启
# =====================================================

param(
    [string]$Server = "",
    [string]$User = "Administrator",
    [string]$RemotePath = "C:\erp"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $ProjectRoot "frontend"
$DistDir = Join-Path $FrontendDir "dist"
$UpdateZip = Join-Path $ProjectRoot "update.zip"
$CloudScript = Join-Path $ProjectRoot "update-cloud.ps1"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Smart ERP - 一键部署到云端" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# --- 检查 SSH 客户端 ---
if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    Write-Host "[错误] 本机未安装 SSH 客户端" -ForegroundColor Red
    Write-Host "  Windows 10/11 已自带，可在 设置->应用->可选功能 中添加 OpenSSH 客户端" -ForegroundColor Yellow
    pause
    exit 1
}

# --- 输入服务器信息 ---
if ([string]::IsNullOrEmpty($Server)) {
    $Server = Read-Host "请输入云服务器 IP 地址（例如 47.118.28.112）"
}
if ([string]::IsNullOrEmpty($Server)) {
    Write-Host "[错误] 服务器地址不能为空" -ForegroundColor Red
    pause
    exit 1
}

# --- 输入密码 ---
$Password = Read-Host "请输入服务器登录密码" -AsSecureString
$PlainPassword = [System.Net.NetworkCredential]::new("", $Password).Password
if ([string]::IsNullOrEmpty($PlainPassword)) {
    Write-Host "[错误] 密码不能为空" -ForegroundColor Red
    pause
    exit 1
}

# --- Step 1: 构建前端 ---
Write-Host ""
Write-Host "[1/5] 构建前端..." -ForegroundColor Yellow
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

# --- Step 2: 打包 ---
Write-Host "[2/5] 打包更新包..." -ForegroundColor Yellow
if (Test-Path $UpdateZip) {
    Remove-Item $UpdateZip -Force
}
Compress-Archive -Path "$DistDir\*" -DestinationPath $UpdateZip -CompressionLevel Optimal
Write-Host "  打包完成." -ForegroundColor Green

# --- Step 3: 上传更新包和云端脚本 ---
Write-Host "[3/5] 上传到服务器 $Server ..." -ForegroundColor Yellow

# 使用 sshpass 替代方案：通过管道输入密码（Windows 上用 plink 更可靠，但先试试 ssh）
# 这里用一个简单的方式：生成临时的 SCP 命令
$scpCmd = "scp -o StrictHostKeyChecking=no `"$UpdateZip`" `"$CloudScript`" `"$User@${Server}:$RemotePath/`""

# Windows 下 ssh/scp 不支持管道传密码，需要用 plink/pscp 或 SSH.NET
# 我们用另一种方案：检查是否有 pscp，没有则提示

$hasPscp = $false
$plinkPath = ""
$pscpPath = ""
foreach ($p in @("C:\Program Files\PuTTY\pscp.exe", "C:\Program Files (x86)\PuTTY\pscp.exe")) {
    if (Test-Path $p) { $pscpPath = $p; $hasPscp = $true; break }
}
if (-not $hasPscp) {
    $plinkInPath = Get-Command pscp -ErrorAction SilentlyContinue
    if ($plinkInPath) {
        $pscpPath = "pscp"
        $plinkPath = "plink"
        $hasPscp = $true
    }
}

if (-not $hasPscp) {
    Write-Host ""
    Write-Host "[提示] 未检测到 PuTTY 工具（pscp/plink）" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  由于 Windows 自带的 scp/ssh 不支持非交互式密码输入，" -ForegroundColor Gray
    Write-Host "  需要安装 PuTTY 才能实现全自动一键更新。" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  下载地址：https://www.chiark.greenend.org.uk/~sgtatham/putty/latest.html" -ForegroundColor Cyan
    Write-Host "  下载并安装 putty-64bit-installer.msi 即可" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  ====== 半自动更新方案 ======" -ForegroundColor Cyan
    Write-Host "  1. 手动上传以下两个文件到服务器 $RemotePath 目录：" -ForegroundColor White
    Write-Host "     - $UpdateZip" -ForegroundColor Gray
    Write-Host "     - $CloudScript" -ForegroundColor Gray
    Write-Host "  2. 在服务器上右键运行 update-cloud.ps1" -ForegroundColor White
    Write-Host ""
    pause
    exit 0
}

# 查找 plink
if ([string]::IsNullOrEmpty($plinkPath)) {
    foreach ($p in @("C:\Program Files\PuTTY\plink.exe", "C:\Program Files (x86)\PuTTY\plink.exe")) {
        if (Test-Path $p) { $plinkPath = $p; break }
    }
}
if ([string]::IsNullOrEmpty($plinkPath)) {
    $plinkInPath = Get-Command plink -ErrorAction SilentlyContinue
    if ($plinkInPath) { $plinkPath = "plink" }
}
if ([string]::IsNullOrEmpty($plinkPath)) {
    Write-Host "  [错误] 找到 pscp 但找不到 plink，请确认 PuTTY 完整安装" -ForegroundColor Red
    pause
    exit 1
}

# --- 用 pscp 上传 ---
Write-Host "  上传中（pscp）..." -ForegroundColor Gray
$env:PLINK_PROTOCOL = "ssh"

# 第一次连接需要接受主机密钥，用 plink 先连一下
echo y | & $plinkPath -ssh -l $User -pw $PlainPassword $Server "echo ok" 2>$null | Out-Null

# 上传文件
& $pscpPath -pw $PlainPassword "$UpdateZip" "$User@${Server}:$RemotePath\update.zip"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [错误] 上传 update.zip 失败!" -ForegroundColor Red
    pause
    exit 1
}

& $pscpPath -pw $PlainPassword "$CloudScript" "$User@${Server}:$RemotePath\update-cloud.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [错误] 上传 update-cloud.ps1 失败!" -ForegroundColor Red
    pause
    exit 1
}
Write-Host "  上传完成." -ForegroundColor Green

# --- Step 4: SSH 远程执行更新脚本 ---
Write-Host "[4/5] 远程执行更新..." -ForegroundColor Yellow

# 在远程执行 PowerShell 脚本更新
$remoteCmd = "powershell -ExecutionPolicy Bypass -File `"$RemotePath\update-cloud.ps1`" -Auto"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $plinkPath
$psi.Arguments = "-ssh -l $User -pw $PlainPassword -batch $Server `"$remoteCmd`""
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)
$output = $proc.StandardOutput.ReadToEnd()
$error = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()

if ($output) {
    Write-Host $output -ForegroundColor Gray
}
if ($error) {
    Write-Host $error -ForegroundColor DarkYellow
}

if ($proc.ExitCode -ne 0) {
    Write-Host "  [警告] 远程执行返回码: $($proc.ExitCode)" -ForegroundColor Yellow
    Write-Host "  请登录服务器检查更新状态" -ForegroundColor Yellow
} else {
    Write-Host "  远程更新已触发." -ForegroundColor Green
}

# --- Step 5: 完成 ---
Write-Host "[5/5] 更新流程结束." -ForegroundColor Yellow
Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  部署完成!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  服务器正在重启 ERP 服务，约需 10-30 秒" -ForegroundColor Cyan
Write-Host "  请稍后访问 http://$Server 验证（Ctrl+F5 强制刷新）" -ForegroundColor Cyan
Write-Host ""

pause
