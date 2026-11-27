$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
Write-Output '=== ADMIN_STANDARD permissions containing /admin menus (by numeric id) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT rp.menu_id FROM sys_role_permission rp JOIN sys_role r ON rp.role_id = r.role_id WHERE r.role_code = 'ADMIN_STANDARD' AND rp.menu_id IN ('5','400','401');"
Write-Output '=== /admin menus version values ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT menu_id, route_path, version FROM sys_menu WHERE route_path LIKE '/admin%';"
Write-Output '=== version-role permission format sample (first 8) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT rp.menu_id FROM sys_role_permission rp JOIN sys_role r ON rp.role_id = r.role_id WHERE r.role_code = 'ADMIN_STANDARD' LIMIT 8;"
