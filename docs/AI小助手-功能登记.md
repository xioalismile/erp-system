# AI 小助手 功能/修复登记

> 小助手 = 右下角 🤖 浮球 + 右侧抽屉（`frontend/src/components/AiAssistant.vue`），
> 后端走 Agent 流式接口。本文档按模块登记已完成功能与踩坑，持续补充。

## 一、已完成

### 1. 界面优化：去模块快捷 / 固定常用指令 / 助手通栏 / 执行过程全折叠（2026-09-14）

- **需求**：① 模块快捷去掉，给回复腾出空间；② 调用工具属内部行为，不摆在答复界面，默认隐藏、要看自己打开 ——
  用户只看「结果」和「你的提问」。

- **改动**（全部集中在 `AiAssistant.vue`）：

  - 删除顶部「模块快捷」8 个模块 chips（连带 `modules` / `activeModule` / `.module-section` 等样式）

  - 「快捷指令」改为固定 8 条**常用指令**（`fixedQuickCmds`），不再随模块切换

  - 助手答复**通栏**（`.msg-text` 的 `max-width` 80% → 100%）；用户自己的提问仍保留 80% 气泡

  - **执行过程全折叠**：思考 / 调用参数 / 工具结果 / 执行错误 默认收起，点「💭 查看执行过程」展开；
    有错误时折叠条显示红色「N 个错误」标记

  - **始终可见**：查询结果表（`mainResult`）、成功/失败卡（`actionResult`）、失败明细、最终答复、
    待确认卡（`CONFIRM_NEEDED` = 「AI 的提问」）

- **不变量**：确认卡后的操作摘要保持可见（用户 2026-09-12 明确要求保留，不得删除）

- **方案与验收**：`d:\erp\.trae\documents\小助手界面优化_立项登记表.md`

- **验证**：`@vue/compiler-sfc` 解析+编译通过（SCRIPT\_OK 40636B / TEMPLATE\_OK 76300B）；
  新旧符号断言 8/8 通过；vite dev 转换 200

### 2. 语音提醒（2026-09-14，与通知模块联动）

- 小铃铛出现新的「待审批」未读 → 小助手用本机语音播报。

- 详见 `docs/通知模块-功能登记.md` 第 2 节。

### 3. AI 租户消耗系数（2026-09-14，设置中心「AI 用量管理」）

- **功能**：消耗计量按 (1+系数) 倍记账，默认系数 0.5（实际消耗 100 → 记 150，额度也按 150 扣）；
  **额度分配不乘系数**；系数属内部商务参数，**终端用户看不到**。

- **落点**：`AiTokenUsageService.recordUsage()` —— 全系统 token 落库唯一入口（Agent 结束处调用），一处生效全局；
  配置键 `ai.usage.tenant.ratio`（sys\_config，group=`ai`）；配置入口 = 设置中心 → AI 用量管理 → 租户额度 Tab。

- **配额自动跟随**：`checkQuota` 读的就是 `total_tokens`（计费值），所以"扣额度按 1.5 倍"无需另改判定逻辑。

- **原始值留痕**：`ai_token_usage` 新增 `raw_prompt_tokens` / `raw_completion_tokens` / `raw_total_tokens`
  三列存**未加价的实际值**，仅平台内部对账用、界面不展示。

- **影响文件**：`db/75_ai_usage_tenant_ratio.sql`（+ manifest）、`AiConfigService`、`AiTokenUsageService`、
  `AiTokenUsageController`、`api/ai/tokenUsage.js`、`AiTokenUsage.vue`

- **验收与方案**：`d:\erp\.trae\documents\AI租户消耗系数_立项登记表.md`

- **已知边界**：改系数只对**之后**的消耗生效、不回算历史；历史行的 raw 值由 75 号脚本回填为当时 total（视为未加价）。

### 4. 语音提醒改读单据类型 ＋ 语音唤醒 ＋ 带上下文的语音审批（2026-09-14）

- **播报改口径**：不再读单号，改读**单据类型**（「您有一张销售订单待您审批，请及时处理。」）；
  多张时「您有 N 张待审批单据，最新一张是采购订单，请及时处理。」；解析不出类型时降级为
  「您有待审批单据，请及时处理。」——**绝不回退去读单号**（用户明确要求）。

- **上下文注入**：播报时记录「最新一条待审批通知」为上下文
  （`billType`/`billNo`/`billId`，`sourceType` 统一转大写、`sourceId` 即单据ID），
  发消息时经 `UnifiedRequest.context.pendingApproval` 带到后端，`ReActAgentService.appendPendingApproval()`
  注入系统提示 → 用户说「打开这张单 / 帮我审批通过」AI 能定位到具体单据（不传上下文时整段不注入，行为同升级前）。

- **语音唤醒**：新增 `utils/voiceWakeup.js`，喊「小助手」→ 自动打开抽屉并把这一轮话自动发出（静默 1.5 秒）；
  国内 Chrome 识别走 Google 云会报 network → **自动关开关 + 提示一次**，降级为点 🎤 说话；
  刷新页面后开关回到关闭（浏览器要求用户手势才能开麦克风）。

- **审批零新增工具**：复用既有 `bill_action`（`approve` 覆盖 29 种单据，**自带用户确认卡片**），
  本次只是让 AI「知道指哪张单」，不放宽权限、无免确认路径。

- **影响文件**：`utils/voiceNotify.js`、`utils/voiceWakeup.js`（新增）、`components/AiAssistant.vue`、
  `ReActAgentService.java`（新增 4 参 `buildSystemPrompt` 重载 + `appendPendingApproval`）

- **验收与方案**：`d:\erp\.trae\documents\小助手语音唤醒与审批_立项登记表.md`

### 5. 语音指令：说完即发判定修复 + 二次确认开关（2026-09-14）

- **修复「说完了却没反应」**：原派发只取 `final` 文本，而浏览器的持续识别可能长时间不给 final →
  文本为空 → **不派发**（现象：用户说完了，小助手一直没动静/停在响应中）。
  改为 **final 优先、interim 兜底**（新增 `lastInterim`）。

- **新增开关**：小助手抽屉顶部「⚡ 语音直发 / 🗣️ 语音需确认」（浏览器本地偏好，**默认直发** = 与升级前一致）。
  「需确认」模式下语音识别内容**只填入输入框**，用户确认后才发送；写操作仍由确认卡片兜底。

- **影响文件**：`utils/voiceWakeup.js`、`components/AiAssistant.vue`

- **验收与方案**：`d:\erp\.trae\documents\通知去重与语音确认_立项登记表.md`

### 6. AI 写操作「人工确认」开关（2026-09-14）

- **新增开关**：设置中心 → AI 工具权限 → 「AI写操作需人工确认」（`ai.confirm.write.enabled`，**默认 true**，与升级前一致）

  - 开启：AI 执行任何写操作前弹确认卡片由用户拍板（现状）

  - 关闭：审批 / 提交 / 驳回 / 重置草稿 / 下推 等【可逆】动作**直接执行**（无需再回"确定"）；
    「删除 / 作废」等【不可逆】动作**仍强制确认**（护栏写死在代码里，不随开关放开）

- **改造点**：`AiConfirmService.confirmWriteEnabled` 由「启动读一次的 final 字段」改为**运行时读 sys\_config**
  （改完立即生效、**无需重启**；配置缺失/读取异常回退 yml 的 `erp.ai.confirm-write-enabled`，默认 true）；
  新增 `isIrreversibleCall(toolName, params)`；`ReActAgentService.interceptWriteTool` 守卫同步调整。

- **影响文件**：`AiConfirmService.java`、`ReActAgentService.java`、`db/77_ai_write_confirm_switch.sql`（+ manifest）、
  `views/system/AiToolPermission.vue`

- **安全**：不放宽任何权限（仍走既有 `bill_action` 权限校验与数据权限）；免确认只省"人点一下"这一步

- **验收与方案**：`d:\erp\.trae\documents\AI写操作免确认开关_立项登记表.md`

### 7. AI 代执行操作的「审计可追溯」（2026-09-14）

- **背景（审计盲点）**：AI 通过**反射直接调用 Service**，不经过 Controller → `BillOperateLogAspect` 拦不到 →
  **AI 代执行的写操作在「操作日志」里完全查不到**；单据审批记录里也显示为「用户本人」操作。

- **做法**：在 `BillActionTool` 写操作**成功后**额外补写一条操作日志 ——
  模块=`AI小助手`、姓名=`账号（AI助手代执行）`、摘要=`【AI助手代执行】对单据ID {id} 执行「{动作}」，发起人：{账号}`。
  → 在「系统 → 操作日志」搜「**AI助手代执行**」即可筛出全部 AI 代执行记录。

- **🔴 关键禁令（勿犯）**：**绝不能给** **`operator`** **加"（AI助手）"后缀** —— 它在
  `ApprovalRuntimeService.approve()` 里被用于**审批人身份校验**（`您不是当前节点的指定审批人`），
  加后缀会让 AI 审批**直接被拒**。operator 必须保持原值，只在日志的姓名/摘要上打标。

- **用户决策（勿扩大）**：只做审计 —— **不做**"下推强制确认 / 批量操作上限 / 一键撤销"（用户判断："系统没必要什么都兜底"）。

- **可复原因子（实查结论）**：审批有重置草稿、作废有反作废、结案有反结案；**下推（pushTo）没有任何反动作**（grep 零命中），
  下推出错只能人工删/作废下游单。

- **已知限制**：单据详情页的审批记录仍显示为登录用户（不识别 AI）；审计只能**定位 + 人工回退**，不能自动还原；
  历史操作无法追溯（当时未记录）。

- **影响文件**：`ai/tool/impl/BillActionTool.java`（**无 SQL / 无新接口 / 无前端改动 / 无新权限**）

- **验收与方案**：`d:\erp\.trae\documents\AI操作审计可追溯_立项登记表.md`

### 8. 修复：AI 单据动作权限码与数据库不一致（2026-09-14）

- **现象**：李工账号（`lyc123`）在设置中心已有销售等模块权限，小助手却回「无权限：需要以下任一单据的菜单权限：「销售订单」」。

- **根因（实查）**：`BillActionPermissionResolver.MENU_CODE` 里写的是 `sale:order:view` 这类
  "模块:资源:**view**"格式，而数据库 `sys_menu.permission_code` 真实值是 **`sale:orders`**
  （实测 105 条形如 `模块:xxx` 的码中**无一条**以 `:view` 结尾；
  `SELECT COUNT(*) FROM sys_menu WHERE permission_code='sale:order:view'` = **0**）→ **27 条映射全错**
  → 除管理员（`*` / `is_admin`）外，**所有正常账号**调用 AI 单据动作**一律被拒**。

- **权限码真实来源（务必对齐）**：`AuthInterceptor.loadUserPermissions()` 用
  `sys_role_permission.menu_id = sys_menu.route_path` 关联后取 `sys_menu.permission_code`，
  注入 request attribute `currentPermissions` → `AiDataScope.permissions`。
  （即"菜单类权限"在 `sys_role_permission.menu_id` 里存的是**路由路径**，如 `/sale/orders`）

- **修复**：27 条映射全部改为数据库真实码（**SQL 逐条校验 27/27 OK**，菜单名与 billType 语义一一对应）；
  代码内补「事故记录」注释 —— **禁止再凭猜测填权限码，必须先查库**。

- **记录但未修（隐患）**：`action` 模式（`ai.bill.permission.mode`，默认关闭）的按钮码拼接是两段码
  `模块:动作`，而真实按钮码是 `模块:资源:动作`（如 `sale:orders:add`）→ **启用前必须先修**。

- **影响文件**：`ai/bill/BillActionPermissionResolver.java`（**无 SQL / 无前端 / 无权限模型变更**）

- **验收与方案**：`d:\erp\.trae\documents\AI单据权限码修复_立项登记表.md`

### 9. 修复：AI 查询结果「客户名称/供应商名称」显示为空（2026-09-14）

- **现象**：小助手查销售订单，界面上客户显示正常，但小助手查询结果里「客户名称」是空的。

- **根因（实查）= 界面与 AI 的取数口径不一致**：

  - **界面**：列表与详情都调 `SaleOrderServiceImpl.fillDisplayFields()`，**按 customerId 动态补全** customerName ✓

  - **AI**：走通用 CRUD 查询（`AiCrudAgentService`），**直接反射实体字段** → 单据冗余列 `customer_name`
    为空时返回 `""` ✗

  - 而"冗余列为空"是常态：新建单据**不写入**该冗余列（全库 58 张销售订单中 **7 张「有客户ID但客户名为空」**，
    有值的是演示数据）。实查 order 258：`customer_id=15` 有值，`customer_code`/`customer_name` 均为空。

