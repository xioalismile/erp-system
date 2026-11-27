<#
  install-asr.ps1 —— 在 Windows 服务器上安装「本机常驻语音识别（ASR）服务」

  作用：检查文件 → 生成启动器（含日志按天轮转） → 注册计划任务（开机自启 + 失败自动重启）
        → 启动并等 /health 就绪 → 打印自检（监听地址必须是 127.0.0.1）。
  幂等：可重复执行（会先停掉旧进程再启动，计划任务用 -Force 覆盖注册）。
  卸载：install-asr.ps1 -Uninstall（停进程 + 注销计划任务，文件保留）

  用法（管理员 PowerShell）：
    powershell -ExecutionPolicy Bypass -File install-asr.ps1
    powershell -ExecutionPolicy Bypass -File install-asr.ps1 -Root D:\smart-erp-asr -Port 6006
    powershell -ExecutionPolicy Bypass -File install-asr.ps1 -Uninstall
#>
param(
  [string]$Root = 'C:\smart-erp-asr',
  [int]$Port = 6006,
  [int]$Threads = 2,
  [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
$MODEL_NAME = 'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17'
$TASK_NAME = 'SmartERP-ASR'
$ServiceDir = Join-Path $Root 'service'
$ModelDir = Join-Path (Join-Path $Root 'models') $MODEL_NAME
$NodeExe = Join-Path (Join-Path $Root 'node') 'node.exe'
$LogDir = Join-Path $Root 'logs'
$DeployDir = Join-Path $Root 'deploy'
$Launcher = Join-Path $DeployDir 'start-asr.ps1'
$ServerFile = Join-Path $ServiceDir 'server.js'

function Write-Step($m) { Write-Host ('[STEP] ' + $m) -ForegroundColor Cyan }
function Write-Ok($m) { Write-Host ('[OK]   ' + $m) -ForegroundColor Green }
function Write-Bad($m) { Write-Host ('[FAIL] ' + $m) -ForegroundColor Red }

# 停掉由本服务启动的 node 进程（其它 node 进程不动）
function Stop-AsrProcess {
  Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains($ServerFile) } |
    ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch { } }
}

if ($Uninstall) {
  Write-Step '卸载：停止进程 + 注销计划任务（文件保留）'
  Stop-AsrProcess
  $t = Get-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue
  if ($t) {
    Stop-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $TASK_NAME -Confirm:$false
    Write-Ok ('计划任务已注销：' + $TASK_NAME)
  } else {
    Write-Ok '未发现计划任务（无需注销）'
  }
  Write-Ok ('卸载完成。目录 ' + $Root + ' 未删除，可手动清理')
  return
}

# 1) 文件检查
Write-Step '检查文件是否齐全'
$missing = @()
if (-not (Test-Path $NodeExe)) { $missing += ('缺少 Node：' + $NodeExe) }
if (-not (Test-Path $ServerFile)) { $missing += ('缺少服务代码：' + $ServerFile) }
if (-not (Test-Path (Join-Path $ServiceDir 'node_modules\sherpa-onnx-node'))) { $missing += ('缺少依赖：' + (Join-Path $ServiceDir 'node_modules\sherpa-onnx-node')) }
if (-not (Test-Path (Join-Path $ModelDir 'model.int8.onnx'))) { $missing += ('缺少模型：' + (Join-Path $ModelDir 'model.int8.onnx')) }
if (-not (Test-Path (Join-Path $ModelDir 'tokens.txt'))) { $missing += ('缺少词表：' + (Join-Path $ModelDir 'tokens.txt')) }
if ($missing.Count -gt 0) {
  $missing | ForEach-Object { Write-Bad $_ }
  Write-Bad '文件不全：请先按 README-部署步骤.md 第 1 节放好文件，再执行本脚本'
  exit 1
}
Write-Ok '文件齐全'

# 2) 目录
New-Item -ItemType Directory -Force -Path $LogDir, $DeployDir | Out-Null

