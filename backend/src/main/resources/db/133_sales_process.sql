-- =====================================================================
-- 133：销售过程动作量化（第一步 · 地基）
--   立项登记表：.trae/documents/销售过程动作量化与销售预警_立项登记表.md
--   内容：① 跟进记录加 3 列；② 新增 4 张表；③ 菜单 2 条 + 改名 2 条 + 角色自动授权；④ 配置种子
--   说明：全部 IF NOT EXISTS / NOT EXISTS 守卫，幂等（跑 100 遍 = 跑 1 遍）；
--         3 个新列同由 SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动时对主库 + 每个租户库兜底（守则 04 三库齐）。
--   影响：存量数据不迁移（3 个新列默认空），历史跟进记录不参与达标统计（起算日由 sales.process.stat_start_date 控制）。
-- =====================================================================

-- ========== 一、跟进记录加 3 列（回访 / 深入交流两条日任务的判据） ==========
ALTER TABLE crm_follow_up ADD COLUMN IF NOT EXISTS call_duration_seconds INTEGER;
COMMENT ON COLUMN crm_follow_up.call_duration_seconds IS '通话时长（秒）；空=未填写，不计入回访达标';

ALTER TABLE crm_follow_up ADD COLUMN IF NOT EXISTS chat_message_count INTEGER;
COMMENT ON COLUMN crm_follow_up.chat_message_count IS '微信交流条数；空=未填写，不计入深入交流判定';

ALTER TABLE crm_follow_up ADD COLUMN IF NOT EXISTS is_deep_communication SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN crm_follow_up.is_deep_communication IS '是否深入交流（1=是 0=否）；由通话≥300秒或交流≥10句自动预判，人可手改';

-- ========== 二、推广记录主表 ==========
CREATE TABLE IF NOT EXISTS crm_promotion_record (
    promotion_id    BIGSERIAL PRIMARY KEY,
    promotion_no    VARCHAR(32),
    promotion_date  DATE         NOT NULL DEFAULT CURRENT_DATE,
    staff_id        BIGINT,
    staff_name      VARCHAR(50),
    material_type   VARCHAR(20)  NOT NULL DEFAULT '朋友圈图片',
    material_name   VARCHAR(100),
    target_type     VARCHAR(10)  NOT NULL DEFAULT '客户',
    target_count    INTEGER      NOT NULL DEFAULT 0,
    attachment      TEXT,
    is_qualified    SMALLINT     NOT NULL DEFAULT 0,
    remark          VARCHAR(255),
    creator_id      BIGINT,
    created_by      VARCHAR(64),
    create_date     TIMESTAMP    DEFAULT NOW(),
    modifier_id     BIGINT,
    modify_date     TIMESTAMP
);
COMMENT ON TABLE  crm_promotion_record IS '推广记录（朋友圈/产品图/车间视频/RFID素材推广台账）';
COMMENT ON COLUMN crm_promotion_record.material_type IS '素材类型：朋友圈图片/朋友圈视频/产品图/车间视频/RFID素材';
COMMENT ON COLUMN crm_promotion_record.target_type IS '发送对象类型：客户/客户群';
COMMENT ON COLUMN crm_promotion_record.is_qualified IS '是否达标（1=是 0=否）；朋友圈类需截图非空才达标';

CREATE INDEX IF NOT EXISTS idx_crm_promo_date  ON crm_promotion_record (promotion_date);
CREATE INDEX IF NOT EXISTS idx_crm_promo_staff ON crm_promotion_record (staff_id, promotion_date);

-- ========== 三、推广记录明细表（RFID 覆盖率必须按客户粒度去重，故建子表） ==========
CREATE TABLE IF NOT EXISTS crm_promotion_target (
    id            BIGSERIAL PRIMARY KEY,
    promotion_id  BIGINT       NOT NULL,
    customer_id   BIGINT       NOT NULL,
    customer_name VARCHAR(100)
);
COMMENT ON TABLE crm_promotion_target IS '推广记录明细（推广发送到的客户，用于RFID素材覆盖率按客户去重统计）';

CREATE INDEX IF NOT EXISTS idx_crm_promo_target_pid ON crm_promotion_target (promotion_id);
CREATE INDEX IF NOT EXISTS idx_crm_promo_target_cid ON crm_promotion_target (customer_id);

