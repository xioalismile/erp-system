-- ============================================================
-- 43_sys_config_dynamic_settings.sql
-- 设置中心数据驱动化：新增 config_section 字段 + 重新分配 group + 补全 type/options
-- 目标：KgwSysParam 前端不再硬编码参数项，改为按 group 动态加载、按 section 分组、按 type 渲染控件
-- ============================================================

-- ========== Step1: 新增 config_section 字段（小节标题，如"公用参数""销售订单"） ==========
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_section VARCHAR(100);
COMMENT ON COLUMN sys_config.config_section IS '参数所属小节标题（设置中心渲染分组用）';

-- ========== Step2: 按 key 前缀重新分配 config_group（从 sys_param 拆分到各自领域 group） ==========
-- 系统参数：sys.* / precision.* / kgw_warehouse_required → 留在 sys_param
UPDATE sys_config SET config_group='sys_param' WHERE config_key LIKE 'sys.%' OR config_key LIKE 'precision.%' OR config_key='kgw_warehouse_required';

-- 采购参数：purchase.* / kgw_purchase_* → purchase
UPDATE sys_config SET config_group='purchase' WHERE config_key LIKE 'purchase.%' OR config_key LIKE 'kgw_purchase_%';

-- 销售参数：sale.* / kgw_sale_* → sale
UPDATE sys_config SET config_group='sale' WHERE config_key LIKE 'sale.%' OR config_key LIKE 'kgw_sale_%';

-- 财务参数：收款发货管控类参数（sale.delivery.payment.* / sale.receipt.receive.mode）→ finance
-- 这些是财务管控规则，归财务参数页签管理；必须在 sale 通用归组之后执行，覆盖前面的结果
UPDATE sys_config SET config_group='finance' WHERE config_key LIKE 'sale.delivery.payment.%' OR config_key='sale.receipt.receive.mode';

-- 库存参数：inv.* / inventory.* / kgw_batch_* → inventory
UPDATE sys_config SET config_group='inventory' WHERE config_key LIKE 'inv.%' OR config_key LIKE 'inventory.%' OR config_key LIKE 'kgw_batch_%';

-- 委外参数：subcontract.* → subcontract
UPDATE sys_config SET config_group='subcontract' WHERE config_key LIKE 'subcontract.%';

-- ========== Step3: 按 key 设置 config_section（小节标题） ==========
-- --- sys_param group ---
UPDATE sys_config SET config_section='基础设置' WHERE config_group='sys_param' AND config_key IN (
  'sys.ip.limit.enabled','sys.ip.limit.segment',
  'sys.tax.enabled','sys.tax.rate','sys.tax.method',
  'sys.foreign.currency.enabled','sys.base.currency',
  'sys.print.when.effective',
  'sys.customer.duplicate.check','sys.supplier.duplicate.check',
  'sys.material.duplicate.check','sys.spec.duplicate.check',
  'sys.auto.customer.no','sys.auto.supplier.no',
  'sys.float.unit.enabled','sys.lock.bill.date',
  'sys.discount.calc.method','sys.loss.calc.method',
  'sys.unified.column.enabled','sys.unified.column.employee',
  'sys.show.spec.in.dropdown',
  'sys.bill.no.duplicate.check','sys.phone.duplicate.check',
  'kgw_warehouse_required'
);
UPDATE sys_config SET config_section='小数位数设置' WHERE config_group='sys_param' AND config_key IN ('precision.quantity','precision.amount','precision.trim.zero');
UPDATE sys_config SET config_section='显示规格设置' WHERE config_group='sys_param' AND config_key='sys.display.specs';

