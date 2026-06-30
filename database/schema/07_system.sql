-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库建表脚本
-- 模块：系统管理模块
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：共8张表，包含用户、角色、权限、操作日志等系统管理功能
-- ============================================================================

-- 1. 用户主表 (sys_user)
CREATE TABLE sys_user (
    user_id BIGSERIAL PRIMARY KEY,
    user_code VARCHAR(50) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    mobile_phone VARCHAR(20),
    avatar_url VARCHAR(500),
    dept_id BIGINT,
    employee_id BIGINT,
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
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE sys_user IS '用户主表';
COMMENT ON COLUMN sys_user.user_id IS '用户ID';
COMMENT ON COLUMN sys_user.user_code IS '用户编码';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password IS '密码';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.dept_id IS '所属部门';
COMMENT ON COLUMN sys_user.employee_id IS '关联员工';
COMMENT ON COLUMN sys_user.auth_type IS '认证方式';

-- 2. 角色主表 (sys_role)
CREATE TABLE sys_role (
    role_id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    role_type VARCHAR(20) NOT NULL DEFAULT '业务角色',
    data_scope VARCHAR(20) NOT NULL DEFAULT '本部门',
    data_scope_value VARCHAR(500),
    is_system SMALLINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE sys_role IS '角色主表';
COMMENT ON COLUMN sys_role.role_id IS '角色ID';
COMMENT ON COLUMN sys_role.role_code IS '角色编码';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.data_scope IS '数据范围';
COMMENT ON COLUMN sys_role.is_system IS '是否系统角色';

-- 3. 用户角色关联表 (sys_user_role)
CREATE TABLE sys_user_role (
    user_role_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    UNIQUE (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(role_id) ON DELETE CASCADE
);

COMMENT ON TABLE sys_user_role IS '用户角色关联表';

-- 4. 菜单主表 (sys_menu)
CREATE TABLE sys_menu (
    menu_id BIGSERIAL PRIMARY KEY,
    menu_code VARCHAR(100) NOT NULL UNIQUE,
    menu_name VARCHAR(100) NOT NULL,
    parent_menu_id BIGINT,
    menu_type VARCHAR(20) NOT NULL,
    icon VARCHAR(100),
    route_path VARCHAR(200),
    component_path VARCHAR(500),
    is_visible SMALLINT NOT NULL DEFAULT 1,
    is_cache SMALLINT NOT NULL DEFAULT 0,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    permission_code VARCHAR(100),
    external_link VARCHAR(500),
    keepalive SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_menu_id) REFERENCES sys_menu(menu_id)
);

COMMENT ON TABLE sys_menu IS '菜单主表';
COMMENT ON COLUMN sys_menu.menu_id IS '菜单ID';
COMMENT ON COLUMN sys_menu.menu_code IS '菜单编码';
COMMENT ON COLUMN sys_menu.menu_name IS '菜单名称';
COMMENT ON COLUMN sys_menu.parent_menu_id IS '上级菜单';
COMMENT ON COLUMN sys_menu.menu_type IS '菜单类型';

-- 5. 角色权限表 (sys_role_permission)
CREATE TABLE sys_role_permission (
    permission_id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    permission_type VARCHAR(20) NOT NULL,
    permission_value INT NOT NULL DEFAULT 0,
    is_inherited SMALLINT NOT NULL DEFAULT 0,
    parent_role_id BIGINT,
    data_scope_type VARCHAR(20),
    data_scope_value VARCHAR(500),
    org_scope_type VARCHAR(20),
    org_scope_value VARCHAR(500),
    field_permissions TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE (role_id, menu_id, permission_type),
    FOREIGN KEY (role_id) REFERENCES sys_role(role_id) ON DELETE CASCADE,
    FOREIGN KEY (menu_id) REFERENCES sys_menu(menu_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_role_id) REFERENCES sys_role(role_id)
);

COMMENT ON TABLE sys_role_permission IS '角色权限表';
COMMENT ON COLUMN sys_role_permission.permission_type IS '权限类型';
COMMENT ON COLUMN sys_role_permission.permission_value IS '权限值';
COMMENT ON COLUMN sys_role_permission.is_inherited IS '是否继承';

-- 6. 数据字典类型表 (sys_dict_type)
CREATE TABLE sys_dict_type (
    dict_type_id BIGSERIAL PRIMARY KEY,
    dict_type_code VARCHAR(50) NOT NULL UNIQUE,
    dict_type_name VARCHAR(100) NOT NULL,
    dict_category VARCHAR(50),
    is_system SMALLINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE sys_dict_type IS '数据字典类型表';

-- 7. 数据字典明细表 (sys_dict_data)
CREATE TABLE sys_dict_data (
    dict_data_id BIGSERIAL PRIMARY KEY,
    dict_type_id BIGINT NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_sort INT NOT NULL DEFAULT 0,
    is_default SMALLINT NOT NULL DEFAULT 0,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    css_class VARCHAR(100),
    list_class VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (dict_type_id) REFERENCES sys_dict_type(dict_type_id) ON DELETE CASCADE
);

COMMENT ON TABLE sys_dict_data IS '数据字典明细表';

-- 8. 操作日志表 (sys_operate_log)
CREATE TABLE sys_operate_log (
    log_id BIGSERIAL PRIMARY KEY,
    user_code VARCHAR(50) NOT NULL,
    user_name VARCHAR(100) NOT NULL,
    real_name VARCHAR(100),
    dept_code VARCHAR(50),
    dept_name VARCHAR(100),
    operate_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    mac_address VARCHAR(50),
    host_name VARCHAR(200),
    browser_info VARCHAR(500),
    operate_type VARCHAR(20) NOT NULL,
    operate_result VARCHAR(10) NOT NULL DEFAULT '成功',
    fail_reason VARCHAR(500),
    bill_type VARCHAR(50),
    bill_no VARCHAR(50),
    operate_content TEXT,
    operate_summary VARCHAR(500),
    module_name VARCHAR(100) NOT NULL,
    request_url VARCHAR(500),
    request_method VARCHAR(10),
    request_params TEXT,
    session_id VARCHAR(100),
    duration INT,
    org_code VARCHAR(50),
    trace_id VARCHAR(100),
    archive_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_operate_log IS '操作日志表';

-- 创建索引
CREATE INDEX idx_sys_user_code ON sys_user(user_code);
CREATE INDEX idx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_dept ON sys_user(dept_id);
CREATE INDEX idx_sys_user_employee ON sys_user(employee_id);
CREATE INDEX idx_sys_user_role_user ON sys_user_role(user_id);
CREATE INDEX idx_sys_user_role_role ON sys_user_role(role_id);
CREATE INDEX idx_sys_role_code ON sys_role(role_code);
CREATE INDEX idx_sys_menu_code ON sys_menu(menu_code);
CREATE INDEX idx_sys_menu_parent ON sys_menu(parent_menu_id);
CREATE INDEX idx_sys_role_permission_role ON sys_role_permission(role_id);
CREATE INDEX idx_sys_role_permission_menu ON sys_role_permission(menu_id);
CREATE INDEX idx_sys_dict_type_code ON sys_dict_type(dict_type_code);
CREATE INDEX idx_sys_dict_data_type ON sys_dict_data(dict_type_id);
CREATE INDEX idx_sys_operate_log_user ON sys_operate_log(user_code);
CREATE INDEX idx_sys_operate_log_time ON sys_operate_log(operate_time);
CREATE INDEX idx_sys_operate_log_type ON sys_operate_log(operate_type);
CREATE INDEX idx_sys_operate_log_module ON sys_operate_log(module_name);

-- ============================================================================
-- 系统管理模块建表完成
-- ============================================================================
