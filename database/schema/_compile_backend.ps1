Set-Location (Join-Path $PSScriptRoot '..\..\backend')
mvn -q "-Dmaven.test.skip=true" compile 2>&1 | Select-Object -Last 8
Write-Output ("compile exit=" + $LASTEXITCODE)
