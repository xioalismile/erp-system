-- 销售价格策略明细表新增字段
-- 用于支持毛利率定价方式

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sale_price_strategy_item' AND column_name = 'price_type') THEN
        ALTER TABLE sale_price_strategy_item ADD COLUMN price_type VARCHAR(20) DEFAULT 'fixed';
        COMMENT ON COLUMN sale_price_strategy_item.price_type IS '价格类型: fixed固定价/discount折扣率/markup加价率/gross_profit毛利率';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sale_price_strategy_item' AND column_name = 'cost_price') THEN
        ALTER TABLE sale_price_strategy_item ADD COLUMN cost_price NUMERIC(18,4) DEFAULT 0;
        COMMENT ON COLUMN sale_price_strategy_item.cost_price IS '成本价';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sale_price_strategy_item' AND column_name = 'gross_profit_rate') THEN
        ALTER TABLE sale_price_strategy_item ADD COLUMN gross_profit_rate NUMERIC(18,4) DEFAULT 0;
        COMMENT ON COLUMN sale_price_strategy_item.gross_profit_rate IS '毛利率(%)';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sale_price_strategy_item' AND column_name = 'remark') THEN
        ALTER TABLE sale_price_strategy_item ADD COLUMN remark VARCHAR(500);
        COMMENT ON COLUMN sale_price_strategy_item.remark IS '备注';
    END IF;
END $$;

-- 修改 price_value 允许默认值为 0（如果原本是 NOT NULL 但没有默认值）
ALTER TABLE sale_price_strategy_item ALTER COLUMN price_value SET DEFAULT 0;
