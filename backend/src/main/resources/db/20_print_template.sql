-- =====================================================
-- 打印模板模块 Schema
-- 数据库: PostgreSQL 15+
-- 包含: print_template, print_template_field, print_log
-- =====================================================

-- 1. 打印模板主表
CREATE TABLE IF NOT EXISTS print_template (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL UNIQUE,
    template_name VARCHAR(100) NOT NULL,
    doc_type VARCHAR(50) NOT NULL,
    template_type VARCHAR(20) DEFAULT 'html',
    template_content TEXT,
    template_style TEXT,
    is_default SMALLINT NOT NULL DEFAULT 0,
    page_size VARCHAR(20) DEFAULT 'A4',
    page_orientation VARCHAR(10) DEFAULT 'portrait',
    margin_top INT DEFAULT 15,
    margin_bottom INT DEFAULT 15,
    margin_left INT DEFAULT 15,
    margin_right INT DEFAULT 15,
    header_content TEXT,
    footer_content TEXT,
    watermark VARCHAR(200),
    watermark_enabled SMALLINT DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    sort_order INT DEFAULT 0,
    remark VARCHAR(500),
    create_by VARCHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE print_template IS '打印模板表';
COMMENT ON COLUMN print_template.doc_type IS '单据类型: SALE_ORDER/SALE_QUOTE/PURCHASE_ORDER等';

-- 2. 打印模板字段表 (定义模板中可用的字段映射)
CREATE TABLE IF NOT EXISTS print_template_field (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    field_key VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    field_type VARCHAR(20) DEFAULT 'text',
    display_format VARCHAR(100),
    position_x INT,
    position_y INT,
    width INT,
    height INT,
    font_size INT,
    font_weight VARCHAR(20),
    font_color VARCHAR(20),
    alignment VARCHAR(20) DEFAULT 'left',
    visible SMALLINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    remark VARCHAR(500),
    CONSTRAINT fk_ptf_template FOREIGN KEY (template_id) REFERENCES print_template(id) ON DELETE CASCADE
);
COMMENT ON TABLE print_template_field IS '打印模板字段配置表';

-- 3. 打印记录表 (记录每次打印的历史)
CREATE TABLE IF NOT EXISTS print_log (
    id BIGSERIAL PRIMARY KEY,
    bill_type VARCHAR(50) NOT NULL,
    bill_id BIGINT NOT NULL,
    bill_no VARCHAR(50),
    template_id BIGINT,
    template_name VARCHAR(100),
    print_count INT DEFAULT 1,
    printer_name VARCHAR(200),
    paper_size VARCHAR(20),
    operator_id BIGINT,
    operator_name VARCHAR(100),
    print_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    print_ip VARCHAR(50)
);
COMMENT ON TABLE print_log IS '打印记录表';
CREATE INDEX IF NOT EXISTS idx_print_log_bill ON print_log(bill_type, bill_id);

-- 4. 预置打印模板数据 (标准模板)
INSERT INTO print_template (template_code, template_name, doc_type, template_type, template_content, template_style, is_default, page_size, page_orientation, margin_top, margin_bottom, margin_left, margin_right, header_content, footer_content, watermark, watermark_enabled, status, sort_order, remark) VALUES
-- 销售订单标准模板
('sale_order_standard', '销售订单-标准模板', 'SALE_ORDER', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">销售订单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{orderNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>销售员:</b> {{salerName}}<br/>
      <b>订单日期:</b> {{orderDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>币种:</b> {{currencyName}}<br/>
      <b>交货日期:</b> {{deliveryDate}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 60px;">单位</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">单价</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">金额</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{unitName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="6" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">合计金额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createTime}}</td>
  </tr>
  <tr style="margin-top: 30px;">
    <td>客户签字: ___________</td>
    <td>销售签字: ___________</td>
    <td>盖章:</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 1, '销售订单标准打印模板'),

-- 销售报价单标准模板
('sale_quote_standard', '销售报价单-标准模板', 'SALE_QUOTE', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">销售报价单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{quoteNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>报价日期:</b> {{quoteDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>有效期:</b> {{validDate}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">单价</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">金额</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td><b>制单人:</b> {{createdBy}}</td>
    <td><b>制单日期:</b> {{createTime}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 2, '销售报价单标准打印模板'),

-- 采购订单标准模板
('purchase_order_standard', '采购订单-标准模板', 'PURCHASE_ORDER', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">采购订单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{orderNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>采购员:</b> {{purchaserName}}<br/>
      <b>订单日期:</b> {{orderDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>币种:</b> {{currencyName}}<br/>
      <b>交货日期:</b> {{deliveryDate}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 60px;">单位</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">单价</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">金额</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{unitName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="6" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">合计金额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createTime}}</td>
  </tr>
  <tr style="margin-top: 30px;">
    <td>供应商签字: ___________</td>
    <td>采购签字: ___________</td>
    <td>盖章:</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 3, '采购订单标准打印模板'),

-- 通用单据模板 (可用于所有其他单据)
('generic_standard', '通用单据-标准模板', 'GENERIC', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">{{billTypeName}}</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{billNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px;">
  <tr>
    <td><b>状态:</b> {{status}}</td>
    <td style="text-align: right;"><b>日期:</b> {{billDate}}</td>
  </tr>
</table>
<p style="font-size: 12px; color: #999; margin-top: 30px;">此为通用打印模板, 请在设置中心配置专用模板以获得更好的打印效果。</p>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; }',
0, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 99, '通用打印模板(未配置专用模板时使用)');
