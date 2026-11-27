# create_tenant_template.ps1
# Rebuilds the tenant TEMPLATE database (default: erp_tenant_template) from the
# live business database (default: erp_db): full table structure + seed data.
# New tenant databases are cloned from this template at tenant-creation time
# (see TenantService.createTenantWithAdmin, CREATE DATABASE ... TEMPLATE ...).
#
# Re-run this script whenever the business DB schema or seed data evolves,
# so that newly created tenant databases stay in sync with erp_db.
#
# IMPORTANT: this script must stay pure ASCII (PS5.1 reads non-BOM UTF-8 as GBK).
# Usage:
#   powershell -ExecutionPolicy Bypass -File create_tenant_template.ps1
param(
    [string]$PgBin      = 'D:\Program Files\PostgreSQL\15\bin',
    [string]$DbHost     = 'localhost',
    [int]   $Port       = 5432,
    [string]$DbUser     = 'erp_user',
    [string]$DbPassword = 'Erp@2026',
    [string]$SourceDb   = 'erp_db',
    [string]$AdminDb    = 'erp_platform',
    [string]$TemplateDb = 'erp_tenant_template'
)

$ErrorActionPreference = 'Stop'
$env:PGPASSWORD = $DbPassword
$env:PGCLIENTENCODING = 'UTF8'

$psql   = Join-Path $PgBin 'psql.exe'
$pgDump = Join-Path $PgBin 'pg_dump.exe'
foreach ($tool in @($psql, $pgDump)) {
    if (-not (Test-Path $tool)) { throw "PostgreSQL tool not found: $tool (use -PgBin to override)" }
}

function Invoke-Sql([string]$Sql, [string]$Db) {
    & $psql -h $DbHost -p $Port -U $DbUser -d $Db -v ON_ERROR_STOP=1 -c $Sql | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "psql failed on database [$Db]: $Sql" }
}

# 0. source database must be reachable
Invoke-Sql 'SELECT 1' $SourceDb

