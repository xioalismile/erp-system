-- ============================================================================
-- 123_qc.sql
-- 第 12 期：质检模块（质检模板设计 + 工序检验记录 + 质检数量驱动下道工序）
--
--   1) qc_template       质检模板头（名称 / 适用物料 / 适用工序 / 质检类型 / 抽样比例 / 允许不良率 / 启停）
--   2) qc_template_item  质检模板检验项（项目名 / 检验方法 / 标准值 / 上限 / 下限 / 单位 / 是否必填）
--   3) qc_record         工序检验记录（一张 = 一次检验：首检 / 抽检 / 全检，判定结果 + 不合格处理方式）
--   4) qc_record_item    检验记录实测明细（逐项实测值 + 单项判定）
--   5) work_order_operation 加列：
--        qc_template_id  本道工序绑定的质检模板（为空 = 不绑定，按物料/工序自动解析）
--        approve_qty     质检判定出的「合格数」= 下一道工序的可开工数量
--        can_start_qty   本道可开工数量（= 上道 approve_qty，进入本道时写入，页面直接展示）
--   6) 菜单「质检模板」挂「基础资料」下 + 角色自动授权（守则 04 2.7.1-7 / 2.8.1-6）
--
-- 口径（已确认为唯一事实来源，代码不得偏离）：
--   · 模板按「物料」或「工序」绑定；两者都没有 = 不绑定。工序「需质检」开关默认关闭；
--     未开启质检的工序完全不受影响（与改造前一致）。
--   · 首检：全部检验项合格 → 整批放行（合格数 = 报工数量）；不合格 → 走不合格处理。
--   · 抽检：按「抽样比例(%)」或前端填报抽样数抽检；不良率 ≤ 允许不良率(默认0) → 放行
--     （合格数 = 报工数量 − 判定不良数）；超出 → 走不合格处理。
--   · 全检：逐件全检；合格数 = 实测合格数，不合格数进不合格处理。
--   · 合格数(approve_qty) = 下一道工序可开工数量，下道报工/开工超过即报「超过上道合格数」。
--
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / ADD COLUMN IF NOT EXISTS /
--       INSERT ... WHERE NOT EXISTS / COMMENT ON COLUMN（均可重复执行）。
--       无 DELETE / DROP / 无 WHERE 的全表 UPDATE。
-- 同步落盘：deploy/sql/123_qc.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈（多租户物理隔离多库）
-- ============================================================================

BEGIN;

-- ---------- 1. 质检模板头 ----------
CREATE TABLE IF NOT EXISTS qc_template (
    template_id          BIGSERIAL PRIMARY KEY,
    template_code        VARCHAR(50),
    template_name        VARCHAR(200) NOT NULL,
    material_id          BIGINT,
    operation_name       VARCHAR(200),
    qc_type              VARCHAR(20) DEFAULT '首检',
    sample_ratio         NUMERIC(18,4) DEFAULT 0,
    allowed_defect_ratio NUMERIC(18,4) DEFAULT 0,
    is_enabled           INT DEFAULT 1,
    remark               VARCHAR(500),
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50)
);
COMMENT ON TABLE  qc_template IS '质检模板头：一套可维护的检验标准（模板头 + 检验项列表）';
COMMENT ON COLUMN qc_template.template_code IS '模板编码（自动生成）';
COMMENT ON COLUMN qc_template.material_id IS '适用物料ID（按物料绑定时使用；为空=不按物料绑定）';
COMMENT ON COLUMN qc_template.operation_name IS '适用工序名称（按工序绑定时使用；为空=不按工序绑定）';
COMMENT ON COLUMN qc_template.qc_type IS '质检类型：首检/抽检/全检（三种判定逻辑不同）';
COMMENT ON COLUMN qc_template.sample_ratio IS '抽样比例(%)：仅抽检使用；抽样数 = 报工数量 × 抽样比例 / 100';
COMMENT ON COLUMN qc_template.allowed_defect_ratio IS '允许不良率(%)：仅抽检使用，默认 0（不允许不良）';
COMMENT ON COLUMN qc_template.is_enabled IS '是否启用：1=启用 0=停用';

-- ---------- 2. 质检模板检验项 ----------
CREATE TABLE IF NOT EXISTS qc_template_item (
    item_id         BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL,
    line_no         INT,
    item_name       VARCHAR(200) NOT NULL,
    inspect_method  VARCHAR(200),
    standard_value  VARCHAR(200),
    upper_limit     NUMERIC(18,4),
    lower_limit     NUMERIC(18,4),
    unit            VARCHAR(50),
    is_required     INT DEFAULT 1,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      VARCHAR(50),
    updated_by      VARCHAR(50)
);
COMMENT ON TABLE  qc_template_item IS '质检模板检验项：项目名 / 检验方法 / 标准值 / 上限 / 下限 / 单位 / 是否必填';
COMMENT ON COLUMN qc_template_item.upper_limit IS '上限：实测值 ≤ 上限才判合格（为空 = 不卡上限）';
COMMENT ON COLUMN qc_template_item.lower_limit IS '下限：实测值 ≥ 下限才判合格（为空 = 不卡下限）';
COMMENT ON COLUMN qc_template_item.is_required IS '是否必填：1=必填（不填判不合格） 0=选填';

