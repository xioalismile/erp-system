# =====================================================
#  Smart ERP - Deploy to Cloud (SSH Key Auth)
# =====================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$UpdateZip = Join-Path $ProjectRoot "update.zip"
$Scp = "scp"
$Ssh = "ssh"

$Server = "8.148.229.2"
$User = "Administrator"
$SshKey = "$env:USERPROFILE\.ssh\id_ed25519"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Smart ERP - Deploy to Cloud (Key Auth)" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Server: $Server"
Write-Host "  User: $User"
Write-Host "  Key:   $SshKey"
Write-Host ""

# Check files
if (-not (Test-Path $UpdateZip)) {
    Write-Host "[ERROR] update.zip not found" -ForegroundColor Red
    pause
    exit 1
}
if (-not (Test-Path $SshKey)) {
    Write-Host "[ERROR] SSH key not found: $SshKey" -ForegroundColor Red
    pause
    exit 1
}

# Step 1: Test connection (with retry)
Write-Host "[1/5] Connecting to server..." -ForegroundColor Yellow
$connected = $false
for ($i = 1; $i -le 3; $i++) {
    $result = & $Ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=accept-new -i $SshKey "$User@$Server" "echo OK" 2>&1
    if ($result -match "OK") {
        $connected = $true
        break
    }
    Write-Host "  Retry $i..." -ForegroundColor DarkYellow
    Start-Sleep -Seconds 2
}
if (-not $connected) {
    Write-Host "  [ERROR] Connection failed: $result" -ForegroundColor Red
    pause
    exit 1
}
Write-Host "  Connected." -ForegroundColor Green

# Step 2: Static directory
Write-Host "[2/5] Static directory: C:\nginx\html" -ForegroundColor Yellow
Write-Host "  OK." -ForegroundColor Green

# Step 3: Upload update.zip
Write-Host "[3/5] Uploading update.zip..." -ForegroundColor Yellow
& $Scp -o StrictHostKeyChecking=accept-new -i $SshKey $UpdateZip "$User@${Server}:C:/smart-erp/update.zip"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [ERROR] Upload failed!" -ForegroundColor Red
    pause
    exit 1
}
Write-Host "  Upload complete." -ForegroundColor Green

# Step 4: Remote update
Write-Host "[4/5] Running remote update..." -ForegroundColor Yellow

$remoteScript = @'
$staticDir = "C:\nginx\html"
$zipFile = "C:\smart-erp\update.zip"

# Backup
if (Test-Path $staticDir) {
    $backup = "C:\smart-erp\static_backup_" + (Get-Date -Format "yyyyMMdd_HHmmss")
    Copy-Item $staticDir $backup -Recurse -Force
    Write-Host "Backup done"
}

# Clear old files
if (Test-Path $staticDir) {
    Remove-Item $staticDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $staticDir | Out-Null

# Extract
Expand-Archive -Path $zipFile -DestinationPath $staticDir -Force
Write-Host "Extract done"

# Restart Java
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Set-Location C:\smart-erp\backend
Start-Process -FilePath java -ArgumentList "-jar","smart-erp-1.0.0.jar" -RedirectStandardOutput "startup.log" -RedirectStandardError "startup_err.log" -NoNewWindow
Write-Host "ERP restarted"

# Reload Nginx
try { & C:\nginx\nginx.exe -s reload 2>$null } catch {}
Write-Host "Nginx reloaded"
Write-Host "DONE"
'@

$tempScript = Join-Path $env:TEMP "erp_remote_update.ps1"
$remoteScript | Out-File -FilePath $tempScript -Encoding UTF8

# Upload remote script
& $Scp -o StrictHostKeyChecking=accept-new -i $SshKey $tempScript "$User@${Server}:C:/smart-erp/remote_update.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [ERROR] Failed to upload script!" -ForegroundColor Red
    pause
    exit 1
}

# Execute remote script
& $Ssh -o ConnectTimeout=30 -i $SshKey "$User@$Server" "powershell -ExecutionPolicy Bypass -File C:\smart-erp\remote_update.ps1"

Write-Host "  Remote update complete." -ForegroundColor Green

# Step 5: Done
Write-Host "[5/5] Done." -ForegroundColor Yellow
Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  Deploy Complete!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  ERP server is restarting (10-30 seconds)" -ForegroundColor Cyan
Write-Host "  Visit: http://$Server" -ForegroundColor Cyan
Write-Host "  Press Ctrl + F5 to force refresh browser" -ForegroundColor Cyan
Write-Host ""

pause
