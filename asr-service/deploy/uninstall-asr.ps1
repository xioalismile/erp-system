<#
  uninstall-asr.ps1 —— 卸载「本机常驻语音识别（ASR）服务」

  等价于 install-asr.ps1 -Uninstall：停止 node 进程 + 注销计划任务 SmartERP-ASR。
  文件目录默认保留（随时可再装），如需彻底清理，手工删除 $Root 目录即可。

  用法（管理员 PowerShell）：
    powershell -ExecutionPolicy Bypass -File uninstall-asr.ps1
#>
param(
  [string]$Root = 'C:\smart-erp-asr'
)
$ErrorActionPreference = 'Stop'
$installer = Join-Path (Join-Path $Root 'deploy') 'install-asr.ps1'
if (-not (Test-Path $installer)) {
  Write-Host ('[FAIL] 找不到安装脚本：' + $installer) -ForegroundColor Red
  exit 1
}
& $installer -Root $Root -Uninstall
