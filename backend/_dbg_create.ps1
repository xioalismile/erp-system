$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'
function Post-Json($url, $body, $token) {
    $h = @{ 'Content-Type' = 'application/json; charset=utf-8' }
    if ($token) { $h['Authorization'] = 'Bearer ' + $token }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    return Invoke-RestMethod -Uri $url -Method POST -Headers $h -Body $bytes -TimeoutSec 60
}
$login = Post-Json ($base + '/auth/login') '{"username":"admin","password":"admin123"}' $null
$adminToken = $login.data.token
if ($login.data.requireTenantSelect) {
    $defId = $null
    foreach ($t in $login.data.tenants) { if ($t.tenantCode -eq 'DEFAULT') { $defId = $t.tenantId } }
    if ($defId) { $sel = Post-Json ($base + '/tenant/select') ('{"tenantId":' + $defId + '}') $adminToken; $adminToken = $sel.data.token }
}
$create = Post-Json ($base + '/admin/tenant/create') '{"tenantCode":"DBG01","tenantName":"debug-01","version":"STANDARD","trialDays":7,"maxUsers":5}' $adminToken
Write-Output ("created: " + ($create | ConvertTo-Json -Compress -Depth 4))
