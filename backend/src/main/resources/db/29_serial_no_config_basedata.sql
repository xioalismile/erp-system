-- =============================================================
-- 29_serial_no_config_basedata.sql
-- 为基础资料补充 sys_serial_no_config 配置，支持编码自动生成
-- 执行方式：psql -U erp_user -d erp_db -h localhost -p 5432 -f 29_serial_no_config_basedata.sql
-- =============================================================
SET client_encoding TO 'UTF8';

-- 1. 系统角色 SYS_ROLE
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'SYS_ROLE', '系统角色', 'ROLE', '', 4, 'NONE', '',
  0, '', 101, 1, 1, '系统角色编码（如 ROLE0001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SYS_ROLE');

-- 2. 员工档案 BD_EMPLOYEE
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'BD_EMPLOYEE', '员工档案', 'EMP', 'yyyyMMdd', 4, 'DAILY', '-',
  0, '', 102, 1, 1, '员工工号编码规则'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'BD_EMPLOYEE');

-- 3. 部门档案 BD_DEPARTMENT
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'BD_DEPARTMENT', '部门档案', 'DEPT', '', 4, 'NONE', '',
  0, '', 103, 1, 1, '部门编码（如 DEPT0001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'BD_DEPARTMENT');

-- 4. 仓库档案 BD_WAREHOUSE
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'BD_WAREHOUSE', '仓库档案', 'WH', '', 3, 'NONE', '',
  0, '', 104, 1, 1, '仓库编码（如 WH001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'BD_WAREHOUSE');

-- 5. 客户档案 MD_CUSTOMER
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'MD_CUSTOMER', '客户档案', 'CUS', 'yyyyMMdd', 4, 'DAILY', '-',
  0, '', 105, 1, 1, '客户编码规则'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MD_CUSTOMER');

-- 6. 供应商档案 MD_SUPPLIER
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'MD_SUPPLIER', '供应商档案', 'SUP', 'yyyyMMdd', 4, 'DAILY', '-',
  0, '', 106, 1, 1, '供应商编码规则'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MD_SUPPLIER');

-- 7. 物料档案 MD_MATERIAL
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'MD_MATERIAL', '物料档案', 'MAT', '', 6, 'NONE', '',
  0, '', 107, 1, 1, '物料编码（如 MAT000001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MD_MATERIAL');

-- 8. 物料分类 BD_MATERIAL_CATEGORY
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'BD_MATERIAL_CATEGORY', '物料分类', 'CAT', '', 4, 'NONE', '',
  0, '', 108, 1, 1, '物料分类编码（如 CAT0001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'BD_MATERIAL_CATEGORY');

-- 9. 币种档案 BD_CURRENCY
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'BD_CURRENCY', '币种档案', 'CUR', '', 3, 'NONE', '',
  0, '', 109, 1, 1, '币种编码（如 CUR001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'BD_CURRENCY');

-- 10. 计量单位 BD_UNIT
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'BD_UNIT', '计量单位', 'U', '', 3, 'NONE', '',
  0, '', 110, 1, 1, '计量单位编码（如 U001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'BD_UNIT');

-- 11. BOM物料清单 MD_BOM
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'MD_BOM', 'BOM物料清单', 'BOM', 'yyyyMMdd', 4, 'DAILY', '-',
  0, '', 111, 1, 1, 'BOM单编码规则'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MD_BOM');

-- 12. 审批流程 WF_APPROVAL_FLOW
INSERT INTO sys_serial_no_config (
  doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
  current_serial, "current_date", sort_order, enabled, auto_generate, remark
)
SELECT 'WF_APPROVAL_FLOW', '审批流程定义', 'FLOW', '', 4, 'NONE', '',
  0, '', 112, 1, 1, '审批流程编码（如 FLOW0001）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'WF_APPROVAL_FLOW');
