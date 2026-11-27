-- ===========================================================================
-- 版本回滚功能 - 数据库表结构
-- 用途: 记录所有业务表数据变更的前/后快照, 支持按版本号回滚
-- 适用: 基础资料 + 所有业务单据模块
-- ===========================================================================

-- 版本记录表
CREATE TABLE IF NOT EXISTS data_version_record (
    version_id        BIGSERIAL PRIMARY KEY,
    table_name        VARCHAR(100)  NOT NULL,                    -- 被修改的表名(如 department, material, sale_order)
    record_id         BIGINT        NOT NULL,                    -- 被修改的记录主键
    record_code       VARCHAR(50),                               -- 业务编码(便于检索, 如 dept_code)
    business_module   VARCHAR(50)   NOT NULL,                    -- 业务模块: basedata/sale/purchase/inventory/production/crm/finance/sample
    operation_type    VARCHAR(20)   NOT NULL,                    -- 操作类型: INSERT/UPDATE/DELETE
    before_snapshot   TEXT,                                      -- 修改前JSON快照(INSERT时为NULL)
    after_snapshot    TEXT,                                      -- 修改后JSON快照(DELETE时为NULL)
    version_number    BIGINT        NOT NULL,                    -- 该记录的第几个版本(同table_name+record_id递增)
    operator          VARCHAR(50),                               -- 操作人
    operate_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rolled_back       SMALLINT      NOT NULL DEFAULT 0,          -- 是否已被回滚(0=否, 1=是)
    rollback_enabled  SMALLINT      NOT NULL DEFAULT 1,          -- 是否允许回滚(0=否, 1=是)
    remark            VARCHAR(500),                              -- 备注
    CONSTRAINT uk_version UNIQUE (table_name, record_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_dvr_table_record ON data_version_record(table_name, record_id);
CREATE INDEX IF NOT EXISTS idx_dvr_module       ON data_version_record(business_module);
CREATE INDEX IF NOT EXISTS idx_dvr_operate_time ON data_version_record(operate_time);

COMMENT ON TABLE  data_version_record IS '数据版本记录表 - 记录所有业务表变更, 支持回滚';
COMMENT ON COLUMN data_version_record.table_name       IS '被修改的表名';
COMMENT ON COLUMN data_version_record.record_id        IS '被修改的记录主键';
COMMENT ON COLUMN data_version_record.record_code      IS '业务编码(便于检索)';
COMMENT ON COLUMN data_version_record.business_module  IS '业务模块: basedata/sale/purchase/inventory/production/crm/finance/sample';
COMMENT ON COLUMN data_version_record.operation_type   IS '操作类型: INSERT/UPDATE/DELETE';
COMMENT ON COLUMN data_version_record.before_snapshot  IS '修改前JSON快照(INSERT时为NULL)';
COMMENT ON COLUMN data_version_record.after_snapshot   IS '修改后JSON快照(DELETE时为NULL)';
COMMENT ON COLUMN data_version_record.version_number   IS '该记录的第几个版本(同table_name+record_id递增)';
COMMENT ON COLUMN data_version_record.operator         IS '操作人';
COMMENT ON COLUMN data_version_record.rolled_back      IS '是否已被回滚(0=否, 1=是)';
COMMENT ON COLUMN data_version_record.rollback_enabled IS '是否允许回滚(0=否, 1=是)';
