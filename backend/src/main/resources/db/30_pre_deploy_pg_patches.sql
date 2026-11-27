-- 客户部署前的 PG 列类型/保留字修复（空库执行时幂等，已存在表的 ALTER）
SET client_encoding TO 'UTF8';

-- (1) sys_config.config_options 升级到 TEXT（如果不是 TEXT）
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='sys_config' AND column_name='config_options' AND data_type <> 'text'
  ) THEN
    EXECUTE 'ALTER TABLE sys_config ALTER COLUMN config_options TYPE TEXT USING config_options::TEXT';
  END IF;
END $$;

-- (2) sys_serial_no_config 的保留字列 & auto_generate（幂等补齐）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns WHERE table_name='sys_serial_no_config' AND column_name='auto_generate'
  ) THEN
    ALTER TABLE sys_serial_no_config ADD COLUMN IF NOT EXISTS auto_generate INT DEFAULT 1;
  END IF;
END $$;

-- (3) 索引名改为与 deploy_package 脚本的一致（如果不存在 uk_sys_serial_no_config_doc_type）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'uk_sys_serial_no_config_doc_type' AND n.nspname = 'public'
  ) THEN
    IF NOT EXISTS (
      SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE c.relname = 'uk_sys_serial_no_doc_type' AND n.nspname = 'public'
    ) THEN
      CREATE UNIQUE INDEX uk_sys_serial_no_config_doc_type ON sys_serial_no_config(doc_type);
    END IF;
  END IF;
END $$;

-- (4) 物料(material)表补齐默认仓库列（前端 MaterialEdit 已使用 defaultWarehouseId）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns WHERE table_name='material' AND column_name='default_warehouse_id'
  ) THEN
    ALTER TABLE material ADD COLUMN default_warehouse_id BIGINT;
    COMMENT ON COLUMN material.default_warehouse_id IS '默认仓库ID';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns WHERE table_name='material' AND column_name='default_warehouse_name'
  ) THEN
    ALTER TABLE material ADD COLUMN default_warehouse_name VARCHAR(100);
    COMMENT ON COLUMN material.default_warehouse_name IS '默认仓库名称(冗余)';
  END IF;
END $$;

-- (5) 设置中心：新增 kgw_warehouse_required（默认 false，与 SaleOrderEdit 前端取消仓库必填保持一致）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES (
  'kgw_warehouse_required',
  'false',
  '单据仓库必填开关',
  'sys_param',
  'boolean',
  NULL,
  75,
  '全局KGW规则：true=采购入库/销售出库/退货等单据必须选择仓库才能保存；false=允许仓库为空',
  NOW(), NOW(), 'system', 'system'
)
ON CONFLICT (config_key) DO UPDATE SET
  config_name   = EXCLUDED.config_name,
  config_group  = EXCLUDED.config_group,
  config_type   = EXCLUDED.config_type,
  remark        = EXCLUDED.remark;

-- (6) 设置中心：inventory.default.warehouse 默认值=1，确保采购入库/销售出库保存逻辑在空仓库时能回退默认
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES (
  'inventory.default.warehouse',
  '1',
  '默认仓库',
  'inventory',
  'select',
  NULL,
  3,
  '单据默认仓库ID',
  NOW(), NOW(), 'system', 'system'
)
ON CONFLICT (config_key) DO UPDATE SET
  config_value  = EXCLUDED.config_value,
  config_name   = EXCLUDED.config_name,
  config_group  = EXCLUDED.config_group,
  remark        = EXCLUDED.remark;

-- (7) 修正 finance.ar.source_bill_type 默认值 sale_invoice -> sale_order
-- 23_crm_sale_finance_integration.sql 老版本默认写的是 sale_invoice，导致销售订单审核不生成 AR，
-- 从而无法拦截"重置草稿/作废"（下游应收单校验空）。这里强制修正为 sale_order，并补全选项。
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES (
  'finance.ar.source_bill_type',
  'sale_order',
  '应收账款来源单据',
  'finance',
  'select',
  '销售订单,销售出库单,销售发票',
  1,
  'AR trigger point: sale_order (approved) / sale_delivery (delivered) / sale_invoice (invoiced)',
  NOW(), NOW(), 'system', 'system'
)
ON CONFLICT (config_key) DO UPDATE SET
  config_value   = EXCLUDED.config_value,
  config_options = EXCLUDED.config_options,
  config_name    = EXCLUDED.config_name,
  config_group   = EXCLUDED.config_group,
  remark         = EXCLUDED.remark,
  updated_at     = NOW();
