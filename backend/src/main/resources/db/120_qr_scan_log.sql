-- =====================================================
-- 扫码审计日志（2026-09-27，P1 对外发码安全）
-- 用途：记录"谁 / 何时 / 扫了什么码 / 结果"，用于对外发码后的追溯与异常排查
-- 幂等：IF NOT EXISTS，重复执行无副作用
-- 说明：写入失败不影响扫码（后端按 try/catch 降级，仅告警）
-- =====================================================

CREATE TABLE IF NOT EXISTS qr_scan_log (
    id            BIGSERIAL PRIMARY KEY,
    scan_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    user_id       BIGINT,
    username      VARCHAR(64),
    code_type     VARCHAR(16),           -- MAT=商品 / BILL=单据 / OP=工序
    doc_type      VARCHAR(64),           -- 单据类型（仅 BILL，如 SALE_ORDER）
    biz_id        BIGINT,                -- 目标主键
    signed        SMALLINT     DEFAULT 0,-- 是否带签名（1=带）
    result        VARCHAR(16),           -- OK / REJECTED
    reason        VARCHAR(255),          -- 拒绝原因 / 说明
    client        VARCHAR(255),          -- User-Agent
    ip            VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_qr_scan_log_time ON qr_scan_log (scan_time DESC);
CREATE INDEX IF NOT EXISTS idx_qr_scan_log_biz ON qr_scan_log (code_type, doc_type, biz_id);

COMMENT ON TABLE qr_scan_log IS '二维码扫码审计日志（P1 对外发码安全，2026-09-27）';
