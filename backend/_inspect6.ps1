$env:PGPASSWORD = 'Erp@2026'
$env:PGCLIENTENCODING = 'UTF8'
$psql = 'D:\Program Files\PostgreSQL\15\bin\psql.exe'
Write-Output '=== erp_db users holding version roles ==='
& $psql -h localhost -p 5432 -U erp_user -d erp_db -c "SELECT u.user_id, u.username, u.real_name, r.role_code FROM sys_user u JOIN sys_user_role ur ON u.user_id = ur.user_id JOIN sys_role r ON ur.role_id = r.role_id WHERE r.role_code IN ('ADMIN_STANDARD','ADMIN_CRM','ADMIN_ULTIMATE') ORDER BY u.user_id;"
