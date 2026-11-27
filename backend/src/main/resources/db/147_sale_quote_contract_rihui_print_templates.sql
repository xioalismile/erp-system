-- =====================================================
-- 147_销售报价单 / 销售合同「日晖风格」默认打印模板
-- 单据类型: SALE_QUOTE（报价单） / SALE_CONTRACT（销售合同）
-- 样式来源: 用户提供的两份原件
--   ① 报价单图片（广州日晖智能科技有限公司 报价单：公司名+「报 价 单」大标题+双边信息表
--      +深绿表头明细表 序号/物料名称/型号规格/数量/单价/总价/备注/图示+右下角公司落款）
--   ② 销售合同 PDF（广州日晖智能科技有限公司销售合同 RH261009：合同号+甲乙双方信息表
--      +名称/型号/单价/单位/数量/合计/特殊要求 明细表+总计金额+8 条条款+双方签字盖章）
-- 设计约束（守则：字段必须真实可取数）:
--   header 占位符只用 SaleQuote / SaleContract 的实体字段 + getDetail 补齐的展示字段，
--   items 占位符只用 SaleQuoteItem / SaleContractItem 的真实字段名（合同明细序号列是 sortNo）。
--   报价单表头无「客户传真/客户类别/图示」等字段 → 按原件留空；合同条款为固定条文，按原件原样保留。
-- 幂等设计: 唯一键 template_code，INSERT ... ON CONFLICT (template_code) DO NOTHING；
--           设为默认用"先清同类型其它默认、再置本模板默认"的定向 UPDATE（可重复执行）。
-- =====================================================

-- ===== 1. 插入两条模板（is_default 暂置 0，由 Step 2 统一定默认） =====
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style, is_default,
    page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content, watermark, watermark_enabled,
    status, sort_order, remark, create_by, create_time, update_time
) VALUES