# 3) 生成启动器（启动器里写死环境变量与日志轮转，避免依赖系统 PATH / 用户环境）
Write-Step '生成启动器 start-asr.ps1'
$launcherText = @'
# 由 install-asr.ps1 生成：启动本机语音识别服务（含日志按天轮转 + 14 天清理）
$ErrorActionPreference = 'Continue'
$Root = '__ROOT__'
$Port = '__PORT__'
$ModelDir = '__MODELDIR__'
$LogDir = Join-Path $Root 'logs'
$NodeExe = Join-Path (Join-Path $Root 'node') 'node.exe'
$ServerFile = Join-Path (Join-Path $Root 'service') 'server.js'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$out = Join-Path $LogDir 'asr.out.log'
$err = Join-Path $LogDir 'asr.err.log'
$stamp = Get-Date -Format 'yyyyMMdd'
if (Test-Path $out) { Move-Item $out (Join-Path $LogDir ('asr.out.' + $stamp + '.log')) -Force -ErrorAction SilentlyContinue }
if (Test-Path $err) { Move-Item $err (Join-Path $LogDir ('asr.err.' + $stamp + '.log')) -Force -ErrorAction SilentlyContinue }
Get-ChildItem $LogDir -Filter 'asr.*.log' -ErrorAction SilentlyContinue |
  Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-14) } |
  Remove-Item -Force -ErrorAction SilentlyContinue
$env:ASR_HOST = '127.0.0.1'
$env:ASR_PORT = $Port
$env:ASR_MODEL_DIR = $ModelDir
$env:ASR_THREADS = '__THREADS__'
& $NodeExe $ServerFile > $out 2> $err
'@
$launcherText = $launcherText.Replace('__ROOT__', $Root).Replace('__PORT__', [string]$Port).Replace('__MODELDIR__', $ModelDir).Replace('__THREADS__', [string]$Threads)
[System.IO.File]::WriteAllText($Launcher, $launcherText, (New-Object System.Text.UTF8Encoding($true)))
Write-Ok ('启动器已写入：' + $Launcher)

# 4) 计划任务（开机自启 + 失败自动重启）
Write-Step '注册计划任务（开机自启 + 失败自动重启）'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $Launcher + '"')
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable
Register-ScheduledTask -TaskName $TASK_NAME -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
Write-Ok ('计划任务已注册：' + $TASK_NAME)

# 5) 启动 + 等就绪
Write-Step '启动服务并等待就绪（最多 60 秒）'
Stop-AsrProcess
Start-Sleep -Seconds 1
Start-ScheduledTask -TaskName $TASK_NAME
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  try {
    $r = Invoke-WebRequest -Uri ('http://127.0.0.1:' + $Port + '/health') -UseBasicParsing -TimeoutSec 3
    if ($r.StatusCode -eq 200) { Write-Ok ('服务健康：' + $r.Content); $healthy = $true; break }
  } catch { }
}
if (-not $healthy) {
  Write-Bad ('服务未在 60 秒内就绪，请看日志：' + (Join-Path $LogDir 'asr.err.log'))
  $errLog = Join-Path $LogDir 'asr.err.log'
  if (Test-Path $errLog) { Get-Content $errLog -Tail 20 | ForEach-Object { Write-Host ('  ' + $_) } }
  exit 2
}

# 6) 自检：必须只监听回环地址
Write-Step '自检（监听地址 / 计划任务）'
$listen = netstat -ano | Select-String (':' + $Port) | Select-String 'LISTENING'
$listen | ForEach-Object { Write-Host ('  ' + $_.Line.Trim()) }
$openAll = $listen | Where-Object { $_.Line -match '0\.0\.0\.0' }
if ($openAll) {
  Write-Bad '检测到对全网卡监听（0.0.0.0）：与设计不符，请检查启动器里的 ASR_HOST'
} else {
  Write-Ok '只监听回环地址 127.0.0.1（外网打不到，安全）'
}
Write-Host ''
Write-Ok '安装完成。下一步：把 sys_config 的 ai.asr.provider 改成 local，然后到 ERP 里点 🎤 说话验证'
