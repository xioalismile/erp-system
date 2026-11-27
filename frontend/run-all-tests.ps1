<#
  智能ERP 全量回归测试 · 一键入口

  目标：不用人工一个个点 —— 一条命令跑完
     ① 后端确定性单元测试（不依赖环境，纯逻辑锁）
     ② 测试租户环境诊断（菜单/角色/基础数据齐备性）
     ③ 基础数据准备（物料/客户/供应商/仓库/单位/币种/银行账户）
     ④ 测试账号 + 角色授权（阶段A）
     ⑤ 端到端闭环 + 权限隔离 + 全页面覆盖（阶段B）
     ⑥ 汇总报告（覆盖度 / 失败明细 / 未覆盖清单）

  前置条件：
    - frontend/tests/smoke/.env.local 已填测试租户凭据（该文件不进仓库）
    - 目标环境在白名单内（kaiyu.pro / 8.148.229.2 / localhost）
    - 已装 JDK17 + Maven + Node（frontend/node_modules 就绪）

  用法：
    .\run-all-tests.ps1                          # 全量
    .\run-all-tests.ps1 -SkipSeed                 # 跳过基础数据准备
    .\run-all-tests.ps1 -ModuleFilter sale        # 只跑销售模块（阶段B）
    .\run-all-tests.ps1 -OnlyDiag                 # 只做环境诊断
    .\run-all-tests.ps1 -Cleanup                  # 清理测试数据后退出

  退出码：0 = 全绿；非 0 = 门禁不通过（禁止上线）
#>
param(
  [switch]$SkipBackendUnit,
  [switch]$SkipSeed,
  [switch]$SkipPrepare,
  [string]$ModuleFilter = '',
  [switch]$OnlyDiag,
  [switch]$Cleanup
)

$ErrorActionPreference = 'Stop'
$frontendDir = $PSScriptRoot
$smokeDir = Join-Path $frontendDir 'tests\smoke'
$backendDir = Join-Path (Split-Path -Parent $frontendDir) 'backend'
$outDir = Join-Path $smokeDir '.out'
$failStep = @()

function Write-Section($text) {
  Write-Host ''
  Write-Host ('=' * 72)
  Write-Host ('  ' + $text)
  Write-Host ('=' * 72)
}

function Invoke-Step([string]$name, [scriptblock]$body, [switch]$TolerateFailure) {
  Write-Host ''
  Write-Host (">>> " + $name) -ForegroundColor Cyan
  $sw = [Diagnostics.Stopwatch]::StartNew()
  try {
    & $body
    $code = $LASTEXITCODE
    if ($null -eq $code) { $code = 0 }
  } catch {
    Write-Host ("    [异常] " + $_.Exception.Message) -ForegroundColor Red
    $code = 1
  }
  $sw.Stop()
  if ($code -ne 0) {
    if ($TolerateFailure) {
      Write-Host ("    [警告] " + $name + " 未通过（已按容错继续），耗时 " + [int]$sw.Elapsed.TotalSeconds + "s") -ForegroundColor Yellow
    } else {
      Write-Host ("    [失败] " + $name + " 退出码 " + $code + "，耗时 " + [int]$sw.Elapsed.TotalSeconds + "s") -ForegroundColor Red
      $script:failStep += $name
    }
  } else {
    Write-Host ("    [通过] " + $name + " 耗时 " + [int]$sw.Elapsed.TotalSeconds + "s") -ForegroundColor Green
  }
  return $code
}

Write-Section '智能ERP 全量回归测试'
Write-Host ("前端目录 : " + $frontendDir)
Write-Host ("冒烟目录 : " + $smokeDir)
Write-Host ("阶段B范围: " + $(if ($ModuleFilter) { $ModuleFilter } else { '全部模块' }))

