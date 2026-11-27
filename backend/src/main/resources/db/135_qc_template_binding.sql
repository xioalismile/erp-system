-- ============================================================================
-- 135_qc_template_binding.sql
-- 质检模板绑定模型改造（2026-10-07）
--
-- 背景（用户口径）：质检模板原来只能绑「一个物料」，且「适用工序」是自由文本、
--   同名工序无法区分（多条命中时"谁后建谁生效"）→ 不同物料的同名工序互相串用。
-- 本期改为：模板可绑「多个物料」+「多个物料分类」，「物料+工序」组合优先匹配；
--   同一「物料+工序」不允许两条启用模板（保存时拦截）。
--
-- 内容：
--   1) qc_template_material  质检模板 ↔ 适用物料（多选，一个模板可绑多个物料）
--   2) qc_template_category  质检模板 ↔ 适用分类（多选，挂分类后该分类及其下级分类的物料自动适用）
--   3) 存量迁移：旧 qc_template.material_id（单值）搬进 qc_template_material
--
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE UNIQUE INDEX IF NOT EXISTS / INSERT ... WHERE NOT EXISTS
-- 只增不改不删：旧列 qc_template.material_id 保留过渡（守则04 2.7.2-1，代码不再写入新值，查询表优先）
-- 兜底：SchemaUpgradeEnsurer.ensureQcTables 启动时对主库 + 每个租户库逐库执行同一份
-- 同步落盘：deploy/sql/135_qc_template_binding.sql（部署镜像）
-- ============================================================================

BEGIN;

-- ---------- 1. 质检模板 ↔ 适用物料（多选） ----------
CREATE TABLE IF NOT EXISTS qc_template_material (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    created_at  TIMESTAMP
);
COMMENT ON TABLE  qc_template_material IS '质检模板↔适用物料：一个模板可绑定多个物料（多选）';
COMMENT ON COLUMN qc_template_material.template_id IS '质检模板ID（qc_template.template_id）';
COMMENT ON COLUMN qc_template_material.material_id IS '适用物料ID（物料档案 material.material_id）';

-- ---------- 2. 质检模板 ↔ 适用分类（多选） ----------
CREATE TABLE IF NOT EXISTS qc_template_category (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    created_at  TIMESTAMP
);
COMMENT ON TABLE  qc_template_category IS '质检模板↔适用分类：挂分类后该分类及其下级分类的物料自动适用';
COMMENT ON COLUMN qc_template_category.template_id IS '质检模板ID（qc_template.template_id）';
COMMENT ON COLUMN qc_template_category.category_id IS '适用物料分类ID（物料分类 material_category.category_id）';

-- ---------- 3. 索引（同一模板内不允许重复绑同一物料/同一分类） ----------
CREATE UNIQUE INDEX IF NOT EXISTS uk_qc_tpl_material ON qc_template_material (template_id, material_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_qc_tpl_category ON qc_template_category (template_id, category_id);
CREATE INDEX IF NOT EXISTS idx_qc_tpl_material_mid ON qc_template_material (material_id);
CREATE INDEX IF NOT EXISTS idx_qc_tpl_category_cid ON qc_template_category (category_id);

-- ---------- 4. 存量迁移：旧「适用物料」单值 → 多选关联表（只补缺失行，重复执行零副作用） ----------
INSERT INTO qc_template_material (template_id, material_id, created_at)
SELECT t.template_id, t.material_id, NOW()
  FROM qc_template t
 WHERE t.material_id IS NOT NULL
   AND NOT EXISTS (
     SELECT 1 FROM qc_template_material m
      WHERE m.template_id = t.template_id
        AND m.material_id = t.material_id
   );

COMMIT;

-- ============================================================
-- 验证 SQL（只读，预期：new_tables=2；material_rows ≥ 旧的单值模板数）
-- ============================================================
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_name IN ('qc_template_material', 'qc_template_category')) AS new_tables,
  (SELECT COUNT(*) FROM qc_template_material) AS material_rows,
  (SELECT COUNT(*) FROM qc_template WHERE material_id IS NOT NULL) AS legacy_rows;
