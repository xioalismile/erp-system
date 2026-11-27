# =====================================================
#  Smart ERP - Deploy to Cloud
# =====================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$UpdateZip = Join-Path $ProjectRoot "update.zip"
$Pscp = "C:\Program Files\PuTTY\pscp.exe"
$Plink = "C:\Program Files\PuTTY\plink.exe"

$Server = "8.148.229.2"
$User = "Administrator"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Smart ERP - Deploy to Cloud" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Server: $Server"
Write-Host "  User: $User"
Write-Host ""

# Input password
$Password = Read-Host "Enter server password" -AsSecureString
$PlainPassword = [System.Net.NetworkCredential]::new("", $Password).Password
if ([string]::IsNullOrEmpty($PlainPassword)) {
    Write-Host "[ERROR] Password cannot be empty" -ForegroundColor Red
    pause
    exit 1
}

# Check files
if (-not (Test-Path $UpdateZip)) {
    Write-Host "[ERROR] update.zip not found" -ForegroundColor Red
    pause
    exit 1
}
if (-not (Test-Path $Pscp)) {
    Write-Host "[ERROR] pscp.exe not found: $Pscp" -ForegroundColor Red
    pause
    exit 1
}
if (-not (Test-Path $Plink)) {
    Write-Host "[ERROR] plink.exe not found: $Plink" -ForegroundColor Red
    pause
    exit 1
}

# Step 1: Connect
Write-Host "[1/5] Connecting to server..." -ForegroundColor Yellow
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $Plink
$psi.Arguments = "-ssh -l $User -pw $PlainPassword $Server `"echo OK`""
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$proc = [System.Diagnostics.Process]::Start($psi)
Start-Sleep -Milliseconds 500
# Send 'y' to accept host key if prompted
$proc.StandardInput.WriteLine("y")
$proc.StandardInput.Flush()
$output = $proc.StandardOutput.ReadToEnd()
$errOut = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()
if ($proc.ExitCode -ne 0) {
    # Try again without host key prompt (already cached)
    $psi2 = New-Object System.Diagnostics.ProcessStartInfo
    $psi2.FileName = $Plink
    $psi2.Arguments = "-ssh -l $User -pw $PlainPassword -batch $Server `"echo OK`""
    $psi2.UseShellExecute = $false
    $psi2.RedirectStandardOutput = $true
    $psi2.RedirectStandardError = $true
    $psi2.CreateNoWindow = $true
    $proc2 = [System.Diagnostics.Process]::Start($psi2)
    $out2 = $proc2.StandardOutput.ReadToEnd()
    $err2 = $proc2.StandardError.ReadToEnd()
    $proc2.WaitForExit()
    if ($proc2.ExitCode -ne 0) {
        Write-Host "  [ERROR] Connection failed!" -ForegroundColor Red
        Write-Host "  $err2" -ForegroundColor DarkYellow
        pause
        exit 1
    }
}
Write-Host "  Connected." -ForegroundColor Green

# Step 2: Static directory is C:\nginx\html (Nginx serves frontend)
Write-Host "[2/5] Static directory: C:\nginx\html" -ForegroundColor Yellow
$staticDir = "C:\nginx\html"
Write-Host "  OK." -ForegroundColor Green

# Step 3: Upload update.zip
Write-Host "[3/5] Uploading update.zip..." -ForegroundColor Yellow
& $Pscp -pw $PlainPassword $UpdateZip "$User@${Server}:C:\smart-erp\update.zip"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [ERROR] Upload failed!" -ForegroundColor Red
    pause
    exit 1
}
Write-Host "  Upload complete." -ForegroundColor Green

# Step 4: Remote update - backup, extract, restart
Write-Host "[4/5] Running remote update..." -ForegroundColor Yellow

# Build remote PowerShell command
$remoteScript = @"
`$staticDir = '$staticDir'
`$zipFile = 'C:\smart-erp\update.zip'

# Backup
if (Test-Path `$staticDir) {
    `$backup = 'C:\smart-erp\static_backup_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
    Copy-Item `$staticDir `$backup -Recurse -Force
    Write-Host "Backup done: `$backup"
}

# Clear old files
if (Test-Path `$staticDir) {
    Remove-Item `$staticDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path `$staticDir | Out-Null

# Extract
Expand-Archive -Path `$zipFile -DestinationPath `$staticDir -Force
Write-Host "Extract done"

# Restart Java + reload Nginx
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Set-Location C:\smart-erp\backend
Start-Process -FilePath java -ArgumentList '-jar','smart-erp-1.0.0.jar' -RedirectStandardOutput 'startup.log' -RedirectStandardError 'startup_err.log' -NoNewWindow
Write-Host "ERP restarted"
# Reload Nginx to pick up new static files
try { & C:\nginx\nginx.exe -s reload -ErrorAction SilentlyContinue } catch {}
Write-Host "Nginx reloaded"
"@

# Write remote script to temp file and upload it
$tempScript = Join-Path $env:TEMP "erp_remote_update.ps1"
$remoteScript | Out-File -FilePath $tempScript -Encoding UTF8

& $Pscp -pw $PlainPassword $tempScript "$User@${Server}:C:\smart-erp\remote_update.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [ERROR] Failed to upload update script!" -ForegroundColor Red
    pause
    exit 1
}

# Execute remote script
$remoteCmd = "powershell -ExecutionPolicy Bypass -File C:\smart-erp\remote_update.ps1"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $Plink
$psi.Arguments = "-ssh -l $User -pw $PlainPassword -batch $Server `"$remoteCmd`""
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)
while (-not $proc.HasExited) {
    if (-not $proc.StandardOutput.EndOfStream) {
        $line = $proc.StandardOutput.ReadLine()
        if ($line) { Write-Host "  $line" -ForegroundColor Gray }
    }
    Start-Sleep -Milliseconds 200
}
$remaining = $proc.StandardOutput.ReadToEnd()
$errRemaining = $proc.StandardError.ReadToEnd()
if ($remaining) { Write-Host "  $remaining" -ForegroundColor Gray }
if ($errRemaining) { Write-Host "  $errRemaining" -ForegroundColor DarkYellow }

if ($proc.ExitCode -ne 0) {
    Write-Host "  [WARNING] Remote exit code: $($proc.ExitCode)" -ForegroundColor Yellow
} else {
    Write-Host "  Remote update complete." -ForegroundColor Green
}

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
