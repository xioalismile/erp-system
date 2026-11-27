-- 177: 化妆品批记录完整闭环 DDL（2026-11-05 立项，设计文档 .trae/documents/化妆品批记录完整闭环_立项登记表与设计文档.md）
-- 幂等：CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS，可重复执行。
-- 镜像落盘：backend/src/main/resources/db/177_batch_record_schema.sql
-- 启动自检：SchemaUpgradeEnsurer.ensureBatchRecordTables 对主库+每个租户库执行同份内容。
-- 侦察结论（2026-11-05）：material_requisition_item / purchase_inbound_item / production_receipt_item / sale_delivery_item
--   均已存在 batch_no 列，全链批号数据基础现成，本脚本不动任何明细表。

BEGIN;

-- ========== 1. 放行单 ==========
CREATE TABLE IF NOT EXISTS release_order (
    release_id     BIGSERIAL PRIMARY KEY,
    release_no     VARCHAR(64) UNIQUE,
    batch_id       BIGINT NOT NULL,
    batch_no       VARCHAR(64),
    material_id    BIGINT,
    material_code  VARCHAR(64),
    material_name  VARCHAR(128),
    work_order_id  BIGINT,
    work_order_no  VARCHAR(64),
    qc_record_id   BIGINT,
    release_result VARCHAR(20) DEFAULT '放行',
    released_by    VARCHAR(64),
    released_at    TIMESTAMP,
    status         VARCHAR(20) DEFAULT '草稿',
    approval_status VARCHAR(20) DEFAULT '无',
    bill_before_status INTEGER,
    close_status   INTEGER DEFAULT 0,
    approved_by    VARCHAR(64),
    approved_at    TIMESTAMP,
    closed_by      VARCHAR(64),
    closed_at      TIMESTAMP,
    closed_reason  VARCHAR(255),
    voided_by      VARCHAR(64),
    voided_at      TIMESTAMP,
    void_reason    VARCHAR(255),
    remark         VARCHAR(255),
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW(),
    created_by     VARCHAR(64),
    updated_by     VARCHAR(64)
);
COMMENT ON TABLE release_order IS '放行单：成品批次检验合格后由质量负责人签发放行，接入统一状态机';

-- ========== 2. 留样登记 ==========
CREATE TABLE IF NOT EXISTS sample_retention (
    retention_id   BIGSERIAL PRIMARY KEY,
    retention_no   VARCHAR(64) UNIQUE,
    batch_id       BIGINT NOT NULL,
    batch_no       VARCHAR(64),
    material_id    BIGINT,
    material_code  VARCHAR(64),
    material_name  VARCHAR(128),
    retain_qty     NUMERIC(18,4) DEFAULT 0,
    store_location VARCHAR(128),
    retain_by      VARCHAR(64),
    retain_date    DATE,
    keep_until     DATE,
    remind_status  VARCHAR(20) DEFAULT '未到期',
    status         VARCHAR(20) DEFAULT '草稿',
    remark         VARCHAR(255),
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW(),
    created_by     VARCHAR(64),
    updated_by     VARCHAR(64)
);
COMMENT ON TABLE sample_retention IS '留样登记：成品放行时自动生成留样任务，到期进工作台提醒';

-- ========== 3. 批生产记录头 ==========
CREATE TABLE IF NOT EXISTS batch_production_record (
    record_id       BIGSERIAL PRIMARY KEY,
    work_order_id   BIGINT UNIQUE NOT NULL,
    work_order_no   VARCHAR(64),
    product_id      BIGINT,
    product_code    VARCHAR(64),
    product_name    VARCHAR(128),
    batch_no        VARCHAR(64),
    bom_snapshot    JSONB,
    routing_snapshot JSONB,
    env_temperature NUMERIC(8,2),
    env_humidity    NUMERIC(8,2),
    env_recorder    VARCHAR(64),
    env_record_time TIMESTAMP,
    status          VARCHAR(20) DEFAULT '生成中',
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE batch_production_record IS '批生产记录头：以生产工单为单位，工单生效时预生成并固化配方/工艺快照；明细（投料/工序/检验）实时聚合不冗余存储';

-- ========== 4. 记录更正 ==========
CREATE TABLE IF NOT EXISTS record_correction (
    correction_id  BIGSERIAL PRIMARY KEY,
    target_table   VARCHAR(64) NOT NULL,
    target_id      BIGINT NOT NULL,
    target_field   VARCHAR(64),
    old_value      TEXT,
    new_value      TEXT,
    reason         VARCHAR(255) NOT NULL,
    corrected_by   VARCHAR(64),
    corrected_at   TIMESTAMP DEFAULT NOW(),
    signed         SMALLINT DEFAULT 0
);
COMMENT ON TABLE record_correction IS '记录更正：只增不改体系下的唯一纠错通道，必填原因，改前改后永久留痕；原记录保持不变，最新一条更正为当前有效值';

-- ========== 5. 电子签名记录 ==========
CREATE TABLE IF NOT EXISTS signature_record (
    signature_id   BIGSERIAL PRIMARY KEY,
    action_type    VARCHAR(32) NOT NULL,
    target_table   VARCHAR(64),
    target_id      BIGINT,
    target_desc    VARCHAR(255),
    signer_name    VARCHAR(64) NOT NULL,
    sign_time      TIMESTAMP DEFAULT NOW(),
    verify_result  VARCHAR(20) DEFAULT '通过'
);
COMMENT ON TABLE signature_record IS '电子签名记录：检验提交/放行签发/记录更正三处二次密码签名，永久留痕只增不改';

-- ========== 6. 批次档案升级（独立质量状态列，不动现有 status 0~3 库存语义） ==========
ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS supplier_batch_no VARCHAR(64);
ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS qc_status   VARCHAR(20) DEFAULT '合格';
ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS qc_result   VARCHAR(20);
ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS released_by VARCHAR(64);
ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS released_at TIMESTAMP;
COMMENT ON COLUMN batch_info.supplier_batch_no IS '供应商批号（追溯树上游展示）';
COMMENT ON COLUMN batch_info.qc_status IS '批次质量状态：待检/合格/不合格/已放行/已冻结（历史批次回填合格）';
COMMENT ON COLUMN batch_info.qc_result IS '检验结论：合格/不合格/让步接收';
COMMENT ON COLUMN batch_info.released_by IS '放行人（放行单生效时回写）';
COMMENT ON COLUMN batch_info.released_at IS '放行时间（放行单生效时回写）';

-- 存量回填：历史批次一律视为合格不拦出库（幂等：只回填 NULL 行）
UPDATE batch_info SET qc_status = '合格' WHERE qc_status IS NULL;

-- ========== 7. 质检记录扩展（复用现有 qc_record，检验不重建） ==========
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS qc_stage VARCHAR(20) DEFAULT '过程检';
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS batch_id  BIGINT;
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS batch_no  VARCHAR(64);
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS reviewer_id   BIGINT;
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS reviewer_name VARCHAR(64);
COMMENT ON COLUMN qc_record.qc_stage IS '检验环节：来料检/过程检/成品检';
COMMENT ON COLUMN qc_record.batch_id IS '批次档案ID（batch_info.id），批记录链路用';
COMMENT ON COLUMN qc_record.batch_no IS '批号（冗余展示用，与 batch_id 同源）';
COMMENT ON COLUMN qc_record.reviewer_name IS '复核人（电子签名后落值）';

COMMIT;