-- (8) finance domain tables self-heal (idempotent, exported from the smoke-tested local erp_db)
DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='account') THEN
    CREATE SEQUENCE IF NOT EXISTS public.account_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.account (
    account_id bigint NOT NULL,
    account_code character varying(255),
    account_name character varying(255),
    account_full_name character varying(255),
    parent_account_id bigint,
    account_type character varying(255),
    balance_direction character varying(255),
    account_level integer,
    is_leaf smallint,
    is_cash_account smallint,
    is_qc_required character varying(255),
    is_qc_required_quantity smallint,
    status character varying(255),
    cash_flow_item character varying(255),
    auxiliary_accounting character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255)
);
    ALTER SEQUENCE public.account_account_id_seq OWNED BY public.account.account_id;
    ALTER TABLE ONLY public.account
    ADD CONSTRAINT account_pkey PRIMARY KEY (account_id);
    ALTER TABLE ONLY public.account ALTER COLUMN account_id SET DEFAULT nextval('public.account_account_id_seq'::regclass);
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='account_balance') THEN
    CREATE SEQUENCE IF NOT EXISTS public.account_balance_balance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.account_balance (
    balance_id bigint NOT NULL,
    period_year integer,
    period_month integer,
    account_id bigint,
    account_code character varying(255),
    account_name character varying(255),
    account_direction character varying(255),
    begin_debit_balance numeric(18,2),
    begin_credit_balance numeric(18,2),
    current_debit numeric(18,2),
    current_credit numeric(18,2),
    end_debit_balance numeric(18,2),
    end_credit_balance numeric(18,2),
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(50),
    updated_by character varying(50)
);
    ALTER SEQUENCE public.account_balance_balance_id_seq OWNED BY public.account_balance.balance_id;
    ALTER TABLE ONLY public.account_balance
    ADD CONSTRAINT account_balance_pkey PRIMARY KEY (balance_id);
    ALTER TABLE ONLY public.account_balance ALTER COLUMN balance_id SET DEFAULT nextval('public.account_balance_balance_id_seq'::regclass);
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='account_payable') THEN
    CREATE SEQUENCE IF NOT EXISTS public.account_payable_ap_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.account_payable (
    ap_id bigint NOT NULL,
    ap_no character varying(255),
    ap_date date,
    supplier_id bigint,
    supplier_name character varying(255),
    currency_id bigint,
    exchange_rate numeric(18,2),
    total_amount numeric(18,2),
    tax_amount numeric(18,2),
    net_amount numeric(18,2),
    paid_amount numeric(18,2),
    balance_amount numeric(18,2),
    due_date date,
    payment_term_id bigint,
    source_bill_type character varying(255),
    source_bill_no character varying(255),
    invoice_no character varying(255),
    buyer_id bigint,
    department_id bigint,
    is_auto_gen smallint,
    is_closed smallint,
    closed_by character varying(255),
    closed_at timestamp without time zone,
    closed_reason character varying(255),
    remark character varying(255),
    attachment_urls character varying(255),
    status character varying(255),
    approval_status character varying(255),
    approved_by character varying(255),
    approved_at timestamp without time zone,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    org_code character varying(255),
    close_status smallint DEFAULT 0 NOT NULL,
    bill_before_status smallint,
    voided_by character varying(50),
    voided_at timestamp without time zone,
    void_reason character varying(500)
);
    ALTER TABLE ONLY public.account_payable
    ADD CONSTRAINT account_payable_pkey PRIMARY KEY (ap_id);
    ALTER TABLE ONLY public.account_payable ALTER COLUMN ap_id SET DEFAULT nextval('public.account_payable_ap_id_seq'::regclass);
    COMMENT ON COLUMN public.account_payable.closed_by IS '结案人';
    COMMENT ON COLUMN public.account_payable.closed_at IS '结案时间';
    COMMENT ON COLUMN public.account_payable.closed_reason IS '结案原因';
    COMMENT ON COLUMN public.account_payable.close_status IS '结案状态 0=未结案 1=已结案';
    COMMENT ON COLUMN public.account_payable.bill_before_status IS '作废前状态（反作废恢复用）';
    COMMENT ON COLUMN public.account_payable.voided_by IS '作废人';
    COMMENT ON COLUMN public.account_payable.voided_at IS '作废时间';
    COMMENT ON COLUMN public.account_payable.void_reason IS '作废原因';
    ALTER SEQUENCE public.account_payable_ap_id_seq OWNED BY public.account_payable.ap_id;
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='account_receivable') THEN
    CREATE SEQUENCE IF NOT EXISTS public.account_receivable_ar_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.account_receivable (
    ar_id bigint NOT NULL,
    ar_no character varying(255),
    ar_date date,
    customer_id bigint,
    customer_name character varying(255),
    currency_id bigint,
    exchange_rate numeric(18,2),
    total_amount numeric(18,2),
    tax_amount numeric(18,2),
    net_amount numeric(18,2),
    paid_amount numeric(18,2),
    balance_amount numeric(18,2),
    due_date date,
    payment_term_id bigint,
    source_bill_type character varying(255),
    source_bill_no character varying(255),
    invoice_no character varying(255),
    saler_id bigint,
    department_id bigint,
    is_auto_gen smallint,
    is_closed smallint,
    closed_by character varying(255),
    closed_at timestamp without time zone,
    closed_reason character varying(255),
    remark character varying(255),
    attachment_urls character varying(255),
    status character varying(255),
    approval_status character varying(255),
    approved_by character varying(255),
    approved_at timestamp without time zone,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    org_code character varying(255),
    close_status smallint DEFAULT 0 NOT NULL,
    bill_before_status smallint,
    voided_by character varying(50),
    voided_at timestamp without time zone,
    void_reason character varying(500)
);
    ALTER TABLE ONLY public.account_receivable
    ADD CONSTRAINT account_receivable_pkey PRIMARY KEY (ar_id);
    ALTER TABLE ONLY public.account_receivable ALTER COLUMN ar_id SET DEFAULT nextval('public.account_receivable_ar_id_seq'::regclass);
    COMMENT ON COLUMN public.account_receivable.closed_by IS '结案人';
    COMMENT ON COLUMN public.account_receivable.closed_at IS '结案时间';
    COMMENT ON COLUMN public.account_receivable.closed_reason IS '结案原因';
    COMMENT ON COLUMN public.account_receivable.close_status IS '结案状态 0=未结案 1=已结案';
    COMMENT ON COLUMN public.account_receivable.bill_before_status IS '作废前状态（反作废恢复用）';
    COMMENT ON COLUMN public.account_receivable.voided_by IS '作废人';
    COMMENT ON COLUMN public.account_receivable.voided_at IS '作废时间';
    COMMENT ON COLUMN public.account_receivable.void_reason IS '作废原因';
    ALTER SEQUENCE public.account_receivable_ar_id_seq OWNED BY public.account_receivable.ar_id;
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='expense_type') THEN
    CREATE SEQUENCE IF NOT EXISTS public.expense_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.expense_type (
    id bigint NOT NULL,
    type_code character varying(255),
    type_name character varying(255),
    expense_category character varying(255),
    cost_sharing_method character varying(255),
    status integer,
    remark character varying(255),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by character varying(50),
    updated_by character varying(50)
);
    ALTER SEQUENCE public.expense_type_id_seq OWNED BY public.expense_type.id;
    ALTER TABLE ONLY public.expense_type
    ADD CONSTRAINT expense_type_pkey PRIMARY KEY (id);
    ALTER TABLE ONLY public.expense_type ALTER COLUMN id SET DEFAULT nextval('public.expense_type_id_seq'::regclass);
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='payment_term') THEN
    CREATE SEQUENCE IF NOT EXISTS public.payment_term_payment_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.payment_term (
    payment_term_id bigint NOT NULL,
    payment_term_code character varying(255),
    payment_term_name character varying(255),
    payment_days integer,
    discount_days integer,
    status character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    discount_rate numeric(8,4) DEFAULT 0.0000,
    advance_rate numeric(8,4) DEFAULT 0.0000
);
    ALTER SEQUENCE public.payment_term_payment_term_id_seq OWNED BY public.payment_term.payment_term_id;
    ALTER TABLE ONLY public.payment_term
    ADD CONSTRAINT payment_term_pkey PRIMARY KEY (payment_term_id);
    ALTER TABLE ONLY public.payment_term ALTER COLUMN payment_term_id SET DEFAULT nextval('public.payment_term_payment_term_id_seq'::regclass);
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='voucher') THEN
    CREATE SEQUENCE IF NOT EXISTS public.voucher_voucher_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.voucher (
    voucher_id bigint NOT NULL,
    voucher_no character varying(255),
    voucher_date date,
    voucher_type character varying(255),
    period_year integer,
    period_month integer,
    total_debit numeric(18,2),
    total_credit numeric(18,2),
    attachment_count integer,
    source_bill_type character varying(255),
    source_bill_no character varying(255),
    maker_id bigint,
    maker_name character varying(255),
    poster_id bigint,
    poster_name character varying(255),
    checker_id bigint,
    checker_name character varying(255),
    cashier_id bigint,
    cashier_name character varying(255),
    is_reversed smallint,
    reversed_voucher_id bigint,
    reversed_voucher_no character varying(255),
    reverse_voucher_id bigint,
    reverse_voucher_no character varying(255),
    remark character varying(255),
    attachment_urls character varying(255),
    status character varying(255),
    approval_status character varying(255),
    approved_by character varying(255),
    approved_at timestamp without time zone,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    org_code character varying(255)
);
    ALTER SEQUENCE public.voucher_voucher_id_seq OWNED BY public.voucher.voucher_id;
    ALTER TABLE ONLY public.voucher
    ADD CONSTRAINT voucher_pkey PRIMARY KEY (voucher_id);
    ALTER TABLE ONLY public.voucher ALTER COLUMN voucher_id SET DEFAULT nextval('public.voucher_voucher_id_seq'::regclass);
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='voucher_entry') THEN
    CREATE SEQUENCE IF NOT EXISTS public.voucher_entry_entry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.voucher_entry (
    entry_id bigint NOT NULL,
    voucher_id bigint,
    line_no integer,
    account_id bigint,
    account_code character varying(255),
    account_name character varying(255),
    account_direction character varying(255),
    debit_amount numeric(18,2),
    credit_amount numeric(18,2),
    quantity numeric(18,2),
    unit_price numeric(18,2),
    currency_id bigint,
    exchange_rate numeric(18,2),
    foreign_amount numeric(18,2),
    auxiliary_type character varying(255),
    auxiliary_id bigint,
    auxiliary_name character varying(255),
    settlement_no character varying(255),
    person_name character varying(255),
    department_name character varying(255),
    project_name character varying(255),
    customer_name character varying(255),
    supplier_name character varying(255),
    summary character varying(255),
    remark character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255)
);
    ALTER SEQUENCE public.voucher_entry_entry_id_seq OWNED BY public.voucher_entry.entry_id;
    ALTER TABLE ONLY public.voucher_entry
    ADD CONSTRAINT voucher_entry_pkey PRIMARY KEY (entry_id);
    ALTER TABLE ONLY public.voucher_entry ALTER COLUMN entry_id SET DEFAULT nextval('public.voucher_entry_entry_id_seq'::regclass);
  END IF;
