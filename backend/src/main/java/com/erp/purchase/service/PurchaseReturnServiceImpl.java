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
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.config.service.SysConfigService;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.service.InventoryService;
import com.erp.masterdata.entity.Material;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.EmployeeService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.entity.PurchaseReturn;
import com.erp.purchase.entity.PurchaseReturnItem;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.purchase.mapper.PurchaseInvoiceMapper;
import com.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.erp.purchase.mapper.PurchaseReturnMapper;
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
 * 采购退货单服务实现
 * 文档3.4 单据状态流转: 草稿 → 待审核 → 已生效
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class PurchaseReturnServiceImpl extends AbstractBillService<PurchaseReturnMapper, PurchaseReturn> implements PurchaseReturnService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 按名称筛但列是外键的场景（供应商/采购员/部门）：Spec.column 写外键「属性名」，
        // 不写裸列名 —— 属性名注册口径下解析不到同名属性时该条件会被忽略（见 BillHeaderFilterSupport.apply）。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("returnNo", new BillHeaderFilterSupport.Spec("returnNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("returnType", new BillHeaderFilterSupport.Spec("returnType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("returnReason", new BillHeaderFilterSupport.Spec("returnReason", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryAddress", new BillHeaderFilterSupport.Spec("deliveryAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("buyerName", new BillHeaderFilterSupport.Spec("buyerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("deptId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("returnDate", new BillHeaderFilterSupport.Spec("returnDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("purchaseReturn", PurchaseReturn.class, f);
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
            "department_id",
            "dept_name",
            "is_closed",
            "is_lot_control",
            "is_red_invoice",
            "line_no",
            "org_code",
            "receipt_id",
            "receipt_no",
            "remark",
            "return_date",
            "return_id",
            "return_no",
            "return_reason",
            "return_type",
            "source_bill_no",
            "source_bill_type",
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

    private final PurchaseReturnItemMapper itemMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final InventoryService inventoryService;
    private final SysParamValidator sysParamValidator;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SupplierService supplierService;
    private final EmployeeService employeeService;
    private final WarehouseService warehouseService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final SysConfigService sysConfigService;

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
    @Override protected String getBillTypeLabel() { return "采购退货单"; }
    @Override protected String getApprovalBillType() { return "PURCHASE_RETURN"; }
    @Override protected String getBillNo(PurchaseReturn b) { return b.getReturnNo(); }
    @Override protected Object getId(PurchaseReturn b) { return b.getReturnId(); }
    @Override protected String getStatus(PurchaseReturn b) { return b.getStatus(); }
    @Override protected void setStatus(PurchaseReturn b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(PurchaseReturn b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(PurchaseReturn b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(PurchaseReturn b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(PurchaseReturn b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(PurchaseReturn b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(PurchaseReturn b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(PurchaseReturn b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(PurchaseReturn b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(PurchaseReturn b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(PurchaseReturn b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(PurchaseReturn b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(PurchaseReturn b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(PurchaseReturn b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(PurchaseReturn bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReturnNo());
    }

    @Override
    @Transactional
    public void saveReturnWithItems(PurchaseReturn returnBill, List<PurchaseReturnItem> items) {
        boolean isNew = returnBill.getReturnId() == null;

        // 业务规则: 已生效不可修改, 如需修改需先重置草稿
        if (!isNew) {
            PurchaseReturn existing = getById(returnBill.getReturnId());
            if (existing != null) {
                if ("已生效".equals(existing.getStatus())) {
                    throw new BusinessException("已生效的退货单不可直接修改，请先重置草稿");
                }
                if ("已取消".equals(existing.getStatus())) {
                    throw new BusinessException("已取消的退货单不可修改");
                }
            }
        }

        // 业务规则: 退货单号按编码规则自动生成 (PRT-yyyyMMdd-0001)；更新时不重新取号
        if (isNew && (returnBill.getReturnNo() == null || returnBill.getReturnNo().isBlank())) {
            returnBill.setReturnNo(generateReturnNo());
        }

        // === 设置中心：单据号查重（sys.bill.no.duplicate.check）===
        QueryWrapper<PurchaseReturn> dupQw = new QueryWrapper<PurchaseReturn>().eq("return_no", returnBill.getReturnNo());
        if (!isNew) {
            dupQw.ne("return_id", returnBill.getReturnId());
        }
        sysParamValidator.validateBillNoDuplicate(returnBill.getReturnNo(), "采购退货单", count(dupQw) > 0);

        // === 设置中心：锁单据日期校验（sys.lock.bill.date）===
        if (!isNew) {
            PurchaseReturn existingForDate = getById(returnBill.getReturnId());
            if (existingForDate != null) {
                sysParamValidator.validateLockBillDate("采购退货单", existingForDate.getReturnDate(), returnBill.getReturnDate(), existingForDate.getStatus());
            }
        }

        // 设置默认值
        if (returnBill.getStatus() == null) returnBill.setStatus("草稿");
        if (returnBill.getReturnDate() == null) returnBill.setReturnDate(LocalDate.now());
        if (returnBill.getSourceType() == null) returnBill.setSourceType("采购入库单");
        if (returnBill.getReturnType() == null) returnBill.setReturnType("质量退货");
        if (returnBill.getIsLotControl() == null) returnBill.setIsLotControl(0);
        if (returnBill.getTotalAmount() == null) returnBill.setTotalAmount(BigDecimal.ZERO);
        if (returnBill.getCreatedAt() == null) returnBill.setCreatedAt(LocalDateTime.now());
        if (returnBill.getUpdatedAt() == null) returnBill.setUpdatedAt(LocalDateTime.now());
        // createdBy/updatedBy 交由 AuditMetaObjectHandler 按当前登录人自动填充（无登录态时落 system）

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<PurchaseReturnItem> validItems = new ArrayList<>();
            for (PurchaseReturnItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            returnBill.setTaxCalcMethod("不计税");
        } else if (returnBill.getTaxCalcMethod() == null || returnBill.getTaxCalcMethod().isBlank()) {
            returnBill.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(returnBill.getTaxCalcMethod());
        // 金额/税额/价税合计由系统按行级「计税方式」自动计算（本单无折扣）
        recalculateReturn(returnBill, items, headerTaxType);

        if (isNew) {
            save(returnBill);
        } else {
            updateById(returnBill);
        }

        // 审批状态镜像: approval_status 未映射到实体, 需显式同步; 仅草稿/待审核态跟随 status
        String returnApStatus = returnBill.getStatus();
        if ("草稿".equals(returnApStatus) || "待审核".equals(returnApStatus)) {
            update(new UpdateWrapper<PurchaseReturn>()
                    .eq("return_id", returnBill.getReturnId())
                    .set("approval_status", returnApStatus));
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<PurchaseReturnItem>().eq("return_id", returnBill.getReturnId()));
            // 业务规则: 表体行号从1开始, 步长1
            int lineNo = 1;
            for (PurchaseReturnItem item : items) {
                item.setReturnItemId(null);
                item.setReturnId(returnBill.getReturnId());
                item.setLineNo(lineNo);
                calculateItemAmount(item, headerTaxType);
                if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                if (item.getUpdatedAt() == null) item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("采购退货单保存成功: {} 状态: {} 合计: {}", returnBill.getReturnNo(), returnBill.getStatus(), returnBill.getTotalAmount());
    }

    /**
     * 业务规则校验
     * - 退货数量必须大于0, 且不能超过源单(采购入库单)可退数量
     * - 退货原因必须填写
     * - 批次管理物料必须填写批次号
     * - 退货单价取源单(采购入库单)单价
     */
    private void validateReturn(PurchaseReturn returnBill, List<PurchaseReturnItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购退货单至少需要一行明细");
        }
        // 退货原因必须填写
        if (returnBill.getReturnReason() == null || returnBill.getReturnReason().isBlank()) {
            throw new BusinessException("退货原因必须填写");
        }
        // 查找源采购入库单
        PurchaseInbound sourceInbound = null;
        if (returnBill.getSourceBillNo() != null && !returnBill.getSourceBillNo().isBlank()) {
            sourceInbound = purchaseInboundMapper.selectOne(
                    new QueryWrapper<PurchaseInbound>().eq("receipt_no", returnBill.getSourceBillNo()).last("LIMIT 1"));
        }
        for (int i = 0; i < items.size(); i++) {
            PurchaseReturnItem item = items.get(i);
            // 退货数量必须大于0
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + (i + 1) + "行退货数量必须大于0");
            }
            // 批次管理物料必须填写批次号
            if (returnBill.getIsLotControl() != null && returnBill.getIsLotControl() == 1) {
                if (item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                    throw new BusinessException("第" + (i + 1) + "行批次管理物料必须填写批次号");
                }
            }
            // 校验可退数量并带出源单单价
            if (sourceInbound != null && item.getSourceRowNo() != null) {
                PurchaseInboundItem receiptItem = purchaseInboundItemMapper.selectOne(
                        new QueryWrapper<PurchaseInboundItem>()
                                .eq("receipt_id", sourceInbound.getReceiptId())
                                .eq("line_no", item.getSourceRowNo())
                                .last("LIMIT 1"));
                if (receiptItem != null) {
                    BigDecimal receiptQty = receiptItem.getQuantity() != null ? receiptItem.getQuantity() : BigDecimal.ZERO;
                    BigDecimal returned = receiptItem.getReturnedQuantity() != null ? receiptItem.getReturnedQuantity() : BigDecimal.ZERO;
                    BigDecimal canReturn = receiptQty.subtract(returned);
                    if (item.getQuantity().compareTo(canReturn) > 0) {
                        throw new BusinessException("第" + (i + 1) + "行退货数量超过可退数量，最大可退: " + canReturn);
                    }
                    // 退货单价取源单(采购入库单)单价
                    if (item.getUnitPrice() == null) {
                        item.setUnitPrice(receiptItem.getUnitPrice());
                    }
                }
            }
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 由表头「计税方式」解析明细计税类型：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。总开关关闭时一律不计税。 */
    private int resolveHeaderTaxType(String method) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String v = method == null ? null : method.trim();
        if (v == null || v.isBlank()) v = sysParamValidator.getDefaultTaxMethod();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    /** 逐行计算（行级口径）：优先按行级 taxCalcMethod（含税价/价内税→1、不计税→3、其它→2），未传时回退表头口径 */
    private void calculateItemAmount(PurchaseReturnItem item, int headerTaxType) {
        BigDecimal unitPrice = nvl(item.getUnitPrice());
        BigDecimal quantity = nvl(item.getQuantity());
        BigDecimal taxRate = nvl(item.getTaxRate());
        BigDecimal discountRate = nvl(item.getDiscountRate());
        int taxType;
        String m = item.getTaxCalcMethod();
        if (m == null || m.isBlank()) {
            taxType = headerTaxType;
        } else if ("不计税".equals(m.trim())) {
            taxType = TAX_TYPE_NO_TAX;
        } else if ("含税价".equals(m.trim()) || "价内税".equals(m.trim())) {
            taxType = TAX_TYPE_INCLUSIVE;
        } else {
            taxType = TAX_TYPE_EXCLUSIVE;
        }
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTaxRate(taxRate);
        item.setDiscountRate(discountRate);

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

        // 折前金额按「不含税」口径参与折扣与税额计算
        BigDecimal originalAmount = inclusive
                ? unitPrice.multiply(quantity).divide(taxFactor, SCALE, RoundingMode.HALF_UP)
                : unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discountAmount = originalAmount.multiply(discountRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        item.setDiscountAmount(discountAmount);
        BigDecimal amount = originalAmount.subtract(discountAmount).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, discountRate, inclusive, noTax);
        item.setTaxAmount(taxAmount);
        item.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 汇总退货总额/税额/价税合计，并按表头汇率折算本位币 */
    private void recalculateReturn(PurchaseReturn returnBill, List<PurchaseReturnItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        if (items != null) {
            for (PurchaseReturnItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()).add(nvl(item.getDiscountAmount())));
                totalDiscount = totalDiscount.add(nvl(item.getDiscountAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
                totalNet = totalNet.add(nvl(item.getNetAmount()));
            }
        }
        returnBill.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        returnBill.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        returnBill.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        returnBill.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
        BigDecimal rate = returnBill.getExchangeRate() != null ? returnBill.getExchangeRate() : BigDecimal.ONE;
        returnBill.setBaseTotalAmount(returnBill.getTotalAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        returnBill.setBaseTaxAmount(returnBill.getTaxAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        returnBill.setBaseNetAmount(returnBill.getNetAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<PurchaseReturn> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<PurchaseReturn> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseReturn.class);
        // 关键词：退货单号 + 供应商名称 多列匹配，强制成组（前端提示"输入退货单号/供应商名称搜索"，原实现只匹配单号）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "return_no", "supplier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 return_date）；供应商（本页真实列 supplier_id）；员工=采购员（本页真实列 buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "return_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "purchase_return", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_return"));
        Page<PurchaseReturn> page = new Page<>(current, size);
        Page<PurchaseReturn> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<PurchaseReturn> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<PurchaseReturn> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseReturn.class);
        // 关键词：退货单号 + 供应商名称 成组匹配（与列表页搜索提示一致，口径同上一重载）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "return_no", "supplier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（return_date）；供应商（supplier_id）；员工=采购员（buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "return_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "purchaseReturn", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        CustomFieldSearchHelper.apply(wrapper, "purchase_return", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_return"));
        Page<PurchaseReturn> page = new Page<>(current, size);
        Page<PurchaseReturn> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseReturn getDetail(Long id) {
        PurchaseReturn returnBill = getById(id);
        if (returnBill == null) throw new BusinessException("采购退货单不存在");
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseReturnItem>().eq("return_id", id).orderByAsc("line_no"));
        returnBill.setItems(items);
        return returnBill;
    }

    /** 提交审核：状态校验/严格校验/审批开关/审批任务统一由统一状态机基类执行 */
    @Override
    @Transactional
    public void submit(Long id, String submitter) {
        PurchaseReturn returnBill = getById(id);
        if (returnBill == null) throw new BusinessException("采购退货单不存在");
        super.submitForApproval(returnBill, submitter != null && !submitter.isBlank()
                ? submitter : BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（明细/供应商/采购员/仓库/库存/来源单号/可退数量/退货原因） */
    @Override
    protected void validateStrictSubmit(PurchaseReturn returnBill) {
        Long id = returnBill.getReturnId();
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseReturnItem>().eq("return_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购退货单没有明细，请至少添加一行物料后再提交");
        }

        if (returnBill.getSupplierId() == null) {
            throw new BusinessException("供应商不能为空，请选择供应商");
        }
        if (supplierService.getById(returnBill.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + returnBill.getSupplierId() + ")，请重新选择供应商");
        }
        if (returnBill.getBuyerId() != null && employeeService.getById(returnBill.getBuyerId()) == null) {
            throw new BusinessException("关联采购员不存在(buyerId=" + returnBill.getBuyerId() + ")，请重新选择采购员");
        }
        if (returnBill.getStockId() == null) {
            throw new BusinessException("退货仓库不能为空，请选择退货仓库");
        }
        Warehouse wh = warehouseService.getById(returnBill.getStockId());
        if (wh == null) {
            throw new BusinessException("关联仓库不存在(stockId=" + returnBill.getStockId() + ")，请重新选择仓库");
        }
        String whName = wh.getStockName() != null ? wh.getStockName() : ("ID:" + returnBill.getStockId());

        for (int i = 0; i < items.size(); i++) {
            PurchaseReturnItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行关联物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            String matInfo = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "") + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
            if (item.getUnitId() != null && unitOfMeasureService.getById(item.getUnitId()) == null) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]关联单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
            }
            // 多单位：退货出库按「基本单位数量」校验库存（辅单位录入时行数量≠库存口径）
            BigDecimal qty = item.effectiveBaseQuantity() != null
                    ? item.effectiveBaseQuantity()
                    : (item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]退货数量必须大于0");
            }
            // 设置中心：允许单价为0校验（purchase.return.allow.zero.price）
            sysParamValidator.validateAllowZeroPrice("purchase.return.allow.zero.price", "采购退货单", item.getUnitPrice(), lineNo);

            boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);
            if (!negativeAllowed) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(mat.getMaterialId(), returnBill.getStockId());
                BigDecimal available = BigDecimal.ZERO;
                for (com.erp.inventory.entity.Inventory inv : invList) {
                    if (inv != null && inv.getAvailableQuantity() != null) {
                        available = available.add(inv.getAvailableQuantity());
                    }
                }
                if (available.compareTo(qty) < 0) {
                    throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]库存不足，仓库[" + whName + "]可用 "
                            + available.setScale(2, java.math.RoundingMode.HALF_UP)
                            + "，需退货 " + qty.setScale(2, java.math.RoundingMode.HALF_UP));
                }
            }
        }

        // 提交=严格校验：来源单号（设置中心开关）+ 明细业务校验（退货数量/批次号/可退数量/退货原因）
        sysParamValidator.validateRequireSource("purchase.return.require.source", "采购退货单", returnBill.getSourceBillNo());
        validateReturn(returnBill, items);
    }

    // ================= 审核入口（统一状态机 + 审批引擎防绕过） =================

    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("PURCHASE_RETURN", (Long) id) != null) {
            throw new BusinessException("采购退货单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：扣减库存（退货出库）+ 回写采购入库单已退货数量 */
    @Override
    public void onApproved(PurchaseReturn returnBill) {
        applyReturnImpact(returnBill, true);
    }

    /** 重置草稿影响：恢复库存 + 减少采购入库单已退货数量 */
    @Override
    public void onReverseApproved(PurchaseReturn returnBill) {
        applyReturnImpact(returnBill, false);
    }

    /** 作废影响：与重置草稿严格镜像（恢复库存 + 减少已退货数量） */
    @Override
    public void onVoided(PurchaseReturn returnBill) {
        applyReturnImpact(returnBill, false);
    }

    /** 反作废影响：恢复生效影响（扣减库存 + 回写已退货数量） */
    @Override
    public void onUnvoided(PurchaseReturn returnBill) {
        applyReturnImpact(returnBill, true);
    }

    /**
     * 退货单出入库影响（一正一反严格镜像）。
     * increase=true 生效（扣减库存/增加已退货数量），false 冲回（恢复库存/减少已退货数量）。
     * stock_effect=0 表示质检自动退货（不合格品从未入库），只做单据与财务冲减，不产生库存移动。
     */
    private void applyReturnImpact(PurchaseReturn returnBill, boolean increase) {
        String operator = returnBill.getApprovedBy() != null && !returnBill.getApprovedBy().isBlank()
                ? returnBill.getApprovedBy() : "系统";
        boolean stockEffect = returnBill.getStockEffect() == null || returnBill.getStockEffect() == 1;
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseReturnItem>().eq("return_id", returnBill.getReturnId()).orderByAsc("line_no"));
        if (items == null) return;
        for (PurchaseReturnItem item : items) {
            Long stockId = returnBill.getStockId();
            Long materialId = item.getMaterialId();
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            // 多单位换算（2026-11-09）：库存记账只认基本单位数量；未启用换算时回落到本行数量
            BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : qty;
            if (increase) {
                if (stockEffect && stockId != null && materialId != null) {
                    BigDecimal outUnitCost = inventoryService.getOutUnitCost(
                            stockId, item.getBinId(), materialId, item.getBatchNo());
                    item.setAvgCost(outUnitCost);
                    itemMapper.updateById(item);
                    inventoryService.deductStock(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQty,
                            returnBill.getReturnNo(), "PURCHASE_RETURN",
                            "PurchaseReturn", returnBill.getReturnNo(), operator);
                }
                writebackReceiptReturnedQty(sourceNo(returnBill.getSourceBillNo(), item.getSourceBillNo()),
                        item.getSourceRowNo(), qty, true);
            } else {
                if (stockEffect && stockId != null && materialId != null) {
                    // 回冲按审核时冻结的实际出库成本加回，避免用单据单价改写库存金额
                    BigDecimal reverseUnitCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                            ? item.getAvgCost()
                            : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                    inventoryService.addStock(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQty, reverseUnitCost,
                            returnBill.getReturnNo(), "PURCHASE_RETURN_REVERSE",
                            "PurchaseReturn", returnBill.getReturnNo(), operator);
                }
                writebackReceiptReturnedQty(sourceNo(returnBill.getSourceBillNo(), item.getSourceBillNo()),
                        item.getSourceRowNo(), qty, false);
            }
        }
        log.info("采购退货单{}影响: {} 操作人: {}", increase ? "生效" : "冲回", returnBill.getReturnNo(), operator);
    }

    /** 本单据特有守卫：已生成红字发票时不允许重置草稿/作废 */
    private void assertNoRedInvoice(PurchaseReturn returnBill, String opName) {
        Long redInvoiceCount = purchaseInvoiceMapper.selectCount(
                new QueryWrapper<PurchaseInvoice>()
                        .eq("source_bill_type", "采购退货单")
                        .eq("source_bill_no", returnBill.getReturnNo())
                        .eq("is_red_invoice", 1)
                        .ne("status", "已取消"));
        if (redInvoiceCount != null && redInvoiceCount > 0) {
            throw new BusinessException("退货单已生成红字发票，不可" + opName);
        }
    }

    /**
     * 重置草稿: 已生效 → 草稿
     * - 先过本单据守卫（已生成红字发票不可重置）
     * - 状态校验 + 下游校验 + 冲回钩子（onReverseApproved）统一由基类执行
     * - 显式置空 approved_by/approved_at（MyBatis-Plus 忽略 null 更新）
     */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        PurchaseReturn returnBill = getById(id);
        if (returnBill == null) throw new BusinessException("采购退货单不存在");
        assertNoRedInvoice(returnBill, "重置草稿");
        super.reverseApprove(id, operator);
        UpdateWrapper<PurchaseReturn> uw = new UpdateWrapper<>();
        uw.eq("return_id", id)
                .set("approved_by", null)
                .set("approved_at", null)
                .set("updated_at", LocalDateTime.now())
                .set("updated_by", operator);
        update(uw);
        log.info("采购退货单重置草稿: {} 操作人: {}", returnBill.getReturnNo(), operator);
    }

    /** 来源单号：表头优先，缺失时取明细行来源单号（手动选来源单只写明细的场景） */
    private static String sourceNo(String headNo, String itemNo) {
        return (headNo != null && !headNo.isBlank()) ? headNo : itemNo;
    }

    /** 回写采购入库单已退货数量 (increase=true 增加, false 减少) */
    private void writebackReceiptReturnedQty(String receiptNo, Integer receiptLineNo, BigDecimal qty, boolean increase) {
        if (receiptNo == null || receiptNo.isBlank() || receiptLineNo == null) return;
        PurchaseInbound receipt = purchaseInboundMapper.selectOne(
                new QueryWrapper<PurchaseInbound>().eq("receipt_no", receiptNo).last("LIMIT 1"));
        if (receipt == null) return;
        PurchaseInboundItem receiptItem = purchaseInboundItemMapper.selectOne(
                new QueryWrapper<PurchaseInboundItem>()
                        .eq("receipt_id", receipt.getReceiptId())
                        .eq("line_no", receiptLineNo)
                        .last("LIMIT 1"));
        if (receiptItem == null) return;
        BigDecimal current = receiptItem.getReturnedQuantity() != null ? receiptItem.getReturnedQuantity() : BigDecimal.ZERO;
        BigDecimal newReturned = increase ? current.add(qty) : current.subtract(qty);
        if (newReturned.compareTo(BigDecimal.ZERO) < 0) newReturned = BigDecimal.ZERO;
        receiptItem.setReturnedQuantity(newReturned);
        purchaseInboundItemMapper.updateById(receiptItem);
        log.info("回写采购入库单已退货数量: 入库单={} 行={} {}后累计={}", receiptNo, receiptLineNo, increase ? "增加" : "减少", newReturned);
    }

    /** 取消: 草稿 → 已取消 */
    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseReturn returnBill = getById(id);
        if (returnBill == null) throw new BusinessException("采购退货单不存在");
        if (!"草稿".equals(returnBill.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + returnBill.getStatus());
        }
        returnBill.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            returnBill.setRemark((returnBill.getRemark() != null ? returnBill.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(returnBill);
        log.info("采购退货单取消: {} 原因: {}", returnBill.getReturnNo(), reason);
    }

    /** 退货单号生成: 文档格式 PRT-yyyyMMdd-0001 (带连字符, 4位流水号) */
    @Override
    public String generateReturnNo() {
        String docType = "PURCHASE_RETURN";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "PRT-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
            QueryWrapper<PurchaseReturn> wrapper = new QueryWrapper<>();
            wrapper.likeRight("return_no", prefix).orderByDesc("return_no").last("LIMIT 1");
            PurchaseReturn last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReturnNo() != null) {
                try {
                    seq = Integer.parseInt(last.getReturnNo().substring(prefix.length())) + 1;
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
        PurchaseReturn bill = getById(id);
        if (bill == null) throw new BusinessException("采购退货单不存在");
        // 本单据特有守卫：已生成红字发票不可作废
        assertNoRedInvoice(bill, "作废");
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
     * 删除已生效退货单会恢复库存并回退来源单已退货数量，已生成红字发票时必须拦截（否则红字发票指向已删除退货单）。
     */
    @Override
    protected void validateBeforeDelete(PurchaseReturn bill) {
        if (!BillStatusUtils.isApprovedClass(bill.getStatus())) return;
        assertNoRedInvoice(bill, "删除");
    }
}
