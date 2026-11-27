-- ============================================================
-- 168_delivery_control_on_bill.sql
-- 发货管控下放单据（2026-10-19）
--   口径：发货收款管控在源头销售订单上由做单人直接选择；出库单随单带出、只读展示；
--         出库单提交校验只认来源订单上的模式；设置中心原「发货收款管控模式」参数
--         退为新单默认值。设置中心「发货收款管控范围」（按客户付款分类分流）随本版
--         取消——做单人已能在源头订单上选管控方式，系统按客户分类替人做主已无意义。
-- 幂等：全部 ADD COLUMN IF NOT EXISTS / 单条 DELETE，重跑无副作用。
-- 同内容文件另存：prodsrc/智能ERP-项目/backend/src/main/resources/db/168_delivery_control_on_bill.sql
-- 启动自愈：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 补列（主库 + 逐租户库）。
-- ============================================================

-- 1. 销售订单：发货收款管控模式（源头订单选定，四档，与设置中心参数同值域）
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS payment_control VARCHAR(20);
COMMENT ON COLUMN sale_order.payment_control IS '发货收款管控模式：none=不管控; quota=收多少款发多少货; full=全额收款才能发货; advance=定金模式；空=按设置中心默认';

-- 2. 销售订单：超期账款管控开关（本单提交时，客户有超期未收款是否阻断）
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS overdue_block VARCHAR(10);
COMMENT ON COLUMN sale_order.overdue_block IS '超期账款管控：true=阻断; false=不阻断；空=按设置中心默认';

-- 3. 销售出库单：发货收款管控模式（下推/选单随来源销售订单带出，单据上只读）
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS payment_control VARCHAR(20);
COMMENT ON COLUMN sale_delivery.payment_control IS '发货收款管控模式（随来源销售订单带出）：none/quota/full/advance';

-- 4. 设置中心：删除「发货收款管控范围」（管控方式已在源头订单上直接选择，按客户付款分类分流取消）
DELETE FROM sys_config WHERE config_key = 'sale.delivery.payment.control.scope';
