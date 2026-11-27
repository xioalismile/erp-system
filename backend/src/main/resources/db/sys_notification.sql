-- ========== 14. sys_notification 系统通知表 ==========
-- 通用消息通知表，由 NotificationScheduler 定时扫描各业务模块生成
-- 接收人维度：每个用户独立的通知列表（receiver_id），支持已读/未读状态
-- 通知类型：approval(待审批) / overdue(已超期) / soon_overdue(即将超期) / draft(草稿) / ar_ap(应收应付跟进)
-- 跳转：通过 source_type + source_id + route_path 字段记录原始单据信息和前端跳转路径
CREATE TABLE IF NOT EXISTS sys_notification (
    id              BIGSERIAL PRIMARY KEY,
    receiver_id     BIGINT      NOT NULL,
    receiver_name   VARCHAR(64),
    type            VARCHAR(32) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    content         TEXT,
    source_type     VARCHAR(64),
    source_id       BIGINT,
    source_no       VARCHAR(64),
    route_path      VARCHAR(255),
    urgency         VARCHAR(16),
    is_read         SMALLINT    NOT NULL DEFAULT 0,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)
);

-- 性能优化索引
CREATE INDEX IF NOT EXISTS idx_sys_notification_receiver_read ON sys_notification (receiver_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_notification_receiver_type   ON sys_notification (receiver_id, type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_notification_source         ON sys_notification (source_type, source_id);
-- 唯一去重：同一接收人同一来源只保留一条未读通知（防止重复推送）
-- 先收敛存量数据：同键多条未读时保留 id 最大（最新）一条，其余标记已读，
-- 否则历史重复数据会让唯一索引创建失败、进而阻断部署（幂等：重复执行零副作用）
UPDATE sys_notification n
SET is_read = 1, read_at = COALESCE(n.read_at, now())
WHERE n.is_read = 0
  AND EXISTS (SELECT 1 FROM sys_notification m
              WHERE m.receiver_id = n.receiver_id
                AND m.source_type IS NOT DISTINCT FROM n.source_type
                AND m.source_id IS NOT DISTINCT FROM n.source_id
                AND m.is_read = 0
                AND m.id > n.id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_notification_unread_source ON sys_notification (receiver_id, source_type, source_id) WHERE is_read = 0;
