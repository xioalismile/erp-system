-- ===========================================================================
-- currency 脏数据清理 + 补唯一约束
-- 背景：线上 erp_db 的 currency 表缺少 01_basics.sql / full_schema.sql 中
--       已声明的 currency_code UNIQUE 约束，导致出现 4 行重复的 CNY(基础币种)。
--       重复编码会让 CurrencyServiceImpl.getByCode 的 getOne 抛
--       TooManyResultsException（保存信誉额度即 500），必须从根上堵住。
-- ===========================================================================
SET client_encoding TO 'UTF8';
BEGIN;

-- 1) 采购订单中有 3 行引用了重复币种 9（与 1 同为 CNY，汇率同为 1），改指 1，语义不变
UPDATE purchase_order SET currency_id = 1 WHERE currency_id = 9;

-- 2) 删除重复基础币种 7/8/9，保留最早且被正常引用的 1
DELETE FROM currency WHERE currency_id IN (7, 8, 9);

-- 3) 补上 DDL 已声明却缺失的唯一约束，从根上阻止重复编码再次产生
ALTER TABLE currency DROP CONSTRAINT IF EXISTS uk_currency_code;
ALTER TABLE currency ADD CONSTRAINT uk_currency_code UNIQUE (currency_code);

-- 4) 复核
SELECT count(*) AS base_currency_cnt FROM currency WHERE is_base_currency = 1;
SELECT currency_id, currency_code, currency_name, is_base_currency, status FROM currency ORDER BY currency_id;
SELECT count(*) AS po_ref_789 FROM purchase_order WHERE currency_id IN (7, 8, 9);

COMMIT;
