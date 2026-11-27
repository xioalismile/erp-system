-- =====================================================
-- 105_高价值单据批量默认打印模板 (batch1)
-- 补齐 12 张常用单据的专用默认打印模板，替代 GENERIC 通用模板的兜底输出。
-- 单据类型: SALE_DELIVERY / SALE_RETURN / SALE_INVOICE /
--           PURCHASE_INBOUND / PURCHASE_RETURN / PURCHASE_CONTRACT / PURCHASE_REQUEST /
--           WORK_ORDER / PRODUCTION_RECEIPT / SUBCONTRACT_ORDER /
--           MATERIAL_REQUISITION / STOCK_TRANSFER
-- 取数支持: 12 张单据在 PrintDataFetcherService.fetchByType 均已存在分支，
--           无需改后端；模板占位符与各单据实体(getDetail 返回)的 Java 字段名严格一致。
-- 幂等设计: 唯一键为 template_code（print_template.template_code UNIQUE，
--           20 号建表 + 54 号 uk_print_template_code 索引），
--           INSERT ... ON CONFLICT (template_code) DO NOTHING，重复执行零副作用。
-- =====================================================

INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style, is_default,
    page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content, watermark, watermark_enabled,
    status, sort_order, remark, create_by, create_time, update_time
) VALUES

