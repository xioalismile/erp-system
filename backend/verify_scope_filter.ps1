# Data scope filter live test: admin (ALL) vs xiaos (SELF)
$base = 'http://localhost:8080'
function Login($u, $p) {
    $body = '{"username":"' + $u + '","password":"' + $p + '"}'
    $resp = Invoke-WebRequest -Uri "$base/api/auth/login" -Method POST -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing -TimeoutSec 15
    $json = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json
    return $json.data.token
}
$adminToken = Login 'admin' 'admin123'
Write-Output "admin login OK"
$xiaosToken = Login 'xiaos' 'admin123'
Write-Output "xiaos login OK"

function GetTotal($token, $url) {
    $resp = Invoke-WebRequest -Uri "$base$url" -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing -TimeoutSec 20
    $json = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json
    if ($json.code -ne 200) { return "bizcode=$($json.code)" }
    if ($null -ne $json.data.total) { return $json.data.total }
    return ($json.data.records | Measure-Object).Count
}

Write-Output ("crm/lead page: admin(ALL) total = " + (GetTotal $adminToken '/api/crm/lead/page?current=1&size=50'))
Write-Output ("crm/lead page: xiaos(SELF) total = " + (GetTotal $xiaosToken '/api/crm/lead/page?current=1&size=50'))
Write-Output ("crm/opportunity page: admin(ALL) total = " + (GetTotal $adminToken '/api/crm/opportunity/page?current=1&size=50'))
Write-Output ("crm/opportunity page: xiaos(SELF) total = " + (GetTotal $xiaosToken '/api/crm/opportunity/page?current=1&size=50'))
Write-Output ("finance/ar: admin(ALL) total = " + (GetTotal $adminToken '/api/finance/ar?current=1&size=50'))
Write-Output ("finance/ar: xiaos(SELF) total = " + (GetTotal $xiaosToken '/api/finance/ar?current=1&size=50'))
Write-Output ("sale/orders: admin(ALL) total = " + (GetTotal $adminToken '/api/sale/orders?current=1&size=50'))
Write-Output ("sale/orders: xiaos(SELF) total = " + (GetTotal $xiaosToken '/api/sale/orders?current=1&size=50'))
Write-Output '--- expected: xiaos sees 0 or fewer rows than admin (crm_lead.created_by all NULL + no saler_id=15) ---'