-- ========== 四、销售预警表（等级 + 处理时限 + 闭环状态机） ==========
CREATE TABLE IF NOT EXISTS crm_sales_warning (
    warning_id        BIGSERIAL PRIMARY KEY,
    warning_no        VARCHAR(32),
    warning_type      VARCHAR(40) NOT NULL,
    warning_level     VARCHAR(4)  NOT NULL DEFAULT '黄',
    target_type       VARCHAR(10) NOT NULL DEFAULT '个人',
    target_id         BIGINT,
    target_name       VARCHAR(100),
    owner_user_id     BIGINT,
    owner_name        VARCHAR(50),
    trigger_value     VARCHAR(50),
    threshold_value   VARCHAR(50),
    push_to           VARCHAR(200),
    deadline          TIMESTAMP,
    status            VARCHAR(10) NOT NULL DEFAULT '待处理',
    handle_remark     VARCHAR(500),
    handle_attachment TEXT,
    handler_id        BIGINT,
    handler_name      VARCHAR(50),
    handle_time       TIMESTAMP,
    escalated_from    VARCHAR(32),
    period_start      DATE,
    period_end        DATE,
    creator_id        BIGINT,
    created_by        VARCHAR(64),
    create_date       TIMESTAMP DEFAULT NOW(),
    modify_date       TIMESTAMP
);
COMMENT ON TABLE  crm_sales_warning IS '销售预警（12 条销售规则 + 3 条物流规则；黄橙红三级 + 处理时限 + 闭环）';
COMMENT ON COLUMN crm_sales_warning.warning_level IS '预警等级：黄(提醒)/橙(警告)/红(严重)';
COMMENT ON COLUMN crm_sales_warning.status IS '闭环状态：待处理/处理中/已处理待确认/已关闭';

CREATE INDEX IF NOT EXISTS idx_crm_warn_status ON crm_sales_warning (status, warning_level);
CREATE INDEX IF NOT EXISTS idx_crm_warn_owner  ON crm_sales_warning (owner_user_id, status);

-- ========== 五、员工月度目标表（仅"被手工覆盖过的月份"落库；未覆盖由 季÷3 实时算） ==========
CREATE TABLE IF NOT EXISTS sales_target_month (
    id            BIGSERIAL PRIMARY KEY,
    target_year   INTEGER       NOT NULL,
    employee_id   BIGINT        NOT NULL,
    month_no      INTEGER       NOT NULL,
    target_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    is_manual     SMALLINT      NOT NULL DEFAULT 1,
    create_by     VARCHAR(64),
    update_by     VARCHAR(64),
    create_date   TIMESTAMP DEFAULT NOW(),
    update_date   TIMESTAMP
);
COMMENT ON TABLE  sales_target_month IS '销售人员月度目标（仅手工覆盖过的月份落库，未覆盖的月份由 季目标÷3 实时计算）';
COMMENT ON COLUMN sales_target_month.is_manual IS '是否手工覆盖（1=页面改过，重算时不被覆盖）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_target_month ON sales_target_month (target_year, employee_id, month_no);

-- ========== 六、菜单：新增「销售过程」（客户管理下，紧随联系人管理）+「经营看板」（数据看板下） ==========
-- 父级取法：客户管理取 menu_code='crm' 的目录行；数据看板取路线 /databoard/sale 所在行的 parent_menu_id（与单据明细报表菜单同一套取法）
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path,
                      permission_code, component_path, sort_order, icon, is_visible, is_enabled,
                      is_cache, keepalive, status)
SELECT 'crm_sales_activity', '销售过程', c.menu_id, 'MENU', '/crm/sales-activity',
       'crm:sales-activity', NULL, 7, 'DataLine', 1, 1, 0, 1, '启用'
  FROM sys_menu c
 WHERE c.menu_code = 'crm'
   AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = 'crm_sales_activity')
 LIMIT 1;

INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path,
                      permission_code, component_path, sort_order, icon, is_visible, is_enabled,
                      is_cache, keepalive, status)
SELECT 'databoard_operation', '经营看板', p.parent_menu_id, 'MENU', '/databoard/operation',
       'databoard:operation', NULL, 4, 'TrendCharts', 1, 1, 0, 1, '启用'
  FROM sys_menu p
 WHERE p.route_path = '/databoard/sale' AND p.parent_menu_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = 'databoard_operation')
 LIMIT 1;

-- ========== 七、菜单改名（只改显示名，route_path 与 permission_code 一律不动，避免收藏夹失效） ==========
UPDATE sys_menu SET menu_name = '销售预警'   WHERE menu_code = 'crm_warning'   AND menu_name <> '销售预警';
UPDATE sys_menu SET menu_name = '我的驾驶舱' WHERE menu_code = 'crm_dashboard' AND menu_name <> '我的驾驶舱';

