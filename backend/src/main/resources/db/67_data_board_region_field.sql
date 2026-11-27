-- ============================================================
-- 67_data_board_region_field.sql
-- 数据看板「各区域销售额/营收分布」统计口径所需的客户自定义字段（区域）
--
-- 幂等：INSERT ... WHERE NOT EXISTS，重复执行零副作用
-- 只增不删：不 DELETE / 不 TRUNCATE / 不 DROP
-- 说明：本文件只创建字段"定义"，不含任何客户取值；客户可在
--       设置中心 → 自定义字段 中修改选项或停用，也可在客户档案中逐户补录。
-- ============================================================

INSERT INTO sys_custom_field (entity_type, field_key, field_label, field_type, field_options,
                              section_name, sort_order, is_required, is_list_show, is_search, is_enabled,
                              placeholder, remark, created_at, updated_at, created_by)
SELECT 'customer', 'region', '区域', 'select',
       '[{"label":"国内","value":"国内"},{"label":"欧美","value":"欧美"},{"label":"东南亚","value":"东南亚"},{"label":"其他","value":"其他"}]',
       '基础信息', 50, 0, 1, 1, 1,
       '请选择区域',
       '数据看板区域分布统计口径字段；可在设置中心-自定义字段修改选项或停用',
       NOW(), NOW(), 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_custom_field WHERE entity_type = 'customer' AND field_key = 'region'
);
