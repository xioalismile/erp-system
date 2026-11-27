-- 78_purchase_contract_require_source.sql
-- 2026-09-15 设置中心-系统参数设置-采购参数：新增「采购合同必须有来源单号才能保存」开关
--
-- 背景：
--   客户要求新建采购合同不能凭空录入，必须由上游单据带出来（采购申请单 / 销售合同）。
--   全站已有 3 个同族开关（purchase.order.require.source / purchase.inbound.require.source /
--   purchase.return.require.source），本次补齐采购合同这一个。
--
-- 校验口径（用户 2026-09-15 确认）：
--   ① 判定取【明细行】purchase_contract_item.source_bill_no —— 采购合同【表头没有】来源单号列
--      （已有 4 处实查互证：实体无该属性 / 全库 SQL grep 零命中 / 本地库列清单无该列 /
--        BillLinkRegistry 给采购合同注册的来源列是 null,null），来源信息按系统既有设计只存在明细行，
--      由「选单」或上游单据「下推」写入；任一明细行有非空来源单号即视为满足。
--   ② 每次保存都校验（不限新建）。
--   ③ 采购合同 Excel 批量导入走同一个 saveContractWithItems → 受同一开关约束
--      （模板不含来源单号列，开关开启后导入会逐单失败，属用户已确认的预期行为）。
--
-- 生效方式：运行时读取（SysConfigServiceImpl Caffeine 缓存，batchUpdate 会立即刷新缓存）
--           → 界面改完立即生效、无需重启；配置缺失时 getBooleanValue(key,false) 回退
--           「不校验」= 与升级前行为完全一致（fail-safe 不拦单）。
--
-- 幂等：WHERE NOT EXISTS（【铁律】禁用 ON CONFLICT：PG 环境该唯一约束可能缺失，
--       报错会中止整次部署，见 2026-09-12 事故）。
-- sort_order=165：让「采购合同」分组落在「采购订单」(100-160) 之后、「公用参数」(170) 之前，
--       不改动任何既有行的 sort_order。

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'purchase.contract.require.source', 'false', '采购合同必须有来源单号才能保存',
       'purchase', '采购合同', 'boolean', 165,
       '开启后采购合同必须有来源单号才能保存：请通过「选单」选择采购申请单/销售合同，或由上游单据「下推」生成（Excel 导入也会被拦截）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'purchase.contract.require.source');
