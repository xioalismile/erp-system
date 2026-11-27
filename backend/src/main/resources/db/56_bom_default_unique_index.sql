-- 56_bom_default_unique_index.sql
-- 目标：业务铁律【同一母件只能有一个默认BOM】 —— 数据库层最后防线，避免并发导入/保存产生多条 is_default=1 的脏数据。
--
-- 执行两阶段：
--   1) 数据收敛（幂等UPDATE）：同一material_id存在多条 is_default=1 时，保留bom_id最小的那条为默认，
--      其余改为非默认(is_default=0)。因bom_id最小最接近"首条创建"，业务上合理；UPDATE带WHERE，不会破坏正常数据。
--   2) 建过滤部分唯一索引（CREATE ... IF NOT EXISTS WHERE）：只针对 is_default=1 的行强制唯一，
--      不影响非默认BOM可任意多条（历史版本、替代BOM等）。
--
-- 适用方言：PostgreSQL（init.sql使用BIGSERIAL，生产部署主数据库）。
--           MySQL 8.0+ 等效写法（不使用时可忽略）：
--               CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_default_one_per_material
--               ON bom(material_id, (CASE WHEN is_default = 1 THEN 1 ELSE NULL END));
--           SQL Server 等效写法（不使用时可忽略）：
--               CREATE UNIQUE NONCLUSTERED INDEX uk_bom_default_one_per_material
--               ON bom(material_id) WHERE is_default = 1;
-- 幂等：UPDATE 只在存在重复默认时命中；CREATE INDEX IF NOT EXISTS 重复执行 0 副作用。
-- -------------------------------------------------------------------------

-- 阶段1：收敛重复默认BOM（保留bom_id最小者，非删除，业务数据安全）
UPDATE bom
   SET is_default = 0,
       updated_at = CURRENT_TIMESTAMP
 WHERE (material_id, bom_id) IN (
     SELECT t.material_id, t.bom_id
       FROM (
           SELECT b.material_id,
                  b.bom_id,
                  ROW_NUMBER() OVER (
                      PARTITION BY b.material_id
                          ORDER BY b.is_default DESC, b.bom_id ASC
                  ) AS rn
             FROM bom b
            WHERE b.is_default = 1
       ) t
      WHERE t.rn > 1
 );

-- 阶段2：数据库层兜底 —— 过滤部分唯一索引：每个物料只允许一行 is_default=1
CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_default_one_per_material
    ON bom(material_id)
 WHERE is_default = 1;