- **修复（首版）**：`AiCrudAgentService` 在结果组装后按**同一口径**补全（新增 `fillForeignDisplayNames()`）——
  仅当「有外键ID 且名称列为空」时查 `customer` / `supplier` 表回填；**IN 批量**避免 N+1；**只补空值不覆盖**；
  查库异常只告警、**绝不影响结果**。

> ⚠️ **首版修复实际未生效（2026-09-14 复查确认）**：`fillForeignDisplayNames` 从**结果 Map** 里取 `customerId`，
> 但该配置的 `displayFields` 里根本没有 `customerId`（主键是 `orderId`）→ `ids.isEmpty()` 直接 return，
> 等于没补。已于**第 10 节**按"**从实体反射取外键 ID**"重做并验证。

- **未做（有意）**：不回写冗余列、不回填历史数据 —— 系统既有设计就是"**读取时补全**"
  （列表/详情都这么做），回写会波及所有保存/下推/导入路径，属过度扩大。

- **影响文件**：`ai/service/AiCrudAgentService.java`（**无 SQL / 无前端 / 无权限变更**）

- **验收与方案**：`d:\erp\.trae\documents\AI查询客户名显示修复_立项登记表.md`

### 10. 修复：AI 查询显示口径全量拉齐（字段名写错 / 补全失效 / 返回裸 ID / 关键词搜索报错）（2026-09-14）

- **触发**：用户报「请帮我检查其他模块是否也有类似的显示问题」→ 复查发现**不是销售模块个例**，
  48 个 AI 查询配置中 **33 个**有问题，且其中 **18 个的关键词搜索会直接 SQL 报错**。

- **四类缺陷（全部实查）**：

  | 类 | 数量               | 表现             | 根因                                                                                                                                                                                   |
  | - | ---------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
  | A | 25 个配置 / 36 个字段名 | 某些列**静默显示为空**  | `displayFields`/`searchFields`/`nameField`/`codeField`/`primaryKeyField` 写了实体上**不存在**的字段名                                                                                            |
  | B | 销售 6 单 + 采购合同    | 客户/供应商名**恒空**  | `customerName`/`supplierName` 是 `@TableField(exist=false)` 非数据库字段，反射取值恒 null；而首版补全从结果 Map 取 ID（Map 里没有该 ID）→ 失效                                                                      |
  | C | 库存 3 单           | 返回**裸数字 ID**   | `displayFields` 配的是 `materialId`/`stockId`/`fromStockId`/`toStockId`/`employeeId`，而界面显示的是名称                                                                                          |
  | D | 18 个配置           | 带关键词查询**直接报错** | `searchFields` 被 `camelToUnderscore()` 转成**列名**拼 SQL `LIKE`，列不存在 → `column xxx does not exist`；另有 6 个是"字段名看着对、列其实不存在"（5 张销售表无 `customer_name`、`purchase_contract` 无 `supplier_name`） |

- **关键新证据（决定了排查方式）**：反编译 Hutool 5.8.27 证实 —— `ReflectUtil.getField(Class,String)`
  找不到字段时是 `areturn` 返回 **null（不抛异常）**，`getFieldValue(Object,Field)` 首行即 `if(field==null) return null`。
  所以**字段名写错不会报错、只会静默为空**，编译器也查不出（字段名是字符串）→ 必须另做强制校验。

- **修复（2 个文件，无 SQL / 无新接口 / 无新权限 / 不改实体）**：

  1. `ai/service/AiCrudAgentService.java`

     - 纠正 25 个配置的 `displayFields`/`searchFields`/`nameField`/`codeField` 与 4 个 `primaryKeyField`
       （`SETTLE`→`settleModeId`、`LOCATION`→`binId`、`WORKORDER`→`orderId`、`DICT`→`dictTypeId`）；

     - **补全器重做**：`fillForeignDisplayNames(result, entities)` 改为**从实体反射取外键 ID**（不再依赖结果 Map 里有没有 `customerId`）；

     - 补齐从 2 类扩到 **8 类外键**：客户/供应商/物料/仓库/调出仓/调入仓/仓库/员工；

     - 库存三单 displayFields 换成名称键；`SALE_INVOICE` 金额字段 `totalAmount`→`amount`（实体真实字段）、
       `PURCHASE_REQUEST` 金额 `totalAmount`→`totalQty`、`PURCHASE_INBOUND` 单号 `inboundNo`→`receiptNo`；

     - 消除 SQL 报错：为无客户名/供应商名列的 6 个配置改写 `searchFields`（4 张销售单只按单号；
       2 张合同改用 `customer_short_name`/`supplier_short_name`）；`INVENTORY_STOCK` 停用关键词（`material_id` 是数值列不能 LIKE）。
  2. `frontend/src/components/AiAssistant.vue` → `formatLabel()` 的 `labelMap` **按后端改名同步**
     （该表当初是照着错误字段名写的，不同步会导致表头显示英文键）；同时清理已失效的旧键。

- **写后强制校验（针对"字符串写错编译器不报"）**：

  - 临时反射校验器逐条断言：`configs parsed=48 / field names checked=416 / allowed-missing=4 / failures=0 → VALIDATOR PASS`；

  - `searchFields` 逐列对库核验（94 个 `(表,列)` 对，反查 `information_schema`）→ **0 缺失**；

  - `mvn -q -Dmaven.test.skip=true compile` 退出码 **0**；`javap` 核 class 产物含新签名与新常量。

- **踩坑（见第四节第 10 条）**：**"改完了"不等于"改对了"** —— 首版补全器上线 == 无效果，原因是取值来源与结果数据结构不匹配；
  且该类改动**编译器完全不设防**（字段名是字符串）。→ 凡改 AI 查询配置，必须同时跑
  「实体字段反射校验 + `searchFields` 列对库校验」两道，缺一道都可能"改完等于没改"。

- **影响文件**：`ai/service/AiCrudAgentService.java`、`frontend/src/components/AiAssistant.vue`

- **验收与方案**：`d:\erp\.trae\documents\AI查询显示口径全量修复_立项登记表.md`

### 11. 唤醒词改「小楷小楷」＋ 助手更名「小楷」＋ 语音指令结果播报（2026-09-17）

- **需求原文**：用户 2026-09-17「小助手语音唤醒词语改为：小楷小楷，用户发布指令，小楷执行完成之后语音播报结果给用户，成功和失败都要播报」

- **用户拍板的三条口径**：① **只播报语音指令**的结果（打字对话不发声）；② **简短摘要、约 40 字内**；③ **界面名称也改成「小楷」**。

- **唤醒词变更**：`WAKE_WORDS` 由 `['小助手','你好小助手','嘿小助手']` 改为 `['小楷小楷','小楷，小楷','小楷,小楷']`
  —— 后两个是**逗号容错**（浏览器识别可能在两次「小楷」之间插入逗号，只匹配连写会"喊了没反应"）；
  **不再响应旧词「小助手」**（第 4 节该句口径作废）。

- **更名对照（界面文案）**：浮球 `🤖 AI助手` → `🤖 小楷`；抽屉标题 `🤖 ERP全能智能助手` → `🤖 小楷 · ERP全能智能助手`；
  消息角色 `🤖 助手` → `🤖 小楷`；唤醒应答 `我在，请说你的指令。` → `小楷在，请说你的指令。`；
  唤醒提示统一改为「喊『小楷小楷』」。**设置中心的模块命名（AI助手工具权限 / AI 用量管理）不改**（那是系统模块名）。

- **结果播报实现**：新增 `voiceNotify.buildSpeakSummary(text, limit=40)`（剔除 Markdown 表格/代码块/链接/标记与 ⚠️❌ 前缀 → 只取首句，超长截断）；
  `AiAssistant.vue` 新增 `voiceRound`（本轮是否语音发起，仅 `onWakeupEvent` 置位）与 `voiceConfirmPending`（等确认卡拍板）；
  三个出口都覆盖：**流结束** **`FINAL_ANSWER`**（成功念答复首句 / 失败念「操作失败，…」）、**流内** **`ERROR`/请求失败**（按失败播）、
  **确认卡执行结果**（成功「已完成，…」/ 失败「操作失败，…」/ 部分完成如实念「操作部分完成」；点「取消」不播）；
  前缀占字数时压缩正文，**整句控制在 40 字左右**。

- **两条设计决定（勿回退）**：① **播报不受 🔊 语音提醒开关 / 🔇 本机静音影响** —— 这是用户主动用语音问的，
  必须听到答复（沿用既有 `speakText()` 强制发声）；② **待确认卡不是"结果"**，弹卡时不播报，等用户拍板后再播真正的执行结果。

- **降级与边界**：无中文语音包 / 不支持 `speechSynthesis` → 静默不发声、界面不受影响；
  客户包（`VITE_VOICE_FEATURES=false`）整段播报直接 return；**后端零改动**。

- **影响文件**：`frontend/src/utils/voiceWakeup.js`、`frontend/src/utils/voiceNotify.js`、`frontend/src/components/AiAssistant.vue`
  （**无 SQL / 无新接口 / 无新菜单 / 无新权限 / 无后端改动**）

- **验收与方案**：`d:\erp\.trae\documents\小助手唤醒词改小楷与结果语音播报_立项登记表.md`

### 12. 删掉浏览器语音唤醒 ＋ 新增「🚀 纯语音（不打开界面）」＋ 写操作改口头确认（2026-09-18）

- **需求原文**：用户 2026-09-18「我的小助手不是有一个功能是不打开操作界面直接给结果的吗？你对接上了吗？而且已经有本地语音了，
  那个浏览器的语音唤醒就去掉吧。代码全删掉」「问题2：语音直发功能优化：小助手答复结果之后如果是需要确认卡片的也别打开界面，
  用户回答确认之后直接执行」

- **三条改造（前两条是"删"，第三条是"接"）**：

  1. **浏览器语音唤醒整套删除**：`voiceWakeup.js` 由 543 行缩到 89 行，只留「唤醒词匹配规则」+「两个本机偏好」。
     删掉的东西（均已实测证明不可用，不是"懒得维护"）：Web Speech API（`SpeechRecognition` / `onresult` / `onerror` / `onend` /
     自动重启 / 切后台暂停 / 首次交互补启动）、设备端语音包全套（`isLocalPackSupported` / `checkLocalPack` / `installLocalPack` /
     `probeLocalPack` / `isLocalPackReady` / `restartWakeupIfOn`）、🎧 语音唤醒标签、⬇️ 下载中文语音包标签 + 480px 下载进度弹窗

     - 相关 CSS（`.lp-progress/.lp-note/.lp-warn`）、`AiAssistant.vue` 里浏览器 ASR 的 🎤 旁路
       （`getSpeechRecognitionCtor` / `browserAsrAllowed` / `markBrowserAsrUnavailable` / `startBrowserAsr` / `stopBrowserAsr` /
       `BROWSER_ASR_FLAG` 记忆）→ **🎤 现在只有一条链路：录音上传 + 服务器自建 ASR**。
       唤醒词匹配 `WAKE_RE_SOURCE`（`小楷助手` 主词 + `小楷小楷` 兼容 + 楷/凯/开 同音容错）**原样保留**，
       它现在是 `utils/serverWakeup.js`（本地语音采音）唯一的匹配来源。

  2. **新增「🚀 纯语音（不打开界面）」**（`voiceWakeup.js` 的 `isVoiceSilentEnabled/toggleVoiceSilent`，localStorage `erp.voiceWakeup.silent`）：
     开启后语音指令**不再打开抽屉**，直接在后台走同一条 `sendAgent` 链路（对话仍记进消息列表，之后打开能看到），执行完**只语音播报结果**。
     点这一个标签会连带做三件事：① 自动开启本地语音采音（`startServerWakeup`，需麦克风，失败则回滚偏好）；
     ② 与「🗣️ 语音需确认」互斥 → 自动切回「⚡ 语音直发」（"不打开界面"+"等你确认才发"是死状态）；
     ③ 刷新页面后靠**本页第一次点击/按键**自动恢复采音（浏览器硬规定：开麦克风必须先有用户手势，无法在加载时直接开）。

  3. **写操作确认改为口头确认（也不打开界面）**：`serverWakeup.js` 新增 `armVoiceCommand(timeoutMs)` ——
     手动把采集器切回「等指令」态，把用户下一句识别结果当作一条指令派发；`AiAssistant.vue` 新增 `askVoiceConfirm(botMsg)` /
     `onVoiceConfirmAnswer(text)`：AI 返回 `CONFIRM_NEEDED` 时**不弹卡片**，改成语音问一句「要执行 XXX，请说"确认"或"取消"」，
     用户口头「确认」→ 直接 `confirmWriteTool`（写操作照样带 token，走原有后端确认接口）；「取消」→ `cancelWriteTool` 并播报"已取消"；
     没听清 → 重问一次并重新 `armVoiceCommand`；**15 秒没回答 → 自动显示确认卡交给用户手点**（不丢单）。

