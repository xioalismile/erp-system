-- ===========================================================================
-- 价格策略模块 - PostgreSQL 建表脚本
-- 包含四张表：
--   1. sale_price_strategy        销售价格策略主表
--   2. sale_price_strategy_item   销售价格策略明细（商品级价格）
--   3. purchase_price_strategy    采购价格策略主表
--   4. purchase_price_strategy_item 采购价格策略明细（商品级价格）
-- 业务规则：
--   - 支持按客户等级/供应商等级、商品分类、品牌、数量区间、生效日期匹配策略
--   - 优先级越高（数值越大）越先匹配
--   - 价格类型：fixed(固定价)、discount(折扣率%)、markup(加价率%)
--   - 明细项优先级高于主策略价格
-- ===========================================================================

-- 1. 销售价格策略主表
CREATE TABLE IF NOT EXISTS sale_price_strategy (
    id BIGSERIAL PRIMARY KEY,
    strategy_name VARCHAR(200),                                  -- 策略名称（2026-10-10 起界面去掉，列保留兼容）
    strategy_code VARCHAR(50) NOT NULL,                         -- 策略编码（系统自动生成）
    target_type VARCHAR(20) DEFAULT 'material',                 -- 适用范围：material按物料/category按商品分类/customer按客户
    priority INTEGER DEFAULT 1,                                 -- 优先级：默认1，不允许重复
    customer_level VARCHAR(50),                                 -- 客户等级（已停用，列保留兼容）
    material_category_id BIGINT,                                -- 商品分类ID（已停用，列保留兼容）
    brand_id BIGINT,                                            -- 品牌ID（已停用，列保留兼容）
    start_date DATE,                                            -- 生效开始日期
    end_date DATE,                                              -- 生效结束日期
    min_quantity NUMERIC(18,4),                                 -- 最小数量
    max_quantity NUMERIC(18,4),                                 -- 最大数量
    price_type VARCHAR(20) NOT NULL DEFAULT 'fixed',            -- 价格类型: fixed/discount/markup
    price_value NUMERIC(18,4),                                  -- 价格值：固定价/折扣率/加价率
    status SMALLINT DEFAULT 1,                                  -- 状态: 0禁用 1启用
    remark VARCHAR(500),                                        -- 备注
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'admin',
    updated_by VARCHAR(50) DEFAULT 'admin',
    CONSTRAINT uk_sale_strategy_code UNIQUE (strategy_code)
);

COMMENT ON TABLE sale_price_strategy IS '销售价格策略表';
COMMENT ON COLUMN sale_price_strategy.strategy_name IS '策略名称';
COMMENT ON COLUMN sale_price_strategy.strategy_code IS '策略编码';
COMMENT ON COLUMN sale_price_strategy.priority IS '优先级，数值越大优先级越高';
COMMENT ON COLUMN sale_price_strategy.customer_level IS '客户等级（为空表示通用）';
COMMENT ON COLUMN sale_price_strategy.material_category_id IS '商品分类ID（为空表示全部分类）';
COMMENT ON COLUMN sale_price_strategy.brand_id IS '品牌ID（为空表示全部品牌）';
COMMENT ON COLUMN sale_price_strategy.start_date IS '生效开始日期';
COMMENT ON COLUMN sale_price_strategy.end_date IS '生效结束日期';
COMMENT ON COLUMN sale_price_strategy.min_quantity IS '最小数量';
COMMENT ON COLUMN sale_price_strategy.max_quantity IS '最大数量';
COMMENT ON COLUMN sale_price_strategy.price_type IS '价格类型: fixed固定价/discount折扣率/markup加价率';
COMMENT ON COLUMN sale_price_strategy.price_value IS '价格值：固定价/折扣率/加价率';
COMMENT ON COLUMN sale_price_strategy.status IS '状态: 0禁用 1启用';
COMMENT ON COLUMN sale_price_strategy.remark IS '备注';