-- ===== 1.1 销售报价单（日晖风格） =====
('sale_quote_rihui_style', '销售报价单-日晖风格默认模板', 'SALE_QUOTE', 'html',
'{{#header}}
<table style="width:100%; margin-bottom:4px;">
  <tr>
    <td style="width:170px; vertical-align:middle; color:#0a5d4b; font-weight:bold; font-size:20px; letter-spacing:1px;">&laquo;RHTAGS&raquo;&reg;</td>
    <td style="text-align:center; vertical-align:middle;">
      <div style="font-size:26px; font-weight:bold; color:#0a5d4b; letter-spacing:2px;">广州日晖智能科技有限公司</div>
    </td>
    <td style="width:170px;">&nbsp;</td>
  </tr>
</table>
<div style="text-align:center; font-size:28px; font-weight:bold; margin:14px 0 14px; letter-spacing:12px;">报 价 单</div>
<table style="width:100%; border-collapse:collapse; font-size:12px;" border="1" cellpadding="5" cellspacing="0">
  <tr>
    <td style="width:13%; background:#f7f7f7;">客户单位：</td><td style="width:37%;">{{customerName}}</td>
    <td style="width:13%; background:#f7f7f7;">报价日期：</td><td style="width:37%;">{{quoteDate}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">联系人：</td><td>{{contactPerson}}</td>
    <td style="background:#f7f7f7;">报价有效日期：</td><td>{{validFrom}} 至 {{validTo}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户电话：</td><td>{{contactPhone}}</td>
    <td style="background:#f7f7f7;">业务经手人：</td><td>{{salerName}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户传真：</td><td></td>
    <td style="background:#f7f7f7;">联系电话：</td><td>{{contactPhone}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户地址：</td><td>{{deliveryAddress}}</td>
    <td style="background:#f7f7f7;">发票类型：</td><td>普通发票 / 增值税发票</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">客户类别：</td><td></td>
    <td style="background:#f7f7f7;">运　　费：</td><td>现付（物流到客户仓库）</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">付款期限：</td><td>{{paymentTermName}}</td>
    <td style="background:#f7f7f7;">币种：</td><td>{{currencyCode}}</td>
  </tr>
</table>
{{/header}}

<div style="font-size:15px; font-weight:bold; margin:12px 0 8px;">1、报价一览表</div>
<table style="width:100%; border-collapse:collapse; font-size:12px;" border="1" cellpadding="6" cellspacing="0">
  <thead>
    <tr style="background:#0a5d4b; color:#ffffff;">
      <th style="width:50px; text-align:center;">序号</th>
      <th style="text-align:center;">物料名称</th>
      <th style="text-align:center;">型号规格</th>
      <th style="width:70px; text-align:center;">数量</th>
      <th style="width:90px; text-align:center;">单价（元）</th>
      <th style="width:100px; text-align:center;">总价（元）</th>
      <th style="text-align:center;">备注</th>
      <th style="width:80px; text-align:center;">图示</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="text-align:center;">{{lineNo}}</td>
      <td>{{materialName}}</td>
      <td>{{specification}}</td>
      <td style="text-align:right;">{{quantity}} {{unit}}</td>
      <td style="text-align:right;">{{unitPrice}}</td>
      <td style="text-align:right;">{{amount}}</td>
      <td>{{remark}}</td>
      <td style="text-align:center;"></td>
    </tr>
    {{/items}}
  </tbody>
</table>

{{#section}}
<table style="width:100%; border-collapse:collapse; font-size:12px; margin-top:0;" border="1" cellpadding="6" cellspacing="0">
  <tr>
    <td style="width:120px; background:#f7f7f7;">货期（交货日期）：</td>
    <td>{{deliveryDate}}</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">报价金额：</td>
    <td>{{totalAmount}} 元</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">价税合计：</td>
    <td>{{netAmount}} 元</td>
  </tr>
</table>
{{/section}}

{{#footer}}
<table style="width:100%; margin-top:24px; font-size:12px; border:none;">
  <tr>
    <td style="width:55%; border:none;">&nbsp;</td>
    <td style="width:45%; line-height:1.9; border:none;">
      公司名称：广州日晖智能科技有限公司<br/>
      联系电话：<br/>
      报价日期：{{quoteDate}}
    </td>
  </tr>
</table>
{{/footer}}',
'body { font-family: SimSun, "宋体", serif; color:#111; padding:0; margin:0; }
th { font-weight:bold; }
td, th { border-color:#333; }',
0, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 20,
'按用户提供的报价单原件复刻：RHTAGS 抬头 + 「报 价 单」大标题 + 双边信息表 + 深绿(#0a5d4b)表头明细表（序号/物料名称/型号规格/数量/单价/总价/备注/图示）+ 货期与金额 + 右下角公司落款。',
'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 1.2 销售合同（日晖风格） =====
('sale_contract_rihui_style', '销售合同-日晖风格默认模板', 'SALE_CONTRACT', 'html',
'{{#header}}
<table style="width:100%; margin-bottom:4px;">
  <tr>
    <td style="width:170px; vertical-align:middle; color:#0a5d4b; font-weight:bold; font-size:20px; letter-spacing:1px;">&laquo;RHTAGS&raquo;&reg;</td>
    <td style="text-align:center; vertical-align:middle;">
      <div style="font-size:24px; font-weight:bold; color:#0a5d4b; letter-spacing:2px;">广州日晖智能科技有限公司销售合同</div>
    </td>
    <td style="width:170px;">&nbsp;</td>
  </tr>
</table>
<table style="width:100%; font-size:13px; margin:6px 0 8px;">
  <tr>
    <td style="width:50%;">合同号：<b>{{contractNo}}</b></td>
    <td style="width:50%; text-align:right;">签订日期：{{contractDate}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:13px;" border="1" cellpadding="6" cellspacing="0">
  <tr>
    <td style="width:16%; background:#f7f7f7;">订购方单位：</td><td style="width:34%;">{{customerShortName}}</td>
    <td style="width:16%; background:#f7f7f7;">承揽方单位：</td><td style="width:34%;">广州日晖智能科技有限公司</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">联系人：</td><td>{{contactPerson}}</td>
    <td style="background:#f7f7f7;">联系人：</td><td></td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">承揽方：</td><td>广州日晖智能科技有限公司</td>
    <td style="background:#f7f7f7;">订购方：</td><td></td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">联系地址：</td><td>{{receiveAddress}}</td>
    <td style="background:#f7f7f7;">联系地址：</td><td>广州市增城区新塘镇大墩638号6栋</td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">联系电话：</td><td>{{contactPhone}}</td>
    <td style="background:#f7f7f7;">联系电话：</td><td></td>
  </tr>
  <tr>
    <td style="background:#f7f7f7;">传真：</td><td></td>
    <td style="background:#f7f7f7;">传真：</td><td></td>
  </tr>
</table>
<div style="font-size:13px; line-height:1.7; margin:10px 0;">
  甲乙双方本着友好合作、共同发展的目的，经过协商就以下产品的订购签订本合同，双方确认并遵守本合同所列所有以下条款：
</div>
{{/header}}

<table style="width:100%; border-collapse:collapse; font-size:13px;" border="1" cellpadding="6" cellspacing="0">
  <thead>
    <tr style="background:#0a5d4b; color:#ffffff;">
      <th style="text-align:center;">名&nbsp;&nbsp;称</th>
      <th style="text-align:center;">型号</th>
      <th style="width:90px; text-align:center;">单价</th>
      <th style="width:60px; text-align:center;">单位</th>
      <th style="width:70px; text-align:center;">数量</th>
      <th style="width:100px; text-align:center;">合计/元</th>
      <th style="text-align:center;">特殊要求</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td>{{materialName}}</td>
      <td>{{specification}}</td>
      <td style="text-align:right;">{{unitPrice}}</td>
      <td style="text-align:center;">{{unit}}</td>
      <td style="text-align:right;">{{quantity}}</td>
      <td style="text-align:right;">{{amount}}</td>
      <td>{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:13px; margin-top:4px;">
  <tr>
    <td style="width:50%;">总计金额：￥ {{totalAmount}} 元</td>
    <td style="width:50%;">是否含运输费用：否</td>
  </tr>
  <tr>
    <td>总计金额大写：</td>
    <td>是否含增值税发票：是</td>
  </tr>
</table>

{{#section}}
<div style="font-size:13px; line-height:1.9; margin-top:12px;">
1．交货时间及地点：<br/>
1）交货时间：生产周期：合同签订后7个工作日；<br/>
2）交货地点：广州<br/>
2．交货费用及方式：<br/>
1）乙方需按照甲方指定日期和需求物料品种、数量发货。<br/>
3．乙方产品验收：<br/>
1）甲方收到产品后，如发现有污损、欠数的情况，乙方应及时负责更换或补足数量。<br/>
2）乙方保证所提供的产品系全新、优质、完好无损，符合双方约定的技术和质量标准；并且符合国家相应规范要求。<br/>
4．结帐方式：<br/>
1）甲方在签订合同时，货款需发货前支付；<br/>
2）汇款信息　公司名称：广州日晖智能科技有限公司　公司帐号：360 2201 5091 0006 5694<br/>
开户银行：工商银行广州 支行<br/>
5．产品质保时间：<br/>
乙方所有供货产品均质保壹年，产品在使用过程中如果有品质问题，均免费保修或换新，由此产生的费用由乙方负责。<br/>
6．违约责任：<br/>
1）产品品种、规格、质量不符合同规定：乙方应负责保修、保退、保换。<br/>
2）产品数量不符合规定：少交而甲方仍有需要的，照数补交；<br/>
7．不可抗力因素：<br/>
在合同执行之过程中，若遇不可抗拒之因素致使合同不能继续执行，双方协商解决；若在合同执行中有异议或未尽事宜，双方协商解决，协商不成，请仲裁机构仲裁；仲裁未果，在签定地法院提起诉讼，按合同法确定。<br/>
8．本合同一式贰份，甲乙双方各执一份，双方签字盖章生效，传真件具有同等法律效力。
</div>
{{/section}}

{{#footer}}
<table style="width:100%; margin-top:26px; font-size:13px; border:none;">
  <tr>
    <td style="width:50%; border:none; line-height:2.0;">
      签字盖章：<br/>
      日期：{{contractDate}}
    </td>
    <td style="width:50%; border:none; line-height:2.0;">
      签字盖章：广州日晖智能科技有限公司<br/>
      日期：{{contractDate}}
    </td>
  </tr>
</table>
{{/footer}}',
'body { font-family: SimSun, "宋体", serif; color:#111; padding:0; margin:0; }
th { font-weight:bold; }
td, th { border-color:#333; }',
0, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 21,
'按用户提供的销售合同原件复刻：RHTAGS 抬头 + 合同标题与合同号 + 甲乙双方信息双列表 + 深绿(#0a5d4b)表头明细表（名称/型号/单价/单位/数量/合计/特殊要求）+ 总计金额 + 8 条固定条款 + 双方签字盖章。',
'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (template_code) DO NOTHING;

-- ===== 2. 设为对应单据类型的默认模板（先清同类型其它默认，再置本模板；幂等） =====
-- 2.1 销售报价单
UPDATE print_template SET is_default = 0, update_time = CURRENT_TIMESTAMP
 WHERE doc_type = 'SALE_QUOTE' AND is_default = 1 AND template_code <> 'sale_quote_rihui_style';
UPDATE print_template SET is_default = 1, update_time = CURRENT_TIMESTAMP
 WHERE template_code = 'sale_quote_rihui_style' AND is_default = 0;

-- 2.2 销售合同
UPDATE print_template SET is_default = 0, update_time = CURRENT_TIMESTAMP
 WHERE doc_type = 'SALE_CONTRACT' AND is_default = 1 AND template_code <> 'sale_contract_rihui_style';
UPDATE print_template SET is_default = 1, update_time = CURRENT_TIMESTAMP
 WHERE template_code = 'sale_contract_rihui_style' AND is_default = 0;