- **判定词表（顺序很重要）**：先判"取消" `/取消|不用|不要|别|算了|否|不是|no/`，再判"确认" `/确认|确定|执行|可以|好的|好|是的|是|对|行|ok|yes/`
  —— 否则「不要执行」里的"执行"会被当成同意（实测过这个反例）。

- **降级与边界**：① 采集器没在跑（麦克风没开）→ 语音确认**自动退回"显示确认卡"**，绝不静默卡死；
  ② 不支持 `speechSynthesis` → 静默不发声，界面行为不受影响；③ 客户包（`VITE_VOICE_FEATURES=false`）整块语音功能不渲染；
  ④ 采集自测面板与配套统计（`stats`/日志）已**整套删除**（2026-09-18 用户要求）：界面不显示、后台也不再记录；
  采音/唤醒本身不受影响，仍由 `utils/serverWakeup.js` 负责（只剩"采音 → 上传识别 → 派发唤醒事件"）。

- **影响文件**：`frontend/src/utils/voiceWakeup.js`（大幅瘦身）、`frontend/src/utils/serverWakeup.js`（+`armVoiceCommand`）、
  `frontend/src/components/AiAssistant.vue`（删浏览器语音 UI/逻辑 + 纯语音 + 口头确认）
  （**无 SQL / 无新接口 / 无新菜单 / 无新权限 / 无后端改动**）

- **实测（Playwright + 系统 Edge，真实后端，20/20 PASS）**：浏览器语音入口 0 残留（🎧/语音包/本机识别全部消失）；
  未开纯语音时语音指令仍会打开界面（原行为保留）+ 结果播报；纯语音时**不打开界面**且直接播报；
  需确认时**不打开界面**、语音问询、口头"确认"后 `POST /api/ai/chat/confirm-tool` 真实发出（confirmCalls=1）并播报「已完成，审批通过」；
  刷新后纯语音标签=开、语音采音自动恢复=开；`ReferenceError/TypeError` 0；
  真实自建识别链路（不打桩）`POST /api/ai/speech/recognize` → 200、识别文本「开饭时间早上9点至下午5点。」

- **验收与方案**：`d:\erp\.trae\documents\服务器自建中文语音识别_立项登记表.md`（第 23 节）

### 13. 纯语音等待期报进度 ＋ 多轮语音沟通（修"第二句收不到"死锁）（2026-09-18）

- **需求原文**：用户 2026-09-18「我唤醒助手之后答复我了，我发出指令，等待的时候稍微有点久，因为这里我没打开卡片是不知道在发送还是查询中，
  如果是在思考中，那么需要答复用户在思考，如果是在查询中需要答复查询中。BUG：第一轮通过唤醒助手发送指令，助手答复我之后第二轮的指令是接受不到了，
  缺少了多次语音沟通的功能」

- **BUG 根因（真实音频驱动复现，不是猜测）**：用 `--use-file-for-fake-audio-capture` 喂"静音+音调"循环音频驱动真实 VAD，
  第一次跑出 **`ASR 调用 23 次 / agent 请求 0 次`** —— 语音被识别了，但指令**永远发不出去**。定位到 `serverWakeup.js` 的派发条件：

  ```js
  if (pendingCmdText && Date.now() >= cmdFlushAt && !speaking && !uploading) { 派发 }
  ```

  `!speaking` 是"当前没在收集人声段"。若用户说完一句**停顿不足 1.5 秒就接着说**（或环境持续有声），`speaking` 会长期为 true
  → 窗口到点也派发不了 → 文本不断被合并进 `pendingCmdText` → 而"等指令超时取消"那条只对 `awaitCmd && !pendingCmdText` 生效（有 pending 就不取消）
  → **永久卡死**。这就是"喊了一次之后，后面说的话助手完全收不到"的机制。

- **修复（`CMD_FORCE_SEND_MS = 9000`** **硬上限）**：新增 `cmdFirstAt`（**第一次**识别出文字的时刻，合并时不刷新）+ 硬上限判断：

  ```js
  const hardDue = !!pendingCmdText && cmdFirstAt > 0 && Date.now() - cmdFirstAt >= CMD_FORCE_SEND_MS
  if (pendingCmdText && !uploading && ((Date.now() >= cmdFlushAt && !speaking) || hardDue)) { 派发 }
  ```

  正常情况（说完有 >1.5 秒停顿）仍走"窗口到点且不在说话"；极端情况（一直说不停顿）最迟 9 秒**无条件发出**。
  7 处状态重置点（start/stop/重新唤醒/armVoiceCommand/派发后）同步重置 `cmdFirstAt`。

- **多轮语音沟通（新能力，用户要求的"多次语音沟通"）**：

  - `speakRoundResult()` 末尾调用 `armNextVoiceRound()` —— **等播报真正结束后**（`voiceNotify.isSpeaking()` 轮询，最多 30 秒兜底）
    再等 300ms，调 `armVoiceCommand(10000)` 把采集器切回"等指令"态 → 用户**接着说下一句即可**，不必再喊唤醒词；
    期间浮球显示「🎙️ 聆听中…」；10 秒内不说话 → 采集器自己派发 cancel → 浮球复原（不会一直占着聆听态）。

  - 语义上下文天然连续：前端一直用同一个 `sessionId`，后端 `ReActAgentService` 会把用户原话写入 `conversationHistory`（2026-09-05 已修），所以多轮指代能接上。

- **等待期进度播报（只在「🚀 纯语音」下发声；界面打开时用户能看到 loading，不念）**：

  - 1.2 秒后还没结果 → 「好的，我正在思考。」

  - SSE 收到 `ACTION`（真在调工具）→ 「正在查询数据。」

  - 超过 8 秒还没结果 → 兜底补一句「还在查询中，请稍等。」

  - 两次进度至少隔 1.5 秒（不足则延后补念，结果一到就被取消），避免"连珠炮 + 被下一句打断"的碎句感。

  - `voiceNotify` 新增 `isSpeaking()`：`speechSynthesis.speaking && now < 按字数估算的播报截止时间` ——
    **不能无条件信任** **`speechSynthesis.speaking`**（Chromium 上 `cancel()` 紧跟 `speak()` 偶发不复位 → 采集被永久丢弃），
    估算上限 = 字数×300ms+3000ms（最多 20 秒）。`serverWakeup` 改为**注入式**使用它（`startServerWakeup({ isSpeaking })`）。

- **降级与边界**：麦克风没开时 `armVoiceCommand()` 返回 false → **语音说清"麦克风没有开启，请在界面上点确认或取消"** 再显示确认卡（不再静默弹界面）；
  客户包（`VITE_VOICE_FEATURES=false`）整块不渲染；打字/界面模式不播进度、不做续接（保持原行为）。

- **影响文件**：`frontend/src/utils/serverWakeup.js`（硬上限 `CMD_FORCE_SEND_MS` + `cmdFirstAt` + 注入 `isSpeaking`）、
  `frontend/src/utils/voiceNotify.js`（新增 `isSpeaking()` + `speakUntil` 估算）、`frontend/src/components/AiAssistant.vue`（进度播报 + 多轮续接 + 确认降级提示）
  （**无 SQL / 无新接口 / 无新菜单 / 无新权限 / 无后端改动**）

- **实测（Playwright + 系统 Edge，21/21 PASS，临时脚本已删）**

  - **多轮/进度（9 项，假麦克风喂真实音频驱动 VAD，刻意让停顿 1300ms < 续话窗口 1500ms 卡住死锁路径）**：
    `ASR 调用 9 次 / agent 请求 2 次`；播报序列 = `["小楷在，请说你的指令。","好的，我正在思考。","还在查询中，请稍等。","共有 3 条物料记录，第一条是螺钉","共有 5 条客户记录"]`；
    **第二轮指令被接收且未再喊唤醒词**（`agentReqs[1]` 为纯指令文本）、全程未打开界面、无 ReferenceError/TypeError。

  - **回归（12 项）**：未开纯语音仍打开界面+播报；纯语音不打开界面+播报；确认卡→语音问询→口头"确认"→`POST /api/ai/chat/confirm-tool` 真实发出（1 次）+ 播报「已完成」；真实自建 ASR 200 且出中文；无 ReferenceError/TypeError。

- **验收与方案**：`d:\erp\.trae\documents\服务器自建中文语音识别_立项登记表.md`（第 24 节）

### 14. 界面导航：说「打开设置中心」自动跳转（2026-09-18）

- **需求原文**：用户 2026-09-18「小助手现在是只有按钮功能是吗，如果我说打开设置中心小助手可以实现吗」→ 确认口径后新增"带路"能力。

- **用户拍板的 5 条口径**：① 覆盖**全系统菜单页面**（含设置中心 24 子项）不只做设置中心；② 命中方式＝**双层**（本地关键词先匹配，未命中交 AI）；③ 设置中心里**保持小助手被卸载的现状**（不改设置中心布局）；④ **支持打开某张单据**；⑤ **不加后台开关**（只读、权限＝菜单权限）。

- **能力边界**：菜单页面（设置中心/销售订单/即时库存/审批设置…）、设置中心子项深链（自动补 `?group=`）、某张单据（按单号+实体名跳详情，复用 `entityRouteMap`）、找不到时给候选。

- **实现（后端 3 文件 + 前端 1 文件；无 SQL / 无新接口 / 无新菜单 / 无新权限 / 无 sys\_config 种子**）：

  | 层     | 位置                                   | 内容                                                                                                                                            |
  | ----- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
  | 后端工具  | `ai/tool/impl/OpenPageTool.java`（新增） | 只读工具 `open_page`：校验参数（**拒绝网址/路径**）、回 `navigate:{page,recordId?,entityType?}`；**不维护"页面名→路由"映射**                                                |
  | 后端注册  | `ai/tool/ToolRegistry.java`          | `READ_ONLY_TOOLS` + 暴露分支（始终暴露，不消耗写开关）+ `checkPermission` 只读放行 + `isWriteTool` 自动判只读                                                           |
  | 后端提示词 | `ai/agent/ReActAgentService.java`    | 新增「能力十一：界面导航」+ `ALWAYS_TOOLS` 加入 `open_page`（**必须常驻**，见踩坑 13）                                                                                 |
  | 前端    | `components/AiAssistant.vue`         | 页面清单从 `menuConfig.js` 派生（含 `?item=` 子项 + 合并 `menu_name_*` 自定义名）；`tryLocalOpenPage` 本地快匹配；SSE 里捕获 `toolResult.navigate`，**流结束后**跳转；失败给候选/无权限话术 |

- **关键设计（勿回退）**：

  1. **页面清单单一来源 =** **`menuConfig.js`**：后端不存页面名清单、也不回 route（登记表编码前据此修订契约）—— 否则菜单改名/新增页面必出两套口径。
  2. **权限同源**：用 `menuRoutePaths` 与左侧菜单渲染同一份数据判定（设置中心子项按 `/system/settings?item=<key>` 精确匹配），**路由守卫 /403 是第二道防线**；不做后端复制判定（后端上下文只有权限码，复制必漂移，见第 8 节教训）。
  3. **等流结束再跳**：`pendingNavigate` 先记下，`FINAL_ANSWER` 之后执行，避免把 SSE 流打断在半路（跳设置中心时本组件会被 `MainLayout` 卸载）。
  4. **歧义不乱跳**：同长度多义（"订单"→销售/采购订单、"操作手册"→多模块）返回 null 交 AI 追问。

- **影响文件**：`ai/tool/impl/OpenPageTool.java`（新增）、`ai/tool/ToolRegistry.java`、`ai/agent/ReActAgentService.java`、`frontend/src/components/AiAssistant.vue`

- **验收与方案**：`d:\erp\.trae\documents\小助手界面导航能力_立项登记表.md`（含 8 层闭环核对表 + 三层实测证据）