END $f$;

DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='voucher_template') THEN
    CREATE SEQUENCE IF NOT EXISTS public.voucher_template_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
    CREATE TABLE IF NOT EXISTS public.voucher_template (
    template_id bigint NOT NULL,
    template_code character varying(255),
    template_name character varying(255),
    source_bill_type character varying(255),
    voucher_type character varying(255),
    is_auto_create smallint,
    template_content character varying(255),
    sort_order integer,
    status character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255)
);
    ALTER SEQUENCE public.voucher_template_template_id_seq OWNED BY public.voucher_template.template_id;
    ALTER TABLE ONLY public.voucher_template
    ADD CONSTRAINT voucher_template_pkey PRIMARY KEY (template_id);
    ALTER TABLE ONLY public.voucher_template ALTER COLUMN template_id SET DEFAULT nextval('public.voucher_template_template_id_seq'::regclass);
  END IF;
END $f$;

-- (9) sale_order_item warehouse/bin/batch columns (new feature this release, idempotent)
DO $f$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='sale_order_item' AND column_name='warehouse_id') THEN
    ALTER TABLE sale_order_item ADD COLUMN warehouse_id BIGINT;
    COMMENT ON COLUMN sale_order_item.warehouse_id IS 'warehouse id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='sale_order_item' AND column_name='bin_id') THEN
    ALTER TABLE sale_order_item ADD COLUMN bin_id BIGINT;
    COMMENT ON COLUMN sale_order_item.bin_id IS 'bin/storage location id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='sale_order_item' AND column_name='batch_no') THEN
    ALTER TABLE sale_order_item ADD COLUMN batch_no VARCHAR(50);
    COMMENT ON COLUMN sale_order_item.batch_no IS 'batch no';
  END IF;
END $f$;
