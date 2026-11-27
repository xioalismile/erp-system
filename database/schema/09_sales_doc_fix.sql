-- ===========================================================================
-- 销售模块文档对齐修复脚本
-- 目的：使销售报价单/销售订单后端实体与文档规范保持一致
-- 说明：
--   1. 报价单明细新增 is_free 字段（是否赠品）
--   2. unit 字段在实体类中改为 exist=false（非数据库字段），数据库无需修改
--   3. discount_rate/discount_amount 字段在数据库中保留（其他单据可能使用），
--      仅报价单/订单明细实体类删除引用
-- ===========================================================================

-- 报价单明细新增 is_free 字段（是否赠品 0-否 1-是）
ALTER TABLE sale_quote_item ADD COLUMN IF NOT EXISTS is_free SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN sale_quote_item.is_free IS '是否赠品 0-否 1-是';

-- 注意：unit 字段在实体类中改为 exist=false，数据库无需修改
-- 注意：discount_rate/discount_amount 字段在数据库中保留（其他单据可能用），
--       仅报价单实体类删除引用
