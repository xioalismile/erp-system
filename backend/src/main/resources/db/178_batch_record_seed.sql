-- 178: 化妆品批记录参数种子 + 默认质检模板种子（2026-11-05，全部幂等可重跑）
-- 镜像落盘：backend/src/main/resources/db/178_batch_record_seed.sql
-- 列名核对（2026-11-05 以实体为准）：qc_template.template_code/template_name/qc_type/is_enabled；
--   qc_template_item.template_id/line_no/item_name/inspect_method/standard_value。

BEGIN;

-- 1) 未放行批次出库控制（设置中心；默认"预警提示"=对存量行为最小改变，守则04 2.7.2-6）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, config_options, sort_order, remark,
                        created_at, updated_at, created_by, updated_by)
SELECT 'batch.release.outbound.control', '预警提示', '未放行批次出库控制', 'inventory', '库存参数',
       'select',
       '[{"label":"允许","value":"允许"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]',
       65,
       '控制销售出库时未完成检验放行的成品批次：允许=不拦截；预警提示=弹窗提示后可继续；不允许=直接拦截。仅对启用批次管理的物料生效',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'batch.release.outbound.control');

-- 2) 留样保存期限默认参数（月，放行自动生成留样任务时用）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'batch.sample.keep.months', '24', '留样保存期限', 'inventory', '库存参数',
       'number', 66, '成品放行自动生成留样任务时的默认保存月数', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'batch.sample.keep.months');

-- 3) 默认质检模板种子（复用现有 qc_template 体系；is_enabled=1 启用）
INSERT INTO qc_template (template_code, template_name, qc_type, is_enabled, remark, created_at, updated_at, created_by, updated_by)
SELECT 'QT-COSMETIC-FINISH', '化妆品成品默认检验模板', '成品检', 1, '化妆品批记录默认模板（可改可停）', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM qc_template WHERE template_code = 'QT-COSMETIC-FINISH');

-- 4) 默认模板检项明细（外观/pH/菌落总数，幂等：同名检项不重复插）
INSERT INTO qc_template_item (template_id, line_no, item_name, inspect_method, standard_value, is_required, created_at, updated_at)
SELECT t.template_id, v.line_no, v.item_name, v.method, v.std, 1, NOW(), NOW()
FROM qc_template t,
     (VALUES (1,'外观','目测','色泽均匀无异味'),
             (2,'pH值','电位计法','5.0~7.0'),
             (3,'菌落总数','平板计数法','≤1000 CFU/g')) AS v(line_no,item_name,method,std)
WHERE t.template_code = 'QT-COSMETIC-FINISH'
  AND NOT EXISTS (SELECT 1 FROM qc_template_item i
                  WHERE i.template_id = t.template_id AND i.item_name = v.item_name);

COMMIT;
