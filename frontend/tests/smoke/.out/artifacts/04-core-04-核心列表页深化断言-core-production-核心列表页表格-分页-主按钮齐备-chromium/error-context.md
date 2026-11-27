# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 04-core.spec.mjs >> 04 核心列表页深化断言 >> @core @production 核心列表页表格/分页/主按钮齐备
- Location: tests\smoke\04-core.spec.mjs:56:5

# Error details

```
Error: [production] 核心列表页断言未通过：
  - /production/progress-tracking → 未渲染出数据表格(.el-table)

expect(received).toEqual(expected) // deep equality

- Expected  - 1
+ Received  + 3

- Array []
+ Array [
+   "/production/progress-tracking → 未渲染出数据表格(.el-table)",
+ ]
```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - complementary [ref=e5]:
      - generic [ref=e6]:
        - img "logo" [ref=e7]
        - generic [ref=e8]: 楷煜
      - menubar [ref=e9]:
        - menuitem "生产管理" [ref=e10]:
          - generic [ref=e11] [cursor=pointer]:
            - img [ref=e13]
            - generic [ref=e15]: 生产管理
            - img [ref=e17]
    - generic [ref=e19]:
      - generic [ref=e20]:
        - generic [ref=e21]:
          - img [ref=e23] [cursor=pointer]
          - navigation "面包屑" [ref=e25]:
            - generic [ref=e26]:
              - link "首页" [ref=e27]
              - text: /
            - link "生产工序质检报表" [ref=e29]
          - generic [ref=e32]:
            - img [ref=e35]
            - textbox "搜索菜单、单号、物料、客户" [ref=e37]
        - generic [ref=e39]:
          - button "简体中文" [ref=e41] [cursor=pointer]:
            - img [ref=e43]
            - generic [ref=e45]: 简体中文
          - img [ref=e48] [cursor=pointer]
          - button "冒 冒烟-生产管理" [ref=e53] [cursor=pointer]:
            - generic [ref=e54]: 冒
            - generic [ref=e55]: 冒烟-生产管理
            - img [ref=e57]
      - generic [ref=e59]:
        - generic [ref=e61]:
          - generic [ref=e63] [cursor=pointer]: 工作台
          - generic [ref=e64] [cursor=pointer]:
            - generic [ref=e66]: 生产工序质检报表
            - img [ref=e68]
        - generic [ref=e70]:
          - generic "刷新当前页" [ref=e71] [cursor=pointer]:
            - img [ref=e72]
          - generic "关闭其他" [ref=e74] [cursor=pointer]:
            - img [ref=e75]
          - generic "全部关闭" [ref=e78] [cursor=pointer]:
            - img [ref=e79]
      - main [ref=e81]:
        - generic [ref=e82]:
          - generic [ref=e83]:
            - generic [ref=e84]:
              - generic [ref=e85] [cursor=pointer]:
                - img [ref=e87]
                - generic [ref=e89]: 返回
              - generic [ref=e90]:
                - generic [ref=e91]:
                  - text: 生产工序质检报表
                  - generic [ref=e92]: QC REPORT
                - generic [ref=e93]: 按工序检验记录统计：检验批次、合格率与不合格处理情况，可按日期 / 质检类型 / 判定结果 / 工单 / 工序 / 检验员筛选
            - button "刷新" [ref=e95] [cursor=pointer]:
              - img [ref=e97]
              - generic [ref=e99]: 刷新
          - generic [ref=e100]:
            - generic [ref=e102]:
              - generic [ref=e103]: "0"
              - generic [ref=e104]: 检验批次
            - generic [ref=e106]:
              - generic [ref=e107]: "0"
              - generic [ref=e108]: 合格批次
            - generic [ref=e110]:
              - generic [ref=e111]: "0"
              - generic [ref=e112]: 不合格批次
            - generic [ref=e115]: 合格率
          - generic [ref=e116]:
            - generic [ref=e117]:
              - generic [ref=e118]:
                - img [ref=e120]
                - combobox "开始日期" [ref=e122]
                - generic [ref=e123]: ~
                - combobox "结束日期" [ref=e124]
              - generic [ref=e126] [cursor=pointer]:
                - generic:
                  - combobox [ref=e128]
                  - generic [ref=e129]: 质检类型
                - img [ref=e132]
              - generic [ref=e135] [cursor=pointer]:
                - generic:
                  - combobox [ref=e137]
                  - generic [ref=e138]: 判定结果
                - img [ref=e141]
              - textbox "工单号" [ref=e145]
              - textbox "工序" [ref=e149]
              - textbox "检验员" [ref=e153]
              - textbox "检验单号 / 工单号" [ref=e157]
              - button "查询" [ref=e159] [cursor=pointer]:
                - img [ref=e161]
                - generic [ref=e163]: 查询
              - button "重置" [ref=e164] [cursor=pointer]:
                - img [ref=e166]
                - generic [ref=e168]: 重置
            - generic [ref=e170]:
              - table [ref=e172]:
                - rowgroup [ref=e189]:
                  - row "# 检验单号 工单号 工序 质检类型 检验数量 合格数 不合格数 判定结果 不合格处理方式 处理转序 检验员 检验时间 备注 操作" [ref=e190]:
                    - columnheader "#" [ref=e191]:
                      - generic [ref=e192]: "#"
                    - columnheader "检验单号" [ref=e193]:
                      - generic [ref=e194]: 检验单号
                    - columnheader "工单号" [ref=e195]:
                      - generic [ref=e196]: 工单号
                    - columnheader "工序" [ref=e197]:
                      - generic [ref=e198]: 工序
                    - columnheader "质检类型" [ref=e199]:
                      - generic [ref=e200]: 质检类型
                    - columnheader "检验数量" [ref=e201]:
                      - generic [ref=e202]: 检验数量
                    - columnheader "合格数" [ref=e203]:
                      - generic [ref=e204]: 合格数
                    - columnheader "不合格数" [ref=e205]:
                      - generic [ref=e206]: 不合格数
                    - columnheader "判定结果" [ref=e207]:
                      - generic [ref=e208]: 判定结果
                    - columnheader "不合格处理方式" [ref=e209]:
                      - generic [ref=e210]: 不合格处理方式
                    - columnheader "处理转序" [ref=e211]:
                      - generic [ref=e212]: 处理转序
                    - columnheader "检验员" [ref=e213]:
                      - generic [ref=e214]: 检验员
                    - columnheader "检验时间" [ref=e215]:
                      - generic [ref=e216]: 检验时间
                    - columnheader "备注" [ref=e217]:
                      - generic [ref=e218]: 备注
                    - columnheader "操作" [ref=e219]:
                      - generic [ref=e220]: 操作
              - generic [ref=e224]:
                - table:
                  - rowgroup
                - generic [ref=e226]: 暂无数据
            - generic [ref=e229]:
              - generic [ref=e230]: 共 0 条
              - generic [ref=e233] [cursor=pointer]:
                - generic:
                  - combobox [ref=e235]
                  - generic [ref=e236]: 20条/页
                - img [ref=e239]
              - button "上一页" [disabled] [ref=e241]:
                - generic:
                  - img
              - list [ref=e242]:
                - listitem "第 1 页" [ref=e243]: "1"
              - button "下一页" [disabled] [ref=e244]:
                - generic:
                  - img
              - generic [ref=e245]:
                - generic [ref=e246]: 前往
                - spinbutton "页" [ref=e249]: "1"
                - generic [ref=e250]: 页
  - img [ref=e252] [cursor=pointer]
