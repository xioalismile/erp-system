param(
    [Parameter(Mandatory=$true)][ValidateSet('Update','Rollback','ListBackups','PruneAssets')][string]$Mode,
    [string]$JarTarget = '',
    [string]$FrontendTargets = '',
    [string]$StopCommand = '',
    [string]$StartCommand = '',
    [string]$HealthUrl = 'http://localhost/api/auth/login',
    [string]$HealthMethod = 'POST',
    [string]$HealthBody = '{"username":"__probe__","password":"__probe__"}',
    [string]$BackupName = '',
    [string]$DbName = '',
    [string]$DbUser = '',
    [string]$DbPassword = '',
    [string]$ZipMd5 = '',
    [string]$TemplateDb = '',
    # Mode=PruneAssets only: -Apply really deletes; without it the run is REPORT ONLY
    [switch]$Apply
)
# update-agent.ps1 - runs ON SERVER. Modes: Update / Rollback / ListBackups / PruneAssets
# agent-version: 20260913.01 (md5 verify + atomic swap + rollback health check + SQL kit gate + SqlRc fix
#                             + honest tenant-template reporting + real health-gate verdict ($true check)
#                             + /index.html frontend probe + stale frontend asset pruning + PruneAssets mode)
# Exit codes: 0=ok, 2=updated-failed-rolled-back(healthy), 3=rollback-failed/unhealthy,
#             4=bad-input(integrity), 5=sql-kit-failed (ABORTED BEFORE ANY FILE TOUCHED)
$ErrorActionPreference = 'Continue'
$base = 'C:\smart-erp-update'
$incoming = Join-Path $base 'incoming.zip'
$stage = Join-Path $base 'stage'
$backups = Join-Path $base 'backups'
# one file per deploy listing the build's files - lets the next deploy keep the previous
# generation alive, so a browser tab that is still on the old index.html can lazy-load its chunks
$manifests = Join-Path $base 'manifests'
$log = Join-Path $base 'update-agent.log'
$agentVersion = '20260913.01'
foreach ($d in @($base, $backups, $manifests)) { if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null } }
function L($m) { $line = ('[' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '] ' + $m); Write-Output $line; Add-Content -Path $log -Value $line }

