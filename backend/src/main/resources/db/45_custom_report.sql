-- ============================================================
-- 45_custom_report.sql
-- 自定义报表模块（批次1：明细模式 + 主从关联 + 基础资料关联）
-- 表：custom_report（报表定义，config_json 存列/筛选/排序配置）
-- 幂等脚本：CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS
-- 生成时间：2026-08-27
-- ============================================================

CREATE TABLE IF NOT EXISTS custom_report (
    report_id       BIGSERIAL PRIMARY KEY,
    report_code     VARCHAR(50)  NOT NULL,
    report_name     VARCHAR(100) NOT NULL,
    data_source     VARCHAR(100) NOT NULL,
    config_json     TEXT,
    status          VARCHAR(20)  DEFAULT '启用',
    share_scope     VARCHAR(20)  DEFAULT '私有',
    share_roles     VARCHAR(500),
    created_by      VARCHAR(50),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    remark          VARCHAR(500)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_report_code ON custom_report (report_code);
CREATE INDEX IF NOT EXISTS idx_custom_report_creator ON custom_report (created_by);

COMMENT ON TABLE custom_report IS '自定义报表定义表';
COMMENT ON COLUMN custom_report.report_code IS '报表编码（CR+日期+流水，自动生成）';
COMMENT ON COLUMN custom_report.data_source IS '数据源key（后端白名单注册表，如 sale_order_item）';
COMMENT ON COLUMN custom_report.config_json IS '报表配置JSON（列/筛选字段/排序，后端白名单校验）';
COMMENT ON COLUMN custom_report.share_scope IS '共享范围：私有/共享';
COMMENT ON COLUMN custom_report.share_roles IS '共享角色编码，逗号分隔（share_scope=共享时生效）';
