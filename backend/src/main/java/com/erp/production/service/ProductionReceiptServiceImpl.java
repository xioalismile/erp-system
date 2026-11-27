package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderProduct;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.mapper.WorkOrderMaterialMapper;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;
import com.erp.common.util.CustomFieldSearchHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class ProductionReceiptServiceImpl extends AbstractBillService<ProductionReceiptMapper, ProductionReceipt> implements ProductionReceiptService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与生产入库单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 经手人/部门/仓库/币种：列是外键，按名称筛的下拉传的是主键 ID → Spec.column 写外键「属性名」。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("receiptNo", new BillHeaderFilterSupport.Spec("receiptNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("productionOrderNo", new BillHeaderFilterSupport.Spec("productionOrderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("workOrderNo", new BillHeaderFilterSupport.Spec("workOrderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("handlerName", new BillHeaderFilterSupport.Spec("handlerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("warehouseName", new BillHeaderFilterSupport.Spec("warehouseId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("receiptDate", new BillHeaderFilterSupport.Spec("receiptDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("totalQuantity", new BillHeaderFilterSupport.Spec("totalQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("qualifiedQuantity", new BillHeaderFilterSupport.Spec("qualifiedQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("rejectedQuantity", new BillHeaderFilterSupport.Spec("rejectedQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("productionReceipt", ProductionReceipt.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "created_at",
            "created_by",
            "currency_id",
            "department_id",
            "exchange_rate",
            "handler_id",
            "line_no",
            "org_code",
            "production_order_id",
            "production_order_no",
            "qualified_quantity",
            "receipt_date",
            "receipt_id",
            "receipt_no",
            "rejected_quantity",
            "remark",
            "source_bill_no",
            "source_bill_type",
            "status",
            "total_amount",
            "total_quantity",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id",
            "work_order_no"
        );

    private final ProductionReceiptItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderProductMapper workOrderProductMapper;
    /** 【2026-09-11 材料成本归集】取工单用料累计材料成本，作为产成品入库成本 */
    private final WorkOrderMaterialMapper workOrderMaterialMapper;
    private final MaterialCostService materialCostService;
    /** 批次号规则（2026-11-06）：自动生成 / 重复入库 / 允许修改 */
    private final com.erp.inventory.service.BatchNoRuleService batchNoRuleService;
    /** 入库单质检（2026-11-07）：审核前必须完成质检、按处理方式拆库存 */
    private final com.erp.qc.service.InboundQcService inboundQcService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    /** 下游引用校验（重置草稿/作废/删除前禁止存在已生效下游） */
    @Autowired
    private BillRelationService billRelationService;

    /** 【2026-11-06 化妆品批记录】批次管理物料生产入库批号必填校验 */
    private final com.erp.masterdata.mapper.MaterialMapper materialMapper;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤生产入库单列表 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    private static final int SCALE = 2;
    private static final int QTY_SCALE = 4;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "生产入库单"; }
    @Override protected String getApprovalBillType() { return "PRODUCTION_RECEIPT"; }
    @Override protected String getBillNo(ProductionReceipt b) { return b.getReceiptNo(); }
    @Override protected Object getId(ProductionReceipt b) { return b.getReceiptId(); }
    @Override protected String getStatus(ProductionReceipt b) { return b.getStatus(); }
    @Override protected void setStatus(ProductionReceipt b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(ProductionReceipt b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(ProductionReceipt b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(ProductionReceipt b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(ProductionReceipt b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(ProductionReceipt b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(ProductionReceipt b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(ProductionReceipt b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(ProductionReceipt b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(ProductionReceipt b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(ProductionReceipt b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(ProductionReceipt b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(ProductionReceipt b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(ProductionReceipt b, String v) { b.setVoidReason(v); }

    @Override
    public List<String> listApprovedDownstreamBills(ProductionReceipt bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReceiptNo());
    }

    /** 提交审核前严格校验：明细非空 + 物料/仓库必填 + 批号规则（质检卡点在审核时另行校验） */
    @Override
    protected void validateStrictSubmit(ProductionReceipt receipt) {
        List<ProductionReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", receipt.getReceiptId()).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有入库明细，请至少添加一行物料后再提交");
        }
        for (ProductionReceiptItem item : items) {
            if (item.getMaterialId() == null) {
                throw new BusinessException("生产入库单第" + item.getLineNo() + "行缺少物料，无法提交");
            }
            if (item.getStockId() == null) {
                throw new BusinessException("第" + item.getLineNo() + "行[" + itemLabel(item) + "]未选择仓库，请选择仓库");
            }
        }
        validateLotBatchNo(receipt.getReceiptId());
    }

    /** 审核生效影响：刷新成本、按质检结果入库、回写工单完工数量/成品明细/自动结案 */
    @Override
    public void onApproved(ProductionReceipt receipt) {
        Long id = receipt.getReceiptId();
        String approver = receipt.getApprovedBy();
        List<ProductionReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));

        // === 审核时冻结实际入库成本（反审按原成本红字冲回；单价为 0 时回落最新核算成本） ===
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                if (item.getMaterialId() != null) {
                    BigDecimal inboundCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                    if (inboundCost.compareTo(BigDecimal.ZERO) <= 0) {
                        BigDecimal latest = materialCostService.getLatestCalcCost(item.getMaterialId());
                        if (latest != null) {
                            inboundCost = latest;
                        }
                    }
                    item.setAvgCost(inboundCost);
                    itemMapper.updateById(item);
                }
            }
        }

        // 【2026-09-11 材料成本归集】产成品入库成本（只做材料成本）：
        //   单位成本 = 工单用料累计材料成本合计 ÷ 工单数量。
        BigDecimal woMaterialUnitCost = BigDecimal.ZERO;
        if (receipt.getProductionOrderId() != null) {
            WorkOrder wo = workOrderMapper.selectById(receipt.getProductionOrderId());
            if (wo != null && wo.getQuantity() != null && wo.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                List<WorkOrderMaterial> woms = workOrderMaterialMapper.selectList(
                        new QueryWrapper<WorkOrderMaterial>().eq("order_id", wo.getOrderId()));
                BigDecimal materialTotal = BigDecimal.ZERO;
                if (woms != null) {
                    for (WorkOrderMaterial m : woms) {
                        if (m.getTotalCost() != null) {
                            materialTotal = materialTotal.add(m.getTotalCost());
                        }
                    }
                }
                if (materialTotal.compareTo(BigDecimal.ZERO) > 0) {
                    woMaterialUnitCost = materialTotal.divide(wo.getQuantity(), QTY_SCALE,
                            java.math.RoundingMode.HALF_UP);
                }
            }
        }

        // 审核通过后增加库存（入库仓库以明细行为准；开启质检时按每行处理方式拆库存）
        boolean qcOn = receipt.getQcEnabled() != null && receipt.getQcEnabled() == 1;
        Long defaultScrapId = null;
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                Long stockId = item.getStockId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                // 工单下推生成的入库单单价被硬编码为 0，用归集出的材料成本补上
                if (unitCost.compareTo(BigDecimal.ZERO) == 0 && woMaterialUnitCost.compareTo(BigDecimal.ZERO) > 0) {
                    unitCost = woMaterialUnitCost;
                    item.setUnitPrice(unitCost);
                    item.setPriceWithTax(unitCost.setScale(6, java.math.RoundingMode.HALF_UP));
                    item.setPriceWithoutTax(unitCost.setScale(6, java.math.RoundingMode.HALF_UP));
                    item.setAmount(quantity.multiply(unitCost).setScale(SCALE, java.math.RoundingMode.HALF_UP));
                    item.setAvgCost(unitCost);
                    itemMapper.updateById(item);
                }
                // 成本兜底（2026-11-26）：工单用料成本也为 0 时，用成品最新核算成本入库，
                // 避免“下推入库单单价 0 + 工单未归集成本”导致成品 0 成本入库。
                if (unitCost.compareTo(BigDecimal.ZERO) == 0 && item.getMaterialId() != null) {
                    BigDecimal latestCost = materialCostService.getLatestCalcCost(item.getMaterialId());
                    if (latestCost != null && latestCost.compareTo(BigDecimal.ZERO) > 0) {
                        unitCost = latestCost;
                        item.setUnitPrice(unitCost);
                        item.setPriceWithTax(unitCost.setScale(6, java.math.RoundingMode.HALF_UP));
                        item.setPriceWithoutTax(unitCost.setScale(6, java.math.RoundingMode.HALF_UP));
                        item.setAmount(quantity.multiply(unitCost).setScale(SCALE, java.math.RoundingMode.HALF_UP));
                        item.setAvgCost(unitCost);
                        itemMapper.updateById(item);
                    }
                }
                if (materialId == null) {
                    throw new BusinessException("生产入库单第" + item.getLineNo() + "行缺少物料，无法审核");
                }
                if (stockId == null) {
                    throw new BusinessException("第" + item.getLineNo() + "行[" + itemLabel(item)
                            + "]未选择仓库，请选择仓库");
                }
                BigDecimal qualified = qcOn ? nvl(item.getQualifiedQuantity()) : quantity;
                BigDecimal rejected = qcOn ? nvl(item.getRejectedQuantity()) : BigDecimal.ZERO;
                String handling = qcOn ? normalizeHandling(item.getHandlingType(), rejected)
                        : com.erp.qc.service.InboundQcService.HANDLE_RELEASE;

                BigDecimal enterQty;
                BigDecimal scrapQty = BigDecimal.ZERO;
                Long scrapWarehouseId = null;
                if (com.erp.qc.service.InboundQcService.HANDLE_RETURN.equals(handling)) {
                    enterQty = qualified;
                } else if (com.erp.qc.service.InboundQcService.HANDLE_SCRAP.equals(handling)) {
                    enterQty = qualified;
                    scrapQty = rejected;
                    scrapWarehouseId = item.getScrapWarehouseId();
                    if (scrapQty.compareTo(BigDecimal.ZERO) > 0 && scrapWarehouseId == null) {
                        if (defaultScrapId == null) defaultScrapId = inboundQcService.defaultScrapWarehouseId();
                        scrapWarehouseId = defaultScrapId;
                    }
                    if (scrapQty.compareTo(BigDecimal.ZERO) > 0 && scrapWarehouseId == null) {
                        throw new BusinessException("第" + item.getLineNo() + "行[" + itemLabel(item)
                                + "]选择进报废仓，但系统没有可用报废仓，请先在设置中心指定默认报废仓");
                    }
                } else {
                    enterQty = quantity;
                }
                if (enterQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.addStock(stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), enterQty),
                            unitCost, receipt.getReceiptNo(), "PRODUCTION_RECEIPT", "ProductionReceipt",
                            receipt.getReceiptNo(), approver);
                }
                if (scrapQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.addStock(scrapWarehouseId, null, materialId, item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), scrapQty),
                            unitCost, receipt.getReceiptNo(), "PRODUCTION_RECEIPT_SCRAP", "ProductionReceipt",
                            receipt.getReceiptNo(), approver);
                }
            }
        }

        // 归集后同步表头金额（下推生成的入库单表头 total_amount 恒为 0）
        if (items != null && !items.isEmpty()) {
            BigDecimal newTotal = BigDecimal.ZERO;
            for (ProductionReceiptItem it : items) {
                if (it.getAmount() != null) {
                    newTotal = newTotal.add(it.getAmount());
                }
            }
            if (newTotal.compareTo(BigDecimal.ZERO) > 0) {
                receipt.setTotalAmount(newTotal.setScale(SCALE, java.math.RoundingMode.HALF_UP));
                updateById(receipt);
            }
        }

        // 回写工单完工数量 + 成品明细完成数量 + 自动结案
        if (receipt.getProductionOrderId() != null) {
            WorkOrder workOrder = workOrderMapper.selectById(receipt.getProductionOrderId());
            if (workOrder != null) {
                BigDecimal totalQualified = resolveQualifiedQuantity(receipt, items);
                BigDecimal currentCompleted = workOrder.getCompletedQuantity() != null
                        ? workOrder.getCompletedQuantity() : BigDecimal.ZERO;
                BigDecimal newCompleted = currentCompleted.add(totalQualified);
                workOrder.setCompletedQuantity(newCompleted);
                boolean autoClosed = markAutoClosedIfCompleted(workOrder, newCompleted, approver);
                workOrderMapper.updateById(workOrder);
                log.info("工单 {} 完工数量回写: +{} → 累计 {}", workOrder.getOrderNo(), totalQualified, newCompleted);
                if (autoClosed) {
                    log.info("工单 {} 累计合格入库量 {} ≥ 计划数量 {}，已自动结案（close_status=1）",
                            workOrder.getOrderNo(), newCompleted, workOrder.getQuantity());
                }
                writeBackProductCompleted(workOrder.getOrderId(), items, totalQualified, 1, qcOn);
            }
        }

        log.info("生产入库单审核: {} 审核人: {}", receipt.getReceiptNo(), approver);
    }

    /** 重置草稿冲回：回冲库存 + 回退工单完工数量/成品明细/自动结案（与 onApproved 严格镜像） */
    @Override
    public void onReverseApproved(ProductionReceipt receipt) {
        reverseStockAndWorkOrder(receipt, receipt.getApprovedBy(), "重置草稿");
    }

    /** 作废冲回：作废前为已生效时，按 onApproved 的反向口径冲回库存与工单回写 */
    @Override
    public void onVoided(ProductionReceipt receipt) {
        reverseStockAndWorkOrder(receipt, receipt.getApprovedBy(), "作废");
    }

    /** 反作废恢复：恢复为已生效时，重新释放生效影响 */
    @Override
    public void onUnvoided(ProductionReceipt receipt) {
        onApproved(receipt);
    }

    /** 结案：双写业务执行态 bus_status=已关闭 */
    @Override
    public void onClosed(ProductionReceipt receipt) {
        UpdateWrapper<ProductionReceipt> uw = new UpdateWrapper<>();
        uw.eq("receipt_id", receipt.getReceiptId())
                .set("bus_status", com.erp.common.bill.BusStatusEnum.CLOSED.getLabel());
        update(uw);
    }

    /** 反结案：清空业务执行态 bus_status */
    @Override
    public void onReopened(ProductionReceipt receipt) {
        UpdateWrapper<ProductionReceipt> uw = new UpdateWrapper<>();
        uw.eq("receipt_id", receipt.getReceiptId()).set("bus_status", null);
        update(uw);
    }

    @Override
    @Transactional
    public void saveReceiptWithItems(ProductionReceipt receipt, List<ProductionReceiptItem> items) {
        boolean isNew = receipt.getReceiptId() == null;
        if (!isNew) {
            ProductionReceipt existing = getById(receipt.getReceiptId());
            if (existing != null) {
                BillStatusUtils.assertDirectModifiable("生产入库单", existing.getReceiptNo(), existing.getStatus(), existing.getCloseStatus());
            }
        }

        if (receipt.getReceiptNo() == null || receipt.getReceiptNo().isBlank()) {
            receipt.setReceiptNo(generateReceiptNo());
        }

        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (receipt.getStatus() == null || receipt.getStatus().isBlank()) receipt.setStatus("草稿");
        if (receipt.getApprovalStatus() == null || receipt.getApprovalStatus().isBlank()
                || ("草稿".equals(receipt.getStatus()) && "待审核".equals(receipt.getApprovalStatus()))) {
            receipt.setApprovalStatus("草稿");
        }
        if (receipt.getReceiptDate() == null) receipt.setReceiptDate(LocalDate.now());
        if (receipt.getExchangeRate() == null) receipt.setExchangeRate(BigDecimal.ONE);
        if (receipt.getTotalAmount() == null) receipt.setTotalAmount(BigDecimal.ZERO);
        if (receipt.getTotalQuantity() == null) receipt.setTotalQuantity(BigDecimal.ZERO);
        if (receipt.getQualifiedQuantity() == null) receipt.setQualifiedQuantity(BigDecimal.ZERO);
        if (receipt.getRejectedQuantity() == null) receipt.setRejectedQuantity(BigDecimal.ZERO);

        // 保存宽松：丢弃未选择物料的空行（material_id 等字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<ProductionReceiptItem> validItems = new ArrayList<>();
            for (ProductionReceiptItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 批次号规则（2026-11-06）：批次管理物料空批号自动生成；不允许修改时拦截手改
        List<ProductionReceiptItem> oldBatchItems = isNew ? new ArrayList<>()
                : itemMapper.selectList(new QueryWrapper<ProductionReceiptItem>()
                        .eq("receipt_id", receipt.getReceiptId()).orderByAsc("line_no"));
        applyBatchNoRules(items, oldBatchItems);

        recalculate(receipt, items);

        if (isNew) {
            save(receipt);
        } else {
            updateById(receipt);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", receipt.getReceiptId()));
            int lineNo = 10;
            for (ProductionReceiptItem item : items) {
                item.setReceiptItemId(null);
                item.setReceiptId(receipt.getReceiptId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                // === 回填最新核算成本 ===
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        // 口径统一（2026-09-21 用户指令「按标准来」）：保存=草稿，保存链路里绝不自动审核。
        // 历史写法是「免审批时保存后自动审核生效」，导致用户只点一次保存就已入账/已生效、且不能再修改，
        // 与全站口径「保存=草稿、提交才生效」冲突。生效动作一律交给提交侧（免审批时提交即生效）。

        log.info("生产入库单保存成功: {} 状态: {} 合计: {} 数量: {}",
                receipt.getReceiptNo(), receipt.getStatus(), receipt.getTotalAmount(), receipt.getTotalQuantity());
    }

    /**
     * 化妆品批记录·全链批号校验：批次管理物料的成品入库明细必须填写批号。
     * 仅对启用批次管理（material.is_lot_control=1）的物料生效，其余物料行为与旧版完全一致。
     */
    private void validateLotBatchNo(Long receiptId) {
        List<ProductionReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", receiptId).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) return;
        List<String> used = new ArrayList<>();
        for (ProductionReceiptItem it : items) {
            if (it.getBatchNo() != null && !it.getBatchNo().isBlank()) used.add(it.getBatchNo().trim());
        }
        for (int i = 0; i < items.size(); i++) {
            ProductionReceiptItem item = items.get(i);
            if (item.getMaterialId() == null) continue;
            com.erp.masterdata.entity.Material material = materialMapper.selectById(item.getMaterialId());
            if (material == null) continue;
            if (material.getIsLotControl() != null && material.getIsLotControl() == 1) {
                // 2026-11-06：历史草稿未填批号时，提交/审核阶段按规则补生成并落库
                if (item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                    String generated = batchNoRuleService.ensureBatchNo(item.getMaterialId(), null, used);
                    if (generated != null && !generated.isBlank()) {
                        item.setBatchNo(generated);
                        itemMapper.updateById(item);
                        used.add(generated);
                    }
                }
                if (item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                    throw new BusinessException("第" + (i + 1) + "行["
                            + (material.getMaterialCode() == null ? "" : material.getMaterialCode())
                            + "]批次管理物料必须填写批次号");
                }
                batchNoRuleService.checkRepeatInbound(item.getMaterialId(), item.getBatchNo(),
                        "第" + (i + 1) + "行[" + (material.getMaterialCode() == null ? "" : material.getMaterialCode()) + "]");
            }
        }
    }

    /**
     * 批次号规则落地（2026-11-06）：自动生成 + 不允许修改时拦截手改。
     * 生产入库保存时明细会整表重建，故按「行顺序 + 物料一致」对比原批号。
     */
    private void applyBatchNoRules(List<ProductionReceiptItem> items, List<ProductionReceiptItem> oldItems) {
        if (items == null || items.isEmpty()) return;
        List<String> used = new ArrayList<>();
        for (ProductionReceiptItem it : items) {
            if (it.getBatchNo() != null && !it.getBatchNo().isBlank()) used.add(it.getBatchNo().trim());
        }
        for (int i = 0; i < items.size(); i++) {
            ProductionReceiptItem it = items.get(i);
            if (it.getMaterialId() == null) continue;
            String oldNo = null;
            if (oldItems != null && oldItems.size() > i) {
                ProductionReceiptItem old = oldItems.get(i);
                if (old != null && it.getMaterialId().equals(old.getMaterialId())) oldNo = old.getBatchNo();
            }
            batchNoRuleService.checkModifyAllowed(it.getMaterialId(), oldNo, it.getBatchNo(), "第" + (i + 1) + "行");
            String ensured = batchNoRuleService.ensureBatchNo(it.getMaterialId(), it.getBatchNo(), used);
            if (ensured != null && !ensured.isBlank()) {
                it.setBatchNo(ensured);
                if (!used.contains(ensured)) used.add(ensured);
            }
        }
    }

    private void calculateItemAmount(ProductionReceiptItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        // 含税价 / 不含税价 两列（落库）：生产入库明细无税率列，二者均等于单价（口径与全站一致）
        item.setPriceWithTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
        BigDecimal amount = unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 价税合计 = 含税价 × 数量（含税价=不含税价=单价，故等于金额，2026-11-23 补齐）
        item.setNetAmount(item.getPriceWithTax().multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private void recalculate(ProductionReceipt receipt, List<ProductionReceiptItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal qualifiedQuantity = BigDecimal.ZERO;
        BigDecimal rejectedQuantity = BigDecimal.ZERO;
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
                totalNet = totalNet.add(item.getNetAmount() != null ? item.getNetAmount() : BigDecimal.ZERO);
                totalQuantity = totalQuantity.add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
                qualifiedQuantity = qualifiedQuantity.add(item.getQualifiedQuantity() != null ? item.getQualifiedQuantity() : BigDecimal.ZERO);
                rejectedQuantity = rejectedQuantity.add(item.getRejectedQuantity() != null ? item.getRejectedQuantity() : BigDecimal.ZERO);
            }
        }
        receipt.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        receipt.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
        receipt.setTotalQuantity(totalQuantity.setScale(QTY_SCALE, RoundingMode.HALF_UP));
        receipt.setQualifiedQuantity(qualifiedQuantity.setScale(QTY_SCALE, RoundingMode.HALF_UP));
        receipt.setRejectedQuantity(rejectedQuantity.setScale(QTY_SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status,
                                                   String startDate, String endDate, String employeeId,
                                                   String sortBy, String sortOrder) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder,
                null, java.util.Collections.emptyMap());
    }

    /** 老签名（仅表头字段过滤）委托新重载（customSearch 空 = 行为与改造前完全一致） */
    @Override
    public PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status,
                                                   String startDate, String endDate, String employeeId,
                                                   String sortBy, String sortOrder,
                                                   java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤 + 自定义字段搜索」（2026-10-14 / 2026-11-24）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status,
                                                   String startDate, String endDate, String employeeId,
                                                   String sortBy, String sortOrder, String customSearch,
                                                   java.util.Map<String, String> headerFilters) {
        QueryWrapper<ProductionReceipt> wrapper = new QueryWrapper<>();
        // 关键词（本页真实列 receipt_no / work_order_no）
        // —— 多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()：平铺会让命中第一列的行绕过状态筛选与数据权限）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no", "work_order_no");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 receipt_date）；员工=经手人（本页真实列 handler_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "handler_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "productionReceipt", headerFilters);
        // 数据权限：按角色 data_scope（统一数据权限）
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "production_receipt", customSearch, CustomFieldSearchHelper.idColumnOf("production_receipt"));
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, ProductionReceipt.class);
        Page<ProductionReceipt> page = new Page<>(current, size);
        Page<ProductionReceipt> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public ProductionReceipt getDetail(Long id) {
        ProductionReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        List<ProductionReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));
        receipt.setItems(items);
        return receipt;
    }

    /**
     * 审核（前端「提交」与「审核」共用一个入口）：
     * 草稿 + 审批开关开 → 按提交处理（待审核 + 生成待办）；否则走统一状态机审核（质检卡点前置校验）。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        ProductionReceipt receipt = getById((Long) id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        String st = receipt.getStatus();
        boolean draft = BillStatusUtils.isDraft(st);
        // 前端「提交」按钮直接调本方法：开关打开且仍在草稿时，本次调用按「提交」处理，不直接入库
        if (draft && needApprovalNow()) {
            submitForApproval(receipt, approver != null && !approver.isBlank() ? approver : null);
            log.info("生产入库单提交审批并生成审批待办: {}", receipt.getReceiptNo());
            return;
        }
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直审
        if (approvalRuntimeService.getRunningTask("PRODUCTION_RECEIPT", (Long) id) != null) {
            throw new BusinessException("生产入库单已提交审批流程，请由指定审批人在审批中心处理");
        }
        // 质检卡点：开启质检的单据必须完成质检才能审核
        inboundQcService.assertInspectionComplete(com.erp.qc.service.InboundQcService.BILL_PRODUCTION, (Long) id);
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /**
     * 把本次入库合格数量回写到工单成品明细(work_order_product.completed_quantity)，按工单行号匹配
     * @param direction 1=审核正向累加；-1=重置草稿逆向回退（与审核同口径取反，保证一正一反回到原值）
     */
    private void writeBackProductCompleted(Long orderId, List<ProductionReceiptItem> items, BigDecimal totalQualified,
                                           int direction, boolean qcOn) {
        List<WorkOrderProduct> products = workOrderProductMapper.selectByOrderId(orderId);
        if (products == null || products.isEmpty()) {
            return;
        }
        BigDecimal sign = BigDecimal.valueOf(direction);
        BigDecimal allocated = BigDecimal.ZERO;
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                BigDecimal q = effectiveQualified(item, qcOn);
                if (q.compareTo(BigDecimal.ZERO) <= 0 || item.getWorkOrderLineNo() == null) {
                    continue;
                }
                WorkOrderProduct target = null;
                for (WorkOrderProduct p : products) {
                    if (item.getWorkOrderLineNo().equals(p.getLineNo())) {
                        target = p;
                        break;
                    }
                }
                if (target == null) {
                    continue;
                }
                BigDecimal cur = target.getCompletedQuantity() != null ? target.getCompletedQuantity() : BigDecimal.ZERO;
                target.setCompletedQuantity(cur.add(q.multiply(sign)));
                workOrderProductMapper.updateById(target);
                allocated = allocated.add(q);
            }
        }
        // 明细未带工单行号或合格数量只落在表头时，整笔计入第一行，保证成品明细能看到入库量。
        // 注意：判定口径必须用无符号余量（否则重置草稿时 remain 为负，永远进不来，回退漏掉这一笔）
        BigDecimal remain = totalQualified.subtract(allocated);
        if (remain.compareTo(BigDecimal.ZERO) > 0) {
            WorkOrderProduct first = products.get(0);
            BigDecimal cur = first.getCompletedQuantity() != null ? first.getCompletedQuantity() : BigDecimal.ZERO;
            first.setCompletedQuantity(cur.add(remain.multiply(sign)));
            workOrderProductMapper.updateById(first);
        }
    }

    /**
     * 本入库单的有效合格入库量：按各行「不合格处理方式」计算 ——
     * 放行(RELEASE)=全量、自动退货(RETURN)/进报废仓(SCRAP)=合格数量；未开质检=全量。
     * 审核回写与重置草稿/作废回退**共用同一口径**，保证一正一反回到原值。
     */
    private BigDecimal resolveQualifiedQuantity(ProductionReceipt receipt, List<ProductionReceiptItem> items) {
        boolean qcOn = receipt.getQcEnabled() != null && receipt.getQcEnabled() == 1;
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                total = total.add(effectiveQualified(item, qcOn));
            }
        }
        return total;
    }

    /** 有效合格数量：放行=全量、自动退货/报废=质检合格数、未开质检=全量 */
    private static BigDecimal effectiveQualified(ProductionReceiptItem item, boolean qcOn) {
        BigDecimal quantity = nvl(item.getQuantity());
        if (!qcOn) return quantity;
        BigDecimal rejected = nvl(item.getRejectedQuantity());
        String handling = normalizeHandling(item.getHandlingType(), rejected);
        if (com.erp.qc.service.InboundQcService.HANDLE_RETURN.equals(handling)
                || com.erp.qc.service.InboundQcService.HANDLE_SCRAP.equals(handling)) {
            return nvl(item.getQualifiedQuantity());
        }
        return quantity;
    }

    /** 不合格处理方式归一：无不合格数量按放行处理 */
    private static String normalizeHandling(String handling, BigDecimal rejected) {
        if (rejected == null || rejected.compareTo(BigDecimal.ZERO) <= 0) {
            return com.erp.qc.service.InboundQcService.HANDLE_RELEASE;
        }
        if (handling == null || handling.isBlank()) {
            return com.erp.qc.service.InboundQcService.HANDLE_RELEASE;
        }
        return handling;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 多单位换算：库存记账只认基本单位数量（baseQuantity）；无折算快照时回落单据数量 */
    private static BigDecimal stockQuantity(BigDecimal baseQuantity, BigDecimal quantity) {
        if (baseQuantity != null) return baseQuantity;
        return quantity != null ? quantity : BigDecimal.ZERO;
    }

    /** 明细行展示用「编码 名称」 */
    private static String itemLabel(ProductionReceiptItem item) {
        String code = item.getMaterialCode() != null ? item.getMaterialCode() : "";
        String name = item.getMaterialName() != null ? item.getMaterialName() : "";
        return (code + " " + name).trim();
    }

    /**
     * 【自动闭环】累计合格入库量 ≥ 工单计划数量时，把工单置为「已结案」
     * （close_status=1 + is_closed=1，status 保持「已生效」）。
     *
     * <p>只改内存字段，由调用方统一 updateById（避免同一次审核对工单写两次）。返回是否本次触发结案。</p>
     *
     * <p>数据一致性：本方法直接操作 {@code WorkOrderMapper} 而**不注入 WorkOrderService**——
     * WorkOrderServiceImpl 已依赖 ProductionReceiptService（工单下推入库单），反向注入会形成循环依赖；
     * 此处只做「写数字段 + 置结案标志」的纯数据操作，不含工单侧业务逻辑。</p>
     */
    private boolean markAutoClosedIfCompleted(WorkOrder workOrder, BigDecimal completed, String operator) {
        if (workOrder == null) return false;
        if (Integer.valueOf(1).equals(workOrder.getCloseStatus())) return false; // 已结案，不重复处理
        BigDecimal plan = workOrder.getQuantity() != null ? workOrder.getQuantity() : BigDecimal.ZERO;
        if (plan.compareTo(BigDecimal.ZERO) <= 0 || completed.compareTo(plan) < 0) return false;
        workOrder.setCloseStatus(1);
        workOrder.setIsClosed((short) 1);
        workOrder.setClosedBy(operator);
        workOrder.setClosedAt(LocalDateTime.now());
        return true;
    }

    /**
     * 【自动闭环回退】重置草稿/作废使累计合格入库量跌破计划数量时，撤销结案标志（close_status=1 → 0）。
     * 口径与 {@link #markAutoClosedIfCompleted} 对称。
     */
    private void revertAutoCloseIfBelowPlan(WorkOrder workOrder, BigDecimal completed) {
        if (workOrder == null) return;
        if (!Integer.valueOf(1).equals(workOrder.getCloseStatus())) return;
        BigDecimal plan = workOrder.getQuantity() != null ? workOrder.getQuantity() : BigDecimal.ZERO;
        if (plan.compareTo(BigDecimal.ZERO) > 0 && completed.compareTo(plan) >= 0) return;
        workOrder.setCloseStatus(0);
        workOrder.setIsClosed((short) 0);
        log.info("工单 {} 累计合格入库量 {} 已低于计划数量 {}，撤销结案（close_status=0）",
                workOrder.getOrderNo(), completed, plan);
    }

    /**
     * 回退本入库单审核时对工单的回写：完工数量 − 合格入库量、成品明细完成数量 − 合格入库量、结案标志按达标情况回退。
     * 供「重置草稿」与「作废」两条路径共用（回退口径与审核回写严格一致，都取 qualified_quantity）。
     *
     * <p>直接操作 {@code WorkOrderMapper}（不注入 WorkOrderService）理由同 {@link #markAutoClosedIfCompleted}。</p>
     */
    private void rollbackWorkOrderWriteBack(ProductionReceipt bill, List<ProductionReceiptItem> items,
                                            String operator, String actionLabel) {
        if (bill.getProductionOrderId() == null) return;
        WorkOrder workOrder = workOrderMapper.selectById(bill.getProductionOrderId());
        if (workOrder == null) return;
        BigDecimal totalQualified = resolveQualifiedQuantity(bill, items);
        BigDecimal currentCompleted = workOrder.getCompletedQuantity() != null
                ? workOrder.getCompletedQuantity() : BigDecimal.ZERO;
        BigDecimal newCompleted = currentCompleted.subtract(totalQualified);
        workOrder.setCompletedQuantity(newCompleted);
        revertAutoCloseIfBelowPlan(workOrder, newCompleted);
        workOrderMapper.updateById(workOrder);
        log.info("生产入库单{}回退工单 {} 完工数量: -{} → 累计 {}",
                actionLabel, workOrder.getOrderNo(), totalQualified, newCompleted);
        writeBackProductCompleted(workOrder.getOrderId(), items, totalQualified, -1,
                bill.getQcEnabled() != null && bill.getQcEnabled() == 1);
    }

    /** 关闭: 已生效 → 结案（close_status=1；bus_status 由 onClosed 双写） */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        ProductionReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        if (reason != null && !reason.isBlank()) {
            receipt.setRemark((receipt.getRemark() != null ? receipt.getRemark() + " | " : "") + "关闭原因: " + reason);
            updateById(receipt);
        }
        super.close(id, closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        ProductionReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        if (!"草稿".equals(receipt.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + receipt.getStatus());
        }
        receipt.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            receipt.setRemark((receipt.getRemark() != null ? receipt.getRemark() + " | " : "") + "取消: " + reason);
        }
        updateById(receipt);
        log.info("生产入库单取消: {} 原因: {}", receipt.getReceiptNo(), reason);
    }

    @Override
    public String generateReceiptNo() {
        String docType = "PRODUCTION_RECEIPT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "MOIN-" + dateStr + "-";
            QueryWrapper<ProductionReceipt> wrapper = new QueryWrapper<>();
            wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
            ProductionReceipt last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReceiptNo() != null) {
                try {
                    String seqPart = last.getReceiptNo().substring(prefix.length());
                    seq = Integer.parseInt(seqPart) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 回冲库存 + 回退工单回写（重置草稿/作废共用，与 onApproved 严格镜像） */
    private void reverseStockAndWorkOrder(ProductionReceipt bill, String operator, String actionLabel) {
        List<ProductionReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", bill.getReceiptId()).orderByAsc("line_no"));

        // 1) 回冲库存：与审核时的拆分严格镜像（放行全量、退货只扣合格、报废再扣报废仓）
        boolean qcOn = bill.getQcEnabled() != null && bill.getQcEnabled() == 1;
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                Long stockId = item.getStockId();
                Long materialId = item.getMaterialId();
                if (stockId == null || materialId == null) {
                    continue;
                }
                BigDecimal quantity = nvl(item.getQuantity());
                BigDecimal qualified = qcOn ? nvl(item.getQualifiedQuantity()) : quantity;
                BigDecimal rejected = qcOn ? nvl(item.getRejectedQuantity()) : BigDecimal.ZERO;
                String handling = qcOn ? normalizeHandling(item.getHandlingType(), rejected)
                        : com.erp.qc.service.InboundQcService.HANDLE_RELEASE;
                BigDecimal enterQty;
                BigDecimal scrapQty = BigDecimal.ZERO;
                Long scrapWarehouseId = null;
                if (com.erp.qc.service.InboundQcService.HANDLE_RETURN.equals(handling)) {
                    enterQty = qualified;
                } else if (com.erp.qc.service.InboundQcService.HANDLE_SCRAP.equals(handling)) {
                    enterQty = qualified;
                    scrapQty = rejected;
                    scrapWarehouseId = item.getScrapWarehouseId();
                    if (scrapQty.compareTo(BigDecimal.ZERO) > 0 && scrapWarehouseId == null) {
                        scrapWarehouseId = inboundQcService.defaultScrapWarehouseId();
                    }
                } else {
                    enterQty = quantity;
                }
                if (enterQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.deductStockAtCost(stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), enterQty),
                            nvl(item.getAvgCost()),
                            bill.getReceiptNo(), "PRODUCTION_RECEIPT_REVERSE", "ProductionReceipt",
                            bill.getReceiptNo(), operator);
                }
                if (scrapWarehouseId != null && scrapQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.deductStockAtCost(scrapWarehouseId, null, materialId, item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), scrapQty),
                            nvl(item.getAvgCost()),
                            bill.getReceiptNo(), "PRODUCTION_RECEIPT_SCRAP_REVERSE", "ProductionReceipt",
                            bill.getReceiptNo(), operator);
                }
            }
        }

        // 2) 回退工单完工数量 / 成品明细完成数量 / 自动结案标志
        rollbackWorkOrderWriteBack(bill, items, operator, actionLabel);
        log.info("生产入库单{}: {} 操作人: {}", actionLabel, bill.getReceiptNo(), operator);
    }

    /** 重置草稿: 已生效 → 草稿（状态/下游校验由基类执行，库存与工单回冲由 onReverseApproved 执行） */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<ProductionReceipt> uw = new UpdateWrapper<>();
        uw.eq("receipt_id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 反结案: close_status=0（bus_status 由 onReopened 清空） */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    /** 删除生产入库单(仅草稿族可删且无下游已生效单据, 连同明细一起删除) */
    @Override
    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }
}
