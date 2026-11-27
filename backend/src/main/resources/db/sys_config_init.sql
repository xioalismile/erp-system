-- ============================================================
-- 系统参数配置表初始化数据（全量幂等版）
-- 铁律：所有 INSERT 一律使用 ON CONFLICT (config_key) DO NOTHING
--      已有的用户配置/自定义系统名称/Logo 绝对不会被覆盖
-- 共 77 个参数，14 个分组
-- ============================================================

-- ========== 1. basic 基础设置 (5个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('system.name', '智能ERP系统', '系统名称', 'basic', 'string', NULL, 1, '系统显示名称', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('system.logo', '/logo.png', '系统Logo', 'basic', 'string', NULL, 2, '系统Logo图片路径', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('system.default.lang', 'zh-CN', '默认语言', 'basic', 'select', '[{"label":"简体中文","value":"zh-CN"},{"label":"English","value":"en-US"}]', 3, '系统默认语言', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('system.default.timezone', 'Asia/Shanghai', '默认时区', 'basic', 'select', '[{"label":"中国标准时间(Asia/Shanghai)","value":"Asia/Shanghai"},{"label":"美国东部时间(America/New_York)","value":"America/New_York"},{"label":"伦敦时间(Europe/London)","value":"Europe/London"}]', 4, '系统默认时区', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('system.default.page.size', '20', '默认分页条数', 'basic', 'number', NULL, 5, '列表默认每页显示条数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 2. precision 精度设置 (6个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.quantity', '2', '数量小数位数', 'precision', 'number', NULL, 1, '数量字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.price', '4', '单价小数位数', 'precision', 'number', NULL, 2, '单价字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.amount', '2', '金额小数位数', 'precision', 'number', NULL, 3, '金额字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.rate', '6', '汇率小数位数', 'precision', 'number', NULL, 4, '汇率字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.percent', '2', '百分比小数位数', 'precision', 'number', NULL, 5, '百分比字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.rounding.mode', 'HALF_UP', '舍入方式', 'precision', 'select', '[{"label":"四舍五入(HALF_UP)","value":"HALF_UP"},{"label":"向下取整(FLOOR)","value":"FLOOR"},{"label":"向上取整(CEILING)","value":"CEILING"}]', 6, '数值计算舍入方式', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 3. font 字体设置 (5个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('font.family', 'Microsoft YaHei', '默认字体', 'font', 'select', '[{"label":"微软雅黑","value":"Microsoft YaHei"},{"label":"宋体","value":"SimSun"},{"label":"黑体","value":"SimHei"},{"label":"Arial","value":"Arial"}]', 1, '系统默认字体', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('font.size.normal', '14', '正文字号', 'font', 'number', NULL, 2, '正文默认字号(px)', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('font.size.title', '16', '标题字号', 'font', 'number', NULL, 3, '标题默认字号(px)', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('font.print.size', '12', '打印字号', 'font', 'number', NULL, 4, '单据打印默认字号(px)', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('font.line.height', '1.5', '行高比例', 'font', 'number', NULL, 5, '文字行高比例', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 4. approval 审批设置 (6个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('approval.enabled', 'true', '启用审批流程', 'approval', 'boolean', NULL, 1, '是否启用单据审批流程', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('approval.purchase.auto.submit', 'false', '采购单自动提交审批', 'approval', 'boolean', NULL, 2, '采购单保存后自动提交审批', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('approval.sale.auto.submit', 'false', '销售单自动提交审批', 'approval', 'boolean', NULL, 3, '销售单保存后自动提交审批', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('approval.max.level', '5', '最大审批级数', 'approval', 'number', NULL, 4, '审批流程最大级数限制', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('approval.timeout.hours', '48', '审批超时时间(小时)', 'approval', 'number', NULL, 5, '审批超时自动提醒时间', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('approval.notify.type', 'system', '审批通知方式', 'approval', 'select', '[{"label":"系统消息","value":"system"},{"label":"邮件通知","value":"email"},{"label":"系统+邮件","value":"all"}]', 6, '审批消息通知方式', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 5. inventory 库存设置 (6个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inventory.negative.allowed', 'false', '允许负库存', 'inventory', 'boolean', NULL, 1, '是否允许库存数量为负', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inventory.cost.method', 'weighted_avg', '存货计价方法', 'inventory', 'select', '[{"label":"加权平均法","value":"weighted_avg"},{"label":"先进先出法","value":"fifo"},{"label":"个别计价法","value":"specific"},{"label":"移动加权平均法","value":"moving_avg"}]', 2, '库存成本核算方法', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inventory.default.warehouse', '1', '默认仓库', 'inventory', 'select', NULL, 3, '单据默认仓库ID', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inventory.low.stock.warn', 'true', '库存不足预警', 'inventory', 'boolean', NULL, 4, '低于安全库存时是否预警', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inventory.overstock.warn', 'true', '库存积压预警', 'inventory', 'boolean', NULL, 5, '高于最高库存时是否预警', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inventory.check.cycle.days', '30', '盘点周期(天)', 'inventory', 'number', NULL, 6, '自动盘点建议周期', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- 发货库存校验口径（新增参数，用户未设置时默认为 actual）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.stock.check.scope', 'actual', '发货库存校验口径', 'inventory', 'select',
        '[{"label":"实际库存","value":"actual"},{"label":"预计可用量","value":"virtual"}]',
        7, '销售出库校验口径：actual=仅校验实际可用库存；virtual=再扣减已生效未结案销售订单的未发货占用（界面显示为"预计可用量"）', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 6. purchase 采购设置 (6个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.prefix', 'PO', '采购单号前缀', 'purchase', 'string', NULL, 1, '采购订单编号前缀', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.price.tolerance', '5', '价格容差(%)', 'purchase', 'number', NULL, 2, '采购价格超出基准价比例预警', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.delivery.default.days', '7', '默认交货天数', 'purchase', 'number', NULL, 3, '采购单默认交货周期(天)', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.return.days', '30', '退货期限(天)', 'purchase', 'number', NULL, 4, '采购退货有效期限', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.auto.generate.po', 'false', '自动生成采购单', 'purchase', 'boolean', NULL, 5, '库存不足时自动生成采购建议', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.supplier.credit.check', 'true', '供应商信用检查', 'purchase', 'boolean', NULL, 6, '下单前检查供应商信用额度', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 7. sale 销售设置 (8个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.order.prefix', 'SO', '销售单号前缀', 'sale', 'string', NULL, 1, '销售订单编号前缀', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.price.lowest.check', 'true', '最低售价检查', 'sale', 'boolean', NULL, 2, '低于最低售价时是否拦截', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.price.strategy.enabled', 'true', '价格策略启用', 'sale', 'boolean', NULL, 3, '是否启用销售价格策略匹配', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.price.fetch.priority', 'strategy,level,material', '取价优先级', 'sale', 'select', '价格策略优先=strategy,level,material|价格等级优先=level,strategy,material|物料默认价优先=material,strategy,level', 4, '销售取价方式优先级，逗号分隔', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.delivery.default.days', '3', '默认交货天数', 'sale', 'number', NULL, 5, '销售单默认交货周期(天)', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.return.days', '7', '退货期限(天)', 'sale', 'number', NULL, 6, '销售退货有效期限', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.credit.check', 'true', '客户信用检查', 'sale', 'boolean', NULL, 7, '下单前检查客户信用额度', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.discount.max', '30', '最大折扣比例(%)', 'sale', 'number', NULL, 8, '销售单据最大允许折扣比例', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 8. produce 生产设置（只保留来源单数量控制） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('produce.over.qty', '不控制', '超过来源单数量', 'produce', 'select',
        '[{"label":"不控制","value":"不控制"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]',
        1, '生产单据转入来源单时，本次数量超过可转数量的处理策略', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 9. subcontract 委外设置 (5个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.order.prefix', 'SC', '委外单号前缀', 'subcontract', 'string', NULL, 1, '委外订单编号前缀', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.material.return', 'true', '剩余物料退回', 'subcontract', 'boolean', NULL, 2, '委外加工完成后剩余物料是否退回', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.quality.check', 'true', '委外品质检', 'subcontract', 'boolean', NULL, 3, '委外加工品入库前是否质检', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.default.days', '10', '默认委外周期(天)', 'subcontract', 'number', NULL, 4, '委外加工默认周期天数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.supplier.assess', 'true', '供应商评估', 'subcontract', 'boolean', NULL, 5, '是否启用委外供应商评估', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 10. attendance 考勤设置 (5个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('attendance.work.start', '09:00', '上班时间', 'attendance', 'string', NULL, 1, '标准上班时间', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('attendance.work.end', '18:00', '下班时间', 'attendance', 'string', NULL, 2, '标准下班时间', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('attendance.late.grace', '10', '迟到宽限(分钟)', 'attendance', 'number', NULL, 3, '迟到宽限分钟数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('attendance.early.leave.grace', '10', '早退宽限(分钟)', 'attendance', 'number', NULL, 4, '早退宽限分钟数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('attendance.work.days', '5', '每周工作天数', 'attendance', 'select', '[{"label":"5天","value":"5"},{"label":"5.5天","value":"5.5"},{"label":"6天","value":"6"}]', 5, '每周标准工作天数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 11. ai AI助手权限设置 (5个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.write.enabled', 'true', 'AI写操作总开关', 'ai', 'boolean', NULL, 1, '是否允许AI助手执行写操作（创建/修改/删除单据等）。关闭后AI只能查询数据，所有写操作工具对AI不可见。子开关仅在此开关开启时生效', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.image_order.enabled', 'false', '图片下单', 'ai', 'boolean', NULL, 2, '允许AI通过识别商品图片自动创建销售订单草稿。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.bom_import.enabled', 'false', 'BOM文件导入', 'ai', 'boolean', NULL, 3, '允许AI导入BOM文件，自动识别字段映射并转换格式后导入系统。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.image_bom.enabled', 'false', '图片识别BOM', 'ai', 'boolean', NULL, 4, '允许AI通过识别BOM图片/产品图纸自动解析父子件结构并创建BOM。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.crud.enabled', 'true', '通用增删改查', 'ai', 'boolean', NULL, 5, '允许AI直接创建/修改/删除所有业务实体数据（高风险）。建议仅在受信任环境开启。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.setting.enabled', 'true', '系统设置（设置中心）', 'ai', 'boolean', NULL, 6, '允许AI操作设置中心能力（自定义字段/系统参数/语言/编号/审批/打印/价格策略等）。实际可用范围还取决于角色权限：用户须拥有设置中心对应权限。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.bill_action.enabled', 'true', '单据流程动作', 'ai', 'boolean', NULL, 7, '允许AI对业务单据执行提交/审核/驳回/重置草稿/关闭/结案/作废/删除等流程动作。实际可用范围取决于角色权限。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.bill.permission.mode', 'menu', '单据动作权限判定模式', 'ai', 'string', NULL, 8, 'menu=按菜单权限(与界面一致，默认)；action=按角色按钮权限。切到 action 后，若用户一条按钮权限都没有会自动回退 menu 模式，避免误伤', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.bill_item.enabled', 'true', '单据明细行编辑', 'ai', 'boolean', NULL, 9, '允许AI修改单据明细行（如单价/数量/税率/折扣率），金额与合计由系统自动重算。实际可用范围取决于角色权限。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.bill_batch.enabled', 'false', '单据批量操作', 'ai', 'boolean', NULL, 10, '允许AI对多张同类业务单据批量执行提交流程动作（批量提交/批量审核/批量删除/批量重置草稿），单次最多50张，逐张执行、逐张返回结果。实际可用范围取决于角色权限。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.work_operation.enabled', 'false', '工序操作', 'ai', 'boolean', NULL, 11, '允许AI对生产工单的工序执行「工序操作面板」的7个操作（状态流转/返工/报废/一键推补料/一键推入库/录入质检/发通知）。实际可用范围取决于角色权限：用户须拥有「生产工单」菜单权限。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.import.enabled', 'true', '数据导入', 'ai', 'boolean', NULL, 9, '允许AI读取用户上传的Excel/CSV并导入系统（客户/供应商/物料/销售订单/采购订单等55类模板）。实际可用范围取决于角色权限。物料导入必须由用户选择冲突策略。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.platform_query.enabled', 'false', '平台级数据查询', 'ai', 'boolean', NULL, 12, '允许AI查询平台级数据（租户清单/部署版本）。这两类数据不在租户库（租户在平台库、部署版本在部署控制台库），不能走通用查询。仅平台管理员（SUPER_ADMIN/ADMIN）可用；默认关闭', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.query.board.enabled', 'true', '查询-看板', 'ai', 'boolean', NULL, 13, '允许AI查询财务/销售/仓储/经营看板指标、多来源公式试算与明细下钻。关闭后该类工具对AI不可见', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ai.tool.board_save.enabled', 'false', 'AI保存看板公式卡', 'ai', 'boolean', NULL, 14, '允许AI把多来源公式卡保存到看板，只影响当前登录人的看板配置，执行前会弹确认卡。需先开启总开关', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 12. crm CRM设置 + 预警设置 (15个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.pool.daily.claim.limit', '5', '每人每日公海领取上限', 'crm', 'number', NULL, 1, '销售员每天最多可从公海池领取的客户数量，0表示不限制。防止恶意囤积客户资源', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.pool.recycle.enabled', 'true', '启用公海自动回收', 'crm', 'boolean', NULL, 2, '启用后系统每日凌晨1:00自动将长期未跟进/未开单的私海客户回收到公海', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.pool.recycle.no.follow.days', '30', '未跟进自动回收天数', 'crm', 'number', NULL, 3, '超过该天数未跟进的私海客户，自动回收到公海池', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.pool.recycle.no.order.days', '90', '未开单自动回收天数', 'crm', 'number', NULL, 4, '超过该天数未开单的私海客户，自动回收到公海池', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.pool.recycle.pre.warn.days', '3', '回收前预警提醒天数', 'crm', 'number', NULL, 5, '回收前N天向责任销售员发送预警提醒，0表示不提醒', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.follow.enabled', 'true', '启用跟进预警', 'crm', 'boolean', NULL, 10, '启用后超过阈值天数未跟进的客户将进入预警列表', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.follow.days', '7', '跟进预警阈值天数', 'crm', 'number', NULL, 11, '超过该天数未跟进的客户触发跟进预警', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.order.enabled', 'true', '启用开单预警', 'crm', 'boolean', NULL, 12, '启用后超过阈值天数未开单的客户将进入预警列表', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.order.days', '30', '开单预警阈值天数', 'crm', 'number', NULL, 13, '超过该天数未开单的客户触发开单预警', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.loss.enabled', 'true', '启用流失预警', 'crm', 'boolean', NULL, 14, '启用后超过阈值天数的高价值客户将进入流失预警列表', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.loss.days', '60', '流失预警阈值天数', 'crm', 'number', NULL, 15, '超过该天数的客户触发流失预警', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.remind.enabled', 'true', '启用一键提醒', 'crm', 'boolean', NULL, 20, '启用后预警列表中的提醒按钮会真实发送消息，否则仅记录日志', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.remind.dedup.days', '3', '提醒去重周期', 'crm', 'number', NULL, 21, '同一客户N天内不重复发送提醒，避免骚扰', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('crm.warning.auto.reassign.days', '90', '自动建议重新分配阈值', 'crm', 'number', NULL, 22, '超过该天数的预警客户自动建议重新分配，0表示不自动建议', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- 客户/供应商/物料名称查重参数
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.customer.duplicate.check',   'true', '客户名称查重',   'sys_param', 'boolean', 80, '保存时校验客户名称是否重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.supplier.duplicate.check',   'true', '供应商名称查重', 'sys_param', 'boolean', 81, '保存时校验供应商名称是否重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.material.duplicate.check',   'true', '物料名称查重',   'sys_param', 'boolean', 82, '保存时校验物料名称是否重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 13. ui 主题设置 (12个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.preset', 'tech-blue', '预设主题', 'ui', 'string', '[{"label":"科技蓝","value":"tech-blue"},{"label":"商务深蓝","value":"business-deep"},{"label":"活力橙","value":"vitality-orange"},{"label":"优雅紫","value":"elegant-purple"},{"label":"自然绿","value":"natural-green"},{"label":"极简灰","value":"minimal-gray"},{"label":"暗黑模式","value":"dark-mode"}]', 1, '当前应用的预设主题 key', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.primaryColor', '#409EFF', '主色', 'ui', 'string', NULL, 2, '系统主色，用于按钮、链接、激活态等', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.successColor', '#67C23A', '成功色', 'ui', 'string', NULL, 3, '成功状态标签/按钮颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.warningColor', '#E6A23C', '警告色', 'ui', 'string', NULL, 4, '警告状态标签/按钮颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.dangerColor', '#F56C6C', '危险色', 'ui', 'string', NULL, 5, '危险/错误状态标签/按钮颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.infoColor', '#909399', '信息色', 'ui', 'string', NULL, 6, '信息提示/中性状态颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.bgPage', '#F5F7FA', '页面背景色', 'ui', 'string', NULL, 7, '主内容区的页面背景颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.sidebarBg', '#001529', '侧边栏背景色', 'ui', 'string', NULL, 8, '左侧导航栏的背景颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.sidebarText', '#ffffff', '侧边栏文字色', 'ui', 'string', NULL, 9, '侧边栏菜单的文字颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.sidebarActiveBg', '#409EFF', '侧边栏选中色', 'ui', 'string', NULL, 10, '侧边栏当前选中项的高亮背景色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.headerBg', '#ffffff', '顶栏背景色', 'ui', 'string', NULL, 11, '顶部导航栏的背景颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('ui.theme.headerText', '#303133', '顶栏文字色', 'ui', 'string', NULL, 12, '顶部导航栏的文字颜色', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 14. notification 通知规则 (8个) ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.enabled', 'true', '启用消息通知', 'notification', 'boolean', NULL, 1, '总开关，关闭后铃铛不再显示未读数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.type.approval.enabled', 'true', '启用待审批通知', 'notification', 'boolean', NULL, 2, '待我审批的单据触发通知', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.type.overdue.enabled', 'true', '启用已超期通知', 'notification', 'boolean', NULL, 3, '已超期单据触发通知', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.type.soon_overdue.enabled', 'true', '启用即将超期通知', 'notification', 'boolean', NULL, 4, '即将超期单据触发通知', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.type.soon_overdue.days', '7', '即将超期阈值(天)', 'notification', 'number', NULL, 5, '剩余天数小于等于该值时视为即将超期', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.type.draft.enabled', 'true', '启用草稿提醒通知', 'notification', 'boolean', NULL, 6, '我创建的草稿单据触发提醒', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.type.ar_ap.enabled', 'true', '启用应收应付跟进通知', 'notification', 'boolean', NULL, 7, '已生效未结清且过到期日的应收应付触发跟进通知', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('notification.scan.cron', '0 */2 * * * ?', '扫描频率(cron)', 'notification', 'string', NULL, 8, '系统通知扫描生成任务的 cron 表达式（默认每2分钟）', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;
