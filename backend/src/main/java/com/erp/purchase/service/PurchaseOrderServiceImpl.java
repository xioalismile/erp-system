package com.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.bill.CloseStatusEnum;
import com.erp.config.BusinessException;
import com.erp.config.KgwBusinessRules;
import com.erp.config.SysParamValidator;import com.erp.config.service.SysConfigService;
import com.erp.finance.prepayment.purchase.service.PurchasePrePaymentService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class PurchaseOrderServiceImpl
        extends AbstractBillService<PurchaseOrderMapper, PurchaseOrder>
        implements PurchaseOrderService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 按名称筛但列是外键的场景（供应商/采购员/部门/币种/结算方式）：Spec.column 写外键「属性名」，
        // 不写裸列名 —— 属性名注册口径下解析不到同名属性时该条件会被忽略（见 BillHeaderFilterSupport.apply）。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryPlace", new BillHeaderFilterSupport.Spec("deliveryPlace", BillHeaderFilterSupport.Kind.TEXT));
        f.put("requestNo", new BillHeaderFilterSupport.Spec("requestNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("accountName", new BillHeaderFilterSupport.Spec("accountName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("buyerName", new BillHeaderFilterSupport.Spec("buyerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("settleModeName", new BillHeaderFilterSupport.Spec("settleModeId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("orderDate", new BillHeaderFilterSupport.Spec("orderDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("deliveryDate", new BillHeaderFilterSupport.Spec("deliveryDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaymentAmount", new BillHeaderFilterSupport.Spec("prepaymentAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxRate", new BillHeaderFilterSupport.Spec("taxRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("purchaseOrder", PurchaseOrder.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "attachment_urls",
            "base_net_amount",
            "base_tax_amount",
            "base_total_amount",
            "bill_before_status",
            "buyer_id",
            "buyer_name",
            "close_status",
            "closed_at",
            "closed_by",
            "closed_reason",
            "contact_person",
            "contact_phone",
            "created_at",
            "created_by",
            "currency_id",
            "delivery_date",
            "delivery_place",
            "department_id",
            "dept_name",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "is_closed",
            "line_no",
            "net_amount",
            "order_date",
            "order_id",
            "order_no",
            "org_code",
            "payment_term_id",
            "prepayment_amount",
            "remark",
            "request_no",
            "source_bill_no",
            "source_bill_type",
            "status",
            "supplier_id",
            "supplier_name",
            "tax_amount",
            "tax_rate",
            "total_amount",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final PurchaseOrderItemMapper itemMapper;
    private final MaterialCostService materialCostService;
    /**
     * 采购价格策略：提交时按"策略价"校验（2026-09-20 价格策略修复）。
     * @Lazy 打破潜在构造期环（价格策略侧依赖通用级联删除组件）。
     */
    @Autowired @Lazy private com.erp.price.purchase.service.PurchasePriceStrategyService purchasePriceStrategyService;
    private final SysParamValidator sysParamValidator;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final KgwBusinessRules kgwRules;
    private final BillRelationService billRelationService;

    @Autowired private SysConfigService sysConfigService;
    @Autowired @Lazy private PurchasePrePaymentService purchasePrePaymentService;
    @Autowired private MaterialService materialService;
    @Autowired private SupplierService supplierService;
    @Autowired private EmployeeService employeeService;
    @Autowired private CurrencyService currencyService;
    @Autowired private UnitOfMeasureService unitOfMeasureService;
    @Autowired private WarehouseService warehouseService;
    /** @Lazy 打破循环依赖：入库单Service构造注入 PurchaseOrderService，本类再注入它即成环 */
    @Autowired @Lazy private PurchaseInboundService purchaseInboundService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;
    private static final BigDecimal OVER_RECEIPT_RATIO = BigDecimal.ZERO;

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    // ================= AbstractBillService 钩子实现 =================
    @Override protected String getBillTypeLabel() { return "采购订单"; }
    @Override protected String getApprovalBillType() { return "PURCHASE_ORDER"; }
    @Override protected String getBillNo(PurchaseOrder b) { return b.getOrderNo(); }
    @Override protected Object getId(PurchaseOrder b) { return b.getOrderId(); }
    @Override protected String getStatus(PurchaseOrder b) { return b.getStatus(); }
    @Override protected void setStatus(PurchaseOrder b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(PurchaseOrder b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(PurchaseOrder b, Integer v) { b.setCloseStatus(v); if (v != null) b.setIsClosed(v); /* 双写兼容旧isClosed */ }
    @Override protected void setApprovedBy(PurchaseOrder b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(PurchaseOrder b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(PurchaseOrder b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(PurchaseOrder b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(PurchaseOrder b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(PurchaseOrder b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(PurchaseOrder b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(PurchaseOrder b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(PurchaseOrder b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(PurchaseOrder b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(PurchaseOrder b, String v) { b.setVoidReason(v); }

    @Override
    public List<String> listApprovedDownstreamBills(PurchaseOrder bill) {
        // 通用关联 + 本单勾稽入库/开票
        List<String> fromRelation = billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getOrderNo());
        List<String> result = new ArrayList<>(fromRelation);
        if (bill.getOrderId() != null) {
            List<PurchaseOrderItem> items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", bill.getOrderId()));
            for (PurchaseOrderItem it : items) {
                BigDecimal received = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : BigDecimal.ZERO;
                BigDecimal invoiced = it.getInvoicedQuantity() != null ? it.getInvoicedQuantity() : BigDecimal.ZERO;
                if (received.compareTo(BigDecimal.ZERO) > 0) {
                    result.add("采购入库数量已勾稽行(line:" + it.getLineNo() + ",qty:" + received.stripTrailingZeros() + ")");
                    break;
                }
                if (invoiced.compareTo(BigDecimal.ZERO) > 0) {
                    result.add("采购开票数量已勾稽行(line:" + it.getLineNo() + ",qty:" + invoiced.stripTrailingZeros() + ")");
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public void onApproved(PurchaseOrder order) {
        List<PurchaseOrderItem> items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", order.getOrderId()).orderByAsc("line_no"));
        if (items != null) {
            for (PurchaseOrderItem item : items) {
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                    itemMapper.updateById(item);
                }
            }
        }
        // TODO 真实接入应付生成（FinanceService暂未暴露createApFromPurchaseOrder，预留）
        log.info("[采购订单审核影响占位] 刷新核算成本完成，订单: {}", order.getOrderNo());
        // 预付联动：预付>0 时按订单自动生成一张草稿采购预付款单（幂等，同事务，失败回滚审批）
        if (order.getPrepaymentAmount() != null && order.getPrepaymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            if (purchasePrePaymentService == null) {
                log.warn("预付联动未就绪，跳过自动生成采购预付款单，订单: {}", order.getOrderNo());
            } else {
                purchasePrePaymentService.createFromPurchaseOrder(order);
            }
        }
    }

    @Override public void onReverseApproved(PurchaseOrder bill) {
        // 预付联动清理：若预付自动生成的预付款单仍是草稿则随订单反审一并删除；
        // 若已生效(非草稿)则由 rollbackAutoByOrder 抛业务异常阻止反审
        if (bill != null && bill.getOrderId() != null && purchasePrePaymentService != null) {
            try {
                int n = purchasePrePaymentService.rollbackAutoByOrder(bill.getOrderId());
                if (n > 0) log.info("采购订单重置草稿联动删除自动预付草稿 {} 张，订单: {}", n, bill.getOrderNo());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("采购订单重置草稿联动清理自动预付款单失败，订单: {}", bill.getOrderNo(), e);
            }
        }
        log.info("[采购订单重置草稿影响] 订单: {}", bill.getOrderNo());
    }

    /** 作废（作废前为已生效）：与重置草稿严格镜像，清理自动预付款草稿（已生效则阻止作废） */
    @Override
    public void onVoided(PurchaseOrder bill) {
        if (bill != null && bill.getOrderId() != null && purchasePrePaymentService != null) {
            int n = purchasePrePaymentService.rollbackAutoByOrder(bill.getOrderId());
            if (n > 0) log.info("采购订单作废联动删除自动预付草稿 {} 张，订单: {}", n, bill.getOrderNo());
        }
    }

    /** 反作废（恢复为已生效）：恢复预付金额对应的自动预付款草稿 */
    @Override
    public void onUnvoided(PurchaseOrder bill) {
        if (bill == null || bill.getOrderId() == null || purchasePrePaymentService == null) return;
        purchasePrePaymentService.createFromPurchaseOrder(bill);
    }

    @Override
    protected void validateStrictSubmit(PurchaseOrder bill) {
        List<PurchaseOrderItem> items;
        if (bill.getItems() != null && !bill.getItems().isEmpty()) items = bill.getItems();
        else if (bill.getOrderId() != null) items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", bill.getOrderId()).orderByAsc("line_no"));
        else items = List.of();
        if (items == null || items.isEmpty()) throw new BusinessException("采购订单没有明细，请至少添加一行物料后再提交");
        if (bill.getSupplierId() == null) throw new BusinessException("供应商不能为空，请选择供应商");
        if (supplierService.getById(bill.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + bill.getSupplierId() + ")，请重新选择供应商");
        }
        if (bill.getBuyerId() != null && employeeService.getById(bill.getBuyerId()) == null) {
            throw new BusinessException("关联采购员不存在(buyerId=" + bill.getBuyerId() + ")，请重新选择采购员");
        }
        if (bill.getCurrencyId() == null) throw new BusinessException("币种不能为空，请选择币种");
        if (currencyService.getById(bill.getCurrencyId()) == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + bill.getCurrencyId() + ")，请重新选择币种");
        }
        sysParamValidator.validateRequireSource("purchase.order.require.source", "采购订单", bill.getSourceBillNo());
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) throw new BusinessException("第" + lineNo + "行物料不能为空");
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) throw new BusinessException("第" + lineNo + "行关联物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            String matInfo = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "") + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
            if (item.getUnitId() != null && unitOfMeasureService.getById(item.getUnitId()) == null) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]关联单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]数量必须大于0");
            }
        }
        // KGW校验：复用validateOrder
        validateOrder(bill, items);
    }

    // ================= 接口实现（保存草稿-宽松 / 提交-严格 / 其他-基类委托） =================

    /**
     * 采购批量整数倍校验（2026-11-25，金蝶/用友采购批量政策口径）。
     * 设置中心「采购订单按采购批量校验」开启时，明细基本单位数量必须是物料采购批量的整数倍；
     * 开关关闭（默认）不做任何校验，与升级前行为一致。
     */
    private void validatePurchaseBatchQty(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) return;
        if (!sysConfigService.getBooleanValue("purchase.order.batch.check.enabled", false)) return;
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            if (item.getMaterialId() == null) continue;
            com.erp.masterdata.entity.Material material = materialService.getById(item.getMaterialId());
            if (material == null) continue;
            BigDecimal batch = material.getPurchaseBatchQty();
            if (batch == null || batch.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal qty = item.getBaseQuantity() != null ? item.getBaseQuantity() : item.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (qty.divideAndRemainder(batch)[1].compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal suggest = qty.divide(batch, 0, RoundingMode.CEILING).multiply(batch);
                throw new BusinessException("第" + (i + 1) + "行[" + item.getMaterialName() + "]采购数量必须是采购批量 "
                        + batch.stripTrailingZeros().toPlainString() + " 的整数倍，建议数量 "
                        + suggest.stripTrailingZeros().toPlainString());
            }
        }
    }

    @Override
    @Transactional
    public void saveOrderWithItems(PurchaseOrder order, List<PurchaseOrderItem> items) {
        boolean isNew = order.getOrderId() == null;
        if (!isNew) {
            PurchaseOrder existing = getById(order.getOrderId());
            if (existing != null) {
                if (BillStatusUtils.isFinalized(existing.getStatus(), existing.getCloseStatus())) {
                    throw new BusinessException(getBillTypeLabel() + "[" + existing.getOrderNo() + "]已完结，不能修改");
                }
                int num = BillStatusUtils.toNumber(existing.getStatus());
                if (num == BillStatusEnum.APPROVED.getCode()) {
                    throw new BusinessException("已生效的采购订单不可直接修改，请先重置草稿");
                }
                if ("已关闭".equals(existing.getStatus())) {
                    throw new BusinessException("已关闭的采购订单不可修改");
                }
                if ("已取消".equals(existing.getStatus())) {
                    throw new BusinessException("已取消的采购订单不可修改");
                }
                sysParamValidator.validateLockBillDate("采购订单", existing.getOrderDate(), order.getOrderDate(), existing.getStatus());
            }
        }
        // 单号只在新建时生成：更新时不重新取号（否则每次保存都会吃掉一个序号）
        if (isNew && (order.getOrderNo() == null || order.getOrderNo().isBlank())) order.setOrderNo(generateOrderNo());
        QueryWrapper<PurchaseOrder> dupQw = new QueryWrapper<PurchaseOrder>().eq("order_no", order.getOrderNo());
        if (!isNew) dupQw.ne("order_id", order.getOrderId());
        sysParamValidator.validateBillNoDuplicate(order.getOrderNo(), "采购订单", count(dupQw) > 0);

        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (order.getStatus() == null || order.getStatus().isBlank()) order.setStatus("草稿");
        if (order.getApprovalStatus() == null || order.getApprovalStatus().isBlank()
                || ("草稿".equals(order.getStatus()) && "待审核".equals(order.getApprovalStatus()))) {
            order.setApprovalStatus("草稿");
        }
        if (order.getExchangeRate() == null) order.setExchangeRate(BigDecimal.ONE);
        if (order.getTaxRate() == null) order.setTaxRate(new BigDecimal("13.0000"));
        if (order.getDiscountRate() == null) order.setDiscountRate(BigDecimal.ZERO);
        if (order.getDiscountAmount() == null) order.setDiscountAmount(BigDecimal.ZERO);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        if (order.getTaxAmount() == null) order.setTaxAmount(BigDecimal.ZERO);
        if (order.getNetAmount() == null) order.setNetAmount(BigDecimal.ZERO);
        if (order.getPrepaymentAmount() == null) order.setPrepaymentAmount(BigDecimal.ZERO);
        if (order.getIsClosed() == null) order.setIsClosed(0);
        if (order.getCloseStatus() == null) order.setCloseStatus(0);
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            order.setTaxCalcMethod("不计税");
        } else if (order.getTaxCalcMethod() == null || order.getTaxCalcMethod().isBlank()) {
            order.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        // 2026-11-25：采购批量整数倍校验（设置中心开关，默认关）
        validatePurchaseBatchQty(items);
        int headerTaxType = resolveHeaderTaxType(order.getTaxCalcMethod());
        // 宽松草稿：不做 validateOrder；只做金额计算
        recalculateOrder(order, items, headerTaxType);

        // 保存
        if (isNew) saveDraft(order); else updateById(order);
        if (items != null) {
            itemMapper.delete(new QueryWrapper<PurchaseOrderItem>().eq("order_id", order.getOrderId()));
            int lineNo = 1;
            for (PurchaseOrderItem item : items) {
                item.setOrderItemId(null);
                item.setOrderId(order.getOrderId());
                item.setLineNo(lineNo);
                if (item.getReceivedQuantity() == null) item.setReceivedQuantity(BigDecimal.ZERO);
                if (item.getInvoicedQuantity() == null) item.setInvoicedQuantity(BigDecimal.ZERO);
                if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                if (item.getUpdatedAt() == null) item.setUpdatedAt(LocalDateTime.now());
                calculateItemAmount(item, headerTaxType);
                if (item.getMaterialId() != null) item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                itemMapper.insert(item);
                lineNo += 1;
            }
        }
        log.info("采购订单保存草稿: {} 状态: {} 合计: {}", order.getOrderNo(), order.getStatus(), order.getTotalAmount());
    }

    @Override
    public PageResult<PurchaseOrder> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseOrder.class);
        // 关键词：订单号 + 供应商名称 多列匹配，强制成组 AND (order_no LIKE ? OR supplier_name LIKE ?)
        // （原实现只匹配 order_no，前端却提示"输入订单号/供应商名称搜索" → 按供应商名搜不到）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "order_no", "supplier_name");
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        // 日期区间（本页真实列 order_date）；供应商（本页真实列 supplier_id）；员工=采购员（本页真实列 buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "order_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/自己经手的单；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "purchase_order", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_order"));
        Page<PurchaseOrder> page = new Page<>(current, size);
        Page<PurchaseOrder> result = page(page, wrapper);
        // 列表"入库"列展示字段：一条聚合查询批量回填（禁止在循环里逐单查询）
        fillInboundStatus(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<PurchaseOrder> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseOrder.class);
        // 关键词：订单号 + 供应商名称 多列匹配，强制成组（与列表页搜索提示一致）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "order_no", "supplier_name");
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        // 日期区间（order_date）；供应商（supplier_id）；员工=采购员（buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "order_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "purchaseOrder", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        CustomFieldSearchHelper.apply(wrapper, "purchase_order", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_order"));
        Page<PurchaseOrder> page = new Page<>(current, size);
        Page<PurchaseOrder> result = page(page, wrapper);
        fillInboundStatus(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 批量回填列表"入库"列（非数据库字段 inboundStatus）：
     * 一条聚合查询按 order_id 汇总订单明细的订单数量与已入库数量，再与订单数量比较得出
     * 未入库 / 部分入库 / 已入库，口径与 {@link #queryItemExecutionStatus(Long)} 的"执行完毕"判定一致
     * （已入库数量 >= 订单数量）。明细的已入库数量由采购入库单审核时回写
     * （PurchaseInboundServiceImpl → PurchaseOrderService#increaseReceivedQuantity）。
     * 注意：禁止在循环里逐单查询（N+1）。
     */
    private void fillInboundStatus(List<PurchaseOrder> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> orderIds = new ArrayList<>();
        for (PurchaseOrder o : records) {
            if (o.getOrderId() != null) orderIds.add(o.getOrderId());
        }
        if (orderIds.isEmpty()) return;
        QueryWrapper<PurchaseOrderItem> qw = new QueryWrapper<>();
        qw.select("order_id",
                        "SUM(COALESCE(quantity,0)) AS order_qty",
                        "SUM(COALESCE(received_quantity,0)) AS received_qty")
                .in("order_id", orderIds)
                .groupBy("order_id");
        Map<Long, String> statusMap = new HashMap<>();
        for (Map<String, Object> row : itemMapper.selectMaps(qw)) {
            Object idVal = row.get("order_id");
            if (idVal == null) continue;
            BigDecimal orderQty = toDecimalValue(row.get("order_qty"));
            BigDecimal receivedQty = toDecimalValue(row.get("received_qty"));
            String status;
            if (receivedQty.compareTo(BigDecimal.ZERO) <= 0) status = "未入库";
            else if (receivedQty.compareTo(orderQty) >= 0) status = "已入库";
            else status = "部分入库";
            statusMap.put(Long.valueOf(idVal.toString()), status);
        }
        // 无明细的订单（聚合结果里没有该 order_id）按"未入库"处理
        for (PurchaseOrder o : records) {
            o.setInboundStatus(statusMap.getOrDefault(o.getOrderId(), "未入库"));
        }
    }

    /** 聚合结果数值兜底：空值按 0 处理 */
    private static BigDecimal toDecimalValue(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(v.toString());
    }

    @Override
    public PurchaseOrder getDetail(Long id) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        List<PurchaseOrderItem> items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", id).orderByAsc("line_no"));
        order.setItems(items);
        return order;
    }

    @Override @Transactional public void submit(Long id, String submitter) { submitForApprovalById(id, submitter); }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        // 兼容采购历史：草稿可直接"审批通过"（简化流程场景）
        int cur = BillStatusUtils.toNumber(order.getStatus());
        if (cur == BillStatusEnum.DRAFT.getCode()) {
            // 草稿直接审核：先提交，再由提交结果决定后续
            order.setItems(itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", id).orderByAsc("line_no")));
            submitForApproval(order, order.getCreatedBy());
            // 【2026-09-14 修复】原实现在 submitForApproval 后无条件再调 super.approve(id)：
            //   · 免审批（默认）：submit 已把状态置"已生效"，super.approve 再校验"只有待审核状态可审核"
            //     → 抛异常并整单回滚（前端 canApprove 在"草稿"也显示审核按钮，用户点"审核"必报错）；
            //   · 启用审批：submit 已生成审批待办，super.approve 又把单据置"已生效"
            //     → 遗留永远处理不掉的待办（审批人点通过时再报"只有待审核状态可审核"）。
            // 提交后直接返回：免审批=已生效；启用审批=等待审批人在审批中心处理。
            return;
        }
        super.approve(id, approver);
    }

    @Override @Transactional public void reject(Long id, String rejector, String reason) { super.reject(id, rejector, reason); }

    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (order.getIsClosed() != null && order.getIsClosed() == 1) {
            throw new BusinessException("已关闭/结案的订单不可重置草稿，请先反结案");
        }
        // 基类会校验"下游未生效"，这里采购还额外校验入库/开票勾稽
        List<PurchaseOrderItem> items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", id));
        for (PurchaseOrderItem item : items) {
            if (item.getReceivedQuantity() != null && item.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("订单已有入库记录，不可重置草稿");
            }
            if (item.getInvoicedQuantity() != null && item.getInvoicedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("订单已有开票记录，不可重置草稿");
            }
        }
        // 显式置空 approved_by/approved_at（兼容MyBatis NOT_NULL策略）
        super.reverseApprove(id, operator);
        UpdateWrapper<PurchaseOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", id)
                .set("approved_by", null)
                .set("approved_at", null)
                .set("updated_at", LocalDateTime.now())
                .set("updated_by", operator);
        update(uw);
    }

    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        // 旧接口：status→"已关闭"同时 is_closed=1 / close_status=1（保持双写兼容）
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        super.close(id, closer, reason);
        UpdateWrapper<PurchaseOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", id).set("is_closed", 1).set("status", "已关闭").set("bus_status", "已关闭");  // 双写：另写业务执行态
        update(uw);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(order.getStatus())) throw new BusinessException("只有草稿状态可取消，当前状态: " + order.getStatus());
        order.setStatus("已取消");
        if (reason != null && !reason.isBlank()) order.setRemark((order.getRemark() != null ? order.getRemark() + " | " : "") + "取消原因: " + reason);
        updateById(order);
        log.info("采购订单取消: {} 原因: {}", order.getOrderNo(), reason);
    }

    @Override
    @Transactional
    public boolean tryAutoClose(Long id) {
        PurchaseOrder order = getById(id);
        if (order == null) return false;
        if (!"已生效".equals(order.getStatus())) return false;
        if (order.getIsClosed() != null && order.getIsClosed() == 1) return false;
        List<PurchaseOrderItem> items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", id));
        if (items == null || items.isEmpty()) return false;
        for (PurchaseOrderItem item : items) {
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            BigDecimal received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal invoiced = item.getInvoicedQuantity() != null ? item.getInvoicedQuantity() : BigDecimal.ZERO;
            if (received.compareTo(qty) < 0) return false;
            if (invoiced.compareTo(qty) < 0) return false;
        }
        super.close(id, "系统", "全部入库且全部开票，自动结案");
        UpdateWrapper<PurchaseOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", id).set("is_closed", 1).set("status", "已关闭").set("bus_status", "已关闭");  // 双写：另写业务执行态
        update(uw);
        log.info("采购订单自动关闭: {}", order.getOrderNo());
        return true;
    }

    @Override @Transactional public void increaseReceivedQuantity(Long orderId, Long itemId, BigDecimal qty) {
        // 保持原实现
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("回写已入库数量必须大于0");
        PurchaseOrderItem item = itemMapper.selectById(itemId);
        if (item == null || !orderId.equals(item.getOrderId())) throw new BusinessException("采购订单行不存在或不属于该订单");
        PurchaseOrder order = getById(orderId);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (order.getIsClosed() != null && order.getIsClosed() == 1) throw new BusinessException("已关闭的订单不可再执行入库");
        if (CloseStatusEnum.isClosed(order.getCloseStatus())) throw new BusinessException("已结案的订单不可再执行入库");
        BigDecimal currentReceived = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : BigDecimal.ZERO;
        BigDecimal newReceived = currentReceived.add(qty);
        BigDecimal maxAllowed = item.getQuantity().multiply(BigDecimal.ONE.add(OVER_RECEIPT_RATIO.divide(HUNDRED, 6, RoundingMode.HALF_UP)));
        if (newReceived.compareTo(maxAllowed) > 0) throw new BusinessException("入库数量超过订单数量+允许超收比例，最大可入库: " + maxAllowed);
        item.setReceivedQuantity(newReceived);
        itemMapper.updateById(item);
        log.info("采购订单行回写已入库数量: 订单={} 行={} 回写={} 累计={}", order.getOrderNo(), item.getLineNo(), qty, newReceived);
        tryAutoClose(orderId);
    }

    @Override @Transactional public void increaseInvoicedQuantity(Long orderId, Long itemId, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("回写已开票数量必须大于0");
        PurchaseOrderItem item = itemMapper.selectById(itemId);
        if (item == null || !orderId.equals(item.getOrderId())) throw new BusinessException("采购订单行不存在或不属于该订单");
        PurchaseOrder order = getById(orderId);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (order.getIsClosed() != null && order.getIsClosed() == 1) throw new BusinessException("已关闭的订单不可再执行开票");
        if (CloseStatusEnum.isClosed(order.getCloseStatus())) throw new BusinessException("已结案的订单不可再执行开票");
        BigDecimal currentInvoiced = item.getInvoicedQuantity() != null ? item.getInvoicedQuantity() : BigDecimal.ZERO;
        BigDecimal newInvoiced = currentInvoiced.add(qty);
        if (newInvoiced.compareTo(item.getQuantity()) > 0) throw new BusinessException("开票数量超过订单数量，最大可开票: " + item.getQuantity());
        item.setInvoicedQuantity(newInvoiced);
        itemMapper.updateById(item);
        log.info("采购订单行回写已开票数量: 订单={} 行={} 回写={} 累计={}", order.getOrderNo(), item.getLineNo(), qty, newInvoiced);
        tryAutoClose(orderId);
    }

    @Override
    public List<Map<String, Object>> queryItemExecutionStatus(Long orderId) {
        PurchaseOrder order = getById(orderId);
        if (order == null) throw new BusinessException("采购订单不存在");
        List<PurchaseOrderItem> items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", orderId).orderByAsc("line_no"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("lineNo", item.getLineNo());
            row.put("materialCode", item.getMaterialCode());
            row.put("materialName", item.getMaterialName());
            row.put("quantity", item.getQuantity());
            BigDecimal received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal invoiced = item.getInvoicedQuantity() != null ? item.getInvoicedQuantity() : BigDecimal.ZERO;
            row.put("receivedQuantity", received);
            row.put("invoicedQuantity", invoiced);
            boolean executed = received.compareTo(item.getQuantity()) >= 0;
            boolean invoicedDone = invoiced.compareTo(item.getQuantity()) >= 0;
            row.put("executionStatus", executed ? "执行完毕" : "未完成");
            row.put("invoiceStatus", invoicedDone ? "开票完毕" : "未开票");
            row.put("fullyCompleted", executed && invoicedDone);
            result.add(row);
        }
        return result;
    }

    @Override @Transactional public void closeOrder(Long id, String closer) { super.close(id, closer, "手动结案"); PurchaseOrder o = getById(id); if (o != null) { UpdateWrapper<PurchaseOrder> uw = new UpdateWrapper<>(); uw.eq("order_id", id).set("is_closed", 1); update(uw); } }
    @Override @Transactional public void reopenOrder(Long id, String closer) { super.reopen(id, closer); UpdateWrapper<PurchaseOrder> uw = new UpdateWrapper<>(); uw.eq("order_id", id).set("is_closed", 0); update(uw); }

    @Override @Transactional public  void voidBill(Long id, String operator, String reason) { super.voidBill(id, operator, reason); }
    @Override @Transactional public void unvoidBill(Long id, String operator) { super.unvoidBill(id, operator); }
    @Override @Transactional public void deleteBill(Long id) { super.deleteBill(id); }

    @Override
    @Transactional
    public void updateTurnedQuantity(Long orderId, Long orderItemId, BigDecimal quantity, String turnType) {
        PurchaseOrderItem item = itemMapper.selectById(orderItemId);
        if (item == null) throw new BusinessException("采购订单明细不存在");
        BigDecimal currentQty = quantity != null ? quantity : BigDecimal.ZERO;
        switch (turnType) {
            case "input":
                item.setTurnedInputQuantity(nvl(item.getTurnedInputQuantity()).add(currentQty));
                break;
            case "return":
                item.setTurnedReturnQuantity(nvl(item.getTurnedReturnQuantity()).add(currentQty));
                break;
            default: throw new BusinessException("未知的转单类型: " + turnType);
        }
        BigDecimal totalTurned = nvl(item.getTurnedInputQuantity()).add(nvl(item.getTurnedReturnQuantity()));
        BigDecimal itemQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        if (totalTurned.compareTo(itemQty) >= 0) item.setDetailCloseStatus(1);
        itemMapper.updateById(item);
        checkAndUpdateOrderCloseStatus(orderId);
        log.info("采购订单明细勾稽更新: 订单ID={}, 明细ID={}, 类型={}, 数量={}", orderId, orderItemId, turnType, currentQty);
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private void checkAndUpdateOrderCloseStatus(Long orderId) {
        List<PurchaseOrderItem> items = itemMapper.selectList(new QueryWrapper<PurchaseOrderItem>().eq("order_id", orderId));
        boolean allClosed = !items.isEmpty() && items.stream().allMatch(it -> it.getDetailCloseStatus() != null && it.getDetailCloseStatus() == 1);
        PurchaseOrder order = getById(orderId);
        if (order != null) {
            order.setCloseStatus(allClosed ? 1 : 0);
            updateById(order);
        }
    }

    @Override
    public String generateOrderNo() {
        String docType = "PURCHASE_ORDER";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
            QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
            wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
            PurchaseOrder last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getOrderNo() != null) {
                try { seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
            }
            return prefix + String.format("%04d", seq);
        });
    }

    private void validateOrder(PurchaseOrder order, List<PurchaseOrderItem> items) {
        // 复用到严格提交
        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                PurchaseOrderItem item = items.get(i);
                int lineNo = i + 1;
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("第" + lineNo + "行数量必须大于0");
                }
                if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("第" + lineNo + "行单价不可为负数");
                }
                sysParamValidator.validateAllowZeroPrice("purchase.order.allow.zero.price", "采购订单", item.getUnitPrice(), lineNo);
                if (item.getDeliveryDate() != null && order.getOrderDate() != null
                        && order.getOrderDate().isAfter(item.getDeliveryDate())) {
                    throw new BusinessException("订单日期不可晚于第" + lineNo + "行的交货日期");
                }
            }
        }
        if ("采购申请单".equals(order.getSourceBillType())) {
            if (order.getSupplierId() == null) throw new BusinessException("从采购申请单生成的订单必须指定供应商");
        }
        boolean supplierCreditCheck = sysConfigService.getBooleanValue("purchase.supplier.credit.check", true);
        if (supplierCreditCheck && order.getSupplierId() != null) {
            log.debug("供应商信用检查开关已开启, 供应商ID: {} (当前无信用额度基准, 跳过拦截)", order.getSupplierId());
        }
        int priceTolerance = sysConfigService.getIntValue("purchase.price.tolerance", 5);
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                PurchaseOrderItem item = items.get(i);
                int lineNo = i + 1;
                if (item.getMaterialId() != null && item.getUnitPrice() != null) {
                    Material m = materialService.getById(item.getMaterialId());
                    if (m != null && m.getPurchasePrice() != null && m.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal diff = item.getUnitPrice().subtract(m.getPurchasePrice())
                                .divide(m.getPurchasePrice(), 4, RoundingMode.HALF_UP).multiply(HUNDRED);
                        if (diff.abs().compareTo(new BigDecimal(priceTolerance)) > 0) {
                            throw new BusinessException(String.format(
                                    "【设置中心-价格容差】第%d行单价 %.2f 超出采购基准价 %.2f 的容差范围 ±%d%%",
                                    lineNo, item.getUnitPrice(), m.getPurchasePrice(), priceTolerance));
                        }
                    }
                }
                if (kgwRules.isPurchasePriceStrategyEnabled()
                        && item.getMaterialId() != null && item.getUnitPrice() != null) {
                    // 2026-09-20 价格策略修复：优先按"价格策略价"校验
                    //（此前只拿物料档案采购价硬比，策略表里的数据完全没参与 → 价格策略"录了不生效"）
                    BigDecimal strategyPrice = purchasePriceStrategyService.tryMatchPrice(
                            order.getSupplierId(), item.getMaterialId(), item.getQuantity(), null);
                    if (strategyPrice != null) {
                        if (item.getUnitPrice().compareTo(strategyPrice) > 0) {
                            throw new BusinessException(String.format(
                                    "【设置中心-采购价格策略】第%d行单价 %.2f 高于价格策略价 %.2f，禁止提交",
                                    lineNo, item.getUnitPrice(), strategyPrice));
                        }
                    } else {
                        // 未命中策略：保持原有"高于采购基准价"兜底，行为与修复前一致
                        Material m = materialService.getById(item.getMaterialId());
                        if (m != null && m.getPurchasePrice() != null && m.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0
                                && item.getUnitPrice().compareTo(m.getPurchasePrice()) > 0) {
                            throw new BusinessException(String.format(
                                    "【设置中心-采购价格策略】第%d行单价 %.2f 高于采购基准价 %.2f，禁止提交",
                                    lineNo, item.getUnitPrice(), m.getPurchasePrice()));
                        }
                    }
                }
            }
        }
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

    /** 逐行计算（行级口径）：优先按行级 taxType（1=含税价 3=不计税 其它=不含税价），未传时回退表头口径 */
    private void calculateItemAmount(PurchaseOrderItem item, int headerTaxType) {
        BigDecimal unitPrice = nvl(item.getUnitPrice());
        BigDecimal quantity = nvl(item.getQuantity());
        BigDecimal taxRate = nvl(item.getTaxRate());
        BigDecimal discountRate = nvl(item.getDiscountRate());
        int taxType = item.getTaxType() != null ? item.getTaxType() : headerTaxType;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTaxRate(taxRate);
        item.setTaxType(taxType);
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

    private void recalculateOrder(PurchaseOrder order, List<PurchaseOrderItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        if (items != null) {
            for (PurchaseOrderItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()).add(nvl(item.getDiscountAmount())));
                totalDiscount = totalDiscount.add(nvl(item.getDiscountAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
                totalNet = totalNet.add(nvl(item.getNetAmount()));
            }
        }
        order.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        order.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        order.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        order.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
        sysParamValidator.adjustTaxIfDisabled("采购订单", order::setTaxAmount, order::setNetAmount, order.getTotalAmount().subtract(order.getDiscountAmount()), order.getTaxAmount());
        BigDecimal rate = order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE;
        order.setBaseTotalAmount(order.getTotalAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        order.setBaseTaxAmount(order.getTaxAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        order.setBaseNetAmount(order.getNetAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 下推到采购入库单: 已生效的订单 → 生成入库单(草稿) */
    @Override
    @Transactional
    public PurchaseInbound pushToInbound(Long id, String operator) {
        PurchaseOrder order = getDetail(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }
        if (!"已生效".equals(order.getApprovalStatus())) {
            throw new BusinessException("只有已生效的采购订单才能下推");
        }
        List<PurchaseOrderItem> orderItems = order.getItems();
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException("采购订单无明细，无法下推");
        }

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setReceiptNo(purchaseInboundService.generateInboundNo());
        inbound.setInDate(LocalDate.now());
        inbound.setSupplierId(order.getSupplierId());
        inbound.setSupplierName(order.getSupplierName());
        inbound.setDeptId(order.getDepartmentId());
        inbound.setDeptName(order.getDeptName());
        inbound.setBuyerId(order.getBuyerId());
        inbound.setBuyerName(order.getBuyerName());
        inbound.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        // warehouse_id 在 purchase_receipt 表中为 NOT NULL；按优先级取仓库，禁止硬编码 1L：
        // 1) 采购订单明细 stock_id（取第一行非空值，多行不一致时告警）；2) 采购订单表头无仓库字段，跳过；
        // 3) 取 warehouse 表 status='启用' 中 stock_id 最小的仓库；4) 都取不到则抛业务异常，不静默使用 1
        Long stockId = resolveInboundStockId(orderItems);
        log.info("采购订单 {} 下推入库单使用仓库: {}", order.getOrderNo(), stockId);
        inbound.setStockId(stockId);
        inbound.setSourceType("采购订单");
        inbound.setSourceBillNo(order.getOrderNo());
        inbound.setRemark("由采购订单 " + order.getOrderNo() + " 下推生成");
        inbound.setStatus("草稿");
        // 下推统一口径（2026-11-28）：下推只生成草稿，审核动作由用户单独完成
        inbound.setApprovalStatus("草稿");

        List<PurchaseInboundItem> inboundItems = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseOrderItem oi : orderItems) {
            PurchaseInboundItem ii = new PurchaseInboundItem();
            ii.setLineNo(lineNo++);
            ii.setMaterialId(oi.getMaterialId());
            ii.setMaterialCode(oi.getMaterialCode());
            ii.setMaterialName(oi.getMaterialName());
            ii.setModel(oi.getSpecification());
            ii.setUnitId(oi.getUnitId());
            ii.setQuantity(oi.getQuantity());
            ii.setUnitPrice(oi.getUnitPrice() != null ? oi.getUnitPrice() : BigDecimal.ZERO);
            ii.setAmount(oi.getAmount() != null ? oi.getAmount() : BigDecimal.ZERO);
            // 说明：purchase_receipt_item 没有"仓位"列（仓位只记在表头 purchase_receipt.stock_id），
            // 故明细不设置仓位；库存归属由表头仓库决定
            ii.setSourceBillNo(order.getOrderNo());
            ii.setSourceRowNo(oi.getLineNo());
            ii.setSourceId(order.getOrderId());
            ii.setSourceItemId(oi.getOrderItemId());
            ii.setSourceType("采购订单");
            ii.setBatchNo("");
            ii.setRemark(oi.getRemark());
            inboundItems.add(ii);
        }

        purchaseInboundService.saveInboundWithItems(inbound, inboundItems);
        return inbound;
    }

    /**
     * 解析下推入库单使用的仓库ID：
     * 1) 采购订单明细 stock_id，取第一行非空值；若多行取值不一致，告警提示（取第一行非空值）；
     * 2) 采购订单表头无仓库字段，跳过；
     * 3) 取 warehouse 表 status='启用' 中 stock_id 最小的仓库（该表无 is_default 字段）；
     * 4) 仍取不到则抛业务异常，绝不静默使用 1L。
     */
    private Long resolveInboundStockId(List<PurchaseOrderItem> orderItems) {
        Long itemStockId = null;
        for (PurchaseOrderItem it : orderItems) {
            if (it.getStockId() == null) {
                continue;
            }
            if (itemStockId == null) {
                itemStockId = it.getStockId();
            } else if (!itemStockId.equals(it.getStockId())) {
                log.warn("采购订单明细仓库(stock_id)存在多值，取第一行非空值 {} 作为入库单仓库", itemStockId);
                break;
            }
        }
        if (itemStockId != null) {
            return itemStockId;
        }
        Warehouse def = warehouseService.getOne(new QueryWrapper<Warehouse>()
                .eq("status", "启用").orderByAsc("stock_id").last("LIMIT 1"));
        if (def != null && def.getStockId() != null) {
            return def.getStockId();
        }
        throw new BusinessException("未找到可用仓库，请先在采购订单明细上指定仓库");
    }
}