function Find-Psql {
    # probe both drives x PG 12..17, then PATH (local console uses D:, 8cloud uses C: PG16)
    foreach ($drive in @('C:', 'D:')) {
        foreach ($v in @('17','16','15','14','13','12')) {
            $p = Join-Path $drive ('\Program Files\PostgreSQL\' + $v + '\bin\psql.exe')
            if (Test-Path $p) { return $p }
        }
    }
    $gc = Get-Command psql -ErrorAction SilentlyContinue
    if ($gc) { return $gc.Source }
    return $null
}

function Find-PgDump {
    # locate pg_dump.exe (for tenant template clone); same drive/version probing as psql
    foreach ($drive in @('C:', 'D:')) {
        foreach ($v in @('17','16','15','14','13','12')) {
            $p = Join-Path $drive ('\Program Files\PostgreSQL\' + $v + '\bin\pg_dump.exe')
            if (Test-Path $p) { return $p }
        }
    }
    $gc = Get-Command pg_dump -ErrorAction SilentlyContinue
    if ($gc) { return $gc.Source }
    return $null
}

function Invoke-SqlFile([string]$file) {
    # run one SQL file with ON_ERROR_STOP; every output line goes to the agent log
    # NOTE: exit code passed via $script:SqlRc, NOT the return value - L() uses Write-Output
    # which would pollute the function output; callers doing (Invoke-SqlFile x) -ne 0 on an
    # array does FILTERING comparison (non-empty array = $true) = false FAILURE even on success.
    $out = & $script:psqlExe -h localhost -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -f $file 2>&1
    $script:SqlRc = $LASTEXITCODE
    foreach ($ln in $out) { if ("$ln".Trim()) { L ('sql: ' + $ln) } }
}

function HealthCheck([int]$maxLoops) {
    # 健康检查升级版 (20260904): 多API冒烟 + 前后端版本号绑定 + 向后兼容降级
    # 探针1: GET /api/system/build-info (新后端) -> 拿后端版本号,验证后端活着+版本号注入
    # 探针2: GET http://localhost/?_cb=xxx (cache-buster) -> 拿前端 index.html,提取 build-version meta
    # 探针3: POST /api/auth/login (向后兼容) -> 端口活着判定 (200 或 401 都算端口通)
    # 判定:
    #   - build-info 返回版本号(新后端): 版本号 != unknown + 前端版本号存在 + 前后端版本号一致 + 登录接口活着
    #   - build-info 404(旧后端兼容): 降级为只查登录接口 (向后兼容,不强制回滚)
    # 失败原因写入日志,方便排查"本地通过服务器炸"的根因
    $buildInfoUrl = 'http://localhost/api/system/build-info'
    # Frontend probe MUST target /index.html explicitly, NOT '/'. 8cloud nginx has
    # "index promo.html index.html" in both server blocks, so '/' returns the marketing page
    # (no build-version meta) and the version-binding gate failed on every deploy there
    # (agent log 2026-09-07 .. 2026-09-12 all show "missing build-version meta tag").
    # '/' is kept as a fallback for servers whose root really serves the ERP index.
    $frontendUrls = @('http://localhost/index.html', 'http://localhost/')
    for ($i = 0; $i -lt $maxLoops; $i++) {
        Start-Sleep 5
        $failReason = ''
        $backendVersion = $null
        $frontendVersion = $null
        $loginOk = $false

        # 探针1: 后端 build-info (新后端模式)
        try {
            $r1 = Invoke-WebRequest -Uri $buildInfoUrl -Method Get -UseBasicParsing -TimeoutSec 5
            if ($r1.StatusCode -eq 200) {
                $j = $r1.Content | ConvertFrom-Json
                if ($j.code -eq 200 -and $j.data -and $j.data.version) {
                    $backendVersion = [string]$j.data.version
                }
            }
        } catch { }

        # 探针2: 前端 index.html (cache-buster 绕过 nginx 缓存)
        foreach ($feBase in $frontendUrls) {
            if ($frontendVersion) { break }
            try {
                $cb = '?_cb=' + (Get-Random)
                $r2 = Invoke-WebRequest -Uri ($feBase + $cb) -Method Get -UseBasicParsing -TimeoutSec 5
                if ($r2.StatusCode -eq 200) {
                    $m = [regex]::Match($r2.Content, 'name=[''"]build-version[''"]\s+content=[''"]([^''"]+)[''"]')
                    if ($m.Success) { $frontendVersion = $m.Groups[1].Value }
                }
            } catch { }
        }

        # 探针3: 登录接口 (向后兼容,200 或 401 都算端口活着)
        try {
            if ($HealthMethod -eq 'POST') {
                $r3 = Invoke-WebRequest -Uri $HealthUrl -Method Post -ContentType 'application/json' -Body $HealthBody -UseBasicParsing -TimeoutSec 5
            } else {
                $r3 = Invoke-WebRequest -Uri $HealthUrl -Method Get -UseBasicParsing -TimeoutSec 5
            }
            if ($r3.StatusCode -eq 200 -or $r3.StatusCode -eq 401) { $loginOk = $true }
        } catch {
            try {
                $sc = $_.Exception.Response.StatusCode.value__
                if ($sc -eq 401) { $loginOk = $true }
            } catch { }
        }

        # 判定逻辑
        $ok = $false
        if ($backendVersion) {
            if ($backendVersion -eq 'unknown' -or $backendVersion -eq 'dev-unknown') {
                $failReason = 'backend build version is unknown (build-info.properties not injected)'
            } elseif (-not $frontendVersion) {
                $failReason = 'frontend index.html missing build-version meta tag'
            } elseif ($backendVersion -ne $frontendVersion) {
                $failReason = 'version mismatch: backend=' + $backendVersion + ' frontend=' + $frontendVersion
            } elseif (-not $loginOk) {
                $failReason = 'login endpoint not responding'
            } else {
                $ok = $true
            }
        } else {
            if ($loginOk) { $ok = $true } else { $failReason = 'login endpoint not responding (legacy mode)' }
        }

        if ($ok) {
            $verInfo = $(if ($backendVersion) { ' version=' + $backendVersion } else { ' (legacy)' })
            L ('healthy after ' + (($i+1)*5) + 's' + $verInfo)
            return $true
        }
    }
    if ($failReason) { L ('health check failed: ' + $failReason) }
    return $false
}

# ---- stale frontend asset pruning -------------------------------------------
# WHY: the swap step overlays new files onto a COPY of the current target (by design, so
# non-ERP files living in nginx html survive). Side effect: every deploy permanently keeps the
# previous builds' content-hashed chunks - 8cloud reached 8714 asset files vs 467 in a clean
# build (~150MB per target, plus every backup copy inherits it).
# WHAT: keep exactly the files that belong to the build being deployed (the stage dir IS that
# build - same content the swap just put in place), delete the rest. Only inside
# <target>\assets and <target>\mobile\assets, and only names matching Vite's content-hash
# pattern, so a hand-placed file can never be removed.
# WHERE: success branch only, AFTER the health check passed. The failure/rollback path never
# calls this. Deleting here is safe for rollback: instant-rollback uses the __prev dir (already
# handled) and the timestamped backups keep a full pre-swap copy.
# NOTE: must NOT call L()/Write-Output - log lines would leak into the return value and break
# callers (same PowerShell trap that made the health gate always true).
function Remove-StaleFrontendAssets([string]$StageFrontend, [string[]]$Targets, [bool]$DoApply, [string]$ExtraKeepList = '') {
    $res = @{ ok = $false; reason = ''; stale = 0; kept = 0; skippedNonBuild = 0; freedMB = 0; extraKept = 0; warnings = @() }
    if (-not (Test-Path $StageFrontend)) { $res.reason = 'stage frontend not found: ' + $StageFrontend; return $res }
    if (-not (Test-Path (Join-Path $StageFrontend 'index.html'))) { $res.reason = 'stage frontend has no index.html: ' + $StageFrontend; return $res }
    $keep = @{}
    foreach ($f in @(Get-ChildItem $StageFrontend -Recurse -File)) {
        $keep[$f.FullName.Substring($StageFrontend.Length + 1).ToLower()] = $true
    }
    # previous generation (a manifest written by an earlier deploy): keep those files as well, so a
    # browser tab still running the old index.html can lazy-load its own chunks after the deploy
    if ($ExtraKeepList -and (Test-Path $ExtraKeepList)) {
        foreach ($ln in [IO.File]::ReadAllLines($ExtraKeepList)) {
            $k = $ln.Trim().ToLower()
            if ($k -and -not $keep.ContainsKey($k)) { $keep[$k] = $true; $res.extraKept = $res.extraKept + 1 }
        }
    }
    $hashName = '-[A-Za-z0-9_-]{8}\.(js|css|png|jpg|jpeg|gif|svg|webp|ico|woff|woff2|ttf|eot|map)$'
    $warnings = New-Object System.Collections.ArrayList
    $freed = 0
    foreach ($t in $Targets) {
        foreach ($sub in @('assets', 'mobile\assets')) {
            $dir = Join-Path $t $sub
            if (-not (Test-Path $dir)) { continue }
            foreach ($f in @(Get-ChildItem $dir -File)) {
                $rel = $f.FullName.Substring($t.Length + 1).ToLower()
                if ($keep.ContainsKey($rel)) { $res.kept = $res.kept + 1; continue }
                if ($f.Name -notmatch $hashName) { $res.skippedNonBuild = $res.skippedNonBuild + 1; continue }
                if ($DoApply) {
                    try {
                        $sz = $f.Length
                        Remove-Item $f.FullName -Force -ErrorAction Stop
                        $res.stale = $res.stale + 1
                        $freed += $sz
                    } catch {
                        [void]$warnings.Add('cannot delete ' + $f.FullName + ' (' + $_.Exception.Message + ')')
                    }
                } else {
                    $res.stale = $res.stale + 1
                    $freed += $f.Length
                }
            }
        }
    }
    $res.freedMB = [math]::Round($freed/1MB, 2)
    $res.warnings = @($warnings)
    $res.ok = $true
    return $res
}

if ($Mode -eq 'ListBackups') {
    Get-ChildItem $backups -Directory | Sort-Object Name -Descending | ForEach-Object { Write-Output $_.Name }
    exit 0
}

# ============ Mode: PruneAssets ============
# On-demand / report-only run of the same logic the success branch uses. Needs -FrontendTargets
# and a populated stage dir (left over from the last deploy). Without -Apply it only reports.
if ($Mode -eq 'PruneAssets') {
    $pTargets = @($FrontendTargets.Split(';') | Where-Object { $_ })
    L ('=== PRUNE ASSETS (' + $(if ($Apply) { 'APPLY' } else { 'REPORT ONLY' }) + ') agent ' + $agentVersion + ' ===')
    L ('targets: ' + ($pTargets -join ' | '))
    L ('keep-set from: ' + (Join-Path $stage 'frontend'))
    $prevM = @(Get-ChildItem $manifests -File -Filter '*.txt' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1)
    $prevPath = if ($prevM.Count -gt 0) { $prevM[0].FullName } else { '' }
    if ($prevPath) { L ('keeping previous generation too: ' + (Split-Path $prevPath -Leaf)) }
    $pr = Remove-StaleFrontendAssets -StageFrontend (Join-Path $stage 'frontend') -Targets $pTargets -DoApply ([bool]$Apply) -ExtraKeepList $prevPath
    foreach ($w in @($pr.warnings)) { L ('prune WARNING: ' + $w) }
    if (-not $pr.ok) { L ('prune SKIPPED: ' + $pr.reason); exit 4 }
    L ('stale build files=' + $pr.stale + ' freedMB=' + $pr.freedMB + ' kept=' + $pr.kept + ' fromPrevGen=' + $pr.extraKept + ' skippedNonBuild=' + $pr.skippedNonBuild)
    exit 0
}

if ($Mode -eq 'Rollback') {
    if (-not $BackupName) {
        $latest = Get-ChildItem $backups -Directory | Sort-Object Name -Descending | Select-Object -First 1
        if (-not $latest) { L 'no backups found'; exit 4 }
        $BackupName = $latest.Name
    }
    $bk = Join-Path $backups $BackupName
    if (-not (Test-Path $bk)) { L ('backup not found: ' + $BackupName); exit 4 }
    L ('=== ROLLBACK to ' + $BackupName + ' (agent ' + $agentVersion + ') ===')
    if ($StopCommand) { cmd /c ($StopCommand + ' <nul >nul 2>&1'); Start-Sleep 5 }
    $bkJar = Join-Path $bk 'smart-erp.jar'
    if ($JarTarget -and (Test-Path $bkJar)) {
        New-Item -ItemType Directory -Path (Split-Path $JarTarget -Parent) -Force | Out-Null
        Copy-Item $bkJar $JarTarget -Force; L ('jar restored -> ' + $JarTarget)
    }
    $bkFe = Join-Path $bk 'frontend'
    if ($FrontendTargets -and (Test-Path $bkFe)) {
        foreach ($t in $FrontendTargets.Split(';')) {
            if ($t -and (Test-Path $t)) {
                Get-ChildItem $bkFe -Recurse -File | ForEach-Object {
                    $rel = $_.FullName.Substring($bkFe.Length + 1)
                    $dst = Join-Path $t $rel
                    $dstDir = Split-Path $dst -Parent
                    if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
                    Copy-Item $_.FullName $dst -Force
                }
                L ('frontend restored -> ' + $t)
            }
        }
    }
    if ($StartCommand) { cmd /c ($StartCommand + ' <nul >nul 2>&1') }
    L 'rollback restart issued, verifying health ...'
    if (@(HealthCheck 24) -contains $true) { L '=== ROLLBACK SUCCESS (healthy) ==='; exit 0 }
    L '!!! ROLLBACK DONE BUT SERVICE UNHEALTHY - MANUAL ATTENTION REQUIRED !!!'
    exit 3
}

# ============ Mode: Update ============
if (-not (Test-Path $incoming)) { L 'incoming.zip not found'; exit 4 }
if (-not $JarTarget) { L 'JarTarget required'; exit 4 }
L ('=== UPDATE start. jar->' + $JarTarget + ' (agent ' + $agentVersion + ') ===')

# 1. MD5 integrity verify - BEFORE touching anything (transfer corruption guard)
$zipMd5Actual = ''
try { $zipMd5Actual = (Get-FileHash $incoming -Algorithm MD5).Hash.ToLower() } catch { L ('md5 compute failed: ' + $_.Exception.Message); exit 4 }
if ($ZipMd5) {
    if ($zipMd5Actual -ne $ZipMd5.ToLower()) {
        L ('MD5 MISMATCH: expected ' + $ZipMd5.ToLower() + ' actual ' + $zipMd5Actual + ' - ABORT (nothing touched)')
        exit 4
    }
    L ('md5 verified ok: ' + $zipMd5Actual)
} else {
    L ('md5 verify SKIPPED (no -ZipMd5 param), actual=' + $zipMd5Actual)
}

# 2. unzip to stage (CRC-validated by .NET; any corruption aborts before modification)
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($incoming, $stage)
} catch {
    L ('zip extract FAILED (corrupt archive?): ' + $_.Exception.Message + ' - ABORT (nothing touched)')
    exit 4
}
if (-not (Test-Path (Join-Path $stage 'smart-erp.jar'))) { L 'stage missing smart-erp.jar - ABORT'; exit 4 }
$feTargets = @($FrontendTargets.Split(';') | Where-Object { $_ })
if ($feTargets.Count -gt 0 -and -not (Test-Path (Join-Path $stage 'frontend'))) { L 'stage missing frontend dir but FrontendTargets set - ABORT'; exit 4 }
L 'zip extracted + validated'

# 2.5 SQL KIT GATE (deploy kit v2) - runs BEFORE any file is touched:
#     sql/always/ : every deploy (menu sync converges target sys_menu to golden source)
#     sql/once/   : once per server, tracked in deploy_sql_log (filename = key)
#     ANY failure -> exit 5, jar/frontend NEVER touched (rollback unneeded).
#     Legacy zip without sql/ dir -> skip (old packages still deployable).
$sqlAlwaysDir = Join-Path $stage 'sql\always'
$sqlOnceDir   = Join-Path $stage 'sql\once'
$kitAlways = @(Get-ChildItem $sqlAlwaysDir -Filter '*.sql' -ErrorAction SilentlyContinue)
$kitOnce   = @(Get-ChildItem $sqlOnceDir   -Filter '*.sql' -ErrorAction SilentlyContinue)
if ($kitAlways.Count -eq 0 -and $kitOnce.Count -eq 0) {
    L 'sql kit: ABSENT in package (legacy zip) - skipped, menus NOT synced this deploy'
} elseif (-not ($DbName -and $DbUser -and $DbPassword)) {
    L 'sql kit present but -DbName/-DbUser/-DbPassword missing - ABORT (exit 5). Fill database config of this server in deploy console first.'
    exit 5
} else {
    $script:psqlExe = Find-Psql
    if (-not $script:psqlExe) { L 'psql.exe not found on server - ABORT (exit 5)'; exit 5 }
    $env:PGPASSWORD = $DbPassword
    $env:PGCLIENTENCODING = 'UTF8'
    L ('sql kit: always=' + $kitAlways.Count + ' once=' + $kitOnce.Count + ' via ' + $script:psqlExe)
    try {
        # 2.5.1 bookkeeping table (idempotent)
        $t0 = & $script:psqlExe -h localhost -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c 'CREATE TABLE IF NOT EXISTS deploy_sql_log (script_name VARCHAR(200) PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);' 2>&1
        foreach ($ln in $t0) { if ("$ln".Trim()) { L ('sql: ' + $ln) } }
        if ($LASTEXITCODE -ne 0) { L 'deploy_sql_log create FAILED - ABORT (exit 5)'; exit 5 }

        # 2.5.2 always scripts: run EVERY deploy, sorted by filename
        foreach ($f in ($kitAlways | Sort-Object Name)) {
            L ('sql always >>> ' + $f.Name)
            Invoke-SqlFile $f.FullName
            if ($script:SqlRc -ne 0) {
                L ('sql always FAILED: ' + $f.Name + ' - ABORT (exit 5), NOTHING touched')
                exit 5
            }
        }

        # 2.5.3 once scripts: skip if already in deploy_sql_log, record after success
        foreach ($f in ($kitOnce | Sort-Object Name)) {
            $safeName = $f.Name.Replace("'", "''")
            $chk = (& $script:psqlExe -h localhost -U $DbUser -d $DbName -t -A -c ('SELECT COUNT(1) FROM deploy_sql_log WHERE script_name = ' + "'" + $safeName + "'") 2>&1) -join ''
            if ("$chk".Trim() -eq '1') { L ('sql once --- ' + $f.Name + ' already applied, skip'); continue }
            L ('sql once >>> ' + $f.Name)
            Invoke-SqlFile $f.FullName
            if ($script:SqlRc -ne 0) {
                L ('sql once FAILED: ' + $f.Name + ' - ABORT (exit 5), NOTHING touched')
                exit 5
            }
            $ins = & $script:psqlExe -h localhost -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c ('INSERT INTO deploy_sql_log (script_name) VALUES (' + "'" + $safeName + "') ON CONFLICT (script_name) DO NOTHING;") 2>&1
            if ($LASTEXITCODE -ne 0) {
                L ('deploy_sql_log record FAILED for ' + $f.Name + ' - ABORT (exit 5, script WILL re-run next deploy, idempotent)')
                exit 5
            }
        }
        L 'sql kit ok (menus synced + upgrade scripts applied)'
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue
    }
}

# 3. backup current (timestamped) - NEVER deleted by rollback path
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$bk = Join-Path $backups $ts
New-Item -ItemType Directory -Path $bk -Force | Out-Null
if (Test-Path $JarTarget) { Copy-Item $JarTarget (Join-Path $bk 'smart-erp.jar') -Force; L 'backup: jar' }
foreach ($t in $feTargets) {
    if (Test-Path $t) {
        $safe = ($t -replace '[:/\\]', '_')
        Copy-Item $t (Join-Path $bk ('frontend' + $safe)) -Recurse -Force
        L ('backup: frontend ' + $t)
        break
    }
}

# 4. PRE-STAGE while service still runs (downtime window shrinks to rename speed):
#    jar  -> smart-erp.jar.new sibling of target
#    each frontend target -> sibling dir '<t>.__incoming' (= full copy of current target
#    + new files overlaid, so non-ERP files in nginx html survive the swap)
$stageJar = Join-Path $stage 'smart-erp.jar'
$jarDir = Split-Path $JarTarget -Parent
if (-not (Test-Path $jarDir)) { New-Item -ItemType Directory -Path $jarDir -Force | Out-Null }
$jarNew = Join-Path $jarDir 'smart-erp.jar.new'
if (Test-Path $jarNew) { Remove-Item $jarNew -Force }
Copy-Item $stageJar $jarNew -Force
L 'pre-stage: jar (.new sibling ready)'
$stageFe = Join-Path $stage 'frontend'
foreach ($t in $feTargets) {
    $inc = $t + '.__incoming'
    if (Test-Path $inc) { Remove-Item $inc -Recurse -Force }   # stale leftover from a crashed run
    if (Test-Path $t) { Copy-Item $t $inc -Recurse -Force } else { New-Item -ItemType Directory -Path $inc -Force | Out-Null }
    if (Test-Path $stageFe) {
        Get-ChildItem $stageFe -Recurse -File | ForEach-Object {
            $rel = $_.FullName.Substring($stageFe.Length + 1)
            $dst = Join-Path $inc $rel
            $dstDir = Split-Path $dst -Parent
            if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
            Copy-Item $_.FullName $dst -Force
        }
    }
    L ('pre-stage: frontend incoming dir ready -> ' + $inc)
}

# 5. stop
if ($StopCommand) { L ('stop: ' + $StopCommand); cmd /c ($StopCommand + ' <nul >nul 2>&1'); Start-Sleep 5 }

# 6. ATOMIC jar switch (same-directory rename, milliseconds)
try {
    Move-Item $jarNew $JarTarget -Force
    L 'jar swapped (rename)'
} catch {
    L ('jar rename failed (' + $_.Exception.Message + ') - fallback copy')
    Copy-Item $stageJar $JarTarget -Force
    Remove-Item $jarNew -Force -ErrorAction SilentlyContinue
}

# 7. ATOMIC frontend switch per target (two renames); fallback = legacy file-level copy
foreach ($t in $feTargets) {
    $inc = $t + '.__incoming'
    if (-not (Test-Path $inc)) { L ('incoming dir missing for ' + $t + ' - skip target'); continue }
    if (Test-Path $t) {
        $prev = $t + '.__prev'
        if (Test-Path $prev) { Remove-Item $prev -Recurse -Force }
        $swapped = $false
        try {
            Rename-Item -Path $t -NewName (Split-Path $prev -Leaf)
            Rename-Item -Path $inc -NewName (Split-Path $t -Leaf)
            $swapped = $true
            L ('frontend swapped (rename) -> ' + $t)
        } catch {
            L ('rename swap FAILED for ' + $t + ' (' + $_.Exception.Message + ') - fallback to file-level copy')
            if ((Test-Path $prev) -and (-not (Test-Path $t))) { Rename-Item -Path $prev -NewName (Split-Path $t -Leaf) }
        }
        if (-not $swapped -and (Test-Path $stageFe)) {
            Get-ChildItem $stageFe -Recurse -File | ForEach-Object {
                $rel = $_.FullName.Substring($stageFe.Length + 1)
                $dst = Join-Path $t $rel
                $dstDir = Split-Path $dst -Parent
                if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
                Copy-Item $_.FullName $dst -Force
            }
            L ('frontend replaced (file copy fallback) -> ' + $t)
        }
    } else {
        try {
            Move-Item $inc $t -Force
            L ('frontend created (move) -> ' + $t)
        } catch {
            L ('frontend move failed for ' + $t + ': ' + $_.Exception.Message)
        }
    }
}

# 7.5 schema sync (idempotent column ensure - BEST EFFORT, never blocks deployment)
#    Column list MUST stay in sync with backend SchemaUpgradeEnsurer.ensureBusinessDbColumns().
#    Root cause this guards against (2026-08-23 8cloud incident): old DB missing new columns
#    + app-level ensure failing on permissions -> userinfo query dies -> login stuck at
#    "entering" screen. If this step fails, deployment still proceeds: app-level
#    SchemaUpgradeEnsurer retries on startup, and health check + auto-rollback still guard.
if ($DbName -and $DbUser -and $DbPassword) {
    $psqlExe = Find-Psql
    if (-not $psqlExe) {
        L 'schema sync SKIPPED: psql.exe not found on server'
    } else {
        $sqlFile = Join-Path $base 'schema-sync.sql'
        $ddl = @(
            'ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_section VARCHAR(100);',
            'ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_type VARCHAR(20);',
            'ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_options TEXT;',
            'ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS version VARCHAR(50);',
            'ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS features JSONB;',
            'ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS route_path VARCHAR(200);',
            'ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS tenant_id BIGINT;',
            'ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS tenant_name VARCHAR(200);',
            'ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS tenant_version VARCHAR(50);',
            'CREATE TABLE IF NOT EXISTS deploy_version (id BIGSERIAL PRIMARY KEY, version_no VARCHAR(50) NOT NULL, build_mode VARCHAR(20) NOT NULL DEFAULT ''full'', note VARCHAR(500), zip_path VARCHAR(500) NOT NULL, zip_size BIGINT, zip_md5 VARCHAR(64), operator VARCHAR(100), created_at TIMESTAMP DEFAULT now());',
            'CREATE UNIQUE INDEX IF NOT EXISTS uk_deploy_version_no ON deploy_version (version_no);',
            'ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS current_version VARCHAR(50);',
            'ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS version_deployed_at TIMESTAMP;',
            'ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS build_mode VARCHAR(20) DEFAULT ''full'';',
            'ALTER TABLE deploy_log ADD COLUMN IF NOT EXISTS version_no VARCHAR(50);'
        )
        [System.IO.File]::WriteAllLines($sqlFile, $ddl, [System.Text.Encoding]::ASCII)
        $env:PGPASSWORD = $DbPassword
        $env:PGCLIENTENCODING = 'UTF8'
        L ('schema sync: ' + $DbName + ' as ' + $DbUser + ' (' + $psqlExe + ')')
        $syncOut = & $psqlExe -h localhost -U $DbUser -d $DbName -f $sqlFile 2>&1
        foreach ($ln in $syncOut) { if ("$ln".Trim()) { L ('schema: ' + $ln) } }
        if ($LASTEXITCODE -ne 0) {
            L ('schema sync FAILED (exit ' + $LASTEXITCODE + ') - continue deploy, app-level SchemaUpgradeEnsurer will retry on startup')
        } else {
            L 'schema sync ok (missing columns ensured, existing skipped)'
        }
        Remove-Item -Path $sqlFile -Force -ErrorAction SilentlyContinue
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
} else {
    L 'schema sync skipped: no -DbName/-DbUser/-DbPassword params (legacy server config)'
}

# 7.5.5 platform DB init (multi-tenant): create erp_platform + core tables + DEFAULT tenant/admin seed
$platformDb = 'erp_platform'
if ($DbName -and $DbUser -and $DbPassword) {
    $psqlExe = Find-Psql
    if (-not $psqlExe) {
        L 'platform DB init SKIPPED: psql not found on server'
    } else {
        $env:PGPASSWORD = $DbPassword
        $env:PGCLIENTENCODING = 'UTF8'
        try {
            $pfExists = ((& $psqlExe -h localhost -U $DbUser -d $DbName -t -A -c "SELECT COUNT(1) FROM pg_database WHERE datname = '$platformDb'" 2>&1) -join '').Trim()
            if ($pfExists -eq '0' -or $pfExists -eq '') {
                & $psqlExe -h localhost -U $DbUser -d $DbName -c "CREATE DATABASE $platformDb" 2>&1 | Out-Null
                L ('platform DB created: ' + $platformDb)
            }
            $pfSql = Join-Path $base 'platform-init.sql'
            $pfDdl = @(
                'CREATE TABLE IF NOT EXISTS sys_tenant (tenant_id BIGSERIAL PRIMARY KEY, tenant_code VARCHAR(50) NOT NULL UNIQUE, tenant_name VARCHAR(200) NOT NULL, db_name VARCHAR(100) NOT NULL, db_host VARCHAR(200) DEFAULT ''localhost'', db_port INT DEFAULT 5432, db_username VARCHAR(100) DEFAULT ''erp_user'', db_password VARCHAR(200) DEFAULT ''Erp@2026'', contact_person VARCHAR(100), contact_phone VARCHAR(50), contact_email VARCHAR(200), address VARCHAR(500), max_users INT DEFAULT 50, status VARCHAR(20) DEFAULT ''active'', expire_date DATE, remark VARCHAR(500), version VARCHAR(50), features TEXT, trial_days INT, created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP, created_by VARCHAR(50), updated_by VARCHAR(50));',
                'CREATE TABLE IF NOT EXISTS sys_user_tenant (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, is_admin_in_tenant SMALLINT DEFAULT 0, last_login_time TIMESTAMP, last_login_ip VARCHAR(50), status VARCHAR(20) DEFAULT ''active'', created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP, UNIQUE(user_id, tenant_id));',
                'CREATE TABLE IF NOT EXISTS sys_tenant_license (id BIGSERIAL PRIMARY KEY, tenant_id BIGINT NOT NULL, license_key VARCHAR(500) NOT NULL, license_content TEXT, mac_address VARCHAR(100), issued_date TIMESTAMP DEFAULT now(), expire_date DATE NOT NULL, max_user_count INT DEFAULT 50, modules VARCHAR(500), status VARCHAR(20) DEFAULT ''valid'', issued_by VARCHAR(50), created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP);',
                'CREATE TABLE IF NOT EXISTS sys_user (user_id BIGSERIAL PRIMARY KEY, user_code VARCHAR(50) NOT NULL UNIQUE, username VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(200) NOT NULL, real_name VARCHAR(100) NOT NULL, email VARCHAR(200), mobile_phone VARCHAR(20), avatar_url VARCHAR(500), user_type VARCHAR(20) DEFAULT ''普通用户'', auth_type VARCHAR(20) DEFAULT ''本地认证'', is_admin SMALLINT DEFAULT 0, is_enabled SMALLINT DEFAULT 1, is_locked SMALLINT DEFAULT 0, lock_time TIMESTAMP, password_expire_time TIMESTAMP, password_change_time TIMESTAMP, last_login_time TIMESTAMP, last_login_ip VARCHAR(50), login_attempts INT DEFAULT 0, remark VARCHAR(500), status VARCHAR(10) DEFAULT ''启用'', created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP, created_by VARCHAR(50), updated_by VARCHAR(50));',
                'CREATE TABLE IF NOT EXISTS sys_platform_role (role_id BIGSERIAL PRIMARY KEY, role_code VARCHAR(50) NOT NULL UNIQUE, role_name VARCHAR(100) NOT NULL, is_system SMALLINT DEFAULT 0, status VARCHAR(10) DEFAULT ''启用'', created_at TIMESTAMP DEFAULT now());',
                'CREATE TABLE IF NOT EXISTS sys_platform_user_role (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, UNIQUE(user_id, role_id));',
                'INSERT INTO sys_user (user_code, username, password, real_name, user_type, is_admin, is_enabled, status) SELECT ''ADMIN001'', ''admin'', ''$2a$10$3DtK0FbY/C9DtMcQEb9q5ujzx30FTmYkREj4nlTo7xIMzOOWkdBRa'', ''System Administrator'', ''System User'', 1, 1, ''启用'' WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = ''admin'');',
                'INSERT INTO sys_tenant (tenant_code, tenant_name, db_name, db_host, db_port, db_username, db_password, max_users, status, expire_date) SELECT ''DEFAULT'', ''默认公司'', ''erp_db'', ''localhost'', 5432, ''erp_user'', ''Erp@2026'', 50, ''active'', ''2026-12-31'' WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE tenant_code = ''DEFAULT'');',
                'INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant) SELECT u.user_id, t.tenant_id, 1 FROM sys_user u, sys_tenant t WHERE u.username = ''admin'' AND t.tenant_code = ''DEFAULT'' AND NOT EXISTS (SELECT 1 FROM sys_user_tenant ut WHERE ut.user_id = u.user_id AND ut.tenant_id = t.tenant_id);',
                'INSERT INTO sys_platform_role (role_code, role_name, is_system) SELECT ''PLATFORM_ADMIN'', ''平台超级管理员'', 1 WHERE NOT EXISTS (SELECT 1 FROM sys_platform_role WHERE role_code = ''PLATFORM_ADMIN'');',
                'INSERT INTO sys_platform_user_role (user_id, role_id) SELECT u.user_id, r.role_id FROM sys_user u, sys_platform_role r WHERE u.username = ''admin'' AND r.role_code = ''PLATFORM_ADMIN'' AND NOT EXISTS (SELECT 1 FROM sys_platform_user_role pur WHERE pur.user_id = u.user_id AND pur.role_id = r.role_id);'
            )
            [System.IO.File]::WriteAllLines($pfSql, $pfDdl, [System.Text.Encoding]::UTF8)
            $pfOut = & $psqlExe -h localhost -U $DbUser -d $platformDb -v ON_ERROR_STOP=1 -f $pfSql 2>&1
            foreach ($ln in $pfOut) { if ("$ln".Trim()) { L ('platform: ' + $ln) } }
            Remove-Item $pfSql -Force -ErrorAction SilentlyContinue
            L 'platform DB tables + DEFAULT tenant/admin ensured'
        } catch {
            L ('platform DB init WARNING (non-fatal, deploy continues): ' + $_.Exception.Message)
        } finally {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
            Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue
        }
    }
}

# 7.6 tenant template rebuild (multi-tenant): clone table structure + seed data from
#     business DB erp_db to template DB erp_tenant_template. Only runs when -TemplateDb is set.
#     Safety: skip if any tenant row points at the template DB (protect live tenant clone source).
#     Non-fatal: failure does NOT block deployment (app-level SchemaGuard already aligned erp_db).
if ($TemplateDb -and $DbName -and $DbUser -and $DbPassword) {
    $psqlExe = Find-Psql
    $pgDumpExe = Find-PgDump
    if (-not $psqlExe -or -not $pgDumpExe) {
        L 'tenant template rebuild SKIPPED: psql/pg_dump not found on server'
    } else {
        $env:PGPASSWORD = $DbPassword
        $env:PGCLIENTENCODING = 'UTF8'
        try {
            # A brand-new install may not have sys_tenant in the business DB yet (local erp_db proves it happens).
            # A bare COUNT(1) would return an error string there, which the old guard mistook for "has refs" and
            # then skipped the rebuild forever. Probe the table first (always safe), count only when present.
            # NOTE: a single CASE ... to_regclass(...) does NOT work - PG validates the table name at plan time
            # even when the branch is not taken (verified 2026-09-12).
            $hasTenantTbl = ((& $psqlExe -h localhost -U $DbUser -d $DbName -t -A -c "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema='public' AND table_name='sys_tenant'" 2>&1) -join '').Trim()
            if ($hasTenantTbl -eq '1') {
                $refCount = ((& $psqlExe -h localhost -U $DbUser -d $DbName -t -A -c "SELECT COUNT(1) FROM sys_tenant WHERE db_name = '$TemplateDb'" 2>&1) -join '').Trim()
            } elseif ($hasTenantTbl -eq '0') {
                L 'tenant template guard: business DB has no sys_tenant yet -> assume 0 tenant references'
                $refCount = '0'
            } else {
                $refCount = $hasTenantTbl
            }
            if ($refCount -notmatch '^\d+$') {
                L ('tenant template rebuild SKIPPED: cannot read sys_tenant reference count -> ' + $refCount)
            } elseif ($refCount -eq '0') {
                # Every step below reports truthfully: a silent failure here used to be logged
                # as "recreated" while the template stayed missing, and tenant creation then
                # failed at runtime with "tenant template db does not exist" (2026-09-12).
                $tplFail = ''
                & $psqlExe -h localhost -U $DbUser -d $DbName -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$TemplateDb' AND pid <> pg_backend_pid()" 2>&1 | Out-Null
                & $psqlExe -h localhost -U $DbUser -d $DbName -c "DROP DATABASE IF EXISTS $TemplateDb WITH (FORCE)" 2>&1 | Out-Null
                & $psqlExe -h localhost -U $DbUser -d $DbName -c "CREATE DATABASE $TemplateDb" 2>&1 | Out-Null
                # CREATE DATABASE needs CREATEDB on the login role -> verify instead of trusting it
                $tplDbCnt = ((& $psqlExe -h localhost -U $DbUser -d $DbName -t -A -c "SELECT COUNT(1) FROM pg_database WHERE datname = '$TemplateDb'" 2>&1) -join '').Trim()
                if ($tplDbCnt -eq '1') {
                    L ('tenant template recreated: ' + $TemplateDb)
                } else {
                    $tplFail = ('could not create ' + $TemplateDb + ' -> FIX: run as superuser once: ALTER ROLE ' + $DbUser + ' CREATEDB;  then re-deploy')
                }

                if (-not $tplFail) {
                    $schemaFile = Join-Path $base 'tenant_template_schema.sql'
                    & $pgDumpExe -h localhost -U $DbUser -s --no-owner --no-privileges -f $schemaFile $DbName 2>&1 | Out-Null
                    $tplDumpRc = $LASTEXITCODE
                    & $psqlExe -h localhost -U $DbUser -d $TemplateDb -v ON_ERROR_STOP=1 -f $schemaFile 2>&1 | Out-Null
                    $tplLoadRc = $LASTEXITCODE
                    Remove-Item $schemaFile -Force -ErrorAction SilentlyContinue
                    if ($tplDumpRc -eq 0 -and $tplLoadRc -eq 0) { L 'tenant template schema cloned' }
                    else { $tplFail = ('schema clone failed (pg_dump=' + $tplDumpRc + ', psql=' + $tplLoadRc + ')') }
                }

                if (-not $tplFail) {
                    $seedFile = Join-Path $base 'tenant_template_seed.sql'
                    $seedTables = @('sys_menu','sys_role','sys_role_permission','sys_role_field_permission','sys_dict_type','sys_dict_data','sys_serial_no_config','sys_config')
                    $dumpArgs = @('-h','localhost','-U',$DbUser,'--data-only','--column-inserts','--no-owner','--no-privileges')
                    foreach ($t in $seedTables) { $dumpArgs += @('-t', $t) }
                    $dumpArgs += @('-f', $seedFile, $DbName)
                    & $pgDumpExe @dumpArgs 2>&1 | Out-Null
                    $tplSeedDumpRc = $LASTEXITCODE
                    & $psqlExe -h localhost -U $DbUser -d $TemplateDb -v ON_ERROR_STOP=1 -f $seedFile 2>&1 | Out-Null
                    $tplSeedLoadRc = $LASTEXITCODE
                    Remove-Item $seedFile -Force -ErrorAction SilentlyContinue
                    if ($tplSeedDumpRc -eq 0 -and $tplSeedLoadRc -eq 0) { L ('tenant template seed cloned: ' + ($seedTables -join ', ')) }
                    else { $tplFail = ('seed clone failed (pg_dump=' + $tplSeedDumpRc + ', psql=' + $tplSeedLoadRc + ')') }
                }

                if (-not $tplFail) {
                    & $psqlExe -h localhost -U $DbUser -d $TemplateDb -v ON_ERROR_STOP=1 -c "DELETE FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('SUPER_ADMIN','ADMIN','MANAGER')); DELETE FROM sys_user_role WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('SUPER_ADMIN','ADMIN','MANAGER')); DELETE FROM sys_role WHERE role_code IN ('SUPER_ADMIN','ADMIN','MANAGER'); DELETE FROM sys_role_permission WHERE menu_id IN (SELECT menu_id::text FROM sys_menu WHERE route_path LIKE '/admin%'); DELETE FROM sys_menu WHERE route_path LIKE '/admin%';" 2>&1 | Out-Null
                    L 'tenant template platform objects stripped'

                    # delivery evidence: non-empty template that carries no users (same check as
                    # database/schema/create_tenant_template.ps1)
                    $tplTables = ((& $psqlExe -h localhost -U $DbUser -d $TemplateDb -t -A -c "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'" 2>&1) -join '').Trim()
                    $tplMenus = ((& $psqlExe -h localhost -U $DbUser -d $TemplateDb -t -A -c "SELECT COUNT(1) FROM sys_menu" 2>&1) -join '').Trim()
                    $tplUsers = ((& $psqlExe -h localhost -U $DbUser -d $TemplateDb -t -A -c "SELECT COUNT(1) FROM sys_user" 2>&1) -join '').Trim()
                    if ($tplTables -match '^\d+$' -and [int]$tplTables -gt 0 -and $tplUsers -eq '0') {
                        L ('tenant template READY: tables=' + $tplTables + ' menus=' + $tplMenus + ' users=' + $tplUsers)
                    } else {
                        $tplFail = ('verify failed, expected tables>0 and users=0 but got tables=' + $tplTables + ' menus=' + $tplMenus + ' users=' + $tplUsers)
                    }
                }

                if ($tplFail) { L ('tenant template rebuild FAILED: ' + $tplFail) }
            } else {
                L ('tenant template rebuild SKIPPED: ' + $refCount + ' tenant row(s) point at ' + $TemplateDb + ' (protect live clone source)')
            }
        } catch {
            L ('tenant template rebuild WARNING (non-fatal, deploy continues): ' + $_.Exception.Message)
        } finally {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
            Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue
        }
    }
}

# 8. start + health check
if ($StartCommand) { L ('start: ' + $StartCommand); cmd /c ($StartCommand + ' <nul >nul 2>&1') }
L 'health check loop (max 120s)...'
# PITFALL: HealthCheck logs through L() (Write-Output), so it returns an ARRAY of log lines plus its
# $true/$false value. A non-empty array is ALWAYS truthy in PowerShell, so a plain
# "if (HealthCheck 24)" passed even when the check failed - the agent log shows
# "health check failed: ..." immediately followed by "=== UPDATE SUCCESS ===" on every 8cloud deploy
# from 2026-09-07, i.e. the auto-rollback net was dead code. Test for $true explicitly.
$healthOk = @(HealthCheck 24) -contains $true
if ($healthOk) {
    # success cleanup: swap leftovers + prune old backups (keep latest 5)
    foreach ($t in $feTargets) {
        $prev = $t + '.__prev'
        if (Test-Path $prev) { Remove-Item $prev -Recurse -Force; L ('cleanup prev dir: ' + $prev) }
        $inc = $t + '.__incoming'
        if (Test-Path $inc) { Remove-Item $inc -Recurse -Force -ErrorAction SilentlyContinue }
    }
    $old = @(Get-ChildItem $backups -Directory | Sort-Object Name -Descending | Select-Object -Skip 5)
    foreach ($o in $old) { Remove-Item $o.FullName -Recurse -Force -ErrorAction SilentlyContinue; L ('prune old backup: ' + $o.Name) }

    # drop older builds' hashed chunks so the frontend dirs stop growing on every deploy, while
    # keeping the CURRENT build plus the PREVIOUS generation (a tab still on the old index.html can
    # then lazy-load its own chunks). Success path only (health check already passed); any failure
    # here is a warning and never changes the deploy result, the exit code, or the rollback data.
    try {
        $stageFe = Join-Path $stage 'frontend'
        $curManifest = Join-Path $manifests ($ts + '.txt')
        if (Test-Path $stageFe) {
            $rel = @(Get-ChildItem $stageFe -Recurse -File | ForEach-Object { $_.FullName.Substring($stageFe.Length + 1).ToLower() })
            [IO.File]::WriteAllLines($curManifest, $rel, (New-Object System.Text.UTF8Encoding($false)))
            L ('build manifest written: ' + (Split-Path $curManifest -Leaf) + ' files=' + $rel.Count)
        }
        $prevM = @(Get-ChildItem $manifests -File -Filter '*.txt' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Where-Object { $_.FullName -ne $curManifest } | Select-Object -First 1)
        $prevPath = if ($prevM.Count -gt 0) { $prevM[0].FullName } else { '' }
        if ($prevPath) { L ('keeping previous generation too: ' + (Split-Path $prevPath -Leaf)) }
        $pr = Remove-StaleFrontendAssets -StageFrontend $stageFe -Targets $feTargets -DoApply $true -ExtraKeepList $prevPath
        foreach ($w in @($pr.warnings)) { L ('assets prune WARNING: ' + $w) }
        if ($pr.ok) {
            L ('assets prune: removed ' + $pr.stale + ' stale file(s) (' + $pr.freedMB + 'MB), kept ' + $pr.kept + ' (prevGen extra ' + $pr.extraKept + '), skipped non-build ' + $pr.skippedNonBuild)
        } else {
            L ('assets prune SKIPPED: ' + $pr.reason)
        }
        $oldM = @(Get-ChildItem $manifests -File -Filter '*.txt' | Sort-Object Name -Descending | Select-Object -Skip 3)
        foreach ($o in $oldM) { Remove-Item $o.FullName -Force -ErrorAction SilentlyContinue; L ('prune old manifest: ' + $o.Name) }
    } catch {
        L ('assets prune WARNING (non-fatal, deploy unaffected): ' + $_.Exception.Message)
    }

    L '=== UPDATE SUCCESS ==='
    exit 0
}

# 9. AUTO ROLLBACK (+ post-rollback health verification)
L '!!! HEALTH CHECK FAILED - AUTO ROLLBACK !!!'
if ($StopCommand) { cmd /c ($StopCommand + ' <nul >nul 2>&1'); Start-Sleep 5 }
$bkJar = Join-Path $bk 'smart-erp.jar'
if (Test-Path $bkJar) { Copy-Item $bkJar $JarTarget -Force; L 'jar rolled back' }
$renamedBack = @{}
foreach ($t in $feTargets) {
    $prev = $t + '.__prev'
    if (Test-Path $prev) {
        # instant restore: drop the swapped-in new dir, rename prev back
        try {
            if (Test-Path $t) { Remove-Item $t -Recurse -Force }
            Rename-Item -Path $prev -NewName (Split-Path $t -Leaf)
            $renamedBack[$t] = $true
            L ('frontend rolled back (rename) -> ' + $t)
        } catch {
            L ('rename rollback FAILED for ' + $t + ' (' + $_.Exception.Message + ') - fallback to backup file copy')
            if ((Test-Path $prev) -and (-not (Test-Path $t))) { Rename-Item -Path $prev -NewName (Split-Path $t -Leaf) }
        }
    }
}
# legacy fallback: file-level restore from timestamped backup.
# Fires when: (a) update used file-copy fallback (no __prev dir), or (b) worst case -
# target dir missing entirely after failed renames -> full rebuild from backup.
$bkFeRoot = Get-ChildItem $bk -Directory | Where-Object { $_.Name -like 'frontend*' } | Select-Object -First 1
if ($bkFeRoot) {
    foreach ($t in $feTargets) {
        if ($renamedBack[$t]) { continue }
        if (-not (Test-Path $t)) { New-Item -ItemType Directory -Path $t -Force | Out-Null }
        Get-ChildItem $bkFeRoot.FullName -Recurse -File | ForEach-Object {
            $rel = $_.FullName.Substring($bkFeRoot.FullName.Length + 1)
            $dst = Join-Path $t $rel
            $dstDir = Split-Path $dst -Parent
            if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
            Copy-Item $_.FullName $dst -Force
        }
        L ('frontend rolled back (backup file copy) -> ' + $t)
    }
}
if ($StartCommand) { cmd /c ($StartCommand + ' <nul >nul 2>&1') }
L 'rollback restart issued, verifying health ...'
if (@(HealthCheck 12) -contains $true) { L '=== ROLLED BACK + ROLLBACK HEALTHY (exit 2) ==='; exit 2 }
L '!!! ROLLED BACK BUT SERVICE STILL UNHEALTHY - MANUAL ATTENTION REQUIRED (exit 3) !!!'
exit 3