-- ---------- 3. 工序检验记录 ----------
CREATE TABLE IF NOT EXISTS qc_record (
    record_id            BIGSERIAL PRIMARY KEY,
    record_no            VARCHAR(50),
    order_id             BIGINT,
    order_no             VARCHAR(50),
    operation_row_id     BIGINT,
    operation_name       VARCHAR(200),
    template_id          BIGINT,
    qc_type              VARCHAR(20),
    inspect_qty          NUMERIC(18,4) DEFAULT 0,
    qualified_qty        NUMERIC(18,4) DEFAULT 0,
    defect_qty           NUMERIC(18,4) DEFAULT 0,
    result               VARCHAR(20),
    handle_type          VARCHAR(20),
    handle_operation_name VARCHAR(200),
    handle_remark        VARCHAR(500),
    inspector_id         BIGINT,
    inspector_name       VARCHAR(50),
    inspect_time         TIMESTAMP,
    remark               VARCHAR(500),
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50)
);
COMMENT ON TABLE  qc_record IS '工序检验记录：一次检验一行（首检/抽检/全检），含判定结果与不合格处理方式';
COMMENT ON COLUMN qc_record.operation_row_id IS '关联工单工序行ID（work_order_operation.id）';
COMMENT ON COLUMN qc_record.inspect_qty IS '检验数量（首检=报工数量；抽检=抽样数；全检=报工数量）';
COMMENT ON COLUMN qc_record.qualified_qty IS '判定合格数（= 写入 work_order_operation.approve_qty，供下道卡量）';
COMMENT ON COLUMN qc_record.defect_qty IS '判定不合格数（进不合格处理）';
COMMENT ON COLUMN qc_record.result IS '判定结果：合格/不合格';
COMMENT ON COLUMN qc_record.handle_type IS '不合格处理方式：补料/返工回退/报废（合格时为空）';
COMMENT ON COLUMN qc_record.handle_operation_name IS '返工回退目标工序名称（仅返工回退时填写）';

-- ---------- 4. 检验记录实测明细 ----------
CREATE TABLE IF NOT EXISTS qc_record_item (
    item_id        BIGSERIAL PRIMARY KEY,
    record_id      BIGINT NOT NULL,
    line_no        INT,
    item_name      VARCHAR(200),
    standard_value VARCHAR(200),
    measured_value VARCHAR(200),
    upper_limit    NUMERIC(18,4),
    lower_limit    NUMERIC(18,4),
    result         VARCHAR(20),
    remark         VARCHAR(500),
    created_at     TIMESTAMP
);
COMMENT ON TABLE  qc_record_item IS '检验记录实测明细：逐项实测值与单项判定结果';
COMMENT ON COLUMN qc_record_item.measured_value IS '实测值（文本，便于记录描述性结果）';

-- ---------- 5. 索引 ----------
CREATE INDEX IF NOT EXISTS idx_qc_template_material  ON qc_template (material_id);
CREATE INDEX IF NOT EXISTS idx_qc_template_operation ON qc_template (operation_name);
CREATE INDEX IF NOT EXISTS idx_qc_template_item_tid  ON qc_template_item (template_id);
CREATE INDEX IF NOT EXISTS idx_qc_record_oprow       ON qc_record (operation_row_id);
CREATE INDEX IF NOT EXISTS idx_qc_record_order       ON qc_record (order_id);
CREATE INDEX IF NOT EXISTS idx_qc_record_item_rid    ON qc_record_item (record_id);

-- ---------- 6. 工单工序列：质检模板绑定 + 合格数（下道可开工数） ----------
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS qc_template_id BIGINT;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS approve_qty    NUMERIC(18,4);
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS can_start_qty  NUMERIC(18,4);
COMMENT ON COLUMN work_order_operation.qc_template_id IS '本道工序绑定的质检模板ID（为空=不绑定，按物料/工序自动解析）';
COMMENT ON COLUMN work_order_operation.approve_qty IS '质检判定出的合格数（= 下一道工序的可开工数量，下道报工/开工不得超过）';
COMMENT ON COLUMN work_order_operation.can_start_qty IS '本道可开工数量（= 上道 approve_qty，上道质检通过后写入）';

-- ---------- 7. 菜单「质检模板」挂「基础资料」下 ----------
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, component_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT 'qc_template', '质检模板',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'basedata'),
       'MENU', '/masterdata/qc-template', 'masterdata/QcTemplateList', 'basedata:qc-template',
       16, 'Checked', 1, 1, 0, 1, '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = 'qc_template');

-- 兜底修复父子关系（父级子查询取不到时为 NULL，这里仅在 NULL 时补齐）
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_code = 'basedata' LIMIT 1)
 WHERE menu_code = 'qc_template'
   AND parent_menu_id IS NULL;

-- ---------- 8. 角色授权（基础资料对所有角色可见，故授权面放宽；幂等 NOT EXISTS） ----------
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'MANAGER',
                      'SALES_MANAGER', 'SALES', 'PURCHASE_MANAGER', 'PURCHASE',
                      'WAREHOUSE_MANAGER', 'WAREHOUSE', 'PRODUCTION_MANAGER', 'PRODUCTION',
                      'FINANCE_MANAGER', 'FINANCE')
  AND m.menu_code = 'qc_template'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（只读，预期：4 张表齐 + 工单工序 3 列齐 + 菜单 1 条 + 授权 ≥ 4 行）
-- ============================================================
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_name IN ('qc_template', 'qc_template_item', 'qc_record', 'qc_record_item')) AS qc_tables,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_name = 'work_order_operation'
      AND column_name IN ('qc_template_id', 'approve_qty', 'can_start_qty')) AS woo_cols,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_code = 'qc_template') AS menu_rows,
  (SELECT COUNT(*) FROM sys_role_permission WHERE menu_id = '/masterdata/qc-template'
      AND permission_type = '菜单') AS grant_rows;