-- 2. 销售价格策略明细表
CREATE TABLE IF NOT EXISTS sale_price_strategy_item (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,                                -- 策略ID
    target_type VARCHAR(20) DEFAULT 'material',                 -- 对象类型：material/category/customer
    target_id BIGINT,                                           -- 对象ID：物料ID/商品分类ID/客户ID
    material_id BIGINT,                                         -- 商品ID（按物料时=target_id，兼容保留）
    price_type VARCHAR(20) DEFAULT 'fixed',                     -- 价格类型: fixed固定价/discount折扣率/markup加价率/gross_profit毛利率
    price_value NUMERIC(18,4) NOT NULL DEFAULT 0,               -- 价格值
    cost_price NUMERIC(18,4) DEFAULT 0,                         -- 成本价
    gross_profit_rate NUMERIC(18,4) DEFAULT 0,                  -- 毛利率(%)
    min_quantity NUMERIC(18,4),                                 -- 该对象的数量下限（为空=不限）
    max_quantity NUMERIC(18,4),                                 -- 该对象的数量上限（为空=不限）
    remark VARCHAR(500),                                        -- 备注
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'admin',
    updated_by VARCHAR(50) DEFAULT 'admin',
    CONSTRAINT uk_sale_strategy_item UNIQUE (strategy_id, material_id)
);

COMMENT ON TABLE sale_price_strategy_item IS '销售价格策略明细表';
COMMENT ON COLUMN sale_price_strategy_item.strategy_id IS '策略ID';
COMMENT ON COLUMN sale_price_strategy_item.material_id IS '商品ID';
COMMENT ON COLUMN sale_price_strategy_item.price_type IS '价格类型: fixed固定价/discount折扣率/markup加价率/gross_profit毛利率';
COMMENT ON COLUMN sale_price_strategy_item.price_value IS '价格值';
COMMENT ON COLUMN sale_price_strategy_item.cost_price IS '成本价';
COMMENT ON COLUMN sale_price_strategy_item.gross_profit_rate IS '毛利率(%)';
COMMENT ON COLUMN sale_price_strategy_item.remark IS '备注';

-- 3. 采购价格策略主表
CREATE TABLE IF NOT EXISTS purchase_price_strategy (
    id BIGSERIAL PRIMARY KEY,
    strategy_name VARCHAR(200),                                  -- 策略名称（2026-10-10 起界面去掉，列保留兼容）
    strategy_code VARCHAR(50) NOT NULL,                         -- 策略编码（系统自动生成）
    target_type VARCHAR(20) DEFAULT 'material',                 -- 适用范围：material按物料/category按商品分类/supplier按供应商
    priority INTEGER DEFAULT 1,                                 -- 优先级：默认1，不允许重复
    supplier_level VARCHAR(50),                                 -- 供应商等级（已停用，列保留兼容）
    material_category_id BIGINT,                                -- 商品分类ID（已停用，列保留兼容）
    brand_id BIGINT,                                            -- 品牌ID（已停用，列保留兼容）
    start_date DATE,                                            -- 生效开始日期
    end_date DATE,                                              -- 生效结束日期
    min_quantity NUMERIC(18,4),                                 -- 最小数量
    max_quantity NUMERIC(18,4),                                 -- 最大数量
    price_type VARCHAR(20) NOT NULL DEFAULT 'fixed',            -- 价格类型: fixed/discount/markup
    price_value NUMERIC(18,4),                                  -- 价格值：固定价/折扣率/加价率
    status SMALLINT DEFAULT 1,                                  -- 状态: 0禁用 1启用
    remark VARCHAR(500),                                        -- 备注
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'admin',
    updated_by VARCHAR(50) DEFAULT 'admin',
    CONSTRAINT uk_purchase_strategy_code UNIQUE (strategy_code)
);

