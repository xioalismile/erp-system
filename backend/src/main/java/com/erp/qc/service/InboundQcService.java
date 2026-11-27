package com.erp.qc.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.qc.dto.InboundQcSaveRequest;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.entity.QcTemplateItem;
import com.erp.qc.mapper.QcRecordItemMapper;
import com.erp.qc.mapper.QcRecordMapper;
import com.erp.subcontract.inbound.entity.SubcontractInbound;
import com.erp.subcontract.inbound.entity.SubcontractInboundItem;
import com.erp.subcontract.inbound.mapper.SubcontractInboundItemMapper;
import com.erp.subcontract.inbound.mapper.SubcontractInboundMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库单质检闭环（2026-11-07）。
 *
 * <p>口径：</p>
 * <ul>
 *   <li>一张入库单一条质检记录（qc_record），明细按入库明细行一一对应（qc_record_item）；</li>
 *   <li>每行必须满足「合格数量 + 不合格数量 = 送检数量」；</li>
 *   <li>不合格数量&gt;0 时必须选择处理方式：RELEASE 放行 / RETURN 自动退货 / SCRAP 进报废仓；</li>
 *   <li>质检开关打开的单据，未完成质检不允许审核。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundQcService {

    public static final String BILL_PURCHASE = "PURCHASE_INBOUND";
    public static final String BILL_PRODUCTION = "PRODUCTION_RECEIPT";
    public static final String BILL_SUBCONTRACT = "SUBCONTRACT_INBOUND";

    public static final String HANDLE_RELEASE = "RELEASE";
    public static final String HANDLE_RETURN = "RETURN";
    public static final String HANDLE_SCRAP = "SCRAP";

    private final QcRecordMapper recordMapper;
    private final QcRecordItemMapper recordItemMapper;
    private final QcTemplateService templateService;
    private final SysConfigService sysConfigService;
    private final WarehouseService warehouseService;

    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;
    private final ProductionReceiptMapper productionReceiptMapper;
    private final ProductionReceiptItemMapper productionReceiptItemMapper;
    private final SubcontractInboundMapper subcontractInboundMapper;
    private final SubcontractInboundItemMapper subcontractInboundItemMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 质检上下文：单据口径 + 可用单据级模板 + 已保存质检记录 + 入库明细行。 */
    public Map<String, Object> context(String billType, Long billId) {
        billType = normalizeBillType(billType);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("billType", billType);
        out.put("billId", billId);
        out.put("defaultScrapWarehouseId", defaultScrapWarehouseId());
        out.put("templates", templateService.listByBillType(billType));

        BillInfo bill = loadBill(billType, billId);
        out.put("billNo", bill.billNo);
        out.put("qcEnabled", bill.qcEnabled);
        out.put("qcTemplateId", bill.qcTemplateId);
        out.put("qcStatus", bill.qcStatus);
        out.put("billItems", bill.items);

        QcRecord record = findRecord(billType, billId);
        if (record != null) {
            record.setItems(listRecordItems(record.getRecordId()));
        }
        out.put("inspection", record);
        return out;
    }

    /** 保存质检：写质检记录/明细，并回写入库单明细的合格/不合格数量与表头应付金额。 */
    @Transactional
    public QcRecord save(InboundQcSaveRequest req) {
        if (req == null || req.getBillType() == null || req.getBillId() == null) {
            throw new BusinessException("质检单据参数不完整");
        }
        req.setBillType(normalizeBillType(req.getBillType()));
        BillInfo bill = loadBill(req.getBillType(), req.getBillId());
        if (bill.qcEnabled == null || bill.qcEnabled != 1) {
            throw new BusinessException("该单据未开启质检开关，无需质检");
        }
        if (req.getTemplateId() == null) {
            throw new BusinessException("请选择质检模板");
        }
        QcTemplate template = templateService.getDetail(req.getTemplateId());
        if (template == null) {
            throw new BusinessException("所选质检模板不存在");
        }
        if (!"BILL".equals(template.getTemplateType())) {
            throw new BusinessException("入库单只能使用单据级质检模板");
        }
        if (template.getBillTypes() == null || !containsBillType(template.getBillTypes(), req.getBillType())) {
            throw new BusinessException("所选质检模板未绑定当前单据类型");
        }
        List<InboundQcSaveRequest.Item> items = req.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("质检明细不能为空");
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalQualified = BigDecimal.ZERO;
        BigDecimal totalRejected = BigDecimal.ZERO;
        BigDecimal allRejectedAmount = BigDecimal.ZERO;
        BigDecimal returnRejectedAmount = BigDecimal.ZERO;
        Long defaultScrapId = defaultScrapWarehouseId();
        String defaultScrapName = null;

        for (int i = 0; i < items.size(); i++) {
            InboundQcSaveRequest.Item it = items.get(i);
            int lineNo = i + 1;
            if (it.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行缺少物料，无法质检");
            }
            BigDecimal qty = nvl(it.getQuantity());
            BigDecimal qualified = nvl(it.getQualifiedQuantity());
            BigDecimal rejected = nvl(it.getRejectedQuantity());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行送检数量必须大于0");
            }
            if (qualified.compareTo(BigDecimal.ZERO) < 0 || rejected.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("第" + lineNo + "行合格/不合格数量不能为负数");
            }
            if (qualified.add(rejected).subtract(qty).abs().compareTo(new BigDecimal("0.0001")) > 0) {
                throw new BusinessException("第" + lineNo + "行[" + safe(it.getMaterialCode()) + " " + safe(it.getMaterialName())
                        + "]合格数量 + 不合格数量必须等于送检数量 " + qty.stripTrailingZeros().toPlainString());
            }
            if (rejected.compareTo(BigDecimal.ZERO) > 0) {
                String handling = it.getHandlingType();
                if (handling == null || handling.isBlank()) {
                    throw new BusinessException("第" + lineNo + "行[" + safe(it.getMaterialCode()) + " " + safe(it.getMaterialName())
                            + "]有不合格数量，请选择不合格处理方式");
                }
                if (!HANDLE_RELEASE.equals(handling) && !HANDLE_RETURN.equals(handling) && !HANDLE_SCRAP.equals(handling)) {
                    throw new BusinessException("第" + lineNo + "行不合格处理方式不合法");
                }
                if (HANDLE_SCRAP.equals(handling)) {
                    if (it.getScrapWarehouseId() == null) it.setScrapWarehouseId(defaultScrapId);
                    if (it.getScrapWarehouseId() == null) {
                        throw new BusinessException("第" + lineNo + "行选择进报废仓，但系统没有可用报废仓，请先在设置中心指定默认报废仓");
                    }
                }
            }
            if (it.getScrapWarehouseId() != null) {
                Warehouse w = warehouseService.getById(it.getScrapWarehouseId());
                if (w == null) {
                    throw new BusinessException("第" + lineNo + "行所选报废仓不存在，请重新选择");
                }
                it.setScrapWarehouseName(w.getStockName());
                if (defaultScrapName == null && it.getScrapWarehouseId().equals(defaultScrapId)) {
                    defaultScrapName = w.getStockName();
                }
            }
            totalQty = totalQty.add(qty);
            totalQualified = totalQualified.add(qualified);
            totalRejected = totalRejected.add(rejected);
            BigDecimal rejectedAmount = rejected.multiply(bill.itemUnitPrices.getOrDefault(it.getSourceItemId(), BigDecimal.ZERO));
            allRejectedAmount = allRejectedAmount.add(rejectedAmount);
            if (HANDLE_RETURN.equals(it.getHandlingType())) {
                returnRejectedAmount = returnRejectedAmount.add(rejectedAmount);
            }
            validateTemplateRequired(template.getItems(), it, lineNo);
        }

        String operator = req.getInspectorName() != null && !req.getInspectorName().isBlank()
                ? req.getInspectorName() : BillApprovalAdapter.currentSubmitter();
        if (operator == null || operator.isBlank()) operator = "system";

        QcRecord record = findRecord(req.getBillType(), req.getBillId());
        boolean isNew = record == null;
        if (isNew) {
            record = new QcRecord();
            record.setRecordNo("QC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
            record.setQcType("全检");
            record.setQcStage(qcStageOf(req.getBillType()));
            record.setOrderId(req.getBillId());
            record.setOrderNo(bill.billNo);
            record.setInspectTime(LocalDateTime.now());
        }
        record.setTemplateId(req.getTemplateId());
        record.setSourceBillType(req.getBillType());
        record.setSourceBillId(req.getBillId());
        record.setInspectQty(totalQty);
        record.setQualifiedQty(totalQualified);
        record.setDefectQty(totalRejected);
        record.setResult(totalRejected.compareTo(BigDecimal.ZERO) == 0 ? "合格" : "不合格");
        record.setHandleType(resolveRecordHandleType(items));
        record.setInspectorName(operator);
        record.setScrapWarehouseId(defaultScrapId);
        record.setScrapWarehouseName(defaultScrapName);
        record.setRemark(req.getRemark());
        if (isNew) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
            recordItemMapper.delete(new QueryWrapper<QcRecordItem>().eq("record_id", record.getRecordId()));
        }

        int lineNo = 1;
        for (InboundQcSaveRequest.Item it : items) {
            QcRecordItem row = new QcRecordItem();
            row.setRecordId(record.getRecordId());
            row.setLineNo(it.getLineNo() != null ? it.getLineNo() : lineNo);
            row.setItemName(it.getMaterialName());
            row.setMaterialId(it.getMaterialId());
            row.setMaterialCode(it.getMaterialCode());
            row.setMaterialName(it.getMaterialName());
            row.setQuantity(nvl(it.getQuantity()));
            row.setQualifiedQuantity(nvl(it.getQualifiedQuantity()));
            row.setRejectedQuantity(nvl(it.getRejectedQuantity()));
            row.setHandlingType(nvl(it.getRejectedQuantity()).compareTo(BigDecimal.ZERO) > 0
                    ? it.getHandlingType() : HANDLE_RELEASE);
            row.setScrapWarehouseId(it.getScrapWarehouseId());
            row.setScrapWarehouseName(it.getScrapWarehouseName());
            row.setExtData(it.getExtData());
            row.setResult(nvl(it.getRejectedQuantity()).compareTo(BigDecimal.ZERO) == 0 ? "合格" : "不合格");
            row.setRemark(it.getRejectReason() != null ? it.getRejectReason() : it.getRemark());
            recordItemMapper.insert(row);
            lineNo++;
        }

        writeback(req.getBillType(), req.getBillId(), items, totalQty, totalQualified, totalRejected,
                allRejectedAmount, returnRejectedAmount, operator);
        log.info("入库单质检保存: 类型={} 单据={} 记录={} 合格={} 不合格={}",
                req.getBillType(), bill.billNo, record.getRecordNo(), totalQualified, totalRejected);
        return record;
    }

    /** 审核前置校验：质检开关打开时，必须已完成质检且每行数量完整。 */
    public void assertInspectionComplete(String billType, Long billId) {
        billType = normalizeBillType(billType);
        BillInfo bill = loadBill(billType, billId);
        if (bill.qcEnabled == null || bill.qcEnabled != 1) return;
        QcRecord record = findRecord(billType, billId);
        if (record == null) {
            throw new BusinessException("该单据已开启质检，请先完成质检再审核");
        }
        for (Map<String, Object> item : bill.items) {
            int lineNo = ((Number) item.get("lineNo")).intValue();
            BigDecimal qty = (BigDecimal) item.get("quantity");
            BigDecimal qualified = (BigDecimal) item.get("qualifiedQuantity");
            BigDecimal rejected = (BigDecimal) item.get("rejectedQuantity");
            if (qualified == null || rejected == null
                    || qualified.add(rejected).subtract(qty == null ? BigDecimal.ZERO : qty).abs()
                    .compareTo(new BigDecimal("0.0001")) > 0) {
                throw new BusinessException("第" + lineNo + "行[" + safe((String) item.get("materialCode")) + " "
                        + safe((String) item.get("materialName")) + "]质检数量未填写完整，请先完成质检");
            }
            if (rejected.compareTo(BigDecimal.ZERO) > 0) {
                Object handling = item.get("handlingType");
                if (handling == null || String.valueOf(handling).isBlank()) {
                    throw new BusinessException("第" + lineNo + "行[" + safe((String) item.get("materialCode")) + " "
                            + safe((String) item.get("materialName")) + "]有不合格数量但未选择处理方式");
                }
            }
        }
    }

    public Long defaultScrapWarehouseId() {
        String v = sysConfigService.getConfigValue("inv.scrap.warehouse.id");
        if (v != null && !v.isBlank()) {
            try {
                return Long.parseLong(v.trim());
            } catch (Exception e) {
                log.warn("默认报废仓参数不合法: {}", v);
            }
        }
        List<Warehouse> list = warehouseService.list(new QueryWrapper<Warehouse>().eq("stock_code", "SCRAP"));
        return list.isEmpty() ? null : list.get(0).getStockId();
    }

    public QcRecord findRecord(String billType, Long billId) {
        return recordMapper.selectOne(new QueryWrapper<QcRecord>()
                .eq("source_bill_type", billType).eq("source_bill_id", billId).last("LIMIT 1"));
    }

    // ---------------- 内部 ----------------

    private static String qcStageOf(String billType) {
        if (BILL_PRODUCTION.equals(billType)) return "成品检";
        return "来料检";
    }

    /** 单据类型归一：委外入库在审批链路里用的是 SUBCONTRACT_RECEIPT，统一归到进货质检口径 */
    private static String normalizeBillType(String billType) {
        if (billType == null) return null;
        String t = billType.trim();
        if ("SUBCONTRACT_RECEIPT".equals(t)) return BILL_SUBCONTRACT;
        return t;
    }

    private static boolean containsBillType(String billTypes, String billType) {
        if (billTypes == null || billTypes.isBlank()) return false;
        for (String s : billTypes.split(",")) {
            if (billType.equals(s.trim())) return true;
        }
        return false;
    }

    /** 整单处理方式：无不合格=空；各行一致=该值；不一致=MIXED */
    private static String resolveRecordHandleType(List<InboundQcSaveRequest.Item> items) {
        String first = null;
        boolean mixed = false;
        for (InboundQcSaveRequest.Item it : items) {
            if (nvl(it.getRejectedQuantity()).compareTo(BigDecimal.ZERO) <= 0) continue;
            String h = it.getHandlingType();
            if (h == null || h.isBlank()) continue;
            if (first == null) first = h;
            else if (!first.equals(h)) mixed = true;
        }
        if (first == null) return null;
        return mixed ? "MIXED" : first;
    }

    private void writeback(String billType, Long billId, List<InboundQcSaveRequest.Item> items,
                           BigDecimal totalQty, BigDecimal totalQualified, BigDecimal totalRejected,
                           BigDecimal allRejectedAmount, BigDecimal returnRejectedAmount, String operator) {
        if (BILL_PURCHASE.equals(billType)) {
            for (InboundQcSaveRequest.Item it : items) {
                if (it.getSourceItemId() == null) continue;
                PurchaseInboundItem row = new PurchaseInboundItem();
                row.setReceiptItemId(it.getSourceItemId());
                row.setQualifiedQuantity(nvl(it.getQualifiedQuantity()));
                row.setRejectedQuantity(nvl(it.getRejectedQuantity()));
                row.setHandlingType(it.getHandlingType());
                row.setScrapWarehouseId(it.getScrapWarehouseId());
                row.setScrapWarehouseName(it.getScrapWarehouseName());
                purchaseInboundItemMapper.updateById(row);
            }
            PurchaseInbound header = purchaseInboundMapper.selectById(billId);
            if (header != null) {
                BigDecimal total = header.getTotalAmount() == null ? BigDecimal.ZERO : header.getTotalAmount();
                BigDecimal payable = total.subtract(returnRejectedAmount).max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                purchaseInboundMapper.update(null, new UpdateWrapper<PurchaseInbound>()
                        .eq("receipt_id", billId)
                        .set("qc_status", "已质检")
                        .set("qualified_quantity", totalQualified)
                        .set("rejected_quantity", totalRejected)
                        .set("rejected_amount", allRejectedAmount.setScale(2, RoundingMode.HALF_UP))
                        .set("payable_amount", payable)
                        .set("updated_by", operator));
            }
        } else if (BILL_PRODUCTION.equals(billType)) {
            for (InboundQcSaveRequest.Item it : items) {
                if (it.getSourceItemId() == null) continue;
                ProductionReceiptItem row = new ProductionReceiptItem();
                row.setReceiptItemId(it.getSourceItemId());
                row.setQualifiedQuantity(nvl(it.getQualifiedQuantity()));
                row.setRejectedQuantity(nvl(it.getRejectedQuantity()));
                row.setHandlingType(it.getHandlingType());
                row.setScrapWarehouseId(it.getScrapWarehouseId());
                row.setScrapWarehouseName(it.getScrapWarehouseName());
                productionReceiptItemMapper.updateById(row);
            }
            productionReceiptMapper.update(null, new UpdateWrapper<ProductionReceipt>()
                    .eq("receipt_id", billId)
                    .set("qc_status", "已质检")
                    .set("qualified_quantity", totalQualified)
                    .set("rejected_quantity", totalRejected)
                    .set("updated_by", operator));
        } else if (BILL_SUBCONTRACT.equals(billType)) {
            for (InboundQcSaveRequest.Item it : items) {
                if (it.getSourceItemId() == null) continue;
                SubcontractInboundItem row = new SubcontractInboundItem();
                row.setId(it.getSourceItemId());
                row.setQualifiedQuantity(nvl(it.getQualifiedQuantity()));
                row.setRejectedQuantity(nvl(it.getRejectedQuantity()));
                row.setHandlingType(it.getHandlingType());
                row.setScrapWarehouseId(it.getScrapWarehouseId());
                row.setScrapWarehouseName(it.getScrapWarehouseName());
                subcontractInboundItemMapper.updateById(row);
            }
            SubcontractInbound header = subcontractInboundMapper.selectById(billId);
            if (header != null) {
                BigDecimal fee = header.getProcessingFee() == null ? BigDecimal.ZERO : header.getProcessingFee();
                BigDecimal payable = fee.subtract(returnRejectedAmount).max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                subcontractInboundMapper.update(null, new UpdateWrapper<SubcontractInbound>()
                        .eq("id", billId)
                        .set("qc_status", "已质检")
                        .set("qualified_quantity", totalQualified)
                        .set("rejected_quantity", totalRejected)
                        .set("rejected_amount", allRejectedAmount.setScale(2, RoundingMode.HALF_UP))
                        .set("payable_amount", payable)
                        .set("updated_by", operator));
            }
        }
    }

    /** 模板必填检验项校验：extData（JSON 文本）里按「检验项名称」取值。 */
    private void validateTemplateRequired(List<QcTemplateItem> fields, InboundQcSaveRequest.Item item, int lineNo) {
        if (fields == null || fields.isEmpty()) return;
        Map<String, Object> ext = new HashMap<>();
        if (item.getExtData() != null && !item.getExtData().isBlank()) {
            try {
                ext = objectMapper.readValue(item.getExtData(), HashMap.class);
            } catch (Exception e) {
                throw new BusinessException("第" + lineNo + "行质检字段格式不合法");
            }
        }
        for (QcTemplateItem f : fields) {
            if (f.getIsRequired() != null && f.getIsRequired() == 1) {
                Object v = ext.get(f.getItemName());
                if (v == null || String.valueOf(v).isBlank()) {
                    throw new BusinessException("第" + lineNo + "行质检项「" + f.getItemName() + "」不能为空");
                }
            }
        }
    }

    private List<QcRecordItem> listRecordItems(Long recordId) {
        return recordItemMapper.selectList(new QueryWrapper<QcRecordItem>()
                .eq("record_id", recordId).orderByAsc("line_no").orderByAsc("item_id"));
    }

    private BillInfo loadBill(String billType, Long billId) {
        BillInfo info = new BillInfo();
        info.billId = billId;
        List<Map<String, Object>> items = new ArrayList<>();
        if (BILL_PURCHASE.equals(billType)) {
            PurchaseInbound b = purchaseInboundMapper.selectById(billId);
            if (b == null) throw new BusinessException("采购入库单不存在");
            info.billNo = b.getReceiptNo();
            info.qcEnabled = b.getQcEnabled();
            info.qcTemplateId = b.getQcTemplateId();
            info.qcStatus = b.getQcStatus();
            info.supplierId = b.getSupplierId();
            info.warehouseId = b.getStockId();
            List<PurchaseInboundItem> rows = purchaseInboundItemMapper.selectList(
                    new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", billId).orderByAsc("line_no"));
            for (PurchaseInboundItem r : rows) {
                items.add(baseItem(r.getReceiptItemId(), r.getLineNo(), r.getMaterialId(),
                        r.getMaterialCode(), r.getMaterialName(), r.getModel(), r.getUnitId(), r.getQuantity(),
                        r.getQualifiedQuantity(), r.getRejectedQuantity(), r.getHandlingType(),
                        r.getScrapWarehouseId()));
                info.itemUnitPrices.put(r.getReceiptItemId(), r.getUnitPrice() == null ? BigDecimal.ZERO : r.getUnitPrice());
            }
        } else if (BILL_PRODUCTION.equals(billType)) {
            ProductionReceipt b = productionReceiptMapper.selectById(billId);
            if (b == null) throw new BusinessException("生产入库单不存在");
            info.billNo = b.getReceiptNo();
            info.qcEnabled = b.getQcEnabled();
            info.qcTemplateId = b.getQcTemplateId();
            info.qcStatus = b.getQcStatus();
            info.warehouseId = b.getWarehouseId();
            List<ProductionReceiptItem> rows = productionReceiptItemMapper.selectList(
                    new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", billId).orderByAsc("line_no"));
            for (ProductionReceiptItem r : rows) {
                items.add(baseItem(r.getReceiptItemId(), r.getLineNo(), r.getMaterialId(),
                        r.getMaterialCode(), r.getMaterialName(), r.getSpecification(), r.getUnitId(), r.getQuantity(),
                        r.getQualifiedQuantity(), r.getRejectedQuantity(), r.getHandlingType(),
                        r.getScrapWarehouseId()));
                info.itemUnitPrices.put(r.getReceiptItemId(), r.getUnitPrice() == null ? BigDecimal.ZERO : r.getUnitPrice());
            }
        } else if (BILL_SUBCONTRACT.equals(billType)) {
            SubcontractInbound b = subcontractInboundMapper.selectById(billId);
            if (b == null) throw new BusinessException("委外入库单不存在");
            info.billNo = b.getInboundNo();
            info.qcEnabled = b.getQcEnabled();
            info.qcTemplateId = b.getQcTemplateId();
            info.qcStatus = b.getQcStatus();
            info.supplierId = b.getSupplierId();
            List<SubcontractInboundItem> rows = subcontractInboundItemMapper.selectList(
                    new QueryWrapper<SubcontractInboundItem>().eq("inbound_id", billId).orderByAsc("id"));
            for (SubcontractInboundItem r : rows) {
                items.add(baseItem(r.getId(), items.size() + 1, r.getMaterialId(),
                        r.getMaterialCode(), r.getMaterialName(), r.getSpecification(), null, r.getQuantity(),
                        r.getQualifiedQuantity(), r.getRejectedQuantity(), r.getHandlingType(),
                        r.getScrapWarehouseId()));
                BigDecimal fee = r.getProcessingFee() == null ? BigDecimal.ZERO : r.getProcessingFee();
                BigDecimal qty = r.getQuantity() == null ? BigDecimal.ZERO : r.getQuantity();
                info.itemUnitPrices.put(r.getId(), qty.compareTo(BigDecimal.ZERO) > 0
                        ? fee.divide(qty, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            }
        } else {
            throw new BusinessException("不支持的质检单据类型：" + billType);
        }
        info.items = items;
        return info;
    }

    private Map<String, Object> baseItem(Long sourceItemId, Integer lineNo, Long materialId, String code, String name,
                                         String spec, Long unitId, BigDecimal qty, BigDecimal qualified,
                                         BigDecimal rejected, String handlingType, Long scrapWarehouseId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceItemId", sourceItemId);
        m.put("lineNo", lineNo == null ? 0 : lineNo);
        m.put("materialId", materialId);
        m.put("materialCode", code);
        m.put("materialName", name);
        m.put("specification", spec);
        m.put("unitId", unitId);
        m.put("quantity", qty);
        m.put("qualifiedQuantity", qualified);
        m.put("rejectedQuantity", rejected);
        m.put("handlingType", handlingType);
        m.put("scrapWarehouseId", scrapWarehouseId);
        return m;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static class BillInfo {
        Long billId;
        String billNo;
        Integer qcEnabled;
        Long qcTemplateId;
        String qcStatus;
        Long supplierId;
        Long warehouseId;
        List<Map<String, Object>> items = new ArrayList<>();
        Map<Long, BigDecimal> itemUnitPrices = new HashMap<>();
    }
}