-- --- purchase group ---
UPDATE sys_config SET config_section='公用参数' WHERE config_group='purchase' AND config_key IN (
  'purchase.price.over.max','kgw_purchase_price_strategy','purchase.price.manual.modify','purchase.invoice.default'
);
UPDATE sys_config SET config_section='采购申请单' WHERE config_group='purchase' AND config_key='purchase.request.price.strategy';
UPDATE sys_config SET config_section='采购订单' WHERE config_group='purchase' AND config_key IN (
  'purchase.order.require.source','purchase.order.allow.add.material','purchase.order.skip.request.price',
  'purchase.order.over.qty','purchase.order.allow.zero.price','purchase.order.over.max.stock'
);
UPDATE sys_config SET config_section='采购入库' WHERE config_group='purchase' AND config_key IN (
  'purchase.inbound.require.source','purchase.inbound.allow.add.material',
  'purchase.inbound.over.qty','purchase.inbound.allow.zero.price','purchase.inbound.over.max.stock'
);
UPDATE sys_config SET config_section='采购退货' WHERE config_group='purchase' AND config_key IN (
  'purchase.return.require.source','purchase.return.over.qty'
);

-- --- sale group ---
UPDATE sys_config SET config_section='公用参数' WHERE config_group='sale' AND config_key IN (
  'sale.price.lowest.check','kgw_sale_price_strategy','sale.price.manual.modify','sale.invoice.default',
  'sale.credit.check','sale.credit.overdue.block','sale.delivery.default.days','sale.discount.max',
  'sale.order.prefix','sale.return.days','sale.price.fetch.priority','sale.price.strategy.enabled'
);
UPDATE sys_config SET config_section='销售报价单' WHERE config_group='sale' AND config_key='sale.quote.price.strategy';
UPDATE sys_config SET config_section='销售订单' WHERE config_group='sale' AND config_key IN (
  'sale.order.require.source','sale.order.allow.add.material','sale.order.over.qty',
  'sale.order.allow.zero.price','sale.order.below.min.stock'
);
UPDATE sys_config SET config_section='销售出库' WHERE config_group='sale' AND config_key IN (
  'sale.outbound.require.source','sale.outbound.allow.add.material','sale.outbound.over.qty',
  'sale.outbound.allow.zero.price','sale.outbound.below.min.stock'
);
UPDATE sys_config SET config_section='销售退货' WHERE config_group='sale' AND config_key IN (
  'sale.return.require.source','sale.return.over.qty'
);
UPDATE sys_config SET config_section='收款发货管控' WHERE config_group='finance' AND config_key IN (
  'sale.delivery.payment.control','sale.delivery.payment.control.scope',
  'sale.delivery.payment.advance.tolerance','sale.delivery.payment.check.bypass.approved',
  'sale.receipt.receive.mode'
);

-- --- inventory group ---
UPDATE sys_config SET config_section='库存管理参数' WHERE config_group='inventory' AND config_key IN (
  'inv.sale.order.check.stock','inventory.negative.allowed',
  'inv.check.stock.when.approving','inv.check.stock.when.draft',
  'inv.location.enabled',
  'inv.allow.expired.batch.outbound','inv.allow.batch.repeat.inbound',
  'inv.sync.default.bom.to.order','inv.sync.material.price.to.bom','inv.sync.material.no.to.bom',
  'inv.stock.check.scope','inv.warning.reference'
);

-- --- subcontract group ---
UPDATE sys_config SET config_section='公用参数' WHERE config_group='subcontract' AND config_key IN (
  'subcontract.price.strategy','subcontract.price.manual.modify','subcontract.invoice.default'
);
UPDATE sys_config SET config_section='委外加工单' WHERE config_group='subcontract' AND config_key IN (
  'subcontract.order.require.source','subcontract.order.allow.add.material',
  'subcontract.order.over.qty','subcontract.order.allow.zero.price'
);
UPDATE sys_config SET config_section='委外发料' WHERE config_group='subcontract' AND config_key IN (
  'subcontract.issue.require.source','subcontract.issue.allow.add.material','subcontract.issue.over.qty'
);
UPDATE sys_config SET config_section='委外入库' WHERE config_group='subcontract' AND config_key IN (
  'subcontract.inbound.require.source','subcontract.inbound.allow.add.material',
  'subcontract.inbound.over.qty','subcontract.inbound.allow.zero.price'
);
UPDATE sys_config SET config_section='委外退料' WHERE config_group='subcontract' AND config_key IN (
  'subcontract.return.require.source','subcontract.return.over.qty'
);

