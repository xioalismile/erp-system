-- 基础资料联动补字段与存量回填（2026-11-08）
-- 内容：客户/供应商新增默认税率、供应商新增默认付款方式、报价单新增客户编号；
--       销售订单/报价/合同的空白客户简称、报价的空白客户编号、采购合同的空白供应商编号与简称按档案回填。
-- 全部幂等；只填空值，不覆盖用户已填内容；同内容文件另存 deploy/sql/188_master_linkage_fields.sql。

ALTER TABLE customer      ADD COLUMN IF NOT EXISTS tax_rate       NUMERIC(5,2);
ALTER TABLE supplier      ADD COLUMN IF NOT EXISTS tax_rate       NUMERIC(5,2);
ALTER TABLE supplier      ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50);
ALTER TABLE sale_quote    ADD COLUMN IF NOT EXISTS customer_no    VARCHAR(64);

UPDATE sale_order o SET customer_short_name = c.short_name
  FROM customer c
 WHERE o.customer_id = c.customer_id
   AND COALESCE(o.customer_short_name, '') = ''
   AND COALESCE(c.short_name, '') <> '';

UPDATE sale_quote q SET customer_short_name = c.short_name
  FROM customer c
 WHERE q.customer_id = c.customer_id
   AND COALESCE(q.customer_short_name, '') = ''
   AND COALESCE(c.short_name, '') <> '';

UPDATE sale_contract t SET customer_short_name = c.short_name
  FROM customer c
 WHERE t.customer_id = c.customer_id
   AND COALESCE(t.customer_short_name, '') = ''
   AND COALESCE(c.short_name, '') <> '';

UPDATE sale_quote q SET customer_no = c.customer_code
  FROM customer c
 WHERE q.customer_id = c.customer_id
   AND COALESCE(q.customer_no, '') = ''
   AND COALESCE(c.customer_code, '') <> '';

UPDATE purchase_contract p SET supplier_no = s.supplier_code
  FROM supplier s
 WHERE p.supplier_id = s.supplier_id
   AND COALESCE(p.supplier_no, '') = ''
   AND COALESCE(s.supplier_code, '') <> '';

UPDATE purchase_contract p SET supplier_short_name = s.short_name
  FROM supplier s
 WHERE p.supplier_id = s.supplier_id
   AND COALESCE(p.supplier_short_name, '') = ''
   AND COALESCE(s.short_name, '') <> '';
