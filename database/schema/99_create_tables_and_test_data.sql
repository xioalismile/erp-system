-- 创建 sys_tenant 表（如果不存在）
CREATE TABLE IF NOT EXISTS sys_tenant (
    tenant_id BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(50) NOT NULL UNIQUE,
    tenant_name VARCHAR(100) NOT NULL,
    db_name VARCHAR(100),
    db_host VARCHAR(100),
    db_port INT,
    db_username VARCHAR(50),
    db_password VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    expire_date DATE,
    version VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    features JSONB,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(30),
    contact_email VARCHAR(100),
    address VARCHAR(200),
    max_users INT DEFAULT 100,
    trial_days INT,
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

-- 创建 sys_user_tenant 表（如果不存在）
CREATE TABLE IF NOT EXISTS sys_user_tenant (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    is_admin_in_tenant SMALLINT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    last_login_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(user_id, tenant_id)
);

-- 插入测试租户（标准版）
INSERT INTO sys_tenant (tenant_id, tenant_code, tenant_name, db_name, db_host, db_port, db_username, db_password, status, expire_date, version, features, created_at) VALUES
(1000, 'TST001', '测试标准版公司', 'erp_db', 'localhost', 5432, 'erp_user', 'Erp@2026', 'active', '2026-12-31', 'STANDARD', '["base","sale","purchase","inventory","finance"]', CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO NOTHING;

-- 插入测试租户（CRM版）
INSERT INTO sys_tenant (tenant_id, tenant_code, tenant_name, db_name, db_host, db_port, db_username, db_password, status, expire_date, version, features, created_at) VALUES
(1001, 'TST002', '测试CRM版公司', 'erp_db', 'localhost', 5432, 'erp_user', 'Erp@2026', 'active', '2026-12-31', 'CRM', '["base","sale","purchase","inventory","crm"]', CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO NOTHING;

-- 插入测试租户管理员用户（标准版）
INSERT INTO sys_user (user_id, username, password, real_name, is_admin, is_enabled, created_at) VALUES
(1000, 'tenant_admin_s', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '标准版租户管理员', 1, 1, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

-- 插入测试租户管理员用户（CRM版）
INSERT INTO sys_user (user_id, username, password, real_name, is_admin, is_enabled, created_at) VALUES
(1001, 'tenant_admin_c', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'CRM版租户管理员', 1, 1, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

-- 为标准版租户管理员分配 ADMIN_STANDARD 角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1000, 100) ON CONFLICT DO NOTHING;

-- 为CRM版租户管理员分配 ADMIN_CRM 角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1001, 101) ON CONFLICT DO NOTHING;

-- 在平台库关联用户与租户
INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at) VALUES
(1000, 1000, 1, 'active', CURRENT_TIMESTAMP),
(1001, 1001, 1, 'active', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;