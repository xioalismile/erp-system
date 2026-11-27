$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
# rewrite sql files as UTF8-BOM (PS5.1 GBK pitfall)
foreach ($name in @('_fix1_platform.sql','_fix2_erpdb.sql','_fix3_neibu.sql','_fix4_erpb.sql')) {
    $f = Join-Path $PSScriptRoot $name
    $c = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText($f, $c, (New-Object System.Text.UTF8Encoding($true)))
}
Write-Output '--- fix1 platform ---'
& $psql -h localhost -p 5432 -U erp_user -d erp_platform -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot '_fix1_platform.sql')
Write-Output '--- fix2 erp_db ---'
& $psql -h localhost -p 5432 -U erp_user -d erp_db -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot '_fix2_erpdb.sql')
Write-Output '--- fix3 neibu001 ---'
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_neibu001 -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot '_fix3_neibu.sql')
Write-Output '--- fix4 erp_db_b ---'
& $psql -h localhost -p 5432 -U erp_user -d erp_db_b -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot '_fix4_erpb.sql')
Write-Output ('all exit=' + $LASTEXITCODE)
