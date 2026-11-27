-- ===========================================================================
-- 146_price_strategy_target.sql
-- 销售 / 采购价格策略改造（2026-10-10）
--
-- 背景（用户口径）：
--   1) 适用范围由"客户等级+分类+品牌+数量同时生效"改为一处互斥单选：
--      按物料 / 按商品分类 / 按客户
--   2) 取消表头统一价，改成明细行"每个对象各自设价"
--   3) 策略名称去掉；策略编码系统自动生成
--   4) 优先级默认 1 且不允许重复
--
-- 本脚本幂等：重复执行结果一致（ADD COLUMN IF NOT EXISTS / 先判重再重排 / 唯一索引 IF NOT EXISTS）
-- ===========================================================================

-- ---------- 1. 主表：适用范围类型 ----------
ALTER TABLE sale_price_strategy     ADD COLUMN IF NOT EXISTS target_type VARCHAR(20) DEFAULT 'material';
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS target_type VARCHAR(20) DEFAULT 'material';

COMMENT ON COLUMN sale_price_strategy.target_type     IS '适用范围：material按物料 / category按商品分类 / customer按客户';
COMMENT ON COLUMN purchase_price_strategy.target_type IS '适用范围：material按物料 / category按商品分类 / supplier按供应商';

-- ---------- 2. 明细表：对象列（target_type + target_id） ----------
ALTER TABLE sale_price_strategy_item     ADD COLUMN IF NOT EXISTS target_type VARCHAR(20);
ALTER TABLE sale_price_strategy_item     ADD COLUMN IF NOT EXISTS target_id   BIGINT;
ALTER TABLE purchase_price_strategy_item ADD COLUMN IF NOT EXISTS target_type VARCHAR(20);
ALTER TABLE purchase_price_strategy_item ADD COLUMN IF NOT EXISTS target_id   BIGINT;

-- 采购明细补齐价格类型 / 毛利率（与销售明细对齐：每个对象可单独设价格类型）
ALTER TABLE purchase_price_strategy_item ADD COLUMN IF NOT EXISTS price_type        VARCHAR(20) DEFAULT 'fixed';
ALTER TABLE purchase_price_strategy_item ADD COLUMN IF NOT EXISTS cost_price        NUMERIC(18,4) DEFAULT 0;
ALTER TABLE purchase_price_strategy_item ADD COLUMN IF NOT EXISTS gross_profit_rate NUMERIC(18,4) DEFAULT 0;

COMMENT ON COLUMN sale_price_strategy_item.target_type IS '对象类型：material/category/customer（与主表 target_type 一致）';
COMMENT ON COLUMN sale_price_strategy_item.target_id   IS '对象ID：物料ID / 商品分类ID / 客户ID';
COMMENT ON COLUMN purchase_price_strategy_item.target_type IS '对象类型：material/category/supplier（与主表 target_type 一致）';
COMMENT ON COLUMN purchase_price_strategy_item.target_id   IS '对象ID：物料ID / 商品分类ID / 供应商ID';

-- ---------- 3. 明细 material_id / 主表 strategy_name 放开非空 ----------
-- 按分类 / 按客户的行没有物料；策略名称已从界面去掉
ALTER TABLE sale_price_strategy_item     ALTER COLUMN material_id DROP NOT NULL;
ALTER TABLE purchase_price_strategy_item ALTER COLUMN material_id DROP NOT NULL;
ALTER TABLE sale_price_strategy          ALTER COLUMN strategy_name DROP NOT NULL;
ALTER TABLE purchase_price_strategy      ALTER COLUMN strategy_name DROP NOT NULL;

-- ---------- 4. 存量回填：老数据统一视为"按物料" ----------
UPDATE sale_price_strategy_item     SET target_type = 'material', target_id = material_id
 WHERE target_type IS NULL AND material_id IS NOT NULL;
UPDATE purchase_price_strategy_item SET target_type = 'material', target_id = material_id
 WHERE target_type IS NULL AND material_id IS NOT NULL;
UPDATE sale_price_strategy     SET target_type = 'material' WHERE target_type IS NULL;
UPDATE purchase_price_strategy SET target_type = 'material' WHERE target_type IS NULL;

-- ---------- 5. 优先级重排为 1..N（仅当存在重复时才重排，避免每次部署覆盖用户手工值） ----------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sale_price_strategy GROUP BY priority HAVING COUNT(*) > 1) THEN
        WITH ranked AS (
            SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn FROM sale_price_strategy
        )
        UPDATE sale_price_strategy s SET priority = r.rn FROM ranked r WHERE s.id = r.id;
    END IF;
    IF EXISTS (SELECT 1 FROM purchase_price_strategy GROUP BY priority HAVING COUNT(*) > 1) THEN
        WITH ranked AS (
            SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn FROM purchase_price_strategy
        )
        UPDATE purchase_price_strategy s SET priority = r.rn FROM ranked r WHERE s.id = r.id;
    END IF;
END $$;

-- ---------- 6. 优先级不重复（唯一索引） ----------
CREATE UNIQUE INDEX IF NOT EXISTS uk_sale_strategy_priority     ON sale_price_strategy(priority);
CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_strategy_priority ON purchase_price_strategy(priority);

-- ---------- 7. 明细对象查询索引 ----------
CREATE INDEX IF NOT EXISTS idx_sale_item_target     ON sale_price_strategy_item(strategy_id, target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_purchase_item_target ON purchase_price_strategy_item(strategy_id, target_type, target_id);

-- ---------- 8. 数量区间下沉到明细行（2026-10-10 追加：一行一个对象、一个对象自己的数量区间） ----------
ALTER TABLE sale_price_strategy_item     ADD COLUMN IF NOT EXISTS min_quantity NUMERIC(18,4);
ALTER TABLE sale_price_strategy_item     ADD COLUMN IF NOT EXISTS max_quantity NUMERIC(18,4);
ALTER TABLE purchase_price_strategy_item ADD COLUMN IF NOT EXISTS min_quantity NUMERIC(18,4);
ALTER TABLE purchase_price_strategy_item ADD COLUMN IF NOT EXISTS max_quantity NUMERIC(18,4);

COMMENT ON COLUMN sale_price_strategy_item.min_quantity IS '该对象的数量下限（为空=不限）';
COMMENT ON COLUMN sale_price_strategy_item.max_quantity IS '该对象的数量上限（为空=不限）';

-- 存量迁移：把主表原来的数量区间搬到每条明细行（只搬空值，幂等）
UPDATE sale_price_strategy_item i
   SET min_quantity = s.min_quantity, max_quantity = s.max_quantity
  FROM sale_price_strategy s
 WHERE i.strategy_id = s.id
   AND i.min_quantity IS NULL AND i.max_quantity IS NULL
   AND (s.min_quantity IS NOT NULL OR s.max_quantity IS NOT NULL);
UPDATE purchase_price_strategy_item i
   SET min_quantity = s.min_quantity, max_quantity = s.max_quantity
  FROM purchase_price_strategy s
 WHERE i.strategy_id = s.id
   AND i.min_quantity IS NULL AND i.max_quantity IS NULL
   AND (s.min_quantity IS NOT NULL OR s.max_quantity IS NOT NULL);
