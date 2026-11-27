# =====================================================
#  Smart ERP - 一键部署到阿里云
#  用法：右键 -> 使用 PowerShell 运行
# =====================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$UpdateZip = Join-Path $ProjectRoot "update.zip"
$CloudScript = Join-Path $ProjectRoot "update-cloud.ps1"
$Pscp = "C:\Program Files\PuTTY\pscp.exe"
$Plink = "C:\Program Files\PuTTY\plink.exe"

$Server = "8.148.229.2"
$User = "Administrator"
$RemotePath = "C:\erp"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Smart ERP - 一键部署到云端" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  服务器: $Server"
Write-Host "  账号: $User"
Write-Host ""

# 输入密码
$Password = Read-Host "请输入服务器密码" -AsSecureString
$PlainPassword = [System.Net.NetworkCredential]::new("", $Password).Password
if ([string]::IsNullOrEmpty($PlainPassword)) {
    Write-Host "[错误] 密码不能为空" -ForegroundColor Red
    pause
    exit 1
}

# 检查文件
if (-not (Test-Path $UpdateZip)) {
    Write-Host "[错误] 找不到 update.zip，请先运行 build-update.ps1" -ForegroundColor Red
    pause
    exit 1
}
if (-not (Test-Path $CloudScript)) {
    Write-Host "[错误] 找不到 update-cloud.ps1" -ForegroundColor Red
    pause
    exit 1
}
if (-not (Test-Path $Pscp)) {
    Write-Host "[错误] 找不到 pscp.exe: $Pscp" -ForegroundColor Red
    pause
    exit 1
}
if (-not (Test-Path $Plink)) {
    Write-Host "[错误] 找不到 plink.exe: $Plink" -ForegroundColor Red
    pause
    exit 1
}

# 第一次连接：接受主机密钥
Write-Host "[1/4] 连接服务器..." -ForegroundColor Yellow
try {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $Plink
    $psi.Arguments = "-ssh -l $User -pw $PlainPassword -batch $Server `"echo OK`""
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $output = $proc.StandardOutput.ReadToEnd()
    $errorMsg = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    
    if ($proc.ExitCode -ne 0) {
        Write-Host "  [错误] 连接失败!" -ForegroundColor Red
        Write-Host "  $errorMsg" -ForegroundColor DarkYellow
        pause
        exit 1
    }
    Write-Host "  连接成功." -ForegroundColor Green
} catch {
    Write-Host "  [错误] 连接异常: $_" -ForegroundColor Red
    pause
    exit 1
}

# 上传文件
Write-Host "[2/4] 上传更新包..." -ForegroundColor Yellow
& $Pscp -pw $PlainPassword $UpdateZip "$User@${Server}:$RemotePath\update.zip"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [错误] 上传 update.zip 失败!" -ForegroundColor Red
    pause
    exit 1
}
& $Pscp -pw $PlainPassword $CloudScript "$User@${Server}:$RemotePath\update-cloud.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [错误] 上传 update-cloud.ps1 失败!" -ForegroundColor Red
    pause
    exit 1
}
Write-Host "  上传完成." -ForegroundColor Green

# 远程执行更新
Write-Host "[3/4] 远程执行更新..." -ForegroundColor Yellow
$remoteCmd = "powershell -ExecutionPolicy Bypass -File `"$RemotePath\update-cloud.ps1`" -Auto"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $Plink
$psi.Arguments = "-ssh -l $User -pw $PlainPassword -batch $Server `"$remoteCmd`""
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)
$sb = New-Object System.Text.StringBuilder
while (-not $proc.HasExited) {
    if (-not $proc.StandardOutput.EndOfStream) {
        $line = $proc.StandardOutput.ReadLine()
        if ($line) { Write-Host "  $line" -ForegroundColor Gray; [void]$sb.AppendLine($line) }
    }
    Start-Sleep -Milliseconds 200
}
# 读取剩余输出
$remaining = $proc.StandardOutput.ReadToEnd()
$errRemaining = $proc.StandardError.ReadToEnd()
if ($remaining) { Write-Host "  $remaining" -ForegroundColor Gray }
if ($errRemaining) { Write-Host "  $errRemaining" -ForegroundColor DarkYellow }

if ($proc.ExitCode -ne 0) {
    Write-Host "  [警告] 远程执行返回码: $($proc.ExitCode)" -ForegroundColor Yellow
} else {
    Write-Host "  更新执行完成." -ForegroundColor Green
}

Write-Host "[4/4] 部署完成." -ForegroundColor Yellow
Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  部署完成!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  服务器正在重启 ERP 服务（约10-30秒）" -ForegroundColor Cyan
Write-Host "  请稍后访问: http://$Server" -ForegroundColor Cyan
Write-Host "  记得按 Ctrl + F5 强制刷新浏览器" -ForegroundColor Cyan
Write-Host ""

pause