- **实测（真实后端 + 真实 DeepSeek；临时脚本已删）**：后端 4/4 PASS（「打开设置中心/打开销售订单/打开即时库存/帮我打开设置中心」→ SSE 均含 `open_page` 且 `navigate.page` 逐字一致）；前端渲染+点击 5/5 PASS（本地快匹配两条 + AI 步骤打桩 + 不存在页面不跳转给候选 + 受限账号不跳转给无权限话术）；`pageerror` 0。

### 14.1 用户实测反馈的两处修复（2026-09-18 追加）

- **反馈①（致命）「让小楷打开一张订单，打开是空白的」**：URL 形如 `/sale/orders/SO202609140005`，详情页内容全空。

  - **根因（实查）**：`SaleOrderEdit.loadDetail()` 用 `route.params.id` **直接当主键**调 `getSaleOrder(id)`；
    而 AI 把**单号**当 recordId 传了下来 → 后端按主键查不到 → 表单空白
    （实测对照：`/sale/orders/258` → 42 个输入框/13 个有值；`/sale/orders/SO-20260914-0005` → 32 个输入框/4 个有值）。

  - **三层修复**：① 后端 `OpenPageTool` **拒绝非数字 recordId**，回「recordId 必须是主键数字 ID…先用 query\_ 查到主键」；
    ② 工具描述 + 系统提示「能力十一」明确 **recordId 必须是主键数字 ID、严禁传单号；只有单号/日期/客户等条件时先查询拿主键**；
    ③ 前端 `navigateToPage` **只在"数字主键 + pathId 实体"时才拼详情路径**，拿到单号则只打开列表页 + 如实提示
    「ℹ️ 已为你打开【销售订单】：我拿到的是单号 xxx，请在列表里按这个单号查一下」。

  - **复测（无打桩、真实 DeepSeek）**：「打开单号 SO-20260914-0005 那张订单」→ AI 先按单号查到主键 → 跳 `/sale/orders/258` → 13 个输入框有值、单号正确显示。

- **反馈②「打开基础资料」→ AI 说已打开、前端回"没找到"**：

  - **根因**：`基础资料` 是**菜单分组**（没有独立路由），不是页面；AI 把它当 page 调了工具。

  - **修复**：前端新增 `matchGroup()`/`rankPageCandidates()` —— 命中菜单分组时直接回
    「【基础资料】是一个菜单分组，不是某一个页面，你要打开里面哪一项？计量单位 / 币种 / 结算方式 / 银行账户」，
    且**本地就答、不消耗 AI**；未命中页面时候选改为按**字面重合度**排序（原先前 3 个是无关页面）。

  - **提示词**同步写明「菜单分组名（基础资料/销售管理/系统管理/采购管理）不是页面，用户这么说先问清哪一项」。

- **顺带修掉③「打开销售订单，2026年7月26号的订单。」条件被吞**：

  - **根因**：本地快匹配只认「打开X」，把日期条件吞掉后直接跳列表（AI 根本没参与）。

  - **修复**：新增 `extraInfo()` —— 「打开X」之外还有残余信息（日期/数字/实词）就**不本地拦截、交 AI**。

  - **复测**：该句 → 发出 agent 请求（未被本地拦）→ AI 按日期查到 2026-07-26 那张单 → 跳 `/sale/orders/238` 并列出单号/客户/金额/状态。

- **语音相关（用户反馈「刷新后要点一下小助手卡片才激活采集」）**：

  - **实测结论（Playwright + 假麦克风，真实点击）**：刷新后 ASR 调用 **0 次**；**中性点击（非小助手区域）后 ASR 2 次** → 说明"点一下页面任意位置"本来就会自动恢复，**不是"必须点小助手卡片"**；
    根因是浏览器硬规定（麦克风必须由用户手势触发）+ 界面没告诉用户这件事（标签显示"关"，看着像坏了）。

  - **修复**：① 采音标签新增「🧪 语音采音：**待点击恢复**」状态 + tooltip 说明；② 绑定自动恢复时给**一条**提示
    「语音采集已就绪：点一下页面任意位置即可恢复（浏览器规定麦克风需由你的点击触发）」；③ 实测提示出现、点击后标签回到「开」、ASR 0→2。

### 14.2 纯语音写操作「口头确认」修复（2026-09-18 用户实测反馈）

- **用户反馈原文**：「语音激活小楷，让它提交一张销售订单并给它单号，小楷自己查询了一遍播报给我。后续我给指令并没提交这张订单，
  而且我这个指令很明确…我语音控制呢就是想方便快捷」（对话里连说三次"确认/确认执行"，小楷每次都回"请在上方确认卡片点击"，最后还说"提交操作已发起"）。

- **实测复现（Playwright + 假麦克风 + 真实后端/DeepSeek，证据保留在下方）**：

  - 确认卡**有**生成、话术**有**念（`要执行对【销售订单】SO-…执行「提交」，请说"确认"或"取消"`），
    但紧接着就念出「**没有听到你的确认，请在界面上点确认或取消。**」；

  - 用户后续说"确认执行"→ **没有调用** **`/api/ai/chat/confirm-tool`（0 次）**，反而**多打了一次 agent 请求**（被当成新指令，AI 又生成一张新卡）→ 死循环；

  - 与用户描述完全一致。

- **根因（三条，全部实查）**：

  1. **等待窗口被播报吃光**：`askVoiceConfirm` 念完问题**立刻** `armVoiceCommand()`（默认窗口 15 秒），
     而采集器**播报期丢弃音频**（`startServerWakeup` 传了 `isSpeaking`）→ 播报本身要念 5\~12 秒 →
     用户听完再开口时窗口只剩几秒甚至已过期 → 必然超时。
  2. **超时即放弃**：超时只会"显示卡片 + 报一句没听到"，**没有重试** → 一次没接上就永久掉回"点卡片"。
  3. **词表太窄 + 无待确认态没有兜底**：`VOICE_YES_RE` 不含"提交"，用户说「提交他」被判"没听清"；
     而"确认执行"在没有待确认态时被当普通指令发给 AI → AI **重新发起一遍**并**幻觉"已发起"**。

- **修复（前端 5 处 + 后端提示词 1 处）**：

  1. `askVoiceConfirm` 改为 `askVoiceConfirmOnce()` + **`armConfirmWindow()`：等播报真正结束再开窗口**（最多等 20 秒，不支持语音合成则最多 3 秒）；
  2. 等待窗口 15 秒 → **30 秒**（`VOICE_CONFIRM_WAIT_MS`）；
  3. 超时**自动再问一次，最多 2 次**（`VOICE_CONFIRM_MAX_RETRY`），仍没接上才显示确认卡并明确播报；
  4. 词表放宽：`VOICE_YES_RE` 加「提交/通过/就这么办/没问题」，`VOICE_NO_RE` 加「不对/不行/不可以」；
  5. 新增 `isBareConfirm()` + `tryLocalConfirmOnly()`：**没有在等确认时**，用户只说「确认/确认执行/提交他/提交这张单」这类纯确认词 →
     **本地如实回**「我这边没有正在等待确认的操作…」，**不发 AI**（既不再重复发起，也不给模型编"已发起"的机会）；
  6. 系统提示「写操作确认机制」新增**严禁假成功**：未真正拿到执行结果前不得说"已提交/已发起/正在处理"，也不得因用户重复说"提交"而反复重新发起。

- **实测（修复后，Playwright + 假麦克风 + 真实 DeepSeek；临时脚本已删）**：

  | 用例                                              | 期望       | 实测                                                                                             |
  | ----------------------------------------------- | -------- | ---------------------------------------------------------------------------------------------- |
  | ① 语音说「提交 SO-20260914-0004」→ 小楷语音问询 → 说「**提交他**」 | 直接执行     | `CONFIRM_TOOL_CALLS=1`、agent 请求仍为 1（没被当新指令）、界面 `✅ 操作已执行`                                       |
  | ② 没有待确认操作时说「确认执行」                               | 不发 AI    | `AGENT_CALLS=0`、界面回「ℹ️ 我这边没有正在等待确认的操作…」                                                        |
  | ③ 真实确认（不打桩）                                     | 调用真实确认接口 | `CONFIRM_TOOL_CALLS=1`（链路通），**但该订单业务上提交不了**：界面如实显示「❌ 操作失败: 执行『提交』失败: 【设置中心-单价校验】销售订单第1行单价不能为0」 |

- **⚠️ 用户那张单"没提交"的真正原因**：`SO-20260914-0004` 第 1 行**单价为 0**，被设置中心【单价校验】规则拦下（`POST /api/sale/orders/257/submit` 直接返回该错误）。
  **与语音链路无关**，且界面已如实显示失败原因（不是静默、也不是假成功）。需要把该行单价填上（或临时关掉该校验）才能提交。

- **影响文件**：`frontend/src/components/AiAssistant.vue`（口头确认窗口/重试/词表/纯确认词兜底）、`ai/agent/ReActAgentService.java`（严禁假成功）。
  **无 SQL / 无新接口 / 无新权限**。

### 15. 单据明细行编辑：改单价/数量（首批销售订单，2026-09-18）

- **需求原文**：用户「把这两张订单的单价改成1」→ 小助手答"我拿不到明细行、也没有改明细的工具" → 用户：「**我需要小助手可以改的系统里面的任何东西**」
  → 拍板「**先从销售订单开始**（支持明细行的操作）」。

- **实查结论（缺口）**：`AiCrudAgentService` 的 48 个配置**全是主表**，40+ 个 `*Item` 子表（如 `SaleOrderItem`）一个都没接入 → AI 能改表头、不能改明细。

- **实现（无 SQL 变更 / 无新接口 / 无新权限码）**：
  | 层 | 位置 | 内容 |
  | -- | ---- | ---- |
  | 接入表 | `ai/bill/BillItemHandler.java`（新增，接口）+ `ai/bill/BillItemRegistry.java`（新增，Spring 收集 + 启动打印已接入清单） | 每种单据一个适配器：读主表/读明细/白名单字段/patch/整单保存 |
  | 首批适配器 | `ai/bill/impl/SaleOrderItemHandler.java`（新增） | 销售订单：保存走 `SaleOrderService.saveOrderWithItems`；白名单 `unitPrice/quantity/taxRate/discountRate` |
  | 工具 | `ai/tool/impl/BillItemListTool.java`（只读）+ `BillItemUpdateTool.java`（写） | `list`（返回行数组给表格）+ `updateItem`（itemId 或 lineNo 定位，**强制确认卡**） |
  | 注册/开关 | `ai/tool/ToolRegistry.java` | `bill_item_list` 归只读名单；`bill_item_update` 用**独立子开关** `ai.tool.bill_item.enabled`（默认 false，代码兜底）；权限复用 `BillActionPermissionResolver` |
  | 提示词 | `ai/agent/ReActAgentService.java` | 能力十二：先 `bill_item_list` 再改、不许猜行号、billId 必须主键数字 ID、其它单据如实说"暂不支持"；`buildWriteSummary` 新增卡片摘要（**改前→改后**） |
  | 前端 | `views/system/AiToolPermission.vue` | 新增「单据明细行编辑」开关行 |

- **关键设计（勿回退）**：**绝不直连子表**（直改会让主表金额与明细对不上）；一律走原 Service 的整单保存 → 金额/税额/合计重算、设置中心各项校验、已生效/已完结不可改，**与界面完全一致**。

