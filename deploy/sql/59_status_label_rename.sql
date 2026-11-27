-- 59_status_label_rename.sql
-- 单据终态显示名统一：「已审核」→「已生效」；动作按钮「反审批」→「重置草稿」（2026-09-19 用户拍板）
--
-- 背景：
--   1) 全系统终态显示名由「已审核」统一改为「已生效」（代码侧 BillStatusEnum / BillStatusUtils /
--      前端 utils/bill-status.js 已改）。存量数据里部分单据表主状态列、全系统 approval_status 列
--      仍是旧名 → 列表按状态筛选会筛不出数据，故必须同步迁移。
--   2) 动作按钮「反审批」改名「重置草稿」（仅显示名；接口名 reverseApprove 与权限标识保持不变）。
--
-- 口径：
--   - 只迁移**字符型** status / approval_status / bill_status 列里的 '已审核' → '已生效'；
--     数字型状态列（0 草稿 / 1 待审核 / 2 已生效 / 7 已作废）语义不变、内容不动。
--   - 代码侧 BillStatusUtils 保留 '已审核' 作为历史别名，因此本脚本**先跑后跑都不炸**；
--     未迁移到的库最多是"筛选不到旧数据"，不会出现状态误判。
--   - 企业语言包 sys_i18n 的键名/中文文本里的旧名一并改名（先删会撞唯一键的旧行，再整体 REPLACE）。
--
-- 幂等：重复执行命中 0 行；不 DROP、不改表结构（除 bom.status 默认值）。
-- 多租户：每个租户库都要执行一次（首次部署由部署流程按文件名升序统一跑；
--         存量库由后端 SchemaUpgradeEnsurer.ensureStatusLabelRenamed() 启动时对当前库自动补一次）。

-- ============ 一、字符型状态列：'已审核' → '已生效' ============
DO $$
DECLARE
    r        record;
    affected bigint;
    total    bigint := 0;
BEGIN
    FOR r IN
        SELECT table_name, column_name
          FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND column_name IN ('status', 'approval_status', 'bill_status')
           AND data_type IN ('character varying', 'character', 'text')
    LOOP
        EXECUTE format('UPDATE %I SET %I = %L WHERE %I = %L',
                       r.table_name, r.column_name, '已生效', r.column_name, '已审核');
        GET DIAGNOSTICS affected = ROW_COUNT;
        IF affected > 0 THEN
            RAISE NOTICE '[59] %.% 迁移 % 行 已审核→已生效', r.table_name, r.column_name, affected;
            total := total + affected;
        END IF;
    END LOOP;
    RAISE NOTICE '[59] 字符型状态列迁移合计 % 行', total;
END $$;

-- ============ 二、bom.status 列默认值同步（新建 BOM 不再落旧名） ============
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'bom' AND column_name = 'status') THEN
        ALTER TABLE bom ALTER COLUMN status SET DEFAULT '已生效';
        COMMENT ON COLUMN bom.status IS '单据状态：草稿/已生效/已停用（2026-09-19 起原「已审核」统一为「已生效」）';
    END IF;
END $$;

-- ============ 三、企业语言包键名与中文文本同步改名 ============
DO $$
DECLARE
    deleted bigint;
    updated bigint;
    pair    record;
BEGIN
    FOR pair IN SELECT * FROM (VALUES ('已审核', '已生效'), ('反审批', '重置草稿')) AS t(old_name, new_name)
    LOOP
        -- 3.1 先删"改名后会撞唯一键 (i18n_key, lang)"的旧行
        DELETE FROM sys_i18n a
         WHERE a.i18n_key LIKE '%' || pair.old_name || '%'
           AND EXISTS (SELECT 1 FROM sys_i18n b
                        WHERE b.i18n_key = replace(a.i18n_key, pair.old_name, pair.new_name)
                          AND b.lang = a.lang);
        GET DIAGNOSTICS deleted = ROW_COUNT;

        -- 3.2 键名与中文文本一起替换（英文行文本不含中文，不受影响）
        UPDATE sys_i18n
           SET i18n_key = replace(i18n_key, pair.old_name, pair.new_name),
               text     = replace(text,     pair.old_name, pair.new_name)
         WHERE i18n_key LIKE '%' || pair.old_name || '%';
        GET DIAGNOSTICS updated = ROW_COUNT;

        IF deleted > 0 OR updated > 0 THEN
            RAISE NOTICE '[59] 语言包 %→%：改名 % 行 / 去重删除 % 行', pair.old_name, pair.new_name, updated, deleted;
        END IF;
    END LOOP;
END $$;

-- ============ 四、迁移后自检（人工核对：两列应为 0） ============
-- SELECT count(*) AS remain_status FROM (
--   SELECT 1 FROM information_schema.columns c
--    WHERE c.table_schema = current_schema()
--      AND c.column_name IN ('status','approval_status','bill_status')
--      AND c.data_type IN ('character varying','character','text')) t;
-- 逐表核对脚本（按需执行）：
-- SELECT table_name, column_name FROM information_schema.columns
--  WHERE table_schema = current_schema()
--    AND column_name IN ('status','approval_status','bill_status')
--    AND data_type IN ('character varying','character','text');