```

# Test source

```ts
  18  | 
  19  | const accountsFile = readJson('accounts.json')
  20  | const enabled = accountsFile && Array.isArray(accountsFile.accounts) && accountsFile.accounts.length > 0
  21  | const accByModule = new Map((accountsFile?.accounts || []).map(a => [a.moduleKey, a]))
  22  | 
  23  | const groups = filterModules(await loadMenuGroups())
  24  | 
  25  | /** 非"实体列表页"的路径特征（这些页面本来就没有数据表格，断言表格会是假失败） */
  26  | const NON_LIST_PATTERNS = [
  27  |   /\/help$/,            // 操作手册
  28  |   /\/report\//,         // 报表
  29  |   /dashboard/i,         // 各类看板（含 /databoard/*）
  30  |   /\/databoard\//,      // 数据看板（图形页，无表格）
  31  |   /schedule-board/,     // 排程看板
  32  |   /\/funnel$/,          // 销售漏斗（图形）
  33  |   /\/mrp$/,             // MRP 分析
  34  |   /\/ledger$/,          // 库存流水（流水台账，无分页主表）
  35  |   /\/current$/,         // 即时库存
  36  |   /\/batches$/,         // 批次库存
  37  |   /\/settings$/,        // 设置中心
  38  |   /\/cost-config$/,     // 成本核算配置
  39  |   /\/login$/            // 获客-企查查登录（配置页，无表格）
  40  | ]
  41  | 
  42  | const CORE_BY_MODULE = new Map()
  43  | for (const g of groups) {
  44  |   const paths = groupLeafItems(g)
  45  |     .map(i => i.path.split('?')[0])
  46  |     .filter(p => !NON_LIST_PATTERNS.some(re => re.test(p)))
  47  |   CORE_BY_MODULE.set(g.key, [...new Set(paths)])
  48  | }
  49  | 
  50  | const report = { startedAt: new Date().toISOString(), baseUrl: cfg.baseUrl, modules: [] }
  51  | 
  52  | test.describe('04 核心列表页深化断言', () => {
  53  |   test.skip(!enabled, '未找到 accounts.json，请先执行 npm run test:smoke:prepare')
  54  | 
  55  |   for (const [moduleKey, corePaths] of CORE_BY_MODULE.entries()) {
  56  |     test(`@core @${moduleKey} 核心列表页表格/分页/主按钮齐备`, async ({ page }) => {
  57  |       const acc = accByModule.get(moduleKey)
  58  |       test.skip(!acc, `账号清单里没有模块 ${moduleKey} 的测试账号`)
  59  |       // 过滤后没有实体列表页的模块（看板/系统管理）显式跳过并说明，不产生"空跑绿灯"
  60  |       test.skip(corePaths.length === 0, `模块 ${moduleKey} 没有实体列表页（看板/设置中心类），本用例不适用`)
  61  | 
  62  |       const mon = startMonitors(page)
  63  |       await loginByUi(page, acc.username, accountsFile.testUserPassword)
  64  |       await waitAppReady(page)
  65  | 
  66  |       const problems = []
  67  |       const details = []
  68  | 
  69  |       for (const p of corePaths) {
  70  |         mon.reset()
  71  |         const item = { module: moduleKey, path: p, ok: true, reasons: [], buttons: [] }
  72  |         try {
  73  |           await page.goto(p, { waitUntil: 'domcontentloaded' })
  74  |           await page.waitForTimeout(1500)
  75  | 
  76  |           if (new URL(page.url()).pathname.startsWith('/403')) {
  77  |             item.ok = false
  78  |             item.reasons.push('被踢到 /403')
  79  |           } else {
  80  |             // 1) 数据表格（等它渲染，避免抢跑误判）
  81  |             const hasTable = await page.locator('.el-table').first()
  82  |               .waitFor({ state: 'visible', timeout: 15000 }).then(() => true).catch(() => false)
  83  |             if (!hasTable) { item.ok = false; item.reasons.push('未渲染出数据表格(.el-table)') }
  84  | 
  85  |             // 2) 分页控件（部分页面记录少时可能不渲染，故记为提示而非失败）
  86  |             item.hasPagination = await page.locator('.el-pagination').count() > 0
  87  | 
  88  |             // 3) 顶部主操作按钮：至少要有一个按钮，且记录文案用于人工核对
  89  |             const btns = page.locator('button')
  90  |             const n = await btns.count()
  91  |             for (let i = 0; i < Math.min(n, 12); i++) {
  92  |               const t = (await btns.nth(i).innerText().catch(() => '')).trim().replace(/\s+/g, ' ')
  93  |               if (t) item.buttons.push(t)
  94  |             }
  95  |             if (n === 0) { item.ok = false; item.reasons.push('页面上找不到任何按钮（疑似 v-permission 全部移除）') }
  96  | 
  97  |             // 4) 控制台
  98  |             const errs = mon.problems('')
  99  |             if (errs.length) { item.ok = false; item.reasons.push(...errs) }
  100 |           }
  101 |         } catch (e) {
  102 |           item.ok = false
  103 |           item.reasons.push(`异常: ${e.message}`)
  104 |         }
  105 |         details.push(item)
  106 |         if (!item.ok) problems.push(`${p} → ${item.reasons.join('；')}`)
  107 |       }
  108 | 
  109 |       mon.stop()
  110 |       report.modules.push({
  111 |         module: moduleKey,
  112 |         pageCount: corePaths.length,
  113 |         failed: details.filter(d => !d.ok).length,
  114 |         pages: details
  115 |       })
  116 |       writeJson('core-result.json', report)
  117 | 
> 118 |       expect(problems, `[${moduleKey}] 核心列表页断言未通过：\n  - ${problems.join('\n  - ')}`).toEqual([])
      |                                                                                      ^ Error: [production] 核心列表页断言未通过：
  119 |     })
  120 |   }
  121 | })
  122 | 
```