- **端到端实测（真实 DeepSeek + 真实点卡）**：卡片「修改【销售订单】SO-20260914-0004 第 1 行…：单价 0.00 → 1」→ 确认 → 「✅ 已修改…该行金额 0.00 → 1.00，单据合计 0.00 → 1.00」；接口核对 单价=1/金额=1/**合计=1/税额=0.13/价税合计=1.13**；**顺带解决**该单"提交被单价校验拦住"的问题（提交返回 200、状态变已生效）。

- **验收与方案**：`d:\erp\.trae\documents\小助手单据明细行编辑能力_立项登记表.md`（含 8 层闭环 + 交付记录 + 后三项对账）

### 16. 销售订单「按条件搜索」＋「带筛选打开列表」（=点看板卡片穿透）（2026-09-18）

- **需求原文**：用户「都做，也就是**销售订单上的任何地方小助手都可以操作**」（含"点按钮/按条件搜索/点卡片"三项）。

- **本轮交付的两项（均已端到端实测）**：
  | 项 | 做法 | 实测 |
  | -- | -- | -- |
  | **按条件搜索** | `EntityCrudConfig` 新增 `filterFields` 白名单（销售订单：status/orderNo/customerId/orderDate/deliveryDate/totalAmount/closeStatus）；查询工具新增 `filters` 参数（`[{field,op,value}]`，op=eq/ne/gt/ge/lt/le/like/**between**）；`AiCrudAgentService.query` 校验"字段在白名单 + 实体上真实存在 + op 在白名单"后拼条件，**非法条件直接报错不静默忽略** | 「查状态是草稿的销售订单」→ 1 张（SO-20260914-0005）；「查 2026年7月1号到31号的销售订单」→ 4 张（全部 7 月单） |
  | **带筛选打开列表**（等价点看板卡片穿透） | `open_page` 新增 `filters` 参数 → 前端按**列表页支持的键**白名单过滤后拼进地址栏（status/startDate/endDate/customerId/customerLevel/materialId/productLineId/keyword，与各列表页 `applyDrillQuery` 同一套），列表页自动应用筛选 | 「打开状态是草稿的销售订单列表」→ URL `/sale/orders?status=草稿`、列表正好 1 行、提示「ℹ️ 已为你打开【销售订单】：状态=草稿」 |

- **关键设计（勿回退）**：① 条件字段**必须走白名单 + 实体反射校验**（杜绝乱传列名/注入）；② 前端只放行列表页认识的键，AI 传的野键丢弃；③ 提示词强制"按条件查必须传 filters，**不许拉全量自己筛**"（返回有上限会漏数据 —— 首测 AI 正是拉全量自己筛，补提示后复测已走服务端条件）。

- **影响文件**：`ai/dto/EntityCrudConfig.java`、`ai/service/AiCrudAgentService.java`、`ai/tool/impl/GenericQueryTool.java`、`ai/tool/ToolRegistry.java`、`ai/tool/impl/OpenPageTool.java`、`ai/agent/ReActAgentService.java`、`frontend/src/components/AiAssistant.vue`。**无 SQL / 无新接口 / 无新权限码**。

- **仍未做（下一批，已登记在立项登记表 十九）**：列表页「导出 / 打印」、编辑页「选单」、列表页「打开新建销售订单页」；其中"打印"受浏览器限制（打印对话框必须用户自己点），计划做成"小助手打开打印预览/生成打印文件"。

### 16. 工具调用治理 ＋ 答复重排 ＋ 打字/说话「确认」真正生效（2026-09-18）

- **需求原文**：用户 2026-09-18「小助手这么现在乱调用工具呢？而且一点 agent 的思考都没有的感觉，还有你的那个结果工具和执行工具，
  还有后续小助手的答复还是乱的很，需要重新布局一下才行」（附实测对话：新增物料被追问编码 → 编了个已存在的 G10002 → 失败 →
  又弹一张新卡 → 打字「执行操作」AI 说"我这一轮并没有真正执行任何工具" → 打字「确认」AI 回"我这边没有正在等待确认的操作"）。

- **用户拍板的三条口径（2026-09-18 三问三答）**：① 思考与工具调用**全部默认折叠**（"界面干净就行，但是也不能没有"）；
  ② **答复放在最上**（先结论、后数据）；③ 工具调用过程默认折叠、**结果放出来**。

- **修复 1（致命 BUG）打字说「确认」被本地吞掉**：
  - 根因：[`tryLocalConfirmOnly()`](file:///d:/erp/prodsrc/智能ERP-项目/frontend/src/components/AiAssistant.vue) 只判"是不是纯确认词"，
    **完全不检查界面上有没有待确认卡** → 卡片明明在眼前也照样回「我这边没有正在等待确认的操作」并把输入吞掉。
    且「执行操作」因残留"操作"二字没被 `BARE_CONFIRM_B_RE` 剥离 → 判不出纯确认词 → 被漏给 AI（AI 回"我并没有真正执行任何工具"）。
  - 修复：新增 `findPendingConfirmStep()`（找**最后一张未处理**的卡）；**有待确认卡时，打字「确认/取消」= 点卡片按钮**
    （仍走 `POST /api/ai/chat/confirm-tool`，权限二次校验不变）；`BARE_CONFIRM_B_RE` 补「操作 / 这张单子 / 这个单」。

- **修复 2 多张确认卡互相作废**：同一写操作被反复发起时并列多张可点的卡（G10002 / G10478 都亮着"确认执行"），
  用户不知道点哪张、点错必失败 → 新增 `supersedeOlderConfirmSteps()`：新卡到达时把旧卡标记 `superseded`，
  显示「该操作已失效（已被下方新的操作取代）」并隐藏按钮。**只在前端内存态做**（会话消息本就是内存态），未加后端撤销逻辑。

- **修复 3 确认卡摘要改人话**：通用 CRUD 工具原本回 `执行写操作 create_basedata_material: {"data":{...}}`（裸工具名 + 原始 JSON）
  → [`buildGenericCrudSummary()`](file:///d:/erp/prodsrc/智能ERP-项目/backend/src/main/java/com/erp/ai/agent/ReActAgentService.java)：
  「新增物料：小助手、成品」「修改物料 #12：materialName=…」「⚠ 删除后不可恢复 / 删除客户 #5」。
  新增 `ToolRegistry.entityLabelOfTool(toolName)`（单一来源 = 既有 `ENTITY_NAME_MAP`，禁止另起一套映射）。

- **修复 4 执行过程的"思考"业务化**：原本是写死的模板句「我需要调用 query_xxx 工具来获取相关数据。」
  → `describeToolAction()`：**模型真返回了推理文字就用原文**（实测真实 DeepSeek 给出「我来帮你新增这条物料。先确认一下物料档案里是否已有同名物料，避免重复。」），
  没有则用业务化步骤说明（「正在查询：物料」「正在创建：物料」）。
  ⚠️ **不伪造模型推理**：适配器只取 `content`、不解析 `reasoning_content`（DeepSeek 在 tool_calls 轮次常不返回 content），
  已在 UI 与文档中如实说明这是"系统按工具生成的步骤说明"。

- **修复 5 编码自动生成**：AI 原本追问用户要物料编码（用户"随便填一个"→ 编了个已存在的 G10002 → 卡片点了必然失败）。
  提示词新增「能力十三：基础资料新增/修改/删除（通用 CRUD）」——**编码一律系统自动生成，严禁索要、严禁自己编**，只有用户主动指定才传。

- **布局重排**：单条消息区块顺序 = **① 答复（置顶）→ ② 查询结果表 → ③ 写操作结果卡 → ④ 待确认卡（始终可见）→ ⑤ 执行过程（默认折叠）**；
  折叠条文案「💭 执行过程 · 思考 N · 工具 M」；待确认卡从折叠区内部**提成独立区块**；答复里不再重复卡片摘要。
  新增 `confirmSteps()/processSteps()/thoughtCount()`，删除已无引用的 `isStepVisible()`。

- **影响文件**：`frontend/src/components/AiAssistant.vue`、`ai/agent/ReActAgentService.java`、`ai/tool/ToolRegistry.java`
  （**无 SQL / 无新接口 / 无新菜单 / 无新权限**）

- **验收与方案**：`d:\erp\.trae\documents\小助手工具调用治理与答复重排_立项登记表.md`（六节 + 初始化/适配 + 8 层闭环 + 实测证据）

- **实测**：后端 8/8 PASS（真实 HTTP + 真实 DeepSeek）；前端点击 18/18 PASS（桩 SSE，确定性）；
  真实浏览器端到端 9/9 PASS（无打桩，创建物料成功且已 DELETE 清理，`pageerror`/`console.error` 0 条）。

### 17. 假成功护栏：没调工具却谎称「已准备好确认卡片」（2026-09-18 第二轮）

- **现象（用户实测）**：用户「呃，单号是SO-20260914-0005提交这张销售订单。」→ 小楷回「**已为你准备好提交操作，请在下方确认卡片里点「确认执行」**，或直接回复「确认」」
  → 用户回「确认。」→ 小楷回「我这边没有正在等待确认的操作」→ 用户：「多次之后小助手的提交操作还是无法继续下去」。

- **根因（日志实锤）**：那一轮（`03:50:00`）日志只有「工具预筛 258→91」「第 1 轮思考中…」，**没有 `调用工具: bill_action`、
  也没有 `写操作已转待确认 … token=…`** → 模型**一个工具都没调用**，却用文字谎称卡片已生成。
  - 上一轮的前端修复**行为正确**：界面上确实没卡，`findPendingConfirmStep()` 返回 null 就该回兜底话术。
  - **提示词自己教的话术**：原「写操作确认机制」写的是「因此你调用写工具后本轮循环即结束，**请直接告知用户"操作已准备就绪，
    请在确认卡片中确认"**」——把"已准备好卡片"做成了**无条件**模板，模型没调工具也会照抄。
  - 工具可见性已核（守则 10 第五节要求）：启动日志「共注册 **260** 个工具」、该用户「全量 **258** 个」、无暴露规则 WARN
    → `bill_action` 本来就在候选里（`bill_` 前缀始终常驻预筛）。**不是工具看不见，就是模型没调。**

- **修复**：① 提示词把无条件模板改成**有条件**（只有真调用写工具、系统回了"待确认卡片"才能说这句；没调工具严禁说）；
  ② 后端加**确定性护栏** `guardFakeConfirmClaim()`：无 tool_calls 分支里，若答复命中
  「已准备好 / 已准备就绪 / 已生成…卡片 / 待确认卡片已」且本轮无 `CONFIRM_NEEDED` → 替换为如实话术 + WARN 日志。

- **实测（真实后端 + 真实 DeepSeek）**：用户原话现在**真的走** `query_sale_order` → `bill_action{action=submit}` → 生成卡片
  「对【销售订单】SO-20260914-0005 执行「提交」」；确认后 `{"message":"已对【销售订单】SO-20260914-0005 执行『提交』","success":true}`，
  订单状态 **草稿 → 已生效**；护栏本轮触发 **0 次**（提示词收紧后模型已改为真正调工具，护栏属"万一再犯"的兜底）。

- **影响文件**：`ai/agent/ReActAgentService.java`（提示词 + `guardFakeConfirmClaim`）
  （**无 SQL / 无新接口 / 无新菜单 / 无新权限 / 前端零改动**）

### 18. A 口径：本轮「前端」改动对 121 隐藏（构建期开关 `VITE_ASSISTANT_UI_V2`，2026-09-18）

- **用户口径**：「小助手本次的改动全部对121隐藏。8服务器全量包」，并明确澄清**不是**"把整个小助手从 121 藏起来"。
  原因：121 因为别的 bug 必须拿新 jar，而后端是同一套源码**无法按服务器切分** → 只能在**前端按发布包**切。

- **实现（1 个构建期开关 + 4 处分流，零模板复制）**：
  - `config/featureFlags.js` 新增 `AI_UI_V2`（不设置=新版 → 8cloud/本地零变化）；`.env.customer` 设 `VITE_ASSISTANT_UI_V2=false` → 121 走旧版。
  - **版式**：消息根节点加 `legacy-ui` 类，旧版只用 **4 条 CSS `order`** 还原改造前顺序（`.msg` 本身已是 flex column）：
    执行过程折叠条 → 待确认卡 → 查询结果表 → **答复置底**（新版则是答复置顶→结果→卡片→过程）。
  - **文案**：折叠条按开关取「💭 执行过程·思考N·工具N」/「💭 查看执行过程·N 次工具调用」；卡片提示「也可以直接回复「确认」」只在新版显示。
  - **交互**：`tryLocalConfirmOnly()` 的"打字确认=点卡片"、`supersedeOlderConfirmSteps()`（旧卡失效）只在 `AI_UI_V2` 生效；
    确认词表按开关取新/旧两套（旧版不剥「操作」，保持「执行操作」照旧交给 AI）。
  - **后端 1 句措辞**：写操作拦截答复去掉「或直接回复「确认」」（121 打字确认不执行，提了会误导；点卡片按钮两包通用）。

- **代价（已写进代码注释与维护约定）**：旧版下键盘 Tab 顺序与视觉顺序不一致（改造前一致）。
  **以后每次改小助手界面/交互，都要同时确认旧版分支是否也要改。**

- **实测（真实浏览器 + 系统 Edge，桩 SSE；15/15 PASS）**：
  - 3000（新版）：y 坐标 答复 288 → 结果表 336 → 卡片 480 → 过程 587；打字「确认」→ confirm-tool 调用 1 次。
  - 3010（`--mode customer` 客户包口径）：过程 296 → 卡片 328 → 结果表 434 → 答复 570（**还原旧版**）；
    折叠条「💭 查看执行过程 1 次工具调用」；无卡片提示；**打字「确认」0 次调用**（落旧兜底话术）；**点卡片按钮仍可用**（1 次）。
  - 两模式 `pageerror`/`console.error` 均 **0**。

- **影响文件**：`config/featureFlags.js`、`.env.customer`、`components/AiAssistant.vue`、`ai/agent/ReActAgentService.java`
  （**无 SQL / 无新接口 / 无新菜单 / 无新权限**）

- **未发布**：121 / 8cloud 均未发布。发布口径：121 = `Publish.ps1 -ServerName 121`（客户包 → 开关=false）；8cloud = `-ServerName 8cloud`（全量包 → 新版）。

### 19. 修复「播报字数设了 60 却念不到」＋ 新增「播报语速」（2026-09-18）

- **现象（用户实测）**：「语音播报自定义的字数长短在播报小楷答复的时候也没有播报 60 个字啊，语音播报的语速是否可以调整呢」。

- **根因（两条，都有代码实锤）**：
  1. **播报路径把上限又硬压回 40 字**：`speakRoundResult()` 先按用户设定取摘要，随后又执行 `const room = 40 - prefix.length`
     → 选 60 字也被二次截断到约 40 字（2026-09-18 上线"播报字数自选"时漏改的旧口径残留）。
  2. **摘要器"只念第一句"**：`buildSpeakSummary()` 按 `。！？；` 切句后**只取第 1 句**再截断
     → 首句只有 10 字时，即使上限设 60 也只念 10 字（当时的口径是"简短摘要"，与用户"要念到能判断结果"的诉求冲突）。

- **口径修订（用户 2026-09-18 拍板）**：纯语音交流里"念到一半就没了"用户无法判断是否成功/是否正常运行
  （用户原话：Siri 定闹钟也会完整回一句"已定好"）→ 改为：
  ① 从第一句起**按句拼接**念到接近用户设定字数；② 整句会超时用剩余内容补齐，并在**自然停顿（。！？；，、）处收尾**，
  **绝不硬切到半个词**；③ 确无任何停顿点才按字数截断加省略号。

- **新增「播报语速」**（用户拍板：放小助手 ⚙️ 设置、4 档、本机偏好）：`0.8 慢（听得清）` / `1.0 标准（默认）` /
  `1.2 稍快` / `1.5 快`。落在 `voiceNotify` 模块（localStorage `erp.voiceNotify.rate`），**不走 sys_config**（语速是个人耳习惯，不该全公司统一）。

- **关键实现点（勿回退）**：
  - `buildSpeakSummary(text, limit)` 重写为"按句拼接 + 自然停顿收尾"（原"只念第一句"作废）。
  - `speakRoundResult()` 去掉写死的 `room = 40`，改按 `speakLimit` 算剩余空间。
  - `voiceNotify.js` 模块级 `let speakRate = readRate()` —— **模块加载即读本机持久值**：小助手组件 `setup` 早于
    `MainLayout` 的 `onMounted(=initVoiceNotify)`，只靠 init 赋值会导致**刷新后 ⚙️ 里错误回显默认 1.0**（本轮实测踩到并修掉）。
  - `estimateSpeakMs()` 按语速折算（慢速实际更久）→ 否则 `isSpeaking()` 提前判"说完了"，
    `armNextVoiceRound()` 会在 TTS 还没念完时就去"等下一句"，把播报声录回来。
  - 新增项加 `v-if="VOICE_FEATURES_ENABLED"` → **121 客户包看不到该项**（A 口径：本轮前端改动对 121 隐藏）。

- **实测（守则06 三层 + 守则07）**：写后必验磁盘/网络全 True；Playwright 点击级 **17/17 PASS**
  （60 字 → 实测念 50 字、20 字 → 念 20 字、rate=0.8、结尾落在"…0005，"完整停顿处、刷新后回显正确、`CONSOLE_ERRORS=NONE`）；
  真实后端端到端 1 轮（只桩 ASR，Agent 走真实 DeepSeek）：「已为你查到销售订单数据，共 58 张，全部为「已生效」状态。…」按 rate=0.8 念出。
  入口截图 `d:\erp\dogfood-output\播报字数与语速_设置项.png`。

- **影响文件**：`frontend/src/utils/voiceNotify.js`、`frontend/src/components/AiAssistant.vue`
  （**无 SQL / 无新接口 / 无新菜单 / 无新权限 / 后端零改动**）

- **配套文档**：`.trae/documents/小助手播报字数与语速_立项登记表.md`（含初始化/适配方案、8 层闭环核对表、5 步冒烟、全部实测输出）

- **未发布**：121 / 8cloud 均未发布（纯前端，待用户确认后按需随包发布）。

### 20. 销售模块通用插件适配：明细行编辑 + 条件搜索推广到 6 种单据（2026-09-18）

- **需求原文**：用户「把小楷助手可以操作销售订单的全部细节整理成说明书，并且抽成通用插件，适配整个销售模块」
  → 拍板范围：**明细编辑 + 条件搜索**（两项都推广），方式：**逐一核实后直接实现**。

- **实查结论（先查后改）**：项目**已有**插件式框架，销售订单只是第一个"样板单"，缺的不是框架而是适配器：

  | 能力 | 插件框架 | 适配前 | 适配后 |
  |---|---|---|---|
  | 查询 | `EntityCrudConfig` + `GenericQueryTool` | 6 种单据全有 | 不变 |
  | 流程动作 / 下推 | `BillActionRegistry` 能力矩阵（29 种单据） | 6 种单据全有 | 不变 |
  | **明细行编辑** | `BillItemHandler` + `BillItemRegistry` | **仅销售订单** | **6 种全有** |
  | **条件搜索** | `EntityCrudConfig.filterFields` | **仅销售订单** | **6 种全有** |

- **本次改动（无 SQL / 无新接口 / 无新菜单 / 无新权限 / 前端零改动）**：

  1. **新增 5 个明细行适配器**（`ai/bill/impl/`，实现 `BillItemHandler` + `@Component`，
     `BillItemRegistry` 启动自动收集 → 工具自动支持，**未改一行工具代码**）：
     `SaleQuoteItemHandler` / `SaleContractItemHandler` / `SaleDeliveryItemHandler` /
     `SaleReturnItemHandler` / `SaleInvoiceItemHandler`。
     - 可改字段统一 4 个：单价 / 数量 / 税率 / 折扣率（金额类派生列由保存链路重算，禁止 AI 直改）；
     - 保存链路**必须走原 Service 整单保存**（`saveQuoteWithItems` / `saveContractWithItems` /
       `saveDeliveryWithItems` / `saveReturnWithItems` / `saveInvoiceWithItems`）→
       与界面点"保存"完全同一条路：重算 + 设置中心校验 + 已生效/已完结不可改。
  2. **补 5 组 `filterFields` 白名单**（`AiCrudAgentService.registerSale()`）：
     报价单/合同/出库单/退货单/发票各一组（status + 单号 + customerId + 单据日期 + totalAmount + closeStatus）。
  3. **客户名统一走 `AiCrudAgentService.foreignDisplayName(entity)`**（既有"外键 → 主档名"单一适配点）——
     因这些单据实体的 `customerName` 是 `@TableField(exist=false)`，反射恒为 null；
     合同例外：优先用真实列 `customerShortName`。

- **逐一核实的关键差异（坑，勿混淆）**：**销售合同明细没有 `line_no` 列**，行序字段是 `sortNo`（列 `sort_no`），
  明细主键是 `itemId`（列 `item_id`）；其余 5 种单据均为 `lineNo` + `xxxItemId`。合同明细的 `unit` 是真实列（其余是 exist=false）。

- **三层验证（守则06，全部真实输出）**：

  | 层 | 验证 | 结果 |
  |---|---|---|
  | 编译 | `mvn -o -q -DskipTests compile` | **EXIT=0**；`target/classes` 下 6 个 Handler class 齐全 |
  | 启动 | 后端启动日志 `BillItemRegistry` | **`已接入单据`：SALE_CONTRACT / SALE_DELIVERY / SALE_INVOICE / SALE_ORDER / SALE_QUOTE / SALE_RETURN；共接入 6 种，冲突丢弃 0** |
  | 对库核验 | 78 个 (表,列) 对（filter/item/pk/line/fk/mainpk/no）逐条对 `information_schema` | **0 缺失**（防"字段名写错编译器不报、运行时静默为空"） |
  | 接口·条件搜索 | 真实 AI：「查一下状态是草稿的销售合同」→ `query_sale_contract` + `filters[status=草稿]` | **返回 8 条草稿合同**（白名单生效） |
  | 接口·明细读取 | 真实 AI：「列出销售合同 SC-20260902-0001 的明细行」→ 先查主键 → `bill_item_list{SALE_CONTRACT, billId=4}` | **返回「第1行 E102 异形钢化玻璃 \| 数量1.00 \| 单价9.80 \| 金额9.80」**（新适配器 + `sortNo` 行号正确） |
  | 接口·明细写入 | 真实 AI + 真实点确认：改成 12.34 → `POST /chat/confirm-tool` → 改回 9.80 | 改后 DB `unit_price=12.34 amount=12.34 net=13.94`（**金额/税额重算**）；改回后 DB `unit_price=9.80 amount=9.80 net=11.07`（**与改动前完全一致，无残留**） |

- **配套说明书**：`docs/小楷助手销售订单操作说明书.md`（守则09 五要素 + 销售模块 6 种单据适配对照表）。

- **仍未做（本轮不在范围）**：销售模块的**数据权限**目前只有 `SALE_ORDER` 接入了 `isSalesOnly` 过滤
  （`AiCrudAgentService` 第 1101 行）；其余 5 种销售单据的数据权限未扩展，need 另行评估。
  列表页「导出 / 打印」、编辑页「选单 / 新建」仍未做（见第 16 节遗留）。

## 二、关键机制说明（读代码核实）

| 机制           | 位置                                                                       | 说明                                                                                                                                                          |
| ------------ | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Agent 流式步骤   | `sendAgent` 内 SSE 解析                                                     | 步骤类型：THOUGHT / ACTION / OBSERVATION / CONFIRM\_NEEDED / ERROR / FINAL\_ANSWER                                                                               |
| 查询结果提升       | 收到带 data 的 OBSERVATION                                                   | 提升为消息级 `msg.mainResult` 独立表格（**这是折叠过程不丢数据的原因**）                                                                                                             |
| 写操作两段式确认     | `confirmWriteTool` / `cancelWriteTool`                                   | 确认后单独 push 一条 bot 消息（带 actionResult），部分成功显式提示                                                                                                               |
| 语音识别 ASR     | 录音上传 → `POST /api/ai/speech/recognize`（`ai.asr.provider=local` 走自建 ASR）  | **只有这一条链路**：浏览器 `SpeechRecognition`（云端 + 设备端语音包）那套已于 2026-09-18 整体删除 —— 云端国内连不通、设备端语音包网页无权安装（实测 Edge 153 对 zh-CN/en-US 全 `unavailable`、`install()` 0ms 空操作） |
| 唤醒词匹配        | `utils/voiceWakeup.js` 的 `WAKE_RE_SOURCE`（全项目唯一来源）                       | `小楷助手` 主词 + 兼容 `小楷小楷` + 楷/凯/开 同音容错；命中后由 `utils/serverWakeup.js` 派发 `erp:voice-wakeup`（detail：`text` / `wakeOnly` / `cancel`）                                |
| 本地语音采音       | `utils/serverWakeup.js` + 抽屉「🧪 语音采音：开/关」                                | 浏览器只做自适应能量门限 + 上传约 1.5 秒短片段，识别与唤醒词匹配都在自建 ASR；由 🚀纯语音 或 🧪标签开启；`armVoiceCommand(ms)` 可手动切回"等指令"态（口头确认靠它）                                                     |
| 纯语音（不打开界面）   | `voiceSilent` + `onWakeupEvent` + `askVoiceConfirm/onVoiceConfirmAnswer` | 语音指令**不打开抽屉**、后台执行、只语音播报结果；写操作 `CONFIRM_NEEDED` 也不弹卡片 → 语音问「请说确认或取消」→ 口头确认后直接 `confirmWriteTool`；15 秒没回答退回显示确认卡（不丢单）；与「🗣️ 语音需确认」互斥                         |
| 等待期进度播报      | `startProgressVoice` / `sayProgress`（1.2 秒 / 8 秒 / 最小间隔 1.5 秒）           | **只在纯语音下发声**：1.2 秒后「好的，我正在思考」、收到 `ACTION` 后「正在查询数据」、超 8 秒兜底「还在查询中」；两次进度至少隔 1.5 秒（不足则延后，结果一到就取消）                                                             |
| 多轮语音沟通       | `armNextVoiceRound()` → `armVoiceCommand(10000)`                         | 结果播报**真正结束后**（`voiceNotify.isSpeaking()` 轮询，30 秒兜底）+300ms 把采集器切回"等指令"态 → **接着说下一句即可，不必再喊唤醒词**；10 秒不说则浮球复原；上下文靠同一个 `sessionId` 连续                            |
| 指令派发硬上限      | `serverWakeup.js` 的 `CMD_FORCE_SEND_MS=9000` + `cmdFirstAt`              | 原派发条件要求"窗口到点 **且没在收集人声段**"；用户停顿 <1.5 秒接着说会让该条件永不成立 → 指令被无限合并、**永远发不出去**（2026-09-18 实测 ASR 23 次 / agent 0 次）。硬上限保证最迟 9 秒无条件发出                                |
| 语音指令结果播报     | `speakRoundResult()` + `voiceNotify.buildSpeakSummary(text, limit)`      | **仅语音唤醒/🎤 发起的轮次**（`voiceRound`）才播；成功念答复摘要、失败念「操作失败，…」；摘要**按用户设定字数（20/30/40/60）从第一句起按句拼接**，超出时在自然停顿（。！？；，、）处收尾、不硬切半个词（2026-09-18 口径修订，原"只念第一句/40 字内"作废）；弹确认卡时不播、等拍板后再播执行结果 |
| 播报语速（本机偏好）   | `voiceNotify` 的 `getSpeakRate/setSpeakRate` + `u.rate = speakRate`      | 小助手 ⚙️ 设置「展示与播报」4 档：0.8 慢 / **1.0 标准（默认=升级前行为）** / 1.2 稍快 / 1.5 快；localStorage `erp.voiceNotify.rate`，**不进 sys_config**；模块加载即读本机值（避免刷新后回显默认值）；`estimateSpeakMs()` 按语速折算时长，防播报未结束就提前"等下一句"；客户包 `VITE_VOICE_FEATURES=false` 时该项整条不显示 |
| 界面导航 · 本地快匹配 | `AiAssistant.vue` 的 `tryLocalOpenPage` / `matchPage`                     | 说「打开 XX」先在本地匹配页面清单（单一来源 `menuConfig.js`，含设置中心子项与自定义菜单名）直接跳，**0 token、语音场景最快**；精确→互相包含→取最长命中；**同长度多义（"订单"→销售/采购订单）判歧义返回 null 交 AI**，不乱跳                      |
| 界面导航 · AI 工具 | `open_page` 工具 + SSE 的 `toolResult.navigate`                             | 后端只回 `{page,recordId?,entityType?}`（**不存页面清单、不回 route**）；前端解析路由 + 用 `menuRoutePaths` 做菜单权限校验（路由守卫 /403 兜底）；`pendingNavigate` **等流结束再跳**；失败给候选 / 无权限话术       |
| 答复布局顺序 | `AiAssistant.vue` 模板（msg 内区块顺序） | **① 答复（置顶）→ ② 查询结果表 → ③ 写操作结果卡 → ④ 待确认卡（`confirmSteps`，始终可见）→ ⑤ 执行过程（`processSteps`，默认折叠）**；折叠条「💭 执行过程 · 思考 N · 工具 M」 |
| 打字/说话「确认」 | `findPendingConfirmStep()` + `tryLocalConfirmOnly()` + `lastBotClaimsPendingAction()` | 有待确认卡时，「确认/取消」等价于点卡片按钮（同一接口 `POST /api/ai/chat/confirm-tool`）；**没有卡时**，只有"小楷上一条回复**声称已生成/执行了操作**"（已准备好 / 请在确认卡片中确认 / 已发起 / 已提交…）才回本地兜底话术（防重复发起 / 幻觉假成功）；上一条是**正常提问**（如"请回复确认""这套结构可以吗"）则**放行给 AI** —— 否则对话里的「确认」会被吞掉，用户体感"上下文丢了"（2026-10-06 用户实测修复） |
| 多张确认卡治理 | `supersedeOlderConfirmSteps()`（按 `confirmToken` 比较） | 新卡到达即把旧的未处理卡标 `superseded` → 显示「该操作已失效（已被下方新的操作取代）」并隐藏按钮；**不能用对象引用比较**（Vue 响应式代理会导致恒不相等） |
| 执行过程文案业务化 | `ReActAgentService.describeToolAction()` + `ToolRegistry.entityLabelOfTool()` | 模型返回了推理文字就用原文，否则生成「正在查询：物料」这类业务说明（不伪造模型推理）；工具中文名单一来源 = `ENTITY_NAME_MAP` |
| 通用 CRUD 卡片摘要 | `ReActAgentService.buildGenericCrudSummary()` | 「新增物料：小助手、成品」/「修改物料 #12：materialName=…」/「⚠ 删除后不可恢复 / 删除客户 #5」——不再输出裸工具名与原始 JSON |
| 假成功护栏 | `ReActAgentService.guardFakeConfirmClaim()` | 无 tool_calls 分支里，答复命中「已准备好/已生成…卡片」而本轮**没有** `CONFIRM_NEEDED` → 替换为如实话术 + WARN 日志 `命中假成功护栏`；真有卡片则原样放行。提示词已同步收紧（无条件模板 → 有条件） |
| 界面版本分流（A 口径） | `AI_UI_V2`（`VITE_ASSISTANT_UI_V2`）+ `.msg.bot.legacy-ui` 的 4 条 `order` | 客户包(121)=旧版：答复置底 / 折叠条「查看执行过程」/打字确认不执行/无"直接回复确认"提示；全量包与本地=新版。**零模板复制**（靠 flex order），代价=旧版 Tab 顺序与视觉顺序不一致 |
| 组件挂载范围       | `MainLayout.vue` 的 `v-if="!isSettingsPage"`                              | 设置中心页不渲染小助手；语音播报已解耦到 `utils/voiceNotify.js`，不受影响                                                                                                            |


## 三、规划中

- 暂无登记

## 四、踩坑记录（不再犯）

1. **"执行过程默认折叠"的前提是结果另有出口**：查询数据已提升为 `mainResult` 表格、写操作结果已单独成消息，
   所以折叠 OBSERVATION 不丢结果。日后若新增"只在步骤里返回数据、且不会被提升为 mainResult"的工具，
   **必须先给它加独立可见出口**，否则用户看不到数据。
2. **待确认卡不能跟着折叠**：它是「AI 的提问」，折叠后用户无法拍板 → `isStepVisible` 对 `CONFIRM_NEEDED` 必须返回 true。
3. **删模块选择器前先核清引用点**：`activeModule` 同时被模块 chips 模板与 `currentQuickCmds` 引用，
   只删模板会留下悬空引用 → 先 grep 全部引用（模板 + script）再删；删后必须再 grep 一次确认零残留。
4. **语音唤醒绝不能做"会话级不可用锁定"**：早期版本把失败原因写进 `sessionStorage` 并永久拒绝再次开启，
   导致"用户点一次失败 → 之后怎么点都提示不可用、**刷新页面也不解除**（同标签页 sessionStorage 仍在）"。
   正解：**失败只关开关 + 说明原因，永不锁定**，让用户修好权限/网络后能随时重试；
   防刷屏改用「同一失败原因 60 秒内只提示一次」的内存去重。
5. **唤醒与 🎤 点按说话的可靠性不一样**：🎤 有「浏览器识别失败 → 录音上传云端 ASR」的回退路径，
   唤醒是**持续监听、没有回退**（无法持续上传音频做唤醒词匹配），所以国内常见表现是"🎤 能用、🎧 不能用"。
   属浏览器限制，UI 提示必须写清原因并给出替代路径。
6. **唤醒词残留**：唤醒后必须立即清空「前置缓冲」（`preBuffer`），否则下一轮唤醒时上一轮的"小助手"
   会被当成指令内容拼进去（现象：发送出 `小助手。帮我打开刚刚的审批订单。`）。
7. **唤醒词"重复识别"必须按最后一次裁剪**：浏览器持续识别会把同一句"小助手"给出多次
   （interim→final 重复、或静音重启后重复），若按**第一个**唤醒词裁剪，剩下的那个"小助手"
   会被当成指令发送（现象：只喊一声，却自动发出"小助手。"）。
   正解：按**最后一次**出现的位置裁剪 + 对结果**反复剥离开头的「唤醒词+标点」**（双保险）。
8. **唤醒交互必须"先聆听、后开界面"**：唤醒瞬间就打开抽屉并发送，用户会觉得"我还没说话它就发了"。
   正解（用户确认的交互）：喊唤醒词 → 浮球变「🎙️ 聆听中…」（绿色呼吸动画）+ 语音应答；
   **不打开界面、不发送任何内容** → 指令收集完（静默 1.5 秒）才打开抽屉 → 填输入框 → 发送；
   唤醒后 12 秒没听到指令 → 自动取消聆听态、浮球恢复常态。
9. **「记住上次选择」会死在 onMounted 的无条件覆盖上**：`initWakeup()` 已从 localStorage 读回上次选择
   （开着会自动恢复监听），但 `onMounted` 里遗留了一行 `wakeupOn.value = false` 把它**无条件覆盖** →
   现象 = "开启后刷新一次就变回关闭"。更坑的是这只是**UI 假象**：内存里 `enabled` 仍为 true、麦克风还在监听，
   用户再点一下反而触发 `stopWakeup()` 真正关掉并**清掉 localStorage 记忆**（越点越乱）。
   正解：`initWakeup()` 之后**必须按真实状态下发**（`getWakeupState().on`），并**先绑状态事件再初始化**。
   日后凡"状态从存储恢复"的路径，都要检查有没有无条件赋值的残留。
10. **「改完了」≠「改对了」：本轮差一点交付一个"等于没改"的修复。**
    首版 `fillForeignDisplayNames()` 号称已修，实际**一行都没生效** —— 它从**结果 Map** 取 `customerId`，
    而结果 Map 只装 `displayFields` 列出的键（该配置主键是 `orderId`，压根没有 `customerId`）→ `ids.isEmpty()` 直接 return。
    两个固化教训：

    - **补全/回填类逻辑，取值来源必须与数据结构对齐**：外键 ID 要从**实体**取（实体一定有），不要从"已经裁剪过的结果 Map"取；

    - **这类改动编译器零防护**（字段名是 String，写错不报错、运行时静默为空）→ 必须额外做
      「**实体字段反射校验**：断言每个 `displayFields`/`searchFields`/`nameField`/`codeField`/`primaryKeyField`
      都真实存在于实体类」+「**`searchFields`** **列对库校验**：断言每个字段转下划线后确实是该表的列」。
      本轮就是靠这两道查出 36 个错字段名 + 18 个会让关键词搜索直接 SQL 报错的配置
      （其中 6 个如 `sale_quote.customer_name` 这类"字段名看着对、列其实不存在"，**只有对库核验才查得出**）。
      附带铁律：`searchFields` 会被 `camelToUnderscore()` 当成**列名**拼进 `LIKE`，
      所以**数值型列（如** **`material_id`）绝不能放进** **`searchFields`**（会报 `operator does not exist: bigint ~~ text`）。
11. **叠词唤醒词必须做"标点容错"**（2026-09-17）：唤醒词改成叠词「小楷小楷」后，浏览器识别可能在两次之间
    插入逗号（`小楷，小楷`）→ 若 `WAKE_WORDS` 只写连写形式，会出现"喊了没反应"（用户会误判成识别坏了）。
    正解：词表同时收录 `小楷小楷` / `小楷，小楷` / `小楷,小楷`；`matchWakeWord` 与 `stripWakePrefix` 复用同一份词表，天然一致。
12. **结果播报必须区分"谁的轮次"和"是不是结果"**（2026-09-17）：① 只在语音唤醒发起的轮次播报（打字不发声）——
    用一次性标记 `voiceRound`，在 `sendAgent` 开头**取走并清空**，否则下一轮打字对话会被误播；
    ② **待确认卡不是结果** —— 弹卡时先不播，等用户拍板后再播真正的执行结果，否则会"先念卡片再念结果"；
    ③ 确认成功 / 确认失败 / 点取消三条路径都要清 `voiceConfirmPending`，漏一条就会漏播或把旧标记带到下一次。
13. **"不打开界面"必须给用户一条活路**（2026-09-18）：纯语音模式下没有界面，一旦"等确认"卡在无处可答的状态，用户就彻底卡住。
    正解三件套：① 确认问询有超时（15 秒）→ 超时自动**显示确认卡**；② 采集器没在跑（麦克风没开）→ 直接退回显示确认卡，不假装在听；
    ③ 没听清 → 重问一次并重新 `armVoiceCommand()`（派发一次后采集器已退出"等指令"态，不重新武装就永远收不到下一句）。
    推论：**"不打开界面"必须与"语音需确认（说完只填输入框）"互斥** —— 后者要求用户看输入框，两者同时开就是死状态，所以开纯语音时自动切回语音直发。
14. **浏览器麦克风不能"加载即开"**：想"刷新后仍能用纯语音"，只有在**本页第一次点击/按键**时才能 `getUserMedia`（浏览器硬规定），
    页面加载时直接开会失败；正解＝记住"上次开着"的偏好 + 绑一次性首次交互监听补启动（用户此前对唤醒开关明确提过这个要求）。
15. **"延迟发送"的合并逻辑必须有硬上限，否则会永久卡死**（2026-09-18，用户报"第二轮指令收不到"的根因）：
    派发条件是 `窗口到点 && !speaking && !uploading`。"别在用户还在说话时发送"是对的，但\*\*`speaking` 可能长期为 true\*\*
    （用户说完停顿不足 1.5 秒就接着说 / 环境持续有声）→ 条件永不成立 → 待发送文本被无限合并，
    而"等指令超时取消"那条又只对 `!pendingCmdText` 生效（有 pending 就不取消）→ **这条指令永远发不出去**。
    正解：记 `cmdFirstAt`（**第一次**识别出文字的时刻，合并时不刷新）+ `CMD_FORCE_SEND_MS` 硬上限，到点**无条件发出**。
    通用教训：任何"满足 A 条件才执行、而 A 依赖外部持续状态"的延迟队列，都必须配一个**基于时间的兜底出库**。
16. **`speechSynthesis.speaking`** **不能当唯一真相**：Chromium 上 `cancel()` 紧跟 `speak()` 偶发不复位，
    若采集侧直接用它判断"自己在播报"就会**永久丢弃麦克风采集**（表现为"喊了没反应"）。
    正解：`speaking && now < 按字数估算的播报截止时间`（`voiceNotify.isSpeaking()`），估算超时即视为已说完。
17. **验证"语音轮次"必须用真实音频驱动 VAD**：只 `dispatchEvent` 模拟唤醒事件只能验证前端分支，
    验不出"识别到了却发不出去"这类采集器内部死锁。手段：`--use-file-for-fake-audio-capture=<wav>`
    （生成"静音 N ms + 440Hz 音调 1200ms"循环，音调 RMS≈0.35 稳过门限；**静音段必须 ≥ 指令态切句门槛 1200ms**），
    配合打桩 `/api/ai/speech/recognize` 按调用序返回文本、打桩 TTS 模拟 speaking 时长、打桩 agent 记录 userInput。
18. **新工具必须登记进「工具预筛」常驻组，否则"有工具却看不见"**（2026-09-18 界面导航实锤）：
    `ReActAgentService.filterToolsByUserInput()` 会按用户输入里的实体关键词把候选工具收窄，
    只保留 `ALWAYS_TOOLS` / `BASELINE_QUERY_TOOLS` / `setting_*` / `bill_*` / `import_*` / 命中实体的 CRUD。
    `open_page` 的开场语料**几乎必带实体词**（"打开销售订单""打开即时库存"）→ 不在常驻组就被整体丢弃 →
    **AI 看不到工具，只在文字里回"已为你打开"的假成功**（实测「打开即时库存」`CALLED_TOOL=False`、流长仅 335）。
    正解：新工具上线时必须同时决定"它是否该常驻预筛候选"；`open_page` 已加入 `ALWAYS_TOOLS`。
    （同 2026-09-13「setting\_/bill\_ 必须常驻」那条教训的第二次踩中。）
19. **提示词要显式禁止"假成功"**：能力型工具（导航/写操作）必须写明「**必须真正调用工具**，
    只在文字里说"已完成"＝错误行为」。否则模型偶发跳过工具调用、直接给成功话术，用户界面毫无变化却以为成功。
20. **复测 AI 链路必须每次换** **`sessionId`**：会话历史按 `sessionId` 缓存，复用同一个 session 会把上一轮
    "已为你打开【X】"带进上下文 → 模型有样学样**不再调工具**，表现为"多个用例整齐失败、流长一模一样"
    （实测 4 个用例全 335、全 `CALLED_TOOL=False`）。看到"整齐失败"先怀疑上下文污染，而不是逐个查代码。
21. **详情页路由参数** **=** **主键，不是单号**（2026-09-18 用户实测"打开订单是空白"）：
    项目里所有详情页都是 `route.params.id` **直接当主键**调接口（如 `SaleOrderEdit.loadDetail()` → `getSaleOrder(route.params.id)`）。
    传单号进去不会报错，只是**查不到 → 整页空白**，非常难从现象反推。凡是"拼详情路径"的地方
    （AI 导航、下推跳转、消息里的链接）都必须**先转成主键**；只拿得到单号时，先查询拿主键，或只打开列表页并如实说明。
22. **菜单分组名 ≠ 页面**：`基础资料 / 销售管理 / 系统管理` 这类是**分组**（`menuConfig.js` 的 group，没有独立路由）。
    用户很自然会说"打开基础资料"，此时 AI 若当页面处理、前端按页面匹配 → 必然"AI 说打开了、前端说没找到"。
    正解：前端先判"是不是分组"，是则回问"你要打开里面哪一项"并给该分组下**有权限的**子项候选；提示词里也写明分组名不是页面。
23. **本地快匹配最容易犯的错是"吞掉用户的附加条件"**：只匹配「打开X」就跳，会把"打开销售订单，**2026年7月26号的**订单"变成"只打开列表"，
    而且**用户不会收到任何异常提示**（看起来功能正常）。任何"关键词命中即短路"的本地捷径，都必须先判断
    "句中除关键词外是否还有实义信息（数字/日期/其他实词）"，有则**让给 AI**。
24. **"等用户口头回答"的窗口必须等播报结束后再开**（2026-09-18 用户报"语音确认永远不生效"的根因）：
    采集器在**播报期丢弃音频**（`startServerWakeup` 传了 `isSpeaking`），而问询话术本身要念 5\~12 秒；
    若一边念一边开窗口（原实现 15 秒），用户听完再开口时窗口已被吃光 → 必然超时。
    正解：`等 isSpeaking 变 false（最多 20 秒，没检测到播报则最多 3 秒）→ 再 armVoiceCommand(30 秒)`，
    并且**超时要能重问**（最多 2 次），否则一次没接上就永久掉回"点卡片"。
    通用教训：任何"念一句 → 等用户回答"的语音交互，计时起点必须是**播报结束**，不是播报开始。
25. **纯确认词不能交给 AI**（2026-09-18 用户实测"连说三次确认，每次都重新发起一遍"）：
    "确认/确认执行/提交他"这类**没有别的信息**的短句，在"没有待确认操作"时应由前端本地如实回
    「我这边没有正在等待确认的操作…」；一旦发给 AI，模型会**重新发起一遍写操作**（新卡片，旧卡作废），
    甚至**幻觉"已提交/已发起"**（用户以为办好了，实际什么都没执行）。判定用"确认词表 + 动作词 + 代词/量词"全部剥离后为空，
    避免把"提交 SO-20260914-0004"（带对象）也拦下来。
26. **「本地兜底话术」必须先把"界面上的真实状态"查一遍**（2026-09-18 用户实测"连说确认都不执行"的根因）：
    `tryLocalConfirmOnly()` 的设计初衷是"没有待确认操作时别让 AI 重新发起一遍"，但它只判了"句子是不是纯确认词"，
    **从没查过界面上有没有待确认卡** → 卡片明明在眼前，用户打字说"确认"照样被回"我这边没有正在等待确认的操作"，
    输入还被吞掉（用户完全无法理解）。正解：本地兜底前必须先查真实状态（`findPendingConfirmStep()`），
    有卡就走真实执行路径（与点按钮完全同一条接口），没卡才回兜底话术。
    通用教训：**任何"本地短路"分支都必须以"当前界面/会话的真实状态"为前提，不能只看用户这句话长什么样**。
    （第 25 条的口径仍然有效：**没有卡时**才回兜底话术，两者是"先查状态、再分两条路"，不冲突。）
27. **Vue 响应式数组里的元素不能用 `===` 做引用比较**（2026-09-18 实锤）：
    `supersedeOlderConfirmSteps()` 首版用 `s === currentStep` 跳过当前卡，而 `messages` 是响应式数组，
    push 进去后读回来的是**元素的代理对象**，引用比较**恒为 false** → 新卡把自己也标成"已失效"
    （现象：卡片只剩"已失效（已被下方新的操作取代）"、按钮全没、打字"确认"永远无卡可执行）。
    正解：按业务唯一键比较（这里用 `confirmToken`）。通用教训：**在 Vue 响应式集合里做"排除自己"时，
    一律用业务 ID 比较，不要依赖对象引用**；这类 BUG 编译不报、只表现为"功能莫名不生效"。
28. **提示词里的"话术模板"必须带上前置条件，否则会变成模型假成功的台词**（2026-09-18 第二轮实锤）：
    原提示词写「因此你调用写工具后本轮循环即结束，**请直接告知用户"操作已准备就绪，请在确认卡片中确认"**」
    —— 本意是"调用之后这么回"，但模型把它当成了**任何时候都能说的台词**：某一轮它压根没调用任何工具，
    照样回了「已为你准备好提交操作，请在下方确认卡片里点「确认执行」」→ 用户去找一张不存在的卡片，
    回「确认」被如实拒绝，体感是"系统坏了、提交永远走不通"。
    正解两条：① 话术**必须写成带条件的**（"只有 X 之后，才能说 Y；没有 X 时严禁说 Y"）；
    ② **不能只靠提示词** —— 加确定性护栏（`guardFakeConfirmClaim()`：命中话术式 + 本轮无对应步骤 → 替换 + WARN）。
    通用教训：**凡是"让模型说某句固定话"的提示词，都要问一句"模型没做那件事时会不会也这么说"**；
    并且**关键不变量要有代码级校验，不能只有提示词约束**（同 19 条：提示词单靠约束已经失败过两次）。
    排查手法（本次有效）：**读后端日志逐轮对照"到底调没调工具"** —— 有 `调用工具: xxx` + `写操作已转待确认 token=…`
    才算真的发起了操作；只有「第 N 轮思考中…」就结束的轮次 = 模型什么都没做。
29. **"设置项"上线后必须回头核一遍"这条链路上还有没有写死的旧值"**（2026-09-18 用户实测"设了 60 字还是念不到 60"）：
    「播报字数」当时只在 `buildSpeakSummary(text, speakLimit)` 一处接上了设置，但**下游 `speakRoundResult()` 里还留着
    `const room = 40 - prefix.length` 的旧口径**，把用户选的 60 又硬压回约 40 —— **设置项"看起来生效了"，实际被下游写死值吃掉**。
    同类第二坑：`buildSpeakSummary()` 当时**只念第一句**，首句短的时候（比如 10 字）即使上限 60 也只念 10 字
    → 用户对"设置不生效"的体感进一步加重。
    通用教训：① 新增"可配置项"时，必须把**整条链路上的旧常量全部 grep 一遍**（本例 `40` 出现在函数默认参数、组件内二次截断两处），
    只改一处等于没改；② 默认值要保证"不配置＝升级前行为"（本例默认 1.0 语速 / 40 字）；③
    **配置值要真的改变用户可感知的结果**，验证时必须断言"设 60 → 实际念出的长度"，不能只断言"localStorage 写进去了"。
30. **模块级状态的"读持久值"时机不能依赖某个组件的生命周期钩子**（2026-09-18 本轮实测踩到）：
    本机语速首版只在 `initVoiceNotify()`（`MainLayout` 的 `onMounted`）里 `speakRate = readRate()`，
    而小助手组件（在 `MainLayout` 模板内）的 `setup` **早于** `onMounted` 执行 →
    刷新页面后 ⚙️ 里的「播报语速」**回显默认 1.0**（localStorage 明明是 0.8），用户会以为"设置没保存住"。
    正解：模块级变量**在模块加载时就读持久值**（`let speakRate = readRate()`，函数声明有提升，可用）。
    通用教训：本机偏好这类"模块级单例 + 组件回显"的组合，取值时机必须是**模块加载**或**读取时实时读**，
    不能挂在某个父组件的挂载钩子上。

