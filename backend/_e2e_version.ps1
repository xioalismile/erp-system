$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'

function Post-Json($url, $body, $token) {
    $h = @{ 'Content-Type' = 'application/json; charset=utf-8' }
    if ($token) { $h['Authorization'] = 'Bearer ' + $token }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    return Invoke-RestMethod -Uri $url -Method POST -Headers $h -Body $bytes -TimeoutSec 60
}
function Get-Json($url, $token) {
    $h = @{}
    if ($token) { $h['Authorization'] = 'Bearer ' + $token }
    return Invoke-RestMethod -Uri $url -Method GET -Headers $h -TimeoutSec 30
}

# wait for backend
$deadline = (Get-Date).AddSeconds(150)
$ready = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    try { Invoke-WebRequest -Uri ($base + '/auth/login') -Method POST -Body '{}' -ContentType 'application/json' -UseBasicParsing -TimeoutSec 3 | Out-Null; $ready = $true; break } catch { if ($_.Exception.Response) { $ready = $true; break } }
}
if (-not $ready) { Write-Output 'BACKEND_NOT_READY'; exit 1 }
Write-Output 'backend ready'

# ========== 1. admin (DEFAULT/erp_db) - must keep full menus + platform ==========
$login = Post-Json ($base + '/auth/login') '{"username":"admin","password":"admin123"}' $null
$adminToken = $login.data.token
if ($login.data.requireTenantSelect) {
    $defId = $null
    foreach ($t in $login.data.tenants) { if ($t.tenantCode -eq 'DEFAULT') { $defId = $t.tenantId } }
    if ($defId) { $sel = Post-Json ($base + '/tenant/select') ('{"tenantId":' + $defId + '}') $adminToken; $adminToken = $sel.data.token }
}
$info = Get-Json ($base + '/auth/userinfo') $adminToken
$adminMenuCount = $info.data.menuRoutePaths.Count
$adminHasPlatform = ($info.data.menuRoutePaths -contains '/admin/tenant/manage')
$adminHasWildcard = ($info.data.permissions -contains '*')
Write-Output ("STEP1 admin: menus=" + $adminMenuCount + " platformVisible=" + $adminHasPlatform + " wildcard=" + $adminHasWildcard + " roles=" + ($info.data.roles -join ','))

# ========== 2. create STANDARD tenant ==========
$createBody = '{"tenantCode":"TESTSTD03","tenantName":"version-verify-03","version":"STANDARD","trialDays":30,"maxUsers":10,"contactPerson":"verifier"}'
$create = Post-Json ($base + '/admin/tenant/create') $createBody $adminToken
if ($create.code -ne 200) { Write-Output ('STEP2_FAIL ' + $create.message); exit 1 }
$tenantId = $create.data.tenantId
Write-Output ("STEP2 tenant created id=" + $tenantId + " db=" + $create.data.dbName)

# ========== 3. tenant admin login -> userinfo ==========
$tLogin = Post-Json ($base + '/auth/login') '{"username":"teststd03_admin","password":"Admin@123456"}' $null
if ($tLogin.code -ne 200) { Write-Output ('STEP3_FAIL ' + $tLogin.message); exit 1 }
$tToken = $tLogin.data.token
if ($tLogin.data.requireTenantSelect) {
    $tid = $tLogin.data.tenants[0].tenantId
    $sel2 = Post-Json ($base + '/tenant/select') ('{"tenantId":' + $tid + '}') $tToken
    $tToken = $sel2.data.token
}
$tInfo = Get-Json ($base + '/auth/userinfo') $tToken
$tMenuCount = $tInfo.data.menuRoutePaths.Count
$tHasAdminPath = $false
foreach ($p in $tInfo.data.menuRoutePaths) { if ($p -like '/admin*') { $tHasAdminPath = $true } }
$tHasWildcard = ($tInfo.data.permissions -contains '*')
Write-Output ("STEP3 tenant admin: menus=" + $tMenuCount + " hasAdminPath=" + $tHasAdminPath + " wildcard=" + $tHasWildcard + " roles=" + ($tInfo.data.roles -join ',') + " version=" + $tInfo.data.tenantVersion)

# ========== 4. cleanup test tenant ==========
$del = Post-Json ($base + '/admin/tenant/' + $tenantId + '/delete') '{}' $adminToken
Write-Output ("STEP4 cleanup deleted=" + ($del.code -eq 200))

# ========== verdict ==========
$pass = ($adminMenuCount -ge 100) -and $adminHasPlatform -and $adminHasWildcard -and ($tMenuCount -gt 0) -and ($tMenuCount -le 95) -and (-not $tHasAdminPath) -and (-not $tHasWildcard)
if ($pass) { Write-Output 'RESULT_PASS' } else { Write-Output 'RESULT_FAIL' }
