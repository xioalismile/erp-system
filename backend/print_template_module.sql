-- ========================================
-- 打印模板配置模块
-- ========================================

-- 打印模板表
DROP TABLE IF EXISTS print_template;
CREATE TABLE print_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    doc_type VARCHAR(64) NOT NULL COMMENT '单据类型: SALE_ORDER/PURCHASE_ORDER/SALE_DELIVERY/PURCHASE_INBOUND等',
    template_type VARCHAR(32) DEFAULT 'HTML' COMMENT '模板类型: PDF/Excel/Word/HTML',
    template_content TEXT COMMENT '模板内容, JSON或HTML或Base64',
    template_style TEXT COMMENT '模板样式, CSS',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认模板: 0否 1是',
    page_size VARCHAR(32) DEFAULT 'A4' COMMENT '纸张大小: A4/A5/Legal/Custom',
    page_orientation VARCHAR(32) DEFAULT 'portrait' COMMENT '纸张方向: portrait纵向/landscape横向',
    margin_top INT DEFAULT 20 COMMENT '上边距(mm)',
    margin_bottom INT DEFAULT 20 COMMENT '下边距(mm)',
    margin_left INT DEFAULT 20 COMMENT '左边距(mm)',
    margin_right INT DEFAULT 20 COMMENT '右边距(mm)',
    header_content TEXT COMMENT '页眉内容',
    footer_content TEXT COMMENT '页脚内容',
    watermark VARCHAR(128) COMMENT '水印文字',
    watermark_enabled TINYINT DEFAULT 0 COMMENT '是否启用水印: 0否 1是',
    status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    remark VARCHAR(500) COMMENT '备注',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_code (template_code),
    KEY idx_doc_type (doc_type),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板表';

