# =====================================================
#  Smart ERP - Cloud Update Script
# =====================================================

param(
    [switch]$Auto = $false
)

$ErrorActionPreference = "Stop"
$ErpHome = "C:\smart-erp"
$StaticDir = Join-Path $ErpHome "static"
$UpdateZip = Join-Path $ErpHome "update.zip"
$BackupDir = Join-Path $ErpHome "static_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Smart ERP - Cloud Update" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 0: Check update package ---
Write-Host "[0/4] Checking update package..." -ForegroundColor Yellow
if (-not (Test-Path $UpdateZip)) {
    Write-Host "  [ERROR] update.zip not found at: $UpdateZip" -ForegroundColor Red
    if (-not $Auto) { pause }
    exit 1
}
Write-Host "  Found update package." -ForegroundColor Green

# --- Step 1: Backup ---
Write-Host "[1/4] Backup old version..." -ForegroundColor Yellow
if (Test-Path $StaticDir) {
    Copy-Item $StaticDir $BackupDir -Recurse -Force
    Write-Host "  Backed up to: $BackupDir" -ForegroundColor Green
} else {
    Write-Host "  No old version, skip backup." -ForegroundColor Gray
}

# --- Step 2: Extract ---
Write-Host "[2/4] Extracting update package..." -ForegroundColor Yellow
if (Test-Path $StaticDir) {
    Remove-Item $StaticDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $StaticDir | Out-Null

Expand-Archive -Path $UpdateZip -DestinationPath $StaticDir -Force
Write-Host "  Extract complete." -ForegroundColor Green

# --- Step 3: Restart ERP ---
Write-Host "[3/4] Restarting ERP service..." -ForegroundColor Yellow

# Find and stop ERP Java process
$javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue
$erpStopped = $false
foreach ($proc in $javaProcesses) {
    try {
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$($proc.Id)").CommandLine
        if ($cmdLine -like "*smart-erp*" -or $cmdLine -like "*erp*jar*") {
            Write-Host "  Stopping ERP process (PID: $($proc.Id))..." -ForegroundColor Gray
            Stop-Process -Id $proc.Id -Force
            $erpStopped = $true
        }
    } catch {}
}

if (-not $erpStopped) {
    Write-Host "  No running ERP process found." -ForegroundColor Gray
} else {
    Start-Sleep -Seconds 2
    Write-Host "  Stopped." -ForegroundColor Green
}

# Start ERP
$StartBat = Join-Path $ErpHome "start-erp.bat"
if (Test-Path $StartBat) {
    Write-Host "  Starting ERP service..." -ForegroundColor Gray
    Start-Process cmd.exe -ArgumentList "/c `"$StartBat`"" -WindowStyle Normal
    Write-Host "  Start command executed." -ForegroundColor Green
} else {
    Write-Host "  [WARNING] Start script not found: $StartBat" -ForegroundColor Yellow
    Write-Host "  Please start ERP manually." -ForegroundColor Yellow
}

# --- Step 4: Cleanup ---
Write-Host "[4/4] Cleanup..." -ForegroundColor Yellow
$archivePath = Join-Path $ErpHome "update_$(Get-Date -Format 'yyyyMMdd_HHmmss').zip"
Move-Item $UpdateZip $archivePath -Force
Write-Host "  Update package archived: $archivePath" -ForegroundColor Green

Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  Update Complete!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Verify:" -ForegroundColor Cyan
Write-Host "  1. Wait for ERP startup (10-30 seconds)"
Write-Host "  2. Open browser, press Ctrl + F5 to force refresh"
Write-Host "  3. Check if issue is fixed"
Write-Host ""
Write-Host "  Backup at: $BackupDir" -ForegroundColor Gray
Write-Host ""

if (-not $Auto) {
    pause
}
