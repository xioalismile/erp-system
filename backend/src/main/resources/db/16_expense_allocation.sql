-- ===========================================================================
-- 费用分摊明细表 (PostgreSQL)
-- 用途: 运营费用单的分摊明细持久化, 审核后自动汇总到成本核算单
-- 来源单类型: SALE_ORDER/PURCHASE_ORDER/PRODUCTION_RECEIPT/PURCHASE_INBOUND/SALE_RETURN
-- ===========================================================================

-- 费用分摊明细表
CREATE TABLE IF NOT EXISTS operation_expense_allocation (
    allocation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    order_type VARCHAR(50),                    -- 来源单类型: SALE_ORDER/PURCHASE_ORDER/PRODUCTION_RECEIPT/PURCHASE_INBOUND/SALE_RETURN
    order_id BIGINT,
    order_no VARCHAR(50),
    amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (expense_id) REFERENCES operation_expense(expense_id) ON DELETE CASCADE
);
COMMENT ON TABLE operation_expense_allocation IS '运营费用分摊明细表';
COMMENT ON COLUMN operation_expense_allocation.expense_id IS '运营费用单ID';
COMMENT ON COLUMN operation_expense_allocation.order_type IS '来源单类型: SALE_ORDER/PURCHASE_ORDER/PRODUCTION_RECEIPT/PURCHASE_INBOUND/SALE_RETURN';
COMMENT ON COLUMN operation_expense_allocation.order_id IS '来源单ID';
COMMENT ON COLUMN operation_expense_allocation.order_no IS '来源单编号';
COMMENT ON COLUMN operation_expense_allocation.amount IS '分摊金额';
COMMENT ON COLUMN operation_expense_allocation.remark IS '备注';

CREATE INDEX IF NOT EXISTS idx_oea_expense_id ON operation_expense_allocation(expense_id);
CREATE INDEX IF NOT EXISTS idx_oea_order ON operation_expense_allocation(order_type, order_id);

-- ===========================================================================
-- 2026-07-09 费用分摊重构: 添加分摊方式/费用类型/分摊基数/分摊比例字段
-- 支持多种费用类型同时分摊, 参考金蝶K3交互设计
-- ===========================================================================

-- 运营费用主表添加分摊方式
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS allocation_method VARCHAR(50);
COMMENT ON COLUMN operation_expense.allocation_method IS '分摊方式: 平均分摊/按数量分摊/按金额分摊/自定义';

-- 运营费用主表添加费用明细行JSON
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS expense_lines_json TEXT;
COMMENT ON COLUMN operation_expense.expense_lines_json IS '费用明细行JSON(多行费用类型+金额+备注)';

-- 分摊明细表添加费用类型/分摊基数/分摊比例
ALTER TABLE operation_expense_allocation ADD COLUMN IF NOT EXISTS expense_type VARCHAR(50);
ALTER TABLE operation_expense_allocation ADD COLUMN IF NOT EXISTS allocation_base NUMERIC(18,4);
ALTER TABLE operation_expense_allocation ADD COLUMN IF NOT EXISTS allocation_ratio NUMERIC(18,4);
COMMENT ON COLUMN operation_expense_allocation.expense_type IS '费用类型(每行可不同): 人工费用/制造费用/水电费/办公费/出差/宴请/快递/其他';
COMMENT ON COLUMN operation_expense_allocation.allocation_base IS '分摊基数(数量/金额等, 按分摊方式自动填写)';
COMMENT ON COLUMN operation_expense_allocation.allocation_ratio IS '分摊比例(%)';
