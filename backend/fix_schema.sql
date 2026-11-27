-- ======================================================================
-- ERP 后端 API 错误修复脚本 (修订版)
--   1. sale_price_strategy / sale_price_strategy_item             (错误1)
--   2. purchase_price_strategy / purchase_price_strategy_item     (错误2)
--   3. purchase_return 补齐 8 个缺失列                            (错误3)
--   4. sample_order / sample_order_item                          (错误8)
--   5. 重建 subcontract_order / subcontract_order_item (无FK)      (错误6)
--   6. ALTER mrp_plan 重命名列+补齐 (有FK依赖,保留表); 新建 mrp_plan_item (错误7)
-- ======================================================================

-- ---------- 错误1: 销售价格策略 ----------
CREATE TABLE IF NOT EXISTS sale_price_strategy (
    id                   BIGSERIAL PRIMARY KEY,
    strategy_name        VARCHAR(255),
    strategy_code        VARCHAR(255),
    priority             INTEGER,
    customer_level       VARCHAR(255),
    material_category_id BIGINT,
    brand_id             BIGINT,
    start_date           DATE,
    end_date             DATE,
    min_quantity          NUMERIC(18,4),
    max_quantity          NUMERIC(18,4),
    price_type           VARCHAR(50),
    price_value          NUMERIC(18,4),
    status               INTEGER,
    remark               VARCHAR(500),
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS sale_price_strategy_item (
    id          BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT,
    material_id BIGINT,
    price_value NUMERIC(18,4),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

-- ---------- 错误2: 采购价格策略 ----------
CREATE TABLE IF NOT EXISTS purchase_price_strategy (
    id                   BIGSERIAL PRIMARY KEY,
    strategy_name        VARCHAR(255),
    strategy_code        VARCHAR(255),
    priority             INTEGER,
    supplier_level       VARCHAR(255),
    material_category_id BIGINT,
    brand_id             BIGINT,
    start_date           DATE,
    end_date             DATE,
    min_quantity          NUMERIC(18,4),
    max_quantity          NUMERIC(18,4),
    price_type           VARCHAR(50),
    price_value          NUMERIC(18,4),
    status               INTEGER,
    remark               VARCHAR(500),
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS purchase_price_strategy_item (
    id          BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT,
    material_id BIGINT,
    price_value NUMERIC(18,4),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

-- ---------- 错误3: purchase_return 补齐缺失列 ----------
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS is_closed          INTEGER;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS close_status        INTEGER;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS closed_by          VARCHAR(255);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS closed_at          TIMESTAMP;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS closed_reason      VARCHAR(500);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS base_total_amount  NUMERIC(18,4);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS base_tax_amount   NUMERIC(18,4);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS base_net_amount    NUMERIC(18,4);

-- ---------- 打样单进度字段 ----------
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS sample_progress VARCHAR(255);

-- ---------- 错误8: 打样单 ----------
CREATE TABLE IF NOT EXISTS sample_order (
    sample_id          BIGSERIAL PRIMARY KEY,
    sample_no          VARCHAR(255),
    sample_name        VARCHAR(255),
    sample_type        VARCHAR(50),
    sample_stage       VARCHAR(50),
    customer_id        BIGINT,
    customer_name      VARCHAR(255),
    contact_person     VARCHAR(255),
    contact_phone      VARCHAR(255),
    material_id        BIGINT,
    material_code      VARCHAR(255),
    material_name      VARCHAR(255),
    specification      VARCHAR(500),
    unit_id            BIGINT,
    sample_qty         NUMERIC(18,4),
    demand_date        DATE,
    plan_finish_date   DATE,
    actual_finish_date DATE,
    department_id      BIGINT,
    engineer_id        BIGINT,
    priority           VARCHAR(50),
    cost_amount        NUMERIC(18,4),
    status             VARCHAR(50),
    is_bom_generated   SMALLINT,
    generated_bom_id   BIGINT,
    remark             VARCHAR(500),
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    org_code           VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS sample_order_item (
    item_id              BIGSERIAL PRIMARY KEY,
    sample_id            BIGINT,
    line_no              INTEGER,
    child_material_id    BIGINT,
    child_material_code  VARCHAR(255),
    child_material_name  VARCHAR(255),
    specification        VARCHAR(500),
    unit_id              BIGINT,
    base_qty             NUMERIC(18,4),
    qty_type             VARCHAR(50),
    scrap_rate           NUMERIC(18,4),
    supply_type          VARCHAR(50),
    is_key_part          SMALLINT,
    unit_price           NUMERIC(18,4),
    amount               NUMERIC(18,4),
    remark               VARCHAR(500),
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255)
);

-- ---------- 错误6: 委外加工单 (实体与旧表不匹配, 无FK, 重建) ----------
DROP TABLE IF EXISTS subcontract_order_item;
DROP TABLE IF EXISTS subcontract_order;
CREATE TABLE subcontract_order (
    id                BIGSERIAL PRIMARY KEY,
    order_no          VARCHAR(255),
    supplier_id       BIGINT,
    supplier_name     VARCHAR(255),
    process_item      VARCHAR(255),
    order_date        DATE,
    expect_date       DATE,
    actual_date       DATE,
    total_quantity    NUMERIC(18,4),
    finished_quantity NUMERIC(18,4),
    unit_price        NUMERIC(18,4),
    total_amount      NUMERIC(18,4),
    tax_rate          NUMERIC(18,4),
    tax_amount        NUMERIC(18,4),
    net_amount        NUMERIC(18,4),
    currency_code     VARCHAR(50),
    exchange_rate     NUMERIC(18,4),
    base_total_amount NUMERIC(18,4),
    base_tax_amount   NUMERIC(18,4),
    base_net_amount   NUMERIC(18,4),
    source_bill_type  VARCHAR(50),
    source_bill_no    VARCHAR(50),
    status            INTEGER,
    close_status      INTEGER,
    remark            VARCHAR(500),
    create_by         VARCHAR(255),
    create_time       TIMESTAMP,
    update_time       TIMESTAMP
);
CREATE TABLE subcontract_order_item (
    id                   BIGSERIAL PRIMARY KEY,
    order_id             BIGINT,
    material_id          BIGINT,
    material_code        VARCHAR(255),
    material_name        VARCHAR(255),
    specification        VARCHAR(500),
    unit                 VARCHAR(50),
    quantity             NUMERIC(18,4),
    finished_qty         NUMERIC(18,4),
    actual_qty           NUMERIC(18,4) DEFAULT 0,
    unit_price           NUMERIC(18,4),
    amount               NUMERIC(18,4),
    tax_rate             NUMERIC(18,4),
    tax_amount           NUMERIC(18,4),
    net_amount           NUMERIC(18,4),
    bom_id               BIGINT,
    remark               VARCHAR(500),
    detail_close_status  INTEGER,
    create_time          TIMESTAMP,
    update_time          TIMESTAMP
);

-- ---------- 错误7: MRP运算方案 ----------
-- mrp_plan 被 mrp_demand/mrp_supply 外键依赖, 不能 DROP, 改用 ALTER 重命名列+补齐
-- 注: 原 plan_status 为 varchar(存中文"草稿"等), 实体为 Integer, 需重建为 integer 列
ALTER TABLE mrp_plan RENAME COLUMN plan_id    TO id;
ALTER TABLE mrp_plan RENAME COLUMN plan_code  TO plan_no;
ALTER TABLE mrp_plan RENAME COLUMN created_at TO create_time;
ALTER TABLE mrp_plan RENAME COLUMN updated_at TO update_time;
ALTER TABLE mrp_plan RENAME COLUMN created_by TO create_by;
ALTER TABLE mrp_plan DROP COLUMN IF EXISTS plan_status;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS plan_status INTEGER;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS demand_order_ids           VARCHAR(500);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS total_net_demand           NUMERIC(18,4);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS total_purchase_suggestion  NUMERIC(18,4);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS total_produce_suggestion   NUMERIC(18,4);
-- mrp_plan_item 表原本不存在, 新建
CREATE TABLE IF NOT EXISTS mrp_plan_item (
    id                BIGSERIAL PRIMARY KEY,
    plan_id           BIGINT,
    material_id       BIGINT,
    material_code     VARCHAR(255),
    material_name     VARCHAR(255),
    specification     VARCHAR(500),
    unit              VARCHAR(50),
    gross_demand      NUMERIC(18,4),
    available_stock   NUMERIC(18,4),
    safety_stock      NUMERIC(18,4),
    net_demand        NUMERIC(18,4),
    plan_quantity     NUMERIC(18,4),
    suggest_type      VARCHAR(50),
    lead_time         INTEGER,
    suggest_date      DATE,
    related_bom_id    BIGINT,
    level             INTEGER,
    parent_id         BIGINT,
    remark            VARCHAR(500),
    create_time       TIMESTAMP,
    update_time       TIMESTAMP
);
