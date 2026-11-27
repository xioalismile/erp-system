-- 66_ui_bill_link_switch.sql
-- 2026-09-11 来源/去向单据页签：定制功能运行时开关
--
-- 背景：「来源单据 / 去向单据」页签（销售报价单/合同/订单/出库单/发票/退货单 共 6 个编辑页顶部）
--       属定制功能，客户服务器（121）不同步。改由本开关控制"本部署是否可见"：
--         true  = 显示（默认，与未加开关前的行为完全一致）
--         false = 不显示（后端 /api/bill-link/* 一律按"未登记"返回：/registered=false、list 为空，
--                 前端 6 个销售编辑页的页签随 v-if 不渲染 —— 前端零改动）
--
-- 落位：config_group='sys_param' → 设置中心「系统参数」页签自动渲染（KgwSysParam 为数据驱动），
--       config_type='boolean' → 渲染成开关；保存走 PUT /batch upsert，同 JVM 缓存即时生效。
--
-- 幂等：WHERE NOT EXISTS —— 已存在的用户配置绝不会被覆盖
--       （因此 121 上若已手动置 false，重复部署不会把它改回 true）
--       【2026-09-12 修正】原先用 ON CONFLICT (config_key) DO NOTHING，但 PG 环境建表时缺
--       config_key 唯一约束（sys_config_schema.sql 里是 MySQL 的 UNIQUE KEY，PG 建表未生效），
--       该写法在 PG 上会报「没有匹配 ON CONFLICT 说明的唯一或者排除约束」并中止整次部署。
--       改为不依赖唯一约束的 WHERE NOT EXISTS，语义完全等价，且对任何库（含约束未补齐的库）都健壮。
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_section, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.bill_link.enabled', 'true', '来源/去向单据页签', 'sys_param', 'boolean', '界面显示', 96, '关闭后销售链路编辑页不显示「来源单据/去向单据」页签（定制功能隔离用）；默认开启', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.bill_link.enabled');