-- ===== 1. 销售出库单 =====
('sale_delivery_standard', '销售出库单-标准模板', 'SALE_DELIVERY', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">销售出库单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{deliveryNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>销售员:</b> {{salerName}}<br/>
      <b>出库日期:</b> {{deliveryDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>仓库:</b> {{warehouseName}}<br/>
      <b>币种:</b> {{currencyCode}}<br/>
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
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
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
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{unit}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="7" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">合计金额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
  <tr style="margin-top: 30px;">
    <td>收货人签字: ___________</td>
    <td>发货人签字: ___________</td>
    <td>盖章:</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 1, '销售出库单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 2. 销售退货单 =====
('sale_return_standard', '销售退货单-标准模板', 'SALE_RETURN', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">销售退货单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{returnNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>销售员:</b> {{salerName}}<br/>
      <b>退货日期:</b> {{returnDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>仓库:</b> {{warehouseName}}<br/>
      <b>币种:</b> {{currencyCode}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
  <tr>
    <td colspan="2"><b>退货原因:</b> {{returnReason}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
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
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{unit}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="7" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">合计金额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 2, '销售退货单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 3. 销售发票 =====
('sale_invoice_standard', '销售发票-标准模板', 'SALE_INVOICE', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">销售发票</td>
    <td style="text-align: right; font-size: 12px; color: #666;">发票号: {{invoiceNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>销售员:</b> {{salerName}}<br/>
      <b>开票日期:</b> {{invoiceDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>发票类型:</b> {{invoiceType}}<br/>
      <b>币种:</b> {{currencyCode}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
  <tr>
    <td colspan="2"><b>税号:</b> {{taxNo}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
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
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{unit}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="5" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">合计金额 / 税额 / 净额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right;">{{totalAmount}}</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right;">{{taxAmount}}</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right;">{{netAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 3, '销售发票标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 4. 采购入库单 =====
('purchase_inbound_standard', '采购入库单-标准模板', 'PURCHASE_INBOUND', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">采购入库单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{receiptNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>采购员:</b> {{buyerName}}<br/>
      <b>入库日期:</b> {{inDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>部门:</b> {{deptName}}<br/>
      <b>仓库:</b> {{stockName}}<br/>
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
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
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
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{model}}</td>
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
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 4, '采购入库单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 5. 采购退货单 =====
('purchase_return_standard', '采购退货单-标准模板', 'PURCHASE_RETURN', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">采购退货单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{returnNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>采购员:</b> {{buyerName}}<br/>
      <b>退货日期:</b> {{returnDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>部门:</b> {{deptName}}<br/>
      <b>仓库:</b> {{stockName}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
  <tr>
    <td colspan="2"><b>退货原因:</b> {{returnReason}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
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
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{model}}</td>
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
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 5, '采购退货单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 6. 采购合同 =====
('purchase_contract_standard', '采购合同-标准模板', 'PURCHASE_CONTRACT', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">采购合同</td>
    <td style="text-align: right; font-size: 12px; color: #666;">合同编号: {{contractNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>供应商:</b> {{supplierShortName}}<br/>
      <b>供应商编码:</b> {{supplierNo}}<br/>
      <b>签订日期:</b> {{contractDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>联系人:</b> {{contactPerson}}<br/>
      <b>联系电话:</b> {{contactPhone}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
  <tr>
    <td style="width: 50%;"><b>交货日期:</b> {{deliveryDate}}</td>
    <td style="width: 50%; text-align: right;"><b>付款方式:</b> {{paymentMethod}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
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
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
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
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
  <tr style="margin-top: 30px;">
    <td>供应商签字: ___________</td>
    <td>采购签字: ___________</td>
    <td>盖章:</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 6, '采购合同标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 7. 采购申请单 =====
('purchase_request_standard', '采购申请单-标准模板', 'PURCHASE_REQUEST', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">采购申请单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{requestNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>申请部门:</b> {{applyDeptName}}<br/>
      <b>申请人:</b> {{applierName}}<br/>
      <b>申请日期:</b> {{requestDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>申请类型:</b> {{applyType}}<br/>
      <b>需求日期:</b> {{demandDate}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
  <tr>
    <td colspan="2"><b>来源类型:</b> {{sourceType}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">申请数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">审批数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">需求日期</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{model}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{applyQty}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{approvedQty}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{demandDate}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="6" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">申请总数量:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalQty}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审批人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 7, '采购申请单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 8. 生产工单 =====
('work_order_standard', '生产工单-标准模板', 'WORK_ORDER', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">生产工单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">工单编号: {{orderNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>产品编码:</b> {{materialCode}}<br/>
      <b>产品名称:</b> {{materialName}}<br/>
      <b>工单日期:</b> {{orderDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>规格型号:</b> {{model}}<br/>
      <b>生产数量:</b> {{quantity}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
  <tr>
    <td style="width: 50%;"><b>计划开始:</b> {{planStartDate}}</td>
    <td style="width: 50%; text-align: right;"><b>计划结束:</b> {{planEndDate}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px; margin-bottom: 20px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 90px; text-align: right;">应领数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 90px; text-align: right;">已领数量</th>
    </tr>
  </thead>
  <tbody>
    {{#materials}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{requiredQuantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{issuedQuantity}}</td>
    </tr>
    {{/materials}}
  </tbody>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px; margin-bottom: 20px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">成品编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">成品名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 90px; text-align: right;">计划数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 90px; text-align: right;">完成数量</th>
    </tr>
  </thead>
  <tbody>
    {{#products}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{completedQuantity}}</td>
    </tr>
    {{/products}}
  </tbody>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 8, '生产工单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 9. 生产入库单 =====
('production_receipt_standard', '生产入库单-标准模板', 'PRODUCTION_RECEIPT', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">生产入库单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{receiptNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>源工单号:</b> {{workOrderNo}}<br/>
      <b>生产工单号:</b> {{productionOrderNo}}<br/>
      <b>入库日期:</b> {{receiptDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>总数量:</b> {{totalQuantity}}<br/>
      <b>合格数量:</b> {{qualifiedQuantity}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">产品编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">产品名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">合格数</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">报废数</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">金额</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{qualifiedQuantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{rejectedQuantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="7" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">合计金额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 9, '生产入库单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 10. 委外加工单 =====
('subcontract_order_standard', '委外加工单-标准模板', 'SUBCONTRACT_ORDER', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">委外加工单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{orderNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>委外工序号:</b> {{processItem}}<br/>
      <b>委外日期:</b> {{orderDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>计划开工:</b> {{expectDate}}<br/>
      <b>计划完工:</b> {{actualDate}}<br/>
      <b>状态:</b> {{approvalStatus}}
    </td>
  </tr>
  <tr>
    <td style="width: 50%;"><b>委外数量:</b> {{totalQuantity}}</td>
    <td style="width: 50%; text-align: right;"><b>加工单价:</b> {{unitPrice}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 60px;">单位</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">单价</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">金额</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{unit}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="6" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">加工费总额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 50%;"><b>制单人:</b> {{createBy}}</td>
    <td style="width: 50%; text-align: right;"><b>制单日期:</b> {{createTime}}</td>
  </tr>
  <tr style="margin-top: 30px;">
    <td>供应商签字: ___________</td>
    <td style="text-align: right;">盖章:</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 10, '委外加工单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 11. 生产领料单 =====
('material_requisition_standard', '生产领料单-标准模板', 'MATERIAL_REQUISITION', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">生产领料单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{requisitionNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>单据类型:</b> {{billType}}<br/>
      <b>工单号:</b> {{workOrderNo}}<br/>
      <b>领料日期:</b> {{requisitionDate}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>状态:</b> {{status}}<br/>
      <b>审核人:</b> {{approvedBy}}
    </td>
  </tr>
  <tr>
    <td colspan="2"><b>备注:</b> {{remark}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">领料数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 80px; text-align: right;">已发数量</th>
      <th style="border: 1px solid #ccc; padding: 6px; width: 100px; text-align: right;">金额</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: center;">{{lineNo}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialCode}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{materialName}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{issuedQuantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="6" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">领料总额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
  <tr style="margin-top: 30px;">
    <td>领料人签字: ___________</td>
    <td>发料人签字: ___________</td>
    <td>盖章:</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 11, '生产领料单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 12. 库存调拨单 =====
('stock_transfer_standard', '库存调拨单-标准模板', 'STOCK_TRANSFER', 'html',
'{{#header}}
<table style="width:100%; border-bottom: 2px solid #333; margin-bottom: 20px;">
  <tr>
    <td style="font-size: 24px; font-weight: bold;">库存调拨单</td>
    <td style="text-align: right; font-size: 12px; color: #666;">单据编号: {{transferNo}}</td>
  </tr>
</table>
{{/header}}
<table style="width:100%; font-size: 12px; margin-bottom: 15px;">
  <tr>
    <td style="width: 50%;">
      <b>调拨类型:</b> {{transferType}}<br/>
      <b>调拨日期:</b> {{transferDate}}<br/>
      <b>状态:</b> {{status}}
    </td>
    <td style="width: 50%; text-align: right;">
      <b>调拨总额:</b> {{totalAmount}}<br/>
      <b>审核人:</b> {{approvedBy}}
    </td>
  </tr>
  <tr>
    <td colspan="2"><b>备注:</b> {{remark}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse: collapse; font-size: 12px;">
  <thead>
    <tr style="background: #f0f0f0;">
      <th style="border: 1px solid #ccc; padding: 6px; width: 40px;">行号</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料编码</th>
      <th style="border: 1px solid #ccc; padding: 6px;">物料名称</th>
      <th style="border: 1px solid #ccc; padding: 6px;">规格型号</th>
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
      <td style="border: 1px solid #ccc; padding: 4px 6px;">{{specification}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{quantity}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{unitPrice}}</td>
      <td style="border: 1px solid #ccc; padding: 4px 6px; text-align: right;">{{amount}}</td>
    </tr>
    {{/items}}
  </tbody>
  <tfoot>
    <tr>
      <td colspan="6" style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">调拨总额:</td>
      <td style="border: 1px solid #ccc; padding: 6px; text-align: right; font-weight: bold;">{{totalAmount}}</td>
    </tr>
  </tfoot>
</table>
<table style="width:100%; font-size: 12px; margin-top: 20px;">
  <tr>
    <td style="width: 33%;"><b>制单人:</b> {{createdBy}}</td>
    <td style="width: 33%;"><b>审核人:</b> {{approvedBy}}</td>
    <td style="width: 33%;"><b>制单日期:</b> {{createdAt}}</td>
  </tr>
</table>',
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 12, '库存调拨单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (template_code) DO NOTHING;
