-- =====================================================
-- 54_销售合同 RHTAGS 风格默认模板 + print_template 结构自愈/标准种子补齐
-- 单据类型: SALE_CONTRACT
-- 样式参考: 用户提供的报价单图片(公司名+大标题+双边信息表+深绿表头明细+付款质保+签名)
-- 修复背景(2026-09-02 8cloud部署实测): 8cloud的print_template由SchemaGuard按实体
--   默认建表 -> content/style/header/footer为VARCHAR(255)且template_code无唯一索引;
--   且20号标准种子SQL从未在服务器执行(8cloud空表, 打印功能整体不可用)。
-- 幂等设计(可重复执行):
--   Step 0  内容列对齐TEXT(已是TEXT时no-op)
--   Step 0b 收敛template_code重复(保留id最大)后建唯一索引(已存在no-op)
--   Step 1  标准模板种子(20号同源, ON CONFLICT DO NOTHING不覆盖已有/用户改过的)
--   Step 1b GENERIC兜底模板is_default修复(getDefaultTemplate要求is_default=1)
--   Step 2  RHTAGS插入 ON CONFLICT DO NOTHING
--   Step 2b 已存在时 UPDATE 覆盖内容
--   Step 3  默认标记带 WHERE NOT EXISTS 防抢用户已有默认模板
-- =====================================================

-- Step 0: 内容列对齐TEXT (幂等, 已是TEXT时no-op)
ALTER TABLE print_template ALTER COLUMN template_content TYPE TEXT;
ALTER TABLE print_template ALTER COLUMN template_style TYPE TEXT;
ALTER TABLE print_template ALTER COLUMN header_content TYPE TEXT;
ALTER TABLE print_template ALTER COLUMN footer_content TYPE TEXT;

-- Step 0b: 收敛template_code重复(保留id最大), 再建唯一索引 (幂等)
DELETE FROM print_template a USING print_template b
 WHERE a.template_code = b.template_code AND a.id < b.id;
CREATE UNIQUE INDEX IF NOT EXISTS uk_print_template_code ON print_template(template_code);

-- Step 1: 标准模板种子 (与20_print_template.sql同源; 不覆盖已存在/用户改过的模板)
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style, is_default,
    page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content, watermark, watermark_enabled,
    status, sort_order, remark, create_by, create_time, update_time
) VALUES
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
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 1, '销售订单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

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
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 2, '销售报价单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

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
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 3, '采购订单标准打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

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
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 99, '通用打印模板(未配置专用模板时使用)', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (template_code) DO NOTHING;

-- Step 1b: GENERIC兜底模板默认标记修复 (getDefaultTemplate要求GENERIC类型存在is_default=1; 幂等)
UPDATE print_template
SET is_default = 1, update_time = CURRENT_TIMESTAMP
WHERE template_code = 'generic_standard'
  AND is_default = 0
  AND NOT EXISTS (
      SELECT 1 FROM print_template
      WHERE doc_type = 'GENERIC' AND is_default = 1
        AND template_code <> 'generic_standard'
  );

