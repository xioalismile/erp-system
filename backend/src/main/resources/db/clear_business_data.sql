-- ===========================================================================
-- 智能ERP系统 业务数据清空脚本 (PostgreSQL)
-- 用途: 清空所有业务单据数据, 保留基础档案/系统配置/字典/打印模板
-- 执行: psql -U postgres -d erp_db -f clear_business_data.sql
-- ===========================================================================
SET client_encoding TO 'UTF8';

-- 动态清空所有业务表(自动跳过不存在的表)
DO $$
DECLARE
    r RECORD;
    tbl_list TEXT[] := ARRAY[
        -- 销售模块
        'sale_quote_item','sale_quote','sale_order_item','sale_order',
        'sale_delivery_item','sale_delivery','sale_return_item','sale_return',
        'sale_invoice_item','sale_invoice',
        -- 采购模块
        'purchase_request_item','purchase_request','purchase_order_item','purchase_order',
        'purchase_receipt_item','purchase_receipt','purchase_return_item','purchase_return',
        'purchase_invoice_item','purchase_invoice','purchase_contract_item','purchase_contract',
        -- 库存模块
        'inventory_current','inventory_ledger','inventory_summary','inventory_freeze',
        'other_receipt_item','other_receipt','other_issue_item','other_issue',
        'material_requisition_item','material_requisition',
        'stock_transfer_item','stock_transfer','stock_check_item','stock_check',
        'batch','serial_number','batch_inventory','batch_info',
        'inventory_warning','inventory_warning_log',
        -- 生产模块
        'production_order_item','production_order','process_card_item','process_card',
        'operation_report_item','operation_report','production_receipt_item','production_receipt',
        'quality_check_item','quality_check',
        'mrp_demand','mrp_supply','mrp_plan_item','mrp_plan',
        'produce_assemble_detail','produce_assemble_bom','produce_assemble',
        'produce_disassemble_detail','produce_disassemble_bom','produce_disassemble',
        -- 财务模块
        'account_receivable_item','account_receivable','account_payable_item','account_payable',
        'cash_receipt_writeoff','cash_receipt','cash_payment_writeoff','cash_payment',
        'voucher_entry','voucher','account_balance',
        'cost_calculation_item','cost_calculation',
        'sale_statement_item','sale_statement','purchase_statement_item','purchase_statement',
        -- CRM模块
        'crm_follow_up','crm_lead','crm_opportunity','crm_complaint','crm_survey',
        -- 销售目标
        'sales_target_company','sales_target_employee',
        -- 价格策略
        'sale_price_strategy_item','sale_price_strategy',
        'purchase_price_strategy_item','purchase_price_strategy',
        -- 费用模块
        'sale_expense_item','sale_expense','purchase_expense_item','purchase_expense',
        'operation_expense_allocation','operation_expense',
        -- 预收预付款
        'sale_pre_receipt_item','sale_pre_receipt','purchase_pre_payment_item','purchase_pre_payment',
        -- 委外模块
        'subcontract_order_item','subcontract_order','subcontract_pick_item','subcontract_pick',
        'subcontract_supplement_item','subcontract_supplement',
        'subcontract_inbound_item','subcontract_inbound','subcontract_return_item','subcontract_return',
        -- 客户驱动
        'customer_pool_claim_log','customer_segment',
        'free_sample_order_item','free_sample_order',
        -- 信誉额度
        'credit_limit_log','credit_limit',
        -- 日志
        'sys_operate_log','ai_audit_log','operation_log','doc_trace','data_version_record',
        -- 基础档案表(E2E验收测试要求从空数据开始, 仅保留 sys_user/sys_role/sys_dict 等系统配置)
        'material','material_category','bom','bom_item',
        'unit_of_measure','currency','payment_term','settlement_mode',
        'price_level','account','bank_account','location','warehouse',
        'department','employee',
        'customer','supplier',
        'work_center','workstation','routing','routing_operation',
        'process_card','process_card_item',
        -- CRM基础数据
        'crm_customer_segment','crm_customer_tag_relation'
    ];
    tbl_name TEXT;
    exists_count INT := 0;
    skipped_count INT := 0;
BEGIN
    FOREACH tbl_name IN ARRAY tbl_list LOOP
        BEGIN
            EXECUTE format('TRUNCATE TABLE %I RESTART IDENTITY CASCADE', tbl_name);
            exists_count := exists_count + 1;
        EXCEPTION WHEN undefined_table THEN
            skipped_count := skipped_count + 1;
        END;
    END LOOP;
    RAISE NOTICE '已清空 % 个表, 跳过 % 个不存在的表', exists_count, skipped_count;
END $$;

-- 重置客户统计字段
UPDATE customer SET
    follow_up_count = 0, order_count = 0, total_order_amount = 0,
    refund_amount = 0, complaint_count = 0, business_opportunity_count = 0,
    last_order_date = NULL, last_follow_up_date = NULL,
    pool_type = 0, pool_enter_date = NULL, satisfaction_score = 0
WHERE pool_type IS NOT NULL OR follow_up_count > 0 OR order_count > 0;

-- 恢复 admin 系统账号(TRUNCATE employee CASCADE 会级联清空 sys_user/sys_user_role)
INSERT INTO sys_user (user_id, user_code, username, password, real_name, user_type, is_admin, is_enabled, status)
VALUES (1, 'U001', 'admin', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '系统管理员', '系统用户', 1, 1, '启用')
ON CONFLICT (username) DO NOTHING;

-- 恢复 admin 的超管角色关联(sys_role 表保留, role_code='SUPER_ADMIN' 为超管角色)
INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, role_id FROM sys_role WHERE role_code IN ('SUPER_ADMIN','ADMIN','admin')
ON CONFLICT DO NOTHING;

-- 重置 admin 用户自增序列(避免后续注册用户与 admin 主键冲突)
SELECT setval(pg_get_serial_sequence('sys_user','user_id'),
    GREATEST((SELECT MAX(user_id) FROM sys_user), 1), true);

SELECT '=== 业务数据清空完成, 基础档案已保留, admin 账号已恢复 ===' AS result;
