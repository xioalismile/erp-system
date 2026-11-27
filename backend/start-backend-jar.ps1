# Start backend from packaged JAR, loading .env first (ASCII-only, paths via $PSScriptRoot)
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -le 0) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "Env:$key" -Value $value
    }
    Write-Host "Env loaded from .env"
} else {
    Write-Host "No .env found"
}
Set-Location $PSScriptRoot
Write-Host "Starting smart-erp JAR..."
java -jar target\smart-erp-1.0.0.jar
