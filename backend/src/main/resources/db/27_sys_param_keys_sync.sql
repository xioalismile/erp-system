-- ============================================================
-- 27_sys_param_keys_sync.sql
-- 设置中心配置项key与后端业务逻辑同步补丁
-- 修复前端KgwSysParam.vue保存的key与后端读取的key不一致问题
-- ============================================================

-- 先清理旧的 sys_param_* 前缀记录（前端已不再使用前缀）
DELETE FROM sys_config WHERE config_group = 'sys_param' AND config_key LIKE 'sys_param_%';

-- ========== 系统参数（system tab，27个） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.ip.limit.enabled', 'false', '启用IP限制', 'sys_param', 'boolean', 1, '开启后仅允许指定IP段登录', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.ip.limit.segment', '', 'IP段', 'sys_param', 'string', 2, '允许登录的IP段', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.tax.enabled', 'true', '启用含税业务', 'sys_param', 'boolean', 3, '开启后支持税率计算', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.tax.rate', '13', '税率', 'sys_param', 'number', 4, '默认税率(%)', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.tax.method', '不含税价', '默认计税方式', 'sys_param', 'string', 5, '不含税价/含税价/不计税', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.foreign.currency.enabled', 'false', '启用外币业务', 'sys_param', 'boolean', 6, '开启后支持多币种', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.base.currency', '人民币', '本位币', 'sys_param', 'string', 7, '系统本位币', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.print.when.effective', 'false', '单据在生效状态下打印', 'sys_param', 'boolean', 8, '开启后仅生效单据可打印', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.customer.duplicate.check', 'true', '客户名称查重', 'sys_param', 'boolean', 9, '新增客户时检查名称重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.supplier.duplicate.check', 'true', '供应商名称查重', 'sys_param', 'boolean', 10, '新增供应商时检查名称重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.material.duplicate.check', 'true', '货品名称查重', 'sys_param', 'boolean', 11, '新增货品时检查名称重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.auto.customer.no', 'true', '自动生成客户编号', 'sys_param', 'boolean', 12, '新增客户时自动生成编号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.auto.supplier.no', 'true', '自动生成供应商编号', 'sys_param', 'boolean', 13, '新增供应商时自动生成编号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.float.unit.enabled', 'false', '启用浮动单位', 'sys_param', 'boolean', 14, '支持浮动单位换算', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.lock.bill.date', 'false', '不允许修改单据日期', 'sys_param', 'boolean', 15, '已生效单据日期不可改', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.discount.calc.method', 'amount', '折扣额推算方式', 'sys_param', 'string', 16, 'amount以折扣额推算率/rate以折扣率推算额', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.loss.calc.method', 'rate', '损耗量推算方式', 'sys_param', 'string', 17, 'rate以损耗率推算量/amount以损耗量推算率', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.unified.column.enabled', 'false', '统一列设置', 'sys_param', 'boolean', 18, '指定员工使用统一列设置', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.show.spec.in.dropdown', 'true', '货品选择下拉列表中显示规格', 'sys_param', 'boolean', 19, '下拉列表显示规格信息', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.spec.duplicate.check', 'false', '货品规格查重', 'sys_param', 'boolean', 20, '同一物料规格不可重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.bill.no.duplicate.check', 'true', '单据号码查重', 'sys_param', 'boolean', 21, '单据号码不可重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.phone.duplicate.check', 'false', '客商手机号查重', 'sys_param', 'boolean', 22, '客商手机号不可重复', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.trim.zero', 'false', '省略小数点后多余的0', 'sys_param', 'boolean', 23, '1.50显示为1.5', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sys.display.specs', '["规格"]', '显示规格设置', 'sys_param', 'string', 24, '系统默认显示的规格维度', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

