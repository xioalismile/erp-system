-- ============================================================================
-- 多租户支撑表（sys_tenant / sys_user_tenant / sys_tenant_license）
-- 幂等补丁：缺失时在业务库 erp_db 中创建；避免单租户模式下选择公司报错
-- ============================================================================

-- 1. 租户注册表
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

-- ============================================================================
-- 默认数据（幂等：不存在才插入）
-- ============================================================================

-- 默认租户：单租户部署时直接使用业务库自身
INSERT INTO sys_tenant (tenant_code, tenant_name, db_name, contact_person, status, remark)
VALUES ('default', '默认公司', 'erp_db', 'System Administrator', 'active', '系统初始化默认租户')
ON CONFLICT (tenant_code) DO NOTHING;

-- 默认用户与默认租户的映射（admin 作为管理员；sale01 作为普通用户）
DO $$
DECLARE
    v_admin_id BIGINT;
    v_sale01_id BIGINT;
    v_tenant_id BIGINT;
BEGIN
    SELECT user_id INTO v_admin_id FROM sys_user WHERE username='admin' LIMIT 1;
    SELECT user_id INTO v_sale01_id FROM sys_user WHERE username='sale01' LIMIT 1;
    SELECT tenant_id INTO v_tenant_id FROM sys_tenant WHERE tenant_code='default' LIMIT 1;

    IF v_admin_id IS NOT NULL AND v_tenant_id IS NOT NULL THEN
        INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status)
        VALUES (v_admin_id, v_tenant_id, 1, 'active')
        ON CONFLICT (user_id, tenant_id) DO NOTHING;
    END IF;

    IF v_sale01_id IS NOT NULL AND v_tenant_id IS NOT NULL THEN
        INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status)
        VALUES (v_sale01_id, v_tenant_id, 0, 'active')
        ON CONFLICT (user_id, tenant_id) DO NOTHING;
    END IF;
END $$;
