-- 76_notification_dedup_include_type.sql
-- 2026-09-14 通知去重维度补上「通知类型（type）」
--
-- 背景（实测事故，勿再回退）：
--   原唯一索引 = (receiver_id, source_type, source_id) WHERE is_read = 0，**不含 type**，
--   Java 层预查重条件与之完全一致 → 同一张单的**不同类别提醒会互相顶掉**。
--   实锤：销售订单 SO-20260914-0005（内部 id=258）在草稿阶段先给审批人写入了一条
--   "草稿待提交"未读通知（type=draft，source_type=sale_order，source_id=258），
--   随后提交审批时要写的"待审批"通知（type=approval，同 receiver+source）被判定为重复 →
--   静默跳过 → 小铃铛里始终没有"待审批"提醒，语音播报也就没有数据可播。
--
-- 修复：去重维度加入 type —— 只有 (receiver_id + type + source_type + source_id + 未读) 才算重复，
--   使「待审批 / 超期 / 即将超期 / 应收应付 / 审批结果」等不同类别提醒可各自存在。
--
-- 安全与幂等：
--   · 新索引比旧索引**更宽松**（多一个维度）→ 存量数据天然满足，不会创建失败、无需清洗数据；
--   · DROP IF EXISTS + CREATE IF NOT EXISTS → 跑 100 遍 = 跑 1 遍；
--   · 只动索引，**不修改任何业务数据**；
--   · 索引名保持不变（uk_sys_notification_unread_source），避免其它引用该名字的DDL失效；
--   · 表不存在（新库首次部署、表由代码创建）时整段跳过，不中止部署。

DO $$
BEGIN
    IF to_regclass('public.sys_notification') IS NOT NULL THEN
        DROP INDEX IF EXISTS uk_sys_notification_unread_source;
        CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_notification_unread_source
            ON sys_notification (receiver_id, type, source_type, source_id)
            WHERE is_read = 0;
    END IF;
END $$;
