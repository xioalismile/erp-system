$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
Write-Output '=== wildcard * permission records (erp_db) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_db -c "SELECT r.role_code, rp.menu_id FROM sys_role_permission rp JOIN sys_role r ON rp.role_id = r.role_id WHERE rp.menu_id = '*';"
Write-Output '=== template: does ADMIN_STANDARD include platform menus (401/admin paths) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT m.menu_id, m.route_path FROM sys_menu m WHERE m.route_path LIKE '/admin%';"
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT count(*) FROM sys_role_permission rp JOIN sys_role r ON rp.role_id=r.role_id JOIN sys_menu m ON rp.menu_id::int = m.menu_id WHERE r.role_code='ADMIN_STANDARD' AND m.route_path LIKE '/admin%';"
Write-Output '=== menu version values distribution (template) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT m.version, count(*) FROM sys_menu m GROUP BY m.version;"