-- ========== Step4: 修正 config_type（string→select）+ 补全 config_options ==========
-- 之前前端硬编码为 select 但数据库存 string 的参数，统一修正为 select 并补 options

-- sys_param 修正
UPDATE sys_config SET config_type='select', config_options='[{"label":"以折扣额推算折扣率","value":"amount"},{"label":"以折扣率推算折扣额","value":"rate"}]' WHERE config_key='sys.discount.calc.method';
UPDATE sys_config SET config_type='select', config_options='[{"label":"以损耗率推算损耗量","value":"rate"},{"label":"以损耗量推算损耗率","value":"amount"}]' WHERE config_key='sys.loss.calc.method';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不含税价","value":"不含税价"},{"label":"含税价","value":"含税价"},{"label":"不计税","value":"不计税"}]' WHERE config_key='sys.tax.method';
UPDATE sys_config SET config_type='select', config_options='[{"label":"人民币","value":"人民币"},{"label":"美元","value":"美元"}]' WHERE config_key='sys.base.currency';

-- purchase 修正
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"},{"label":"不允许","value":"不允许"},{"label":"不检测","value":"不检测"}]' WHERE config_key='purchase.price.over.max';
UPDATE sys_config SET config_type='select', config_options='[{"label":"最新采购入库价","value":"最新采购入库价"},{"label":"最新采购订价","value":"最新采购订价"},{"label":"取货品资料中采购价","value":"取货品资料中采购价"}]' WHERE config_key='kgw_purchase_price_strategy';
UPDATE sys_config SET config_type='select', config_options='[{"label":"允许","value":"允许"},{"label":"仅超管允许","value":"仅超管允许"},{"label":"不允许","value":"不允许"}]' WHERE config_key='purchase.price.manual.modify';
UPDATE sys_config SET config_type='select', config_options='[{"label":"否","value":"否"},{"label":"是","value":"是"},{"label":"手动选择","value":"手动选择"}]' WHERE config_key='purchase.invoice.default';
UPDATE sys_config SET config_type='select', config_options='[{"label":"取货品资料中采购价","value":"取货品资料中采购价"}]' WHERE config_key='purchase.request.price.strategy';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"}]' WHERE config_key='purchase.order.over.qty';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不检测","value":"不检测"}]' WHERE config_key='purchase.order.over.max.stock';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"}]' WHERE config_key='purchase.inbound.over.qty';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不允许","value":"不允许"}]' WHERE config_key='purchase.inbound.over.max.stock';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不检测","value":"不检测"}]' WHERE config_key='purchase.return.over.qty';

-- sale 修正
UPDATE sys_config SET config_type='select', config_options='[{"label":"不启用","value":"不启用"}]' WHERE config_key='kgw_sale_price_strategy';
UPDATE sys_config SET config_type='select', config_options='[{"label":"允许","value":"允许"}]' WHERE config_key='sale.price.manual.modify';
UPDATE sys_config SET config_type='select', config_options='[{"label":"是","value":"是"},{"label":"否","value":"否"},{"label":"手动选择","value":"手动选择"}]' WHERE config_key='sale.invoice.default';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不启用","value":"不启用"}]' WHERE config_key='sale.quote.price.strategy';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不控制","value":"不控制"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]' WHERE config_key='sale.order.over.qty';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"}]' WHERE config_key='sale.order.below.min.stock';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不控制","value":"不控制"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]' WHERE config_key='sale.outbound.over.qty';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"}]' WHERE config_key='sale.outbound.below.min.stock';
UPDATE sys_config SET config_type='select', config_options='[{"label":"不控制","value":"不控制"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]' WHERE config_key='sale.return.over.qty';