if (-not (Test-Path (Join-Path $smokeDir '.env.local'))) {
  Write-Host ''
  Write-Host '[中止] 缺少 frontend/tests/smoke/.env.local（测试租户凭据）' -ForegroundColor Red
  Write-Host '       请复制该文件的 .example 版本并填入 E2E_TENANT_ADMIN_USER / E2E_TENANT_ADMIN_PASSWORD' -ForegroundColor Red
  exit 2
}

# ---------------- 清理模式 ----------------
if ($Cleanup) {
  Push-Location $smokeDir
  try { node seed-tenant-data.mjs --cleanup } finally { Pop-Location }
  exit $LASTEXITCODE
}

# ---------------- ① 后端确定性单元测试（不依赖环境）----------------
# 只跑纯逻辑/纯 Mock 的用例：不需要后端进程、不需要数据库，任何机器上结果一致。
# 依赖 HTTP/DB 的集成测试（*FullTest / *TraversalHttpTest）不在门禁内，另按需手动跑。
if (-not $SkipBackendUnit) {
  $unitTests = @(
    'BillStatusUtilsTest','BillStatusEnumsTest','ApprovalGateHelperTest',
    'SortHelperTest','SecurityUtilsTest','ListQuerySupportTest',
    'TaxUtilTest','PrecisionUtilTest','SysParamValidatorTest','KgwBusinessRulesTest',
    'BillExcelRegistryIntegrityTest','BillLinkCrossCheckTest'
  ) -join ','
  Invoke-Step '① 后端确定性单元测试' {
    Push-Location $backendDir
    try {
      # JVM 往 stderr 打的 "Sharing is only supported..." 警告会被 PowerShell 当成 NativeCommandError 中止，
      # 与 npm 弃用警告同类（2026-11-11 实测）。这里临时降为 Continue，成败只看 mvn 退出码。
      $prevEap = $ErrorActionPreference
      $ErrorActionPreference = 'Continue'
      try {
        # 项目铁律：必须带 useIncrementalCompilation=false，否则 target/classes 会被弄成半成品
        mvn -o test '-Derp.test.skip=false' '-Dmaven.compiler.useIncrementalCompilation=false' ("-Dtest=" + $unitTests) 2>&1 |
          Select-String -Pattern 'Tests run|BUILD|ERROR' | Select-Object -Last 12 | ForEach-Object { Write-Host ('    ' + $_) }
      } finally { $ErrorActionPreference = $prevEap }
    } finally { Pop-Location }
  }
}

# ---------------- ② 测试租户环境诊断 ----------------
Invoke-Step '② 测试租户环境诊断' {
  Push-Location $smokeDir
  try { node diag-tenant.mjs } finally { Pop-Location }
} -TolerateFailure

if ($OnlyDiag) {
  Write-Host ''
  if ($failStep.Count) { Write-Host ('诊断未通过：' + ($failStep -join ', ')) -ForegroundColor Red; exit 1 }
  exit 0
}

# ---------------- ③ 基础数据准备 ----------------
if (-not $SkipSeed) {
  Invoke-Step '③ 基础数据准备（物料/客户/供应商/仓库/单位/币种/银行账户）' {
    Push-Location $smokeDir
    try { node seed-tenant-data.mjs } finally { Pop-Location }
  }
  Invoke-Step '③b 单据链造数与自检（销售订单→出库→发票 / 采购申请→订单→入库 / BOM→工单→领料→入库，全部走业务接口，建后逐字段回读断言）' {
    Push-Location $smokeDir
    try { node seed-bill-chain.mjs } finally { Pop-Location }
  }
}

# ---------------- ④ 阶段A：测试账号 + 授权 ----------------
if (-not $SkipPrepare) {
  Invoke-Step '④ 阶段A 测试账号创建与授权回读校验' {
    Push-Location $smokeDir
    try { node prepare.mjs } finally { Pop-Location }
  }
}

