-- 197：生产工单进度对外分享（2026-11-25）
-- 用途：手机端「工序进度」生成带过期时间的分享链接 / 小程序卡片，微信用户免登录查看该工单工序进度。
-- 口径：只记录分享令牌与展示配置，不存客户、价格、成本等敏感字段；过期或停止后立即失效。
-- 幂等：CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS，重复执行零副作用。

CREATE TABLE IF NOT EXISTS work_order_share (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(96) NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    title VARCHAR(120) NOT NULL,
    remark VARCHAR(500),
    expire_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    show_assignee INTEGER NOT NULL DEFAULT 1,
    show_plan_date INTEGER NOT NULL DEFAULT 1,
    show_material_code INTEGER NOT NULL DEFAULT 1,
    view_count INTEGER NOT NULL DEFAULT 0,
    last_view_at TIMESTAMP WITHOUT TIME ZONE,
    created_by BIGINT,
    created_by_name VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_work_order_share_token ON work_order_share(token);
CREATE INDEX IF NOT EXISTS idx_work_order_share_order ON work_order_share(order_id);
CREATE INDEX IF NOT EXISTS idx_work_order_share_expire ON work_order_share(expire_at);