-- Step 1: 插入模板记录 (is_default 暂置 0)
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style, is_default,
    page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content, watermark, watermark_enabled,
    status, sort_order, remark, create_by, create_time, update_time
) VALUES (
    'sale_contract_rhtags_style',
    '销售合同-RHTAGS风格默认模板',
    'SALE_CONTRACT',
    'html',
    '{{#header}}
<table style="width:100%; margin-bottom:10px;">
  <tr>
    <td style="width:120px; vertical-align: middle;">
      <div style="width:110px; height:48px; border:2px solid #0a5d4b; color:#0a5d4b; font-weight:bold; font-size:18px; text-align:center; line-height:44px; letter-spacing:2px;">楷煜KY</div>
    </td>
    <td style="text-align:center; vertical-align: middle;">
      <div style="font-size:26px; font-weight:bold; color:#0a5d4b; letter-spacing:4px;">楷煜科技有限公司</div>
    </td>
    <td style="width:120px;">&nbsp;</td>
  </tr>
</table>
<div style="text-align:center; font-size:28px; font-weight:bold; margin:18px 0 6px; letter-spacing:14px;">销售合同</div>
<div style="border-bottom:1px dashed #888; margin:0 0 14px;"></div>
<table style="width:100%; font-size:13px; margin-bottom:8px;">
  <tr>
    <td style="width:50%;">合同编号：<b>{{contractNo}}</b></td>
    <td style="width:50%; text-align:right;">签订日期：{{contractDate}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:13px; margin-bottom:20px;" border="1" cellpadding="6" cellspacing="0">
  <tr>
    <td style="width:15%; background:#f7f7f7;">客户单位：</td>
    <td style="width:35%;"><b>{{customerShortName}}</b></td>
    <td style="width:15%; background:#f7f7f7;">客户编码：</td>
    <td style="width:35%;">{{customerNo}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">联 系 人：</td>
    <td>{{contactPerson}}</td>
    <td style="background:#f7f7f7;">合同有效期：</td>
    <td>{{validityStart}} 至 {{validityEnd}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户电话：</td>
    <td>{{contactPhone}}</td>
    <td style="background:#f7f7f7;">业务经手人：</td>
    <td>{{salerName}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户传真：</td>
    <td></td>
    <td style="background:#f7f7f7;">联系电话：</td>
    <td>{{contactPhone}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户地址：</td>
    <td>{{receiveAddress}}</td>
    <td style="background:#f7f7f7;">币种/汇率：</td>
    <td>{{currencyCode}} / {{exchangeRate}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户类别：</td>
    <td></td>
    <td style="background:#f7f7f7;">发票类型：</td>
    <td>{{invoiceType}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">付款期限：</td>
    <td>{{paymentTermName}}{{paymentMethod}}</td>
    <td style="background:#f7f7f7;">运　　费：</td>
    <td>{{transportMethod}}</td>
  </tr>
</table>
{{/header}}

<div style="font-size:15px; font-weight:bold; margin:10px 0 8px;">1、合同明细一览表</div>
<table style="width:100%; border-collapse:collapse; font-size:13px; margin-bottom:20px;" border="1" cellpadding="6" cellspacing="0">
  <thead>
    <tr style="background:#0a5d4b; color:#ffffff;">
      <th style="width:50px; text-align:center;">序号</th>
      <th style="width:20%; text-align:center;">物料名称</th>
      <th style="width:30%; text-align:center;">品牌规格型号</th>
      <th style="width:12%; text-align:center;">订单数量</th>
      <th style="width:13%; text-align:center;">单价（元）</th>
      <th style="text-align:center;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="text-align:center;">{{sortNo}}</td>
      <td>{{materialName}}</td>
      <td style="white-space:pre-wrap;">{{specification}}{{materialCode}}</td>
      <td style="text-align:right;">{{quantity}} {{unit}}</td>
      <td style="text-align:right;">{{unitPrice}}</td>
      <td>{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>

{{#section}}
<div style="font-size:14px; font-weight:bold; margin:6px 0 6px;">2、付款方式：</div>
<div style="font-size:13px; line-height:1.7; padding-left:14px;">
  1、签订合同之日起，30%预付款，余款发货前支付。
</div>
<div style="font-size:14px; font-weight:bold; margin:12px 0 6px;">3、产品质保：</div>
<div style="font-size:13px; line-height:1.7; padding-left:14px;">
  销售方所供产品均质保 1 年，产品在使用过程中如果有品质问题，均免费保修，产生的物流费用由销售方承担。
</div>
{{/section}}

{{#footer}}
<table style="width:100%; margin-top:30px; font-size:13px; border:none;">
  <tr>
    <td style="width:55%; border:none;">&nbsp;</td>
    <td style="width:45%; line-height:1.9; border:none;">
      公司名称：楷煜科技有限公司<br/>
      联系人签名：{{salerName}}<br/>
      盖章：<br/>
      电话：{{contactPhone}}<br/>
      合同日期：{{contractDate}}
    </td>
  </tr>
</table>
{{/footer}}',
    'body { font-family: SimSun, "宋体", serif; color:#111; padding:0; margin:0; }
th { font-weight:bold; }
td, th { border-color:#333; }',
    0,
    'A4', 'portrait',
    15, 15, 15, 15,
    NULL, NULL, NULL, 0,
    1, 1,
    '按用户提供报价单图片样式复刻的销售合同专属默认模板：公司名+大标题、客户信息双列表、深绿(#0a5d4b)表头明细表、付款方式/质保条款、右下角签名公司信息。',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (template_code) DO NOTHING;

-- Step 1b: 若模板已存在则覆盖内容和样式 (幂等, 唯一键精准WHERE)
UPDATE print_template SET
    template_content = '{{#header}}
<table style="width:100%; margin-bottom:10px;">
  <tr>
    <td style="width:120px; vertical-align: middle;">
      <div style="width:110px; height:48px; border:2px solid #0a5d4b; color:#0a5d4b; font-weight:bold; font-size:18px; text-align:center; line-height:44px; letter-spacing:2px;">楷煜KY</div>
    </td>
    <td style="text-align:center; vertical-align: middle;">
      <div style="font-size:26px; font-weight:bold; color:#0a5d4b; letter-spacing:4px;">楷煜科技有限公司</div>
    </td>
    <td style="width:120px;">&nbsp;</td>
  </tr>
</table>
<div style="text-align:center; font-size:28px; font-weight:bold; margin:18px 0 6px; letter-spacing:14px;">销售合同</div>
<div style="border-bottom:1px dashed #888; margin:0 0 14px;"></div>
<table style="width:100%; font-size:13px; margin-bottom:8px;">
  <tr>
    <td style="width:50%;">合同编号：<b>{{contractNo}}</b></td>
    <td style="width:50%; text-align:right;">签订日期：{{contractDate}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:13px; margin-bottom:20px;" border="1" cellpadding="6" cellspacing="0">
  <tr>
    <td style="width:15%; background:#f7f7f7;">客户单位：</td>
    <td style="width:35%;"><b>{{customerShortName}}</b></td>
    <td style="width:15%; background:#f7f7f7;">客户编码：</td>
    <td style="width:35%;">{{customerNo}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">联 系 人：</td>
    <td>{{contactPerson}}</td>
    <td style="background:#f7f7f7;">合同有效期：</td>
    <td>{{validityStart}} 至 {{validityEnd}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户电话：</td>
    <td>{{contactPhone}}</td>
    <td style="background:#f7f7f7;">业务经手人：</td>
    <td>{{salerName}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户传真：</td>
    <td></td>
    <td style="background:#f7f7f7;">联系电话：</td>
    <td>{{contactPhone}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户地址：</td>
    <td>{{receiveAddress}}</td>
    <td style="background:#f7f7f7;">币种/汇率：</td>
    <td>{{currencyCode}} / {{exchangeRate}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户类别：</td>
    <td></td>
    <td style="background:#f7f7f7;">发票类型：</td>
    <td>{{invoiceType}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">付款期限：</td>
    <td>{{paymentTermName}}{{paymentMethod}}</td>
    <td style="background:#f7f7f7;">运　　费：</td>
    <td>{{transportMethod}}</td>
  </tr>
</table>
{{/header}}

<div style="font-size:15px; font-weight:bold; margin:10px 0 8px;">1、合同明细一览表</div>
<table style="width:100%; border-collapse:collapse; font-size:13px; margin-bottom:20px;" border="1" cellpadding="6" cellspacing="0">
  <thead>
    <tr style="background:#0a5d4b; color:#ffffff;">
      <th style="width:50px; text-align:center;">序号</th>
      <th style="width:20%; text-align:center;">物料名称</th>
      <th style="width:30%; text-align:center;">品牌规格型号</th>
      <th style="width:12%; text-align:center;">订单数量</th>
      <th style="width:13%; text-align:center;">单价（元）</th>
      <th style="text-align:center;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="text-align:center;">{{sortNo}}</td>
      <td>{{materialName}}</td>
      <td style="white-space:pre-wrap;">{{specification}}{{materialCode}}</td>
      <td style="text-align:right;">{{quantity}} {{unit}}</td>
      <td style="text-align:right;">{{unitPrice}}</td>
      <td>{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>

{{#section}}
<div style="font-size:14px; font-weight:bold; margin:6px 0 6px;">2、付款方式：</div>
<div style="font-size:13px; line-height:1.7; padding-left:14px;">
  1、签订合同之日起，30%预付款，余款发货前支付。
</div>
<div style="font-size:14px; font-weight:bold; margin:12px 0 6px;">3、产品质保：</div>
<div style="font-size:13px; line-height:1.7; padding-left:14px;">
  销售方所供产品均质保 1 年，产品在使用过程中如果有品质问题，均免费保修，产生的物流费用由销售方承担。
</div>
{{/section}}

{{#footer}}
<table style="width:100%; margin-top:30px; font-size:13px; border:none;">
  <tr>
    <td style="width:55%; border:none;">&nbsp;</td>
    <td style="width:45%; line-height:1.9; border:none;">
      公司名称：楷煜科技有限公司<br/>
      联系人签名：{{salerName}}<br/>
      盖章：<br/>
      电话：{{contactPhone}}<br/>
      合同日期：{{contractDate}}
    </td>
  </tr>
</table>
{{/footer}}',
    template_style   = 'body { font-family: SimSun, "宋体", serif; color:#111; padding:0; margin:0; }
th { font-weight:bold; }
td, th { border-color:#333; }',
    remark           = '按用户提供报价单图片样式复刻的销售合同专属默认模板：公司名+大标题、客户信息双列表、深绿(#0a5d4b)表头明细表、付款方式/质保条款、右下角签名公司信息。v2 移除specification嵌套Mustache条件，避免渲染残留。',
    update_time      = CURRENT_TIMESTAMP
WHERE template_code = 'sale_contract_rhtags_style';

-- Step 3: 若 SALE_CONTRACT 类型下还没有默认模板，则把本模板升为默认
UPDATE print_template
SET is_default = 1,
    update_time = CURRENT_TIMESTAMP
WHERE template_code = 'sale_contract_rhtags_style'
  AND is_default = 0
  AND NOT EXISTS (
      SELECT 1 FROM print_template
      WHERE doc_type = 'SALE_CONTRACT'
        AND is_default = 1
        AND template_code <> 'sale_contract_rhtags_style'
  );