-- ========== 八、角色自动授权（守则 2.7.2-3：禁止部署后要求管理员手工点） ==========
-- 口径：sys_role_permission.menu_id 存的是 sys_menu.route_path 字符串，不是数字 menu_id（已对实库核对）
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at)
SELECT r.role_id, '/crm/sales-activity', '菜单', NOW()
  FROM sys_role r
 WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'SALES_MANAGER', 'SALES')
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission p
                    WHERE p.role_id = r.role_id AND p.permission_type = '菜单'
                      AND p.menu_id = '/crm/sales-activity');

INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at)
SELECT r.role_id, '/databoard/operation', '菜单', NOW()
  FROM sys_role r
 WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'FINANCE_MANAGER')
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission p
                    WHERE p.role_id = r.role_id AND p.permission_type = '菜单'
                      AND p.menu_id = '/databoard/operation');

-- ========== 九、配置种子（设置中心「CRM 预警设置」新增配置区；存在即跳过，不覆盖用户改过的值） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark)
SELECT v.k, v.va, v.nm, 'crm_process', 'text', v.so, v.rm
  FROM (VALUES
        ('sales.process.daily_visit',      '30',   '每日回访老客户数（家）',        101, '方案默认 30 家/天；回访判据：通话≥30秒或微信有实质回复'),
        ('sales.process.daily_new_wechat', '5',    '每日新增意向微信（家）',        102, '方案默认 5 家/天'),
        ('sales.process.daily_deep_chat',  '3',    '每日深入业务交流（家）',        103, '方案默认 3 家/天；判据：通话≥5分钟或微信交流≥10句'),
        ('sales.process.weekly_sample',    '5',    '每周寄样品数（份）',           104, '方案默认 5 份/周；需快递单号能在物流侧查到轨迹'),
        ('sales.process.weekly_promotion', '3',    '每周朋友圈推广（次）',          105, '方案默认 3 次/周；需上传截图'),
        ('sales.process.private_overdue_days', '30', '私海超期未联系天数',         106, '方案默认 30 天'),
        ('sales.process.rfid_coverage',    '100',  'RFID素材覆盖率（%）',          107, '方案默认 100%'),
        ('sales.process.bonus_amount',     '500',  '过程奖金金额（元/人/月）',      108, '方案默认 500 元；四项全 100% 才发放（一刀切）'),
        ('sales.process.bonus_tolerance',  '0',    '过程奖金容差（天）',           109, '0=一刀切，任一条件不达标即为 0 元（用户 2026-10-07 拍板）'),
        ('sales.process.level.yellow_rate','80',   '黄色预警完成率下限（%）',        110, '黄=80~99%；橙=60~79%；红=<60%'),
        ('sales.process.level.orange_rate','60',   '橙色预警完成率下限（%）',        111, '见上'),
        ('sales.process.level.yellow_days','25',   '黄色预警私海天数下限（天）',      112, '黄=25~29天；橙=30~44天；红=>45天'),
        ('sales.process.level.orange_days','30',   '橙色预警私海天数下限（天）',      113, '见上'),
        ('sales.process.deadline.yellow',  '当日', '黄色预警处理时限',             114, '黄=本人/当日；橙=本人+总监/24小时；红=本人+总监+总经理/48小时'),
        ('sales.process.deadline.orange',  '24',   '橙色预警处理时限（小时）',       115, '见上'),
        ('sales.process.deadline.red',     '48',   '红色预警处理时限（小时）',       116, '见上'),
        ('sales.process.level.ar_yellow_days','15','回款逾期黄色天数上限（天）',     117, '文档第八节：回款逾期 1~15 天为黄'),
        ('sales.process.level.ar_orange_days','30','回款逾期橙色天数上限（天）',     118, '文档第八节：16~30 天为橙、超过为红'),
        ('sales.process.push.yellow',      '销售本人',               '黄色预警推送对象',  119, '仅当预警类型未单独规定推送对象时生效（第八节逐类型规定优先）'),
        ('sales.process.push.orange',      '销售 + 总监',            '橙色预警推送对象',  120, '同上'),
        ('sales.process.push.red',         '销售 + 总监 + 总经理',   '红色预警推送对象',  121, '同上')
       ) AS v(k, va, nm, so, rm)
 WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = v.k);

-- 过程达标统计起算日（默认 = 脚本执行当日；部署后可在设置中心改为实际启用日）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark)
SELECT 'sales.process.stat_start_date', TO_CHAR(NOW(), 'YYYY-MM-DD'), '过程达标统计起算日',
       'crm_process', 'text', 100, '该日之前的历史跟进记录不参与达标统计、不产生预警、不算奖金'
 WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'sales.process.stat_start_date');
