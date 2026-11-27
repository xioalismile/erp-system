-- 79_material_dedup_fields_and_ui_section.sql
-- 2026-09-02 货品查重「字段组合」自定义配置 + 设置中心「界面显示」归并
--
-- 背景：
--   ① 设置中心「货品规格查重」原是 boolean 开关，但全库代码 0 处消费（历史死配置，改了没效果）。
--      现改造为「货品查重字段」= 多选字段数组（config_type='multi_fields'），
--      语义：所选字段各自不允许重复（默认 物料编码+物料名称 = 与改造前行为逐条一致）；清空 = 不查重。
--      可勾选物料档案全部系统字段 + 全部已启用自定义字段（选项由物料字段元数据接口动态生成，不入库）。
--      → 原 boolean 开关「货品名称查重」(sys.material.duplicate.check) 由本配置里的「物料名称」接管，
--        界面不再显示（数据保留、代码不再消费），故本次不迁移其 config_section。
--   ② 查重类参数（客户/供应商/单据号码/客商手机号）与「货品选择下拉列表中显示规格」
--      从「基础设置」小节迁到「界面显示」小节（页签不变，仍是「系统参数」页签内的分组）。
--
-- 幂等：全部为"带 WHERE 的固定值 UPDATE" + "WHERE NOT EXISTS 的 INSERT"；
--       重复执行零副作用；无 DDL / 无 DELETE / 无 TRUNCATE / 无 DROP。

-- ========== ① 货品规格查重（boolean）→ 货品查重字段（multi_fields 数组） ==========
-- 只在 boolean 时代收敛一次：已是 multi_fields 的库（含用户自定义过的字段）绝不被覆盖
UPDATE sys_config
   SET config_name    = '货品查重字段',
       config_type    = 'multi_fields',
       config_options = '{"source":"material"}',
       config_value   = '["materialCode","materialName"]',
       config_section = '界面显示',
       sort_order     = 94,
       remark         = '选中的字段各自不允许重复（默认 物料编码+物料名称）；清空=不查重；可勾选物料档案全部字段与全部自定义字段',
       updated_at     = NOW(),
       updated_by     = 'system'
 WHERE config_key = 'sys.spec.duplicate.check'
   AND config_type = 'boolean';

-- 兜底：个别老库该键缺失时补一条（幂等）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type,
                        config_options, config_section, sort_order, remark,
                        created_at, updated_at, created_by, updated_by)
SELECT 'sys.spec.duplicate.check', '["materialCode","materialName"]', '货品查重字段', 'sys_param', 'multi_fields',
       '{"source":"material"}', '界面显示', 94,
       '选中的字段各自不允许重复（默认 物料编码+物料名称）；清空=不查重；可勾选物料档案全部字段与全部自定义字段',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.spec.duplicate.check');

-- ========== ② 查重类 + 下拉显示规格 → 「界面显示」小节（页签内分组，排序 90~97） ==========
UPDATE sys_config SET config_section = '界面显示', sort_order = 90, updated_at = NOW(), updated_by = 'system'
 WHERE config_key = 'sys.customer.duplicate.check' AND config_group = 'sys_param';

UPDATE sys_config SET config_section = '界面显示', sort_order = 91, updated_at = NOW(), updated_by = 'system'
 WHERE config_key = 'sys.supplier.duplicate.check' AND config_group = 'sys_param';

UPDATE sys_config SET config_section = '界面显示', sort_order = 92, updated_at = NOW(), updated_by = 'system'
 WHERE config_key = 'sys.bill.no.duplicate.check' AND config_group = 'sys_param';

UPDATE sys_config SET config_section = '界面显示', sort_order = 93, updated_at = NOW(), updated_by = 'system'
 WHERE config_key = 'sys.phone.duplicate.check' AND config_group = 'sys_param';

UPDATE sys_config SET config_section = '界面显示', sort_order = 95, updated_at = NOW(), updated_by = 'system'
 WHERE config_key = 'sys.show.spec.in.dropdown' AND config_group = 'sys_param';

-- ========== 验证 ==========
SELECT 'material_dedup_ui_ok' AS result, config_key, config_type, config_value, config_section, sort_order
  FROM sys_config
 WHERE config_key IN ('sys.spec.duplicate.check', 'sys.customer.duplicate.check', 'sys.supplier.duplicate.check',
                      'sys.bill.no.duplicate.check', 'sys.phone.duplicate.check', 'sys.show.spec.in.dropdown')
 ORDER BY sort_order;
