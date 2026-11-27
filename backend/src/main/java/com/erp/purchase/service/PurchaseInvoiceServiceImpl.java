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
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.entity.PurchaseInvoiceItem;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.mapper.PurchaseInvoiceItemMapper;
import com.erp.purchase.mapper.PurchaseInvoiceMapper;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
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
 * 采购发票服务实现
 * 文档3.5 单据状态流转: 草稿 → 待审核 → 已生效
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class PurchaseInvoiceServiceImpl extends AbstractBillService<PurchaseInvoiceMapper, PurchaseInvoice> implements PurchaseInvoiceService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 按名称筛但列是外键的场景（供应商）：Spec.column 写外键「属性名」，
        // 不写裸列名 —— 属性名注册口径下解析不到同名属性时该条件会被忽略（见 BillHeaderFilterSupport.apply）。
        // 注：主表金额列名特殊——属性 amount → 列 total_amount、属性 taxTotalAmount → 列 net_amount；
        // 本表无采购员列（发票无业务归属），员工维度不接入（与既有 pageQuery 口径一致）。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("invoiceNo", new BillHeaderFilterSupport.Spec("invoiceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceType", new BillHeaderFilterSupport.Spec("invoiceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("supplierTaxNo", new BillHeaderFilterSupport.Spec("supplierTaxNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceCode", new BillHeaderFilterSupport.Spec("invoiceCode", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceNumber", new BillHeaderFilterSupport.Spec("invoiceNumber", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryAddress", new BillHeaderFilterSupport.Spec("deliveryAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("invoiceDate", new BillHeaderFilterSupport.Spec("invoiceDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("amount", new BillHeaderFilterSupport.Spec("amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxTotalAmount", new BillHeaderFilterSupport.Spec("taxTotalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("purchaseInvoice", PurchaseInvoice.class, f);
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
            "close_status",
            "closed_at",
            "closed_by",
            "closed_reason",
            "created_at",
            "created_by",
            "invoice_code",
            "invoice_date",
            "invoice_id",
            "invoice_no",
            "invoice_number",
            "invoice_type",
            "is_closed",
            "is_match",
            "is_red_invoice",
            "line_no",
            "net_amount",
            "order_id",
            "order_no",
            "org_code",
            "receipt_id",
            "receipt_no",
            "remark",
            "source_bill_no",
            "source_bill_type",
            "status",
            "supplier_id",
            "supplier_name",
            "tax_amount",
            "tax_no",
            "total_amount",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final PurchaseInvoiceItemMapper itemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SupplierService supplierService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    /** 设置中心业务规则校验器（含税总开关 / 默认计税方式） */
    private final com.erp.config.SysParamValidator sysParamValidator;

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
    @Override protected String getBillTypeLabel() { return "采购发票"; }
    @Override protected String getApprovalBillType() { return "PURCHASE_INVOICE"; }
    @Override protected String getBillNo(PurchaseInvoice b) { return b.getInvoiceNo(); }
    @Override protected Object getId(PurchaseInvoice b) { return b.getInvoiceId(); }
    @Override protected String getStatus(PurchaseInvoice b) { return b.getStatus(); }
    @Override protected void setStatus(PurchaseInvoice b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(PurchaseInvoice b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(PurchaseInvoice b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(PurchaseInvoice b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(PurchaseInvoice b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(PurchaseInvoice b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(PurchaseInvoice b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(PurchaseInvoice b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(PurchaseInvoice b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(PurchaseInvoice b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(PurchaseInvoice b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(PurchaseInvoice b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(PurchaseInvoice b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(PurchaseInvoice b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(PurchaseInvoice bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getInvoiceNo());
    }

    @Override
    @Transactional
    public void saveInvoiceWithItems(PurchaseInvoice invoice, List<PurchaseInvoiceItem> items) {
        boolean isNew = invoice.getInvoiceId() == null;

        // 业务规则: 已生效不可修改, 如需修改需先重置草稿
        if (!isNew) {
            PurchaseInvoice existing = getById(invoice.getInvoiceId());
            if (existing != null) {
                if ("已生效".equals(existing.getStatus())) {
                    throw new BusinessException("已生效的发票不可直接修改，请先重置草稿");
                }
                if ("已取消".equals(existing.getStatus())) {
                    throw new BusinessException("已取消的发票不可修改");
                }
            }
        }

        // 业务规则: 发票号按编码规则自动生成 (PINV-yyyyMMdd-0001)
        if (invoice.getInvoiceNo() == null || invoice.getInvoiceNo().isBlank()) {
            invoice.setInvoiceNo(generateInvoiceNo());
        }

        // 设置默认值
        if (invoice.getStatus() == null) invoice.setStatus("草稿");
        if (invoice.getInvoiceDate() == null) invoice.setInvoiceDate(LocalDate.now());
        if (invoice.getInvoiceType() == null) invoice.setInvoiceType("增值税专用发票");
        if (invoice.getSourceType() == null) invoice.setSourceType("采购入库单");
        if (invoice.getIsRedInvoice() == null) invoice.setIsRedInvoice(0);
        if (invoice.getIsMatch() == null) invoice.setIsMatch(0);
        if (invoice.getAmount() == null) invoice.setAmount(BigDecimal.ZERO);
        if (invoice.getTaxAmount() == null) invoice.setTaxAmount(BigDecimal.ZERO);
        if (invoice.getTaxTotalAmount() == null) invoice.setTaxTotalAmount(BigDecimal.ZERO);
        if (invoice.getCreatedAt() == null) invoice.setCreatedAt(LocalDateTime.now());
        if (invoice.getUpdatedAt() == null) invoice.setUpdatedAt(LocalDateTime.now());
        // createdBy/updatedBy 交由 AuditMetaObjectHandler 按当前登录人自动填充（无登录态时落 system）

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<PurchaseInvoiceItem> validItems = new ArrayList<>();
            for (PurchaseInvoiceItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            invoice.setTaxCalcMethod("不计税");
        } else if (invoice.getTaxCalcMethod() == null || invoice.getTaxCalcMethod().isBlank()) {
            invoice.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(invoice.getTaxCalcMethod());
        // 金额/税额/价税合计由系统按行级「计税方式」自动计算
        recalculateInvoice(invoice, items, headerTaxType);

        // 金额模式：保存即校验「剩余可开金额」（不等提交），超额直接拦下
        if (sysParamValidator.isInvoiceAmountMode()
                && (invoice.getIsRedInvoice() == null || invoice.getIsRedInvoice() == 0)
                && items != null && !items.isEmpty()) {
            validateSourceAmounts(invoice, items);
        }

        if (isNew) {
            save(invoice);
        } else {
            updateById(invoice);
        }

        // 审批状态镜像: approval_status 未映射到实体, 需显式同步; 仅草稿/待审核态跟随 status
        String invoiceApStatus = invoice.getStatus();
        if ("草稿".equals(invoiceApStatus) || "待审核".equals(invoiceApStatus)) {
            update(new UpdateWrapper<PurchaseInvoice>()
                    .eq("invoice_id", invoice.getInvoiceId())
                    .set("approval_status", invoiceApStatus));
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<PurchaseInvoiceItem>().eq("invoice_id", invoice.getInvoiceId()));
            // 业务规则: 表体行号从1开始, 步长1
            int lineNo = 1;
            for (PurchaseInvoiceItem item : items) {
                item.setInvoiceItemId(null);
                item.setInvoiceId(invoice.getInvoiceId());
                item.setLineNo(lineNo);
                calculateItemAmount(item, headerTaxType);
                if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                if (item.getUpdatedAt() == null) item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("采购发票保存成功: {} 状态: {} 价税合计: {}", invoice.getInvoiceNo(), invoice.getStatus(), invoice.getTaxTotalAmount());
    }

    /**
     * 业务规则校验
     * - 专票(增值税专用发票)必须填写供应商税号
     * - 开票数量必须>0, 且≤可开票数量(源单数量-已开票数量)
     * - 单价≥0
     * - 红字发票源单类型必须为"采购退货单"
     */
    private void validateInvoice(PurchaseInvoice invoice, List<PurchaseInvoiceItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购发票至少需要一行明细");
        }
        // 专票必须填写供应商税号
        if ("增值税专用发票".equals(invoice.getInvoiceType()) || "专票".equals(invoice.getInvoiceType())) {
            if (invoice.getSupplierTaxNo() == null || invoice.getSupplierTaxNo().isBlank()) {
                throw new BusinessException("增值税专用发票必须填写供应商税号");
            }
        }
        // 红字发票源单类型必须为"采购退货单"
        if (invoice.getIsRedInvoice() != null && invoice.getIsRedInvoice() == 1) {
            if (!"采购退货单".equals(invoice.getSourceType())) {
                throw new BusinessException("红字发票的源单类型必须为采购退货单");
            }
        }
        // 查找源采购订单(用于可开票数量校验和回写)
        PurchaseOrder sourceOrder = null;
        if ("采购订单".equals(invoice.getSourceType())) {
            if (invoice.getSourceBillNo() == null || invoice.getSourceBillNo().isBlank()) {
                throw new BusinessException("源单类型为采购订单时, 源单编号必须填写");
            }
            sourceOrder = purchaseOrderMapper.selectOne(
                    new QueryWrapper<PurchaseOrder>().eq("order_no", invoice.getSourceBillNo()).last("LIMIT 1"));
            if (sourceOrder == null) {
                throw new BusinessException("源采购订单不存在: " + invoice.getSourceBillNo());
            }
        } else if ("采购入库单".equals(invoice.getSourceType())) {
            // 通过入库单找到源采购订单
            if (invoice.getSourceBillNo() != null && !invoice.getSourceBillNo().isBlank()) {
                PurchaseInbound receipt = purchaseInboundMapper.selectOne(
                        new QueryWrapper<PurchaseInbound>().eq("receipt_no", invoice.getSourceBillNo()).last("LIMIT 1"));
                if (receipt != null && receipt.getSourceBillNo() != null) {
                    sourceOrder = purchaseOrderMapper.selectOne(
                            new QueryWrapper<PurchaseOrder>().eq("order_no", receipt.getSourceBillNo()).last("LIMIT 1"));
                }
            }
        }
        boolean amountMode = sysParamValidator.isInvoiceAmountMode();
        for (int i = 0; i < items.size(); i++) {
            PurchaseInvoiceItem item = items.get(i);
            if (amountMode) {
                // 金额模式（对齐金蝶「发票只校验金额」）：数量允许为 0/空，只校验开票金额
                if (nvl(item.getNetAmount()).compareTo(BigDecimal.ZERO) == 0) {
                    throw new BusinessException("第" + (i + 1) + "行开票金额不能为 0");
                }
            } else {
                // 开票数量必须>0
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("第" + (i + 1) + "行开票数量必须大于0");
                }
            }
            // 单价≥0
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("第" + (i + 1) + "行单价不能为负数");
            }
            // 税率≥0
            if (item.getTaxRate() == null || item.getTaxRate().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("第" + (i + 1) + "行税率不能为负数");
            }
            // 校验可开票数量(源单为采购订单时；金额模式改按金额校验，见循环后的 validateSourceAmounts)
            if (!amountMode && sourceOrder != null && item.getSourceRowNo() != null) {
                PurchaseOrderItem orderItem = purchaseOrderItemMapper.selectOne(
                        new QueryWrapper<PurchaseOrderItem>()
                                .eq("order_id", sourceOrder.getOrderId())
                                .eq("line_no", item.getSourceRowNo())
                                .last("LIMIT 1"));
                if (orderItem != null) {
                    BigDecimal orderQty = orderItem.getQuantity() != null ? orderItem.getQuantity() : BigDecimal.ZERO;
                    BigDecimal invoiced = orderItem.getInvoicedQuantity() != null ? orderItem.getInvoicedQuantity() : BigDecimal.ZERO;
                    BigDecimal canInvoice = orderQty.subtract(invoiced);
                    if (item.getQuantity().compareTo(canInvoice) > 0) {
                        throw new BusinessException("第" + (i + 1) + "行开票数量超过可开票数量，最大可开: " + canInvoice);
                    }
                    // 单价从源单带出
                    if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0) {
                        item.setUnitPrice(orderItem.getUnitPrice());
                    }
                    // 税率从源单带出
                    if (item.getTaxRate() == null || item.getTaxRate().compareTo(BigDecimal.ZERO) == 0) {
                        item.setTaxRate(orderItem.getTaxRate());
                    }
                }
            }
        }
        // 金额模式：按「来源单剩余可开金额」统一核对（同一来源行多行明细先合并再比较）
        if (amountMode) {
            validateSourceAmounts(invoice, items);
        }
    }

    /**
     * 金额模式来源校验（对齐金蝶「发票只校验金额」）：
     * 同一来源明细「本次开票金额合计 + 已生效发票已开金额」不得超过来源明细的价税合计。
     * 来源：采购订单 → 订单明细；采购入库单 → 入库明细；红字（采购退货单）不校验金额。
     */
    private void validateSourceAmounts(PurchaseInvoice invoice, List<PurchaseInvoiceItem> items) {
        if ("采购退货单".equals(invoice.getSourceType())) return;
        if (invoice.getSourceBillNo() == null || invoice.getSourceBillNo().isBlank()) return;

        Map<Integer, BigDecimal> thisAmount = new java.util.LinkedHashMap<>();
        for (PurchaseInvoiceItem item : items) {
            if (item.getSourceRowNo() == null) continue;
            thisAmount.merge(item.getSourceRowNo(), nvl(item.getNetAmount()), BigDecimal::add);
        }
        if (thisAmount.isEmpty()) return;

        Long sourceDocId = null;
        if ("采购订单".equals(invoice.getSourceType())) {
            PurchaseOrder order = purchaseOrderMapper.selectOne(
                    new QueryWrapper<PurchaseOrder>().eq("order_no", invoice.getSourceBillNo()).last("LIMIT 1"));
            if (order == null) throw new BusinessException("源采购订单不存在: " + invoice.getSourceBillNo());
            sourceDocId = order.getOrderId();
        } else if ("采购入库单".equals(invoice.getSourceType())) {
            PurchaseInbound inbound = purchaseInboundMapper.selectOne(
                    new QueryWrapper<PurchaseInbound>().eq("receipt_no", invoice.getSourceBillNo()).last("LIMIT 1"));
            if (inbound == null) throw new BusinessException("源采购入库单不存在: " + invoice.getSourceBillNo());
            sourceDocId = inbound.getReceiptId();
        }
        if (sourceDocId == null) return;

        for (Map.Entry<Integer, BigDecimal> entry : thisAmount.entrySet()) {
            Integer rowNo = entry.getKey();
            BigDecimal sourceTotal = null;
            if ("采购订单".equals(invoice.getSourceType())) {
                PurchaseOrderItem oi = purchaseOrderItemMapper.selectOne(
                        new QueryWrapper<PurchaseOrderItem>()
                                .eq("order_id", sourceDocId).eq("line_no", rowNo).last("LIMIT 1"));
                if (oi != null) sourceTotal = nvl(oi.getNetAmount());
            } else {
                PurchaseInboundItem ii = purchaseInboundItemMapper.selectOne(
                        new QueryWrapper<PurchaseInboundItem>()
                                .eq("receipt_id", sourceDocId).eq("line_no", rowNo).last("LIMIT 1"));
                if (ii != null) sourceTotal = nvl(ii.getNetAmount());
            }
            if (sourceTotal == null) continue;
            BigDecimal invoiced = sumInvoicedAmount(invoice.getSourceBillNo(), rowNo, invoice.getInvoiceId());
            BigDecimal remain = sourceTotal.subtract(invoiced);
            if (entry.getValue().compareTo(remain) > 0) {
                throw new BusinessException("来源单【" + invoice.getSourceBillNo() + "】第 " + rowNo + " 行开票金额超过剩余可开金额（来源金额: "
                        + sourceTotal + "，已开票: " + invoiced + "，剩余可开: " + remain + "，本次: " + entry.getValue() + "）");
            }
        }
    }

    /** 某来源单某行的「已生效发票已开金额」合计（排除当前发票自身；红字发票金额为负自动抵扣） */
    private BigDecimal sumInvoicedAmount(String sourceBillNo, Integer sourceRowNo, Long excludeInvoiceId) {
        QueryWrapper<PurchaseInvoiceItem> qw = new QueryWrapper<>();
        qw.eq("source_bill_no", sourceBillNo);
        if (sourceRowNo != null) qw.eq("source_row_no", sourceRowNo);
        qw.inSql("invoice_id", "select invoice_id from purchase_invoice where status = '已生效'");
        if (excludeInvoiceId != null) qw.ne("invoice_id", excludeInvoiceId);
        BigDecimal sum = BigDecimal.ZERO;
        for (PurchaseInvoiceItem it : itemMapper.selectList(qw)) {
            sum = sum.add(nvl(it.getNetAmount()));
        }
        return sum;
    }

    /**
     * 金额计算(无折扣)
     * - 金额 = 数量 × 单价
     * - 税额 = 金额 × 税率 / 100
     * - 价税合计 = 金额 + 税额
     */
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
    private void calculateItemAmount(PurchaseInvoiceItem item, int headerTaxType) {
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

        // 金额模式（设置中心「发票录入口径」=按发票金额）：金额说了算，数量单价只读并反算
        if (sysParamValidator.isInvoiceAmountMode()) {
            calculateItemAmountByEnteredAmount(item, taxType, taxRate, quantity);
            return;
        }

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

        // 折前金额按「不含税」口径；折扣直接并入金额（本表无折扣列，只体现在 amount 中）
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

    /**
     * 金额模式逐行计算（对齐金蝶「发票只校验金额」）：
     * - 不含税价：以录入的「开票金额(amount)」为准，税额=金额×税率，价税合计=金额+税额；
     * - 含税价  ：以录入的「开票价税合计(netAmount)」为准，金额=合计/(1+税率)，税额=合计-金额；
     * - 不计税  ：以录入的「开票金额」为准，税额=0，价税合计=金额。
     * 单价按「折前金额 ÷ 数量」反算（数量为 0 时保留原单价），含税/不含税单价按口径换算。
     */
    private void calculateItemAmountByEnteredAmount(PurchaseInvoiceItem item, int taxType,
                                                    BigDecimal taxRate, BigDecimal quantity) {
        BigDecimal discountAmount = nvl(item.getDiscountAmount());
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        BigDecimal amount;
        BigDecimal taxAmount;
        BigDecimal netAmount;
        if (taxType == TAX_TYPE_NO_TAX) {
            amount = nvl(item.getAmount()).setScale(SCALE, RoundingMode.HALF_UP);
            taxAmount = BigDecimal.ZERO.setScale(SCALE);
            netAmount = amount;
        } else if (taxType == TAX_TYPE_INCLUSIVE) {
            netAmount = nvl(item.getNetAmount()).setScale(SCALE, RoundingMode.HALF_UP);
            amount = netAmount.divide(taxFactor, SCALE, RoundingMode.HALF_UP);
            taxAmount = netAmount.subtract(amount).setScale(SCALE, RoundingMode.HALF_UP);
        } else {
            amount = nvl(item.getAmount()).setScale(SCALE, RoundingMode.HALF_UP);
            taxAmount = amount.multiply(taxRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            netAmount = amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP);
        }

        item.setAmount(amount);
        item.setTaxAmount(taxAmount);
        item.setNetAmount(netAmount);
        item.setDiscountAmount(discountAmount);

        BigDecimal originalAmount = amount.add(discountAmount);
        BigDecimal unitPrice = quantity.compareTo(BigDecimal.ZERO) > 0
                ? originalAmount.divide(quantity, 6, RoundingMode.HALF_UP)
                : nvl(item.getUnitPrice());
        item.setUnitPrice(unitPrice);
        if (taxType == TAX_TYPE_INCLUSIVE) {
            item.setPriceWithTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
            item.setPriceWithoutTax(unitPrice.divide(taxFactor, 6, RoundingMode.HALF_UP));
        } else if (taxType == TAX_TYPE_NO_TAX) {
            item.setPriceWithTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
            item.setPriceWithoutTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
        } else {
            item.setPriceWithoutTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
            item.setPriceWithTax(unitPrice.multiply(taxFactor).setScale(6, RoundingMode.HALF_UP));
        }
    }

    /** 汇总金额/税额/价税合计，并按表头汇率折算本位币 */
    private void recalculateInvoice(PurchaseInvoice invoice, List<PurchaseInvoiceItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        if (items != null) {
            for (PurchaseInvoiceItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
            }
        }
        invoice.setAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        invoice.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        invoice.setTaxTotalAmount(totalAmount.add(totalTax).setScale(SCALE, RoundingMode.HALF_UP));
        BigDecimal rate = invoice.getExchangeRate() != null ? invoice.getExchangeRate() : BigDecimal.ONE;
        invoice.setBaseTotalAmount(invoice.getAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        invoice.setBaseTaxAmount(invoice.getTaxAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        invoice.setBaseNetAmount(invoice.getTaxTotalAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<PurchaseInvoice> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<PurchaseInvoice> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseInvoice.class);
        // 关键词：发票号 + 供应商名称 多列匹配，强制成组（前端提示"输入发票号/供应商名称搜索"，原实现只匹配发票号）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "invoice_no", "supplier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 invoice_date）；供应商（本页真实列 supplier_id）
        // 员工维度：本表无采购员列（见下方数据权限注释），故不接入 employeeId
        ListQuerySupport.applyDateRange(wrapper, "invoice_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        // 注意：purchase_invoice 表没有 buyer_id 列（发票无采购员归属字段），只按 created_by 过滤，传 null 避免 SQL 报错
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "purchase_invoice", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_invoice"));
        Page<PurchaseInvoice> page = new Page<>(current, size);
        Page<PurchaseInvoice> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<PurchaseInvoice> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<PurchaseInvoice> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseInvoice.class);
        // 关键词：发票号 + 供应商名称 成组匹配（与列表页搜索提示一致，口径同上一重载）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "invoice_no", "supplier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（invoice_date）；供应商（supplier_id）；本表无采购员列，员工维度不接入
        ListQuerySupport.applyDateRange(wrapper, "invoice_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "purchaseInvoice", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        CustomFieldSearchHelper.apply(wrapper, "purchase_invoice", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_invoice"));
        Page<PurchaseInvoice> page = new Page<>(current, size);
        Page<PurchaseInvoice> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseInvoice getDetail(Long id) {
        PurchaseInvoice invoice = getById(id);
        if (invoice == null) throw new BusinessException("采购发票不存在");
        List<PurchaseInvoiceItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInvoiceItem>().eq("invoice_id", id).orderByAsc("line_no"));
        invoice.setItems(items);
        return invoice;
    }

    /** 提交审核：状态校验/严格校验/审批开关/审批任务统一由统一状态机基类执行 */
    @Override
    @Transactional
    public void submit(Long id, String submitter) {
        PurchaseInvoice invoice = getById(id);
        if (invoice == null) throw new BusinessException("采购发票不存在");
        super.submitForApproval(invoice, submitter != null && !submitter.isBlank()
                ? submitter : BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（明细/供应商/物料/单位/开票数量 + 专票税号/红字源单/可开票金额） */
    @Override
    protected void validateStrictSubmit(PurchaseInvoice invoice) {
        Long id = invoice.getInvoiceId();
        List<PurchaseInvoiceItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInvoiceItem>().eq("invoice_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购发票没有明细，请至少添加一行物料后再提交");
        }

        if (invoice.getSupplierId() == null) {
            throw new BusinessException("供应商不能为空，请选择供应商");
        }
        if (supplierService.getById(invoice.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + invoice.getSupplierId() + ")，请重新选择供应商");
        }

        for (int i = 0; i < items.size(); i++) {
            PurchaseInvoiceItem item = items.get(i);
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
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]开票数量必须大于0");
            }
        }

        // 提交=严格校验：专票税号 / 红字发票源单类型 / 源单号与源单存在性 / 数量·单价·税率 / 可开票数量
        validateInvoice(invoice, items);
    }

    // ================= 审核入口（统一状态机 + 审批引擎防绕过） =================

    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("PURCHASE_INVOICE", (Long) id) != null) {
            throw new BusinessException("采购发票已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：回写源单（采购订单）已开票数量 */
    @Override
    public void onApproved(PurchaseInvoice invoice) {
        applyInvoicedWriteback(invoice, true);
    }

    /** 重置草稿影响：减少源单已开票数量 */
    @Override
    public void onReverseApproved(PurchaseInvoice invoice) {
        applyInvoicedWriteback(invoice, false);
    }

    /** 作废影响：与重置草稿镜像（减少源单已开票数量） */
    @Override
    public void onVoided(PurchaseInvoice invoice) {
        applyInvoicedWriteback(invoice, false);
    }

    /** 反作废影响：恢复生效影响（增加源单已开票数量） */
    @Override
    public void onUnvoided(PurchaseInvoice invoice) {
        applyInvoicedWriteback(invoice, true);
    }

    /** 已开票数量回写（increase=true 增加，false 减少；金额模式不回写数量） */
    private void applyInvoicedWriteback(PurchaseInvoice invoice, boolean increase) {
        if (sysParamValidator.isInvoiceAmountMode()) return;
        List<PurchaseInvoiceItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInvoiceItem>().eq("invoice_id", invoice.getInvoiceId()).orderByAsc("line_no"));
        if (items == null) return;
        for (PurchaseInvoiceItem item : items) {
            writebackOrderInvoicedQty(invoice, item, item.getQuantity(), increase);
        }
        log.info("采购发票{}影响: {} 已开票数量{}", increase ? "生效" : "冲回",
                invoice.getInvoiceNo(), increase ? "增加" : "减少");
    }

    /**
     * 重置草稿: 已生效 → 草稿
     * - 先过本单据守卫（已勾稽的发票不可重置）
     * - 状态校验 + 下游校验 + 冲回钩子（onReverseApproved）统一由基类执行
     * - 显式置空 approved_by/approved_at（MyBatis-Plus 忽略 null 更新）
     */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        PurchaseInvoice invoice = getById(id);
        if (invoice == null) throw new BusinessException("采购发票不存在");
        // 已勾稽的发票不可重置草稿
        if (invoice.getIsMatch() != null && invoice.getIsMatch() == 1) {
            throw new BusinessException("发票已勾稽，不可重置草稿");
        }
        super.reverseApprove(id, operator);
        UpdateWrapper<PurchaseInvoice> uw = new UpdateWrapper<>();
        uw.eq("invoice_id", id)
                .set("approved_by", null)
                .set("approved_at", null)
                .set("updated_at", LocalDateTime.now())
                .set("updated_by", operator);
        update(uw);
        log.info("采购发票重置草稿: {} 操作人: {}", invoice.getInvoiceNo(), operator);
    }

    /**
     * 回写源单(采购订单)已开票数量
     * - 发票源单类型为"采购订单": 直接按 source_row_no 回写订单行
     * - 发票源单类型为"采购入库单": 通过入库单明细找到源订单行号, 再回写
     * - 红字发票(源单为采购退货单): 不回写采购订单
     * - increase=true 增加, false 减少
     */
    private void writebackOrderInvoicedQty(PurchaseInvoice invoice, PurchaseInvoiceItem item, BigDecimal qty, boolean increase) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) == 0) return;
        PurchaseOrder sourceOrder = null;
        Integer orderLineNo = null;

        if ("采购订单".equals(invoice.getSourceType())) {
            if (invoice.getSourceBillNo() == null) return;
            sourceOrder = purchaseOrderMapper.selectOne(
                    new QueryWrapper<PurchaseOrder>().eq("order_no", invoice.getSourceBillNo()).last("LIMIT 1"));
            orderLineNo = item.getSourceRowNo();
        } else if ("采购入库单".equals(invoice.getSourceType())) {
            if (invoice.getSourceBillNo() == null) return;
            PurchaseInbound receipt = purchaseInboundMapper.selectOne(
                    new QueryWrapper<PurchaseInbound>().eq("receipt_no", invoice.getSourceBillNo()).last("LIMIT 1"));
            if (receipt == null || receipt.getSourceBillNo() == null) return;
            sourceOrder = purchaseOrderMapper.selectOne(
                    new QueryWrapper<PurchaseOrder>().eq("order_no", receipt.getSourceBillNo()).last("LIMIT 1"));
            // 通过入库单明细的 source_row_no 找到源订单行号
            if (receipt.getReceiptId() != null && item.getSourceRowNo() != null) {
                PurchaseInboundItem receiptItem = purchaseInboundItemMapper.selectOne(
                        new QueryWrapper<PurchaseInboundItem>()
                                .eq("receipt_id", receipt.getReceiptId())
                                .eq("line_no", item.getSourceRowNo())
                                .last("LIMIT 1"));
                if (receiptItem != null) {
                    orderLineNo = receiptItem.getSourceRowNo();
                }
            }
        } else {
            // 采购退货单(红字发票) 不回写采购订单
            return;
        }

        if (sourceOrder == null || orderLineNo == null) return;
        PurchaseOrderItem orderItem = purchaseOrderItemMapper.selectOne(
                new QueryWrapper<PurchaseOrderItem>()
                        .eq("order_id", sourceOrder.getOrderId())
                        .eq("line_no", orderLineNo)
                        .last("LIMIT 1"));
        if (orderItem == null) return;
        BigDecimal current = orderItem.getInvoicedQuantity() != null ? orderItem.getInvoicedQuantity() : BigDecimal.ZERO;
        BigDecimal newInvoiced = increase ? current.add(qty) : current.subtract(qty);
        if (newInvoiced.compareTo(BigDecimal.ZERO) < 0) newInvoiced = BigDecimal.ZERO;
        orderItem.setInvoicedQuantity(newInvoiced);
        purchaseOrderItemMapper.updateById(orderItem);
        log.info("回写采购订单已开票数量: 订单={} 行={} {}后累计={}", sourceOrder.getOrderNo(), orderLineNo, increase ? "增加" : "减少", newInvoiced);
    }

    /** 取消: 草稿 → 已取消 */
    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseInvoice invoice = getById(id);
        if (invoice == null) throw new BusinessException("采购发票不存在");
        if (!"草稿".equals(invoice.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + invoice.getStatus());
        }
        invoice.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            invoice.setRemark((invoice.getRemark() != null ? invoice.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(invoice);
        log.info("采购发票取消: {} 原因: {}", invoice.getInvoiceNo(), reason);
    }

    /** 发票号生成: 文档格式 PINV-yyyyMMdd-0001 (带连字符, 4位流水号) */
    @Override
    public String generateInvoiceNo() {
        String docType = "PURCHASE_INVOICE";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "PINV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
            QueryWrapper<PurchaseInvoice> wrapper = new QueryWrapper<>();
            wrapper.likeRight("invoice_no", prefix).orderByDesc("invoice_no").last("LIMIT 1");
            PurchaseInvoice last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getInvoiceNo() != null) {
                try {
                    seq = Integer.parseInt(last.getInvoiceNo().substring(prefix.length())) + 1;
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
        PurchaseInvoice bill = getById(id);
        if (bill == null) throw new BusinessException("采购发票不存在");
        // 本单据特有守卫：已勾稽的发票不可作废
        if (bill.getIsMatch() != null && bill.getIsMatch() == 1) {
            throw new BusinessException("发票已勾稽，不可作废");
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
     * 删除前守卫：与「作废」同口径。
     * 删除已生效发票会回退源单已开票数量，已勾稽的发票必须拦截（否则勾稽明细与发票数量不一致）。
     */
    @Override
    protected void validateBeforeDelete(PurchaseInvoice bill) {
        if (!BillStatusUtils.isApprovedClass(bill.getStatus())) return;
        if (bill.getIsMatch() != null && bill.getIsMatch() == 1) {
            throw new BusinessException("发票已勾稽，不可删除");
        }
    }
}
