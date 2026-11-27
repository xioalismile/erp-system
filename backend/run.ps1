# ============================================================
# Smart ERP Backend Startup Script
# Auto-loads .env file as environment variables, then starts Spring Boot
# Usage:
#   1. Copy .env.example to .env and modify the values
#   2. Run in PowerShell: .\run.ps1
# ============================================================

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $scriptDir '.env'

# Load .env file if exists
if (Test-Path $envFile) {
    Write-Host "Loading env file: $envFile" -ForegroundColor Green
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        # Skip empty lines and comments
        if (-not $line -or $line.StartsWith('#')) { return }
        # Parse KEY=VALUE
        $idx = $line.IndexOf('=')
        if ($idx -le 0) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        # Strip surrounding quotes
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        # Set environment variable
        Set-Item -Path "Env:$key" -Value $value
        # Only print KEY to avoid leaking secrets to logs
        Write-Host "  Loaded: $key" -ForegroundColor Gray
    }
    Write-Host "Env loaded." -ForegroundColor Green
} else {
    Write-Host "No .env file found, will use defaults in application.yml" -ForegroundColor Yellow
    Write-Host "  To customize, copy .env.example to .env and edit it" -ForegroundColor Yellow
}

# Security check: JWT secret must be changed in production
$jwtSecret = $env:ERP_JWT_SECRET
if ($jwtSecret -and ($jwtSecret -like '*DevOnly*' -or $jwtSecret -like '*ChangeThis*')) {
    Write-Host "[WARNING] ERP_JWT_SECRET uses dev default. Must change in production!" -ForegroundColor Red
}

Write-Host ""
Write-Host "Starting Spring Boot..." -ForegroundColor Cyan
Write-Host ""

# Start Spring Boot
Set-Location $scriptDir
mvn spring-boot:run -DskipTests
