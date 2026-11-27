-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库完整初始化脚本
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：初始化数据脚本，需在建表脚本执行完毕后运行
-- ============================================================================

-- 注意：\i 引用已移至 SCHEMA_SCRIPTS 数组中按顺序独立执行
-- 如果需要单独使用本脚本，请先执行 01_basics.sql ~ 07_system.sql 建表脚本
-- \i schema/01_basics.sql
-- \i schema/02_sales.sql
-- \i schema/03_purchase.sql
-- \i schema/04_inventory.sql
-- \i schema/05_production.sql
-- \i schema/06_finance.sql
-- \i schema/07_system.sql

-- ============================================================================
-- 初始化数据
-- ============================================================================

-- 基础资料初始化数据
INSERT INTO currency (currency_code, currency_name, currency_symbol, is_base_currency, exchange_rate, status)
VALUES ('CNY', '人民币', '¥', 1, 1.00000000, '启用');

INSERT INTO unit_of_measure (unit_code, unit_name, unit_group, is_base_unit, conversion_rate, status)
VALUES
('PCS', '件', '个数', 1, 1.000000, '启用'),
('BOX', '盒', '个数', 0, 10.000000, '启用'),
('SET', '套', '个数', 0, 1.000000, '启用'),
('KG', '千克', '重量', 1, 1.000000, '启用'),
('G', '克', '重量', 0, 1000.000000, '启用'),
('M', '米', '长度', 1, 1.000000, '启用'),
('CM', '厘米', '长度', 0, 100.000000, '启用'),
('L', '升', '体积', 1, 1.000000, '启用'),
('ML', '毫升', '体积', 0, 1000.000000, '启用'),
('SQM', '平方米', '面积', 1, 1.000000, '启用'),
('TON', '吨', '重量', 0, 0.001000, '启用');

INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, status)
VALUES
('PT001', '货到付款', 0, 0, 0.0000, '启用'),
('PT002', '月结30天', 30, 0, 0.0000, '启用'),
('PT003', '月结60天', 60, 0, 0.0000, '启用'),
('PT004', '预付30%', 0, 0, 30.0000, '启用'),
('PT005', '季结90天', 90, 0, 0.0000, '启用');

INSERT INTO settlement_mode (settle_mode_code, settle_mode_name, settle_type, sort_order, status)
VALUES
('CASH', '现金', '收款', 1, '启用'),
('TRANSFER', '转账', '通用', 2, '启用'),
('CHECK', '支票', '收款', 3, '启用'),
('BILL', '汇票', '通用', 4, '启用'),
('ONLINE', '在线支付', '通用', 5, '启用');

INSERT INTO price_level (price_level_code, price_level_name, price_adjust_rate, sort_order, status)
VALUES
('PL01', '标准价格', 0.0000, 1, '启用'),
('PL02', '零售价', 10.0000, 2, '启用'),
('PL03', '批发价', -5.0000, 3, '启用'),
('PL04', 'VIP价', -15.0000, 4, '启用'),
('PL05', '代理价', -20.0000, 5, '启用');

-- 部门初始化数据
INSERT INTO department (dept_code, dept_name, parent_dept_id, dept_type, status)
VALUES
('D001', '总经理室', NULL, '决策部门', '启用'),
('D002', '销售部', NULL, '业务部门', '启用'),
('D003', '采购部', NULL, '业务部门', '启用'),
('D004', '仓库部', NULL, '业务部门', '启用'),
('D005', '生产部', NULL, '业务部门', '启用'),
('D006', '财务部', NULL, '职能部门', '启用'),
('D007', '人力资源部', NULL, '职能部门', '启用'),
('D008', '技术部', NULL, '职能部门', '启用');

-- 仓库初始化数据
INSERT INTO warehouse (stock_code, stock_name, stock_type, is_bin_control, is_mrp_available, status)
VALUES
('WH001', '原料仓', '原材料仓', 1, 1, '启用'),
('WH002', '成品仓', '成品仓', 1, 1, '启用'),
('WH003', '半成品仓', '半成品仓', 1, 1, '启用'),
('WH004', '包材仓', '辅助材料仓', 1, 1, '启用'),
('WH005', '不合格品仓', '废品仓', 0, 0, '启用');

