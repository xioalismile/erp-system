-- ===========================================================================
-- 成本核算表增强字段 (PostgreSQL)
-- 用途: 支持版本控制、期间管理和审计合规
-- ===========================================================================

-- 添加版本号字段（已确认的反确认后递增，保留审计痕迹）
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;
COMMENT ON COLUMN cost_calculation.version IS '核算版本号（反确认后递增，保留审计痕迹）';

-- 添加期间字段（用于精确定位该核算单所属的会计期间）
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS period VARCHAR(7);
COMMENT ON COLUMN cost_calculation.period IS '核算期间(yyyy-MM格式)';

-- 添加确认时间/反确认时间字段（审计合规）
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;
COMMENT ON COLUMN cost_calculation.confirmed_at IS '确认时间';
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMP;
COMMENT ON COLUMN cost_calculation.reversed_at IS '反确认时间';

-- 添加确认人/反确认人字段（审计合规）
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS confirmed_by VARCHAR(50);
COMMENT ON COLUMN cost_calculation.confirmed_by IS '确认人';
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS reversed_by VARCHAR(50);
COMMENT ON COLUMN cost_calculation.reversed_by IS '反确认人';

-- 添加期间+物料唯一索引（防止同一物料同一期间重复核算单）
-- 注意: 仅对未确认状态生效（部分唯一索引），已确认的核算单保留作为历史快照
CREATE UNIQUE INDEX IF NOT EXISTS uk_cost_calc_period_material
    ON cost_calculation(period, material_id)
    WHERE status IN ('草稿', '已核算');

-- 添加期间索引（提升按期间查询性能）
CREATE INDEX IF NOT EXISTS idx_cost_calc_period ON cost_calculation(period);
