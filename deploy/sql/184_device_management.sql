-- ============================================================
-- 184_device_management.sql
-- 用途：设备管理模块（一期全量）—— 建表 + 菜单 + 角色授权 + 字典 + 初始分类 + 预置协议模板
-- 依据：PRD_设备管理模块_协议接入与生产联动.html / 设备管理模块_实施计划.md
--
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
--       INSERT ... WHERE NOT EXISTS；重复执行结果一致。
-- 执行：psql -U erp_user -d erp_db -v ON_ERROR_STOP=1 -f 184_device_management.sql
--       执行后必须重跑 database/schema/create_tenant_template.ps1（否则新租户无表无菜单）
--       菜单授权完成后需重新登录（菜单路由在登录时缓存）
-- ============================================================

BEGIN;
SET client_encoding TO 'UTF8';

-- ============================================================
-- 一、设备对象层
-- ============================================================

-- 1. 设备分类（树）
CREATE TABLE IF NOT EXISTS device_category (
    id             BIGSERIAL PRIMARY KEY,
    category_code  VARCHAR(50)  NOT NULL,
    category_name  VARCHAR(100) NOT NULL,
    parent_id      BIGINT,
    sort_order     INT          NOT NULL DEFAULT 0,
    is_system      SMALLINT     NOT NULL DEFAULT 0,
    status         VARCHAR(10)  NOT NULL DEFAULT '启用',
    remark         VARCHAR(500),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP,
    create_by      VARCHAR(50),
    update_by      VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_category_code ON device_category (category_code);
CREATE INDEX IF NOT EXISTS idx_device_category_parent ON device_category (parent_id);

-- 2. 设备档案
CREATE TABLE IF NOT EXISTS device_info (
    id                    BIGSERIAL PRIMARY KEY,
    device_code           VARCHAR(50)  NOT NULL,
    device_name           VARCHAR(100) NOT NULL,
    category_id           BIGINT       NOT NULL,
    brand                 VARCHAR(100),
    model                 VARCHAR(100),
    serial_no             VARCHAR(100),
    asset_no              VARCHAR(100),
    department_id         BIGINT,
    work_center_id        BIGINT,
    workstation_id        BIGINT,
    allowed_operation_ids TEXT,
    owner_employee_id     BIGINT,
    location              VARCHAR(200),
    purchase_date         DATE,
    warranty_end_date     DATE,
    supplier_id           BIGINT,
    rated_speed           NUMERIC(10,2),
    report_threshold      INT          NOT NULL DEFAULT 50,
    count_merge_mode      VARCHAR(20)  NOT NULL DEFAULT 'SUM',
    auto_start            SMALLINT     NOT NULL DEFAULT 0,
    auto_pause            SMALLINT     NOT NULL DEFAULT 0,
    is_key_device         SMALLINT     NOT NULL DEFAULT 0,
    status                VARCHAR(20)  NOT NULL DEFAULT '启用',
    image_url             VARCHAR(500),
    remark                VARCHAR(500),
    create_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP,
    create_by             VARCHAR(50),
    update_by             VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_code ON device_info (device_code);
CREATE INDEX IF NOT EXISTS idx_device_info_category ON device_info (category_id);
CREATE INDEX IF NOT EXISTS idx_device_info_wc ON device_info (work_center_id);
CREATE INDEX IF NOT EXISTS idx_device_info_status ON device_info (status);

-- ============================================================
-- 二、接入层
-- ============================================================

-- 3. 接入凭证（一设备一凭证，仅存密钥摘要）
CREATE TABLE IF NOT EXISTS device_access_credential (
    id                BIGSERIAL PRIMARY KEY,
    device_id         BIGINT       NOT NULL,
    credential_no     VARCHAR(64)  NOT NULL,
    secret_hash       VARCHAR(128) NOT NULL,
    secret_enc        VARCHAR(500),
    secret_salt       VARCHAR(64),
    status            VARCHAR(20)  NOT NULL DEFAULT '启用',
    last_access_time  TIMESTAMP,
    last_access_ip    VARCHAR(64),
    fail_count        INT          NOT NULL DEFAULT 0,
    remark            VARCHAR(500),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP,
    create_by         VARCHAR(50),
    update_by         VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_credential_no ON device_access_credential (credential_no);
CREATE INDEX IF NOT EXISTS idx_device_credential_device ON device_access_credential (device_id);
-- 存量库补列（2026-11-06 第二期：HMAC 验签需要可解密的密钥）
ALTER TABLE device_access_credential ADD COLUMN IF NOT EXISTS secret_enc VARCHAR(500);

-- 4. 接入通道
CREATE TABLE IF NOT EXISTS device_channel (
    id                  BIGSERIAL PRIMARY KEY,
    device_id           BIGINT      NOT NULL,
    channel_type        VARCHAR(30) NOT NULL,
    channel_name        VARCHAR(100),
    config_json         TEXT,
    heartbeat_interval  INT         NOT NULL DEFAULT 30,
    offline_threshold   INT         NOT NULL DEFAULT 3,
    enabled             SMALLINT    NOT NULL DEFAULT 0,
    last_connected_time TIMESTAMP,
    create_time         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    create_by           VARCHAR(50),
    update_by           VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_device_channel_device ON device_channel (device_id);
CREATE INDEX IF NOT EXISTS idx_device_channel_type ON device_channel (channel_type);

-- 5. 协议模板
CREATE TABLE IF NOT EXISTS device_protocol_template (
    id               BIGSERIAL PRIMARY KEY,
    template_code    VARCHAR(50)  NOT NULL,
    template_name    VARCHAR(100) NOT NULL,
    protocol_type    VARCHAR(30)  NOT NULL,
    is_preset        SMALLINT     NOT NULL DEFAULT 0,
    config_json      TEXT,
    parse_format     VARCHAR(20)  NOT NULL DEFAULT 'JSON',
    byte_order       VARCHAR(10)  NOT NULL DEFAULT 'BIG_ENDIAN',
    word_order       VARCHAR(10)  NOT NULL DEFAULT 'HIGH_FIRST',
    timestamp_source VARCHAR(20)  NOT NULL DEFAULT 'CLOUD',
    status           VARCHAR(10)  NOT NULL DEFAULT '启用',
    remark           VARCHAR(500),
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP,
    create_by        VARCHAR(50),
    update_by        VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_protocol_code ON device_protocol_template (template_code);
CREATE INDEX IF NOT EXISTS idx_device_protocol_type ON device_protocol_template (protocol_type);

-- 6. 数据点
CREATE TABLE IF NOT EXISTS device_point (
    id             BIGSERIAL PRIMARY KEY,
    device_id      BIGINT       NOT NULL,
    template_id    BIGINT,
    point_key      VARCHAR(64)  NOT NULL,
    point_name     VARCHAR(100) NOT NULL,
    purpose        VARCHAR(30)  NOT NULL DEFAULT 'MONITOR',
    address        VARCHAR(200),
    parse_key      VARCHAR(100),
    data_type      VARCHAR(20)  NOT NULL DEFAULT 'DECIMAL',
    unit           VARCHAR(30),
    scale_factor   NUMERIC(18,6) NOT NULL DEFAULT 1,
    scale_offset   NUMERIC(18,6) NOT NULL DEFAULT 0,
    sample_interval INT         NOT NULL DEFAULT 30,
    dead_zone      NUMERIC(18,6),
    writable       SMALLINT     NOT NULL DEFAULT 0,
    alarm_upper    NUMERIC(18,6),
    alarm_lower    NUMERIC(18,6),
    alarm_level    VARCHAR(20)  NOT NULL DEFAULT '警告',
    enabled        SMALLINT     NOT NULL DEFAULT 1,
    remark         VARCHAR(500),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP,
    create_by      VARCHAR(50),
    update_by      VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_point ON device_point (device_id, point_key);
CREATE INDEX IF NOT EXISTS idx_device_point_purpose ON device_point (device_id, purpose);

-- ============================================================
-- 三、数据落地
-- ============================================================

-- 7. 原始报文
CREATE TABLE IF NOT EXISTS device_raw_message (
    id           BIGSERIAL PRIMARY KEY,
    device_id    BIGINT      NOT NULL,
    channel_type VARCHAR(30),
    direction    VARCHAR(10) NOT NULL DEFAULT 'IN',
    message_key  VARCHAR(100),
    raw_content  TEXT,
    parsed_json  TEXT,
    parse_status VARCHAR(20) NOT NULL DEFAULT '成功',
    error_msg    VARCHAR(500),
    received_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    simulated    SMALLINT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_msg_key ON device_raw_message (device_id, message_key);
CREATE INDEX IF NOT EXISTS idx_device_msg_time ON device_raw_message (device_id, received_at);

-- 8. 点位实时值
CREATE TABLE IF NOT EXISTS device_realtime_value (
    id          BIGSERIAL PRIMARY KEY,
    device_id   BIGINT       NOT NULL,
    point_id    BIGINT       NOT NULL,
    point_key   VARCHAR(64),
    value_text  VARCHAR(500),
    value_num   NUMERIC(18,6),
    value_time  TIMESTAMP,
    quality     VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    update_time TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_realtime ON device_realtime_value (device_id, point_id);

-- 9. 设备状态流水
CREATE TABLE IF NOT EXISTS device_status_log (
    id               BIGSERIAL PRIMARY KEY,
    device_id        BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL,
    start_time       TIMESTAMP   NOT NULL,
    end_time         TIMESTAMP,
    duration_seconds BIGINT,
    source           VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    remark           VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_device_status_time ON device_status_log (device_id, start_time);

-- ============================================================
-- 四、关联层
-- ============================================================

-- 10. 设备任务（设备与工单工序的关联）
CREATE TABLE IF NOT EXISTS device_task (
    id               BIGSERIAL PRIMARY KEY,
    task_no          VARCHAR(50) NOT NULL,
    device_id        BIGINT      NOT NULL,
    order_id         BIGINT      NOT NULL,
    order_no         VARCHAR(50),
    operation_row_id BIGINT      NOT NULL,
    operation_name   VARCHAR(100),
    source_type      VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    count_base       NUMERIC(18,4) NOT NULL DEFAULT 0,
    task_status      VARCHAR(20) NOT NULL DEFAULT '待下发',
    release_reason   VARCHAR(20),
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP,
    create_by        VARCHAR(50),
    update_by        VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_task_no ON device_task (task_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_task_running ON device_task (device_id) WHERE task_status = '执行中';
CREATE INDEX IF NOT EXISTS idx_device_task_op ON device_task (operation_row_id);
CREATE INDEX IF NOT EXISTS idx_device_task_status ON device_task (task_status);

-- 11. 未关联数据池
CREATE TABLE IF NOT EXISTS device_unmatched_data (
    id                      BIGSERIAL PRIMARY KEY,
    device_id               BIGINT       NOT NULL,
    period_start            TIMESTAMP    NOT NULL,
    period_end              TIMESTAMP    NOT NULL,
    qualified_count         NUMERIC(18,4) NOT NULL DEFAULT 0,
    rejected_count          NUMERIC(18,4) NOT NULL DEFAULT 0,
    raw_message_id          BIGINT,
    reason                  VARCHAR(500),
    status                  VARCHAR(20)  NOT NULL DEFAULT '未关联',
    claimed_operation_row_id BIGINT,
    claimed_pending_id      BIGINT,
    claimed_by              VARCHAR(50),
    claimed_time            TIMESTAMP,
    create_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_device_unmatched_device ON device_unmatched_data (device_id, period_start);
CREATE INDEX IF NOT EXISTS idx_device_unmatched_status ON device_unmatched_data (status);
-- 阶段 4 补列：认领后生成的设备任务，便于追溯
ALTER TABLE device_unmatched_data ADD COLUMN IF NOT EXISTS claimed_task_id BIGINT;

-- 12. 待确认报工单
CREATE TABLE IF NOT EXISTS device_report_pending (
    id                  BIGSERIAL PRIMARY KEY,
    pending_no          VARCHAR(50)  NOT NULL,
    device_id           BIGINT       NOT NULL,
    device_code         VARCHAR(50),
    device_name         VARCHAR(100),
    order_id            BIGINT,
    order_no            VARCHAR(50),
    operation_row_id    BIGINT       NOT NULL,
    operation_name      VARCHAR(100),
    period_start        TIMESTAMP    NOT NULL,
    period_end          TIMESTAMP    NOT NULL,
    qualified_count     NUMERIC(18,4) NOT NULL DEFAULT 0,
    rejected_count      NUMERIC(18,4) NOT NULL DEFAULT 0,
    run_hours           NUMERIC(10,2) NOT NULL DEFAULT 0,
    count_start         NUMERIC(18,4),
    count_end           NUMERIC(18,4),
    source_type         VARCHAR(20)  NOT NULL DEFAULT 'AUTO',
    status              VARCHAR(20)  NOT NULL DEFAULT '待确认',
    reject_reason       VARCHAR(500),
    confirm_by          VARCHAR(50),
    confirm_time        TIMESTAMP,
    operation_report_id BIGINT,
    operation_report_no VARCHAR(50),
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_pending_no ON device_report_pending (pending_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_pending ON device_report_pending (device_id, operation_row_id, period_start);
CREATE INDEX IF NOT EXISTS idx_device_pending_status ON device_report_pending (status);

-- 13. 报警事件
CREATE TABLE IF NOT EXISTS device_alarm (
    id               BIGSERIAL PRIMARY KEY,
    alarm_no         VARCHAR(50) NOT NULL,
    device_id        BIGINT      NOT NULL,
    point_id         BIGINT,
    alarm_type       VARCHAR(30) NOT NULL,
    level            VARCHAR(20) NOT NULL DEFAULT '警告',
    trigger_value    NUMERIC(18,6),
    threshold_value  NUMERIC(18,6),
    trigger_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recover_time     TIMESTAMP,
    duration_seconds BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT '未处理',
    handler          VARCHAR(50),
    handle_time      TIMESTAMP,
    handle_note      VARCHAR(500),
    repair_order_id  BIGINT,
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_alarm_no ON device_alarm (alarm_no);
CREATE INDEX IF NOT EXISTS idx_device_alarm_device ON device_alarm (device_id, status);
CREATE INDEX IF NOT EXISTS idx_device_alarm_time ON device_alarm (trigger_time);

-- ============================================================
-- 五、运维层
-- ============================================================

-- 14. 点检模板
CREATE TABLE IF NOT EXISTS device_inspection_template (
    id            BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(50)  NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    category_id   BIGINT,
    device_id     BIGINT,
    cycle_type    VARCHAR(20)  NOT NULL DEFAULT 'DAY',
    items_json    TEXT,
    enabled       SMALLINT     NOT NULL DEFAULT 1,
    remark        VARCHAR(500),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP,
    create_by     VARCHAR(50),
    update_by     VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_inspection_tpl ON device_inspection_template (template_code);
CREATE INDEX IF NOT EXISTS idx_device_inspection_tpl_cat ON device_inspection_template (category_id);

-- 15. 点检记录
CREATE TABLE IF NOT EXISTS device_inspection_record (
    id             BIGSERIAL PRIMARY KEY,
    record_no      VARCHAR(50) NOT NULL,
    template_id    BIGINT,
    device_id      BIGINT      NOT NULL,
    plan_time      TIMESTAMP,
    execute_time   TIMESTAMP,
    executor_id    BIGINT,
    executor_name  VARCHAR(50),
    result         VARCHAR(20) NOT NULL DEFAULT '合格',
    items_json     TEXT,
    abnormal_desc  VARCHAR(500),
    photos         VARCHAR(1000),
    repair_order_id BIGINT,
    status         VARCHAR(20) NOT NULL DEFAULT '待点检',
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_inspection_no ON device_inspection_record (record_no);
CREATE INDEX IF NOT EXISTS idx_device_inspection_device ON device_inspection_record (device_id, plan_time);

-- 16. 保养计划
CREATE TABLE IF NOT EXISTS device_maintenance_plan (
    id                BIGSERIAL PRIMARY KEY,
    plan_no           VARCHAR(50)  NOT NULL,
    plan_name         VARCHAR(100) NOT NULL,
    device_id         BIGINT       NOT NULL,
    maintenance_type  VARCHAR(20)  NOT NULL DEFAULT '日常',
    trigger_type      VARCHAR(20)  NOT NULL DEFAULT 'DAY',
    cycle_value       NUMERIC(12,2) NOT NULL DEFAULT 30,
    advance_days      INT          NOT NULL DEFAULT 3,
    owner_employee_id BIGINT,
    items_json        TEXT,
    last_execute_time TIMESTAMP,
    next_due_time     TIMESTAMP,
    status            VARCHAR(20)  NOT NULL DEFAULT '未开始',
    remark            VARCHAR(500),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP,
    create_by         VARCHAR(50),
    update_by         VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_maint_plan_no ON device_maintenance_plan (plan_no);
CREATE INDEX IF NOT EXISTS idx_device_maint_due ON device_maintenance_plan (device_id, next_due_time);

-- 17. 保养工单
CREATE TABLE IF NOT EXISTS device_maintenance_order (
    id              BIGSERIAL PRIMARY KEY,
    order_no        VARCHAR(50) NOT NULL,
    plan_id         BIGINT,
    device_id       BIGINT      NOT NULL,
    plan_time       TIMESTAMP,
    execute_time    TIMESTAMP,
    executor_id     BIGINT,
    executor_name   VARCHAR(50),
    items_json      TEXT,
    result          VARCHAR(20) NOT NULL DEFAULT '合格',
    downtime_hours  NUMERIC(10,2) NOT NULL DEFAULT 0,
    spare_cost      NUMERIC(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT '待执行',
    remark          VARCHAR(500),
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       VARCHAR(50),
    update_by       VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_maint_order_no ON device_maintenance_order (order_no);
CREATE INDEX IF NOT EXISTS idx_device_maint_order_device ON device_maintenance_order (device_id, status);

-- 18. 维修工单
CREATE TABLE IF NOT EXISTS device_repair_order (
    id             BIGSERIAL PRIMARY KEY,
    order_no       VARCHAR(50) NOT NULL,
    device_id      BIGINT      NOT NULL,
    source_type    VARCHAR(20) NOT NULL DEFAULT '人工',
    alarm_id       BIGINT,
    inspection_id  BIGINT,
    fault_type     VARCHAR(30),
    fault_desc     VARCHAR(500),
    urgency        VARCHAR(20) NOT NULL DEFAULT '一般',
    reporter_id    BIGINT,
    reporter_name  VARCHAR(50),
    report_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assignee_id    BIGINT,
    assignee_name  VARCHAR(50),
    assign_time    TIMESTAMP,
    start_time     TIMESTAMP,
    finish_time    TIMESTAMP,
    downtime_hours NUMERIC(10,2) NOT NULL DEFAULT 0,
    fault_reason   VARCHAR(500),
    solution       VARCHAR(1000),
    spare_cost     NUMERIC(18,2) NOT NULL DEFAULT 0,
    acceptor_id    BIGINT,
    acceptor_name  VARCHAR(50),
    accept_time    TIMESTAMP,
    accept_result  VARCHAR(20),
    status         VARCHAR(20) NOT NULL DEFAULT '报修',
    remark         VARCHAR(500),
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP,
    create_by      VARCHAR(50),
    update_by      VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_repair_no ON device_repair_order (order_no);
CREATE INDEX IF NOT EXISTS idx_device_repair_device ON device_repair_order (device_id, status);
CREATE INDEX IF NOT EXISTS idx_device_repair_time ON device_repair_order (report_time);

-- 19. 维修/保养更换备件明细
CREATE TABLE IF NOT EXISTS device_repair_part (
    id                 BIGSERIAL PRIMARY KEY,
    repair_order_id    BIGINT,
    maintenance_order_id BIGINT,
    device_id          BIGINT NOT NULL,
    spare_id           BIGINT NOT NULL,
    spare_code         VARCHAR(50),
    spare_name         VARCHAR(100),
    quantity           NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit               VARCHAR(20),
    unit_price         NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount             NUMERIC(18,2) NOT NULL DEFAULT 0,
    stock_out_no       VARCHAR(50),
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_device_repair_part_order ON device_repair_part (repair_order_id);
CREATE INDEX IF NOT EXISTS idx_device_repair_part_maint ON device_repair_part (maintenance_order_id);
CREATE INDEX IF NOT EXISTS idx_device_repair_part_spare ON device_repair_part (spare_id);

-- 20. 备件档案
CREATE TABLE IF NOT EXISTS device_spare_part (
    id             BIGSERIAL PRIMARY KEY,
    spare_code     VARCHAR(50)  NOT NULL,
    spare_name     VARCHAR(100) NOT NULL,
    material_id    BIGINT,
    material_code  VARCHAR(50),
    material_name  VARCHAR(200),
    specification  VARCHAR(200),
    unit           VARCHAR(20),
    safety_stock   NUMERIC(18,4) NOT NULL DEFAULT 0,
    warehouse_id   BIGINT,
    location_id    BIGINT,
    supplier_id    BIGINT,
    ref_price      NUMERIC(18,4) NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT '启用',
    remark         VARCHAR(500),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP,
    create_by      VARCHAR(50),
    update_by      VARCHAR(50)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_spare_code ON device_spare_part (spare_code);
CREATE INDEX IF NOT EXISTS idx_device_spare_material ON device_spare_part (material_id);

-- 21. 备件更换记录
CREATE TABLE IF NOT EXISTS device_spare_replacement (
    id                  BIGSERIAL PRIMARY KEY,
    device_id           BIGINT      NOT NULL,
    repair_order_id     BIGINT,
    maintenance_order_id BIGINT,
    spare_id            BIGINT      NOT NULL,
    spare_code          VARCHAR(50),
    spare_name          VARCHAR(100),
    quantity            NUMERIC(18,4) NOT NULL DEFAULT 0,
    replace_time        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator_id         BIGINT,
    operator_name       VARCHAR(50),
    stock_out_no        VARCHAR(50),
    remark              VARCHAR(500),
    create_time         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_device_spare_rec_device ON device_spare_replacement (device_id, replace_time);
CREATE INDEX IF NOT EXISTS idx_device_spare_rec_spare ON device_spare_replacement (spare_id);

-- 22. 设备日汇总
CREATE TABLE IF NOT EXISTS device_daily_stat (
    id              BIGSERIAL PRIMARY KEY,
    device_id       BIGINT      NOT NULL,
    stat_date       DATE        NOT NULL,
    online_seconds  BIGINT      NOT NULL DEFAULT 0,
    run_seconds     BIGINT      NOT NULL DEFAULT 0,
    idle_seconds    BIGINT      NOT NULL DEFAULT 0,
    alarm_seconds   BIGINT      NOT NULL DEFAULT 0,
    offline_seconds BIGINT      NOT NULL DEFAULT 0,
    repair_seconds  BIGINT      NOT NULL DEFAULT 0,
    output_qty      NUMERIC(18,4) NOT NULL DEFAULT 0,
    reject_qty      NUMERIC(18,4) NOT NULL DEFAULT 0,
    alarm_count     INT         NOT NULL DEFAULT 0,
    downtime_hours  NUMERIC(10,2) NOT NULL DEFAULT 0,
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_daily_stat ON device_daily_stat (device_id, stat_date);

-- ============================================================
-- 六、菜单（按 menu_code 幂等，不写死 menu_id）
-- ============================================================
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, component_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT 'device', '设备管理', NULL, 'CATALOG', '/device', 'Layout', NULL,
       45, 'Cpu', 1, 1, 0, 0, '启用', 'ULTIMATE'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = 'device');

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, component_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT v.menu_code, v.menu_name,
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'device' LIMIT 1),
       'MENU', v.route_path, v.component_path, v.permission_code,
       v.sort_order, NULL, 1, 1, 0, 0, '启用', 'ULTIMATE'
FROM (VALUES
    ('device_ledger',         '设备台账',   '/device/ledger',         'views/device/DeviceLedgerList',         'device:ledger',         10),
    ('device_access',         '接入管理',   '/device/access',         'views/device/DeviceAccess',             'device:access',         20),
    ('device_debug',          '接入调试台', '/device/debug',          'views/device/DeviceDebug',              'device:debug',          30),
    ('device_monitor',        '实时监控',   '/device/monitor',        'views/device/DeviceMonitor',            'device:monitor',        40),
    ('device_task',           '设备任务',   '/device/task',           'views/device/DeviceTaskList',           'device:task',           50),
    ('device_report_confirm', '报工待确认', '/device/report-confirm', 'views/device/DeviceReportConfirmList',  'device:report-confirm', 60),
    ('device_unmatched',      '未关联数据', '/device/unmatched',      'views/device/DeviceUnmatchedList',      'device:unmatched',      70),
    ('device_alarm',          '报警中心',   '/device/alarm',          'views/device/DeviceAlarmList',          'device:alarm',          80),
    ('device_inspection',     '点检管理',   '/device/inspection',     'views/device/DeviceInspectionTemplateList', 'device:inspection', 90),
    ('device_maintenance',    '保养管理',   '/device/maintenance',    'views/device/DeviceMaintenancePlanList','device:maintenance',   100),
    ('device_repair',         '维修工单',   '/device/repair',         'views/device/DeviceRepairList',         'device:repair',        110),
    ('device_spare',          '备件管理',   '/device/spare',          'views/device/DeviceSpareList',          'device:spare',         120),
    ('device_report',         '设备报表',   '/device/report',         'views/device/DeviceReport',             'device:report',        130),
    ('device_help',           '操作手册',   '/device/help',           'views/device/DeviceHelp',               'device:help',          140)
) AS v(menu_code, menu_name, route_path, component_path, permission_code, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = v.menu_code);

UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_code = 'device' LIMIT 1)
 WHERE menu_code LIKE 'device\_%' ESCAPE '\'
   AND parent_menu_id IS NULL;

-- ============================================================
-- 七、角色授权（超级管理员 + 系统管理员；menu_id 存 route_path）
-- ============================================================
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND (m.route_path = '/device' OR m.route_path LIKE '/device/%')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

-- ============================================================
-- 八、数据字典
-- ============================================================
INSERT INTO sys_dict_type (dict_type_code, dict_type_name, dict_category, is_system, sort_order, status)
SELECT v.code, v.name, '设备管理', 0, v.sort, '启用'
FROM (VALUES
    ('device_alarm_level',    '设备报警等级', 10),
    ('device_fault_type',     '设备故障类型', 20),
    ('device_urgency',        '设备维修紧急程度', 30),
    ('device_maintenance_type','设备保养类型', 40)
) AS v(code, name, sort)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type t WHERE t.dict_type_code = v.code);

INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, dict_sort, is_default, is_enabled)
SELECT t.dict_type_id, v.label, v.value, v.sort, v.is_default, 1
FROM sys_dict_type t
JOIN (VALUES
    ('device_alarm_level', '提示', '提示', 10, 0),
    ('device_alarm_level', '警告', '警告', 20, 1),
    ('device_alarm_level', '严重', '严重', 30, 0),
    ('device_alarm_level', '紧急', '紧急', 40, 0),
    ('device_fault_type', '机械', '机械', 10, 0),
    ('device_fault_type', '电气', '电气', 20, 0),
    ('device_fault_type', '软件', '软件', 30, 0),
    ('device_fault_type', '人为', '人为', 40, 0),
    ('device_fault_type', '其它', '其它', 50, 1),
    ('device_urgency', '一般', '一般', 10, 1),
    ('device_urgency', '紧急', '紧急', 20, 0),
    ('device_urgency', '停机', '停机', 30, 0),
    ('device_maintenance_type', '日常', '日常', 10, 1),
    ('device_maintenance_type', '一级', '一级', 20, 0),
    ('device_maintenance_type', '二级', '二级', 30, 0)
) AS v(type_code, label, value, sort, is_default) ON v.type_code = t.dict_type_code
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data d
     WHERE d.dict_type_id = t.dict_type_id AND d.dict_value = v.value
);

-- ============================================================
-- 九、初始设备分类
-- ============================================================
INSERT INTO device_category (category_code, category_name, parent_id, sort_order, is_system, status)
SELECT v.code, v.name, NULL, v.sort, 1, '启用'
FROM (VALUES
    ('DEV_CAT_MACHINING', '加工设备', 10),
    ('DEV_CAT_TESTING',   '检测设备', 20),
    ('DEV_CAT_ASSEMBLY',  '装配设备', 30),
    ('DEV_CAT_PACKING',   '包装设备', 40),
    ('DEV_CAT_POWER',     '动力设备', 50),
    ('DEV_CAT_AUX',       '辅助设备', 60)
) AS v(code, name, sort)
WHERE NOT EXISTS (SELECT 1 FROM device_category c WHERE c.category_code = v.code);

-- ============================================================
-- 十、系统预置协议模板（只读，可复制为租户模板）
-- ============================================================
INSERT INTO device_protocol_template
  (template_code, template_name, protocol_type, is_preset, config_json, parse_format,
   byte_order, word_order, timestamp_source, status, remark)
SELECT v.code, v.name, v.protocol, 1, v.config, v.parse_format,
       'BIG_ENDIAN', 'HIGH_FIRST', 'CLOUD', '启用', v.remark
FROM (VALUES
    ('SYS_MODBUS_TCP', 'Modbus TCP（通用）', 'MODBUS_TCP', '{"host":"","port":502,"unitId":1,"timeoutMs":3000}', 'FIXED', '云端主动轮询；要求设备具备公网可达地址'),
    ('SYS_MODBUS_RTU', 'Modbus RTU（经串口服务器）', 'MODBUS_RTU', '{"host":"","port":502,"unitId":1,"timeoutMs":3000}', 'FIXED', '串口服务器转 TCP 后接入；云端不直连串口'),
    ('SYS_MQTT',       'MQTT（通用）', 'MQTT', '{"broker":"","port":1883,"topic":"","qos":1,"clientId":""}', 'JSON', '设备或厂商平台主动发布；需外部 MQTT Broker'),
    ('SYS_HTTP',       'HTTP 上报（通用）', 'HTTP', '{"path":"/api/device/ingest/http","method":"POST"}', 'JSON', '设备主动 POST；凭证 + 时间戳 + 签名鉴权'),
    ('SYS_OPCUA',      'OPC UA（通用）', 'OPC_UA', '{"endpoint":"","securityPolicy":"None","username":"","password":""}', 'FIXED', '云端主动订阅；要求设备具备公网可达地址')
) AS v(code, name, protocol, config, parse_format, remark)
WHERE NOT EXISTS (SELECT 1 FROM device_protocol_template t WHERE t.template_code = v.code);

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手工核对）
-- ============================================================
-- 表数量（预期 22）：SELECT count(*) FROM information_schema.tables
--   WHERE table_schema='public' AND table_name LIKE 'device\_%' ESCAPE '\';
-- 菜单（预期 15）：SELECT menu_id, menu_code, menu_name, route_path, sort_order
--   FROM sys_menu WHERE route_path LIKE '/device%' ORDER BY sort_order;
-- 授权（预期 30 = 15 路由 × 2 角色）：
--   SELECT r.role_code, p.menu_id FROM sys_role_permission p
--     JOIN sys_role r ON r.role_id = p.role_id
--    WHERE p.menu_id LIKE '/device%' ORDER BY r.role_code, p.menu_id;
-- 分类（预期 6）：SELECT count(*) FROM device_category WHERE is_system = 1;
-- 模板（预期 5）：SELECT count(*) FROM device_protocol_template WHERE is_preset = 1;
