-- 191_multi_unit_assemble.sql
-- 生产组装单 / 生产拆卸单 接入多单位换算（2026-11-10）
--
-- 背景：多单位换算已铺开销售 / 采购 / 库存 / 生产入库等单据；组装单与拆卸单当时因
--       明细表缺折算列、行数据又是下划线命名而未接入。本轮补齐两张单据的折算字段：
--       成品明细 + BOM 原料明细共 4 张表。
--
-- 口径：
--   1) 全部 ADD COLUMN IF NOT EXISTS，幂等；重复执行 0 副作用；
--   2) 只加列、不删列、不改类型、不动任何存量数据；
--   3) 新增列一律允许 NULL：历史明细换算率为空 = 未启用辅计量（等价换算率 1，旧行为不变）；
--   4) 三库同口径由启动自愈保证：主库 + 全部租户独立库 + 租户模板库
--      （SchemaUpgradeEnsurer#ensureMultiUnitSupportOn 的 itemTables 已登记这 4 张表）。
--
-- 影响表：produce_assemble_detail / produce_assemble_bom /
--         produce_disassemble_detail / produce_disassemble_bom

-- ============ 生产组装单 成品明细 ============
ALTER TABLE produce_assemble_detail ADD COLUMN IF NOT EXISTS conversion_rate NUMERIC(18,8);
ALTER TABLE produce_assemble_detail ADD COLUMN IF NOT EXISTS base_quantity NUMERIC(18,4);
ALTER TABLE produce_assemble_detail ADD COLUMN IF NOT EXISTS float_unit_id BIGINT;
ALTER TABLE produce_assemble_detail ADD COLUMN IF NOT EXISTS float_quantity NUMERIC(18,8);

-- ============ 生产组装单 BOM 原料明细 ============
ALTER TABLE produce_assemble_bom ADD COLUMN IF NOT EXISTS conversion_rate NUMERIC(18,8);
ALTER TABLE produce_assemble_bom ADD COLUMN IF NOT EXISTS base_quantity NUMERIC(18,4);
ALTER TABLE produce_assemble_bom ADD COLUMN IF NOT EXISTS float_unit_id BIGINT;
ALTER TABLE produce_assemble_bom ADD COLUMN IF NOT EXISTS float_quantity NUMERIC(18,8);

-- ============ 生产拆卸单 成品明细 ============
ALTER TABLE produce_disassemble_detail ADD COLUMN IF NOT EXISTS conversion_rate NUMERIC(18,8);
ALTER TABLE produce_disassemble_detail ADD COLUMN IF NOT EXISTS base_quantity NUMERIC(18,4);
ALTER TABLE produce_disassemble_detail ADD COLUMN IF NOT EXISTS float_unit_id BIGINT;
ALTER TABLE produce_disassemble_detail ADD COLUMN IF NOT EXISTS float_quantity NUMERIC(18,8);

-- ============ 生产拆卸单 BOM 原料明细 ============
ALTER TABLE produce_disassemble_bom ADD COLUMN IF NOT EXISTS conversion_rate NUMERIC(18,8);
ALTER TABLE produce_disassemble_bom ADD COLUMN IF NOT EXISTS base_quantity NUMERIC(18,4);
ALTER TABLE produce_disassemble_bom ADD COLUMN IF NOT EXISTS float_unit_id BIGINT;
ALTER TABLE produce_disassemble_bom ADD COLUMN IF NOT EXISTS float_quantity NUMERIC(18,8);
