$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
Write-Output '=== erp_db sys_role (template source) ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_db -c "SELECT role_id, role_code, role_name, is_enabled FROM sys_role ORDER BY role_id;"
Write-Output '=== erp_tenant_template sys_role ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT role_id, role_code, role_name, is_enabled FROM sys_role ORDER BY role_id;"
Write-Output '=== template ADMIN_STANDARD permission count ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_tenant_template -c "SELECT r.role_code, count(p.menu_id) as menu_count FROM sys_role r LEFT JOIN sys_role_permission p ON r.role_id = p.role_id GROUP BY r.role_code ORDER BY r.role_code;"
Write-Output '=== erp_db admin user roles ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_db -c "SELECT u.username, r.role_code FROM sys_user u LEFT JOIN sys_user_role ur ON u.user_id = ur.user_id LEFT JOIN sys_role r ON ur.role_id = r.role_id WHERE u.username = 'admin';"
