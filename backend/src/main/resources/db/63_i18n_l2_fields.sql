-- 长句标题的 key（前缀 field. + 中文原文）会超过原 128 上限，先扩容（幂等）
ALTER TABLE sys_i18n ALTER COLUMN i18n_key TYPE VARCHAR(512);
-- 长句的翻译正文（中文标题/英文翻译）会超过原 255 上限，同步扩容（幂等）
ALTER TABLE sys_i18n ALTER COLUMN "text" TYPE TEXT;

-- ============================================================
-- L2 多语言：字段标题整条覆盖（field.<中文标题>）
-- 用途：词素组合翻译不理想的长句/专有名词，在此逐条指定英文；
--      运行时 $tf() 优先取语言包命中值，未命中才走词素组合。
-- 维护：也可在 设置中心 → 语言管理 里改（module=page），本文件是初始种子。
-- 幂等：按 (i18n_key, lang) 先查后插，可重复执行
-- ============================================================

DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('当前状态不允许编辑', 'Not editable in current status'),
        ('快递100授权码', 'Kuaidi100 Auth Code'),
        ('注册百递云API', 'Register Baidi Cloud API'),
        ('选单 - 选择源单据', 'Select Bill - Choose Source Bill'),
        ('每月最后一个工作日', 'Last working day of each month'),
        ('每 3 月（季）', 'Every 3 months (quarterly)'),
        ('每 6 月（半年）', 'Every 6 months (half-year)'),
        ('每周（*）', 'Weekly (*)'),
        ('备份（时间戳）', 'Backup (timestamp)'),
        ('正式版（指定到期日）', 'Official (fixed expiry)'),
        ('净金额(netAmount)', 'Net Amount (netAmount)'),
        ('新客户数目标(个)', 'New Customer Target (count)'),
        ('开票合计(含税)', 'Total Invoiced (Tax Incl.)'),
        ('运营费用/费用分摊', 'Operating Expense / Expense Allocation'),
        ('应付结余(含预付)', 'AP Balance (incl. Prepayment)'),
        ('应收结余(含预收)', 'AR Balance (incl. Prepayment)'),
        ('临期预警（30天内到期）', 'Expiry Alert (due within 30 days)'),
        ('即将超期 / 已超期', 'Due Soon / Overdue'),
        ('库存移动平均成本', 'Stock Moving Average Cost'),
        ('MRP运算历史记录', 'MRP Calculation History'),
        ('MRP 运算方案', 'MRP Scheme'),
        ('已取消，未做任何数据变更', 'Cancelled, no data changed'),
        ('🔍 未找到匹配的物料，建议上传更清晰的物料图片', 'No matching material found. Please upload a clearer material image.'),
        ('单号可能属于以下多个快递公司，请手动选择：', 'The tracking number may belong to multiple couriers, please select manually:'),
        ('服务器上还没有备份（首次部署成功后自动产生）', 'No backup on the server yet (created automatically after the first successful deployment)'),
        ('重置后密码将被设置为默认密码：123456789，员工登录后可自行修改。', 'After reset the password becomes the default 123456789; employees can change it after signing in.'),
        ('配置租户自有Key后，该渠道优先使用租户Key调用AI，免平台配额限制，费用由租户自行承担。', 'Once a tenant key is configured, this channel calls AI with the tenant key first, bypassing platform quota limits; costs are borne by the tenant.'),
        ('您的API Key将加密存储在数据库中，不会明文显示或硬编码在代码中', 'Your API Key is stored encrypted in the database; it is never shown in plain text or hard-coded.'),
        ('线索可【转化】为正式客户自动进入私海；公海客户可【领取】到私海跟进；跟进记录与商机推进是客户经营的核心动作。', 'A lead can be [converted] into a customer and enters the private pool automatically; public-pool customers can be [claimed] into the private pool for follow-up. Follow-up records and opportunity progress are the core of customer management.'),
        ('即将超期 / 已超期 / 正常', 'Due Soon / Overdue / Normal'),
        ('归属销售', 'Owned by Sales'),
        ('归属', 'Ownership'),
        ('交易流水', 'Transaction')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'zh-CN', r.zh_text, 'page', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'en', r.en_text, 'page', 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 批次2：长尾字段标题整条覆盖（扫描驱动，按出现频次）============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('已收/已付', 'Received / Paid'),
        ('框架协议', 'Framework Agreement'),
        ('本月合同', 'Contracts This Month'),
        ('预计交货', 'Expected Delivery'),
        ('是否红字', 'Is Red-letter'),
        ('到货日期', 'Arrival Date'),
        ('本月退货', 'Returns This Month'),
        ('仪表盘名称', 'Dashboard Name'),
        ('共享角色', 'Shared Role'),
        ('最后交易日期', 'Last Transaction Date'),
        ('快递', 'Courier'),
        ('物流', 'Logistics'),
        ('空运', 'Air Freight'),
        ('海运', 'Sea Freight'),
        ('快捷新建客户', 'Quick Add Customer'),
        ('维度ID', 'Dimension ID'),
        ('提成基数', 'Commission Base'),
        ('提成金额', 'Commission Amount'),
        ('需求交期', 'Required Delivery Date'),
        ('委外发料单', 'Subcontract Material Issue'),
        ('加工费', 'Processing Fee'),
        ('财务', 'Finance'),
        ('月配额', 'Monthly Quota'),
        ('字段标识', 'Field Key'),
        ('小数位', 'Decimal Places'),
        ('组装单', 'Assembly Order'),
        ('拆卸单', 'Disassembly Order'),
        ('字典标签', 'Dictionary Label'),
        ('可见', 'Visible'),
        ('可编辑', 'Editable'),
        ('必填', 'Required'),
        ('日期格式', 'Date Format'),
        ('流水号位数', 'Serial No. Digits'),
        ('分隔符', 'Separator'),
        ('显示', 'Display'),
        ('库存流水', 'Stock Ledger'),
        ('MRP运算', 'MRP Calculation'),
        ('计算基础', 'Calculation Basis'),
        ('基础提成率', 'Base Commission Rate'),
        ('跟进预警', 'Follow-up Alert'),
        ('开单预警', 'Order Alert'),
        ('流失预警', 'Churn Alert'),
        ('物流查询功能', 'Logistics Tracking'),
        ('签名密钥', 'Signing Key'),
        ('获取授权信息', 'Get Authorization Info'),
        ('填入并测试', 'Fill In and Test'),
        ('纸张', 'Paper'),
        ('发货仓库', 'Shipping Warehouse'),
        ('签订日期', 'Signing Date'),
        ('完工数量', 'Completed Qty'),
        ('凭证号', 'Voucher No.'),
        ('折让金额', 'Allowance Amount'),
        ('数据范围', 'Data Scope'),
        ('当前流水', 'Current Serial'),
        ('物流配置', 'Logistics Config'),
        ('真实姓名', 'Real Name'),
        ('是否管理员', 'Is Admin'),
        ('模型名称', 'Model Name'),
        ('自有Key', 'Own Key'),
        ('已确认执行', 'Confirmed & Executed'),
        ('该件BOM编号', 'This Item BOM No.'),
        ('单件用量', 'Usage per Unit'),
        ('所在行', 'Line'),
        ('已存在物料', 'Existing Material'),
        ('缺失物料', 'Missing Material'),
        ('父件数量', 'Parent Item Qty'),
        ('创建物料', 'Create Material'),
        ('无匹配分类', 'No Matching Category'),
        ('流动', 'Float'),
        ('固定左侧', 'Fixed Left'),
        ('固定右侧', 'Fixed Right'),
        ('已超期', 'Overdue'),
        ('即将超期', 'Due Soon'),
        ('原数量', 'Original Qty'),
        ('已下单', 'Ordered'),
        ('已发料', 'Issued'),
        ('刷新当前页', 'Refresh Page'),
        ('获客任务', 'Acquisition Task'),
        ('企查查登录', 'Qichacha Login'),
        ('平台登录', 'Platform Login'),
        ('风鸟登录说明', 'Fengniao Login Guide'),
        ('天眼查登录说明', 'Tianyancha Login Guide'),
        ('企查查登录说明', 'Qichacha Login Guide'),
        ('截图', 'Screenshot'),
        ('关键词', 'Keyword'),
        ('最后执行时间', 'Last Run Time'),
        ('搜索关键词', 'Search Keyword'),
        ('行业筛选', 'Industry Filter'),
        ('地区筛选', 'Region Filter'),
        ('最大采集页数', 'Max Pages to Capture'),
        ('定时执行', 'Scheduled Run'),
        ('每月最后一天', 'Last Day of Month'),
        ('周日', 'Sunday'), ('周一', 'Monday'), ('周二', 'Tuesday'), ('周三', 'Wednesday'),
        ('周四', 'Thursday'), ('周五', 'Friday'), ('周六', 'Saturday'),
        ('周一至周五', 'Mon - Fri'), ('周六和周日', 'Sat & Sun'),
        ('企查查', 'Qichacha'), ('风鸟', 'Fengniao'), ('天眼查', 'Tianyancha'),
        ('工商系统', 'Business Registry'),
        ('待执行', 'Pending'), ('已暂停', 'Paused'),
        ('主机IP', 'Host IP'), ('工作目录', 'Working Directory'),
        ('后端jar路径', 'Backend JAR Path'), ('前端目录', 'Frontend Directory'),
        ('停止命令', 'Stop Command'), ('启动命令', 'Start Command'),
        ('健康检查URL', 'Health Check URL'), ('从版本库选择', 'Select from Repository'),
        ('更新包', 'Update Package'), ('一键打包', 'One-click Build'),
        ('打包模式', 'Build Mode'), ('打包人', 'Built By'), ('打包时间', 'Build Time'),
        ('标准版', 'Standard Edition'), ('旗舰版', 'Flagship Edition'),
        ('试用期', 'Trial Period'), ('30天试用', '30-day Trial'), ('15天试用', '15-day Trial'),
        ('7天试用', '7-day Trial'), ('初始密码', 'Initial Password'),
        ('客户续费', 'Customer Renewal'), ('续费时长', 'Renewal Duration'),
        ('续费后到期', 'Expires After Renewal'), ('运算符', 'Operator'),
        ('审批通过', 'Approved'), ('转交审批', 'Transfer Approval'),
        ('转交给', 'Transfer To'), ('转交说明', 'Transfer Note'),
        ('资产', 'Asset'), ('负债', 'Liability'), ('权益', 'Equity'), ('损益', 'P&L'),
        ('借', 'Debit'), ('贷', 'Credit'),
        ('是否末级科目', 'Is Leaf Account'), ('末级', 'Leaf'),
        ('组织与人员', 'Organization & Staff'), ('仓储档案', 'Warehouse Profile'),
        ('往来单位', 'Business Partner'), ('币种符号', 'Currency Symbol'),
        ('是否基本币种', 'Is Base Currency'), ('符号', 'Symbol'),
        ('采集结果须经人工审核方可入库：审核通过后将自动写入公海池可供销售领取，也可跳转查看对应线索；驳回或未通过的结果不会进入客户资源池。', 'Captured results require manual review before import: once approved they are written into the public pool for sales to claim, and you can jump to the related lead; rejected results never enter the customer pool.'),
        ('提示：登录成功后会自动保存Cookie，无需手动粘贴。此功能仅用于联调测试或扫码失败时的备选方案。', 'Note: the cookie is saved automatically after a successful login, no manual paste needed. This is a fallback for integration testing or when QR scanning fails.'),
        ('先建基础档案（单位/币种/部门），再建业务档案（物料/客户/供应商）——后者的录入表单会引用前者的数据作为下拉选项。', 'Create basic master data first (units / currencies / departments), then business master data (materials / customers / suppliers) — the latter reference the former as dropdown options.'),
        ('还没有客户服务器，点击右上角「新增服务器」添加第一台（需服务器已开启 OpenSSH，可在服务器上运行 D:\erp\deploy\server\INIT-AGENT.bat 一键开启）', 'No customer server yet. Click "Add Server" at the top right to add the first one (OpenSSH must be enabled; you can run D:\erp\deploy\server\INIT-AGENT.bat on the server to enable it).')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'zh-CN', r.zh_text, 'page', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'en', r.en_text, 'page', 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 批次3：主数据/CRM/财务/库存 长尾覆盖 ============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('战略客户', 'Strategic Customer'), ('默认发货仓', 'Default Shipping Warehouse'),
        ('职能部门', 'Functional Dept.'), ('业务部门', 'Business Dept.'), ('决策部门', 'Decision Dept.'),
        ('当前密码', 'Current Password'), ('新密码', 'New Password'), ('未知', 'Unknown'),
        ('手机号码', 'Mobile Number'), ('入职日期', 'Hire Date'), ('在职', 'Active'), ('离职', 'Resigned'),
        ('当前流水号', 'Current Serial No.'), ('下一编码', 'Next Code'), ('质检要求', 'QC Requirement'),
        ('保密物料', 'Confidential Material'), ('包材', 'Packaging Material'), ('辅助材料', 'Auxiliary Material'),
        ('自制件', 'Manufactured Item'), ('价格调整率', 'Price Adjustment Rate'), ('调整率%', 'Adjustment Rate %'),
        ('一般供应商', 'General Supplier'), ('战略供应商', 'Strategic Supplier'), ('交货天数', 'Delivery Days'),
        ('原材料仓', 'Raw Material Warehouse'), ('半成品仓', 'Semi-finished Warehouse'),
        ('废品仓', 'Scrap Warehouse'), ('辅助材料仓', 'Auxiliary Material Warehouse'),
        ('管理员', 'Administrator'), ('参与MRP', 'Include in MRP'), ('高严重待处理', 'High Severity Pending'),
        ('主联系人', 'Primary Contact'), ('是否主联系人', 'Is Primary Contact'),
        ('生日', 'Birthday'), ('兴趣爱好', 'Hobbies'), ('私海', 'Private Pool'),
        ('年营业额', 'Annual Revenue'), ('潜在价值', 'Potential Value'), ('公司网站', 'Website'),
        ('待回款金额', 'Pending Collection Amount'), ('CRM仪表盘', 'CRM Dashboard'),
        ('我的私海', 'My Private Pool'), ('公海池', 'Public Pool'), ('跟进超期预警', 'Follow-up Overdue Alert'),
        ('销售漏斗', 'Sales Funnel'), ('报表中心', 'Reports Center'), ('客户资源', 'Customer Resources'),
        ('销售推进', 'Sales Pipeline'), ('客户服务', 'Customer Service'), ('置顶', 'Pin to Top'),
        ('转化率', 'Conversion Rate'), ('未回款', 'Uncollected'), ('选中数量', 'Selected Qty'),
        ('未跟进天数', 'Days Without Follow-up'), ('未开单天数', 'Days Without Order'),
        ('预警天数', 'Alert Days'), ('进入公海日期', 'Date Entered Public Pool'),
        ('原归属人', 'Previous Owner'), ('今日回收数', 'Reclaimed Today'),
        ('超30天未领取', 'Unclaimed Over 30 Days'), ('重新分配销售员', 'Reassign Salesperson'),
        ('超过天数', 'Days Exceeded'), ('下一步计划', 'Next Step Plan'), ('线索跟进登记', 'Lead Follow-up'),
        ('客户行业', 'Customer Industry'), ('赢率(%)', 'Win Rate (%)'), ('更改销售阶段', 'Change Sales Stage'),
        ('赢率', 'Win Rate'), ('预计总金额', 'Expected Total Amount'),
        ('公司年度目标', 'Company Annual Target'), ('完成情况排名', 'Completion Ranking'),
        ('未分配', 'Unassigned'), ('名次', 'Rank'), ('新客户实际', 'New Customers (Actual)'),
        ('服务评分', 'Service Score'), ('交期评分', 'Delivery Score'), ('售后评分', 'After-sales Score'),
        ('表扬内容', 'Praise'), ('批评内容', 'Criticism'), ('改进计划', 'Improvement Plan'),
        ('新状态', 'New Status'), ('服务', 'Service'), ('交期', 'Delivery'), ('售后', 'After-sales'),
        ('待我审批', 'Pending My Approval'), ('我的草稿', 'My Drafts'),
        ('本月我的销售额', 'My Sales This Month'), ('本月我的采购额', 'My Purchases This Month'),
        ('现存量', 'On-hand Qty'), ('最长逾期天数', 'Max Overdue Days'),
        ('基本户', 'Basic Account'), ('一般户', 'General Account'), ('专用户', 'Special Account'),
        ('临时户', 'Temporary Account'), ('核算名称', 'Costing Name'), ('核算日期', 'Costing Date'),
        ('核算方法', 'Costing Method'), ('实际成本法', 'Actual Costing'), ('标准成本法', 'Standard Costing'),
        ('作业成本法', 'Activity-based Costing'), ('当期核算', 'Current Period Costing'),
        ('核算期间', 'Costing Period'), ('期初数量', 'Opening Qty'), ('期初金额', 'Opening Amount'),
        ('期末数量', 'Closing Qty'), ('期末金额', 'Closing Amount'), ('有结存', 'Has Balance'),
        ('负结存', 'Negative Balance'), ('零结存', 'Zero Balance'), ('物料种类', 'Material Categories'),
        ('期末金额合计', 'Total Closing Amount'), ('核算方法数', 'Costing Methods'),
        ('预计收款日期', 'Expected Receipt Date'), ('预计付款日期', 'Expected Payment Date'),
        ('收款记录', 'Receipt Records'), ('付款记录', 'Payment Records'), ('账龄区间', 'Aging Bucket'),
        ('收款登记', 'Receipt Entry'), ('付款登记', 'Payment Entry'), ('账龄分桶', 'Aging Buckets'),
        ('开票税额', 'Invoiced Tax'), ('已付', 'Paid'), ('净现金流', 'Net Cash Flow'),
        ('运营费用', 'Operating Expense'), ('净利', 'Net Profit'), ('净利率', 'Net Margin'),
        ('未开票合计', 'Total Uninvoiced'), ('回款合计', 'Total Collections'),
        ('当前未收余额', 'Current Outstanding Balance'), ('未开票额', 'Uninvoiced Amount'),
        ('蓝/红', 'Blue/Red'), ('去向', 'Destination'), ('资金单据', 'Cash Document'),
        ('财务汇总', 'Finance Summary'), ('分摊基数', 'Allocation Base'), ('分摊金额', 'Allocation Amount'),
        ('费用支出单导入', 'Expense Bill Import'), ('🚚 发货核销', '🚚 Delivery Write-off'),
        ('本月收款', 'Receipts This Month'), ('本月付款', 'Payments This Month'),
        ('个人供应商', 'Individual Supplier'), ('期初应付', 'Opening Payable'),
        ('期初预付', 'Opening Prepayment'), ('本期已付', 'Paid This Period'),
        ('本期预付', 'Prepayment This Period'), ('应付结余', 'AP Balance'),
        ('期初应收', 'Opening Receivable'), ('期初预收', 'Opening Prepayment Received'),
        ('本期预收', 'Prepayment Received This Period'), ('应收结余', 'AR Balance'),
        ('批次与流水', 'Batches & Ledger'), ('虚拟可用', 'Virtual Available'),
        ('最新核算成本', 'Latest Costed Cost'), ('有库存', 'In Stock'), ('零库存', 'Out of Stock'),
        ('库存不足', 'Insufficient Stock'), ('最后出库日', 'Last Delivery Date'),
        ('呆滞天数', 'Dormant Days'), ('呆滞标记', 'Dormant Flag'),
        ('低于安全库存', 'Below Safety Stock'), ('呆滞数', 'Dormant Qty'), ('呆滞值', 'Dormant Value'),
        ('贵重', 'Valuable'), ('危险品', 'Hazardous Goods'),
        ('当期核算将自动归集指定期间所有已生效的费用分摊、领料单、报工单数据，为每个有成本数据的物料自动计算成本。', 'Current-period costing automatically collects all approved expense allocations, material requisitions and work reports within the specified period, and calculates cost for every material that has cost data.'),
        ('财务单据状态体系不统一：收款单/付款单/运营费用使用中文状态（草稿/待确认/待审核/已确认/已取消），而销售预收款/采购预付款使用数字状态（0-4）。请以各单据实际状态为准。', 'Document status conventions differ: receipts, payment vouchers and operating expenses use Chinese statuses (Draft / Pending Confirm / Pending Approval / Confirmed / Cancelled), while sales prepayments and purchase prepayments use numeric statuses (0-4). Please refer to each document''s actual status.'),
        ('🔓 本单据已通过特殊审批，核销来源非系统默认路径。提交时将记录特殊审批日志。', '🔓 This document passed special approval; the write-off source is not the system default path. A special-approval log will be recorded on submit.'),
        ('🔒 本单据已添加核销明细，核销来源已锁定。如需更改，请先解锁清空。', '🔒 Write-off details have been added and the write-off source is locked. To change it, unlock and clear first.'),
        ('库存单据多数支持【下推】生成下游单据：生产工单可下推生产领料单；其他出库/领料出库后即时库存随之扣减。业务驱动的出入库（销售出库、采购入库、生产入库等）由对应业务模块自动更新库存。', 'Most inventory documents support [Push Down] to generate downstream documents: a work order can push down a material requisition; other issues and requisition issues reduce on-hand stock immediately. Business-driven movements (sales delivery, purchase receipt, production receipt, etc.) update stock from their own modules.')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'zh-CN', r.zh_text, 'page', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'en', r.en_text, 'page', 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 批次4：库存/生产/采购/销售/报表 长尾覆盖 ============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('本月领料', 'Material Requisition This Month'), ('动态盘点', 'Cycle Count'),
        ('全月盘点', 'Full-month Stocktake'), ('全年盘点', 'Annual Stocktake'), ('批号', 'Batch No.'),
        ('本月盘点单', 'Stocktake This Month'), ('交易日期', 'Transaction Date'),
        ('结存数量', 'Balance Qty'), ('委外发料', 'Subcontract Material Issue'),
        ('流水总数', 'Total Transactions'), ('同价调拨', 'Same-price Transfer'),
        ('异价调拨', 'Different-price Transfer'), ('本月调拨单', 'Transfers This Month'),
        ('普通仓', 'Normal Warehouse'), ('原料仓', 'Raw Material Warehouse'),
        ('当前状态', 'Current Status'), ('最新轨迹', 'Latest Tracking'), ('已揽收', 'Picked Up'),
        ('派送中', 'Out for Delivery'), ('已签收', 'Delivered'), ('异常', 'Exception'),
        ('色号', 'Color No.'), ('政府机构', 'Government Agency'), ('线上推广', 'Online Promotion'),
        ('线下展会', 'Offline Expo'), ('客户转介绍', 'Customer Referral'), ('市场活动', 'Marketing Event'),
        ('标准采购价', 'Standard Purchase Price'), ('标准售价', 'Standard Selling Price'),
        ('成本价', 'Cost Price'), ('运算方案名称', 'Scheme Name'), ('考虑BOM替代', 'Consider BOM Substitutes'),
        ('考虑采购申请', 'Consider Purchase Requisition'), ('展开所有层级', 'Expand All Levels'),
        ('生产周期', 'Production Cycle'), ('运算时间', 'Calculation Time'), ('涉及物料', 'Materials Involved'),
        ('调整方式', 'Adjustment Method'), ('调整值', 'Adjustment Value'),
        ('原净需求', 'Original Net Requirement'), ('调整后数量', 'Adjusted Qty'),
        ('运算配置', 'Calculation Config'), ('执行运算', 'Run Calculation'),
        ('下推执行', 'Push Down & Execute'), ('涉及物料数', 'Materials Involved'),
        ('可满足物料', 'Fulfillable Materials'), ('手工录入', 'Manual Entry'),
        ('使用设备', 'Equipment Used'), ('转入工序号', 'Next Operation No.'),
        ('返修数量', 'Rework Qty'), ('正常转移', 'Normal Transfer'), ('返工转移', 'Rework Transfer'),
        ('跳序转移', 'Skip-sequence Transfer'), ('转移单总数', 'Total Transfer Orders'),
        ('作废组装单', 'Void Assembly Order'), ('本月组装', 'Assembly This Month'),
        ('作废拆卸单', 'Void Disassembly Order'), ('本月拆卸', 'Disassembly This Month'),
        ('夜班', 'Night Shift'), ('今日产量', 'Output Today'), ('今日产值', 'Output Value Today'),
        ('整体效率', 'Overall Efficiency'), ('准时交付率', 'On-time Delivery Rate'),
        ('基础档案', 'Basic Master Data'), ('生产执行单据', 'Production Execution Docs'),
        ('组装与拆卸', 'Assembly & Disassembly'), ('延期天数', 'Delay Days'), ('总产量', 'Total Output'),
        ('良率', 'Yield Rate'), ('核算单号', 'Costing No.'), ('准时完成率', 'On-time Completion Rate'),
        ('总良率', 'Overall Yield'), ('可退数量', 'Returnable Qty'),
        ('即将到期(3天内)', 'Expiring within 3 days'), ('是否主工艺', 'Is Primary Routing'),
        ('主工艺路线', 'Primary Routing'), ('准备工时', 'Setup Hours'), ('移动工时', 'Move Hours'),
        ('需质检', 'Requires QC'), ('关键工序', 'Key Operation'),
        ('本月委外单', 'Subcontract Orders This Month'),
        ('委外发料清单列设置', 'Subcontract Issue Column Settings'),
        ('生产线', 'Production Line'), ('工作单元', 'Work Unit'), ('机器工时', 'Machine Hours'),
        ('件数', 'Pieces'), ('张三', 'Zhang San'), ('李四', 'Li Si'), ('王五', 'Wang Wu'),
        ('早班', 'Morning Shift'), ('中班', 'Middle Shift'), ('晚班', 'Evening Shift'),
        ('转移到下道', 'Transfer to Next'), ('汇报记录', 'Work Reports'),
        ('转移记录', 'Transfer Records'), ('质量情况', 'Quality Status'), ('返修数', 'Rework Qty'),
        ('完成进度', 'Completion Progress'), ('本月工单', 'Work Orders This Month'),
        ('待下达', 'To Release'), ('工作站总数', 'Total Workstations'),
        ('款到发货', 'Ship on Payment'), ('货到付款', 'Cash on Delivery'),
        ('送货单号', 'Delivery Note No.'), ('专票', 'Special VAT Invoice'),
        ('普票', 'General VAT Invoice'), ('发票代码', 'Invoice Code'), ('发票号码', 'Invoice No.'),
        ('勾稽状态', 'Matching Status'), ('税收分类编码', 'Tax Category Code'),
        ('本月发票', 'Invoices This Month'), ('未付款', 'Unpaid'), ('最高税率', 'Max Tax Rate'),
        ('从销售订单导入', 'Import from Sales Order'), ('本月采购', 'Purchases This Month'),
        ('准时率', 'On-time Rate'), ('研发采购', 'R&D Purchase'), ('制单日期', 'Created Date'),
        ('本月申请', 'Requisitions This Month'), ('质量退货', 'Quality Return'),
        ('错发退货', 'Wrong-shipment Return'), ('仪表盘编码', 'Dashboard Code'), ('卡片数', 'Cards'),
        ('期初应收余额', 'Opening AR Balance'), ('期末应收余额', 'Closing AR Balance'),
        ('图表类型', 'Chart Type'), ('主表', 'Main Table'), ('升序', 'Ascending'), ('降序', 'Descending'),
        ('试运行（前 20 条）', 'Dry Run (first 20)'), ('日期分组粒度', 'Date Grouping Granularity'),
        ('图形', 'Chart'), ('期初应付余额', 'Opening AP Balance'), ('期末应付余额', 'Closing AP Balance'),
        ('定金金额', 'Deposit Amount'), ('是否开票', 'Invoiced?'), ('是否代发', 'Dropship?'),
        ('拣货人', 'Picker'), ('开票类型', 'Invoice Type'), ('增值税专票', 'VAT Special Invoice'),
        ('增值税普票', 'VAT General Invoice'), ('电子发票', 'E-invoice'), ('项目号', 'Project No.'),
        ('运输费', 'Freight Fee'), ('物流监控看板', 'Logistics Tracking Board'),
        ('销售报表中心', 'Sales Reports Center'), ('增值税专用发票', 'VAT Special Invoice'),
        ('增值税普通发票', 'VAT General Invoice'), ('本月开票', 'Invoiced This Month'),
        ('开票总额', 'Total Invoiced'), ('回款率', 'Collection Rate'), ('本月订单', 'Orders This Month'),
        ('本月报价', 'Quotations This Month'), ('按订单额', 'By Order Amount'), ('按毛利', 'By Gross Profit'),
        ('生产工单是执行中枢：支持下推 生产领料/补料/退料单（库存模块）、工序汇报（报工）单、生产入库单与委外加工单；MRP 运算结果可下推 采购申请（外购件）与 生产工单（自制件）。', 'The work order is the execution hub: it can push down material requisition / supplement / return (inventory module), operation reporting, production receipt and subcontract orders; MRP results can push down purchase requisitions (bought-out parts) and work orders (in-house parts).'),
        ('注意：生产模块单据统计口径并不统一——生产工单为独立状态流（草稿→已下达→执行中→已完工→已关闭）；MRP 方案为 草稿→已生效→已执行；组装/拆卸为数字态（草稿→待审批→生效）；委外加工单为 8 态数字体系。详见各单据说明。', 'Note: production documents use different status conventions — work orders follow their own flow (Draft → Released → In Progress → Completed → Closed); MRP schemes are Draft → Approved → Executed; assembly/disassembly use numeric statuses (Draft → Pending Approval → Effective); subcontract orders use an 8-state numeric system. See each document help for details.'),
        ('多数单据支持【下推】生成下游单据：采购申请可下推合同 / 订单，合同可下推订单，订单可下推入库 / 发票，入库单可下推退货 / 发票，退货单可下推红字发票。详见各单据说明中的“单据下推”。', 'Most documents support [Push Down] to create downstream documents: a requisition can push down a contract / order, a contract can push down an order, an order can push down a receipt / invoice, a receipt can push down a return / invoice, and a return can push down a red-letter invoice. See "Document Push Down" in each document help.'),
        ('多数单据支持【下推】生成下游单据：报价单 / 合同可下推订单，订单可下推出库单 / 发票，出库单可下推退货 / 发票，退货单可下推红字发票。详见各单据说明中的“单据下推”。', 'Most documents support [Push Down] to create downstream documents: a quotation / contract can push down an order, an order can push down a delivery / invoice, a delivery can push down a return / invoice, and a return can push down a red-letter invoice. See "Document Push Down" in each document help.')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'zh-CN', r.zh_text, 'page', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'en', r.en_text, 'page', 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 批次5：收尾（目标/委外/配置/主题/字典项等） ============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('月度目标', 'Monthly Target'), ('应发提成', 'Commission Payable'),
        ('本年销售总额', 'Total Sales This Year'), ('本月订单数', 'Orders This Month'),
        ('本月毛利率', 'Gross Margin This Month'), ('预付比例%', 'Prepayment Ratio %'),
        ('改进产品', 'Improved Product'), ('来料打样', 'Incoming-material Sampling'),
        ('内部研发', 'Internal R&D'), ('原样', 'Original Sample'), ('确认样', 'Approval Sample'),
        ('大货样', 'Bulk Sample'), ('责任部门', 'Responsible Dept.'),
        ('责任工程师', 'Responsible Engineer'), ('基本用量', 'Base Usage'),
        ('已转BOM', 'Converted to BOM'), ('计划开工日期', 'Planned Start Date'),
        ('我方供料', 'Material Supplied by Us'), ('客供料', 'Customer-supplied Material'),
        ('不供料', 'No Material Supply'), ('本月发料', 'Material Issued This Month'),
        ('草稿发料', 'Draft Issue'), ('本月退料', 'Material Returns This Month'),
        ('本月补料', 'Material Supplements This Month'), ('数据政策', 'Data Policy'),
        ('数据用于训练', 'Data Used for Training'), ('工具权限', 'Tool Permissions'),
        ('生产环境部署建议', 'Production Deployment Guide'), ('文档标题', 'Document Title'),
        ('切片数', 'Chunks'), ('调用次数', 'Calls'), ('输入 Tokens', 'Input Tokens'),
        ('输出 Tokens', 'Output Tokens'), ('分区', 'Partition'), ('所属分区', 'Partition'),
        ('输入提示', 'Input Hint'), ('请购单', 'Purchase Requisition'),
        ('请购单明细', 'Requisition Details'), ('单行文本', 'Single-line Text'),
        ('多行文本', 'Multi-line Text'), ('数字', 'Number'), ('下拉选择', 'Dropdown'),
        ('开关(布尔)', 'Switch (Boolean)'), ('公式计算', 'Formula'), ('选项1', 'Option 1'),
        ('选项2', 'Option 2'), ('左括号', 'Left Bracket'), ('清空公式', 'Clear Formula'),
        ('复制权限到目标角色', 'Copy Permissions to Role'), ('系统自动通过', 'Auto-approved by System'),
        ('单据审批开关', 'Document Approval Switch'), ('我的待办', 'My To-dos'),
        ('审批通知', 'Approval Notification'), ('财务管理', 'Finance'),
        ('大于等于', 'Greater or Equal'), ('大于', 'Greater Than'), ('小于等于', 'Less or Equal'),
        ('小于', 'Less Than'), ('介于', 'Between'), ('等于(当天)', 'Equals (same day)'),
        ('晚于', 'Later Than'), ('不早于', 'Not Earlier Than'), ('早于', 'Earlier Than'),
        ('不晚于', 'Not Later Than'), ('生成示例', 'Generate Example'), ('阈值天数', 'Threshold Days'),
        ('预警等级', 'Alert Level'), ('提醒', 'Reminder'), ('警告', 'Warning'),
        ('提醒方式', 'Reminder Method'), ('压缩包', 'Archive'), ('结束时间', 'End Time'),
        ('部分失败', 'Partially Failed'), ('待审批通知', 'Pending Approval Notification'),
        ('已超期通知', 'Overdue Notification'), ('即将超期通知', 'Due-soon Notification'),
        ('草稿提醒', 'Draft Reminder'), ('登录页副标题', 'Login Subtitle'),
        ('客服电话', 'Support Phone'), ('底部版权信息', 'Footer Copyright'),
        ('登录', 'Sign In'), ('登出', 'Sign Out'), ('纵向', 'Vertical'), ('横向', 'Horizontal'),
        ('设为默认', 'Set as Default'), ('免费样品单', 'Free Sample Order'),
        ('销售预收单', 'Sales Prepayment'), ('采购预付单', 'Purchase Prepayment'),
        ('经营费用单', 'Operating Expense'), ('合计金额(折前)', 'Total Amount (Pre-discount)'),
        ('实收数量', 'Received Qty'), ('实盘数量', 'Counted Qty'), ('发料仓库', 'Issue Warehouse'),
        ('计划开工', 'Planned Start'), ('已领数量', 'Issued Qty'), ('本次收款', 'This Receipt'),
        ('实收金额', 'Received Amount'), ('实付金额', 'Paid Amount'),
        ('预收金额', 'Prepayment Received'), ('预付单号', 'Prepayment No.'),
        ('加工项目', 'Processing Item'), ('实际交货', 'Actual Delivery'),
        ('组装单号', 'Assembly Order No.'), ('组装日期', 'Assembly Date'),
        ('拆卸单号', 'Disassembly Order No.'), ('拆卸日期', 'Disassembly Date'),
        ('返工数量', 'Rework Qty'), ('本部门及子部门', 'My Dept. & Sub-depts'),
        ('本部门数据', 'My Department'), ('仅本人数据', 'My Data Only'),
        ('自动生成', 'Auto-generate'), ('无日期', 'No Date'), ('不重置', 'Do Not Reset'),
        ('测试生成编号', 'Test Generate No.'), ('员工权限补充', 'Employee Extra Permissions'),
        ('数据维护', 'Data Maintenance'), ('AI数据使用政策', 'AI Data Usage Policy'),
        ('AI知识库管理', 'AI Knowledge Base'), ('业务', 'Business'),
        ('CRM预警设置', 'CRM Alert Settings'), ('主题设置', 'Theme Settings'),
        ('消息通知设置', 'Notification Settings'), ('自动编号设置', 'Auto Numbering Settings'),
        ('业务参数', 'Business Parameters'), ('界面定制', 'UI Customization'),
        ('财务参数', 'Finance Parameters'), ('考勤参数', 'Attendance Parameters'),
        ('销售出库单（发货即应收）', 'Sales Delivery (AR on Shipment)'),
        ('销售发票（开票即应收）', 'Sales Invoice (AR on Invoice)'),
        ('审批流参数', 'Approval Flow Parameters'), ('主色', 'Primary Color'),
        ('成功色', 'Success Color'), ('警告色', 'Warning Color'), ('危险色', 'Danger Color'),
        ('信息色', 'Info Color'), ('页面背景色', 'Page Background'),
        ('侧边栏背景色', 'Sidebar Background'), ('侧边栏文字色', 'Sidebar Text'),
        ('侧边栏选中色', 'Sidebar Active'), ('顶栏背景色', 'Header Background'),
        ('顶栏文字色', 'Header Text'), ('内部用户', 'Internal User'), ('外部用户', 'External User'),
        ('单据链路：生产工单（已下达/执行中）→ 委外加工单；委外加工单（已生效）→ 委外发料单 & 委外入库单；委外发料单（已生效）→ 委外补料单 & 委外退料单。上下游单据可通过【下推】自动带入表头与明细。', 'Document chain: work order (Released / In Progress) → subcontract order; subcontract order (Approved) → subcontract material issue & subcontract receipt; material issue (Approved) → material supplement & material return. Upstream/downstream documents can be created by [Push Down], carrying header and details automatically.'),
        ('注意：委外模块状态为数字态体系且各单据不一致——委外加工单为 8 态（草稿0/待审核1/已生效2/已下达3/执行中4/已完工5/已结案6/已作废7）；发料/补料/退料单为 4 态（草稿0/待审核1/已生效2/已出库3，其中退料单的 3 为“已入库”补齐语义）；委外入库单为 3 态（草稿0/待审核1/已生效2）。详见各单据说明。', 'Note: the subcontract module uses numeric statuses that differ per document — subcontract order has 8 states (Draft 0 / Pending 1 / Approved 2 / Released 3 / In Progress 4 / Completed 5 / Settled 6 / Voided 7); issue, supplement and return documents have 4 states (Draft 0 / Pending 1 / Approved 2 / Issued 3, where 3 means "received" for returns); subcontract receipt has 3 states (Draft 0 / Pending 1 / Approved 2). See each document help.'),
        ('危险操作：以下功能会物理删除数据且不可恢复，请确认已做好数据备份后再执行。', 'Dangerous operation: the following actions physically delete data and cannot be undone. Make sure you have a backup before proceeding.'),
        ('说明：员工权限补充用于限制员工只能查看/操作指定的仓库、客户、供应商和货品类别。不选则表示不限制（可查看全部）。', 'Note: extra employee permissions restrict an employee to specific warehouses, customers, suppliers and goods categories. Leaving them unchecked means no restriction (full access).')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'zh-CN', r.zh_text, 'page', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'en', r.en_text, 'page', 'system', NOW());
        END IF;
    END LOOP;
END $$;

SELECT 'L2 field overrides rows = ' || count(*) FROM sys_i18n WHERE module = 'page' AND i18n_key LIKE 'field.%';
