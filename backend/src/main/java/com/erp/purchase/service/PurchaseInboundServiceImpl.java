package com.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.entity.Material;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.EmployeeService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.purchase.entity.*;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.purchase.mapper.PurchaseInvoiceMapper;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.erp.purchase.mapper.PurchaseReturnMapper;
import com.erp.qc.service.InboundQcService;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.common.util.TaxAmountUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采购入库单服务实现
 * 文档3.3 单据状态流转: 草稿 → 待审核 → 已生效
 * 修复点: PIN-单号带连字符, 行号从1步长1, 无折扣, 提交/重置草稿, 库存增减, 回写采购订单已入库数量
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class PurchaseInboundServiceImpl extends AbstractBillService<PurchaseInboundMapper, PurchaseInbound> implements PurchaseInboundService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 按名称筛但列是外键的场景（供应商/采购员/部门）：Spec.column 写外键「属性名」，
        // 不写裸列名 —— 属性名注册口径下解析不到同名属性时该条件会被忽略（见 BillHeaderFilterSupport.apply）。
        // 注：主日期引擎键 receiptDate 对应实体属性 inDate（列 receipt_date，Java 字段名与列名方向相反）。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("receiptNo", new BillHeaderFilterSupport.Spec("receiptNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryNo", new BillHeaderFilterSupport.Spec("deliveryNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("inspectionNo", new BillHeaderFilterSupport.Spec("inspectionNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("paymentMethod", new BillHeaderFilterSupport.Spec("paymentMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryAddress", new BillHeaderFilterSupport.Spec("deliveryAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("buyerName", new BillHeaderFilterSupport.Spec("buyerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("deptId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("receiptDate", new BillHeaderFilterSupport.Spec("inDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("expectedDeliveryDate", new BillHeaderFilterSupport.Spec("expectedDeliveryDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("purchaseInbound", PurchaseInbound.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "base_net_amount",
            "base_tax_amount",
            "base_total_amount",
            "buyer_id",
            "buyer_name",
            "close_status",
            "closed_at",
            "closed_by",
            "closed_reason",
            "created_at",
            "created_by",
            "delivery_no",
            "department_id",
            "dept_name",
            "inspection_no",
            "is_closed",
            "is_lot_control",
            "line_no",
            "order_id",
            "order_no",
            "org_code",
            "receipt_date",
            "receipt_id",
            "receipt_no",
            "remark",
            "source_bill_no",
            "source_bill_type",
            "source_type",
            "status",
            "stock_name",
            "supplier_id",
            "supplier_name",
            "total_amount",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final PurchaseInboundItemMapper itemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    /** 质检不合格自动退货：退货单主表/明细写入（2026-11-07） */
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseReturnItemMapper purchaseReturnItemMapper;
    private final InventoryService inventoryService;
    private final PurchaseOrderService purchaseOrderService;
    private final MaterialService materialService;
    /** 批次号规则（2026-11-06）：自动生成 / 重复入库 / 允许修改 */
    private final com.erp.inventory.service.BatchNoRuleService batchNoRuleService;
    /** 入库单质检（2026-11-07）：审核前必须完成质检 */
    private final com.erp.qc.service.InboundQcService inboundQcService;
    /** 质检不合格自动退货（2026-11-07） */
    private final PurchaseReturnService purchaseReturnService;
    private final MaterialCostService materialCostService;
    private final SysParamValidator sysParamValidator;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SupplierService supplierService;
    private final EmployeeService employeeService;
    private final WarehouseService warehouseService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final PurchaseInvoiceService purchaseInvoiceService;

    /** 下游引用校验（统一状态机重置草稿/作废/删除前使用） */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "采购入库单"; }
    @Override protected String getApprovalBillType() { return "PURCHASE_INBOUND"; }
    @Override protected String getBillNo(PurchaseInbound b) { return b.getReceiptNo(); }
    @Override protected Object getId(PurchaseInbound b) { return b.getReceiptId(); }
    @Override protected String getStatus(PurchaseInbound b) { return b.getStatus(); }
    @Override protected void setStatus(PurchaseInbound b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(PurchaseInbound b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(PurchaseInbound b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(PurchaseInbound b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(PurchaseInbound b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(PurchaseInbound b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(PurchaseInbound b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(PurchaseInbound b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(PurchaseInbound b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(PurchaseInbound b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(PurchaseInbound b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(PurchaseInbound b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(PurchaseInbound b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(PurchaseInbound b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(PurchaseInbound bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReceiptNo());
    }

    @Override
    @Transactional
    public void saveInboundWithItems(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        boolean isNew = inbound.getReceiptId() == null;

        // 业务规则: 已生效不可修改, 如需修改需先重置草稿
        if (!isNew) {
            PurchaseInbound existing = getById(inbound.getReceiptId());
            if (existing != null) {
                if ("已生效".equals(existing.getStatus())) {
                    throw new BusinessException("已生效的入库单不可直接修改，请先重置草稿");
                }
                if ("已取消".equals(existing.getStatus())) {
                    throw new BusinessException("已取消的入库单不可修改");
                }
            }
        }

        // 业务规则: 入库单号按编码规则自动生成, 不可手动修改 (PIN-yyyyMMdd-0001)
        if (inbound.getReceiptNo() == null || inbound.getReceiptNo().isBlank()) {
            inbound.setReceiptNo(generateInboundNo());
        }

        // === 设置中心：单据号查重（sys.bill.no.duplicate.check）===
        QueryWrapper<PurchaseInbound> dupQw = new QueryWrapper<PurchaseInbound>().eq("receipt_no", inbound.getReceiptNo());
        if (!isNew) {
            dupQw.ne("receipt_id", inbound.getReceiptId());
        }
        sysParamValidator.validateBillNoDuplicate(inbound.getReceiptNo(), "采购入库单", count(dupQw) > 0);

        // === 设置中心：锁单据日期校验（sys.lock.bill.date）===
        if (!isNew) {
            PurchaseInbound existingForDate = getById(inbound.getReceiptId());
            if (existingForDate != null) {
                sysParamValidator.validateLockBillDate("采购入库单", existingForDate.getInDate(), inbound.getInDate(), existingForDate.getStatus());
            }
        }

        // 设置默认值
        if (inbound.getStatus() == null) inbound.setStatus("草稿");
        if (inbound.getInDate() == null) inbound.setInDate(LocalDate.now());
        if (inbound.getSourceType() == null) inbound.setSourceType("采购订单");
        if (inbound.getIsLotControl() == null) inbound.setIsLotControl(0);
        if (inbound.getTotalAmount() == null) inbound.setTotalAmount(BigDecimal.ZERO);
        if (inbound.getCreatedAt() == null) inbound.setCreatedAt(LocalDateTime.now());
        if (inbound.getUpdatedAt() == null) inbound.setUpdatedAt(LocalDateTime.now());
        // createdBy/updatedBy 交由 AuditMetaObjectHandler 按当前登录人自动填充（无登录态时落 system）

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<PurchaseInboundItem> validItems = new ArrayList<>();
            for (PurchaseInboundItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 批次号规则（2026-11-06）：批次管理物料空批号自动生成；不允许修改时拦截手改
        applyBatchNoRules(items);

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            inbound.setTaxCalcMethod("不计税");
        } else if (inbound.getTaxCalcMethod() == null || inbound.getTaxCalcMethod().isBlank()) {
            inbound.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(inbound.getTaxCalcMethod());
        // 金额/税额/价税合计由系统按表头「计税方式」自动计算（本单无折扣）
        recalculateInbound(inbound, items, headerTaxType);

        // === 设置中心-KGW审批：净金额 >= 阈值时升级为"待审核" ===
        sysParamValidator.applyKgwApprovalForPurchase("采购入库单",
                inbound::setStatus, s -> { /* PurchaseInbound 无 approvalStatus 字段 */ },
                inbound.getStatus(), inbound.getTotalAmount());

        if (isNew) {
            save(inbound);
        } else {
            updateById(inbound);
        }

        // 审批状态镜像: approval_status 未映射到实体, 需显式同步; 仅草稿/待审核态跟随 status
        String inboundApStatus = inbound.getStatus();
        if ("草稿".equals(inboundApStatus) || "待审核".equals(inboundApStatus)) {
            update(new UpdateWrapper<PurchaseInbound>()
                    .eq("receipt_id", inbound.getReceiptId())
                    .set("approval_status", inboundApStatus));
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", inbound.getReceiptId()));
            // 业务规则: 表体行号从1开始, 步长1
            int lineNo = 1;
            for (PurchaseInboundItem item : items) {
                item.setReceiptItemId(null);
                item.setReceiptId(inbound.getReceiptId());
                item.setLineNo(lineNo);
                // 明细仓库名称回填（前端只传仓库ID时界面与退货单也需要看到名称）
                if (item.getStockId() != null && (item.getStockName() == null || item.getStockName().isBlank())) {
                    Warehouse warehouse = warehouseService.getById(item.getStockId());
                    if (warehouse != null) item.setStockName(warehouse.getStockName());
                }
                calculateItemAmount(item, headerTaxType);
                // === 回填最新核算成本 ===
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                if (item.getUpdatedAt() == null) item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("采购入库单保存成功: {} 状态: {} 合计: {}", inbound.getReceiptNo(), inbound.getStatus(), inbound.getTotalAmount());
    }

    /**
     * 批次号规则落地（2026-11-06）：
     * ① 已保存明细的批号被改且规则不允许修改 → 拦截；
     * ② 批次管理物料批号为空 → 按规则自动生成（同单据内已用号自动跳过）；
     * ③ 重复入库参数关闭时，同一物料同一批号已在库 → 拦截。
     */
    private void applyBatchNoRules(List<PurchaseInboundItem> items) {
        if (items == null || items.isEmpty()) return;
        List<String> used = new ArrayList<>();
        for (PurchaseInboundItem it : items) {
            if (it.getBatchNo() != null && !it.getBatchNo().isBlank()) used.add(it.getBatchNo().trim());
        }
        for (int i = 0; i < items.size(); i++) {
            PurchaseInboundItem it = items.get(i);
            if (it.getMaterialId() == null) continue;
            String line = "第" + (i + 1) + "行";
            String oldNo = null;
            if (it.getReceiptItemId() != null) {
                PurchaseInboundItem old = itemMapper.selectById(it.getReceiptItemId());
                oldNo = old == null ? null : old.getBatchNo();
            }
            batchNoRuleService.checkModifyAllowed(it.getMaterialId(), oldNo, it.getBatchNo(), line);
            String ensured = batchNoRuleService.ensureBatchNo(it.getMaterialId(), it.getBatchNo(), used);
            if (ensured != null && !ensured.isBlank()) {
                it.setBatchNo(ensured);
                if (!used.contains(ensured)) used.add(ensured);
            }
        }
    }

    /**
     * 业务规则校验
     * - 入库数量必须大于0, 且不能超过源单可入库数量(订单数量 - 已入库数量)
     * - 批次管理物料必须填写批次号
     * - 序列号管理物料必须填写序列号
     * - 货位管理物料必须填写货位
     * - 保质期管理物料必须填写有效期至
     * - 入库单价取源单(采购订单)单价
     */
    private void validateInbound(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购入库单至少需要一行明细");
        }
        // 查找源采购订单
        PurchaseOrder sourceOrder = null;
        if (inbound.getSourceBillNo() != null && !inbound.getSourceBillNo().isBlank()) {
            sourceOrder = purchaseOrderMapper.selectOne(
                    new QueryWrapper<PurchaseOrder>().eq("order_no", inbound.getSourceBillNo()).last("LIMIT 1"));
        }
        for (int i = 0; i < items.size(); i++) {
            PurchaseInboundItem item = items.get(i);
            // 入库数量必须大于0
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + (i + 1) + "行入库数量必须大于0");
            }
            // 设置中心：允许单价为0校验（purchase.inbound.allow.zero.price）
            sysParamValidator.validateAllowZeroPrice("purchase.inbound.allow.zero.price", "采购入库单", item.getUnitPrice(), i + 1);
            // 根据物料配置的控制选项进行校验
            if (item.getMaterialId() != null) {
                Material material = materialService.getById(item.getMaterialId());
                if (material != null) {
                    // 批次管理物料必须填写批次号
                    if (material.getIsLotControl() != null && material.getIsLotControl() == 1) {
                        if (item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]批次管理物料必须填写批次号");
                        }
                        // 2026-11-06：重复入库参数关闭时，同物料同批号已在库 → 拦截
                        batchNoRuleService.checkRepeatInbound(item.getMaterialId(), item.getBatchNo(),
                                "第" + (i + 1) + "行[" + material.getMaterialCode() + "]");
                    }
                    // 序列号管理物料必须填写序列号
                    if (material.getIsSnControl() != null && material.getIsSnControl() == 1) {
                        if (item.getSerialNo() == null || item.getSerialNo().isBlank()) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]序列号管理物料必须填写序列号");
                        }
                    }
                    // 货位管理物料必须填写货位
                    if (material.getIsBinControl() != null && material.getIsBinControl() == 1) {
                        if (item.getBinId() == null) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]货位管理物料必须选择货位");
                        }
                    }
                    // 保质期管理物料必须填写有效期至
                    if (material.getIsExpiryControl() != null && material.getIsExpiryControl() == 1) {
                        if (item.getExpiryDate() == null) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]保质期管理物料必须填写有效期至");
                        }
                    }
                }
            }
            // 校验可入库数量并带出源单单价
            if (sourceOrder != null && item.getSourceRowNo() != null) {
                PurchaseOrderItem orderItem = purchaseOrderItemMapper.selectOne(
                        new QueryWrapper<PurchaseOrderItem>()
                                .eq("order_id", sourceOrder.getOrderId())
                                .eq("line_no", item.getSourceRowNo())
                                .last("LIMIT 1"));
                if (orderItem != null) {
                    BigDecimal orderQty = orderItem.getQuantity() != null ? orderItem.getQuantity() : BigDecimal.ZERO;
                    BigDecimal received = orderItem.getReceivedQuantity() != null ? orderItem.getReceivedQuantity() : BigDecimal.ZERO;
                    BigDecimal canInbound = orderQty.subtract(received);
                    if (item.getQuantity().compareTo(canInbound) > 0) {
                        throw new BusinessException("第" + (i + 1) + "行入库数量超过可入库数量，最大可入库: " + canInbound);
                    }
                    // 入库单价取源单(采购订单)单价
                    if (item.getUnitPrice() == null) {
                        item.setUnitPrice(orderItem.getUnitPrice());
                    }
                }
            }
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /**
     * 多单位换算（2026-11-09）：把单据单位数量折算为库存基本单位数量。
     * 数量等于整行数量时直接取 baseQuantity；质检拆出的合格/不合格数量按同一比例折算；
     * 未启用换算（baseQuantity 为空）时原样返回，与旧口径完全一致。
     */
    private static BigDecimal toStockQty(BigDecimal qty, BigDecimal totalQty, BigDecimal baseTotalQty) {
        if (qty == null) return BigDecimal.ZERO;
        if (baseTotalQty == null) return qty;
        if (totalQty == null || totalQty.compareTo(BigDecimal.ZERO) <= 0) return qty;
        if (qty.compareTo(totalQty) == 0) return baseTotalQty;
        return qty.multiply(baseTotalQty).divide(totalQty, 6, RoundingMode.HALF_UP);
    }

    /** 由表头「计税方式」解析明细计税类型：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。总开关关闭时一律不计税。 */
    private int resolveHeaderTaxType(String method) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String v = method == null ? null : method.trim();
        if (v == null || v.isBlank()) v = sysParamValidator.getDefaultTaxMethod();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    /** 金额计算（本单无折扣）：按表头「计税方式」由「单价」换算含税价/不含税价，并计算金额/税额/价税合计 */
    private void calculateItemAmount(PurchaseInboundItem item, int headerTaxType) {
        BigDecimal unitPrice = nvl(item.getUnitPrice());
        BigDecimal quantity = nvl(item.getQuantity());
        BigDecimal taxRate = nvl(item.getTaxRate());
        int taxType = headerTaxType;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTaxRate(taxRate);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        BigDecimal priceWithTax;
        BigDecimal priceWithoutTax;
        if (inclusive) {
            priceWithTax = unitPrice;
            priceWithoutTax = unitPrice.divide(taxFactor, 6, RoundingMode.HALF_UP);
        } else if (noTax) {
            priceWithTax = unitPrice;
            priceWithoutTax = unitPrice;
        } else {
            priceWithoutTax = unitPrice;
            priceWithTax = unitPrice.multiply(taxFactor).setScale(6, RoundingMode.HALF_UP);
        }
        item.setPriceWithTax(priceWithTax.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(priceWithoutTax.setScale(6, RoundingMode.HALF_UP));

        // 折前金额按「不含税」口径（本单无折扣，金额 = 不含税金额）
        BigDecimal amount = inclusive
                ? unitPrice.multiply(quantity).divide(taxFactor, SCALE, RoundingMode.HALF_UP)
                : unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（本单无折扣，统一口径见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, BigDecimal.ZERO, inclusive, noTax);
        item.setTaxAmount(taxAmount);
        item.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 汇总入库总额/税额/价税合计，并按表头汇率折算本位币 */
    private void recalculateInbound(PurchaseInbound inbound, List<PurchaseInboundItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        if (items != null) {
            for (PurchaseInboundItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
                totalNet = totalNet.add(nvl(item.getNetAmount()));
            }
        }
        inbound.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        inbound.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        inbound.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
        BigDecimal rate = inbound.getExchangeRate() != null ? inbound.getExchangeRate() : BigDecimal.ONE;
        inbound.setBaseTotalAmount(inbound.getTotalAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        inbound.setBaseTaxAmount(inbound.getTaxAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        inbound.setBaseNetAmount(inbound.getNetAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<PurchaseInbound> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<PurchaseInbound> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseInbound.class);
        // 关键词：入库单号 + 供应商名称 多列匹配，强制成组（前端提示"输入入库单号/供应商名称搜索"，原实现只匹配单号）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no", "supplier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 receipt_date）；供应商（本页真实列 supplier_id）；员工=采购员（本页真实列 buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "purchase_inbound", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_inbound"));
        Page<PurchaseInbound> page = new Page<>(current, size);
        Page<PurchaseInbound> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<PurchaseInbound> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<PurchaseInbound> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseInbound.class);
        // 关键词：入库单号 + 供应商名称 成组匹配（与列表页搜索提示一致，口径同上一重载）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no", "supplier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（receipt_date）；供应商（supplier_id）；员工=采购员（buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "purchaseInbound", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        CustomFieldSearchHelper.apply(wrapper, "purchase_inbound", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_inbound"));
        Page<PurchaseInbound> page = new Page<>(current, size);
        Page<PurchaseInbound> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseInbound getDetail(Long id) {
        PurchaseInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        List<PurchaseInboundItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", id).orderByAsc("line_no"));
        inbound.setItems(items);
        return inbound;
    }

    /** 提交审核：状态校验/严格校验/审批开关/审批任务统一由统一状态机基类执行 */
    @Override
    @Transactional
    public void submit(Long id, String submitter) {
        PurchaseInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        super.submitForApproval(inbound, submitter != null && !submitter.isBlank()
                ? submitter : BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（明细/供应商/采购员/仓库/物料/单位/数量 + 来源/批次/序列号/货位/保质期/可入库数量） */
    @Override
    protected void validateStrictSubmit(PurchaseInbound inbound) {
        Long id = inbound.getReceiptId();
        List<PurchaseInboundItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购入库单没有明细，请至少添加一行物料后再提交");
        }

        if (inbound.getSupplierId() == null) {
            throw new BusinessException("供应商不能为空，请选择供应商");
        }
        if (supplierService.getById(inbound.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + inbound.getSupplierId() + ")，请重新选择供应商");
        }
        if (inbound.getBuyerId() != null && employeeService.getById(inbound.getBuyerId()) == null) {
            throw new BusinessException("关联采购员不存在(buyerId=" + inbound.getBuyerId() + ")，请重新选择采购员");
        }
        // 2026-11-07：表头仓库改为统一选择器，不再要求必填；入库以明细行仓库为准
        for (int i = 0; i < items.size(); i++) {
            PurchaseInboundItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行关联物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            String matInfo = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "") + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
            if (item.getStockId() == null) {
                throw new BusinessException("第" + lineNo + "行[" + matInfo + "]未选择仓库，请选择仓库");
            }
            if (warehouseService.getById(item.getStockId()) == null) {
                throw new BusinessException("第" + lineNo + "行[" + matInfo + "]所选仓库不存在(stockId=" + item.getStockId() + ")，请重新选择");
            }
            if (item.getUnitId() != null && unitOfMeasureService.getById(item.getUnitId()) == null) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]关联单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]入库数量必须大于0");
            }
        }

        // 提交=严格校验：来源单号（设置中心开关）+ 明细完整业务校验（批次/序列号/货位/保质期/可入库数量/单价）
        sysParamValidator.validateRequireSource("purchase.inbound.require.source", "采购入库单", inbound.getSourceBillNo());
        validateInbound(inbound, items);
    }

    // ================= 审核入口（统一状态机 + 审批引擎防绕过） =================

    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("PURCHASE_INBOUND", (Long) id) != null) {
            throw new BusinessException("采购入库单已提交审批流程，请由指定审批人在审批中心处理");
        }
        // 审核前置条件：开启质检的单据必须完成质检
        inboundQcService.assertInspectionComplete(InboundQcService.BILL_PURCHASE, (Long) id);
        // 审核前置条件：明细必须有物料与仓库，避免无仓库行直接入库
        List<PurchaseInboundItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", (Long) id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购入库单没有明细，无法审核");
        }
        for (PurchaseInboundItem item : items) {
            if (item.getMaterialId() == null) {
                throw new BusinessException("采购入库单第" + item.getLineNo() + "行缺少物料，无法入库");
            }
            if (item.getStockId() == null) {
                throw new BusinessException("第" + item.getLineNo() + "行[" + itemLabel(item) + "]未选择仓库，请选择仓库");
            }
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：刷新成本、按质检结果拆库存、回写采购订单已入库/勾稽数量、生成质检退货草稿 */
    @Override
    public void onApproved(PurchaseInbound inbound) {
        applyInboundImpact(inbound, true);
    }

    /** 重置草稿影响：与审核严格镜像（扣回库存、减少已入库数量、清理质检自动退货草稿） */
    @Override
    public void onReverseApproved(PurchaseInbound inbound) {
        applyInboundImpact(inbound, false);
        cleanupQcAutoReturnDrafts(inbound);
    }

    /** 作废影响：与重置草稿严格镜像 */
    @Override
    public void onVoided(PurchaseInbound inbound) {
        applyInboundImpact(inbound, false);
        cleanupQcAutoReturnDrafts(inbound);
    }

    /** 反作废影响：恢复生效影响 */
    @Override
    public void onUnvoided(PurchaseInbound inbound) {
        applyInboundImpact(inbound, true);
    }

    /**
     * 入库影响（一正一反严格镜像）：
     * increase=true 生效（按质检结果入库存、回写采购订单已入库/勾稽数量、生成质检退货草稿）；
     * increase=false 冲回（扣回库存、减少采购订单已入库数量）。
     */
    private void applyInboundImpact(PurchaseInbound inbound, boolean increase) {
        String operator = inbound.getApprovedBy() != null && !inbound.getApprovedBy().isBlank()
                ? inbound.getApprovedBy() : "系统";
        List<PurchaseInboundItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", inbound.getReceiptId()).orderByAsc("line_no"));
        if (items == null) return;
        if (increase) {
            // 成本链路（2026-11-26）：冻结本次实际入库成本，供反审按原成本红字冲回；
            // 单价为 0 时回落最新核算成本，避免 0 成本留痕。
            for (PurchaseInboundItem item : items) {
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

        List<PurchaseReturnItem> autoReturnItems = new ArrayList<>();
        Long autoReturnStockId = null;
        String autoReturnStockName = null;
        Long defaultScrapId = null;
        boolean qcOn = inbound.getQcEnabled() != null && inbound.getQcEnabled() == 1;
        for (PurchaseInboundItem item : items) {
            Long materialId = item.getMaterialId();
            BigDecimal qty = nvl(item.getQuantity());
            BigDecimal unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal qualified = qcOn ? nvl(item.getQualifiedQuantity()) : qty;
            BigDecimal rejected = qcOn ? nvl(item.getRejectedQuantity()) : BigDecimal.ZERO;
            String handling = qcOn ? normalizeHandling(item.getHandlingType(), rejected)
                    : InboundQcService.HANDLE_RELEASE;

            BigDecimal enterQty;
            BigDecimal scrapQty = BigDecimal.ZERO;
            Long scrapWarehouseId = null;
            if (InboundQcService.HANDLE_RETURN.equals(handling)) {
                enterQty = qualified;
            } else if (InboundQcService.HANDLE_SCRAP.equals(handling)) {
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
                enterQty = qty;
            }

            // 多单位换算（2026-11-09）：库存记账只认基本单位数量（baseQuantity）；
            // 质检拆出的合格/不合格数量按整行同一比例折算，未启用换算时与旧口径完全一致。
            BigDecimal stockEnterQty = toStockQty(enterQty, qty, item.effectiveBaseQuantity());
            BigDecimal stockScrapQty = toStockQty(scrapQty, qty, item.effectiveBaseQuantity());

            if (increase) {
                if (enterQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.addStock(
                            item.getStockId(), item.getBinId(), materialId, item.getBatchNo(),
                            stockEnterQty, unitCost,
                            inbound.getReceiptNo(), "PURCHASE_IN",
                            "PurchaseInbound", inbound.getReceiptNo(), operator);
                }
                if (scrapQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.addStock(
                            scrapWarehouseId, null, materialId, item.getBatchNo(),
                            stockScrapQty, unitCost,
                            inbound.getReceiptNo(), "PURCHASE_IN_SCRAP",
                            "PurchaseInbound", inbound.getReceiptNo(), operator);
                }
                if (InboundQcService.HANDLE_RETURN.equals(handling) && rejected.compareTo(BigDecimal.ZERO) > 0) {
                    autoReturnItems.add(buildQcReturnItem(inbound, item, rejected));
                    if (autoReturnStockId == null) {
                        autoReturnStockId = item.getStockId();
                        autoReturnStockName = item.getStockName();
                    }
                }
            } else {
                if (materialId != null && item.getStockId() != null && enterQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.deductStockAtCost(
                            item.getStockId(), item.getBinId(), materialId, item.getBatchNo(),
                            stockEnterQty, nvl(item.getAvgCost()),
                            inbound.getReceiptNo(), "PURCHASE_IN_REVERSE",
                            "PurchaseInbound", inbound.getReceiptNo(), operator);
                }
                if (materialId != null && scrapWarehouseId != null && scrapQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.deductStockAtCost(
                            scrapWarehouseId, null, materialId, item.getBatchNo(),
                            stockScrapQty, nvl(item.getAvgCost()),
                            inbound.getReceiptNo(), "PURCHASE_IN_SCRAP_REVERSE",
                            "PurchaseInbound", inbound.getReceiptNo(), operator);
                }
            }

            // 回写采购订单已入库数量（放行/报废按全量，自动退货按合格数量）
            BigDecimal writebackQty = InboundQcService.HANDLE_RETURN.equals(handling) ? qualified : qty;
            if (writebackQty.compareTo(BigDecimal.ZERO) > 0) {
                writebackOrderReceivedQty(sourceNo(inbound.getSourceBillNo(), item.getSourceBillNo()),
                        item.getSourceRowNo(), writebackQty, increase);
                if (increase && item.getSourceId() != null && item.getSourceItemId() != null) {
                    purchaseOrderService.updateTurnedQuantity(
                            item.getSourceId(), item.getSourceItemId(), writebackQty, "input");
                }
            }
        }

        if (increase && !autoReturnItems.isEmpty()) {
            createQcAutoReturnDraft(inbound, autoReturnItems, autoReturnStockId, autoReturnStockName);
        }
        log.info("采购入库单{}影响: {} 操作人: {}", increase ? "生效" : "冲回", inbound.getReceiptNo(), operator);
    }

    /** 清理质检不合格自动生成的采购退货单草稿（明细 + 主表） */
    private void cleanupQcAutoReturnDrafts(PurchaseInbound inbound) {
        List<PurchaseReturn> autoReturns = purchaseReturnMapper.selectList(new QueryWrapper<PurchaseReturn>()
                .eq("source_bill_type", "采购入库单")
                .eq("source_bill_no", inbound.getReceiptNo())
                .eq("return_type", "质检退货"));
        for (PurchaseReturn ret : autoReturns) {
            purchaseReturnItemMapper.delete(new QueryWrapper<PurchaseReturnItem>()
                    .eq("return_id", ret.getReturnId()));
            purchaseReturnMapper.deleteById(ret.getReturnId());
            log.info("同步清理质检自动退货草稿: 入库单={} 退货单={}", inbound.getReceiptNo(), ret.getReturnNo());
        }
    }

    /** 明细行展示用「编码 名称」 */
    private static String itemLabel(PurchaseInboundItem item) {
        String code = item.getMaterialCode() != null ? item.getMaterialCode() : "";
        String name = item.getMaterialName() != null ? item.getMaterialName() : "";
        return (code + " " + name).trim();
    }

    /** 不合格处理方式归一：无不合格数量按放行处理 */
    private static String normalizeHandling(String handling, BigDecimal rejected) {
        if (rejected == null || rejected.compareTo(BigDecimal.ZERO) <= 0) {
            return InboundQcService.HANDLE_RELEASE;
        }
        if (handling == null || handling.isBlank()) {
            return InboundQcService.HANDLE_RELEASE;
        }
        return handling;
    }

    /** 构造质检自动退货单的一行（数量 = 不合格数量） */
    private PurchaseReturnItem buildQcReturnItem(PurchaseInbound inbound, PurchaseInboundItem item, BigDecimal rejected) {
        PurchaseReturnItem ri = new PurchaseReturnItem();
        ri.setMaterialId(item.getMaterialId());
        ri.setMaterialCode(item.getMaterialCode());
        ri.setMaterialName(item.getMaterialName());
        ri.setModel(item.getModel());
        ri.setUnitId(item.getUnitId());
        ri.setQuantity(rejected);
        ri.setUnitPrice(item.getUnitPrice());
        ri.setTaxRate(item.getTaxRate());
        ri.setBatchNo(item.getBatchNo());
        ri.setBinId(item.getBinId());
        ri.setBinName(item.getBinName());
        ri.setSourceType("采购入库单");
        ri.setSourceBillType("PurchaseInbound");
        ri.setSourceBillNo(inbound.getReceiptNo());
        ri.setSourceDocId(inbound.getReceiptId());
        ri.setSourceRowNo(item.getLineNo());
        ri.setSourceId(item.getSourceId());
        ri.setSourceItemId(item.getSourceItemId());
        ri.setRemark("质检不合格自动退货");
        return ri;
    }

    /** 质检不合格自动生成采购退货单草稿（不产生库存变动，仅单据与财务冲减） */
    private void createQcAutoReturnDraft(PurchaseInbound inbound, List<PurchaseReturnItem> returnItems,
                                         Long stockId, String stockName) {
        PurchaseReturn ret = new PurchaseReturn();
        ret.setReturnDate(LocalDate.now());
        ret.setSupplierId(inbound.getSupplierId());
        ret.setSupplierName(inbound.getSupplierName());
        ret.setStockId(stockId);
        ret.setStockName(stockName);
        ret.setSourceType("采购入库单");
        ret.setSourceBillNo(inbound.getReceiptNo());
        ret.setReturnType("质检退货");
        ret.setReturnReason("入库质检不合格自动生成");
        ret.setStatus("草稿");
        ret.setStockEffect(0);
        ret.setRemark("由采购入库单 " + inbound.getReceiptNo() + " 质检不合格自动生成");
        purchaseReturnService.saveReturnWithItems(ret, returnItems);
        log.info("质检不合格自动生成采购退货单草稿: 入库单={} 退货单={} 行数={}",
                inbound.getReceiptNo(), ret.getReturnNo(), returnItems.size());
    }

    /** 本单据特有守卫：已生成采购发票 / 质检自动退货单非草稿 时不允许重置草稿或作废 */
    private void assertReversible(PurchaseInbound inbound, String opName) {
        Long invoiceCount = purchaseInvoiceMapper.selectCount(
                new QueryWrapper<PurchaseInvoice>()
                        .eq("source_bill_type", "采购入库单")
                        .eq("source_bill_no", inbound.getReceiptNo())
                        .ne("status", "已取消"));
        if (invoiceCount != null && invoiceCount > 0) {
            throw new BusinessException("入库单已生成采购发票，不可" + opName);
        }
        List<PurchaseReturn> autoReturns = purchaseReturnMapper.selectList(new QueryWrapper<PurchaseReturn>()
                .eq("source_bill_type", "采购入库单")
                .eq("source_bill_no", inbound.getReceiptNo())
                .eq("return_type", "质检退货"));
        for (PurchaseReturn ret : autoReturns) {
            if (!"草稿".equals(ret.getStatus())) {
                throw new BusinessException("入库单质检自动生成的采购退货单 " + ret.getReturnNo()
                        + " 已" + ret.getStatus() + "，请先重置/作废该退货单后再" + opName + "本入库单");
            }
        }
    }

    /**
     * 重置草稿: 已生效 → 草稿
     * - 先过本单据守卫（已生成采购发票 / 质检自动退货单非草稿）
     * - 状态校验 + 下游校验 + 冲回钩子（onReverseApproved：扣回库存、减少已入库数量、清理自动退货草稿）由基类执行
     * - 显式置空 approved_by/approved_at（MyBatis-Plus 忽略 null 更新）
     */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        PurchaseInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        assertReversible(inbound, "重置草稿");
        super.reverseApprove(id, operator);
        UpdateWrapper<PurchaseInbound> uw = new UpdateWrapper<>();
        uw.eq("receipt_id", id)
                .set("approved_by", null)
                .set("approved_at", null)
                .set("updated_at", LocalDateTime.now())
                .set("updated_by", operator);
        update(uw);
        log.info("采购入库单重置草稿: {} 操作人: {}", inbound.getReceiptNo(), operator);
    }

    /** 来源单号：表头优先，缺失时取明细行来源单号（手动选来源单只写明细的场景） */
    private static String sourceNo(String headNo, String itemNo) {
        return (headNo != null && !headNo.isBlank()) ? headNo : itemNo;
    }

    /** 回写采购订单已入库数量 (increase=true 增加, false 减少) */
    private void writebackOrderReceivedQty(String orderNo, Integer orderLineNo, BigDecimal qty, boolean increase) {
        if (orderNo == null || orderNo.isBlank() || orderLineNo == null) return;
        PurchaseOrder order = purchaseOrderMapper.selectOne(
                new QueryWrapper<PurchaseOrder>().eq("order_no", orderNo).last("LIMIT 1"));
        if (order == null) return;
        PurchaseOrderItem orderItem = purchaseOrderItemMapper.selectOne(
                new QueryWrapper<PurchaseOrderItem>()
                        .eq("order_id", order.getOrderId())
                        .eq("line_no", orderLineNo)
                        .last("LIMIT 1"));
        if (orderItem == null) return;
        BigDecimal current = orderItem.getReceivedQuantity() != null ? orderItem.getReceivedQuantity() : BigDecimal.ZERO;
        BigDecimal newReceived = increase ? current.add(qty) : current.subtract(qty);
        if (newReceived.compareTo(BigDecimal.ZERO) < 0) newReceived = BigDecimal.ZERO;
        orderItem.setReceivedQuantity(newReceived);
        purchaseOrderItemMapper.updateById(orderItem);
        log.info("回写采购订单已入库数量: 订单={} 行={} {}后累计={}", orderNo, orderLineNo, increase ? "增加" : "减少", newReceived);
    }

    /** 取消: 草稿 → 已取消 */
    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        if (!"草稿".equals(inbound.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + inbound.getStatus());
        }
        inbound.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            inbound.setRemark((inbound.getRemark() != null ? inbound.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(inbound);
        log.info("采购入库单取消: {} 原因: {}", inbound.getReceiptNo(), reason);
    }

    /** 入库单号生成: 文档格式 PIN-yyyyMMdd-0001 (带连字符, 4位流水号) */
    @Override
    public String generateInboundNo() {
        String docType = "PURCHASE_INBOUND";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "PIN-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
            QueryWrapper<PurchaseInbound> wrapper = new QueryWrapper<>();
            wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
            PurchaseInbound last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReceiptNo() != null) {
                try {
                    seq = Integer.parseInt(last.getReceiptNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    /** 结案：统一状态机（已生效 + 未结案校验，写 close_status/closed_*） */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案：统一状态机 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        PurchaseInbound bill = getById(id);
        if (bill == null) throw new BusinessException("采购入库单不存在");
        // 仅已生效作废时才需要冲回库存/已入库数量，先过「已生成发票/自动退货单」守卫
        if (BillStatusUtils.isApprovedClass(bill.getStatus())) {
            assertReversible(bill, "作废");
        }
        // 重复作废/结案/下游校验 + 作废前状态记录 + 作废审计字段 + onVoided 冲回由基类执行
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    @Override
    @Transactional
    public void deleteBill(Long id) {
        // 草稿校验 + 下游校验 + 整单级联删除由基类执行
        super.deleteBill(id);
    }

    /**
     * 删除前守卫：与「重置草稿 / 作废」同口径。
     * 删除已生效入库单会冲回库存并清理质检自动退货草稿，因此已生成采购发票或自动退货单非草稿时必须拦截。
     */
    @Override
    protected void validateBeforeDelete(PurchaseInbound bill) {
        if (!BillStatusUtils.isApprovedClass(bill.getStatus())) return;
        assertReversible(bill, "删除");
    }

    /** 下推到采购发票: 已生效的入库单 → 生成发票(草稿) */
    @Override
    @Transactional
    public PurchaseInvoice pushToInvoice(Long id, String operator) {
        PurchaseInbound inbound = getDetail(id);
        if (inbound == null) {
            throw new BusinessException("采购入库单不存在");
        }
        if (!"已生效".equals(inbound.getStatus())) {
            throw new BusinessException("只有已生效的采购入库单才能下推");
        }
        List<PurchaseInboundItem> inboundItems = inbound.getItems();
        if (inboundItems == null || inboundItems.isEmpty()) {
            throw new BusinessException("采购入库单无明细，无法下推");
        }

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setInvoiceNo(purchaseInvoiceService.generateInvoiceNo());
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setSupplierId(inbound.getSupplierId());
        invoice.setSupplierName(inbound.getSupplierName());
        invoice.setAmount(inbound.getTotalAmount() != null ? inbound.getTotalAmount() : BigDecimal.ZERO);
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setTaxTotalAmount(BigDecimal.ZERO);
        invoice.setSourceType("采购入库单");
        invoice.setSourceBillNo(inbound.getReceiptNo());
        invoice.setRemark("由采购入库单 " + inbound.getReceiptNo() + " 下推生成");
        invoice.setStatus("草稿");
        // 下推统一口径（2026-11-28）：下推只生成草稿，审核动作由用户单独完成
        invoice.setApprovalStatus("草稿");
        // 默认普通发票, 避免下推时强制校验供应商税号 (用户可在界面切换为专票并补全税号)
        invoice.setInvoiceType("普通发票");

        List<PurchaseInvoiceItem> invoiceItems = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseInboundItem item : inboundItems) {
            PurchaseInvoiceItem ii = new PurchaseInvoiceItem();
            ii.setLineNo(lineNo++);
            ii.setMaterialId(item.getMaterialId());
            ii.setMaterialCode(item.getMaterialCode());
            ii.setMaterialName(item.getMaterialName());
            ii.setModel(item.getModel());
            ii.setUnitId(item.getUnitId());
            ii.setQuantity(item.getQuantity());
            ii.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
            ii.setTaxRate(BigDecimal.ZERO);
            ii.setAmount(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
            ii.setTaxAmount(BigDecimal.ZERO);
            ii.setSourceBillNo(inbound.getReceiptNo());
            ii.setSourceRowNo(item.getLineNo());
            ii.setSourceType("采购入库单");
            invoiceItems.add(ii);
        }

        purchaseInvoiceService.saveInvoiceWithItems(invoice, invoiceItems);
        return invoice;
    }

}
