package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.entity.PaymentTerm;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.PaymentTermService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.credit.CreditGuardService;
import com.erp.config.BusinessException;
import com.erp.config.KgwBusinessRules;
import com.erp.config.SysParamValidator;
import com.erp.config.service.SysConfigService;
import com.erp.crm.service.OpportunityService;
import com.erp.finance.prepayment.sale.service.SalePreReceiptService;
import com.erp.finance.service.FinanceService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.service.PurchaseOrderService;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.entity.SaleOrderSubstitutePlan;
import com.erp.sale.entity.SaleQuote;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.entity.SaleReturnItem;
import com.erp.sale.mapper.SaleDeliveryItemMapper;
import com.erp.sale.mapper.SaleDeliveryMapper;
import com.erp.sale.mapper.SaleReturnItemMapper;
import com.erp.sale.mapper.SaleReturnMapper;
import com.erp.sale.entity.SaleQuoteItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.sale.mapper.SaleQuoteItemMapper;
import com.erp.sale.mapper.SaleQuoteMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;
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

@Slf4j
@Service
@RequiredArgsConstructor

public class SaleOrderServiceImpl
        extends AbstractBillService<SaleOrderMapper, SaleOrder>
        implements SaleOrderService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义 一期）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = sale_order 列名（代码常量，防注入）+ 过滤形态。字段解释见 BillHeaderFilterSupport。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("orderNo", new BillHeaderFilterSupport.Spec("order_no", BillHeaderFilterSupport.Kind.TEXT));
        f.put("customerNo", new BillHeaderFilterSupport.Spec("customer_no", BillHeaderFilterSupport.Kind.TEXT));
        f.put("customerShortName", new BillHeaderFilterSupport.Spec("customer_short_name", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contact_person", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contact_phone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("receiveAddress", new BillHeaderFilterSupport.Spec("receive_address", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("projectNo", new BillHeaderFilterSupport.Spec("project_no", BillHeaderFilterSupport.Kind.TEXT));
        f.put("customerOrderNo", new BillHeaderFilterSupport.Spec("customer_order_no", BillHeaderFilterSupport.Kind.TEXT));
        f.put("businessType", new BillHeaderFilterSupport.Spec("business_type", BillHeaderFilterSupport.Kind.TEXT));
        f.put("quoteNo", new BillHeaderFilterSupport.Spec("quote_no", BillHeaderFilterSupport.Kind.TEXT));
        f.put("opportunityNo", new BillHeaderFilterSupport.Spec("opportunity_no", BillHeaderFilterSupport.Kind.TEXT));
        f.put("orderSourceBillNo", new BillHeaderFilterSupport.Spec("source_bill_no", BillHeaderFilterSupport.Kind.TEXT));
        f.put("accountName", new BillHeaderFilterSupport.Spec("account_name", BillHeaderFilterSupport.Kind.TEXT));
        f.put("transportMethod", new BillHeaderFilterSupport.Spec("transport_method", BillHeaderFilterSupport.Kind.TEXT));
        f.put("paymentMethod", new BillHeaderFilterSupport.Spec("payment_method", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceType", new BillHeaderFilterSupport.Spec("invoice_type", BillHeaderFilterSupport.Kind.TEXT));
        f.put("logisticsCompany", new BillHeaderFilterSupport.Spec("logistics_company", BillHeaderFilterSupport.Kind.TEXT));
        f.put("logisticsNo", new BillHeaderFilterSupport.Spec("logistics_no", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customer_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("salerName", new BillHeaderFilterSupport.Spec("saler_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("department_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currency_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("priceLevelName", new BillHeaderFilterSupport.Spec("price_level_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("orderDate", new BillHeaderFilterSupport.Spec("order_date", BillHeaderFilterSupport.Kind.DATE));
        f.put("deliveryDate", new BillHeaderFilterSupport.Spec("delivery_date", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("total_amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discount_amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("tax_amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("net_amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discount_rate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchange_rate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("depositAmount", new BillHeaderFilterSupport.Spec("deposit_amount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.register("saleOrder", f);
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
            "business_type",
            "close_status",
            "closed_at",
            "closed_by",
            "closed_reason",
            "contact_person",
            "contact_phone",
            "created_at",
            "created_by",
            "currency_id",
            "customer_id",
            "customer_no",
            "customer_order_no",
            "customer_short_name",
            "delivery_date",
            "department_id",
            "deposit_amount",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "invoice_type",
            "is_consignment",
            "is_invoiced",
            "is_sample",
            "is_tax",
            "line_no",
            "logistics_company",
            "logistics_no",
            "net_amount",
            "opportunity_id",
            "opportunity_no",
            "order_date",
            "order_id",
            "order_no",
            "org_code",
            "payment_method",
            "payment_term_id",
            "picker_id",
            "price_level_id",
            "project_no",
            "quote_id",
            "quote_no",
            "receive_address",
            "remark",
            "sale_order",
            "saler_id",
            "source_bill_no",
            "source_bill_type",
            "status",
            "tax_amount",
            "tax_calc_method",
            "total_amount",
            "transport_method",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final SaleOrderItemMapper itemMapper;
    private final SaleQuoteItemMapper saleQuoteItemMapper;
    private final SaleQuoteMapper saleQuoteMapper;
    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final PaymentTermService paymentTermService;
    private final MaterialService materialService;
    private final com.erp.basedata.mapper.MaterialCategoryMapper materialCategoryMapper;
    private final UnitOfMeasureService unitOfMeasureService;
    private final MaterialCostService materialCostService;
    private final KgwBusinessRules kgwRules;
    private final SysParamValidator sysParamValidator;
    private final SaleOverQtyGuardService saleOverQtyGuardService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final BillRelationService billRelationService;
    private final PurchaseOrderService purchaseOrderService;
    private final WarehouseService warehouseService;

    @Autowired
    @Lazy
    private SaleDeliveryService saleDeliveryService;

    /** 替换货（2026-11-17）：结案放弃待退回、按钮显隐、明细替换信息 */
    @Autowired
    @Lazy
    private SaleExchangeService saleExchangeService;

    @Autowired
    private SaleDeliveryItemMapper saleDeliveryItemMapper;
    @Autowired
    private SaleDeliveryMapper saleDeliveryMapper;
    @Autowired
    private SaleReturnItemMapper saleReturnItemMapper;
    @Autowired
    private SaleReturnMapper saleReturnMapper;

    /** 替换发货指示（2026-11-27）：订单详情回填、反审/作废级联取消 */
    @Autowired
    @Lazy
    private SaleOrderSubstitutePlanService saleOrderSubstitutePlanService;

    /**
     * 销售价格策略：提交时按"策略价"校验（2026-09-20 价格策略修复）。
     * @Lazy 打破潜在构造期环（价格策略侧依赖通用级联删除组件）。
     */
    @Autowired
    @Lazy
    private com.erp.price.sale.service.SalePriceStrategyService salePriceStrategyService;

    @Autowired
    @Lazy
    private SalePreReceiptService salePreReceiptService;

    @Autowired
    @Lazy
    private OpportunityService opportunityService;

    @Autowired
    @Lazy
    private FinanceService financeService;

    @Autowired
    @Lazy
    private CreditGuardService creditGuardService;

    /** 自动下推单据（2026-11-08）：订单生效后按设置中心开关自动生成已生效销售合同 */
    @Lazy
    @Autowired
    private SaleAutoPushService saleAutoPushService;

    /** 销售合同服务（2026-11-09）：拦截"由销售订单自动生成的合同再次下推订单" */
    @Lazy
    @Autowired
    private SaleContractService saleContractService;

    @Autowired
    private SysConfigService sysConfigService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    // ================= AbstractBillService 钩子实现 =================
    @Override protected String getBillTypeLabel() { return "销售订单"; }
    @Override protected String getApprovalBillType() { return "SALE_ORDER"; }
    @Override protected String getBillNo(SaleOrder b) { return b.getOrderNo(); }
    @Override protected Object getId(SaleOrder b) { return b.getOrderId(); }
    @Override protected String getStatus(SaleOrder b) { return b.getStatus(); }
    @Override protected void setStatus(SaleOrder b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleOrder b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleOrder b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleOrder b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleOrder b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SaleOrder b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleOrder b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleOrder b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SaleOrder b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleOrder b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleOrder b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleOrder b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleOrder b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SaleOrder b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：调用通用 BillRelationService */
    @Override
    public List<String> listApprovedDownstreamBills(SaleOrder bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getOrderNo());
    }

    /** 审核通过影响：刷新avg_cost + CRM商机成交 + 生成应收（沿用原 approve() 内逻辑）*/
    @Override
    public void onApproved(SaleOrder order) {
        List<SaleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", order.getOrderId()).orderByAsc("line_no"));
        if (items != null) {
            for (SaleOrderItem item : items) {
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                    itemMapper.updateById(item);
                }
            }
        }
        if (order.getCustomerId() != null && order.getOrderDate() != null) {
            try { customerService.updateLastOrderDate(order.getCustomerId(), order.getOrderDate()); }
            catch (Exception e) { log.warn("更新客户最后开单日期失败，客户ID: {}", order.getCustomerId(), e); }
        }
        if (order.getOpportunityId() != null) {
            try {
                opportunityService.markWonBySaleOrder(order.getOpportunityId(),
                        order.getOrderId(), order.getOrderNo(), order.getTotalAmount());
            } catch (Exception e) { log.warn("回写商机成交失败，订单: {}", order.getOrderNo(), e); }
        } else if (order.getCustomerId() != null) {
            // 无商机关联的订单：审核通过同样将客户升级为正式客户（2026-09-06 客户档案域准入：
            // 私海培育客户下过单即推送进入客户档案；商机关联分支已在 markWonBySaleOrder 内升级）
            try {
                Customer c = customerService.getById(order.getCustomerId());
                if (c != null && !"正式客户".equals(c.getCrmStatus())) {
                    customerService.update(new UpdateWrapper<Customer>()
                            .eq("customer_id", order.getCustomerId())
                            .set("crm_status", "正式客户"));
                    log.info("销售订单审核 → 客户升级为正式客户: customerId={}, customerName={}",
                            order.getCustomerId(), c.getCustomerName());
                }
            } catch (Exception e) { log.warn("订单审核升级客户为正式客户失败，订单: {}", order.getOrderNo(), e); }
        }
        try {
            // 收款单生成点由配置 finance.ar.source_bill_type（显示名：收款单生成方式）决定；
            // 【2026-10-19 设计图 v3】系统不生成应收单：命中配置时直接生成「收款单草稿」，财务核对后提交生效。
            // 可选值: none(默认,不生成) / sale_order(订单审核) / sale_delivery(出库审核) / sale_invoice(发票审核)
            String arSource = sysConfigService.getValue("finance.ar.source_bill_type", "none");
            if ("sale_order".equals(arSource)) {
                financeService.createReceiptDraftFromSource("销售订单", order.getOrderId());
            }
        } catch (Exception e) { log.warn("订单审核生成收款单草稿失败，订单: {}", order.getOrderNo(), e); }
        // 定金联动：定金>0 时按订单自动生成一张草稿销售预收款单（幂等，同事务，失败回滚审批）
        if (order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            if (salePreReceiptService == null) {
                log.warn("定金联动未就绪，跳过自动生成销售预收款单，订单: {}", order.getOrderNo());
            } else {
                salePreReceiptService.createFromSaleOrder(order);
            }
        }
        // 自动下推单据（2026-11-08）：设置中心开关开启时，订单生效后自动生成一份已生效的销售合同。
        // 生成失败抛业务异常 → 订单生效整体回滚，避免出现"订单生效了但下游没有"。
        saleAutoPushService.autoPushOrderToContract(order, order.getApprovedBy());
    }

    /** 重置草稿影响（onApproved反向） */
    @Override public void onReverseApproved(SaleOrder bill) {
        // 定金联动清理：若定金自动生成的预收单仍是草稿则随订单反审一并删除；
        // 若已生效(非草稿)则由 rollbackAutoByOrder 抛业务异常阻止反审
        if (bill != null && bill.getOrderId() != null && salePreReceiptService != null) {
            try {
                int n = salePreReceiptService.rollbackAutoByOrder(bill.getOrderId());
                if (n > 0) log.info("销售订单重置草稿联动删除自动预收草稿 {} 张，订单: {}", n, bill.getOrderNo());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("销售订单重置草稿联动清理自动预收单失败，订单: {}", bill.getOrderNo(), e);
            }
        }
        // 替换发货指示随订单反审核级联取消（此时无已生效下游，指示尚未执行）
        if (bill != null && bill.getOrderId() != null) {
            saleOrderSubstitutePlanService.cancelByOrder(bill.getOrderId(), "订单重置草稿", "system");
        }
        log.info("[销售订单重置草稿影响] 订单: {}", bill.getOrderNo());
    }

    /** 订单作废：替换发货指示级联取消 + 清理定金自动生成的预收款草稿（已生效则阻止作废） */
    @Override
    @Transactional
    public void onVoided(SaleOrder bill) {
        if (bill != null && bill.getOrderId() != null) {
            saleOrderSubstitutePlanService.cancelByOrder(bill.getOrderId(), "订单作废", "system");
            if (salePreReceiptService != null) {
                salePreReceiptService.rollbackAutoByOrder(bill.getOrderId());
            }
        }
    }

    /** 反作废（恢复为已生效）：重新应用审核影响中可恢复的部分（定金自动预收款草稿） */
    @Override
    @Transactional
    public void onUnvoided(SaleOrder bill) {
        if (bill == null || bill.getOrderId() == null) return;
        if (salePreReceiptService != null) {
            salePreReceiptService.createFromSaleOrder(bill);
        }
    }

    /** 提交审核前严格校验（原 submitForApproval 内部的校验 + 设置中心KGW规则 + 设置中心校验） */
    @Override
    protected void validateStrictSubmit(SaleOrder bill) {
        // 按用户要求：保存草稿不校验，但"提交审核"必须严格
        if (bill.getCustomerId() == null) {
            throw new BusinessException("客户为空，请选择客户后再提交");
        }
        Customer customer = customerService.getById(bill.getCustomerId());
        if (customer == null) {
            throw new BusinessException("关联客户不存在(customerId=" + bill.getCustomerId() + ")，请重新选择客户");
        }
        if (bill.getSalerId() != null) {
            Employee emp = employeeService.getById(bill.getSalerId());
            if (emp == null) {
                throw new BusinessException("关联销售员不存在(employeeId=" + bill.getSalerId() + ")，请重新选择销售员");
            }
        }
        if (bill.getCurrencyId() == null) {
            throw new BusinessException("币种为空，请选择币种后再提交");
        }
        Currency cur = currencyService.getById(bill.getCurrencyId());
        if (cur == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + bill.getCurrencyId() + ")，请重新选择币种");
        }

        // 来源单号校验（提交才生效）
        sysParamValidator.validateRequireSource("sale.order.require.source", "销售订单", bill.getSourceBillNo());

        List<SaleOrderItem> items;
        if (bill.getItems() != null && !bill.getItems().isEmpty()) {
            items = bill.getItems();
        } else if (bill.getOrderId() != null) {
            items = itemMapper.selectList(new QueryWrapper<SaleOrderItem>().eq("order_id", bill.getOrderId()).orderByAsc("line_no"));
        } else {
            items = List.of();
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("销售订单没有明细，请至少添加一行物料后再提交");
        }
        for (int i = 0; i < items.size(); i++) {
            SaleOrderItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行未选择物料，请选择物料后再提交");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            if (item.getUnitId() != null) {
                UnitOfMeasure uom = unitOfMeasureService.getById(item.getUnitId());
                if (uom == null) {
                    throw new BusinessException("第" + lineNo + "行计量单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
                }
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                String matCode = mat != null ? mat.getMaterialCode() : "物料";
                throw new BusinessException("第" + lineNo + "行[" + matCode + "]数量必须大于 0");
            }
        }

        // KGW 设置中心业务规则（提交才触发，保存草稿不触发）
        applyKgwBusinessRules(bill, items);
        // 设置中心：超过来源单数量下单（不控制 / 预警提示 / 不允许），提交时按三态策略处理
        saleOverQtyGuardService.validateSaleOrder(bill, items);
    }

    // ================= 原有 + 新增接口实现（委托基类） =================

    /**
     * 防循环：由销售订单自动生成的销售合同，不允许再作为来源下推生成新的销售订单。
     * 该合同本身来源类型就是「销售订单」，再推订单会形成 订单→合同→订单 循环。
     */
    private void assertSourceContractNotAutoGenerated(SaleOrder order) {
        if (order == null) return;
        String srcType = order.getSourceBillType();
        String srcNo = order.getSourceBillNo();
        if (srcType == null || !srcType.contains("销售合同") || srcNo == null || srcNo.isBlank()) return;
        try {
            SaleContract sc = saleContractService.getOne(new QueryWrapper<SaleContract>()
                    .eq("contract_no", srcNo).last("LIMIT 1"));
            if (sc != null && sc.getSourceBillType() != null && sc.getSourceBillType().contains("销售订单")) {
                throw new BusinessException("销售合同[" + srcNo + "]由销售订单自动生成，不能再次下推生成销售订单");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("校验来源合同是否由订单自动生成失败，放行: {}", e.getMessage());
        }
    }

    /**
     * 保存（宽松草稿模式） — 按用户要求：保存=存为草稿，必填项允许为空，不做业务校验。
     * 只做：单号自动生成、金额自动计算、保存主从。其余校验放到"提交审核"才执行。
     */
    @Override
    @Transactional
    public void saveOrderWithItems(SaleOrder order, List<SaleOrderItem> items) {
        // 防循环：由销售订单自动生成的销售合同，不允许再作为来源下推生成销售订单
        assertSourceContractNotAutoGenerated(order);
        boolean isNew = order.getOrderId() == null;
        // 非新建：允许编辑的前提 —— 必须草稿/待审核态（已生效必须反审）
        if (!isNew) {
            SaleOrder existing = getById(order.getOrderId());
            if (existing != null) {
                if (BillStatusUtils.isFinalized(existing.getStatus(), existing.getCloseStatus())) {
                    throw new BusinessException(getBillTypeLabel() + "[" + existing.getOrderNo() + "]已完结，不能修改");
                }
                int num = BillStatusUtils.toNumber(existing.getStatus());
                if (num == BillStatusEnum.APPROVED.getCode()) {
                    throw new BusinessException("已生效的" + getBillTypeLabel() + "不可直接修改，请先重置草稿");
                }
                // === 设置中心：锁单据日期校验 ===
                sysParamValidator.validateLockBillDate(getBillTypeLabel(), existing.getOrderDate(), order.getOrderDate(), existing.getStatus());
            }
        }

        // 单号生成（仅新建时；更新时不重新取号，否则每次保存都会吃掉一个序号）
        if (isNew && (order.getOrderNo() == null || order.getOrderNo().isBlank())) {
            order.setOrderNo(generateOrderNo());
        }
        // === 设置中心：单据号查重 ===
        QueryWrapper<SaleOrder> dupQw = new QueryWrapper<SaleOrder>().eq("order_no", order.getOrderNo());
        if (!isNew) dupQw.ne("order_id", order.getOrderId());
        sysParamValidator.validateBillNoDuplicate(order.getOrderNo(), getBillTypeLabel(), count(dupQw) > 0);

        // 宽松：默认值填充，不做业务校验
        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (order.getStatus() == null || order.getStatus().isBlank()) order.setStatus("草稿");
        if (order.getApprovalStatus() == null || order.getApprovalStatus().isBlank()
                || ("草稿".equals(order.getStatus()) && "待审核".equals(order.getApprovalStatus()))) {
            order.setApprovalStatus("草稿");
        }
        if (order.getExchangeRate() == null) order.setExchangeRate(BigDecimal.ONE);
        if (order.getDiscountRate() == null) order.setDiscountRate(BigDecimal.ZERO);
        if (order.getDiscountAmount() == null) order.setDiscountAmount(BigDecimal.ZERO);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        if (order.getTaxAmount() == null) order.setTaxAmount(BigDecimal.ZERO);
        if (order.getNetAmount() == null) order.setNetAmount(BigDecimal.ZERO);
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());
        if (order.getCloseStatus() == null) order.setCloseStatus(0);

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SaleOrderItem> validItems = new ArrayList<>();
            for (SaleOrderItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 宽松：允许空明细；允许金额为0
        recalculateOrder(order, items);

        // 保存主表（调用基类 saveDraft，设置默认状态/结案标志）
        if (isNew) {
            saveDraft(order);
        } else {
            updateById(order);
        }
        // 保存明细
        if (items != null) {
            itemMapper.delete(new QueryWrapper<SaleOrderItem>().eq("order_id", order.getOrderId()));
            int lineNo = 1;
            for (SaleOrderItem item : items) {
                item.setOrderItemId(null);
                item.setOrderId(order.getOrderId());
                item.setLineNo(lineNo);
                if (item.getDeliveredQuantity() == null) item.setDeliveredQuantity(BigDecimal.ZERO);
                calculateItemAmount(item);
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                itemMapper.insert(item);
                lineNo += 1;
            }
        }
        updateQuoteTurnedQty(order, items);
        log.info("销售订单保存草稿: {} 状态: {} 合计: {}", order.getOrderNo(), order.getStatus(), order.getTotalAmount());
    }

    // 原有方法：按ID提交（load明细后调用基类 submitForApproval(bill) — 会执行严格校验）
    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("销售订单不存在");
        List<SaleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", id).orderByAsc("line_no"));
        order.setItems(items);
        submitForApproval(order, order.getCreatedBy());
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        super.approve(id, approver);
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        // 自动下推生成的订单与人工建单走同一套状态机：重置草稿不做额外拦截，
        // 下游引用校验（已生效的销售出库/发票/工单等）由基类统一执行。
        super.reverseApprove(id, operator);
        // 显式置空 approved_by/approved_at：MyBatis-Plus 默认忽略 null，基类 setApprovedBy(null)+updateById 不会清旧值。
        // 否则「人工审核通过 → 重置草稿 → 免审批再提交」后仍显示上一次的真人审核人（与采购订单等其余单据同一口径）。
        UpdateWrapper<SaleOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", id)
                .set("approved_by", null)
                .set("approved_at", null);
        update(uw);
    }

    /** 兼容旧 close：status→"已关闭"的旧语义不再用，改为内部走 close→close_status=1 结案 */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        super.close(id, closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"草稿".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + order.getStatus());
        }
        order.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            order.setRemark((order.getRemark() != null ? order.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(order);
        log.info("销售订单取消: {} 原因: {}", order.getOrderNo(), reason);
    }

    @Override
    @Transactional
    public void closeOrder(Long id, String closer) {
        // 【2026-11-17 替换货】人工结案 = 业务确认"剩下的不需要了"：允许结案，放弃待退回并留痕
        try {
            saleExchangeService.waivePendingReturns(id, closer, "人工结案放弃待退回");
        } catch (Exception e) {
            log.warn("人工结案放弃待退回失败 orderId={}: {}", id, e.getMessage());
        }
        // 替换发货指示：结案后不再执行，取消剩余并留痕
        try {
            saleOrderSubstitutePlanService.cancelByOrder(id, "订单结案", closer);
        } catch (Exception e) {
            log.warn("人工结案取消替换发货指示失败 orderId={}: {}", id, e.getMessage());
        }
        super.close(id, closer, "手动结案");
    }

    @Override
    @Transactional
    public void reopenOrder(Long id, String closer) {
        super.reopen(id, closer);
    }

    @Override @Transactional public void voidBill(Long id, String operator, String reason) { super.voidBill(id, operator, reason); }
    @Override @Transactional public void unvoidBill(Long id, String operator) { super.unvoidBill(id, operator); }
    @Override @Transactional public void deleteBill(Long id) {
        super.deleteBill(id);
        // 草稿订单删除：清理可能残留的已取消指示（物理删除仅用于草稿）
        saleOrderSubstitutePlanService.deleteByOrder(id);
    }

    // ================= 其余保持原样（查询、勾稽、编号生成、金额计算） =================

    @Override
    public PageResult<SaleOrder> pageQuery(int current, int size, String keyword, String status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleOrder.class);
        // 关键词按列表页文案「输入订单号/客户名称搜索」适配（说明书 2.2）：订单号 OR 客户名称 OR 关联客户主键
        // customer_name 列保留（兼容历史有值的单据）；客户名称为空的单据由 customer_id 分支兜住
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword, ListQuerySupport.ID_LOOKUP_LIMIT);
        ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword, List.of("order_no", "customer_name"),
                "customer_id", kwCustomerIds);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/自己经手的单；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_order", customSearch, CustomFieldSearchHelper.idColumnOf("sale_order"));
        Page<SaleOrder> page = new Page<>(current, size);
        Page<SaleOrder> result = page(page, wrapper);
        for (SaleOrder o : result.getRecords()) fillDisplayFields(o);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PageResult<SaleOrder> pageQuery(int current, int size, String keyword, String status,
                                           com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                           String sortBy, String sortOrder, Map<String, String> drill, String customSearch) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleOrder.class);
        // 关键词按列表页文案「输入订单号/客户名称搜索」适配（说明书 2.2）：订单号 OR 客户名称 OR 关联客户主键
        // customer_name 列保留（兼容历史有值的单据）；客户名称为空的单据由 customer_id 分支兜住
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword, ListQuerySupport.ID_LOOKUP_LIMIT);
        ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword, List.of("order_no", "customer_name"),
                "customer_id", kwCustomerIds);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        applyDrillFilter(wrapper, drill);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_order", customSearch, CustomFieldSearchHelper.idColumnOf("sale_order"));
        Page<SaleOrder> page = new Page<>(current, size);
        Page<SaleOrder> result = page(page, wrapper);
        for (SaleOrder o : result.getRecords()) fillDisplayFields(o);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PageResult<SaleOrder> pageQuery(int current, int size, String keyword, String status,
                                           com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                           String sortBy, String sortOrder, Map<String, String> drill,
                                           String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleOrder.class);
        // 关键词按列表页文案「输入订单号/客户名称搜索」适配（说明书 2.2）：订单号 OR 客户名称 OR 关联客户主键
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword, ListQuerySupport.ID_LOOKUP_LIMIT);
        ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword, List.of("order_no", "customer_name"),
                "customer_id", kwCustomerIds);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        applyDrillFilter(wrapper, drill);
        // 表头字段过滤（2026-10-14 筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "saleOrder", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_order", customSearch, CustomFieldSearchHelper.idColumnOf("sale_order"));
        Page<SaleOrder> page = new Page<>(current, size);
        Page<SaleOrder> result = page(page, wrapper);
        for (SaleOrder o : result.getRecords()) fillDisplayFields(o);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 列表页筛选维度（数据看板穿透 + 通用搜索栏共用；全部参数化，无字符串拼接；非法值一律忽略，不影响默认查询）。
     * 支持：startDate/endDate（单据日期 order_date，均含端点）、customerId、employeeId（→ saler_id）、
     * customerLevel、materialId、productLineId（一级分类，含子孙分类）。
     *
     * <p>2026-09-16 通用化：日期区间 / 客户 / 员工三个维度改走 {@link ListQuerySupport}，
     * 与其它列表页同一口径；customerLevel / materialId / productLineId 为看板穿透专有的级联逻辑，保持原实现。</p>
     */
    private void applyDrillFilter(QueryWrapper<SaleOrder> wrapper, Map<String, String> drill) {
        if (drill == null || drill.isEmpty()) return;
        // 日期区间（单据日期，含端点）
        ListQuerySupport.applyDateRange(wrapper, "order_date", drill.get("startDate"), drill.get("endDate"));
        // 客户维度：统一入参 customerId
        ListQuerySupport.applyId(wrapper, "customer_id", drill.get("customerId"));
        // 员工维度（销售员）：统一入参 employeeId → sale_order.saler_id
        ListQuerySupport.applyEmployee(wrapper, drill.get("employeeId"), "saler_id");
        String level = drill.get("customerLevel");
        if (level != null && !level.isBlank()) {
            List<Long> ids = customerService.list(new QueryWrapper<Customer>().eq("customer_level", level))
                    .stream().map(Customer::getCustomerId).filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
            if (ids.isEmpty()) wrapper.eq("order_id", -1L); else wrapper.in("customer_id", ids);
        }
        Long materialId = drillLong(drill.get("materialId"));
        Long lineId = drillLong(drill.get("productLineId"));
        if (materialId != null) {
            List<Long> orderIds = orderIdsByMaterial(java.util.List.of(materialId));
            if (orderIds.isEmpty()) wrapper.eq("order_id", -1L); else wrapper.in("order_id", orderIds);
        } else if (lineId != null) {
            List<Long> catIds = categoryAndDescendants(lineId);
            List<Long> matIds = materialService.list(new QueryWrapper<Material>().in("category_id", catIds))
                    .stream().map(Material::getMaterialId).filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
            if (matIds.isEmpty()) { wrapper.eq("order_id", -1L); return; }
            List<Long> orderIds = orderIdsByMaterial(matIds);
            if (orderIds.isEmpty()) wrapper.eq("order_id", -1L); else wrapper.in("order_id", orderIds);
        }
    }

    private List<Long> orderIdsByMaterial(List<Long> materialIds) {
        if (materialIds.isEmpty()) return java.util.Collections.emptyList();
        return itemMapper.selectList(new QueryWrapper<SaleOrderItem>().in("material_id", materialIds))
                .stream().map(SaleOrderItem::getOrderId).filter(java.util.Objects::nonNull).distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /** 一级分类 + 其所有子孙分类ID（供按产品线穿透用） */
    private List<Long> categoryAndDescendants(Long rootId) {
        List<com.erp.basedata.entity.MaterialCategory> all = materialCategoryMapper.selectList(null);
        List<Long> result = new java.util.ArrayList<>();
        result.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (com.erp.basedata.entity.MaterialCategory c : all) {
                if (c.getCategoryId() != null && c.getParentId() != null
                        && result.contains(c.getParentId()) && !result.contains(c.getCategoryId())) {
                    result.add(c.getCategoryId());
                    changed = true;
                }
            }
        }
        return result;
    }

    private static Long drillLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public SaleOrder getDetail(Long id) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        List<SaleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", id).orderByAsc("line_no"));
        fillDisplayFields(order);
        if (items != null) for (SaleOrderItem item : items) fillItemDisplayFields(item);
        order.setItems(items);
        // 【2026-11-17 替换货】按钮显隐依据 + 明细替换发货信息（失败不影响订单详情）
        try {
            order.setHasEffectiveDelivery(saleExchangeService.hasEffectiveNormalDelivery(id));
            order.setManualClosed("手动结案".equals(order.getClosedReason()));
            order.setHasPendingReturn(saleExchangeService.hasPendingReturn(id));
            // 2026-11-18：替换发货完成后（可替换额度=0）入口应消失，避免点开弹窗无可操作行
            order.setReplaceableTotalQuantity(saleExchangeService.sumReplaceableQuantity(id));
            // 销售替换发货指示（待执行/部分执行）
            java.util.List<SaleOrderSubstitutePlan> plans = saleOrderSubstitutePlanService.listByOrder(id);
            java.util.Map<Long, java.util.List<SaleOrderSubstitutePlan>> plansByItem = new java.util.LinkedHashMap<>();
            for (SaleOrderSubstitutePlan p : plans) {
                if (p.getOrderItemId() == null) continue;
                plansByItem.computeIfAbsent(p.getOrderItemId(), x -> new java.util.ArrayList<>()).add(p);
            }
            // 已生效替换出库行（销售指示执行 + 仓库直接替代）
            java.util.List<SaleDeliveryItem> subItems = saleDeliveryItemMapper.selectList(
                    new QueryWrapper<SaleDeliveryItem>()
                            .eq("source_id", id)
                            .isNotNull("replaced_from_material_id"));
            java.util.Map<Long, SaleDelivery> deliveryMap = new java.util.HashMap<>();
            if (!subItems.isEmpty()) {
                java.util.Set<Long> deliveryIds = new java.util.HashSet<>();
                for (SaleDeliveryItem di : subItems) {
                    if (di.getDeliveryId() != null) deliveryIds.add(di.getDeliveryId());
                }
                if (!deliveryIds.isEmpty()) {
                    for (SaleDelivery d : saleDeliveryMapper.selectBatchIds(deliveryIds)) {
                        deliveryMap.put(d.getDeliveryId(), d);
                    }
                }
            }
            java.util.Map<Long, java.util.List<SaleDeliveryItem>> subByItem = new java.util.LinkedHashMap<>();
            for (SaleDeliveryItem di : subItems) {
                SaleDelivery d = deliveryMap.get(di.getDeliveryId());
                if (d == null || !"已生效".equals(d.getStatus())) continue;
                if (d.getSourceBillType() != null && d.getSourceBillType().contains("销售换货单")) continue;
                if (di.getSourceItemId() == null) continue;
                subByItem.computeIfAbsent(di.getSourceItemId(), x -> new java.util.ArrayList<>()).add(di);
            }
            // 售后换货
            java.util.List<java.util.Map<String, Object>> infos = saleExchangeService.listExchangeInfoByOrder(id);
            java.util.Map<Long, java.util.List<java.util.Map<String, Object>>> byItem = new java.util.LinkedHashMap<>();
            for (java.util.Map<String, Object> r : infos) {
                Object oid = r.get("orderItemId");
                if (oid == null) continue;
                Long k = Long.valueOf(String.valueOf(oid));
                byItem.computeIfAbsent(k, x -> new java.util.ArrayList<>()).add(r);
            }
            for (SaleOrderItem item : items) {
                StringBuilder sb = new StringBuilder();
                java.math.BigDecimal pending = java.math.BigDecimal.ZERO;
                // ① 待执行的销售指示
                for (SaleOrderSubstitutePlan p : plansByItem.getOrDefault(item.getOrderItemId(), java.util.Collections.emptyList())) {
                    java.math.BigDecimal pend = p.getPendingQuantity() != null
                            ? p.getPendingQuantity()
                            : nvl(p.getQuantity()).subtract(nvl(p.getTurnedDeliveryQuantity()));
                    if (pend.signum() <= 0) continue;
                    appendLine(sb, safe(p.getSubstituteMaterialName()) + "×" + fmtQty(pend)
                            + "（销售指定，待执行 " + fmtQty(pend) + "）");
                }
                // ② 已生效替换出库行
                for (SaleDeliveryItem di : subByItem.getOrDefault(item.getOrderItemId(), java.util.Collections.emptyList())) {
                    SaleDelivery d = deliveryMap.get(di.getDeliveryId());
                    String source = di.getReplacePlanId() != null ? "销售指定" : "仓库替代";
                    appendLine(sb, safe(di.getMaterialName()) + "×" + fmtQty(di.getQuantity())
                            + "（" + source + "，出库单 " + (d != null ? safe(d.getDeliveryNo()) : "") + "）");
                }
                // ③ 售后换货
                for (java.util.Map<String, Object> r : byItem.getOrDefault(item.getOrderItemId(), java.util.Collections.emptyList())) {
                    appendLine(sb, safe(r.get("materialName")) + "×" + fmtQty(r.get("quantity"))
                            + "（换货单 " + safe(r.get("exchangeNo")) + "）");
                    Object p = r.get("pendingReturn");
                    if (p instanceof java.math.BigDecimal) pending = pending.add((java.math.BigDecimal) p);
                }
                item.setReplaceDeliveryInfo(sb.toString());
                item.setPendingReturnQuantity(pending);
                item.setSubstitutePlans(plansByItem.getOrDefault(item.getOrderItemId(), java.util.Collections.emptyList()));
            }
        } catch (Exception e) {
            log.warn("装配销售订单替换发货信息失败 orderId={}: {}", id, e.getMessage());
        }
        return order;
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(line);
    }

    private static String safe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String fmtQty(Object v) {
        if (v == null) return "0";
        try {
            return new java.math.BigDecimal(String.valueOf(v)).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    private void fillDisplayFields(SaleOrder o) {
        if (o.getCustomerId() != null) { Customer c = customerService.getById(o.getCustomerId()); if (c != null) o.setCustomerName(c.getCustomerName()); }
        if (o.getSalerId() != null) { Employee e = employeeService.getById(o.getSalerId()); if (e != null) o.setSalerName(e.getEmployeeName()); }
        if (o.getCurrencyId() != null) { Currency cur = currencyService.getById(o.getCurrencyId()); if (cur != null) o.setCurrencyCode(cur.getCurrencyCode()); }
        if (o.getPaymentTermId() != null) { PaymentTerm pt = paymentTermService.getById(o.getPaymentTermId()); if (pt != null) o.setPaymentTermName(pt.getPaymentTermName()); }
    }

    private void fillItemDisplayFields(SaleOrderItem item) {
        if (item.getMaterialId() != null) {
            Material m = materialService.getById(item.getMaterialId());
            if (m != null) { item.setMaterialCode(m.getMaterialCode()); item.setMaterialName(m.getMaterialName()); item.setSpecification(m.getModel()); }
        }
        if (item.getUnitId() != null) { UnitOfMeasure u = unitOfMeasureService.getById(item.getUnitId()); if (u != null) item.setUnit(u.getUnitName()); }
    }

    @Override
    @Transactional
    public void updateTurnedQuantity(Long orderId, Long orderItemId, BigDecimal quantity, String turnType) {
        SaleOrderItem item = itemMapper.selectById(orderItemId);
        if (item == null) throw new BusinessException("订单明细不存在");
        BigDecimal currentQty = quantity != null ? quantity : BigDecimal.ZERO;
        switch (turnType) {
            case "output":
                item.setTurnedOutputQuantity(nvl(item.getTurnedOutputQuantity()).add(currentQty)); break;
            case "return":
                item.setTurnedReturnQuantity(nvl(item.getTurnedReturnQuantity()).add(currentQty)); break;
            case "invoice":
                item.setTurnedInvoiceQuantity(nvl(item.getTurnedInvoiceQuantity()).add(currentQty)); break;
            case "produce":
                item.setTurnedProduceQuantity(nvl(item.getTurnedProduceQuantity()).add(currentQty)); break;
            case "subcontract":
                item.setTurnedSubcontractQuantity(nvl(item.getTurnedSubcontractQuantity()).add(currentQty)); break;
            default: throw new BusinessException("未知的转单类型: " + turnType);
        }
        itemMapper.updateById(item);
        // 【2026-10-14 完结口径可配置】明细结案判定不再写死"只看发货"，统一走
        // recalcOrderCloseStatus 按「销售订单状态设置」三模式整单重算（设置中心 → 销售参数）：
        //   销售发货单（默认）= 净发货 ≥ 订购数量（与 2026-10-14 修正口径一致，退货冲减双向）；
        //   销售发票 = 已开票金额 ≥ 行价税合计；
        //   销售发货+销售发票+收款单 = 前两者且已收金额 ≥ 行价税合计。
        recalcOrderCloseStatus(orderId);
        log.info("订单明细勾稽更新: 订单ID={}, 明细ID={}, 类型={}, 数量={}", orderId, orderItemId, turnType, currentQty);
    }

    @Override
    @Transactional
    public void updateTurnedInvoiceAmount(Long orderId, Long orderItemId, BigDecimal amountDelta) {
        SaleOrderItem item = itemMapper.selectById(orderItemId);
        if (item == null) throw new BusinessException("订单明细不存在");
        BigDecimal delta = amountDelta != null ? amountDelta : BigDecimal.ZERO;
        if (delta.signum() == 0) return;
        item.setTurnedInvoiceAmount(nvl(item.getTurnedInvoiceAmount()).add(delta).max(BigDecimal.ZERO));
        itemMapper.updateById(item);
        recalcOrderCloseStatus(orderId);
        log.info("订单明细已开票金额回写: 订单ID={}, 明细ID={}, 变动={}", orderId, orderItemId, delta);
    }

    /** 设置中心「销售订单状态设置」当前值（缺省/未知值一律兜底=销售发货单，即旧行为） */
    private String getCloseMode() {
        String v = sysConfigService.getValue("sale.order.auto.close.mode", "销售发货单");
        return (v == null || v.isBlank()) ? "销售发货单" : v.trim();
    }

    @Override
    public boolean isOrderClosed(Long orderId) {
        if (orderId == null) return false;
        SaleOrder order = getById(orderId);
        return order != null && order.getCloseStatus() != null && order.getCloseStatus() == 1;
    }

    /**
     * 【2026-10-14 完结口径可配置】按「销售订单状态设置」整单重算：
     * 明细行逐行按当前模式重算 detail_close_status（双向：满足置 1、不满足回 0），
     * 订单级 = 全部明细完结 → close_status=1，任一不满足 → 0。
     * 口径（均为"已生效"下游、金额对齐行价税合计）：
     *   发货完成 = 净发货(已发货-已退货) ≥ 订购数量；
     *   开票完成 = 已开票金额(turned_invoice_amount) ≥ 行价税合计(net_amount)；
     *   收款完成 = 已收金额(received_amount) ≥ 行价税合计(net_amount)。
     * 手动结案（closed_reason='手动结案'）是人工决定，重算不覆盖（与 SQL 158/159 口径一致）。
     */
    @Override
    @Transactional
    public void recalcOrderCloseStatus(Long orderId) {
        SaleOrder order = getById(orderId);
        if (order == null) return;
        if ("手动结案".equals(order.getClosedReason())) return;
        List<SaleOrderItem> items = itemMapper.selectList(new QueryWrapper<SaleOrderItem>().eq("order_id", orderId));
        if (items == null || items.isEmpty()) return;
        String mode = getCloseMode();
        boolean checkInvoice = "销售发票".equals(mode) || "销售发货+销售发票+收款单".equals(mode);
        boolean checkShipment = !"销售发票".equals(mode);
        boolean checkReceipt = "销售发货+销售发票+收款单".equals(mode);
        boolean allClosed = true;
        for (SaleOrderItem item : items) {
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            BigDecimal lineNet = item.getNetAmount() != null ? item.getNetAmount() : BigDecimal.ZERO;
            boolean done = true;
            if (checkShipment) {
                BigDecimal netShipped = nvl(item.getTurnedOutputQuantity()).subtract(nvl(item.getTurnedReturnQuantity()));
                done = done && qty.signum() > 0 && netShipped.compareTo(qty) >= 0;
            }
            if (checkInvoice) {
                done = done && lineNet.signum() > 0 && nvl(item.getTurnedInvoiceAmount()).compareTo(lineNet) >= 0;
            }
            if (checkReceipt) {
                done = done && lineNet.signum() > 0 && nvl(item.getReceivedAmount()).compareTo(lineNet) >= 0;
            }
            // 【2026-11-17 替换货】自动结案安全阀：该行还有替换发货待客户回库时，不自动结案
            // （人工「结案」是业务出口，允许放弃待退回，见 closeOrder）
            if (done) {
                try {
                    if (saleExchangeService.hasPendingReturnForItem(item.getOrderItemId())) done = false;
                } catch (Exception e) {
                    log.warn("结案重算查询待退回失败 itemId={}: {}", item.getOrderItemId(), e.getMessage());
                }
            }
            Integer newFlag = done ? 1 : 0;
            if (item.getDetailCloseStatus() == null || item.getDetailCloseStatus() != newFlag) {
                item.setDetailCloseStatus(newFlag);
                itemMapper.updateById(item);
            }
            if (!done) allClosed = false;
        }
        int newClose = allClosed ? 1 : 0;
        Integer oldClose = order.getCloseStatus();
        if (oldClose == null || oldClose != newClose) {
            order.setCloseStatus(newClose);
            updateById(order);
            log.info("销售订单完结重算: 订单={}, 模式={}, 结案={}→{}", order.getOrderNo(), mode, oldClose, newClose);
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private void updateQuoteTurnedQty(SaleOrder order, List<SaleOrderItem> items) {
        if (items == null || items.isEmpty()) return;
        if (order.getSourceBillType() == null || order.getSourceBillNo() == null) return;
        SaleQuote quote = saleQuoteMapper.selectOne(
                new QueryWrapper<SaleQuote>().eq("quote_no", order.getSourceBillNo()).last("LIMIT 1"));
        if (quote == null) return;
        for (SaleOrderItem item : items) {
            if (item.getSourceLineNo() == null) continue;
            SaleQuoteItem quoteItem = saleQuoteItemMapper.selectOne(
                    new QueryWrapper<SaleQuoteItem>()
                            .eq("quote_id", quote.getQuoteId())
                            .eq("line_no", item.getSourceLineNo())
                            .last("LIMIT 1"));
            if (quoteItem == null) continue;
            quoteItem.setTurnedOrderQuantity(item.getQuantity());
            saleQuoteItemMapper.updateById(quoteItem);
        }
    }

    private void calculateItemAmount(SaleOrderItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;
        BigDecimal discountRate = item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO;
        Integer taxType = item.getTaxType() != null ? item.getTaxType() : TAX_TYPE_EXCLUSIVE;
        if (quantity.compareTo(BigDecimal.ZERO) < 0) return; // 草稿宽松：负数跳过抛错
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) return;
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(HUNDRED) > 0) return;
        if (discountRate.compareTo(BigDecimal.ZERO) < 0 || discountRate.compareTo(HUNDRED) > 0) return;
        item.setUnitPrice(unitPrice); item.setQuantity(quantity); item.setTaxRate(taxRate);
        item.setDiscountRate(discountRate); item.setTaxType(taxType);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;           // 不计税
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;    // 含税价
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        // 含税价 / 不含税价 两列（落库）：按表头「计税方式」由「单价」换算
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

        // 折前金额一律按「不含税」口径参与折扣与税额计算
        BigDecimal originalAmount = inclusive
                ? unitPrice.multiply(quantity).divide(taxFactor, SCALE, RoundingMode.HALF_UP)
                : unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discountAmount = originalAmount.multiply(discountRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        item.setDiscountAmount(discountAmount);
        BigDecimal amount = originalAmount.subtract(discountAmount).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 不计税：税额恒为 0；含税价：税额按「折后含税合计 − 折后不含税金额」倒算，
        // 避免两条舍入路径产生 1 分尾差（2026-10-18 统一口径，见 TaxAmountUtils）
        BigDecimal finalTaxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, discountRate, inclusive, noTax);
        item.setTaxAmount(finalTaxAmount);
        item.setNetAmount(amount.add(finalTaxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 由表头「计税方式」解析明细计税类型。
     * 口径：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。
     * 设置中心「启用含税业务」总开关关闭时，一律按「不计税」。
     */
    private int resolveHeaderTaxType(SaleOrder order) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = order.getTaxCalcMethod();
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private void recalculateOrder(SaleOrder order, List<SaleOrderItem> items) {
        // 表头「计税方式」归一：总开关关闭→不计税；为空→取设置中心默认
        if (!sysParamValidator.isTaxEnabled()) {
            order.setTaxCalcMethod("不计税");
        } else if (order.getTaxCalcMethod() == null || order.getTaxCalcMethod().isBlank()) {
            order.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(order);
        BigDecimal exRate = order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        if (items != null) {
            for (SaleOrderItem item : items) {
                item.setTaxType(headerTaxType);
                calculateItemAmount(item);
                // 本位币折算（按汇率）
                item.setBaseUnitPrice(nvl(item.getPriceWithoutTax()).multiply(exRate).setScale(6, RoundingMode.HALF_UP));
                item.setBaseAmount(nvl(item.getAmount()).multiply(exRate).setScale(SCALE, RoundingMode.HALF_UP));
                item.setBaseTaxAmount(nvl(item.getTaxAmount()).multiply(exRate).setScale(SCALE, RoundingMode.HALF_UP));
                item.setBaseNetAmount(nvl(item.getNetAmount()).multiply(exRate).setScale(SCALE, RoundingMode.HALF_UP));
                BigDecimal originalAmount = nvl(item.getAmount()).add(nvl(item.getDiscountAmount()));
                totalAmount = totalAmount.add(originalAmount);
                totalDiscount = totalDiscount.add(nvl(item.getDiscountAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
            }
        }
        order.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        order.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        order.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        BigDecimal netAmount = order.getTotalAmount().subtract(order.getDiscountAmount()).add(order.getTaxAmount());
        order.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));
        sysParamValidator.adjustTaxIfDisabled("销售订单",
                order::setTaxAmount, order::setNetAmount,
                order.getTotalAmount().subtract(order.getDiscountAmount()), order.getTaxAmount());
        BigDecimal rate = order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE;
        order.setBaseTotalAmount(order.getTotalAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        order.setBaseTaxAmount(order.getTaxAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        order.setBaseNetAmount(order.getNetAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private void applyKgwBusinessRules(SaleOrder order, List<SaleOrderItem> items) {
        if (order == null || items == null || items.isEmpty()) return;
        if (kgwRules.isCustomerRequired() && order.getCustomerId() == null) {
            throw new BusinessException("【设置中心-客户必填】客户不能为空，请在销售订单中选择客户");
        }
        boolean salePriceStrategyOn = kgwRules.isSalePriceStrategyEnabled();
        for (int i = 0; i < items.size(); i++) {
            SaleOrderItem item = items.get(i);
            int lineNo = i + 1;
            if (kgwRules.isMaterialRequired() && item.getMaterialId() == null) {
                throw new BusinessException("【设置中心-货品必填】第" + lineNo + "行货品不能为空");
            }
            sysParamValidator.validateAllowZeroPrice("sale.order.allow.zero.price", "销售订单", item.getUnitPrice(), lineNo);
            if (salePriceStrategyOn && item.getMaterialId() != null && item.getUnitPrice() != null) {
                // 2026-09-20 价格策略修复：优先按"价格策略价"校验
                //（此前只拿平均成本硬比，策略表里的数据完全没参与 → 价格策略"录了不生效"）
                BigDecimal strategyPrice = salePriceStrategyService.tryMatchPrice(
                        order.getCustomerId(), item.getMaterialId(), item.getQuantity(), null);
                if (strategyPrice != null) {
                    if (item.getUnitPrice().compareTo(strategyPrice) < 0) {
                        throw new BusinessException(String.format(
                                "【设置中心-销售价格策略】第%d行单价 %.2f 低于价格策略价 %.2f，禁止提交",
                                lineNo, item.getUnitPrice(), strategyPrice));
                    }
                } else {
                    // 未命中策略：保持原有"成本底线"兜底，行为与修复前一致
                    BigDecimal avgCost = materialCostService.getLatestCalcCost(item.getMaterialId());
                    BigDecimal fallbackCost = BigDecimal.ZERO;
                    if (avgCost == null || avgCost.compareTo(BigDecimal.ZERO) == 0) {
                        Material m = materialService.getById(item.getMaterialId());
                        if (m != null && m.getPurchasePrice() != null) fallbackCost = m.getPurchasePrice();
                    } else fallbackCost = avgCost;
                    if (fallbackCost.compareTo(BigDecimal.ZERO) > 0 && item.getUnitPrice().compareTo(fallbackCost) < 0) {
                        throw new BusinessException(String.format(
                                "【设置中心-销售价格策略】第%d行单价 %.2f 低于平均成本 %.2f，禁止提交",
                                lineNo, item.getUnitPrice(), fallbackCost));
                    }
                }
            }
            boolean lowestPriceCheck = sysConfigService.getBooleanValue("sale.price.lowest.check", true);
            if (lowestPriceCheck && item.getMaterialId() != null && item.getUnitPrice() != null) {
                Material m = materialService.getById(item.getMaterialId());
                if (m != null && m.getPurchasePrice() != null && m.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0
                        && item.getUnitPrice().compareTo(m.getPurchasePrice()) < 0) {
                    throw new BusinessException(String.format(
                            "【设置中心-最低售价检查】第%d行单价 %.2f 低于最低售价 %.2f，禁止提交",
                            lineNo, item.getUnitPrice(), m.getPurchasePrice()));
                }
            }
        }
        // 超期账款管控（2026-10-19 下放单据）：按本单「超期账款管控」开关判断；
        // 本单未选（历史单据/接口建单为空）时按设置中心「超期账款阻断新订单」兜底，升级前后行为一致
        if (order.getCustomerId() != null) {
            Customer customer = customerService.getById(order.getCustomerId());
            if (customer != null) {
                boolean overdueBlock = order.getOverdueBlock() != null && !order.getOverdueBlock().isBlank()
                    ? "true".equalsIgnoreCase(order.getOverdueBlock().trim())
                    : sysConfigService.getBooleanValue("sale.credit.overdue.block", false);
                if (overdueBlock) {
                    try {
                        Map<String, Object> summary = financeService.getReceivableSummary(null, null, null, order.getCustomerId(), null);
                        if (summary != null) {
                            Object overdueAmount = summary.get("overdueAmount");
                            BigDecimal overdue = BigDecimal.ZERO;
                            if (overdueAmount instanceof BigDecimal) overdue = (BigDecimal) overdueAmount;
                            else if (overdueAmount != null) overdue = new BigDecimal(overdueAmount.toString());
                            if (overdue.compareTo(BigDecimal.ZERO) > 0) {
                                throw new BusinessException(String.format(
                                    "【超期账款管控】客户[%s]有超期未收款 %.2f，请先催收欠款或关闭本单超期账款管控后再提交",
                                    customer.getCustomerName(), overdue));
                            }
                        }
                    } catch (BusinessException e) { throw e; }
                    catch (Exception e) { log.warn("查询超期应收失败，客户ID: {}", order.getCustomerId(), e); }
                }
            }
        }
        // 信用额度超限阻断（2026-09-07 迁移：总开关由 sale.credit.check 改为 sale.credit.limit.block，默认关闭，设置中心可开启）
        creditGuardService.validateCreditLimit("销售订单", order.getCustomerId() != null
                ? customerService.getById(order.getCustomerId()) : null, order.getNetAmount());
    }

    @Override
    public String generateOrderNo() {
        return sysSerialNoConfigService.generateOrDefault("SALE_ORDER", this::defaultOrderNo);
    }

    @Override
    public String generateOrderNoForSystem() {
        // 系统自动建单：设置中心关闭自动编号时仍按编号规则取号
        return sysSerialNoConfigService.generateForSystem("SALE_ORDER", this::defaultOrderNo);
    }

    private String defaultOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SO-" + dateStr + "-";
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
        SaleOrder last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getOrderNo() != null) {
            try { seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1; } catch (Exception ignored) { }
        }
        return prefix + String.format("%04d", seq);
    }

    @Override
    @Transactional
    public SaleDelivery pushToDelivery(Long id, String operator) {
        SaleOrder order = getDetail(id);
        if (order == null) {
            throw new BusinessException("销售订单不存在");
        }
        if (!"已生效".equals(order.getApprovalStatus())) {
            throw new BusinessException("只有已生效的销售订单才能下推");
        }
        // 【2026-10-14 下推联动】已完结（已结案）订单不允许再下推：前端按钮隐藏，此处兜底拦截
        if (order.getCloseStatus() != null && order.getCloseStatus() == 1) {
            throw new BusinessException("销售订单[" + order.getOrderNo() + "]已完结（已结案），不能下推生成销售出库单");
        }
        List<SaleOrderItem> orderItems = order.getItems();
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException("销售订单无明细，无法下推");
        }

        // 构建出库单
        SaleDelivery delivery = new SaleDelivery();
        delivery.setDeliveryNo(saleDeliveryService.generateDeliveryNo());
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setCustomerId(order.getCustomerId());
        delivery.setContactPerson(order.getContactPerson());
        delivery.setContactPhone(order.getContactPhone());
        // warehouse_id 在 sale_delivery 表中为 NOT NULL；按优先级取仓库，禁止硬编码 1L（warehouse 表无 id=1，会落到幽灵仓库）：
        // 1) 销售订单表头 warehouseId；2) warehouse 表无 is_default 字段，故取 status='启用' 中 stock_id 最小的仓库；
        // 3) 都取不到则抛业务异常，不静默使用 1
        Long warehouseId = resolveWarehouseId(order.getWarehouseId());
        log.info("销售订单 {} 下推出库单使用仓库: {}", order.getOrderNo(), warehouseId);
        delivery.setWarehouseId(warehouseId);
        delivery.setSalerId(order.getSalerId());
        delivery.setCurrencyId(order.getCurrencyId());
        delivery.setExchangeRate(order.getExchangeRate());
        delivery.setTotalAmount(order.getTotalAmount());
        delivery.setTaxAmount(order.getTaxAmount());
        delivery.setDiscountRate(order.getDiscountRate());
        delivery.setDiscountAmount(order.getDiscountAmount());
        delivery.setNetAmount(order.getNetAmount());
        delivery.setSourceBillType("销售订单");
        delivery.setSourceBillNo(order.getOrderNo());
        delivery.setOrderNo(order.getOrderNo());
        // 发货收款管控随单下推（2026-10-19）：出库单继承源头订单上选定的管控模式，出库环节只读
        delivery.setPaymentControl(order.getPaymentControl());
        delivery.setRemark("由销售订单 " + order.getOrderNo() + " 下推生成");
        delivery.setStatus("草稿");
        delivery.setApprovalStatus("待审核");
        String username = operator;
        if (username != null) delivery.setCreatedBy(username);

        // 构建出库单明细
        List<SaleDeliveryItem> deliveryItems = new ArrayList<>();
        int lineNo = 1;
        for (SaleOrderItem oi : orderItems) {
            BigDecimal remainingQty = calcRemainingDeliveryQty(oi);
            if (remainingQty.signum() <= 0) continue;
            SaleDeliveryItem di = new SaleDeliveryItem();
            di.setLineNo(lineNo++);
            di.setMaterialId(oi.getMaterialId());
            di.setMaterialCode(oi.getMaterialCode());
            di.setMaterialName(oi.getMaterialName());
            di.setSpecification(oi.getSpecification());
            di.setUnitId(oi.getUnitId());
            di.setQuantity(remainingQty);
            di.setUnitPrice(oi.getUnitPrice() != null ? oi.getUnitPrice() : BigDecimal.ZERO);
            di.setDiscountRate(oi.getDiscountRate() != null ? oi.getDiscountRate() : BigDecimal.ZERO);
            di.setDiscountAmount(oi.getDiscountAmount() != null ? oi.getDiscountAmount() : BigDecimal.ZERO);
            di.setTaxRate(oi.getTaxRate() != null ? oi.getTaxRate() : BigDecimal.ZERO);
            // 计税方式随单下推（2026-10-09）：出库单为行级口径，按源订单明细计税类型带入，避免默认口径重算导致金额虚增
            di.setTaxType(oi.getTaxType());
            di.setAmount(oi.getAmount() != null ? oi.getAmount() : BigDecimal.ZERO);
            di.setTaxAmount(oi.getTaxAmount() != null ? oi.getTaxAmount() : BigDecimal.ZERO);
            di.setNetAmount(oi.getNetAmount() != null ? oi.getNetAmount() : BigDecimal.ZERO);
            di.setStockId(warehouseId);
            di.setSourceBillType("销售订单");
            di.setSourceBillNo(order.getOrderNo());
            di.setSourceLineNo(oi.getLineNo());
            di.setSourceId(order.getOrderId());
            di.setSourceItemId(oi.getOrderItemId());
            di.setSourceType("销售订单");
            di.setRemark(oi.getRemark());
            deliveryItems.add(di);
        }

        if (deliveryItems.isEmpty()) {
            throw new BusinessException("该销售订单的发货数量已全部下推（含草稿/待审核），不能重复下推生成销售出库单");
        }

        saleDeliveryService.saveDeliveryWithItems(delivery, deliveryItems);
        // 开关判断：出库单免审批时，下推生成后自动审核生效
        if (!approvalGateHelper.needApproval("SALE_DELIVERY")) {
            saleDeliveryService.approve(delivery.getDeliveryId(), username != null ? username : "系统(免审批)");
        }
        return delivery;
    }

    /**
     * 下推剩余可发货数量 = 订单数量 - 已出库（生效+草稿/待审核，排除作废/取消） + 已生效退货。
     */
    private BigDecimal calcRemainingDeliveryQty(SaleOrderItem orderItem) {
        BigDecimal orderQty = nvl(orderItem.getQuantity());
        if (orderQty.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal delivered = BigDecimal.ZERO;
        for (SaleDeliveryItem item : saleDeliveryItemMapper.selectList(
                new QueryWrapper<SaleDeliveryItem>().eq("source_item_id", orderItem.getOrderItemId()))) {
            SaleDelivery head = saleDeliveryService.getById(item.getDeliveryId());
            if (head == null || BillStatusUtils.isVoided(head.getStatus())
                    || "已取消".equals(head.getStatus())) continue;
            delivered = delivered.add(nvl(item.getQuantity()));
        }
        BigDecimal returned = BigDecimal.ZERO;
        for (SaleReturnItem item : saleReturnItemMapper.selectList(
                new QueryWrapper<SaleReturnItem>().eq("source_item_id", orderItem.getOrderItemId()))) {
            SaleReturn head = saleReturnMapper.selectById(item.getReturnId());
            if (head == null || BillStatusUtils.isVoided(head.getStatus())
                    || "已取消".equals(head.getStatus())) continue;
            if (!BillStatusUtils.isApprovedClass(head.getStatus())) continue;
            returned = returned.add(nvl(item.getQuantity()));
        }
        return orderQty.subtract(delivered).add(returned).max(BigDecimal.ZERO);
    }

    /**
     * 解析下推出库单使用的仓库ID：
     * 1) 优先取销售订单表头 warehouseId；
     * 2) 否则取 warehouse 表 status='启用' 中 stock_id 最小的仓库（该表无 is_default 字段）；
     * 3) 仍取不到则抛业务异常，绝不静默使用 1L（warehouse 表可能不存在 id=1，会落到幽灵仓库）。
     */
    private Long resolveWarehouseId(Long orderWarehouseId) {
        if (orderWarehouseId != null) {
            return orderWarehouseId;
        }
        Warehouse def = warehouseService.getOne(new QueryWrapper<Warehouse>()
                .eq("status", "启用").orderByAsc("stock_id").last("LIMIT 1"));
        if (def != null && def.getStockId() != null) {
            return def.getStockId();
        }
        throw new BusinessException("未找到可用仓库，请先在销售订单上指定仓库");
    }

    /**
     * 下推到采购订单: 已生效的销售订单 → 生成采购订单(草稿)
     * 用于销售订单中的物料需要采购的场景（如贸易型、原料采购）
     * 通过sourceBillType/sourceBillNo/sourceId/sourceItemId 建立与销售订单的关联
     */
    @Override
    @Transactional
    public PurchaseOrder pushToPurchase(Long id, String operator) {
        SaleOrder order = getDetail(id);
        if (order == null) {
            throw new BusinessException("销售订单不存在");
        }
        if (!"已生效".equals(order.getApprovalStatus())) {
            throw new BusinessException("只有已生效的销售订单才能下推");
        }
        // 【2026-10-14 下推联动】已完结（已结案）订单不允许再下推：前端按钮隐藏，此处兜底拦截
        if (order.getCloseStatus() != null && order.getCloseStatus() == 1) {
            throw new BusinessException("销售订单[" + order.getOrderNo() + "]已完结（已结案），不能下推生成采购订单");
        }
        List<SaleOrderItem> orderItems = order.getItems();
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException("销售订单无明细，无法下推");
        }

        // 物料主数据暂无默认供应商字段，统一使用默认供应商（ID=1）
        // 后续可在物料档案新增"默认供应商"字段后改为按供应商分组生成多张采购订单
        Long supplierId = 1L;
        String supplierName = "默认供应商";
        List<SaleOrderItem> groupedItems = orderItems;

        PurchaseOrder po = new PurchaseOrder();
        po.setOrderNo(purchaseOrderService.generateOrderNo());
        po.setOrderDate(LocalDate.now());
        po.setSupplierId(supplierId);
        po.setSupplierName(supplierName);
        po.setBuyerId(order.getSalerId());
        po.setCurrencyId(order.getCurrencyId() != null ? order.getCurrencyId() : 1L);
        po.setExchangeRate(order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE);
        po.setDeliveryDate(order.getDeliveryDate());
        po.setSourceBillType("销售订单");
        po.setSourceBillNo(order.getOrderNo());
        po.setRemark("由销售订单 " + order.getOrderNo() + " 下推生成");
        // 计税方式随单下推（2026-10-09）：带过销售订单口径，避免采购订单按默认口径重算导致金额/税额不一致
        po.setTaxCalcMethod(order.getTaxCalcMethod());
        po.setStatus("草稿");
        po.setApprovalStatus("草稿");
        String username = operator;
        if (username != null) po.setCreatedBy(username);

        // 合计金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal netAmount = BigDecimal.ZERO;

        List<PurchaseOrderItem> poItems = new ArrayList<>();
        int lineNo = 1;
        for (SaleOrderItem oi : groupedItems) {
            if (oi.getMaterialId() == null) continue;
            PurchaseOrderItem pi = new PurchaseOrderItem();
            pi.setLineNo(lineNo++);
            pi.setMaterialId(oi.getMaterialId());
            pi.setMaterialCode(oi.getMaterialCode());
            pi.setMaterialName(oi.getMaterialName());
            pi.setSpecification(oi.getSpecification());
            pi.setUnitId(oi.getUnitId());
            pi.setQuantity(oi.getQuantity());
            pi.setUnitPrice(oi.getUnitPrice() != null ? oi.getUnitPrice() : BigDecimal.ZERO);
            pi.setDiscountRate(oi.getDiscountRate() != null ? oi.getDiscountRate() : BigDecimal.ZERO);
            pi.setDiscountAmount(oi.getDiscountAmount() != null ? oi.getDiscountAmount() : BigDecimal.ZERO);
            pi.setTaxRate(oi.getTaxRate() != null ? oi.getTaxRate() : BigDecimal.ZERO);
            pi.setAmount(oi.getAmount() != null ? oi.getAmount() : BigDecimal.ZERO);
            pi.setTaxAmount(oi.getTaxAmount() != null ? oi.getTaxAmount() : BigDecimal.ZERO);
            pi.setNetAmount(oi.getNetAmount() != null ? oi.getNetAmount() : BigDecimal.ZERO);
            pi.setDeliveryDate(order.getDeliveryDate());
            // 建立与销售订单明细的关联链路
            pi.setSourceBillType("销售订单");
            pi.setSourceBillNo(order.getOrderNo());
            pi.setSourceLineNo(oi.getLineNo());
            pi.setSourceId(order.getOrderId());
            pi.setSourceItemId(oi.getOrderItemId());
            pi.setSourceType("销售订单");
            pi.setRemark(oi.getRemark());
            poItems.add(pi);

            if (pi.getAmount() != null) totalAmount = totalAmount.add(pi.getAmount());
            if (pi.getTaxAmount() != null) taxAmount = taxAmount.add(pi.getTaxAmount());
            if (pi.getNetAmount() != null) netAmount = netAmount.add(pi.getNetAmount());
        }

        if (poItems.isEmpty()) {
            throw new BusinessException("销售订单明细没有有效物料，无法下推");
        }

        po.setTotalAmount(totalAmount);
        po.setTaxAmount(taxAmount);
        po.setNetAmount(netAmount);

        purchaseOrderService.saveOrderWithItems(po, poItems);
        return po;
    }
}
