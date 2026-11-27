$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
Write-Output '=== neibu001 exists? ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_platform -t -c "SELECT datname FROM pg_database WHERE datname = 'erp_tenant_neibu001';"
Write-Output '=== neibu001 roles + users ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_neibu001 -c "SELECT r.role_code FROM sys_role r ORDER BY r.role_id;"
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_neibu001 -c "SELECT u.username, u.is_admin, r.role_code FROM sys_user u LEFT JOIN sys_user_role ur ON u.user_id = ur.user_id LEFT JOIN sys_role r ON ur.role_id = r.role_id;"
Write-Output '=== neibu001 /admin menus ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_neibu001 -c "SELECT menu_id, route_path FROM sys_menu WHERE route_path LIKE '/admin%';"
Write-Output '=== erp_db version roles usage (any user holds them?) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_db -c "SELECT r.role_code, count(ur.user_id) AS user_count FROM sys_role r LEFT JOIN sys_user_role ur ON ur.role_id = r.role_id WHERE r.role_code IN ('ADMIN_STANDARD','ADMIN_CRM','ADMIN_ULTIMATE') GROUP BY r.role_code;"
