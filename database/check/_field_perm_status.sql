SET client_encoding TO 'UTF8';
\pset pager off
-- 字段权限现状：哪些角色/页面配了 required=1（这些才会触发本次新增的叠加逻辑）
SELECT r.role_code, r.role_name, p.page_key, count(*) AS required_fields,
       string_agg(p.field_key, ', ' ORDER BY p.field_key) AS fields
FROM sys_role_field_permission p
JOIN sys_role r ON r.role_id = p.role_id
WHERE p.required = 1
GROUP BY r.role_code, r.role_name, p.page_key
ORDER BY r.role_code, p.page_key;

-- 总览
SELECT (SELECT count(*) FROM sys_role_field_permission) AS perm_rows,
       (SELECT count(*) FROM sys_role_field_permission WHERE required = 1) AS required_rows,
       (SELECT count(*) FROM sys_role_field_permission WHERE visible = 1 AND editable = 1) AS normal_rows;