-- inventory 修正
UPDATE sys_config SET config_type='select', config_options='[{"label":"实际库存","value":"actual"},{"label":"可用库存","value":"available"}]' WHERE config_key='inv.warning.reference';

-- subcontract 修正
UPDATE sys_config SET config_type='select', config_options='[{"label":"最新委外入库价","value":"最新委外入库价"},{"label":"最新委外订价","value":"最新委外订价"},{"label":"取货品资料中加工价","value":"取货品资料中加工价"}]' WHERE config_key='subcontract.price.strategy';
UPDATE sys_config SET config_type='select', config_options='[{"label":"允许","value":"允许"},{"label":"仅超管允许","value":"仅超管允许"},{"label":"不允许","value":"不允许"}]' WHERE config_key='subcontract.price.manual.modify';
UPDATE sys_config SET config_type='select', config_options='[{"label":"否","value":"否"},{"label":"是","value":"是"},{"label":"手动选择","value":"手动选择"}]' WHERE config_key='subcontract.invoice.default';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"},{"label":"不允许","value":"不允许"}]' WHERE config_key='subcontract.order.over.qty';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"},{"label":"不允许","value":"不允许"}]' WHERE config_key='subcontract.issue.over.qty';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"},{"label":"不允许","value":"不允许"}]' WHERE config_key='subcontract.inbound.over.qty';
UPDATE sys_config SET config_type='select', config_options='[{"label":"警告提示","value":"警告提示"},{"label":"不检测","value":"不检测"}]' WHERE config_key='subcontract.return.over.qty';

-- ========== Step5: 补全缺失的 config_name（部分参数可能没有中文名） ==========
UPDATE sys_config SET config_name='IP段' WHERE config_key='sys.ip.limit.segment' AND (config_name IS NULL OR config_name='');
UPDATE sys_config SET config_name='税率' WHERE config_key='sys.tax.rate' AND (config_name IS NULL OR config_name='');
UPDATE sys_config SET config_name='默认计税方式' WHERE config_key='sys.tax.method' AND (config_name IS NULL OR config_name='');
UPDATE sys_config SET config_name='本位币' WHERE config_key='sys.base.currency' AND (config_name IS NULL OR config_name='');
UPDATE sys_config SET config_name='统一列设置员工' WHERE config_key='sys.unified.column.employee' AND (config_name IS NULL OR config_name='');
UPDATE sys_config SET config_name='数量小数位数' WHERE config_key='precision.quantity' AND (config_name IS NULL OR config_name='');
UPDATE sys_config SET config_name='金额小数位数' WHERE config_key='precision.amount' AND (config_name IS NULL OR config_name='');

-- ========== Step6: 补全 sort_order（按 group 内字母序自动编号，间隔10） ==========
-- 用窗口函数给每个 group 内的参数按 config_key 排序生成 sort_order
DO $$
DECLARE
    r RECORD;
    v_seq INT;
    v_group VARCHAR;
BEGIN
    FOR r IN SELECT config_group, config_key FROM sys_config
             WHERE config_group IN ('sys_param','purchase','sale','inventory','subcontract')
             ORDER BY config_group, config_key
    LOOP
        IF r.config_group IS DISTINCT FROM v_group THEN
            v_group := r.config_group;
            v_seq := 10;
        ELSE
            v_seq := v_seq + 10;
        END IF;
        UPDATE sys_config SET sort_order = v_seq WHERE config_key = r.config_key;
    END LOOP;
END $$;

-- ========== 验证 ==========
SELECT 'dyn_settings_ok' AS result, COUNT(*) AS total_configured
FROM sys_config
WHERE config_group IN ('sys_param','purchase','sale','inventory','subcontract')
  AND config_section IS NOT NULL
  AND config_name IS NOT NULL AND config_name != '';
