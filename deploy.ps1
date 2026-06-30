# =====================================================
# Smart ERP Deploy Script for Windows Server
# Usage: Right-click -> Run with PowerShell
# Or: powershell -ExecutionPolicy Bypass -File deploy.ps1
# =====================================================

$ErrorActionPreference = "Stop"
$ERP_HOME = "C:\erp"
$ERP_APP = "$ERP_HOME\app"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Smart ERP One-Click Deploy Script" -ForegroundColor Cyan
Write-Host "  Target: Windows Server 2022 (4C8G)" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 0: Check Admin ---
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[ERROR] Please run as Administrator!" -ForegroundColor Red
    Write-Host "Right-click PowerShell -> Run as Administrator" -ForegroundColor Yellow
    pause
    exit 1
}

# --- Create directories ---
Write-Host "[1/9] Creating directories..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $ERP_HOME | Out-Null
New-Item -ItemType Directory -Force -Path $ERP_APP | Out-Null
New-Item -ItemType Directory -Force -Path "$ERP_HOME\logs" | Out-Null
New-Item -ItemType Directory -Force -Path "$ERP_HOME\files" | Out-Null
Write-Host "  Done. ERP home: $ERP_HOME" -ForegroundColor Green

# --- Step 2: Install JDK 17 ---
Write-Host "[2/9] Checking JDK 17..." -ForegroundColor Yellow
$javaOk = $false
try {
    $javaVer = & java -version 2>&1 | Select-Object -First 1
    if ($javaVer -match "17") { $javaOk = $true; Write-Host "  JDK 17 already installed." -ForegroundColor Green }
} catch {}

if (-not $javaOk) {
    Write-Host "  Installing JDK 17 via winget..." -ForegroundColor Yellow
    try {
        winget install --id Microsoft.OpenJDK.17 --accept-package-agreements --accept-source-agreements 2>$null
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        Write-Host "  JDK 17 installed." -ForegroundColor Green
    } catch {
        Write-Host "  winget failed, trying chocolatey..." -ForegroundColor Yellow
        if (-not (Get-Command choco -ErrorAction SilentlyContinue)) {
            Set-ExecutionPolicy Bypass -Scope Process -Force
            iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
        }
        choco install temurin17 -y
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    }
}

# --- Step 3: Install Node.js ---
Write-Host "[3/9] Checking Node.js..." -ForegroundColor Yellow
$nodeOk = $false
try {
    $nodeVer = & node --version 2>&1
    if ($nodeVer -match "v1[89]|v2[0-9]") { $nodeOk = $true; Write-Host "  Node.js already installed ($nodeVer)." -ForegroundColor Green }
} catch {}

if (-not $nodeOk) {
    Write-Host "  Installing Node.js 18..." -ForegroundColor Yellow
    try {
        winget install --id OpenJS.NodeJS.LTS --accept-package-agreements --accept-source-agreements 2>$null
    } catch {
        choco install nodejs-lts -y
    }
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    Write-Host "  Node.js installed." -ForegroundColor Green
}

# --- Step 4: Install PostgreSQL ---
Write-Host "[4/9] Checking PostgreSQL..." -ForegroundColor Yellow
$pgOk = $false
try {
    $psqlVer = & psql --version 2>&1
    if ($psqlVer) { $pgOk = $true; Write-Host "  PostgreSQL already installed." -ForegroundColor Green }
} catch {}

if (-not $pgOk) {
    Write-Host "  Installing PostgreSQL 15..." -ForegroundColor Yellow
    try {
        winget install --id PostgreSQL.PostgreSQL.15 --accept-package-agreements --accept-source-agreements 2>$null
    } catch {
        choco install postgresql15 -y --params '/Password:Erp@2026'
    }
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

    # If installed via winget, need to set password and start service
    $pgPath = Get-ChildItem "C:\Program Files\PostgreSQL" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pgPath) {
        $env:Path += ";$($pgPath.FullName)\bin"
    }
    Write-Host "  PostgreSQL installed." -ForegroundColor Green

    # Wait for PG to be ready
    Start-Sleep -Seconds 5
}

