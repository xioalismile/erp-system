-- =====================================================
-- 195_新增单据默认打印模板（2026-11-22）
-- 补齐 11 类单据的专用默认打印模板（此前未配置，打印会回退通用模板）：
--   SALE_EXCHANGE（销售换货单）/ SALE_REPAIR（售后维修单）/ OPERATION_REPORT（报工单）/
--   MRP_PLAN（MRP计划）/ SUBCONTRACT_PICK（委外发料单）/ SUBCONTRACT_INBOUND（委外入库单）/
--   SUBCONTRACT_RETURN（委外退料单）/ SUBCONTRACT_SUPPLEMENT（委外补料单）/
--   COST_CALCULATION（成本核算单）/ SALE_EXPENSE（销售费用单）/ PURCHASE_EXPENSE（采购费用单）
--
-- 商品标签（MATERIAL）与工序卡（WORK_OPERATION）已有默认模板（119_qrcode_print_templates.sql），
-- 本脚本不重复插入。
--
-- 口径：
--   1. 占位符只用各单据真实字段名（与 PrintDataFetcherService 取数结果一致），不造字段；
--   2. 全部 INSERT ... ON CONFLICT (template_code) DO NOTHING，幂等，重复执行零副作用；
--   3. 不改后端、不改既有模板；已存在的同名模板不覆盖。
-- =====================================================

INSERT INTO print_template (
    template_code, template_name, doc_type, template_type,
    template_content, template_style, is_default,
    page_size, page_orientation,
    margin_top, margin_bottom, margin_left, margin_right,
    header_content, footer_content, watermark, watermark_enabled,
    status, sort_order, remark, create_by, create_time, update_time
) VALUES

