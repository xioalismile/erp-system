-- ============================================================
-- 62_attachment_urls_sample_tables.sql
-- 目的：给「打样单」「免费样品订单」补附件列 attachment_urls，打通这 3 个单据页的附件上传。
--
-- 背景：全站 36 个单据页已挂附件组件（FileUploader），剩余 3 页因"实体无附件字段"被跳过：
--   - production/ProductionReceiptEdit：实体 ProductionReceipt.java 无 @TableField 映射，
--     但 production_receipt 表**列已存在** → 只补实体字段映射，本脚本不含它。
--   - sample/SampleEdit：实体 SampleOrder.java + 建表语句（13_sample_module.sql）均无该列 → 本脚本补列。
--   - freesample/FreeSampleEdit：实体 FreeSampleOrder.java + 建表语句（15_customer_driven_features.sql）
--     均无该列 → 本脚本补列。
--
-- 幂等：ADD COLUMN IF NOT EXISTS；只增不删、不改类型、不动已有列。
-- 多租户：本文件只覆盖执行时那一个库；每个租户库由启动时
--   SchemaUpgradeEnsurer.ensureBusinessDbColumns() 的同名列清单逐库补齐（幂等）。
-- 口径：与全站既有约定一致 —— 前端 form.attachmentUrls（逗号分隔 URL 字符串）↔ 实体 attachmentUrls。
-- ============================================================

ALTER TABLE sample_order      ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
