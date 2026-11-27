$path = Join-Path $PSScriptRoot '..\database\schema\create_tenant_template.ps1'
$tokens = $null
$errs = $null
[System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errs) | Out-Null
if ($errs.Count -eq 0) { Write-Output 'PARSE_OK' } else { $errs | ForEach-Object { Write-Output ('ERR: ' + $_.Message) } }
