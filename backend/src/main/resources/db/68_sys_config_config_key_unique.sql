-- 68_sys_config_config_key_unique.sql
-- 2026-09-12 PG 环境数据模型修正：为 sys_config.config_key 补唯一约束
--
-- 背景：sys_config_schema.sql 里写的是 MySQL 语法 `UNIQUE KEY uk_config_key (config_key)`，
--       而生产/客户服务器用的是 PostgreSQL —— PG 建表时该唯一约束并未被创建。
--       导致所有依赖 `ON CONFLICT (config_key)` 的迁移脚本在 PG 上直接报
--       「没有匹配 ON CONFLICT 说明的唯一或者排除约束」并中止部署（66 号即因此失败）。
--       本脚本把 PG 环境的 config_key 唯一语义补齐，与设计（MySQL 版建表脚本）保持一致。
--
-- 幂等 + 安全：
--   ① 表不存在            → 告警跳过
--   ② 约束已存在          → 跳过
--   ③ 存在重复 config_key → 只告警跳过，绝不删数据，绝不阻塞部署
--   （①②③ 都不做任何 UPDATE/DELETE，不影响任何既有配置值）

DO $$
DECLARE
    has_table      boolean;
    has_constraint boolean;
    dup_cnt        int;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'sys_config'
    ) INTO has_table;

    IF NOT has_table THEN
        RAISE NOTICE 'sys_config table not found, skip unique constraint';
        RETURN;
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_sys_config_config_key'
          AND conrelid = 'sys_config'::regclass
    ) INTO has_constraint;

    IF has_constraint THEN
        RAISE NOTICE 'sys_config.config_key unique constraint already exists, skip';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO dup_cnt FROM (
        SELECT config_key FROM sys_config GROUP BY config_key HAVING COUNT(*) > 1
    ) d;

    IF dup_cnt > 0 THEN
        RAISE NOTICE 'sys_config has % duplicated config_key group(s), skip adding unique constraint (no data touched)', dup_cnt;
        RETURN;
    END IF;

    ALTER TABLE sys_config ADD CONSTRAINT uk_sys_config_config_key UNIQUE (config_key);
    RAISE NOTICE 'added unique constraint uk_sys_config_config_key on sys_config(config_key)';
END $$;
