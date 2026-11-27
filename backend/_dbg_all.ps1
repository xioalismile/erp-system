$f = Join-Path $PSScriptRoot '_dbg_create.ps1'
[System.IO.File]::WriteAllText($f, [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8), (New-Object System.Text.UTF8Encoding($true)))
powershell -NoProfile -ExecutionPolicy Bypass -File $f
$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
Write-Output '=== DBG01 sys_user ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_dbg01 -c "SELECT user_id, username, is_admin FROM sys_user;"
Write-Output '=== DBG01 sys_role ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_dbg01 -c "SELECT role_id, role_code FROM sys_role ORDER BY role_id;"
Write-Output '=== DBG01 sys_user_role ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_dbg01 -c "SELECT * FROM sys_user_role;"
Write-Output '=== DBG01 ADMIN_STANDARD perms count ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_dbg01 -c "SELECT count(*) FROM sys_role_permission rp JOIN sys_role r ON rp.role_id = r.role_id WHERE r.role_code = 'ADMIN_STANDARD';"
Write-Output '=== platform sys_user for dbg01_admin ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_platform -c "SELECT user_id, username, is_admin FROM sys_user WHERE username = 'dbg01_admin';"
