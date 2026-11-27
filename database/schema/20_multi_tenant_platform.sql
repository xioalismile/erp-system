-- ============================================================================
-- 智能ERP系统 - 多租户平台中央库建表脚本
-- 模块：平台管理（erp_platform数据库）
-- 说明：平台中央库存放租户信息、用户-租户映射、License授权
--       每个租户的业务数据在独立的 erp_tenant_xxx 数据库中
-- ============================================================================

-- ============================================================================
-- 注意：此脚本需要在 erp_platform 数据库中执行
-- 创建平台库：CREATE DATABASE erp_platform;
-- ============================================================================

-- 1. 租户（公司）注册表
CREATE TABLE IF NOT EXISTS sys_tenant (
    tenant_id BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(50) NOT NULL UNIQUE,
    tenant_name VARCHAR(200) NOT NULL,
    db_name VARCHAR(100) NOT NULL,
    db_host VARCHAR(200) NOT NULL DEFAULT 'localhost',
    db_port INT NOT NULL DEFAULT 5432,
    db_username VARCHAR(100) NOT NULL DEFAULT 'erp_user',
    db_password VARCHAR(200) NOT NULL DEFAULT 'Erp@2026',
    contact_person VARCHAR(100),
    contact_phone VARCHAR(50),
    contact_email VARCHAR(200),
    address VARCHAR(500),
    max_users INT NOT NULL DEFAULT 50,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    expire_date DATE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50)
);

COMMENT ON TABLE sys_tenant IS '租户（公司）注册表';
COMMENT ON COLUMN sys_tenant.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_tenant.tenant_code IS '租户编码（唯一）';
COMMENT ON COLUMN sys_tenant.tenant_name IS '租户名称（公司名）';
COMMENT ON COLUMN sys_tenant.db_name IS '该租户对应的数据库名（如erp_tenant_001）';
COMMENT ON COLUMN sys_tenant.db_host IS '数据库主机地址';
COMMENT ON COLUMN sys_tenant.db_port IS '数据库端口';
COMMENT ON COLUMN sys_tenant.db_username IS '数据库用户名';
COMMENT ON COLUMN sys_tenant.db_password IS '数据库密码';
COMMENT ON COLUMN sys_tenant.max_users IS '最大用户数';
COMMENT ON COLUMN sys_tenant.status IS '状态：active-正常, suspended-暂停, expired-过期';
COMMENT ON COLUMN sys_tenant.expire_date IS '到期日期';

-- 2. 用户-租户映射表（一个用户可属于多个租户）
CREATE TABLE IF NOT EXISTS sys_user_tenant (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    is_admin_in_tenant SMALLINT NOT NULL DEFAULT 0,
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (user_id, tenant_id)
);

COMMENT ON TABLE sys_user_tenant IS '用户-租户映射表';
COMMENT ON COLUMN sys_user_tenant.user_id IS '用户ID（对应平台库sys_user.user_id）';
COMMENT ON COLUMN sys_tenant.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_user_tenant.is_admin_in_tenant IS '是否为该租户的管理员';
COMMENT ON COLUMN sys_user_tenant.last_login_time IS '在该租户的最后登录时间';

-- 3. 租户License授权表
CREATE TABLE IF NOT EXISTS sys_tenant_license (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    license_key VARCHAR(500) NOT NULL,
    license_content TEXT,
    mac_address VARCHAR(100),
    issued_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_date DATE NOT NULL,
    max_user_count INT NOT NULL DEFAULT 50,
    modules VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'valid',
    issued_by VARCHAR(50) DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

COMMENT ON TABLE sys_tenant_license IS '租户License授权表';
COMMENT ON COLUMN sys_tenant_license.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_tenant_license.license_key IS 'License密钥';
COMMENT ON COLUMN sys_tenant_license.mac_address IS '绑定的MAC地址';
COMMENT ON COLUMN sys_tenant_license.expire_date IS 'License到期日期';
COMMENT ON COLUMN sys_tenant_license.max_user_count IS '最大用户数';
COMMENT ON COLUMN sys_tenant_license.modules IS '授权模块（逗号分隔）';

-- 4. 平台用户表（从业务库迁移，一个用户可属于多个租户）
-- 注意：此表结构与业务库的sys_user相同，但存放在平台库中
CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGSERIAL PRIMARY KEY,
    user_code VARCHAR(50) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    mobile_phone VARCHAR(20),
    avatar_url VARCHAR(500),
    user_type VARCHAR(20) NOT NULL DEFAULT '普通用户',
    auth_type VARCHAR(20) NOT NULL DEFAULT '本地认证',
    is_admin SMALLINT NOT NULL DEFAULT 0,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    is_locked SMALLINT NOT NULL DEFAULT 0,
    lock_time TIMESTAMP,
    password_expire_time TIMESTAMP,
    password_change_time TIMESTAMP,
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(50),
    login_attempts INT DEFAULT 0,
    remark VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50)
);

COMMENT ON TABLE sys_user IS '平台用户表（全局，跨租户）';

-- 5. 平台角色表（平台级角色，如超级管理员）
CREATE TABLE IF NOT EXISTS sys_platform_role (
    role_id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    is_system SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_platform_role IS '平台角色表';

-- 6. 平台用户-角色映射表
CREATE TABLE IF NOT EXISTS sys_platform_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE (user_id, role_id)
);

COMMENT ON TABLE sys_platform_user_role IS '平台用户角色映射表';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_sys_tenant_code ON sys_tenant(tenant_code);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_status ON sys_tenant(status);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant_user ON sys_user_tenant(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant_tenant ON sys_user_tenant(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_license_tenant ON sys_tenant_license(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);

-- ============================================================================
-- 初始化数据：插入默认超级管理员和默认租户
-- ============================================================================

-- 插入平台超级管理员（密码: admin123，BCrypt加密）
INSERT INTO sys_user (user_code, username, password, real_name, user_type, is_admin, is_enabled, status)
SELECT 'ADMIN001', 'admin', '$2a$10$3DtK0FbY/C9DtMcQEb9q5ujzx30FTmYkREj4nlTo7xIMzOOWkdBRa', 'System Administrator', 'System User', 1, 1, 'Enabled'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- 插入默认租户（对应现有的erp_db业务库）
INSERT INTO sys_tenant (tenant_code, tenant_name, db_name, db_host, db_port, db_username, db_password, max_users, status, expire_date)
SELECT 'DEFAULT', '默认公司', 'erp_db', 'localhost', 5432, 'erp_user', 'Erp@2026', 50, 'active', '2026-12-31'
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE tenant_code = 'DEFAULT');

-- 将admin关联到默认租户
INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant)
SELECT u.user_id, t.tenant_id, 1
FROM sys_user u, sys_tenant t
WHERE u.username = 'admin' AND t.tenant_code = 'DEFAULT'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_tenant ut WHERE ut.user_id = u.user_id AND ut.tenant_id = t.tenant_id
  );

-- 插入平台超级管理员角色
INSERT INTO sys_platform_role (role_code, role_name, is_system)
SELECT 'PLATFORM_ADMIN', '平台超级管理员', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_platform_role WHERE role_code = 'PLATFORM_ADMIN');

-- 将admin关联到平台超级管理员角色
INSERT INTO sys_platform_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_platform_role r
WHERE u.username = 'admin' AND r.role_code = 'PLATFORM_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_platform_user_role pur WHERE pur.user_id = u.user_id AND pur.role_id = r.role_id
  );

-- ============================================================================
-- 多租户平台中央库建表完成
-- ============================================================================