-- ========== 采购参数（purchase tab，18个） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.price.over.max', '警告提示', '进价高于最高进价', 'sys_param', 'string', 30, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('kgw_purchase_price_strategy', 'false', '采购业务取价方式', 'sys_param', 'boolean', 31, '是否启用采购取价策略', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.price.manual.modify', '允许', '手工修改采购价', 'sys_param', 'string', 32, '允许/不允许', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.invoice.default', '否', '默认需收票', 'sys_param', 'string', 33, '是/否', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.request.price.strategy', '取货品资料中采购价', '采购申请单取价方式', 'sys_param', 'string', 34, '采购申请单单价来源', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.require.source', 'false', '采购订单有来源单号才能保存', 'sys_param', 'boolean', 35, '开启后必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.allow.add.material', 'true', '允许增加来源单货品', 'sys_param', 'boolean', 36, '允许在订单中增加来源单没有的货品', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.skip.request.price', 'true', '不取采购申请单价', 'sys_param', 'boolean', 37, '采购订单不取申请单价格', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.over.qty', '警告提示', '超过来源单数量下单', 'sys_param', 'string', 38, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.allow.zero.price', 'true', '允许单价为0', 'sys_param', 'boolean', 39, '允许采购订单单价为0', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.over.max.stock', '不检测', '超过最大库存', 'sys_param', 'string', 40, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.inbound.require.source', 'false', '采购入库有来源单号才能保存', 'sys_param', 'boolean', 41, '开启后入库必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.inbound.allow.add.material', 'true', '采购入库允许增加来源单货品', 'sys_param', 'boolean', 42, '允许入库中增加来源单没有的货品', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.inbound.over.qty', '警告提示', '超过来源单数量入库', 'sys_param', 'string', 43, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.inbound.allow.zero.price', 'true', '采购入库允许单价为0', 'sys_param', 'boolean', 44, '允许入库单价为0', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.inbound.over.max.stock', '不允许', '采购入库超过最大库存', 'sys_param', 'string', 45, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.return.require.source', 'false', '采购退货有来源单号才能保存', 'sys_param', 'boolean', 46, '开启后退货必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.return.over.qty', '不检测', '超过来源单数量退货', 'sys_param', 'string', 47, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

-- ========== 销售参数（sale tab，17个） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.price.lowest.check', 'true', '售价低于最低售价检查', 'sys_param', 'boolean', 50, '开启后低于最低售价禁止保存', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('kgw_sale_price_strategy', 'false', '销售业务取价方式', 'sys_param', 'boolean', 51, '是否启用销售取价策略', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.price.manual.modify', '允许', '手工修改销售价', 'sys_param', 'string', 52, '允许/不允许', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.invoice.default', '是', '默认需开票', 'sys_param', 'string', 53, '是/否', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.quote.price.strategy', '不启用', '销售报价单取价方式', 'sys_param', 'string', 54, '报价单单价来源', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.order.require.source', 'false', '销售订单有来源单号才能保存', 'sys_param', 'boolean', 55, '开启后必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.order.allow.add.material', 'false', '销售订单允许增加来源单货品', 'sys_param', 'boolean', 56, '允许在订单中增加来源单没有的货品', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.order.over.qty', '预警提示', '超过来源单数量下单', 'sys_param', 'string', 57, '不控制/预警提示/不允许', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.order.allow.zero.price', 'false', '销售订单允许单价为0', 'sys_param', 'boolean', 58, '允许销售订单单价为0', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.order.below.min.stock', '警告提示', '销售订单低于最小库存', 'sys_param', 'string', 59, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.outbound.require.source', 'false', '销售出库有来源单号才能保存', 'sys_param', 'boolean', 60, '开启后出库必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.outbound.allow.add.material', 'false', '销售出库允许增加来源单货品', 'sys_param', 'boolean', 61, '允许出库中增加来源单没有的货品', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.outbound.over.qty', '预警提示', '超过来源单数量出库', 'sys_param', 'string', 62, '不控制/预警提示/不允许', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.outbound.allow.zero.price', 'false', '销售出库允许单价为0', 'sys_param', 'boolean', 63, '允许出库单价为0', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.outbound.below.min.stock', '警告提示', '销售出库低于最小库存', 'sys_param', 'string', 64, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.return.require.source', 'false', '销售退货有来源单号才能保存', 'sys_param', 'boolean', 65, '开启后退货必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.return.over.qty', '预警提示', '超过来源单数量退货', 'sys_param', 'string', 66, '不控制/预警提示/不允许', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

-- ========== 库存参数（inventory tab，12个） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.sale.order.check.stock', 'false', '销售订单检测库存', 'sys_param', 'boolean', 70, '销售订单保存时检查库存', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