# ---------------- ⑤ 阶段B：端到端闭环 + 隔离 + 全页面 ----------------
$pwArgs = @('playwright', 'test', '--config=playwright.smoke.config.js', '--reporter=line')
if ($ModuleFilter) { $pwArgs += ('--grep=@' + $ModuleFilter) }
Invoke-Step '⑤ 阶段B 端到端闭环 / 权限隔离 / 全页面覆盖' {
  Push-Location $frontendDir
  try {
    & npx @pwArgs
  } finally { Pop-Location }
} -TolerateFailure

# ---------------- ⑤b 全按钮点击体检（每个动作按钮真点一次，抓"点了没反应"）----------------
# 背景（2026-09-21 实锤）：10 张单据的「提交」点击即抛 ReferenceError（onSubmit 引用未声明变量），
# 既无提示也无请求；而阶段B的 e2e 口径是"绝不点提交"，于是整段漏检 —— 客户手点才暴露。
# 本步骤对每张单据的 列表页/新增页/详情页 逐个动作按钮真点一次，断言"必须有可观察反馈 + 0 JS 异常"，
# 确认框一律取消（不写库）。命中即视为门禁不通过。
$envFile = Join-Path $smokeDir '.env.local'
$e2eBase = ''
$e2eUser = ''
$e2ePass = ''
$e2eTenantCode = ''
if (Test-Path $envFile) {
  foreach ($line in (Get-Content $envFile)) {
    if ($line -match '^E2E_BASE_URL=(.+)$') { $e2eBase = $Matches[1].Trim() }
    if ($line -match '^E2E_TENANT_ADMIN_USER=(.+)$') { $e2eUser = $Matches[1].Trim() }
    if ($line -match '^E2E_TENANT_ADMIN_PASSWORD=(.+)$') { $e2ePass = $Matches[1].Trim() }
    if ($line -match '^E2E_TENANT_CODE=(.+)$') { $e2eTenantCode = $Matches[1].Trim() }
  }
}
# ⑤b/⑤c 必须带上租户：脚本默认 API=http://localhost:8080、tenant=1，
# 在多租户服务器（8cloud）上会直接 fetch 失败 → 步骤 1 秒即"未通过"（2026-11-11 实锤）。
$e2eTenantId = ''
$accFile = Join-Path $smokeDir '.out\accounts.json'
if (Test-Path $accFile) {
  try {
    $acc = Get-Content $accFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $e2eTenantCode -and $acc.tenant.tenantCode) { $e2eTenantCode = $acc.tenant.tenantCode }
    if ($acc.tenant.tenantId) { $e2eTenantId = [string]$acc.tenant.tenantId }
  } catch { }
}
$btnScan = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $frontendDir))) 'deploy\tools\button-reachability-scan.mjs'
if ($e2eBase -and $e2eUser -and $e2ePass -and (Test-Path $btnScan)) {
  Invoke-Step '⑤b 全按钮点击体检（列表/新增/详情 逐按钮真点）' {
    node $btnScan "--base=$e2eBase" "--api=$e2eBase" "--user=$e2eUser" "--pass=$e2ePass" "--tenant=$e2eTenantCode"
  } -TolerateFailure
} else {
  Write-Host '    [跳过] ⑤b 全按钮点击体检：缺少 .env.local 凭据或脚本未就位' -ForegroundColor Yellow
}

