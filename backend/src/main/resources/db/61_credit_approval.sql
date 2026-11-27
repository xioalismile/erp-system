-- 61_credit_approval.sql
-- 客户信用授信管控（2026-09-07 客户需求）：
--   A. 信用额度超限阻断总开关 sale.credit.limit.block（默认 false），并迁移旧开关 sale.credit.check
--   B. 客户授信(CUSTOMER_CREDIT) 单据级审批开关 approval.bill.CUSTOMER_CREDIT.enabled = true（默认开启）
-- 幂等：可重复执行，零副作用。customer 表授信三列（credit_approval_status/credit_approved_at/credit_approved_by）
--      由 SchemaGuard 实体反射自动 ADD COLUMN，本脚本不重复建列。

-- A1. 信用额度超限总开关：新 key 不存在时（优先把旧 sale.credit.check 行改名迁移，否则直接插入）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.credit.limit.block') THEN
        IF EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.credit.check') THEN
            -- 迁移旧开关：改名 + 默认关闭（用户需在设置中心重新开启）+ 中文名/分组/小节规范
            UPDATE sys_config
               SET config_key    = 'sale.credit.limit.block',
                   config_value  = 'false',
                   config_name   = '信用额度超限阻断销售单据(合同/订单)',
                   config_group  = 'sale',
                   config_type   = 'boolean',
                   config_section = '公用参数',
                   sort_order    = 13,
                   remark        = 'true=客户未收余额+本次单金额超信用额度时，禁止提交销售合同/销售订单；默认关，需在设置中心开启'
             WHERE config_key = 'sale.credit.check';
        ELSE
            INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_section, sort_order, remark, created_by)
            SELECT 'sale.credit.limit.block', 'false', '信用额度超限阻断销售单据(合同/订单)', 'sale', 'boolean', '公用参数', 13,
                   'true=客户未收余额+本次单金额超信用额度时，禁止提交销售合同/销售订单；默认关，需在设置中心开启', 'system'
            WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.credit.limit.block');
        END IF;
    END IF;
END $$;

-- B. 客户授信单据级审批开关：默认开启（该类型为系统内部授信审批类型，须走审批引擎）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_section, remark, created_by)
SELECT 'approval.bill.CUSTOMER_CREDIT.enabled', 'true', '客户授信 审批开关', 'approval', 'boolean', '审批单据开关',
       '客户授信(月结一次性审批)单据级审批开关，默认开启；关闭后月结客户首单不会触发授信审批', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.CUSTOMER_CREDIT.enabled');
