-- 129_field_persistence_batch3.sql
-- Idempotent: safe to run multiple times (every statement uses ADD COLUMN IF NOT EXISTS).
-- Purpose: backfill 23 columns that exist in the UI (the user can fill / select them)
--          but were missing in BOTH the backend entity and the database, so the value
--          looked "saved" (success toast) yet was lost after refresh.
-- Scope: record-only fields. They are NOT used by any business decision logic.
-- Rules: no DROP / no DELETE / no UPDATE of existing data. ASCII only.

ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS is_consignment varchar(255);
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS picker_id bigint;
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS customer_short_name varchar(255);
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS payment_term_id bigint;
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS price_level_id bigint;
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS freight numeric(18,4);
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS payment_term_id bigint;
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS price_level_id bigint;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS payment_term_id bigint;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS price_level_id bigint;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS payment_term_id bigint;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS contact_person varchar(255);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS contact_phone varchar(255);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS payment_method varchar(255);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS currency_id bigint;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS contact_person varchar(255);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS contact_phone varchar(255);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS currency_id bigint;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS discount_rate numeric(18,2);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS contact_person varchar(255);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS contact_phone varchar(255);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS currency_id bigint;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS discount_rate numeric(18,2);

-- 2026-09-28 batch3 follow-up: customer/supplier credit days + unify sale_delivery freight column.
ALTER TABLE customer ADD COLUMN IF NOT EXISTS credit_days integer;
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS credit_days integer;
UPDATE sale_delivery SET freight = delivery_cost WHERE (freight IS NULL OR freight = 0) AND delivery_cost IS NOT NULL AND delivery_cost <> 0;
