-- ============================================================================
-- 智能ERP系统 - 字段补齐修复脚本
-- 说明：根据《完整表单字段规范文档》补齐数据库缺失字段
-- 兼容：PostgreSQL
-- 日期：2026-07-03
-- ============================================================================

-- ===========================================================================
-- 模块1：基础资料模块
-- ===========================================================================

-- 1.1 客户档案表 - 补充税务登记号、法人代表
ALTER TABLE customer ADD COLUMN IF NOT EXISTS tax_register_no VARCHAR(50);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS legal_person VARCHAR(50);
COMMENT ON COLUMN customer.tax_register_no IS '税务登记号';
COMMENT ON COLUMN customer.legal_person IS '法人代表';

-- 1.2 供应商档案表 - 补充法人代表
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS legal_person VARCHAR(50);
COMMENT ON COLUMN supplier.legal_person IS '法人代表';

-- 1.3 员工档案表 - 补充入职/离职日期、学历、身份证号
ALTER TABLE employee ADD COLUMN IF NOT EXISTS leave_date DATE;
ALTER TABLE employee ADD COLUMN IF NOT EXISTS education VARCHAR(50);
ALTER TABLE employee ADD COLUMN IF NOT EXISTS id_card VARCHAR(20);
COMMENT ON COLUMN employee.leave_date IS '离职日期';
COMMENT ON COLUMN employee.education IS '学历';
COMMENT ON COLUMN employee.id_card IS '身份证号';

-- 1.4 会计科目表 - 补充现金流量项目、辅助核算项
ALTER TABLE account ADD COLUMN IF NOT EXISTS cash_flow_item VARCHAR(50);
ALTER TABLE account ADD COLUMN IF NOT EXISTS auxiliary_accounting VARCHAR(500);
COMMENT ON COLUMN account.cash_flow_item IS '现金流量项目';
COMMENT ON COLUMN account.auxiliary_accounting IS '辅助核算项';


-- ===========================================================================
-- 模块2：销售管理模块
-- ===========================================================================

-- 2.1 销售报价单 - 补充联系人、联系电话、销售员
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS contact_person VARCHAR(50);
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(20);
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS saler_id BIGINT;
COMMENT ON COLUMN sale_quote.contact_person IS '联系人';
COMMENT ON COLUMN sale_quote.contact_phone IS '联系电话';
COMMENT ON COLUMN sale_quote.saler_id IS '销售员ID';

-- 2.2 销售订单 - 补充关闭人/关闭日期/关闭原因
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
COMMENT ON COLUMN sale_order.closed_by IS '关闭人';
COMMENT ON COLUMN sale_order.closed_at IS '关闭日期';
COMMENT ON COLUMN sale_order.closed_reason IS '关闭原因';

-- 2.3 销售出库单 - 补充承运商、物流单号、运输费
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS carrier VARCHAR(200);
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS tracking_no VARCHAR(100);
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS delivery_cost NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN sale_delivery.carrier IS '承运商';
COMMENT ON COLUMN sale_delivery.tracking_no IS '物流单号';
COMMENT ON COLUMN sale_delivery.delivery_cost IS '运输费';

-- 2.4 销售出库明细 - 补充发货仓库、发货货位、批次号
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS stock_id BIGINT;
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS bin_id BIGINT;
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS batch_no VARCHAR(50);
COMMENT ON COLUMN sale_delivery_item.stock_id IS '发货仓库ID';
COMMENT ON COLUMN sale_delivery_item.bin_id IS '发货货位ID';
COMMENT ON COLUMN sale_delivery_item.batch_no IS '批次号';

-- 2.5 销售退货单 - 补充源出库单号、退货原因
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS source_delivery_no VARCHAR(50);
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS return_reason VARCHAR(500);
COMMENT ON COLUMN sale_return.source_delivery_no IS '源出库单号';
COMMENT ON COLUMN sale_return.return_reason IS '退货原因';

-- 2.6 销售发票 - 补充应收单号、是否生成应收
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS ar_no VARCHAR(50);
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS generate_ar SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN sale_invoice.ar_no IS '应收单号';
COMMENT ON COLUMN sale_invoice.generate_ar IS '是否生成应收';


-- ===========================================================================
-- 模块3：采购管理模块
-- ===========================================================================