# 1. safety: refuse to touch the template if any tenant row points at it
$refCount = (& $psql -h $DbHost -p $Port -U $DbUser -d $AdminDb -t -A -c `
    "SELECT count(*) FROM sys_tenant WHERE db_name = '$TemplateDb'").Trim()
if ([int]$refCount -gt 0) {
    throw "Safety stop: $TemplateDb is referenced by $refCount sys_tenant row(s); refusing to rebuild"
}

# 2. drop old template (kill stray connections first) and recreate it
Invoke-Sql "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$TemplateDb' AND pid <> pg_backend_pid()" $AdminDb
Invoke-Sql "DROP DATABASE IF EXISTS $TemplateDb WITH (FORCE)" $AdminDb
Invoke-Sql "CREATE DATABASE $TemplateDb" $AdminDb
Write-Host "[1/3] template database $TemplateDb recreated"

# 3. copy full structure (schema only, NO business data) from the live business DB
#    pg_dump writes files itself and psql reads them with -f, so UTF-8 bytes are
#    never re-encoded through the PowerShell pipeline (GBK pitfall avoided).
#    --no-owner --no-privileges: some erp_db objects are owned by role "postgres"
#    and erp_user (not a member of that role) cannot restore "OWNER TO postgres"
#    in the new database. Objects in the template will be owned by erp_user instead.
$schemaFile = Join-Path $env:TEMP 'tenant_template_schema.sql'
& $pgDump -h $DbHost -p $Port -U $DbUser -s --no-owner --no-privileges -f $schemaFile $SourceDb
if ($LASTEXITCODE -ne 0) { throw 'pg_dump (schema) failed' }
& $psql -h $DbHost -p $Port -U $DbUser -d $TemplateDb -v ON_ERROR_STOP=1 -f $schemaFile
if ($LASTEXITCODE -ne 0) { throw 'restore schema into template failed' }
Write-Host "[2/3] table structure copied from $SourceDb"

# 4. copy seed data only: menus / roles / permissions / dicts / serial-no rules / config
#    sys_user and sys_user_role stay EMPTY: the tenant admin is inserted at
#    tenant-creation time with a user_id aligned to the platform database.
$seedTables = @(
    'sys_menu',
    'sys_role',
    'sys_role_permission',
    'sys_role_field_permission',
    'sys_dict_type',
    'sys_dict_data',
    'sys_serial_no_config',
    'sys_config'
)
$seedFile = Join-Path $env:TEMP 'tenant_template_seed.sql'
$dumpArgs = @('-h', $DbHost, '-p', $Port, '-U', $DbUser, '--data-only', '--column-inserts', '--no-owner', '--no-privileges')
foreach ($t in $seedTables) { $dumpArgs += @('-t', $t) }
$dumpArgs += @('-f', $seedFile, $SourceDb)
& $pgDump @dumpArgs
if ($LASTEXITCODE -ne 0) { throw 'pg_dump (seed data) failed' }
& $psql -h $DbHost -p $Port -U $DbUser -d $TemplateDb -v ON_ERROR_STOP=1 -f $seedFile
if ($LASTEXITCODE -ne 0) { throw 'restore seed data into template failed' }
Write-Host "[3/3] seed data copied: $($seedTables -join ', ')"

# 4.5 strip platform-only objects out of the template:
#     - ONLY platform role SUPER_ADMIN is removed. It must never exist inside a tenant DB.
#     - ADMIN / MANAGER MUST BE KEPT (2026-09-20 fix, do NOT re-add them to the list):
#       the whole system's "is admin" checks are hasAnyRole("ADMIN","SUPER_ADMIN","MANAGER")
#       (SecurityUtils.isAdmin / SysUserController / EmployeeController / AiConfigController /
#        SysRoleFieldPermissionController / SystemMaintainController - 10 call sites).
#       Tenant admins get a *version* role (ADMIN_STANDARD/ADMIN_CRM/ADMIN_ULTIMATE) which is NOT
#       in that allowlist, so TenantService.assignAdminRole additionally binds ADMIN for them.
#       If ADMIN is deleted from the template, no tenant can ever have it -> every tenant admin is
#       treated as an ordinary user -> creating/updating/deleting users returns 403
#       "no permission to manage users" (verified on 8cloud tenant sc_wudong).
#     - platform admin menus (/admin, /admin/tenant/manage, /admin/deploy/manage)
#       and every sys_role_permission row pointing at them are still removed: tenants cannot even
#       hold menu records for platform management (prevents menu leakage even if a future role
#       gets mis-granted).
Invoke-Sql "DELETE FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('SUPER_ADMIN'))" $TemplateDb
Invoke-Sql "DELETE FROM sys_user_role  WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('SUPER_ADMIN'))" $TemplateDb
Invoke-Sql "DELETE FROM sys_role WHERE role_code IN ('SUPER_ADMIN')" $TemplateDb
Invoke-Sql "DELETE FROM sys_role_permission WHERE menu_id IN (SELECT menu_id::text FROM sys_menu WHERE route_path LIKE '/admin%')" $TemplateDb
Invoke-Sql "DELETE FROM sys_menu WHERE route_path LIKE '/admin%'" $TemplateDb
Write-Host "[3.5/3] platform-only objects stripped (SUPER_ADMIN role + /admin menus); ADMIN/MANAGER kept for tenant admin auth"

# 5. report
$tableCount = (& $psql -h $DbHost -p $Port -U $DbUser -d $TemplateDb -t -A -c `
    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'").Trim()
$menuCount = (& $psql -h $DbHost -p $Port -U $DbUser -d $TemplateDb -t -A -c `
    "SELECT count(*) FROM sys_menu").Trim()
$userCount = (& $psql -h $DbHost -p $Port -U $DbUser -d $TemplateDb -t -A -c `
    "SELECT count(*) FROM sys_user").Trim()
Write-Host ""
Write-Host "Template $TemplateDb ready: tables=$tableCount menus=$menuCount users=$userCount (users must be 0)"
if ([int]$userCount -ne 0) { throw "Template must have 0 users but has $userCount" }