# --- Step 5: Configure PostgreSQL ---
Write-Host "[5/9] Configuring database..." -ForegroundColor Yellow
$pgBin = $null
$pgPaths = @("C:\Program Files\PostgreSQL\15\bin", "C:\Program Files\PostgreSQL\16\bin", "C:\Program Files\PostgreSQL\17\bin")
foreach ($p in $pgPaths) {
    if (Test-Path $p) { $pgBin = $p; break }
}
if (-not $pgBin) {
    # Search for psql in PATH
    $psqlLoc = Get-Command psql -ErrorAction SilentlyContinue
    if ($psqlLoc) { $pgBin = Split-Path $psqlLoc.Source }
}
if (-not $pgBin) {
    Write-Host "  [WARNING] Cannot find psql. Please configure database manually." -ForegroundColor Red
    Write-Host "  You need to:" -ForegroundColor Yellow
    Write-Host "  1. Set postgres password to: Erp@2026" -ForegroundColor Yellow
    Write-Host "  2. Create database erp_db" -ForegroundColor Yellow
    Write-Host "  3. Create user erp_user with password Erp@2026" -ForegroundColor Yellow
    Write-Host "  4. Run init.sql" -ForegroundColor Yellow
} else {
    Write-Host "  PostgreSQL bin: $pgBin" -ForegroundColor Gray

    # Try to connect with default password
    $env:PGPASSWORD = "Erp@2026"
    $pgReady = $false
    try {
        & "$pgBin\psql" -U postgres -h localhost -c "SELECT 1" 2>$null | Out-Null
        $pgReady = $true
    } catch {
        # Try common passwords
        foreach ($pwd in @("postgres", "Erp@2026", "")) {
            $env:PGPASSWORD = $pwd
            try {
                & "$pgBin\psql" -U postgres -h localhost -c "SELECT 1" 2>$null | Out-Null
                $pgReady = $true
                break
            } catch {}
        }
    }

    if ($pgReady) {
        Write-Host "  PostgreSQL connected. Setting up database..." -ForegroundColor Gray

        # Set password
        & "$pgBin\psql" -U postgres -h localhost -c "ALTER USER postgres WITH PASSWORD 'Erp@2026';" 2>$null
        & "$pgBin\psql" -U postgres -h localhost -c "CREATE USER erp_user WITH PASSWORD 'Erp@2026';" 2>$null
        & "$pgBin\psql" -U postgres -h localhost -c "CREATE DATABASE erp_db OWNER erp_user ENCODING 'UTF8';" 2>$null
        & "$pgBin\psql" -U postgres -h localhost -c "GRANT ALL PRIVILEGES ON DATABASE erp_db TO erp_user;" 2>$null

        # Run init SQL
        $initSql = "$ERP_APP\backend\src\main\resources\db\init.sql"
        if (Test-Path $initSql) {
            & "$pgBin\psql" -U postgres -h localhost -d erp_db -f $initSql 2>$null
            Write-Host "  Database initialized." -ForegroundColor Green
        } else {
            Write-Host "  [WARNING] init.sql not found at $initSql" -ForegroundColor Yellow
            Write-Host "  Please copy your project to $ERP_APP first, then re-run this script." -ForegroundColor Yellow
        }
    } else {
        Write-Host "  [WARNING] Cannot connect to PostgreSQL." -ForegroundColor Red
        Write-Host "  Please set postgres password and run:" -ForegroundColor Yellow
        Write-Host "  psql -U postgres -h localhost -f init.sql" -ForegroundColor Yellow
    }
}

# --- Step 6: Install Redis ---
Write-Host "[6/9] Checking Redis..." -ForegroundColor Yellow
$redisOk = $false
try {
    $redisVer = & redis-cli --version 2>&1
    if ($redisVer) { $redisOk = $true; Write-Host "  Redis already installed." -ForegroundColor Green }
} catch {}

if (-not $redisOk) {
    Write-Host "  Downloading Redis for Windows..." -ForegroundColor Yellow
    $redisZip = "$ERP_HOME\redis.zip"
    $redisUrl = "https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip"
    try {
        # Use TLS 1.2 for GitHub downloads
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest $redisUrl -OutFile $redisZip -UseBasicParsing
        Expand-Archive $redisZip -DestinationPath "$ERP_HOME\redis" -Force
        Remove-Item $redisZip

        # Add to PATH
        $env:Path += ";$ERP_HOME\redis"

        # Start Redis
        Start-Process "$ERP_HOME\redis\redis-server.exe" -WindowStyle Minimized
        Start-Sleep -Seconds 2
        Write-Host "  Redis installed and started." -ForegroundColor Green
    } catch {
        Write-Host "  [WARNING] Redis download failed: $_" -ForegroundColor Red
        Write-Host "  ERP can still run, AI confirm feature will use memory fallback." -ForegroundColor Yellow
    }
}

# --- Step 7: Build Backend ---
Write-Host "[7/9] Building backend..." -ForegroundColor Yellow
$backendDir = "$ERP_APP\backend"
if (-not (Test-Path "$backendDir\pom.xml")) {
    Write-Host "  [ERROR] Backend not found at $backendDir" -ForegroundColor Red
    Write-Host "  Please copy your project files to $ERP_APP" -ForegroundColor Yellow
    Write-Host "  Expected structure:" -ForegroundColor Yellow
    Write-Host "    C:\erp\app\backend\pom.xml" -ForegroundColor Yellow
    Write-Host "    C:\erp\app\frontend\package.json" -ForegroundColor Yellow
    pause
    exit 1
}

