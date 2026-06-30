-- H2数据库演示数据

INSERT INTO sys_user (username, password, real_name, department, role) VALUES
('admin', 'admin123', '系统管理员', '信息部', 'admin'),
('sale01', '123456', '张三', '销售一部', 'user'),
('store01', '123456', '李四', '仓库', 'user');

INSERT INTO customer (customer_code, customer_name, contact_person, contact_phone, address, payment_method) VALUES
('CRM-001', '华南精密制造有限公司', '张工', '0755-88881234', '深圳市宝安区西乡街道XX工业园A栋3楼', '月结30天'),
('CRM-002', '东方机械集团', '王经理', '021-66667788', '上海市浦东新区张江高科技园区', '月结60天'),
('CRM-003', '北方重工股份', '李总', '010-88889999', '北京市昌平区科技园路8号', '款到发货');

INSERT INTO material (material_code, material_name, specification, unit, category, purchase_price, sale_price, safety_stock, lead_time_days) VALUES
('MAT-001', '精密轴承A', '6204-2RS', '个', '轴承', 18.00, 25.50, 200, 7),
('MAT-002', '传动轴B', 'D20xL300', '根', '轴类', 45.00, 68.00, 50, 14),
('MAT-003', '控制板C', 'V2.0', '片', '电子件', 80.00, 120.00, 30, 21),
('MAT-004', '外壳组件', 'BOX-100', '套', '结构件', 50.00, 88.00, 100, 10),
('MAT-005', '螺丝M8', 'D8x30', '个', '标准件', 0.50, 1.00, 2000, 3);

INSERT INTO inventory (material_code, material_name, warehouse_code, quantity, safety_stock) VALUES
('MAT-001', '精密轴承A', 'WH001', 500, 200),
('MAT-002', '传动轴B', 'WH001', 80, 50),
('MAT-003', '控制板C', 'WH001', 15, 30),
('MAT-004', '外壳组件', 'WH001', 120, 100),
('MAT-005', '螺丝M8', 'WH001', 3000, 2000);

INSERT INTO bom (parent_material_code, child_material_code, usage_quantity, version, effective_date) VALUES
('MAT-001', 'MAT-004', 0.5, '1.0', '2026-01-01'),
('MAT-001', 'MAT-005', 4, '1.0', '2026-01-01');

INSERT INTO supplier (supplier_code, supplier_name, contact_person, contact_phone, address, payment_method, lead_time_days) VALUES
('SUP-001', '深圳精密轴承有限公司', '陈经理', '0755-26551234', '深圳市南山区科技园路10号', '月结30天', 7),
('SUP-002', '上海传动设备股份', '刘总', '021-58889999', '上海市闵行区莘庄工业区', '月结60天', 14),
('SUP-003', '北京控制技术研究所', '王工', '010-82658888', '北京市海淀区中关村大街18号', '款到发货', 21);