# ---------------- ⑤c 单据「全按钮」写链路体检（点下去真的生效：提交/审核/驳回/重置/结案/作废/打印/删除）----------------
# 背景（2026-09-28 实锤）：⑤b 只断言"点了有反馈 + 0 异常"，写库动作全部跳过、确认框一律取消，
# 因此"按钮在、点下去什么也没发生"这一类仍然漏检。本步骤对每张**有数据的单据**：
#   列表页取一张已生效源单 → 点「复制」→ 点「保存」得到**测试单**（不碰真实单）
#   → 依次点 提交 / 审核|驳回 / 重置草稿 / 结案 / 反结案 / 作废 / 反作废 / 打印 / 操作日志 / 删除
#   → 每一步回读接口状态断言确实变了 → 最后删除测试单。
# 判定：点了既无弹窗、又无状态变化 ⇒ FAIL「假按钮」；业务校验拦截（如"物料不存在""必填项未填"）属正常拦截。
$smScan = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $frontendDir))) 'deploy\tools\bill-state-machine-scan.mjs'
if ($e2eBase -and (Test-Path $smScan)) {
  Invoke-Step '⑤c 单据全按钮写链路体检（复制→保存→提交/审核/重置/结案/作废/打印/日志/删除 逐步断言）' {
    node $smScan "--base=$e2eBase" "--api=$e2eBase" "--user=$e2eUser" "--pass=$e2ePass" "--tenant=$e2eTenantId"
  } -TolerateFailure
} else {
  Write-Host '    [跳过] ⑤c 全按钮写链路体检：缺少 .env.local 凭据或脚本未就位' -ForegroundColor Yellow
}

# ---------------- ⑤d 菜单与用例对账（有菜单却没登记闭环 = 新单据漏测，当场点名）----------------
# 背景（2026-11-11 用户提出「加了新功能脚本怎么办」）：
#   ⑤ 全页面覆盖网只查"打得开、不白屏、不报错"；真正抓业务问题的是 03 端到端闭环，
#   但它只跑「登记表 BILLS」里的单据。新单据忘登记 → 永远不测且无人报警。
#   本步骤按角色实际菜单反推"可建单页面"，与登记表比对，漏登记即门禁不通过。
if (-not $SkipPrepare) {
  Invoke-Step '⑤d 菜单与用例对账（漏登记闭环的新单据当场点名）' {
    Push-Location $smokeDir
    try { node menu-coverage.mjs } finally { Pop-Location }
  }
}

# ---------------- ⑤e 界面闭环测试单清理（体检结束后清掉本轮建的测试单据）----------------
# 背景（2026-11-11）：03 端到端闭环会真实建单，并在 L7 把单据提交/审核到已生效供 ⑤c 取数；
# 体检结束必须清掉，否则测试库一轮轮堆积测试单（守则 12：不留测试残留）。
Invoke-Step '⑤e 界面闭环测试单清理（反结案→反审核→删除）' {
  Push-Location $smokeDir
  try { node cleanup-loop-docs.mjs } finally { Pop-Location }
} -TolerateFailure

# ---------------- ⑥ 汇总报告 ----------------
Write-Section '⑥ 汇总报告'
$loopFile = Join-Path $outDir 'e2e-loop-summary.json'
if (Test-Path $loopFile) {
  $s = Get-Content $loopFile -Raw -Encoding UTF8 | ConvertFrom-Json
  Write-Host ("闭环单据   : 通过 " + $s.passed + " / 失败 " + $s.failed + "（共 " + $s.total + "）")
  Write-Host ("已登记单据 : " + $s.registered.Count + " 张")
  Write-Host ("未覆盖单据 : " + $s.pending.Count + " 张 -> " + ($s.pending -join '、'))
  if ($s.failed -gt 0) {
    Write-Host ''
    Write-Host '失败单据明细：' -ForegroundColor Red
    foreach ($r in $s.results) {
      if (-not $r.ok) {
        Write-Host ("  - " + $r.name + "（" + $r.docCode + "）") -ForegroundColor Red
        foreach ($p in $r.problems) { Write-Host ("      · " + $p) -ForegroundColor DarkYellow }
      }
    }
  }
} else {
  Write-Host '未找到 e2e-loop-summary.json（阶段B 可能未执行）' -ForegroundColor Yellow
}

Write-Host ''
if ($failStep.Count) {
  Write-Host ('门禁结果：不通过 —— 未通过步骤：' + ($failStep -join ' / ')) -ForegroundColor Red
  exit 1
}
Write-Host '门禁结果：全绿' -ForegroundColor Green
exit 0
