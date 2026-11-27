-- 107_attachment_urls_production_receipt.sql
-- 目的：生产入库单补附件列 attachment_urls。
-- 背景（2026-09-20 8cloud 端到端闭环实测）：实体 ProductionReceipt 已映射 attachment_urls，
--   但该列在 erp_db / erp_tenant_template / 租户独立库**都不存在**（此前注释误判为“列已存在，无需补”），
--   导致保存生产入库单时 PostgreSQL 报 42703（列不存在）→ 事务被中止 → 前端只看到「当前事务被终止」。
-- 幂等：ADD COLUMN IF NOT EXISTS；只增不删、不改类型、不动既有数据。
-- 多租户：本文件只覆盖执行时的那一个库；每个租户库另由启动自愈 SchemaUpgradeEnsurer
--   BUSINESS_COLUMN_SPECS 清单逐库补齐（2026-09-20 起该清单对主库 + 全部租户库各跑一遍）。
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);