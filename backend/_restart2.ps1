$c = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($c) {
    Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
    Write-Output ("killed old backend pid=" + $c.OwningProcess)
    Start-Sleep -Seconds 3
} else {
    Write-Output "no old backend on 8080"
}
schtasks /Create /TN SmartERP-StartNow /TR "powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\erp\smart-erp-services\START-ALL.ps1" /SC ONCE /ST 23:59 /F | Out-Null
schtasks /Run /TN SmartERP-StartNow | Out-Null
Start-Sleep -Seconds 2
schtasks /Delete /TN SmartERP-StartNow /F | Out-Null
Write-Output "START-ALL dispatched (detached)"