-- ===== 1. 销售换货单 =====
('sale_exchange_standard', '销售换货单-标准模板', 'SALE_EXCHANGE', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">销售换货单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{exchangeNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>销售员:</b> {{salerName}}<br/>
      <b>换货日期:</b> {{exchangeDate}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>来源订单:</b> {{sourceBillNo}}<br/>
      <b>币种:</b> {{currencyCode}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px; width:40px;">行号</th>
      <th style="border:1px solid #ccc; padding:5px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px;">规格</th>
      <th style="border:1px solid #ccc; padding:5px; width:55px;">单位</th>
      <th style="border:1px solid #ccc; padding:5px; width:70px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px; width:80px; text-align:right;">单价</th>
      <th style="border:1px solid #ccc; padding:5px; width:90px; text-align:right;">金额</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{lineNo}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{specification}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{unitPrice}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{amount}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:10px;">
  <tr>
    <td style="text-align:right;"><b>金额合计:</b> {{totalAmount}}　<b>价税合计:</b> {{netAmount}}</td>
  </tr>
  <tr>
    <td style="text-align:right; padding-top:24px;">制单人: {{createdBy}}　　审核人: ________　　客户签字: ________</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 1, '销售换货单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 2. 售后维修单 =====
('sale_repair_standard', '售后维修单-标准模板', 'SALE_REPAIR', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">售后维修单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{repairNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>联系人:</b> {{contactPerson}}　<b>电话:</b> {{contactPhone}}<br/>
      <b>来源单号:</b> {{sourceBillNo}}<br/>
      <b>服务类型:</b> {{serviceType}}　<b>保修状态:</b> {{warrantyStatus}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>收件日期:</b> {{receiveDate}}<br/>
      <b>返还日期:</b> {{returnDate}}<br/>
      <b>维修费:</b> {{repairFee}}　<b>配件费:</b> {{partsFee}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px; width:40px;">行号</th>
      <th style="border:1px solid #ccc; padding:5px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px;">规格</th>
      <th style="border:1px solid #ccc; padding:5px; width:55px;">单位</th>
      <th style="border:1px solid #ccc; padding:5px; width:60px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px;">故障描述</th>
      <th style="border:1px solid #ccc; padding:5px;">维修方案</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{lineNo}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{specification}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{faultDescription}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{repairPlan}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:10px;">
  <tr>
    <td style="text-align:right;"><b>维修费合计:</b> {{repairFee}}　<b>配件费合计:</b> {{partsFee}}　<b>合计费用:</b> {{totalFee}}</td>
  </tr>
  <tr>
    <td style="text-align:right; padding-top:24px;">制单人: {{createdBy}}　　维修人: ________　　客户签收: ________</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 2, '售后维修单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 3. 报工单（无明细，表头级） =====
('operation_report_standard', '报工单-标准模板', 'OPERATION_REPORT', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">报工单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{reportNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; border-collapse:collapse;">
  <tr>
    <td style="border:1px solid #ccc; padding:6px; width:18%; background:#fafafa;"><b>汇报日期</b></td>
    <td style="border:1px solid #ccc; padding:6px; width:32%;">{{reportDate}}</td>
    <td style="border:1px solid #ccc; padding:6px; width:18%; background:#fafafa;"><b>生产工单号</b></td>
    <td style="border:1px solid #ccc; padding:6px; width:32%;">{{productionOrderNo}}</td>
  </tr>
  <tr>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>工序号</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{processNo}}</td>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>工序名称</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{operationName}}</td>
  </tr>
  <tr>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>工作中心</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{workCenterName}}</td>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>报工人</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{employeeName}}</td>
  </tr>
  <tr>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>报工类型</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{reportType}}</td>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>班次</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{shiftType}}</td>
  </tr>
  <tr>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>报工数量</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{totalQuantity}}</td>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>合格数量</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{qualifiedQuantity}}</td>
  </tr>
  <tr>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>报废数量</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{rejectedQuantity}}</td>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>工时</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{workHours}}</td>
  </tr>
  <tr>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>状态</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{status}}</td>
    <td style="border:1px solid #ccc; padding:6px; background:#fafafa;"><b>备注</b></td>
    <td style="border:1px solid #ccc; padding:6px;">{{remark}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-top:24px;">
  <tr>
    <td style="width:33%;">报工人签字: ________</td>
    <td style="width:33%;">班组长签字: ________</td>
    <td style="width:33%;">制单人: {{createdBy}}</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 3, '报工单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 4. MRP计划 =====
('mrp_plan_standard', 'MRP计划-标准模板', 'MRP_PLAN', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">MRP计划</td>
    <td style="text-align:right; font-size:12px; color:#666;">计划单号: {{planNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>计划名称:</b> {{planName}}<br/>
      <b>计划日期:</b> {{planDate}}<br/>
      <b>需求来源:</b> {{demandSource}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>状态:</b> {{status}}<br/>
      <b>净需求合计:</b> {{totalNetDemand}}<br/>
      <b>建议采购:</b> {{totalPurchaseSuggestion}}　<b>建议生产:</b> {{totalProduceSuggestion}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:11px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:4px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:4px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:4px;">规格</th>
      <th style="border:1px solid #ccc; padding:4px; width:50px;">单位</th>
      <th style="border:1px solid #ccc; padding:4px; width:62px; text-align:right;">毛需求</th>
      <th style="border:1px solid #ccc; padding:4px; width:62px; text-align:right;">可用库存</th>
      <th style="border:1px solid #ccc; padding:4px; width:62px; text-align:right;">安全库存</th>
      <th style="border:1px solid #ccc; padding:4px; width:62px; text-align:right;">净需求</th>
      <th style="border:1px solid #ccc; padding:4px; width:62px; text-align:right;">计划数量</th>
      <th style="border:1px solid #ccc; padding:4px; width:60px;">建议类型</th>
      <th style="border:1px solid #ccc; padding:4px; width:70px;">建议日期</th>
      <th style="border:1px solid #ccc; padding:4px;">销售订单号</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:4px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:4px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:4px;">{{specification}}</td>
      <td style="border:1px solid #ccc; padding:4px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:4px; text-align:right;">{{grossDemand}}</td>
      <td style="border:1px solid #ccc; padding:4px; text-align:right;">{{availableStock}}</td>
      <td style="border:1px solid #ccc; padding:4px; text-align:right;">{{safetyStock}}</td>
      <td style="border:1px solid #ccc; padding:4px; text-align:right;">{{netDemand}}</td>
      <td style="border:1px solid #ccc; padding:4px; text-align:right;">{{planQuantity}}</td>
      <td style="border:1px solid #ccc; padding:4px; text-align:center;">{{suggestType}}</td>
      <td style="border:1px solid #ccc; padding:4px;">{{suggestDate}}</td>
      <td style="border:1px solid #ccc; padding:4px;">{{saleOrderNo}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:10px;">
  <tr><td style="text-align:right;">制单人: {{createBy}}　　制单时间: {{createTime}}</td></tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'landscape', 12, 12, 12, 12, NULL, NULL, NULL, 0, 1, 4, 'MRP计划默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 5. 委外发料单 =====
('subcontract_pick_standard', '委外发料单-标准模板', 'SUBCONTRACT_PICK', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">委外发料单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{pickNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>委外订单号:</b> {{orderNo}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>发料日期:</b> {{pickDate}}<br/>
      <b>发料数量:</b> {{totalQuantity}}　<b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px;">规格</th>
      <th style="border:1px solid #ccc; padding:5px; width:55px;">单位</th>
      <th style="border:1px solid #ccc; padding:5px; width:75px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px;">仓库</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{specification}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{warehouseName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:24px;">
  <tr>
    <td style="width:33%;">发料人: ________</td>
    <td style="width:33%;">领料人: ________</td>
    <td style="width:33%;">制单人: {{createBy}}</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 5, '委外发料单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 6. 委外入库单 =====
('subcontract_inbound_standard', '委外入库单-标准模板', 'SUBCONTRACT_INBOUND', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">委外入库单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{inboundNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>委外订单号:</b> {{orderNo}}<br/>
      <b>来源单号:</b> {{sourceBillNo}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>入库日期:</b> {{inboundDate}}<br/>
      <b>入库数量:</b> {{totalQuantity}}<br/>
      <b>加工费:</b> {{processingFee}}　<b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px;">规格</th>
      <th style="border:1px solid #ccc; padding:5px; width:55px;">单位</th>
      <th style="border:1px solid #ccc; padding:5px; width:70px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px;">仓库</th>
      <th style="border:1px solid #ccc; padding:5px; width:80px; text-align:right;">加工费</th>
      <th style="border:1px solid #ccc; padding:5px; width:70px; text-align:right;">合格数</th>
      <th style="border:1px solid #ccc; padding:5px; width:70px; text-align:right;">不合格数</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{specification}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{warehouseName}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{processingFee}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{qualifiedQuantity}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{rejectedQuantity}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:10px;">
  <tr><td style="text-align:right;"><b>加工费合计:</b> {{processingFee}}　<b>应付金额:</b> {{payableAmount}}</td></tr>
  <tr>
    <td style="text-align:right; padding-top:24px;">收货人: ________　　检验人: ________　　制单人: {{createBy}}</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 6, '委外入库单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 7. 委外退料单 =====
('subcontract_return_standard', '委外退料单-标准模板', 'SUBCONTRACT_RETURN', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">委外退料单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{returnNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>委外订单号:</b> {{orderNo}}<br/>
      <b>来源发料单号:</b> {{pickNo}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>退料日期:</b> {{returnDate}}<br/>
      <b>退料数量:</b> {{totalQuantity}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px;">规格</th>
      <th style="border:1px solid #ccc; padding:5px; width:55px;">单位</th>
      <th style="border:1px solid #ccc; padding:5px; width:75px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px;">仓库</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{specification}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{warehouseName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:24px;">
  <tr>
    <td style="width:33%;">退料人: ________</td>
    <td style="width:33%;">收料人: ________</td>
    <td style="width:33%;">制单人: {{createBy}}</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 7, '委外退料单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 8. 委外补料单 =====
('subcontract_supplement_standard', '委外补料单-标准模板', 'SUBCONTRACT_SUPPLEMENT', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">委外补料单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{supplementNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>委外订单号:</b> {{orderNo}}<br/>
      <b>来源发料单号:</b> {{pickNo}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>补料日期:</b> {{supplementDate}}<br/>
      <b>补料数量:</b> {{totalQuantity}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
  <tr>
    <td colspan="2"><b>补料原因:</b> {{reason}}</td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px;">规格</th>
      <th style="border:1px solid #ccc; padding:5px; width:55px;">单位</th>
      <th style="border:1px solid #ccc; padding:5px; width:75px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px;">仓库</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{specification}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{warehouseName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:24px;">
  <tr>
    <td style="width:33%;">发料人: ________</td>
    <td style="width:33%;">领料人: ________</td>
    <td style="width:33%;">制单人: {{createBy}}</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 8, '委外补料单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 9. 成本核算单 =====
('cost_calculation_standard', '成本核算单-标准模板', 'COST_CALCULATION', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">成本核算单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{calcNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>核算名称:</b> {{calcName}}<br/>
      <b>核算日期:</b> {{calcDate}}　<b>期间:</b> {{period}}<br/>
      <b>物料:</b> {{materialCode}} {{materialName}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>核算方法:</b> {{calcMethod}}<br/>
      <b>数量:</b> {{quantity}}　<b>版本:</b> {{version}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px;">成本类型</th>
      <th style="border:1px solid #ccc; padding:5px;">成本项目</th>
      <th style="border:1px solid #ccc; padding:5px;">物料编码</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px; width:55px;">单位</th>
      <th style="border:1px solid #ccc; padding:5px; width:70px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px; width:80px; text-align:right;">单价</th>
      <th style="border:1px solid #ccc; padding:5px; width:90px; text-align:right;">金额</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px;">{{costType}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{costName}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialCode}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:center;">{{unit}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{unitPrice}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{amount}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:10px;">
  <tr>
    <td style="text-align:right;">
      直接材料: {{directMaterialCost}}　直接人工: {{directLaborCost}}　
      制造费用: {{manufacturingOverhead}}　报废损失: {{scrapLoss}}<br/>
      <b>总成本: {{totalCost}}　单位成本: {{unitCost}}</b>
    </td>
  </tr>
  <tr>
    <td style="text-align:right; padding-top:24px;">制单人: {{createdBy}}　　审核人: ________</td>
  </tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 9, '成本核算单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 10. 销售费用单 =====
('sale_expense_standard', '销售费用单-标准模板', 'SALE_EXPENSE', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">销售费用单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{expenseNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>客户:</b> {{customerName}}<br/>
      <b>费用类型:</b> {{expenseTypeName}}<br/>
      <b>关联单据号:</b> {{relatedDocNo}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>费用日期:</b> {{expenseDate}}<br/>
      <b>费用金额:</b> {{totalAmount}}　<b>币种:</b> {{currencyCode}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px;">来源单号</th>
      <th style="border:1px solid #ccc; padding:5px;">来源类型</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px; width:70px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px; width:90px; text-align:right;">金额</th>
      <th style="border:1px solid #ccc; padding:5px; width:90px; text-align:right;">分摊金额</th>
      <th style="border:1px solid #ccc; padding:5px; width:80px; text-align:right;">分摊比例</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px;">{{sourceNo}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{sourceType}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{amount}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{sharingAmount}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{sharingRatio}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:10px;">
  <tr><td style="text-align:right;"><b>费用合计:</b> {{totalAmount}}　<b>本位币合计:</b> {{baseAmount}}</td></tr>
  <tr><td style="text-align:right; padding-top:24px;">制单人: {{createBy}}　　审核人: ________</td></tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 10, '销售费用单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- ===== 11. 采购费用单 =====
('purchase_expense_standard', '采购费用单-标准模板', 'PURCHASE_EXPENSE', 'html',
$tpl$<table style="width:100%; border-bottom:2px solid #333; margin-bottom:14px;">
  <tr>
    <td style="font-size:22px; font-weight:bold;">采购费用单</td>
    <td style="text-align:right; font-size:12px; color:#666;">单据编号: {{expenseNo}}</td>
  </tr>
</table>
<table style="width:100%; font-size:12px; margin-bottom:10px;">
  <tr>
    <td style="width:50%;">
      <b>供应商:</b> {{supplierName}}<br/>
      <b>费用类型:</b> {{expenseTypeName}}<br/>
      <b>关联单据号:</b> {{relatedDocNo}}
    </td>
    <td style="width:50%; text-align:right;">
      <b>费用日期:</b> {{expenseDate}}<br/>
      <b>费用金额:</b> {{totalAmount}}　<b>币种:</b> {{currencyCode}}<br/>
      <b>状态:</b> {{status}}
    </td>
  </tr>
</table>
<table style="width:100%; border-collapse:collapse; font-size:12px;">
  <thead>
    <tr style="background:#f0f0f0;">
      <th style="border:1px solid #ccc; padding:5px;">来源单号</th>
      <th style="border:1px solid #ccc; padding:5px;">来源类型</th>
      <th style="border:1px solid #ccc; padding:5px;">物料名称</th>
      <th style="border:1px solid #ccc; padding:5px; width:70px; text-align:right;">数量</th>
      <th style="border:1px solid #ccc; padding:5px; width:90px; text-align:right;">金额</th>
      <th style="border:1px solid #ccc; padding:5px; width:90px; text-align:right;">分摊金额</th>
      <th style="border:1px solid #ccc; padding:5px; width:80px; text-align:right;">分摊比例</th>
      <th style="border:1px solid #ccc; padding:5px;">备注</th>
    </tr>
  </thead>
  <tbody>
    {{#items}}
    <tr>
      <td style="border:1px solid #ccc; padding:5px;">{{sourceNo}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{sourceType}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{materialName}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{quantity}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{amount}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{sharingAmount}}</td>
      <td style="border:1px solid #ccc; padding:5px; text-align:right;">{{sharingRatio}}</td>
      <td style="border:1px solid #ccc; padding:5px;">{{remark}}</td>
    </tr>
    {{/items}}
  </tbody>
</table>
<table style="width:100%; font-size:12px; margin-top:10px;">
  <tr><td style="text-align:right;"><b>费用合计:</b> {{totalAmount}}　<b>本位币合计:</b> {{baseAmount}}</td></tr>
  <tr><td style="text-align:right; padding-top:24px;">制单人: {{createBy}}　　审核人: ________</td></tr>
</table>$tpl$,
'body { font-family: "Microsoft YaHei", sans-serif; margin: 0; padding: 15px; } table { table-layout: fixed; }',
1, 'A4', 'portrait', 15, 15, 15, 15, NULL, NULL, NULL, 0, 1, 11, '采购费用单默认打印模板', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)

ON CONFLICT (template_code) DO NOTHING;
