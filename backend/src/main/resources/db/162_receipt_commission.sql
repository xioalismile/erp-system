-- ============================================================
-- 162_receipt_commission.sql
-- 用途：收款单销售提成（2026-10-16）
--   收款单表头 4 列：提成比例% / 特殊服务费 / 不含税收款金额 / 销售提成
--   核销明细 4 列：销售员ID / 销售员名 / 行不含税收款 / 行销售提成
--   设置中心参数 2 条：启用销售提成（默认开）/ 销售提成默认比例（默认 3）
-- 口径：
--   行不含税收款 = 本行本次核销金额 ×(订单不含税金额÷订单价税合计)；订单价税合计≤0或缺 → 不换算
--   表头不含税收款金额 = Σ核销明细行不含税收款（预收冲抵行不参与）
--   销售提成 = (不含税收款金额 − 特殊服务费) × 提成比例%   （前端实时算，保存时后端兜底重算）
-- 幂等：ADD COLUMN IF NOT EXISTS + NOT EXISTS；重复执行 0 副作用。
-- 同内容文件另存 deploy/sql/162_receipt_commission.sql（守则04 编号体系）。
-- ============================================================

-- 1) 收款单主表（cash_receipt）
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS commission_rate            NUMERIC(9,4);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS special_service_fee        NUMERIC(18,2) DEFAULT 0;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS tax_excluded_received_amount NUMERIC(18,2);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS sales_commission           NUMERIC(18,2);

-- 2) 收付款核销明细（bill_writeoff_detail）
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS saler_id           BIGINT;
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS saler_name         VARCHAR(100);
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS tax_excluded_amount NUMERIC(18,2);
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS commission_rate      NUMERIC(9,4);
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS special_service_fee NUMERIC(18,2) DEFAULT 0;
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS commission         NUMERIC(18,2);

-- 3) 设置中心参数（finance 组「收款发货管控」小节）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, config_options, sort_order, remark,
                        created_at, updated_at, created_by, updated_by)
SELECT 'finance.commission.enabled', 'true', '启用销售提成', 'finance', '收款发货管控',
       'boolean', NULL, 16,
       '开启后收款单显示提成字段（提成比例/特殊服务费/不含税收款金额/销售提成）与核销明细销售员列；关闭即完全旧行为',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'finance.commission.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, config_options, sort_order, remark,
                        created_at, updated_at, created_by, updated_by)
SELECT 'finance.commission.default.rate', '3', '销售提成默认比例', 'finance', '收款发货管控',
       'number', NULL, 17,
       '新开收款单「提成比例%」的默认值（单内可改）；销售提成=(不含税收款金额−特殊服务费)×比例%',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'finance.commission.default.rate');

-- 4) 验证
SELECT '验证: 收款单提成列' AS info, column_name FROM information_schema.columns
WHERE table_name = 'cash_receipt' AND column_name IN ('commission_rate','special_service_fee','tax_excluded_received_amount','sales_commission')
ORDER BY column_name;
SELECT '验证: 核销明细销售员列' AS info, column_name FROM information_schema.columns
WHERE table_name = 'bill_writeoff_detail' AND column_name IN ('saler_id','saler_name','tax_excluded_amount','commission_rate','special_service_fee','commission')
ORDER BY column_name;
SELECT '验证: 提成参数' AS info, config_key, config_value FROM sys_config
WHERE config_key IN ('finance.commission.enabled','finance.commission.default.rate')
ORDER BY config_key;
