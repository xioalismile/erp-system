$f = Join-Path $PSScriptRoot '_e2e_version.ps1'
$content = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText($f, $content, (New-Object System.Text.UTF8Encoding($true)))
Write-Output 'BOM added'