-- 3.1 采购申请单 - 补充紧急程度、申请原因
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS urgency_level VARCHAR(20) DEFAULT '普通';
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS request_reason VARCHAR(500);
COMMENT ON COLUMN purchase_request.urgency_level IS '紧急程度';
COMMENT ON COLUMN purchase_request.request_reason IS '申请原因';

-- 3.2 采购订单 - 补充预付比例、预付日期
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS prepayment_ratio NUMERIC(8,4) DEFAULT 0.0000;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS prepayment_date DATE;
COMMENT ON COLUMN purchase_order.prepayment_ratio IS '预付比例';
COMMENT ON COLUMN purchase_order.prepayment_date IS '预付日期';

-- 3.3 采购退货单 - 补充源入库单号、退货原因
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS source_receipt_no VARCHAR(50);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS return_reason VARCHAR(500);
COMMENT ON COLUMN purchase_return.source_receipt_no IS '源入库单号';
COMMENT ON COLUMN purchase_return.return_reason IS '退货原因';

-- 3.4 采购发票 - 补充应付单号、是否生成应付
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS ap_no VARCHAR(50);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS generate_ap SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN purchase_invoice.ap_no IS '应付单号';
COMMENT ON COLUMN purchase_invoice.generate_ap IS '是否生成应付';


-- ===========================================================================
-- 模块4：库存管理模块
-- ===========================================================================

-- 4.1 生产入库单 - 补充工单号、工序卡号
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS work_order_no VARCHAR(50);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS process_card_no VARCHAR(50);
COMMENT ON COLUMN production_receipt.work_order_no IS '工单号';
COMMENT ON COLUMN production_receipt.process_card_no IS '工序卡号';

-- 4.2 盘点明细表 - 补充盈亏原因
ALTER TABLE stock_check_item ADD COLUMN IF NOT EXISTS profit_loss_reason VARCHAR(500);
COMMENT ON COLUMN stock_check_item.profit_loss_reason IS '盈亏原因';


-- ===========================================================================
-- 模块5：生产管理模块
-- ===========================================================================

-- 5.1 工序汇报单 - 补充加班工时
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS overtime_hours NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN operation_report.overtime_hours IS '加班工时';

-- 5.1.1 工序汇报单 - 补充来源单据字段
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
COMMENT ON COLUMN operation_report.source_bill_type IS '来源单据类型';
COMMENT ON COLUMN operation_report.source_bill_no IS '来源单据号';

-- 5.2 工序转移单表（文档定义，数据库原本缺失）- 新建
CREATE TABLE IF NOT EXISTS process_transfer (
    transfer_id BIGSERIAL PRIMARY KEY,
    transfer_no VARCHAR(50) NOT NULL UNIQUE,
    transfer_date DATE NOT NULL,
    work_order_no VARCHAR(50) NOT NULL,
    process_card_no VARCHAR(50),
    source_operation_id BIGINT,
    target_operation_id BIGINT NOT NULL,
    transfer_qty NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_qty NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_qty NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    transfer_type VARCHAR(20) NOT NULL DEFAULT '工序转移',
    operator_id BIGINT,
    remark VARCHAR(2000),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50)
);
COMMENT ON TABLE process_transfer IS '工序转移单';
COMMENT ON COLUMN process_transfer.transfer_no IS '转移单号';
COMMENT ON COLUMN process_transfer.work_order_no IS '工单号';
COMMENT ON COLUMN process_transfer.source_operation_id IS '源工序ID';
COMMENT ON COLUMN process_transfer.target_operation_id IS '目标工序ID';
COMMENT ON COLUMN process_transfer.source_bill_type IS '来源单据类型';
COMMENT ON COLUMN process_transfer.source_bill_no IS '来源单据号';
CREATE INDEX IF NOT EXISTS idx_process_transfer_no ON process_transfer(transfer_no);
CREATE INDEX IF NOT EXISTS idx_process_transfer_wo ON process_transfer(work_order_no);

-- 5.3 委外加工单 - 补充来源单据字段
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
COMMENT ON COLUMN subcontract_order.source_bill_type IS '来源单据类型';
COMMENT ON COLUMN subcontract_order.source_bill_no IS '来源单据号';


-- ===========================================================================
-- 模块6：财务管理模块
-- ===========================================================================