-- inventory.negative.allowed 已在 sys_config_init.sql 中初始化，这里只更新 group 和 remark
UPDATE sys_config SET config_group = 'sys_param', config_name = '允许负库存出库', remark = '允许出库数量超过现有库存', sort_order = 71
WHERE config_key = 'inventory.negative.allowed';

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.check.stock.when.approving', 'false', '审批中单据检测库存', 'sys_param', 'boolean', 72, '审批中单据纳入库存检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.check.stock.when.draft', 'false', '草稿单据检测库存', 'sys_param', 'boolean', 73, '草稿单据纳入库存检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.location.enabled', 'false', '启用储位管理', 'sys_param', 'boolean', 75, '精细化管理仓库仓位', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.allow.expired.batch.outbound', 'false', '允许过期批次货品出库', 'sys_param', 'boolean', 76, '否则禁止过期批次出库', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.allow.batch.repeat.inbound', 'false', '允许批次货品重复入库', 'sys_param', 'boolean', 77, '否则同一批次只能入库一次', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.sync.default.bom.to.order', 'false', '同步默认BOM到无BOM的订单', 'sys_param', 'boolean', 78, '自动同步默认BOM', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.sync.material.price.to.bom', 'false', '同步货品的价格到BOM', 'sys_param', 'boolean', 79, '同步物料价格到BOM', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.sync.material.no.to.bom', 'false', '同步货品编号到BOM单号', 'sys_param', 'boolean', 80, '同步物料编号到BOM单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.warning.reference', 'actual', '库存预警参考值', 'sys_param', 'string', 81, 'actual实际库存/available可用库存', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

-- ========== 委外参数（subcontract tab，16个） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.price.strategy', '最新委外入库价', '委外业务取价方式', 'sys_param', 'string', 90, '委外单单价来源', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.price.manual.modify', '允许', '手工修改加工价', 'sys_param', 'string', 91, '允许/不允许', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.invoice.default', '否', '默认需收票', 'sys_param', 'string', 92, '是/否', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.order.require.source', 'false', '委外订单有来源单号才能保存', 'sys_param', 'boolean', 93, '开启后必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.order.allow.add.material', 'true', '委外订单允许增加来源单货品', 'sys_param', 'boolean', 94, '允许增加来源单没有的货品', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.order.over.qty', '警告提示', '超过来源单数量下单', 'sys_param', 'string', 95, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.order.allow.zero.price', 'true', '委外订单允许单价为0', 'sys_param', 'boolean', 96, '允许委外订单单价为0', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.issue.require.source', 'false', '委外发料有来源单号才能保存', 'sys_param', 'boolean', 97, '开启后发料必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.issue.allow.add.material', 'true', '委外发料允许增加来源单货品', 'sys_param', 'boolean', 98, '允许发料中增加来源单没有的货品', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.issue.over.qty', '警告提示', '超过来源单数量发料', 'sys_param', 'string', 99, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.inbound.require.source', 'false', '委外入库有来源单号才能保存', 'sys_param', 'boolean', 100, '开启后入库必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.inbound.allow.add.material', 'true', '委外入库允许增加来源单货品', 'sys_param', 'boolean', 101, '允许入库中增加来源单没有的货品', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.inbound.over.qty', '警告提示', '超过来源单数量入库', 'sys_param', 'string', 102, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.inbound.allow.zero.price', 'true', '委外入库允许单价为0', 'sys_param', 'boolean', 103, '允许入库单价为0', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.return.require.source', 'false', '委外退料有来源单号才能保存', 'sys_param', 'boolean', 104, '开启后退料必须填写来源单号', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('subcontract.return.over.qty', '不检测', '超过来源单数量退料', 'sys_param', 'string', 105, '警告提示/不允许/不检测', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, config_name = EXCLUDED.config_name, remark = EXCLUDED.remark;

-- ========== 同步已有的 precision.* 和 inventory.* key 到 sys_param group ==========
UPDATE sys_config SET config_group = 'sys_param', config_name = '数量类型小数位数', remark = '数量字段保留小数位数', sort_order = 25
WHERE config_key = 'precision.quantity' AND config_group <> 'sys_param';

UPDATE sys_config SET config_group = 'sys_param', config_name = '金额类型小数位数', remark = '金额字段保留小数位数', sort_order = 26
WHERE config_key = 'precision.amount' AND config_group <> 'sys_param';

-- 验证：查询所有 sys_param 组的配置项数量（应该 >= 90）
SELECT '验证: sys_param组配置项总数' AS info, COUNT(*) AS total FROM sys_config WHERE config_group = 'sys_param';