-- 打印模板字段配置表
DROP TABLE IF EXISTS print_template_field;
CREATE TABLE print_template_field (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    field_key VARCHAR(128) NOT NULL COMMENT '字段标识',
    field_name VARCHAR(128) NOT NULL COMMENT '字段名称',
    field_type VARCHAR(32) DEFAULT 'text' COMMENT '字段类型: text/number/date/image/table',
    display_format VARCHAR(128) COMMENT '显示格式: 如日期格式、金额格式',
    position_x INT COMMENT 'X坐标(精确定位用)',
    position_y INT COMMENT 'Y坐标(精确定位用)',
    width INT COMMENT '宽度',
    height INT COMMENT '高度',
    font_size INT COMMENT '字体大小',
    font_weight VARCHAR(32) COMMENT '字体粗细: normal/bold',
    font_color VARCHAR(32) COMMENT '字体颜色',
    alignment VARCHAR(32) DEFAULT 'left' COMMENT '对齐方式: left/center/right',
    visible TINYINT DEFAULT 1 COMMENT '是否显示: 0否 1是',
    sort_order INT DEFAULT 0 COMMENT '排序',
    remark VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_template_id (template_id),
    KEY idx_field_key (field_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板字段配置表';

-- ========================================
-- 初始模板数据
-- ========================================

-- 销售订单默认模板
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style,
    is_default, page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content,
    watermark, watermark_enabled,
    status, sort_order, remark
) VALUES (
    'SALE_ORDER_DEFAULT', '销售订单默认模板', 'SALE_ORDER', 'HTML',
    '<div class=\"document\">
  <div class=\"title\">销售订单</div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">订单编号：</span><span class=\"value\">{{orderNo}}</span></div>
    <div class=\"info-item\"><span class=\"label\">订单日期：</span><span class=\"value\">{{orderDate}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">客户名称：</span><span class=\"value\">{{customerName}}</span></div>
    <div class=\"info-item\"><span class=\"label\">联系人：</span><span class=\"value\">{{contactName}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">联系电话：</span><span class=\"value\">{{contactPhone}}</span></div>
    <div class=\"info-item\"><span class=\"label\">地址：</span><span class=\"value\">{{address}}</span></div>
  </div>
  <table class=\"item-table\">
    <thead>
      <tr>
        <th>序号</th>
        <th>商品编码</th>
        <th>商品名称</th>
        <th>规格型号</th>
        <th>单位</th>
        <th>数量</th>
        <th>单价</th>
        <th>金额</th>
        <th>备注</th>
      </tr>
    </thead>
    <tbody>
      {{#items}}
      <tr>
        <td>{{index}}</td>
        <td>{{materialCode}}</td>
        <td>{{materialName}}</td>
        <td>{{specification}}</td>
        <td>{{unit}}</td>
        <td>{{quantity}}</td>
        <td>{{price}}</td>
        <td>{{amount}}</td>
        <td>{{remark}}</td>
      </tr>
      {{/items}}
    </tbody>
  </table>
  <div class=\"summary-row\">
    <div class=\"summary-item\"><span class=\"label\">合计金额：</span><span class=\"value\">{{totalAmount}}</span></div>
  </div>
  <div class=\"remark-section\">
    <div class=\"label\">备注：</div>
    <div class=\"content\">{{remark}}</div>
  </div>
  <div class=\"sign-section\">
    <div class=\"sign-item\">
      <div class=\"sign-label\">制单人：</div>
      <div class=\"sign-value\">{{createBy}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">审核人：</div>
      <div class=\"sign-value\">{{auditor}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">客户签字：</div>
      <div class=\"sign-value\"></div>
    </div>
  </div>
</div>',
    '.document { font-family: "Microsoft YaHei", sans-serif; font-size: 14px; color: #333; }
.title { text-align: center; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
.info-row { display: flex; margin-bottom: 10px; }
.info-item { flex: 1; }
.label { font-weight: bold; }
.item-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
.item-table th, .item-table td { border: 1px solid #333; padding: 8px; text-align: center; }
.item-table th { background-color: #f5f5f5; font-weight: bold; }
.summary-row { text-align: right; margin: 10px 0; font-size: 16px; font-weight: bold; }
.remark-section { margin: 20px 0; }
.remark-section .content { min-height: 60px; border: 1px solid #ccc; padding: 10px; margin-top: 5px; }
.sign-section { display: flex; margin-top: 40px; }
.sign-item { flex: 1; text-align: center; }
.sign-label { margin-bottom: 30px; }',
    1, 'A4', 'portrait',
    20, 20, 20, 20,
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">XX公司销售订单</div>',
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">第 {{pageNum}} 页 / 共 {{totalPages}} 页</div>',
    '', 0,
    1, 1, '销售订单默认打印模板'
);

-- 采购订单默认模板
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style,
    is_default, page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content,
    watermark, watermark_enabled,
    status, sort_order, remark
) VALUES (
    'PURCHASE_ORDER_DEFAULT', '采购订单默认模板', 'PURCHASE_ORDER', 'HTML',
    '<div class=\"document\">
  <div class=\"title\">采购订单</div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">订单编号：</span><span class=\"value\">{{orderNo}}</span></div>
    <div class=\"info-item\"><span class=\"label\">订单日期：</span><span class=\"value\">{{orderDate}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">供应商名称：</span><span class=\"value\">{{supplierName}}</span></div>
    <div class=\"info-item\"><span class=\"label\">联系人：</span><span class=\"value\">{{contactName}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">联系电话：</span><span class=\"value\">{{contactPhone}}</span></div>
    <div class=\"info-item\"><span class=\"label\">地址：</span><span class=\"value\">{{address}}</span></div>
  </div>
  <table class=\"item-table\">
    <thead>
      <tr>
        <th>序号</th>
        <th>商品编码</th>
        <th>商品名称</th>
        <th>规格型号</th>
        <th>单位</th>
        <th>数量</th>
        <th>单价</th>
        <th>金额</th>
        <th>备注</th>
      </tr>
    </thead>
    <tbody>
      {{#items}}
      <tr>
        <td>{{index}}</td>
        <td>{{materialCode}}</td>
        <td>{{materialName}}</td>
        <td>{{specification}}</td>
        <td>{{unit}}</td>
        <td>{{quantity}}</td>
        <td>{{price}}</td>
        <td>{{amount}}</td>
        <td>{{remark}}</td>
      </tr>
      {{/items}}
    </tbody>
  </table>
  <div class=\"summary-row\">
    <div class=\"summary-item\"><span class=\"label\">合计金额：</span><span class=\"value\">{{totalAmount}}</span></div>
  </div>
  <div class=\"remark-section\">
    <div class=\"label\">备注：</div>
    <div class=\"content\">{{remark}}</div>
  </div>
  <div class=\"sign-section\">
    <div class=\"sign-item\">
      <div class=\"sign-label\">制单人：</div>
      <div class=\"sign-value\">{{createBy}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">审核人：</div>
      <div class=\"sign-value\">{{auditor}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">供应商签字：</div>
      <div class=\"sign-value\"></div>
    </div>
  </div>
</div>',
    '.document { font-family: "Microsoft YaHei", sans-serif; font-size: 14px; color: #333; }
.title { text-align: center; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
.info-row { display: flex; margin-bottom: 10px; }
.info-item { flex: 1; }
.label { font-weight: bold; }
.item-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
.item-table th, .item-table td { border: 1px solid #333; padding: 8px; text-align: center; }
.item-table th { background-color: #f5f5f5; font-weight: bold; }
.summary-row { text-align: right; margin: 10px 0; font-size: 16px; font-weight: bold; }
.remark-section { margin: 20px 0; }
.remark-section .content { min-height: 60px; border: 1px solid #ccc; padding: 10px; margin-top: 5px; }
.sign-section { display: flex; margin-top: 40px; }
.sign-item { flex: 1; text-align: center; }
.sign-label { margin-bottom: 30px; }',
    1, 'A4', 'portrait',
    20, 20, 20, 20,
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">XX公司采购订单</div>',
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">第 {{pageNum}} 页 / 共 {{totalPages}} 页</div>',
    '', 0,
    1, 1, '采购订单默认打印模板'
);

-- 销售出库单默认模板
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style,
    is_default, page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content,
    watermark, watermark_enabled,
    status, sort_order, remark
) VALUES (
    'SALE_DELIVERY_DEFAULT', '销售出库单默认模板', 'SALE_DELIVERY', 'HTML',
    '<div class=\"document\">
  <div class=\"title\">销售出库单</div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">出库单号：</span><span class=\"value\">{{deliveryNo}}</span></div>
    <div class=\"info-item\"><span class=\"label\">出库日期：</span><span class=\"value\">{{deliveryDate}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">客户名称：</span><span class=\"value\">{{customerName}}</span></div>
    <div class=\"info-item\"><span class=\"label\">仓库：</span><span class=\"value\">{{warehouseName}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">销售订单号：</span><span class=\"value\">{{orderNo}}</span></div>
    <div class=\"info-item\"><span class=\"label\">联系人：</span><span class=\"value\">{{contactName}}</span></div>
  </div>
  <table class=\"item-table\">
    <thead>
      <tr>
        <th>序号</th>
        <th>商品编码</th>
        <th>商品名称</th>
        <th>规格型号</th>
        <th>单位</th>
        <th>出库数量</th>
        <th>单价</th>
        <th>金额</th>
        <th>备注</th>
      </tr>
    </thead>
    <tbody>
      {{#items}}
      <tr>
        <td>{{index}}</td>
        <td>{{materialCode}}</td>
        <td>{{materialName}}</td>
        <td>{{specification}}</td>
        <td>{{unit}}</td>
        <td>{{quantity}}</td>
        <td>{{price}}</td>
        <td>{{amount}}</td>
        <td>{{remark}}</td>
      </tr>
      {{/items}}
    </tbody>
  </table>
  <div class=\"summary-row\">
    <div class=\"summary-item\"><span class=\"label\">合计金额：</span><span class=\"value\">{{totalAmount}}</span></div>
  </div>
  <div class=\"remark-section\">
    <div class=\"label\">备注：</div>
    <div class=\"content\">{{remark}}</div>
  </div>
  <div class=\"sign-section\">
    <div class=\"sign-item\">
      <div class=\"sign-label\">制单人：</div>
      <div class=\"sign-value\">{{createBy}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">仓管员：</div>
      <div class=\"sign-value\">{{warehouseKeeper}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">客户签收：</div>
      <div class=\"sign-value\"></div>
    </div>
  </div>
</div>',
    '.document { font-family: "Microsoft YaHei", sans-serif; font-size: 14px; color: #333; }
.title { text-align: center; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
.info-row { display: flex; margin-bottom: 10px; }
.info-item { flex: 1; }
.label { font-weight: bold; }
.item-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
.item-table th, .item-table td { border: 1px solid #333; padding: 8px; text-align: center; }
.item-table th { background-color: #f5f5f5; font-weight: bold; }
.summary-row { text-align: right; margin: 10px 0; font-size: 16px; font-weight: bold; }
.remark-section { margin: 20px 0; }
.remark-section .content { min-height: 60px; border: 1px solid #ccc; padding: 10px; margin-top: 5px; }
.sign-section { display: flex; margin-top: 40px; }
.sign-item { flex: 1; text-align: center; }
.sign-label { margin-bottom: 30px; }',
    1, 'A4', 'portrait',
    20, 20, 20, 20,
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">XX公司销售出库单</div>',
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">第 {{pageNum}} 页 / 共 {{totalPages}} 页</div>',
    '', 0,
    1, 1, '销售出库单默认打印模板'
);

-- 采购入库单默认模板
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style,
    is_default, page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content,
    watermark, watermark_enabled,
    status, sort_order, remark
) VALUES (
    'PURCHASE_INBOUND_DEFAULT', '采购入库单默认模板', 'PURCHASE_INBOUND', 'HTML',
    '<div class=\"document\">
  <div class=\"title\">采购入库单</div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">入库单号：</span><span class=\"value\">{{inboundNo}}</span></div>
    <div class=\"info-item\"><span class=\"label\">入库日期：</span><span class=\"value\">{{inboundDate}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">供应商名称：</span><span class=\"value\">{{supplierName}}</span></div>
    <div class=\"info-item\"><span class=\"label\">仓库：</span><span class=\"value\">{{warehouseName}}</span></div>
  </div>
  <div class=\"info-row\">
    <div class=\"info-item\"><span class=\"label\">采购订单号：</span><span class=\"value\">{{orderNo}}</span></div>
    <div class=\"info-item\"><span class=\"label\">联系人：</span><span class=\"value\">{{contactName}}</span></div>
  </div>
  <table class=\"item-table\">
    <thead>
      <tr>
        <th>序号</th>
        <th>商品编码</th>
        <th>商品名称</th>
        <th>规格型号</th>
        <th>单位</th>
        <th>入库数量</th>
        <th>单价</th>
        <th>金额</th>
        <th>备注</th>
      </tr>
    </thead>
    <tbody>
      {{#items}}
      <tr>
        <td>{{index}}</td>
        <td>{{materialCode}}</td>
        <td>{{materialName}}</td>
        <td>{{specification}}</td>
        <td>{{unit}}</td>
        <td>{{quantity}}</td>
        <td>{{price}}</td>
        <td>{{amount}}</td>
        <td>{{remark}}</td>
      </tr>
      {{/items}}
    </tbody>
  </table>
  <div class=\"summary-row\">
    <div class=\"summary-item\"><span class=\"label\">合计金额：</span><span class=\"value\">{{totalAmount}}</span></div>
  </div>
  <div class=\"remark-section\">
    <div class=\"label\">备注：</div>
    <div class=\"content\">{{remark}}</div>
  </div>
  <div class=\"sign-section\">
    <div class=\"sign-item\">
      <div class=\"sign-label\">制单人：</div>
      <div class=\"sign-value\">{{createBy}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">仓管员：</div>
      <div class=\"sign-value\">{{warehouseKeeper}}</div>
    </div>
    <div class=\"sign-item\">
      <div class=\"sign-label\">供应商签字：</div>
      <div class=\"sign-value\"></div>
    </div>
  </div>
</div>',
    '.document { font-family: "Microsoft YaHei", sans-serif; font-size: 14px; color: #333; }
.title { text-align: center; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
.info-row { display: flex; margin-bottom: 10px; }
.info-item { flex: 1; }
.label { font-weight: bold; }
.item-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
.item-table th, .item-table td { border: 1px solid #333; padding: 8px; text-align: center; }
.item-table th { background-color: #f5f5f5; font-weight: bold; }
.summary-row { text-align: right; margin: 10px 0; font-size: 16px; font-weight: bold; }
.remark-section { margin: 20px 0; }
.remark-section .content { min-height: 60px; border: 1px solid #ccc; padding: 10px; margin-top: 5px; }
.sign-section { display: flex; margin-top: 40px; }
.sign-item { flex: 1; text-align: center; }
.sign-label { margin-bottom: 30px; }',
    1, 'A4', 'portrait',
    20, 20, 20, 20,
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">XX公司采购入库单</div>',
    '<div style=\"text-align: center; font-size: 12px; color: #666;\">第 {{pageNum}} 页 / 共 {{totalPages}} 页</div>',
    '', 0,
    1, 1, '采购入库单默认打印模板'
);

-- ========================================
-- 初始模板字段配置数据 - 销售订单
-- ========================================

-- 销售订单模板字段
INSERT INTO print_template_field (template_id, field_key, field_name, field_type, display_format, sort_order, visible) VALUES
(1, 'orderNo', '订单编号', 'text', NULL, 1, 1),
(1, 'orderDate', '订单日期', 'date', 'yyyy-MM-dd', 2, 1),
(1, 'customerName', '客户名称', 'text', NULL, 3, 1),
(1, 'contactName', '联系人', 'text', NULL, 4, 1),
(1, 'contactPhone', '联系电话', 'text', NULL, 5, 1),
(1, 'address', '地址', 'text', NULL, 6, 1),
(1, 'items', '商品明细', 'table', NULL, 7, 1),
(1, 'items.materialCode', '商品编码', 'text', NULL, 8, 1),
(1, 'items.materialName', '商品名称', 'text', NULL, 9, 1),
(1, 'items.specification', '规格型号', 'text', NULL, 10, 1),
(1, 'items.unit', '单位', 'text', NULL, 11, 1),
(1, 'items.quantity', '数量', 'number', '#,##0.##', 12, 1),
(1, 'items.price', '单价', 'number', '#,##0.00', 13, 1),
(1, 'items.amount', '金额', 'number', '#,##0.00', 14, 1),
(1, 'totalAmount', '合计金额', 'number', '#,##0.00', 15, 1),
(1, 'remark', '备注', 'text', NULL, 16, 1),
(1, 'createBy', '制单人', 'text', NULL, 17, 1),
(1, 'auditor', '审核人', 'text', NULL, 18, 1);

-- 采购订单模板字段
INSERT INTO print_template_field (template_id, field_key, field_name, field_type, display_format, sort_order, visible) VALUES
(2, 'orderNo', '订单编号', 'text', NULL, 1, 1),
(2, 'orderDate', '订单日期', 'date', 'yyyy-MM-dd', 2, 1),
(2, 'supplierName', '供应商名称', 'text', NULL, 3, 1),
(2, 'contactName', '联系人', 'text', NULL, 4, 1),
(2, 'contactPhone', '联系电话', 'text', NULL, 5, 1),
(2, 'address', '地址', 'text', NULL, 6, 1),
(2, 'items', '商品明细', 'table', NULL, 7, 1),
(2, 'items.materialCode', '商品编码', 'text', NULL, 8, 1),
(2, 'items.materialName', '商品名称', 'text', NULL, 9, 1),
(2, 'items.specification', '规格型号', 'text', NULL, 10, 1),
(2, 'items.unit', '单位', 'text', NULL, 11, 1),
(2, 'items.quantity', '数量', 'number', '#,##0.##', 12, 1),
(2, 'items.price', '单价', 'number', '#,##0.00', 13, 1),
(2, 'items.amount', '金额', 'number', '#,##0.00', 14, 1),
(2, 'totalAmount', '合计金额', 'number', '#,##0.00', 15, 1),
(2, 'remark', '备注', 'text', NULL, 16, 1),
(2, 'createBy', '制单人', 'text', NULL, 17, 1),
(2, 'auditor', '审核人', 'text', NULL, 18, 1);

-- 销售出库单模板字段
INSERT INTO print_template_field (template_id, field_key, field_name, field_type, display_format, sort_order, visible) VALUES
(3, 'deliveryNo', '出库单号', 'text', NULL, 1, 1),
(3, 'deliveryDate', '出库日期', 'date', 'yyyy-MM-dd', 2, 1),
(3, 'customerName', '客户名称', 'text', NULL, 3, 1),
(3, 'warehouseName', '仓库', 'text', NULL, 4, 1),
(3, 'orderNo', '销售订单号', 'text', NULL, 5, 1),
(3, 'contactName', '联系人', 'text', NULL, 6, 1),
(3, 'items', '商品明细', 'table', NULL, 7, 1),
(3, 'items.materialCode', '商品编码', 'text', NULL, 8, 1),
(3, 'items.materialName', '商品名称', 'text', NULL, 9, 1),
(3, 'items.specification', '规格型号', 'text', NULL, 10, 1),
(3, 'items.unit', '单位', 'text', NULL, 11, 1),
(3, 'items.quantity', '出库数量', 'number', '#,##0.##', 12, 1),
(3, 'items.price', '单价', 'number', '#,##0.00', 13, 1),
(3, 'items.amount', '金额', 'number', '#,##0.00', 14, 1),
(3, 'totalAmount', '合计金额', 'number', '#,##0.00', 15, 1),
(3, 'remark', '备注', 'text', NULL, 16, 1),
(3, 'createBy', '制单人', 'text', NULL, 17, 1),
(3, 'warehouseKeeper', '仓管员', 'text', NULL, 18, 1);

-- 采购入库单模板字段
INSERT INTO print_template_field (template_id, field_key, field_name, field_type, display_format, sort_order, visible) VALUES
(4, 'inboundNo', '入库单号', 'text', NULL, 1, 1),
(4, 'inboundDate', '入库日期', 'date', 'yyyy-MM-dd', 2, 1),
(4, 'supplierName', '供应商名称', 'text', NULL, 3, 1),
(4, 'warehouseName', '仓库', 'text', NULL, 4, 1),
(4, 'orderNo', '采购订单号', 'text', NULL, 5, 1),
(4, 'contactName', '联系人', 'text', NULL, 6, 1),
(4, 'items', '商品明细', 'table', NULL, 7, 1),
(4, 'items.materialCode', '商品编码', 'text', NULL, 8, 1),
(4, 'items.materialName', '商品名称', 'text', NULL, 9, 1),
(4, 'items.specification', '规格型号', 'text', NULL, 10, 1),
(4, 'items.unit', '单位', 'text', NULL, 11, 1),
(4, 'items.quantity', '入库数量', 'number', '#,##0.##', 12, 1),
(4, 'items.price', '单价', 'number', '#,##0.00', 13, 1),
(4, 'items.amount', '金额', 'number', '#,##0.00', 14, 1),
(4, 'totalAmount', '合计金额', 'number', '#,##0.00', 15, 1),
(4, 'remark', '备注', 'text', NULL, 16, 1),
(4, 'createBy', '制单人', 'text', NULL, 17, 1),
(4, 'warehouseKeeper', '仓管员', 'text', NULL, 18, 1);