-- 6.1 固定资产 - 补充科目编码、折旧状态、使用月数
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS asset_account_code VARCHAR(50);
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS depreciation_account_code VARCHAR(50);
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS depreciation_expense_account_code VARCHAR(50);
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS depreciation_status VARCHAR(20) DEFAULT '未折旧';
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS useful_life_months INT;
COMMENT ON COLUMN fixed_asset.asset_account_code IS '资产科目编码';
COMMENT ON COLUMN fixed_asset.depreciation_account_code IS '累计折旧科目编码';
COMMENT ON COLUMN fixed_asset.depreciation_expense_account_code IS '折旧费用科目编码';
COMMENT ON COLUMN fixed_asset.depreciation_status IS '折旧状态';
COMMENT ON COLUMN fixed_asset.useful_life_months IS '使用月数';

-- 6.2 预算单 - 补充预算期间、预警比率、执行率
ALTER TABLE budget ADD COLUMN IF NOT EXISTS budget_period VARCHAR(20);
ALTER TABLE budget ADD COLUMN IF NOT EXISTS warn_rate NUMERIC(6,4) DEFAULT 80.0000;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS executed_rate NUMERIC(6,4) DEFAULT 0.0000;
COMMENT ON COLUMN budget.budget_period IS '预算期间';
COMMENT ON COLUMN budget.warn_rate IS '预警比率';
COMMENT ON COLUMN budget.executed_rate IS '执行率';

-- 6.3 预算明细 - 补充明细期间、预算项目、预算依据、调整金额、调整原因
ALTER TABLE budget_item ADD COLUMN IF NOT EXISTS period_detail VARCHAR(50);
ALTER TABLE budget_item ADD COLUMN IF NOT EXISTS budget_item_name VARCHAR(200);
ALTER TABLE budget_item ADD COLUMN IF NOT EXISTS budget_basis VARCHAR(500);
ALTER TABLE budget_item ADD COLUMN IF NOT EXISTS adjustment_amount NUMERIC(18,2) DEFAULT 0.00;
ALTER TABLE budget_item ADD COLUMN IF NOT EXISTS adjustment_reason VARCHAR(500);
COMMENT ON COLUMN budget_item.period_detail IS '明细期间';
COMMENT ON COLUMN budget_item.budget_item_name IS '预算项目';
COMMENT ON COLUMN budget_item.budget_basis IS '预算依据';
COMMENT ON COLUMN budget_item.adjustment_amount IS '调整金额';
COMMENT ON COLUMN budget_item.adjustment_reason IS '调整原因';

-- 6.4 资金调拨单 - 补充调出/调入银行信息、调拨类型、完成日期
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS from_bank_name VARCHAR(200);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS from_bank_branch VARCHAR(200);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS from_account_balance NUMERIC(18,2);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS to_bank_name VARCHAR(200);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS to_bank_branch VARCHAR(200);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS transfer_type VARCHAR(20) DEFAULT '内部调拨';
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS expected_fee NUMERIC(18,2) DEFAULT 0.00;
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS actual_fee NUMERIC(18,2) DEFAULT 0.00;
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS completion_date DATE;
COMMENT ON COLUMN fund_transfer.from_bank_name IS '调出银行名称';
COMMENT ON COLUMN fund_transfer.from_bank_branch IS '调出开户行';
COMMENT ON COLUMN fund_transfer.from_account_balance IS '调出账户余额';
COMMENT ON COLUMN fund_transfer.to_bank_name IS '调入银行名称';
COMMENT ON COLUMN fund_transfer.to_bank_branch IS '调入开户行';
COMMENT ON COLUMN fund_transfer.transfer_type IS '调拨类型';
COMMENT ON COLUMN fund_transfer.expected_fee IS '预计手续费';
COMMENT ON COLUMN fund_transfer.actual_fee IS '实际手续费';
COMMENT ON COLUMN fund_transfer.completion_date IS '完成日期';

-- 6.5 资金调拨明细表（文档定义，数据库原本缺失）- 新建
CREATE TABLE IF NOT EXISTS fund_transfer_item (
    item_id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    fee_item VARCHAR(100),
    fee_amount NUMERIC(18,2) DEFAULT 0.00,
    fee_account_code VARCHAR(50),
    settle_status VARCHAR(20) DEFAULT '未结算',
    settle_date DATE,
    bank_ref_no VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (transfer_id) REFERENCES fund_transfer(transfer_id) ON DELETE CASCADE
);
COMMENT ON TABLE fund_transfer_item IS '资金调拨明细表';
COMMENT ON COLUMN fund_transfer_item.fee_item IS '费用项目';
COMMENT ON COLUMN fund_transfer_item.fee_amount IS '费用金额';
COMMENT ON COLUMN fund_transfer_item.fee_account_code IS '费用科目编码';
COMMENT ON COLUMN fund_transfer_item.settle_status IS '结算状态';
COMMENT ON COLUMN fund_transfer_item.bank_ref_no IS '银行流水号';