# Check Maven
$mvnOk = $false
try { & mvn --version 2>&1 | Out-Null; $mvnOk = $true } catch {}
if (-not $mvnOk) {
    Write-Host "  Installing Maven..." -ForegroundColor Gray
    try { winget install --id Apache.Maven --accept-package-agreements --accept-source-agreements 2>$null }
    catch { choco install maven -y }
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
}

Push-Location $backendDir
& mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [ERROR] Maven build failed!" -ForegroundColor Red
    pause
    exit 1
}
$jarFile = Get-ChildItem "target\*.jar" | Where-Object { $_.Name -notmatch "sources|javadoc" } | Select-Object -First 1
if ($jarFile) {
    Copy-Item $jarFile.FullName "$ERP_HOME\smart-erp.jar" -Force
    Write-Host "  Backend built: $($jarFile.Name)" -ForegroundColor Green
}
Pop-Location

# --- Step 8: Build Frontend ---
Write-Host "[8/9] Building frontend..." -ForegroundColor Yellow
$frontendDir = "$ERP_APP\frontend"
if (Test-Path "$frontendDir\package.json") {
    Push-Location $frontendDir
    & npm install --silent 2>$null
    & npm run build 2>$null
    if (Test-Path "dist") {
        # Copy dist to backend static resources
        $staticDir = "$ERP_HOME\static"
        New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
        Copy-Item "dist\*" $staticDir -Recurse -Force
        Write-Host "  Frontend built to $staticDir" -ForegroundColor Green
    } else {
        Write-Host "  [WARNING] Frontend build may have failed." -ForegroundColor Yellow
    }
    Pop-Location
} else {
    Write-Host "  [SKIP] Frontend not found." -ForegroundColor Gray
}

# --- Step 9: Create startup scripts ---
Write-Host "[9/9] Creating startup scripts..." -ForegroundColor Yellow

# Create config file
$configContent = @"
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/erp_db
spring.datasource.username=erp_user
spring.datasource.password=Erp@2026
spring.redis.host=localhost
spring.redis.port=6379
erp.upload.path=$ERP_HOME\files
logging.file.name=$ERP_HOME\logs\erp.log
erp.ai.qwen.enabled=true
erp.ai.qwen.api-key=
erp.ai.qwen.model=qwen-plus
erp.ai.gateway.write-confirm-required=true
"@
Set-Content -Path "$ERP_HOME\application.properties" -Value $configContent -Encoding UTF8

# Create start script
$startScript = @"
@echo off
title Smart ERP Server
echo Starting Smart ERP...
echo.
echo Access URL: http://localhost:8080
echo Press Ctrl+C to stop.
echo.
java -jar "$ERP_HOME\smart-erp.jar" --spring.config.additional-location="$ERP_HOME\application.properties"
pause
"@
Set-Content -Path "$ERP_HOME\start-erp.bat" -Value $startScript -Encoding ASCII

# Create start Redis script
$startRedis = @"
@echo off
title Redis Server
"$ERP_HOME\redis\redis-server.exe"
pause
"@
Set-Content -Path "$ERP_HOME\start-redis.bat" -Value $startRedis -Encoding ASCII

# Create start all script
$startAll = @"
@echo off
echo Starting Redis...
start "$ERP_HOME\start-redis.bat"
timeout /t 2 /nobreak >nul
echo Starting ERP Server...
start "$ERP_HOME\start-erp.bat"
echo.
echo ERP System started!
echo Web URL: http://localhost:8080
echo.
"@
Set-Content -Path "$ERP_HOME\start-all.bat" -Value $startAll -Encoding ASCII

# --- Create desktop shortcut ---
$desktopPath = [Environment]::GetFolderPath("Desktop")
$shortcutPath = "$desktopPath\启动ERP.lnk"
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = "$ERP_HOME\start-all.bat"
$shortcut.WorkingDirectory = $ERP_HOME
$shortcut.IconLocation = "shell32.dll,44"
$shortcut.Save()

Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  DEPLOY COMPLETE!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Next steps:" -ForegroundColor Cyan
Write-Host "  1. Set AI API Key:" -ForegroundColor White
Write-Host "     Edit C:\erp\application.properties" -ForegroundColor Gray
Write-Host "     Set erp.ai.qwen.api-key=your-key" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Start ERP:" -ForegroundColor White
Write-Host "     Double-click desktop shortcut '启动ERP'" -ForegroundColor Gray
Write-Host "     Or run: C:\erp\start-all.bat" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. Access:" -ForegroundColor White
Write-Host "     http://localhost:8080 (local)" -ForegroundColor Gray
Write-Host "     http://114.55.15.199:8080 (public)" -ForegroundColor Gray
Write-Host ""
Write-Host "  4. Login:" -ForegroundColor White
Write-Host "     admin / admin123" -ForegroundColor Gray
Write-Host ""
Write-Host "  5. Open firewall port 8080:" -ForegroundColor White
Write-Host "     Run: open-firewall.bat" -ForegroundColor Gray
Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
pause
