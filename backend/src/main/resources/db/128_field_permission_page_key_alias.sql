-- 128 字段权限「页面键历史别名」归一（幂等，可反复执行）
--
-- 背景：早期字段权限按"单据类型"存页面键（如 purchase_inbound / sale_outbound），
--       后来统一成"目标表名"（purchase_receipt / sale_delivery）。老配置留在旧键上，
--       而页面读取与提交校验一律按新键查询 → 用户"配了必填却没有 * 号、也不拦提交"。
--       线上实测：采购入库单 4 条、销售出库单 4 条必填挂在旧键上，从未生效。
--
-- 处置：把旧键行改写到新键；若新键上已存在同角色同字段的配置，则保留新键那条、删除旧键重复行。
--       运行 n 次结果一致；执行后旧键不再有行（读取侧另有别名兜底，双保险）。

-- ① purchase_inbound → purchase_receipt
UPDATE sys_role_field_permission p
   SET page_key = 'purchase_receipt'
 WHERE p.page_key = 'purchase_inbound'
   AND NOT EXISTS (
       SELECT 1 FROM sys_role_field_permission q
        WHERE q.role_id = p.role_id
          AND q.page_key = 'purchase_receipt'
          AND q.field_key = p.field_key
   );

DELETE FROM sys_role_field_permission p
 WHERE p.page_key = 'purchase_inbound'
   AND EXISTS (
       SELECT 1 FROM sys_role_field_permission q
        WHERE q.role_id = p.role_id
          AND q.page_key = 'purchase_receipt'
          AND q.field_key = p.field_key
   );

-- ② sale_outbound → sale_delivery
UPDATE sys_role_field_permission p
   SET page_key = 'sale_delivery'
 WHERE p.page_key = 'sale_outbound'
   AND NOT EXISTS (
       SELECT 1 FROM sys_role_field_permission q
        WHERE q.role_id = p.role_id
          AND q.page_key = 'sale_delivery'
          AND q.field_key = p.field_key
   );

DELETE FROM sys_role_field_permission p
 WHERE p.page_key = 'sale_outbound'
   AND EXISTS (
       SELECT 1 FROM sys_role_field_permission q
        WHERE q.role_id = p.role_id
          AND q.page_key = 'sale_delivery'
          AND q.field_key = p.field_key
   );

-- ③ 结果自检（输出到部署日志，便于回读核对；应为 0）
DO $$
DECLARE legacy_cnt INT;
BEGIN
    SELECT COUNT(*) INTO legacy_cnt
      FROM sys_role_field_permission
     WHERE page_key IN ('purchase_inbound', 'sale_outbound');
    RAISE NOTICE '[128] legacy page_key rows left = %', legacy_cnt;
END $$;
