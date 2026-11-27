-- ===========================================================================
-- 38_material_code_rule_seed.sql
-- 用途：物料编码规则（material_code_rule）通用兜底规则种子数据
--       修复"物料编码根据分类自动编号不生效"问题：
--       后端按分类生成编码时，若分类未配置专属规则，则回退到本通用规则
--       （category_id 为 NULL 的那条）。
-- 规则内容：前缀 WL + 拼接分类编码 + 4 位流水号（不含日期）
-- 幂等性：通用规则已存在时不重复插入；列已存在时不重复添加
-- ===========================================================================

-- 兼容保护：早期建表脚本（15_customer_driven_features.sql）未包含以下两列
ALTER TABLE material_code_rule ADD COLUMN IF NOT EXISTS include_category_code SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN material_code_rule.include_category_code IS '是否拼接分类编码: 0-不拼接, 1-拼接(从根分类到当前分类的编码依次拼接)';
ALTER TABLE material_code_rule ADD COLUMN IF NOT EXISTS include_date SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN material_code_rule.include_date IS '是否包含日期: 0-不包含, 1-包含yyyyMMdd';

-- 幂等插入通用规则（category_id IS NULL 表示通用规则，仅允许一条）
INSERT INTO material_code_rule (category_id, category_name, prefix, seq_length, is_enabled, include_category_code, include_date, last_seq, created_at, updated_at)
SELECT NULL, '通用规则', 'WL', 4, 1, 1, 0, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM material_code_rule WHERE category_id IS NULL);
