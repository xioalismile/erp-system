-- ============================================================================
-- 端到端测试【数据准备】脚本（仅用于本地测试库，不要在生产执行）
-- 用法：psql -h localhost -p 5432 -U erp_user -d erp_db -f e2e_test_prep.sql
-- 说明：本文件含中文，必须用 psql -f 执行（内联中文会报"无效的 UTF8 编码字节"）
-- ============================================================================

-- 1) 发货收款管控：当前为 advance(定金模式)，会拦住"订单无收款不允许发货"，
--    导致销售→出库链路无法单独测试。改回系统默认值 none（不做收款管控）。
UPDATE sys_config SET config_value = 'none'
 WHERE config_key = 'sale.delivery.payment.control';

-- 2) 把 BOM-20260911-0003 / -0004 置为"已生效"。
--    原因：MRP 展开 BOM 只认已生效的 BOM，草稿 BOM 不参与展开，
--    否则 MRP 只会算出成品一行，验证不到"按 BOM 展开子件 + 按供应商分组生成多张采购订单"。
UPDATE bom SET status = '已生效' WHERE bom_id IN (9, 10);

-- 3) 给 BOM 子件维护"默认供应商"，用于验证 MRP 生成采购订单的【按供应商分组】逻辑：
--    88(SFG-001)、90(RM-002) → 供应商1；89(RM-001) → 供应商2
--    预期：生成 2 张草稿采购订单（供应商1 含 2 行、供应商2 含 1 行）
UPDATE material SET default_supplier_id = 1, default_supplier_name = '示例供应商公司'
 WHERE material_id IN (88, 90);
UPDATE material SET default_supplier_id = 2, default_supplier_name = '测试供应商B'
 WHERE material_id = 89;

-- 4) 给 87(FG-001) 维护默认供应商，避免"未指定供应商"分支干扰前两项校验
UPDATE material SET default_supplier_id = 1, default_supplier_name = '示例供应商公司'
 WHERE material_id = 87;

-- 校验：应返回 4 行（87/88/89/90 均有默认供应商）
-- select material_id, material_code, default_supplier_id from material
--  where material_id in (87,88,89,90) order by material_id;