-- 角色初始化数据
INSERT INTO sys_role (role_code, role_name, role_type, data_scope, is_system, sort_order, status)
VALUES
('SUPER_ADMIN', '超级管理员', '系统角色', '全部', 1, 1, '启用'),
('ADMIN', '系统管理员', '系统角色', '全部', 1, 2, '启用'),
('SALES_MANAGER', '销售主管', '业务角色', '本部门', 0, 3, '启用'),
('SALES', '销售人员', '业务角色', '本部门', 0, 4, '启用'),
('PURCHASE_MANAGER', '采购主管', '业务角色', '本部门', 0, 5, '启用'),
('PURCHASE', '采购人员', '业务角色', '本部门', 0, 6, '启用'),
('WAREHOUSE_MANAGER', '仓库主管', '业务角色', '本部门', 0, 7, '启用'),
('WAREHOUSE', '仓库人员', '业务角色', '本部门', 0, 8, '启用'),
('PRODUCTION_MANAGER', '生产主管', '业务角色', '本部门', 0, 9, '启用'),
('PRODUCTION', '生产人员', '业务角色', '本部门', 0, 10, '启用'),
('FINANCE_MANAGER', '财务主管', '业务角色', '本部门', 0, 11, '启用'),
('FINANCE', '财务人员', '业务角色', '本部门', 0, 12, '启用'),
('VIEWER', '查看者', '业务角色', '本部门', 0, 13, '启用');

-- 用户初始化数据（密码为 admin123，需要加密）
INSERT INTO sys_user (user_code, username, password, real_name, user_type, is_admin, is_enabled, status)
VALUES
('U001', 'admin', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '系统管理员', '系统用户', 1, 1, '启用'),
('U002', 'manager', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '张总', '普通用户', 0, 1, '启用'),
('U003', 'sale01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '李销售', '普通用户', 0, 1, '启用'),
('U004', 'purchase01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '王采购', '普通用户', 0, 1, '启用'),
('U005', 'warehouse01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '赵仓管', '普通用户', 0, 1, '启用'),
('U006', 'production01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '钱生产', '普通用户', 0, 1, '启用'),
('U007', 'finance01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '孙财务', '普通用户', 0, 1, '启用');

-- 用户角色关联
INSERT INTO sys_user_role (user_id, role_id)
VALUES
(1, 1),  -- admin -> 超级管理员
(2, 3),  -- manager -> 销售主管
(3, 4),  -- sale01 -> 销售人员
(4, 6),  -- purchase01 -> 采购人员
(5, 8),  -- warehouse01 -> 仓库人员
(6, 10), -- production01 -> 生产人员
(7, 12); -- finance01 -> 财务人员

-- 数据字典类型初始化
INSERT INTO sys_dict_type (dict_type_code, dict_type_name, dict_category, is_system, sort_order, status)
VALUES
('bill_status', '单据状态', '业务', 1, 1, '启用'),
('approval_status', '审核状态', '业务', 1, 2, '启用'),
('material_type', '物料类型', '基础资料', 1, 3, '启用'),
('warehouse_type', '仓库类型', '基础资料', 1, 4, '启用'),
('customer_type', '客户类型', '基础资料', 1, 5, '启用'),
('supplier_type', '供应商类型', '基础资料', 1, 6, '启用'),
('gender', '性别', '系统', 1, 7, '启用'),
('yes_no', '是否', '系统', 1, 8, '启用'),
('priority', '优先级', '业务', 1, 9, '启用'),
('order_status', '工单状态', '生产', 1, 10, '启用');

-- 数据字典明细初始化
INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, dict_sort, is_default, status)
VALUES
-- 单据状态
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '草稿', '草稿', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '已提交', '已提交', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '已生效', '已生效', 3, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '已关闭', '已关闭', 4, 0, '启用'),
-- 审核状态
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '待审核', '待审核', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '审核中', '审核中', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '已生效', '已生效', 3, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '已驳回', '已驳回', 4, 0, '启用'),
-- 物料类型
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '原材料', '原材料', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '半成品', '半成品', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '成品', '成品', 3, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '包材', '包材', 4, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '辅助材料', '辅助材料', 5, 0, '启用'),
-- 性别
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'gender'), '男', '男', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'gender'), '女', '女', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'gender'), '未知', '未知', 3, 0, '启用'),
-- 是否
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'yes_no'), '是', '是', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'yes_no'), '否', '否', 2, 0, '启用'),
-- 优先级
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '紧急', '紧急', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '高', '高', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '普通', '普通', 3, 1, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '低', '低', 4, 0, '启用'),
-- 工单状态
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'order_status'), '计划', '计划', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'order_status'), '已下达', '已下达', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'order_status'), '生产中', '生产中', 3, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'order_status'), '已完成', '已完成', 4, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'order_status'), '已关闭', '已关闭', 5, 0, '启用');

