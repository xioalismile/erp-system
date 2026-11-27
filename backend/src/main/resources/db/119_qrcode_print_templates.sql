-- =====================================================
-- 二维码打印默认模板（2026-09-27，D1）
-- 内容: 商品标签（含二维码）/ 单据二维码（通用）/ 工序卡（含二维码）
-- 幂等: 按 template_code 判重，重复执行不产生重复行
-- 依赖: 打印模板渲染支持 {{qr:...}} 占位（后端 PrintTemplateServiceImpl）
-- =====================================================

-- 1) 商品标签（含二维码）—— doc_type=MATERIAL
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type, template_content,
    is_default, page_size, page_orientation, margin_top, margin_bottom, margin_left, margin_right,
    watermark_enabled, status, sort_order, remark, create_time, update_time
)
SELECT
    'MATERIAL_LABEL_QR', '商品标签（含二维码）', 'MATERIAL', 'html',
    $tpl$
<table style="width:100%;border-collapse:collapse;">
  <tr>
    <td style="width:180px;text-align:center;vertical-align:middle;">{{qr:material,size=140}}</td>
    <td style="vertical-align:middle;padding-left:8px;">
      <div style="font-size:16px;font-weight:bold;">{{materialName}}</div>
      <div style="font-size:12px;">编号：{{materialCode}}</div>
      <div style="font-size:12px;">型号：{{model}}</div>
      <div style="font-size:12px;">全称：{{fullName}}</div>
      <div style="font-size:12px;">单位：{{baseUnitName}}</div>
    </td>
  </tr>
</table>
$tpl$,
    1, '60mm 40mm', 'portrait', 5, 5, 5, 5,
    0, 1, 10, '商品二维码标签（扫商品码可打开商品）', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM print_template WHERE template_code = 'MATERIAL_LABEL_QR');

-- 2) 单据二维码（通用）—— doc_type=GENERIC（对所有单据可见，非默认不抢占）
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type, template_content,
    is_default, page_size, page_orientation, margin_top, margin_bottom, margin_left, margin_right,
    watermark_enabled, status, sort_order, remark, create_time, update_time
)
SELECT
    'BILL_QR_GENERIC', '单据二维码（通用）', 'GENERIC', 'html',
    $tpl$
<div style="text-align:center;">
  <div style="font-size:18px;font-weight:bold;">{{billTypeName}}</div>
  <div style="margin:10px 0;">{{qr:bill,size=220}}</div>
  <div style="font-size:14px;">单据编号：{{billNo}}</div>
  <div style="font-size:14px;">单据日期：{{billDate}}</div>
</div>
$tpl$,
    0, 'A4', 'portrait', 15, 15, 15, 15,
    0, 1, 20, '任意单据的二维码（扫单据码可打开该单据）', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM print_template WHERE template_code = 'BILL_QR_GENERIC');

-- 3) 工序卡（含二维码）—— doc_type=WORK_OPERATION
INSERT INTO print_template (
    template_code, template_name, doc_type, template_type, template_content,
    is_default, page_size, page_orientation, margin_top, margin_bottom, margin_left, margin_right,
    watermark_enabled, status, sort_order, remark, create_time, update_time
)
SELECT
    'WORK_OPERATION_CARD_QR', '工序卡（含二维码）', 'WORK_OPERATION', 'html',
    $tpl$
<table style="width:100%;border-collapse:collapse;">
  <tr>
    <td style="width:180px;text-align:center;vertical-align:middle;">{{qr:operation,size=150}}</td>
    <td style="vertical-align:middle;padding-left:8px;">
      <div style="font-size:16px;font-weight:bold;">{{operationName}}</div>
      <div style="font-size:12px;">工单号：{{orderNo}}</div>
      <div style="font-size:12px;">工序编码：{{operationCode}}</div>
      <div style="font-size:12px;">计划数量：{{planQuantity}}</div>
      <div style="font-size:12px;">工作中心：{{workCenterName}}</div>
      <div style="font-size:12px;">责任人：{{assigneeName}}</div>
    </td>
  </tr>
</table>
$tpl$,
    1, '80mm 50mm', 'portrait', 5, 5, 5, 5,
    0, 1, 30, '工序二维码卡（扫工序码可打开该工序操作面板）', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM print_template WHERE template_code = 'WORK_OPERATION_CARD_QR');