COMMENT ON TABLE purchase_price_strategy IS '采购价格策略表';
COMMENT ON COLUMN purchase_price_strategy.strategy_name IS '策略名称';
COMMENT ON COLUMN purchase_price_strategy.strategy_code IS '策略编码';
COMMENT ON COLUMN purchase_price_strategy.priority IS '优先级，数值越大优先级越高';
COMMENT ON COLUMN purchase_price_strategy.supplier_level IS '供应商等级（为空表示通用）';
COMMENT ON COLUMN purchase_price_strategy.material_category_id IS '商品分类ID（为空表示全部分类）';
COMMENT ON COLUMN purchase_price_strategy.brand_id IS '品牌ID（为空表示全部品牌）';
COMMENT ON COLUMN purchase_price_strategy.start_date IS '生效开始日期';
COMMENT ON COLUMN purchase_price_strategy.end_date IS '生效结束日期';
COMMENT ON COLUMN purchase_price_strategy.min_quantity IS '最小数量';
COMMENT ON COLUMN purchase_price_strategy.max_quantity IS '最大数量';
COMMENT ON COLUMN purchase_price_strategy.price_type IS '价格类型: fixed固定价/discount折扣率/markup加价率';
COMMENT ON COLUMN purchase_price_strategy.price_value IS '价格值：固定价/折扣率/加价率';
COMMENT ON COLUMN purchase_price_strategy.status IS '状态: 0禁用 1启用';
COMMENT ON COLUMN purchase_price_strategy.remark IS '备注';

-- 4. 采购价格策略明细表
CREATE TABLE IF NOT EXISTS purchase_price_strategy_item (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,                                -- 策略ID
    target_type VARCHAR(20) DEFAULT 'material',                 -- 对象类型：material/category/supplier
    target_id BIGINT,                                           -- 对象ID：物料ID/商品分类ID/供应商ID
    material_id BIGINT,                                         -- 商品ID（按物料时=target_id，兼容保留）
    price_type VARCHAR(20) DEFAULT 'fixed',                     -- 价格类型: fixed/discount/markup
    price_value NUMERIC(18,4) NOT NULL,                         -- 价格值
    cost_price NUMERIC(18,4) DEFAULT 0,                         -- 成本价
    gross_profit_rate NUMERIC(18,4) DEFAULT 0,                  -- 毛利率(%)
    remark VARCHAR(500),                                        -- 行备注（明细行的备注）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'admin',
    updated_by VARCHAR(50) DEFAULT 'admin',
    CONSTRAINT uk_purchase_strategy_item UNIQUE (strategy_id, material_id)
);

COMMENT ON TABLE purchase_price_strategy_item IS '采购价格策略明细表';
COMMENT ON COLUMN purchase_price_strategy_item.strategy_id IS '策略ID';
COMMENT ON COLUMN purchase_price_strategy_item.material_id IS '商品ID';
COMMENT ON COLUMN purchase_price_strategy_item.price_value IS '价格值';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_sale_strategy_status ON sale_price_strategy(status);
CREATE INDEX IF NOT EXISTS idx_sale_strategy_priority ON sale_price_strategy(priority DESC);
CREATE INDEX IF NOT EXISTS idx_sale_strategy_customer_level ON sale_price_strategy(customer_level);
CREATE INDEX IF NOT EXISTS idx_sale_strategy_category ON sale_price_strategy(material_category_id);
CREATE INDEX IF NOT EXISTS idx_sale_strategy_date ON sale_price_strategy(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_sale_item_strategy_id ON sale_price_strategy_item(strategy_id);
CREATE INDEX IF NOT EXISTS idx_sale_item_material_id ON sale_price_strategy_item(material_id);

CREATE INDEX IF NOT EXISTS idx_purchase_strategy_status ON purchase_price_strategy(status);
CREATE INDEX IF NOT EXISTS idx_purchase_strategy_priority ON purchase_price_strategy(priority DESC);
CREATE INDEX IF NOT EXISTS idx_purchase_strategy_supplier_level ON purchase_price_strategy(supplier_level);
CREATE INDEX IF NOT EXISTS idx_purchase_strategy_category ON purchase_price_strategy(material_category_id);
CREATE INDEX IF NOT EXISTS idx_purchase_strategy_date ON purchase_price_strategy(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_purchase_item_strategy_id ON purchase_price_strategy_item(strategy_id);
CREATE INDEX IF NOT EXISTS idx_purchase_item_material_id ON purchase_price_strategy_item(material_id);
