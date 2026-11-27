-- ===========================================================================
-- 企查查/天眼查/国家企业信用信息公示系统 采集模块 - PostgreSQL 建表脚本
-- 包含2张表：
--   1. acquisition_task     采集任务表（定义采集计划/任务/定时调度）
--   2. acquisition_result   采集结果表（保存抓取到的企业信息及AI识别结果）
-- 任务编号格式：ACQ-yyyyMMdd-0001
-- 任务状态：pending(待执行) / running(执行中) / completed(已完成) / failed(失败) / paused(已暂停)
-- 审核状态：pending(待审核) / approved(已通过) / rejected(已驳回) / imported(已导入线索)
-- ===========================================================================

-- 1. 采集任务表
DROP TABLE IF EXISTS acquisition_result CASCADE;
DROP TABLE IF EXISTS acquisition_task CASCADE;

CREATE TABLE acquisition_task (
    task_id           BIGSERIAL    PRIMARY KEY,
    task_no           VARCHAR(50)  NOT NULL,
    task_name         VARCHAR(200) NOT NULL,
    platform          VARCHAR(50)  NOT NULL,
    keywords          TEXT,
    industry          VARCHAR(100),
    area              VARCHAR(100),
    max_pages         INT          NOT NULL DEFAULT 5,
    max_companies     INT          NOT NULL DEFAULT 50,
    status            VARCHAR(20)  NOT NULL DEFAULT 'pending',
    login_cookie      TEXT,
    last_run_time     TIMESTAMP,
    next_run_time     TIMESTAMP,
    cron_expression   VARCHAR(50),
    captured_count    INT          NOT NULL DEFAULT 0,
    imported_count    INT          NOT NULL DEFAULT 0,
    error_message     TEXT,
    creator_id        BIGINT,
    create_date       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id       BIGINT,
    modify_date       TIMESTAMP,
    deleted           INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_acquisition_task_no UNIQUE (task_no)
);

-- 索引：按任务编号查询
CREATE INDEX IF NOT EXISTS idx_acquisition_task_no        ON acquisition_task(task_no);
-- 索引：按平台查询
CREATE INDEX IF NOT EXISTS idx_acquisition_task_platform  ON acquisition_task(platform);
-- 索引：按任务状态查询
CREATE INDEX IF NOT EXISTS idx_acquisition_task_status    ON acquisition_task(status);
-- 索引：按下次执行时间查询（定时调度）
CREATE INDEX IF NOT EXISTS idx_acquisition_task_next_run   ON acquisition_task(next_run_time);
-- 索引：按创建人查询
CREATE INDEX IF NOT EXISTS idx_acquisition_task_creator    ON acquisition_task(creator_id);
-- 索引：逻辑删除过滤
CREATE INDEX IF NOT EXISTS idx_acquisition_task_deleted   ON acquisition_task(deleted);

COMMENT ON TABLE  acquisition_task IS '采集任务表（定义采集计划/任务/定时调度）';
COMMENT ON COLUMN acquisition_task.task_id         IS '任务ID（自增主键）';
COMMENT ON COLUMN acquisition_task.task_no         IS '任务编号（唯一，格式：ACQ-yyyyMMdd-0001）';
COMMENT ON COLUMN acquisition_task.task_name       IS '任务名称';
COMMENT ON COLUMN acquisition_task.platform        IS '采集平台：qichacha(企查查) / tianyancha(天眼查) / gsxt(国家企业信用信息公示系统)';
COMMENT ON COLUMN acquisition_task.keywords       IS '搜索关键词（JSON数组，如 ["制造业","深圳","ERP软件"]）';
COMMENT ON COLUMN acquisition_task.industry        IS '行业筛选';
COMMENT ON COLUMN acquisition_task.area            IS '地区筛选';
COMMENT ON COLUMN acquisition_task.max_pages       IS '最大采集页数（默认5）';
COMMENT ON COLUMN acquisition_task.max_companies   IS '最大采集企业数（默认50）';
COMMENT ON COLUMN acquisition_task.status          IS '任务状态：pending(待执行) / running(执行中) / completed(已完成) / failed(失败) / paused(已暂停)';
COMMENT ON COLUMN acquisition_task.login_cookie    IS '企查查登录Cookie（JSON格式，用于鉴权抓取）';
COMMENT ON COLUMN acquisition_task.last_run_time   IS '最后执行时间';
COMMENT ON COLUMN acquisition_task.next_run_time   IS '下次执行时间（定时调度使用）';
COMMENT ON COLUMN acquisition_task.cron_expression IS '定时表达式（可选，为空表示手动触发）';
COMMENT ON COLUMN acquisition_task.captured_count  IS '已采集企业数';
COMMENT ON COLUMN acquisition_task.imported_count  IS '已导入线索数';
COMMENT ON COLUMN acquisition_task.error_message   IS '错误信息（任务失败时记录）';
COMMENT ON COLUMN acquisition_task.creator_id      IS '创建人ID';
COMMENT ON COLUMN acquisition_task.create_date     IS '创建时间';
COMMENT ON COLUMN acquisition_task.modifier_id     IS '修改人ID';
COMMENT ON COLUMN acquisition_task.modify_date     IS '修改时间';
COMMENT ON COLUMN acquisition_task.deleted         IS '逻辑删除标志：0未删除 1已删除';