-- ============================================================================
-- 创建序列（确保所有序列从最大ID开始）
-- ============================================================================
SELECT setval('currency_currency_id_seq', (SELECT COALESCE(MAX(currency_id), 1) FROM currency));
SELECT setval('unit_of_measure_unit_id_seq', (SELECT COALESCE(MAX(unit_id), 1) FROM unit_of_measure));
SELECT setval('material_category_category_id_seq', (SELECT COALESCE(MAX(category_id), 1) FROM material_category));
SELECT setval('sys_user_user_id_seq', (SELECT COALESCE(MAX(user_id), 1) FROM sys_user));
SELECT setval('sys_role_role_id_seq', (SELECT COALESCE(MAX(role_id), 1) FROM sys_role));
SELECT setval('sys_menu_menu_id_seq', (SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu));
SELECT setval('sys_dict_type_dict_type_id_seq', (SELECT COALESCE(MAX(dict_type_id), 1) FROM sys_dict_type));
SELECT setval('sys_dict_data_dict_data_id_seq', (SELECT COALESCE(MAX(dict_data_id), 1) FROM sys_dict_data));

-- ============================================================================
-- 默认角色菜单权限初始化 (sys_role_permission)
-- 说明: menu_id 存的是前端菜单 path(如 /sale/orders) 或整组标识(如 group_basedata)
-- ============================================================================

-- 超级管理员/系统管理员 通配 * 全部可见
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, '*', '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r WHERE r.role_code IN ('SUPER_ADMIN','ADMIN');

-- 销售 / 销售主管
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_acquisition'),('group_crm'),('group_quote'),
    ('group_sale'),('group_logistics'),('group_inventory'),('group_price'),
    ('group_report'),('group_system'),('/system/settings')
) AS ids(menu_id) WHERE r.role_code IN ('SALES','SALES_MANAGER');

-- 采购 / 采购主管
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_purchase'),('group_inventory'),('group_price'),
    ('group_report'),('group_system'),('/system/settings')
) AS ids(menu_id) WHERE r.role_code IN ('PURCHASE','PURCHASE_MANAGER');

-- 仓库 / 仓库主管
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_inventory'),('group_report'),
    ('group_system'),('/system/settings')
) AS ids(menu_id) WHERE r.role_code IN ('WAREHOUSE','WAREHOUSE_MANAGER');

-- 生产 / 生产主管
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_masterdata'),('/masterdata/bom'),
    ('group_production'),('group_subcontract'),('group_sample'),('group_freesample'),
    ('group_inventory'),('group_report'),('group_system'),('/system/settings')
) AS ids(menu_id) WHERE r.role_code IN ('PRODUCTION','PRODUCTION_MANAGER');

-- 财务 / 财务主管
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_sale'),('group_purchase'),('group_inventory'),('group_finance'),
    ('group_report'),('group_system'),('/system/settings')
) AS ids(menu_id) WHERE r.role_code IN ('FINANCE','FINANCE_MANAGER');

-- 查看者
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_report'),('group_system'),('/system/settings')
) AS ids(menu_id) WHERE r.role_code = 'VIEWER';

-- 重置序列
SELECT setval('sys_role_permission_permission_id_seq',
    COALESCE((SELECT MAX(permission_id) FROM sys_role_permission),1), true);

-- ============================================================================
-- 数据库初始化完成
-- ============================================================================
