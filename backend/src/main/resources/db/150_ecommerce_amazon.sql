-- ============================================================
-- 72_ecommerce_amazon.sql
-- 用途：电商平台（第一期：亚马逊北美站）—— 建表 + 菜单初始化 + 角色自动授权
--       （守则 04 初始化 2.7.1-1/2.7.1-2/2.7.1-7、守则 05 八层闭环 2.8.1-5/2.8.1-6）
--
-- 范围（已确认立项）：
--   1. 四张新表：电商店铺授权 / 电商商品映射 / 电商平台订单 / 电商平台订单明细
--   2. 一级菜单「电商平台」+ 三个子菜单（店铺授权 / 亚马逊订单 / 商品映射）
--   3. 角色授权：超级管理员（SUPER_ADMIN）+ 系统管理员（ADMIN）
--
-- 幂等：建表 IF NOT EXISTS；菜单/授权 INSERT 走 NOT EXISTS；重复执行结果一致。
-- 执行后需重新登录（menuRoutePaths 在登录时缓存）。
-- ============================================================

BEGIN;

-- ---------- 1. 电商店铺授权 ----------
CREATE TABLE IF NOT EXISTS ecom_shop (
    id                      BIGSERIAL PRIMARY KEY,
    shop_name               VARCHAR(100) NOT NULL,
    platform                VARCHAR(20)  NOT NULL DEFAULT 'AMAZON',
    region                  VARCHAR(10)  NOT NULL DEFAULT 'NA',
    marketplace_id          VARCHAR(50)  NOT NULL DEFAULT 'ATVPDKIKX0DER',
    seller_id               VARCHAR(100),
    client_id               VARCHAR(500),
    client_secret           VARCHAR(500),
    refresh_token           VARCHAR(1000),
    auth_status             VARCHAR(20)  NOT NULL DEFAULT '未授权',
    last_auth_time          TIMESTAMP,
    associate_customer_id   BIGINT,
    associate_customer_code VARCHAR(50),
    associate_customer_name VARCHAR(200),
    status                  SMALLINT     NOT NULL DEFAULT 1,
    remark                  VARCHAR(500),
    create_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP,
    create_by               VARCHAR(50),
    CONSTRAINT uk_ecom_shop_name UNIQUE (shop_name)
);
CREATE INDEX IF NOT EXISTS idx_ecom_shop_status ON ecom_shop (status);

-- ---------- 2. 电商商品映射 ----------
CREATE TABLE IF NOT EXISTS ecom_product_mapping (
    id           BIGSERIAL PRIMARY KEY,
    shop_id      BIGINT        NOT NULL,
    shop_name    VARCHAR(100),
    seller_sku   VARCHAR(100)  NOT NULL,
    asin         VARCHAR(50),
    product_name VARCHAR(500),
    material_id  BIGINT        NOT NULL,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP,
    create_by    VARCHAR(50),
    CONSTRAINT uk_ecom_mapping_shop_sku UNIQUE (shop_id, seller_sku)
);
CREATE INDEX IF NOT EXISTS idx_ecom_mapping_material ON ecom_product_mapping (material_id);

-- ---------- 3. 电商平台订单 ----------
CREATE TABLE IF NOT EXISTS ecom_platform_order (
    id                      BIGSERIAL PRIMARY KEY,
    shop_id                 BIGINT        NOT NULL,
    shop_name               VARCHAR(100),
    platform_order_id       VARCHAR(50)   NOT NULL,
    purchase_date           TIMESTAMP,
    order_status            VARCHAR(50),
    payment_method          VARCHAR(100),
    currency_code           VARCHAR(10),
    order_total             NUMERIC(18,2),
    item_count              INTEGER,
    ship_name               VARCHAR(200),
    ship_city               VARCHAR(100),
    ship_state              VARCHAR(100),
    ship_country            VARCHAR(10),
    ship_address            VARCHAR(500),
    converted               SMALLINT      NOT NULL DEFAULT 0,
    converted_sale_order_id BIGINT,
    converted_sale_order_no VARCHAR(50),
    pull_time               TIMESTAMP,
    raw_json                TEXT,
    create_time             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP,
    CONSTRAINT uk_ecom_order_platform_id UNIQUE (platform_order_id)
);
CREATE INDEX IF NOT EXISTS idx_ecom_order_shop ON ecom_platform_order (shop_id, purchase_date DESC);
CREATE INDEX IF NOT EXISTS idx_ecom_order_converted ON ecom_platform_order (converted);

-- ---------- 4. 电商平台订单明细 ----------
CREATE TABLE IF NOT EXISTS ecom_platform_order_item (
    id                   BIGSERIAL PRIMARY KEY,
    order_id             BIGINT        NOT NULL,
    platform_item_id     VARCHAR(100),
    line_no              INTEGER,
    seller_sku           VARCHAR(100),
    asin                 VARCHAR(50),
    product_name         VARCHAR(500),
    quantity             NUMERIC(18,4) DEFAULT 0,
    unit_price           NUMERIC(18,6),
    item_total           NUMERIC(18,2),
    mapped_material_id   BIGINT,
    mapped_material_code VARCHAR(50),
    mapped_material_name VARCHAR(200),
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ecom_order_item_order ON ecom_platform_order_item (order_id);

-- ---------- 5. 一级菜单「电商平台」+ 三个子菜单 ----------
-- 顶级目录：参照现有惯例（CATALOG / keepalive=0 / version='STANDARD' / permission_code 空）
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT 'ecommerce', '电商平台', NULL, 'CATALOG', '/ecommerce', NULL,
       160, 'Cpu', 1, 1, 0, 0, '启用', 'STANDARD'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = 'ecommerce');

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT v.menu_code, v.menu_name,
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'ecommerce'),
       'MENU', v.route_path, v.permission_code,
       v.sort_order, NULL, 1, 1, 0, 0, '启用', 'STANDARD'
FROM (VALUES
    ('ecommerce_shop',    '店铺授权',  '/ecommerce/shop',    'ecommerce:shop',    10),
    ('ecommerce_orders',  '亚马逊订单','/ecommerce/orders',  'ecommerce:orders',  20),
    ('ecommerce_mapping', '商品映射',  '/ecommerce/mapping', 'ecommerce:mapping', 30)
) AS v(menu_code, menu_name, route_path, permission_code, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = v.menu_code);

-- 兜底修复父子关系（父目录后补的场景）
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_code = 'ecommerce' LIMIT 1)
 WHERE menu_code IN ('ecommerce_shop', 'ecommerce_orders', 'ecommerce_mapping')
   AND parent_menu_id IS NULL;

-- ---------- 6. 角色授权（超级管理员 + 系统管理员；sys_role_permission.menu_id 存 route_path）----------
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND m.route_path IN ('/ecommerce', '/ecommerce/shop', '/ecommerce/orders', '/ecommerce/mapping')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（预期：目录 1 + 子菜单 3 + 授权 4×2 = 8 行）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code, sort_order
--   FROM sys_menu WHERE route_path LIKE '/ecommerce%' ORDER BY sort_order;
--
-- SELECT p.role_id, r.role_code, p.menu_id FROM sys_role_permission p
--   JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id LIKE '/ecommerce%' ORDER BY p.menu_id, r.role_code;
--
-- SELECT COUNT(*) FROM ecom_shop;
-- SELECT COUNT(*) FROM ecom_product_mapping;
-- SELECT COUNT(*) FROM ecom_platform_order;
-- SELECT COUNT(*) FROM ecom_platform_order_item;
