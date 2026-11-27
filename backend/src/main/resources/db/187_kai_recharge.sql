-- 小楷独立版 · 支付宝充值（2026-11-07）
-- 充值等级：运营自定义售价与到账积分；充值订单：一笔订单对应一次支付宝支付。
-- 全部 IF NOT EXISTS 幂等；同内容文件另存 deploy/sql/187_kai_recharge.sql（守则 04 编号体系）。

CREATE TABLE IF NOT EXISTS kai_recharge_package (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    price      NUMERIC(12,2) NOT NULL DEFAULT 0,
    credits    NUMERIC(14,4) NOT NULL DEFAULT 0,
    sort_no    INT           DEFAULT 0,
    enabled    INT           DEFAULT 1,
    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kai_recharge_pkg_sort ON kai_recharge_package(enabled, sort_no, id);

CREATE TABLE IF NOT EXISTS kai_recharge_order (
    id           BIGSERIAL PRIMARY KEY,
    order_no     VARCHAR(64)   NOT NULL UNIQUE,
    kai_user_id  BIGINT        NOT NULL,
    package_id   BIGINT,
    package_name VARCHAR(100),
    amount       NUMERIC(12,2) NOT NULL DEFAULT 0,
    credits      NUMERIC(14,4) NOT NULL DEFAULT 0,
    status       VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    trade_no     VARCHAR(64),
    pay_time     TIMESTAMP,
    expire_at    TIMESTAMP,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kai_recharge_user ON kai_recharge_order(kai_user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_kai_recharge_status ON kai_recharge_order(status, id DESC);