-- 2. 采集结果表
CREATE TABLE acquisition_result (
    result_id          BIGSERIAL    PRIMARY KEY,
    task_id            BIGINT       NOT NULL,
    company_name       VARCHAR(200),
    credit_code        VARCHAR(50),
    legal_person       VARCHAR(50),
    registered_capital VARCHAR(100),
    contact_phone      VARCHAR(50),
    contact_email      VARCHAR(100),
    registered_address VARCHAR(500),
    business_scope     TEXT,
    industry           VARCHAR(100),
    establish_date     VARCHAR(20),
    company_status     VARCHAR(50),
    screenshot_path    VARCHAR(500),
    source_url         VARCHAR(500),
    ai_raw_result      TEXT,
    review_status      VARCHAR(20)  NOT NULL DEFAULT 'pending',
    reviewed_by        BIGINT,
    reviewed_date      TIMESTAMP,
    reject_reason      VARCHAR(500),
    imported_lead_id   BIGINT,
    create_date        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            INT          NOT NULL DEFAULT 0
);

-- 索引：按任务ID查询（最常用）
CREATE INDEX IF NOT EXISTS idx_acquisition_result_task_id       ON acquisition_result(task_id);
-- 索引：按公司名称查询
CREATE INDEX IF NOT EXISTS idx_acquisition_result_company_name  ON acquisition_result(company_name);
-- 索引：按统一社会信用代码查询
CREATE INDEX IF NOT EXISTS idx_acquisition_result_credit_code   ON acquisition_result(credit_code);
-- 索引：按审核状态查询
CREATE INDEX IF NOT EXISTS idx_acquisition_result_review_status ON acquisition_result(review_status);
-- 索引：按已导入的线索ID查询
CREATE INDEX IF NOT EXISTS idx_acquisition_result_lead_id      ON acquisition_result(imported_lead_id);
-- 索引：逻辑删除过滤
CREATE INDEX IF NOT EXISTS idx_acquisition_result_deleted      ON acquisition_result(deleted);
-- 索引：按创建时间倒序查询
CREATE INDEX IF NOT EXISTS idx_acquisition_result_create_date  ON acquisition_result(create_date);

COMMENT ON TABLE  acquisition_result IS '采集结果表（保存抓取到的企业信息及AI识别结果）';
COMMENT ON COLUMN acquisition_result.result_id          IS '结果ID（自增主键）';
COMMENT ON COLUMN acquisition_result.task_id            IS '关联采集任务ID（关联 acquisition_task.task_id）';
COMMENT ON COLUMN acquisition_result.company_name       IS '公司名称';
COMMENT ON COLUMN acquisition_result.credit_code       IS '统一社会信用代码';
COMMENT ON COLUMN acquisition_result.legal_person     IS '法人代表';
COMMENT ON COLUMN acquisition_result.registered_capital IS '注册资本';
COMMENT ON COLUMN acquisition_result.contact_phone      IS '联系电话';
COMMENT ON COLUMN acquisition_result.contact_email     IS '联系邮箱';
COMMENT ON COLUMN acquisition_result.registered_address IS '注册地址';
COMMENT ON COLUMN acquisition_result.business_scope     IS '经营范围';
COMMENT ON COLUMN acquisition_result.industry           IS '行业分类';
COMMENT ON COLUMN acquisition_result.establish_date    IS '成立日期（字符串保存，兼容不同来源格式）';
COMMENT ON COLUMN acquisition_result.company_status    IS '企业状态（存续/注销/吊销等）';
COMMENT ON COLUMN acquisition_result.screenshot_path    IS '截图文件路径（JSON数组，可保存多张截图）';
COMMENT ON COLUMN acquisition_result.source_url        IS '来源页面URL';
COMMENT ON COLUMN acquisition_result.ai_raw_result     IS 'AI识别原始结果（JSON格式，包含解析出的字段）';
COMMENT ON COLUMN acquisition_result.review_status     IS '审核状态：pending(待审核) / approved(已通过) / rejected(已驳回) / imported(已导入线索)';
COMMENT ON COLUMN acquisition_result.reviewed_by      IS '审核人ID';
COMMENT ON COLUMN acquisition_result.reviewed_date    IS '审核时间';
COMMENT ON COLUMN acquisition_result.reject_reason     IS '驳回原因';
COMMENT ON COLUMN acquisition_result.imported_lead_id  IS '已导入的线索ID（关联线索表，审核通过后导入）';
COMMENT ON COLUMN acquisition_result.create_date       IS '创建时间';
COMMENT ON COLUMN acquisition_result.deleted           IS '逻辑删除标志：0未删除 1已删除';