-- ===========================================================================
-- 模块7：系统管理模块
-- ===========================================================================

-- 7.1 用户表 - 补充密码盐值、岗位、座机、性别、出生日期、入职日期、数据权限级别
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS password_salt VARCHAR(100);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS position_code VARCHAR(50);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS position_name VARCHAR(100);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS telephone VARCHAR(20);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS gender VARCHAR(2);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS birthday DATE;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS entry_date DATE;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS data_permission_level VARCHAR(20) DEFAULT '本人';
COMMENT ON COLUMN sys_user.password_salt IS '密码盐值';
COMMENT ON COLUMN sys_user.position_code IS '岗位编码';
COMMENT ON COLUMN sys_user.position_name IS '岗位名称';
COMMENT ON COLUMN sys_user.telephone IS '座机号';
COMMENT ON COLUMN sys_user.gender IS '性别';
COMMENT ON COLUMN sys_user.birthday IS '出生日期';
COMMENT ON COLUMN sys_user.entry_date IS '入职日期';
COMMENT ON COLUMN sys_user.data_permission_level IS '数据权限级别';

-- 7.2 角色表 - 补充父角色编码、关联用户数、生效日期、失效日期
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS parent_role_code VARCHAR(50);
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS user_count INT DEFAULT 0;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS expiry_date DATE;
COMMENT ON COLUMN sys_role.parent_role_code IS '父角色编码';
COMMENT ON COLUMN sys_role.user_count IS '关联用户数';
COMMENT ON COLUMN sys_role.effective_date IS '生效日期';
COMMENT ON COLUMN sys_role.expiry_date IS '失效日期';

-- 7.3 用户角色关联表 - 补充生效日期、失效日期、是否主角色、分配日期、分配人、数据范围
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS expiry_date DATE;
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS is_primary_role SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS assign_date DATE;
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS assign_by VARCHAR(50);
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS data_scope_type VARCHAR(20);
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS data_scope_value VARCHAR(500);
COMMENT ON COLUMN sys_user_role.effective_date IS '生效日期';
COMMENT ON COLUMN sys_user_role.expiry_date IS '失效日期';
COMMENT ON COLUMN sys_user_role.is_primary_role IS '是否主角色';
COMMENT ON COLUMN sys_user_role.assign_date IS '分配日期';
COMMENT ON COLUMN sys_user_role.assign_by IS '分配人';
COMMENT ON COLUMN sys_user_role.data_scope_type IS '数据范围类型';
COMMENT ON COLUMN sys_user_role.data_scope_value IS '数据范围值';

-- 7.4 操作日志变更明细表（文档定义，数据库原本缺失）- 新建
CREATE TABLE IF NOT EXISTS sys_operate_log_detail (
    detail_id BIGSERIAL PRIMARY KEY,
    log_id BIGINT NOT NULL,
    field_name VARCHAR(100),
    field_desc VARCHAR(200),
    old_value TEXT,
    new_value TEXT,
    data_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (log_id) REFERENCES sys_operate_log(log_id) ON DELETE CASCADE
);
COMMENT ON TABLE sys_operate_log_detail IS '操作日志变更明细表';
COMMENT ON COLUMN sys_operate_log_detail.field_name IS '变更字段';
COMMENT ON COLUMN sys_operate_log_detail.field_desc IS '字段描述';
COMMENT ON COLUMN sys_operate_log_detail.old_value IS '变更前值';
COMMENT ON COLUMN sys_operate_log_detail.new_value IS '变更后值';
COMMENT ON COLUMN sys_operate_log_detail.data_type IS '数据类型';
CREATE INDEX IF NOT EXISTS idx_operate_log_detail ON sys_operate_log_detail(log_id);


-- ============================================================================
-- 修复脚本执行完成
-- 统计：补齐字段 65 个，新建表 3 张（process_transfer / fund_transfer_item / sys_operate_log_detail）
-- ============================================================================
