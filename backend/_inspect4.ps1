$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
Write-Output '=== tenant versions (platform) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_platform -c "SELECT tenant_id, tenant_code, db_name, version FROM sys_tenant ORDER BY tenant_id;"
Write-Output '=== t30206330: roles + admin held roles ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_t30206330 -c "SELECT r.role_code FROM sys_role r ORDER BY r.role_id;"
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_t30206330 -c "SELECT u.username, r.role_code FROM sys_user u JOIN sys_user_role ur ON u.user_id = ur.user_id JOIN sys_role r ON ur.role_id = r.role_id;"
Write-Output '=== erp_db_b: roles + admin held roles ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_db_b -c "SELECT r.role_code FROM sys_role r ORDER BY r.role_id;"
& $psql -h localhost -p 5432 -U erp_user -d erp_db_b -c "SELECT u.username, r.role_code FROM sys_user u JOIN sys_user_role ur ON u.user_id = ur.user_id JOIN sys_role r ON ur.role_id = r.role_id;"
Write-Output '=== /admin menus in existing tenant dbs ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_t30206330 -c "SELECT menu_id, route_path FROM sys_menu WHERE route_path LIKE '/admin%';"
& $psql -h localhost -p 5432 -U erp_user -d erp_db_b -c "SELECT menu_id, route_path FROM sys_menu WHERE route_path LIKE '/admin%';"
