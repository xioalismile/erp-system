-- 82_stock_qty_i18n_scope_label.sql
-- 归属功能：单据明细行「库存量 / 可用量 / 预计可用量」联动展示 + 即时库存「虚拟可用」改名「预计可用量」（2026-09-15）
-- 本脚本解决两处"改名未联动"：
--   1) 设置中心 → 库存参数 →「发货库存校验口径」选项文案：虚拟库存 → 预计可用量
--      （只改展示 label，value 仍为 actual/virtual，用户已保存的配置值与校验行为零变更）
--   2) 语言包英文词条：新增 3 个界面标题（库存量/可用量/预计可用量），
--      避免英文态走词素组合出现 "Stock量" 这类中英混排
--
-- 幂等性（deploy-kit manifest 铁律）：
--   · i18n 采用「先查后插」，重复执行零副作用
--   · sys_config 的 UPDATE 为固定值基线收敛（WHERE config_key 精确命中 1 行），重复执行结果一致
--   · 不新增/删除任何表与列

-- ---------- 1. 设置中心发货校验口径选项文案改名 ----------
UPDATE sys_config
SET config_options = '[{"label":"实际库存","value":"actual"},{"label":"预计可用量","value":"virtual"}]',
    updated_at     = NOW()
WHERE config_key = 'inv.stock.check.scope';

-- ---------- 2. 新增列的英文词条（field.<中文标题> 整条覆盖，module=page） ----------
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('库存量',   'Stock Qty'),
        ('可用量',   'Available Qty'),
        ('预计可用量', 'Expected Available Qty')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'zh-CN', r.zh_text, 'page', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('field.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('field.' || r.zh_text, 'en', r.en_text, 'page', 'system', NOW());
        END IF;
    END LOOP;
END $$;
