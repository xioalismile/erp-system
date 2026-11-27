package com.erp.ai.bill;

import com.erp.finance.cost.calculation.service.CostCalculationService;
import com.erp.finance.expense.purchase.service.PurchaseExpenseService;
import com.erp.finance.expense.sale.service.SaleExpenseService;
import com.erp.finance.prepayment.purchase.service.PurchasePrePaymentService;
import com.erp.finance.prepayment.sale.service.SalePreReceiptService;
import com.erp.finance.purchasebill.service.PurchaseStatementService;
import com.erp.finance.salebill.service.SaleStatementService;
import com.erp.finance.service.OperationExpenseService;
import com.erp.finance.wage.service.WageSettlementService;
import com.erp.finance.service.FinanceService;
import com.erp.freesample.service.FreeSampleOrderService;
import com.erp.inventory.service.MaterialRequisitionService;
import com.erp.inventory.service.OtherIssueService;
import com.erp.inventory.service.OtherReceiptService;
import com.erp.inventory.service.StockCheckService;
import com.erp.inventory.service.StockTransferService;
import com.erp.masterdata.service.BomService;
import com.erp.produce.assemble.service.ProduceAssembleService;
import com.erp.produce.assemble.service.ProduceDisassembleService;
import com.erp.produce.mrp.service.MrpService;
import com.erp.production.service.OperationReportService;
import com.erp.production.service.ProductionReceiptService;
import com.erp.production.service.WorkOrderService;
import com.erp.purchase.service.PurchaseContractService;
import com.erp.purchase.service.PurchaseInboundService;
import com.erp.purchase.service.PurchaseInvoiceService;
import com.erp.purchase.service.PurchaseOrderService;
import com.erp.purchase.service.PurchaseRequestService;
import com.erp.purchase.service.PurchaseReturnService;
import com.erp.sale.service.SaleContractService;
import com.erp.sale.service.SaleDeliveryService;
import com.erp.sale.service.SaleExchangeService;
import com.erp.sale.service.SaleInvoiceService;
import com.erp.sale.service.SaleOrderService;
import com.erp.sale.service.SaleQuoteService;
import com.erp.sale.service.SaleRepairService;
import com.erp.sale.service.SaleReturnService;
import com.erp.sample.service.SampleOrderService;
import com.erp.subcontract.inbound.service.SubcontractInboundService;
import com.erp.subcontract.order.service.SubcontractOrderService;
import com.erp.subcontract.pick.service.SubcontractPickService;
import com.erp.subcontract.returnn.service.SubcontractReturnService;
import com.erp.subcontract.supplement.service.SubcontractSupplementService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 单据能力矩阵注册表（动作 + 下推）。
 *
 * <p>职责：
 * <ol>
 *   <li>billType → {中文名, 主键字段, Service Bean}（供 bill_action 反射调度）；</li>
 *   <li>billType → 有序动作矩阵 {@link ActionSpec}（方法名 + 精确参数类型，供 bill_action 精确调用）；</li>
 *   <li>sourceType → 下推目标 {@link PushTarget}（供 bill_pushdown 精确调用）。</li>
 * </ol>
 *
 * <p>设计要点：动作调度不再"候选方法名 + 实参变体逐个试错"，
 * 而是以本矩阵登记的 {@code method + paramTypes} 精确匹配反射，从根上消除
 * "命中语义不同的重载"这一隐患（历史上 SALE_CONTRACT 同时存在 submitForApproval(Long)
 * 与 submitForApproval(Long,String)，试错式匹配可能命中错误重载）。
 *
 * <p>矩阵来源：立项登记表《小助手单据动作修正与下推》3.3，逐条打开对应 Service 接口核对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillActionRegistry {

    private final SaleQuoteService saleQuoteService;
    private final SaleOrderService saleOrderService;
    private final SaleContractService saleContractService;
    private final SaleDeliveryService saleDeliveryService;
    private final SaleReturnService saleReturnService;
    private final SaleInvoiceService saleInvoiceService;
    // 2026-11-19 补登：销售换货单 / 售后维修单
    private final SaleExchangeService saleExchangeService;
    private final SaleRepairService saleRepairService;
    private final PurchaseRequestService purchaseRequestService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseContractService purchaseContractService;
    private final PurchaseInboundService purchaseInboundService;
    private final PurchaseReturnService purchaseReturnService;
    private final PurchaseInvoiceService purchaseInvoiceService;
    private final WorkOrderService workOrderService;
    private final ProductionReceiptService productionReceiptService;
    private final MaterialRequisitionService materialRequisitionService;
    private final StockTransferService stockTransferService;
    private final StockCheckService stockCheckService;
    private final OtherReceiptService otherReceiptService;
    private final OtherIssueService otherIssueService;
    private final SubcontractOrderService subcontractOrderService;
    private final SubcontractPickService subcontractPickService;
    private final SubcontractInboundService subcontractInboundService;
    private final SampleOrderService sampleOrderService;
    private final FreeSampleOrderService freeSampleOrderService;
    private final FinanceService financeService;
    private final PurchaseExpenseService purchaseExpenseService;
    private final PurchasePrePaymentService purchasePrePaymentService;
    private final BomService bomService;
    private final OperationReportService operationReportService;
    private final ProduceAssembleService produceAssembleService;
    private final ProduceDisassembleService produceDisassembleService;
    // 2026-09-28 批次3 补登（单据动作矩阵补全）
    private final SalePreReceiptService salePreReceiptService;
    private final OperationExpenseService operationExpenseService;
    private final CostCalculationService costCalculationService;
    private final WageSettlementService wageSettlementService;
    private final SaleExpenseService saleExpenseService;
    private final SaleStatementService saleStatementService;
    private final PurchaseStatementService purchaseStatementService;
    private final SubcontractReturnService subcontractReturnService;
    private final SubcontractSupplementService subcontractSupplementService;
    private final MrpService mrpService;

    // ===== 反射调度用类型别名 =====
    private static final Class<?> L = Long.class;
    private static final Class<?> S = String.class;
    private static final Class<?> LST = List.class;
    private static final Class<?> BD = BigDecimal.class;

    /** 动作英文 → 中文名（工具提示、确认卡片、清单展示复用） */
    public static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("submit", "提交"), Map.entry("approve", "审核"), Map.entry("reject", "驳回"),
            Map.entry("reverseApprove", "重置草稿"), Map.entry("close", "关闭"), Map.entry("closeOrder", "结案"),
            Map.entry("reopen", "反结案"), Map.entry("void", "作废"), Map.entry("unvoid", "反作废"),
            Map.entry("delete", "删除"), Map.entry("release", "下达"), Map.entry("start", "开工"),
            Map.entry("complete", "完工"), Map.entry("startCheck", "开始盘点"), Map.entry("finishCheck", "完成盘点"),
            Map.entry("ship", "发货"), Map.entry("confirm", "确认"),
            Map.entry("allocateCost", "费用分摊"), Map.entry("writeOff", "核销"), Map.entry("reverseWriteOff", "反核销"),
            Map.entry("enable", "启用"), Map.entry("disable", "停用"), Map.entry("setDefault", "设为默认"),
            Map.entry("startSample", "开始打样"), Map.entry("completeSample", "完成打样"),
            Map.entry("confirmSample", "确认打样"), Map.entry("pushTo", "下推"),
            // 2026-09-28 批次3 新增动作（现有枚举里没有对应动作）
            Map.entry("execute", "执行"),
            // 2026-11-19 售后维修过程动作
            Map.entry("receiveIn", "收件入库"), Map.entry("startRepair", "开始维修"),
            Map.entry("issueParts", "配件领用"), Map.entry("completeRepair", "完成维修"),
            Map.entry("returnToCustomer", "返还客户"), Map.entry("transferToExchange", "转换新"),
            Map.entry("transferToReturn", "转退货")
    );

    /**
     * 动作 → 角色按钮权限码后缀（预留全部动作；null 表示该动作当前无对应按钮码，
     * action 模式下将回退 menu 模式）。
     * 按钮码格式 = {@code <模块码>:<后缀>}，与 设置中心 → 角色权限 → 按钮权限 的 buttonCode 对齐。
     */
    public static final Map<String, String> ACTION_BUTTON_SUFFIX = Map.ofEntries(
            Map.entry("approve", "audit"), Map.entry("delete", "delete"), Map.entry("submit", "submit"),
            Map.entry("reject", "reject"), Map.entry("reverseApprove", "reverseApprove"), Map.entry("close", "close"),
            Map.entry("closeOrder", "closeOrder"), Map.entry("reopen", "reopen"), Map.entry("void", "void"),
            Map.entry("unvoid", "unvoid"), Map.entry("release", "release"), Map.entry("start", "start"),
            Map.entry("complete", "complete"), Map.entry("startCheck", "startCheck"),
            Map.entry("finishCheck", "finishCheck"), Map.entry("ship", "ship"),
            Map.entry("confirm", "confirm"), Map.entry("allocateCost", "allocateCost"),
            Map.entry("writeOff", "writeOff"), Map.entry("reverseWriteOff", "reverseWriteOff"),
            Map.entry("enable", "enable"), Map.entry("disable", "disable"), Map.entry("setDefault", "setDefault"),
            Map.entry("startSample", "startSample"), Map.entry("completeSample", "completeSample"),
            Map.entry("confirmSample", "confirmSample"), Map.entry("pushTo", "push"),
            Map.entry("execute", "execute")
    );

    /** billType → 元数据（LinkedHashMap 保持登记顺序） */
    private final Map<String, BillMeta> registry = new LinkedHashMap<>();

    /** billType → 动作矩阵（LinkedHashMap 保持登记顺序） */
    private final Map<String, LinkedHashMap<String, ActionSpec>> actionMatrix = new LinkedHashMap<>();

    /** sourceType → 下推目标列表（保持登记顺序） */
    private final Map<String, List<PushTarget>> pushMatrix = new LinkedHashMap<>();

    /**
     * 兜底服务/中文名（billType 不在主登记表时使用）。
     * 2026-09-28 批次3 起，原登记于此的工序汇报单/委外退料单/委外补料单已全部并入主登记表，
     * 本兜底表暂时为空（保留给"仅用于提示、无动作"的未来单据）。
     */
    private final Map<String, Object> extraServices = new HashMap<>();
    private final Map<String, String> extraLabels = new HashMap<>();

    /** 「后端无生成接口」的下推关系（sourceType->targetType），仅做界面选单引导提示 */
    private static final Set<String> NO_BACKEND_RELATIONS = Set.of(
            "SALE_QUOTE->SALE_CONTRACT",
            "SALE_CONTRACT->SALE_ORDER",
            "SALE_ORDER->SALE_INVOICE",
            "SALE_DELIVERY->SALE_RETURN",
            "SALE_RETURN->SALE_INVOICE",
            "PURCHASE_REQUEST->PURCHASE_CONTRACT",
            "PURCHASE_CONTRACT->PURCHASE_ORDER",
            "PURCHASE_ORDER->PURCHASE_INVOICE",
            "PURCHASE_INBOUND->PURCHASE_RETURN",
            "PURCHASE_RETURN->PURCHASE_INVOICE",
            "SAMPLE_ORDER->SALE_ORDER",
            "SUBCONTRACT_DELIVERY->SUBCONTRACT_RETURN",
            "SUBCONTRACT_DELIVERY->SUBCONTRACT_SUPPLEMENT",
            "SALE_CONTRACT->PURCHASE_CONTRACT"
    );

    /** 目标单据 → 前端新建页路由（界面选单引导用） */
    private static final Map<String, String> TARGET_ROUTES = Map.ofEntries(
            Map.entry("SALE_CONTRACT", "/sale/contracts"),
            Map.entry("SALE_ORDER", "/sale/orders"),
            Map.entry("SALE_INVOICE", "/sale/invoices"),
            Map.entry("SALE_RETURN", "/sale/returns"),
            Map.entry("PURCHASE_CONTRACT", "/purchase/contracts"),
            Map.entry("PURCHASE_ORDER", "/purchase/orders"),
            Map.entry("PURCHASE_INVOICE", "/purchase/invoices"),
            Map.entry("PURCHASE_RETURN", "/purchase/returns"),
            Map.entry("SUBCONTRACT_RETURN", "/subcontract/returns"),
            Map.entry("SUBCONTRACT_SUPPLEMENT", "/subcontract/supplements")
    );

    @PostConstruct
    public void init() {
        register("SALE_QUOTE", "销售报价单", "quoteId", saleQuoteService);
        register("SALE_ORDER", "销售订单", "orderId", saleOrderService);
        register("SALE_CONTRACT", "销售合同", "contractId", saleContractService);
        register("SALE_DELIVERY", "销售出库单", "deliveryId", saleDeliveryService);
        register("SALE_RETURN", "销售退货单", "returnId", saleReturnService);
        register("SALE_INVOICE", "销售发票", "invoiceId", saleInvoiceService);
        // 2026-11-19 补登：销售换货单（主键 exchangeId / 单号 exchangeNo）
        register("SALE_EXCHANGE", "销售换货单", "exchangeId", saleExchangeService);
        // 2026-11-19 补登：售后维修单（主键 repairId / 单号 repairNo）
        register("SALE_REPAIR", "售后维修单", "repairId", saleRepairService);
        register("PURCHASE_REQUEST", "采购申请单", "requestId", purchaseRequestService);
        register("PURCHASE_ORDER", "采购订单", "orderId", purchaseOrderService);
        register("PURCHASE_CONTRACT", "采购合同", "contractId", purchaseContractService);
        register("PURCHASE_INBOUND", "采购入库单", "receiptId", purchaseInboundService);
        register("PURCHASE_RETURN", "采购退货单", "returnId", purchaseReturnService);
        register("PURCHASE_INVOICE", "采购发票", "invoiceId", purchaseInvoiceService);
        register("PRODUCTION_ORDER", "生产工单", "orderId", workOrderService);
        register("PRODUCTION_RECEIPT", "生产入库单", "receiptId", productionReceiptService);
        register("PRODUCE_ASSEMBLE", "生产组装单", "id", produceAssembleService);
        register("PRODUCE_DISASSEMBLE", "生产拆卸单", "id", produceDisassembleService);
        register("MATERIAL_REQUISITION", "生产领料单", "requisitionId", materialRequisitionService);
        // 2026-11-22 二期补登：生产退料单 / 生产补料单（领料单族变体，同一 Service 按单据类型区分）
        register("PRODUCTION_RETURN", "生产退料单", "requisitionId", materialRequisitionService);
        register("PRODUCTION_SUPPLEMENT", "生产补料单", "requisitionId", materialRequisitionService);
        register("STOCK_TRANSFER", "库存调拨单", "transferId", stockTransferService);
        register("STOCK_CHECK", "库存盘点单", "checkId", stockCheckService);
        register("OTHER_RECEIPT", "其他入库单", "receiptId", otherReceiptService);
        register("OTHER_ISSUE", "其他出库单", "issueId", otherIssueService);
        register("SUBCONTRACT_ORDER", "委外订单", "id", subcontractOrderService);
        register("SUBCONTRACT_DELIVERY", "委外发料单", "id", subcontractPickService);
        register("SUBCONTRACT_RECEIPT", "委外入库单", "id", subcontractInboundService);
        register("SAMPLE_ORDER", "打样单", "sampleId", sampleOrderService);
        register("FREE_SAMPLE", "免费样品单", "orderId", freeSampleOrderService);
        register("PURCHASE_EXPENSE", "采购费用单", "id", purchaseExpenseService);
        register("PURCHASE_PRE_PAYMENT", "预付款单", "id", purchasePrePaymentService);
        register("PAYMENT_RECEIPT", "收款单", "receiptId", financeService);
        register("PAYMENT_VOUCHER", "付款单", "paymentId", financeService);
        register("BOM", "BOM物料清单", "bomId", bomService);

        // ---------- 2026-09-28 批次3 补登（单据动作矩阵补全，逐条对 Controller/Service 源码核实）----------
        // 说明：工序汇报单原先是「仅下推用」的附加服务，本批正式纳入主登记表（同时具备动作矩阵）
        register("SALE_PRE_RECEIPT", "销售预收款单", "id", salePreReceiptService);
        register("OPERATION_EXPENSE", "费用分摊单", "expenseId", operationExpenseService);
        register("COST_CALCULATION", "成本核算单", "id", costCalculationService);
        register("WAGE_SETTLEMENT", "工序工资核算单", "settlementId", wageSettlementService);
        register("SALE_EXPENSE", "销售费用单", "id", saleExpenseService);
        register("SALE_STATEMENT", "客户对账单", "id", saleStatementService);
        register("PURCHASE_STATEMENT", "供应商对账单", "id", purchaseStatementService);
        register("OPERATION_REPORT", "工序汇报单", "reportId", operationReportService);
        register("SUBCONTRACT_RETURN", "委外退料单", "id", subcontractReturnService);
        register("SUBCONTRACT_SUPPLEMENT", "委外补料单", "id", subcontractSupplementService);
        register("MRP_PLAN", "MRP计划单", "id", mrpService);

        buildActionMatrix();
        buildPushMatrix();

        log.info("[AI单据动作] 能力矩阵初始化完成：{} 种单据，{} 条下推关系",
                registry.size(), pushMatrix.values().stream().mapToInt(List::size).sum());
    }

    // ==================================================================
    // 动作矩阵登记（逐条来自 Service 接口源码核实）
    // ==================================================================

    private void buildActionMatrix() {
        // ---------- 销售报价单：close 为单参 ----------
        act("SALE_QUOTE", "submit", "submitForApproval", L);
        act("SALE_QUOTE", "approve", "approve", L, S);
        act("SALE_QUOTE", "reject", "reject", L, S, S);
        act("SALE_QUOTE", "reverseApprove", "reverseApprove", L, S);
        act("SALE_QUOTE", "close", "close", L);
        act("SALE_QUOTE", "closeOrder", "closeOrder", L, S);
        act("SALE_QUOTE", "reopen", "reopenOrder", L, S);
        act("SALE_QUOTE", "void", "voidBill", L, S, S);
        act("SALE_QUOTE", "unvoid", "unvoidBill", L, S);
        act("SALE_QUOTE", "delete", "deleteBill", L);

        // ---------- 销售订单 ----------
        sactionWithCloseLSS("SALE_ORDER");

        // ---------- 销售合同 / 出库单 / 退货单 / 发票：无 close ----------
        sactionNoClose("SALE_CONTRACT");
        sactionNoClose("SALE_DELIVERY");
        sactionNoClose("SALE_RETURN");
        sactionNoClose("SALE_INVOICE");

        // ---------- 销售换货单：接口仅声明 submit/approve/reject/reverseApprove/void/delete ----------
        act("SALE_EXCHANGE", "submit", "submitForApproval", L);
        act("SALE_EXCHANGE", "approve", "approve", L, S);
        act("SALE_EXCHANGE", "reject", "reject", L, S, S);
        act("SALE_EXCHANGE", "reverseApprove", "reverseApprove", L, S);
        act("SALE_EXCHANGE", "void", "voidBill", L, S, S);
        act("SALE_EXCHANGE", "delete", "deleteBill", L);

        // ---------- 售后维修单：标准动作 + 收件/维修/配件/返还/转换新/转退货 ----------
        act("SALE_REPAIR", "submit", "submitForApproval", L);
        act("SALE_REPAIR", "approve", "approve", L, S);
        act("SALE_REPAIR", "reject", "reject", L, S, S);
        act("SALE_REPAIR", "reverseApprove", "reverseApprove", L, S);
        act("SALE_REPAIR", "void", "voidBill", L, S, S);
        act("SALE_REPAIR", "delete", "deleteBill", L);
        act("SALE_REPAIR", "receiveIn", "receiveIn", L, S);
        act("SALE_REPAIR", "startRepair", "startRepair", L, S);
        act("SALE_REPAIR", "issueParts", "issueParts", L, S);
        // completeRepair(Long, String operator, String result)：第 3 个参数为"维修结果"，界面由弹窗选择，对话中用 reason 传入
        act("SALE_REPAIR", "completeRepair", "completeRepair", L, S, S);
        act("SALE_REPAIR", "returnToCustomer", "returnToCustomer", L, S);
        act("SALE_REPAIR", "transferToExchange", "transferToExchange", L, LST, S);
        act("SALE_REPAIR", "transferToReturn", "transferToReturn", L, S);

        // ---------- 采购申请单 / 采购订单：submit(LS) + close(LSS) ----------
        purchaseWithClose("PURCHASE_REQUEST");
        purchaseWithClose("PURCHASE_ORDER");

        // ---------- 采购合同 / 入库单 / 退货单 / 发票：无 close ----------
        purchaseNoClose("PURCHASE_CONTRACT");
        purchaseNoClose("PURCHASE_INBOUND");
        purchaseNoClose("PURCHASE_RETURN");
        purchaseNoClose("PURCHASE_INVOICE");

        // ---------- 生产工单：全站通用状态机（submit/approve），reject 方法名为 rejectBill ----------
        act("PRODUCTION_ORDER", "submit", "submit", L, S);
        act("PRODUCTION_ORDER", "approve", "approve", L, S);
        act("PRODUCTION_ORDER", "reject", "rejectBill", L, S, S);
        act("PRODUCTION_ORDER", "reverseApprove", "reverseApprove", L, S);
        act("PRODUCTION_ORDER", "close", "close", L, S, S);
        act("PRODUCTION_ORDER", "reopen", "reopenOrder", L, S);
        act("PRODUCTION_ORDER", "void", "voidBill", L, S, S);
        act("PRODUCTION_ORDER", "unvoid", "unvoidBill", L, S);
        act("PRODUCTION_ORDER", "delete", "deleteBill", L);

        // ---------- 生产入库单：无 submit/closeOrder/delete ----------
        act("PRODUCTION_RECEIPT", "approve", "approve", L, S);
        act("PRODUCTION_RECEIPT", "reject", "reject", L, S, S);
        act("PRODUCTION_RECEIPT", "reverseApprove", "reverseApprove", L, S);
        act("PRODUCTION_RECEIPT", "close", "close", L, S, S);
        act("PRODUCTION_RECEIPT", "reopen", "reopenOrder", L, S);
        act("PRODUCTION_RECEIPT", "void", "voidBill", L, S, S);
        act("PRODUCTION_RECEIPT", "unvoid", "unvoidBill", L, S);

        // ---------- 生产领料单：无 closeOrder/reopen ----------
        act("MATERIAL_REQUISITION", "submit", "submitForApproval", L);
        act("MATERIAL_REQUISITION", "approve", "approve", L, S);
        act("MATERIAL_REQUISITION", "reject", "reject", L, S, S);
        act("MATERIAL_REQUISITION", "reverseApprove", "reverseApprove", L, S);
        act("MATERIAL_REQUISITION", "close", "close", L, S, S);
        act("MATERIAL_REQUISITION", "void", "voidBill", L, S, S);
        act("MATERIAL_REQUISITION", "unvoid", "unvoidBill", L, S);
        act("MATERIAL_REQUISITION", "delete", "deleteBill", L);

        // ---------- 生产退料单 / 生产补料单（2026-11-22 二期补登）：与领料单同一 Service，动作矩阵一致 ----------
        for (String bt : new String[]{"PRODUCTION_RETURN", "PRODUCTION_SUPPLEMENT"}) {
            act(bt, "submit", "submitForApproval", L);
            act(bt, "approve", "approve", L, S);
            act(bt, "reject", "reject", L, S, S);
            act(bt, "reverseApprove", "reverseApprove", L, S);
            act(bt, "close", "close", L, S, S);
            act(bt, "void", "voidBill", L, S, S);
            act(bt, "unvoid", "unvoidBill", L, S);
            act(bt, "delete", "deleteBill", L);
        }

        // ---------- 库存调拨单：无 closeOrder ----------
        act("STOCK_TRANSFER", "submit", "submitForApproval", L);
        act("STOCK_TRANSFER", "approve", "approve", L, S);
        act("STOCK_TRANSFER", "reject", "reject", L, S, S);
        act("STOCK_TRANSFER", "reverseApprove", "reverseApprove", L, S);
        act("STOCK_TRANSFER", "close", "close", L, S, S);
        act("STOCK_TRANSFER", "reopen", "reopenOrder", L, S);
        act("STOCK_TRANSFER", "void", "voidBill", L, S, S);
        act("STOCK_TRANSFER", "unvoid", "unvoidBill", L, S);
        act("STOCK_TRANSFER", "delete", "deleteBill", L);

        // ---------- 库存盘点单：无 closeOrder/reopen，扩展 startCheck/finishCheck（单参） ----------
        act("STOCK_CHECK", "submit", "submitForApproval", L);
        act("STOCK_CHECK", "approve", "approve", L, S);
        act("STOCK_CHECK", "reject", "reject", L, S, S);
        act("STOCK_CHECK", "reverseApprove", "reverseApprove", L, S);
        act("STOCK_CHECK", "close", "close", L, S, S);
        act("STOCK_CHECK", "reopen", "reopenOrder", L, S);
        act("STOCK_CHECK", "void", "voidBill", L, S, S);
        act("STOCK_CHECK", "unvoid", "unvoidBill", L, S);
        act("STOCK_CHECK", "delete", "deleteBill", L);
        act("STOCK_CHECK", "startCheck", "startCheck", L);
        act("STOCK_CHECK", "finishCheck", "finishCheck", L);

        // ---------- 其他入库单 / 其他出库单：无 closeOrder ----------
        for (String bt : new String[]{"OTHER_RECEIPT", "OTHER_ISSUE"}) {
            act(bt, "submit", "submitForApproval", L);
            act(bt, "approve", "approve", L, S);
            act(bt, "reject", "reject", L, S, S);
            act(bt, "reverseApprove", "reverseApprove", L, S);
            act(bt, "close", "close", L, S, S);
            act(bt, "reopen", "reopenOrder", L, S);
            act(bt, "void", "voidBill", L, S, S);
            act(bt, "unvoid", "unvoidBill", L, S);
            act(bt, "delete", "deleteBill", L);
        }

        // ---------- 委外订单：无 closeOrder/delete；reopen 归一为 unclose(LS)；扩展 release/complete(单参) ----------
        act("SUBCONTRACT_ORDER", "submit", "submitForApproval", L);
        act("SUBCONTRACT_ORDER", "approve", "approve", L, S);
        act("SUBCONTRACT_ORDER", "reject", "reject", L, S, S);
        act("SUBCONTRACT_ORDER", "reverseApprove", "reverseApprove", L, S);
        act("SUBCONTRACT_ORDER", "close", "close", L, S, S);
        act("SUBCONTRACT_ORDER", "reopen", "unclose", L, S);
        act("SUBCONTRACT_ORDER", "void", "voidBill", L, S, S);
        act("SUBCONTRACT_ORDER", "unvoid", "unvoidBill", L, S);
        act("SUBCONTRACT_ORDER", "release", "release", L);
        act("SUBCONTRACT_ORDER", "complete", "complete", L);

        // ---------- 委外发料单：无 close/closeOrder/reopen/delete；扩展 issue(LS) ----------
        act("SUBCONTRACT_DELIVERY", "submit", "submitForApproval", L);
        act("SUBCONTRACT_DELIVERY", "approve", "approve", L, S);
        act("SUBCONTRACT_DELIVERY", "reject", "reject", L, S, S);
        act("SUBCONTRACT_DELIVERY", "reverseApprove", "reverseApprove", L, S);
        act("SUBCONTRACT_DELIVERY", "void", "voidBill", L, S, S);
        act("SUBCONTRACT_DELIVERY", "unvoid", "unvoidBill", L, S);

        // ---------- 委外入库单：无 close/closeOrder/reopen/delete ----------
        act("SUBCONTRACT_RECEIPT", "submit", "submitForApproval", L);
        act("SUBCONTRACT_RECEIPT", "approve", "approve", L, S);
        act("SUBCONTRACT_RECEIPT", "reject", "reject", L, S, S);
        act("SUBCONTRACT_RECEIPT", "reverseApprove", "reverseApprove", L, S);
        act("SUBCONTRACT_RECEIPT", "void", "voidBill", L, S, S);
        act("SUBCONTRACT_RECEIPT", "unvoid", "unvoidBill", L, S);

        // ---------- 打样单：无 close/delete；扩展 startSample/completeSample/confirmSample(LS) ----------
        act("SAMPLE_ORDER", "submit", "submit", L, S);
        act("SAMPLE_ORDER", "approve", "approve", L, S);
        act("SAMPLE_ORDER", "reject", "reject", L, S, S);
        act("SAMPLE_ORDER", "reverseApprove", "reverseApprove", L, S);
        act("SAMPLE_ORDER", "closeOrder", "closeOrder", L, S);
        act("SAMPLE_ORDER", "reopen", "reopenOrder", L, S);
        act("SAMPLE_ORDER", "void", "voidBill", L, S, S);
        act("SAMPLE_ORDER", "unvoid", "unvoidBill", L, S);
        act("SAMPLE_ORDER", "startSample", "startSample", L, S);
        act("SAMPLE_ORDER", "completeSample", "completeSample", L, S);
        act("SAMPLE_ORDER", "confirmSample", "confirmSample", L, S);

        // ---------- 免费样品单：无 close；delete 方法名为 delete(L)；扩展 ship(L,S,S,S)/complete(LS) ----------
        act("FREE_SAMPLE", "submit", "submit", L, S);
        act("FREE_SAMPLE", "approve", "approve", L, S);
        act("FREE_SAMPLE", "reject", "reject", L, S, S);
        act("FREE_SAMPLE", "reverseApprove", "reverseApprove", L, S);
        act("FREE_SAMPLE", "closeOrder", "closeOrder", L, S);
        act("FREE_SAMPLE", "reopen", "reopenOrder", L, S);
        act("FREE_SAMPLE", "void", "voidBill", L, S, S);
        act("FREE_SAMPLE", "unvoid", "unvoidBill", L, S);
        act("FREE_SAMPLE", "delete", "delete", L);
        act("FREE_SAMPLE", "ship", "ship", L, S, S, S);
        act("FREE_SAMPLE", "complete", "complete", L, S);

        // ---------- 收款单 / 付款单：共用一个 Service，方法名不同；无 closeOrder ----------
        act("PAYMENT_RECEIPT", "submit", "submitReceipt", L, S);
        act("PAYMENT_RECEIPT", "approve", "confirmReceipt", L, S);
        act("PAYMENT_RECEIPT", "reject", "rejectReceipt", L, S, S);
        act("PAYMENT_RECEIPT", "reverseApprove", "reverseReceipt", L, S);
        act("PAYMENT_RECEIPT", "close", "closeReceipt", L, S, S);
        act("PAYMENT_RECEIPT", "reopen", "reopenReceipt", L, S);
        act("PAYMENT_RECEIPT", "void", "voidReceipt", L, S, S);
        act("PAYMENT_RECEIPT", "unvoid", "unvoidReceipt", L, S);
        act("PAYMENT_RECEIPT", "delete", "deleteReceipt", L);

        act("PAYMENT_VOUCHER", "submit", "submitVoucher", L, S);
        act("PAYMENT_VOUCHER", "approve", "confirmVoucher", L, S);
        act("PAYMENT_VOUCHER", "reject", "rejectVoucher", L, S, S);
        act("PAYMENT_VOUCHER", "reverseApprove", "reverseVoucher", L, S);
        act("PAYMENT_VOUCHER", "close", "closeVoucher", L, S, S);
        act("PAYMENT_VOUCHER", "reopen", "reopenVoucher", L, S);
        act("PAYMENT_VOUCHER", "void", "voidVoucher", L, S, S);
        act("PAYMENT_VOUCHER", "unvoid", "unvoidVoucher", L, S);
        act("PAYMENT_VOUCHER", "delete", "deleteVoucher", L);

        // ---------- 采购费用单：仅 approve/void/delete + allocateCost，均为单参 ----------
        act("PURCHASE_EXPENSE", "approve", "audit", L);
        act("PURCHASE_EXPENSE", "void", "voidExpense", L);
        act("PURCHASE_EXPENSE", "delete", "delete", L);
        act("PURCHASE_EXPENSE", "allocateCost", "allocateCost", L);

        // ---------- 预付款单：无 close；void/delete 单参；扩展 writeOff(L,List)/reverseWriteOff(L,BigDecimal) ----------
        act("PURCHASE_PRE_PAYMENT", "submit", "submit", L);
        act("PURCHASE_PRE_PAYMENT", "approve", "audit", L);
        act("PURCHASE_PRE_PAYMENT", "reject", "reject", L, S, S);
        act("PURCHASE_PRE_PAYMENT", "reverseApprove", "reverseApprove", L, S);
        act("PURCHASE_PRE_PAYMENT", "closeOrder", "closeOrder", L, S);
        act("PURCHASE_PRE_PAYMENT", "reopen", "reopenOrder", L, S);
        act("PURCHASE_PRE_PAYMENT", "void", "voidPayment", L);
        act("PURCHASE_PRE_PAYMENT", "unvoid", "unvoidBill", L, S);
        act("PURCHASE_PRE_PAYMENT", "delete", "delete", L);
        act("PURCHASE_PRE_PAYMENT", "writeOff", "writeOff", L, LST);
        act("PURCHASE_PRE_PAYMENT", "reverseWriteOff", "reverseWriteOff", L, BD);

        // ---------- BOM：无 close/closeOrder/reopen/delete；reject 为两参(无反审原因) ----------
        act("BOM", "submit", "submit", L, S);
        act("BOM", "approve", "approve", L, S);
        act("BOM", "reject", "reject", L, S);
        act("BOM", "reverseApprove", "reverseApprove", L, S);
        act("BOM", "void", "voidBill", L, S, S);
        act("BOM", "unvoid", "unvoidBill", L, S);
        act("BOM", "enable", "enable", L, S);
        act("BOM", "disable", "disable", L, S);
        act("BOM", "setDefault", "setDefault", L, S);

        // ---------- 生产组装单 / 生产拆卸单：标准状态机（submit 单参；reject 两参(原因)） ----------
        act("PRODUCE_ASSEMBLE", "submit", "submit", L);
        act("PRODUCE_ASSEMBLE", "approve", "approve", L, S);
        act("PRODUCE_ASSEMBLE", "reject", "reject", L, S);
        act("PRODUCE_ASSEMBLE", "reverseApprove", "reverseApprove", L, S);
        act("PRODUCE_ASSEMBLE", "void", "voidBill", L, S, S);
        act("PRODUCE_ASSEMBLE", "unvoid", "unvoidBill", L, S);
        act("PRODUCE_ASSEMBLE", "closeOrder", "closeOrder", L, S);
        act("PRODUCE_ASSEMBLE", "reopen", "reopenOrder", L, S);

        act("PRODUCE_DISASSEMBLE", "submit", "submit", L);
        act("PRODUCE_DISASSEMBLE", "approve", "approve", L, S);
        act("PRODUCE_DISASSEMBLE", "reject", "reject", L, S);
        act("PRODUCE_DISASSEMBLE", "reverseApprove", "reverseApprove", L, S);
        act("PRODUCE_DISASSEMBLE", "void", "voidBill", L, S, S);
        act("PRODUCE_DISASSEMBLE", "unvoid", "unvoidBill", L, S);
        act("PRODUCE_DISASSEMBLE", "closeOrder", "closeOrder", L, S);
        act("PRODUCE_DISASSEMBLE", "reopen", "reopenOrder", L, S);

        // ================================================================
        // 2026-09-28 批次3：单据动作矩阵补全（逐条对 Controller/Service 源码核实）
        // ================================================================

        // ---------- 销售预收款单：submit/audit/void 单参；writeOff(L,List) 需明细 ----------
        act("SALE_PRE_RECEIPT", "submit", "submit", L);
        act("SALE_PRE_RECEIPT", "approve", "audit", L);
        act("SALE_PRE_RECEIPT", "reject", "reject", L, S, S);
        act("SALE_PRE_RECEIPT", "reverseApprove", "reverseApprove", L, S);
        act("SALE_PRE_RECEIPT", "void", "voidReceipt", L);
        act("SALE_PRE_RECEIPT", "unvoid", "unvoidBill", L, S);
        act("SALE_PRE_RECEIPT", "closeOrder", "closeOrder", L, S);
        act("SALE_PRE_RECEIPT", "reopen", "reopenOrder", L, S);
        act("SALE_PRE_RECEIPT", "writeOff", "writeOff", L, LST);
        act("SALE_PRE_RECEIPT", "delete", "delete", L);

        // ---------- 费用分摊单（运营费用）：标准状态机（submit/approve 双参） ----------
        act("OPERATION_EXPENSE", "submit", "submit", L, S);
        act("OPERATION_EXPENSE", "approve", "approve", L, S);
        act("OPERATION_EXPENSE", "reject", "reject", L, S, S);
        act("OPERATION_EXPENSE", "reverseApprove", "reverseApprove", L, S);
        act("OPERATION_EXPENSE", "void", "voidBill", L, S, S);
        act("OPERATION_EXPENSE", "unvoid", "unvoidBill", L, S);
        act("OPERATION_EXPENSE", "closeOrder", "closeOrder", L, S);
        act("OPERATION_EXPENSE", "reopen", "reopenOrder", L, S);
        act("OPERATION_EXPENSE", "delete", "delete", L);

        // ---------- 成本核算单：已核实 Service 接口确无 submit/approve 端点 ----------
        act("COST_CALCULATION", "reject", "reject", L, S, S);
        act("COST_CALCULATION", "reverseApprove", "reverseApprove", L, S);
        act("COST_CALCULATION", "void", "voidBill", L, S, S);
        act("COST_CALCULATION", "unvoid", "unvoidBill", L, S);
        act("COST_CALCULATION", "closeOrder", "closeOrder", L, S);
        act("COST_CALCULATION", "reopen", "reopenOrder", L, S);

        // ---------- 工序工资核算单：仅 submit/void/unvoid ----------
        act("WAGE_SETTLEMENT", "submit", "submit", L, S);
        act("WAGE_SETTLEMENT", "void", "voidBill", L, S, S);
        act("WAGE_SETTLEMENT", "unvoid", "unvoidBill", L, S);

        // ---------- 销售费用单：仅 void/allocateCost（均为单参） ----------
        act("SALE_EXPENSE", "void", "voidExpense", L);
        act("SALE_EXPENSE", "allocateCost", "allocateCost", L);

        // ---------- 客户对账单 / 供应商对账单：confirm + close（均为单参） ----------
        act("SALE_STATEMENT", "confirm", "confirm", L);
        act("SALE_STATEMENT", "close", "close", L);
        act("PURCHASE_STATEMENT", "confirm", "confirm", L);
        act("PURCHASE_STATEMENT", "close", "close", L);

        // ---------- 工序汇报单：标准状态机（submit/approve 双参） ----------
        act("OPERATION_REPORT", "submit", "submit", L, S);
        act("OPERATION_REPORT", "approve", "approve", L, S);
        act("OPERATION_REPORT", "reject", "reject", L, S, S);
        act("OPERATION_REPORT", "reverseApprove", "reverseApprove", L, S);
        act("OPERATION_REPORT", "void", "voidBill", L, S, S);
        act("OPERATION_REPORT", "unvoid", "unvoidBill", L, S);
        act("OPERATION_REPORT", "closeOrder", "closeOrder", L, S);
        act("OPERATION_REPORT", "reopen", "reopenOrder", L, S);

        // ---------- 委外退料单：submitForApproval(L) ----------
        act("SUBCONTRACT_RETURN", "submit", "submitForApproval", L);
        act("SUBCONTRACT_RETURN", "approve", "approve", L, S);
        act("SUBCONTRACT_RETURN", "reject", "reject", L, S, S);
        act("SUBCONTRACT_RETURN", "reverseApprove", "reverseApprove", L, S);
        act("SUBCONTRACT_RETURN", "void", "voidBill", L, S, S);
        act("SUBCONTRACT_RETURN", "unvoid", "unvoidBill", L, S);

        // ---------- 委外补料单：submitForApproval(L) ----------
        act("SUBCONTRACT_SUPPLEMENT", "submit", "submitForApproval", L);
        act("SUBCONTRACT_SUPPLEMENT", "approve", "approve", L, S);
        act("SUBCONTRACT_SUPPLEMENT", "reject", "reject", L, S, S);
        act("SUBCONTRACT_SUPPLEMENT", "reverseApprove", "reverseApprove", L, S);
        act("SUBCONTRACT_SUPPLEMENT", "void", "voidBill", L, S, S);
        act("SUBCONTRACT_SUPPLEMENT", "unvoid", "unvoidBill", L, S);

        // ---------- MRP计划单：approve/reject/reverseApprove/execute/void/unvoid/closeOrder/reopen ----------
        act("MRP_PLAN", "approve", "approvePlan", L, S);
        act("MRP_PLAN", "reject", "rejectPlan", L, S, S);
        act("MRP_PLAN", "reverseApprove", "reverseApprovePlan", L, S);
        act("MRP_PLAN", "execute", "executePlan", L, S);
        act("MRP_PLAN", "void", "voidBill", L, S, S);
        act("MRP_PLAN", "unvoid", "unvoidBill", L, S);
        act("MRP_PLAN", "closeOrder", "closeOrder", L, S);
        act("MRP_PLAN", "reopen", "reopenOrder", L, S);
    }

    /** 销售订单：标准动作 + close(LSS) */
    private void sactionWithCloseLSS(String bt) {
        act(bt, "submit", "submitForApproval", L);
        act(bt, "approve", "approve", L, S);
        act(bt, "reject", "reject", L, S, S);
        act(bt, "reverseApprove", "reverseApprove", L, S);
        act(bt, "close", "close", L, S, S);
        act(bt, "closeOrder", "closeOrder", L, S);
        act(bt, "reopen", "reopenOrder", L, S);
        act(bt, "void", "voidBill", L, S, S);
        act(bt, "unvoid", "unvoidBill", L, S);
        act(bt, "delete", "deleteBill", L);
    }

    /** 销售类：submitForApproval(L) + 无 close */
    private void sactionNoClose(String bt) {
        act(bt, "submit", "submitForApproval", L);
        act(bt, "approve", "approve", L, S);
        act(bt, "reject", "reject", L, S, S);
        act(bt, "reverseApprove", "reverseApprove", L, S);
        act(bt, "closeOrder", "closeOrder", L, S);
        act(bt, "reopen", "reopenOrder", L, S);
        act(bt, "void", "voidBill", L, S, S);
        act(bt, "unvoid", "unvoidBill", L, S);
        act(bt, "delete", "deleteBill", L);
    }

    /** 采购类：submit(L,S) + close(L,S,S) */
    private void purchaseWithClose(String bt) {
        act(bt, "submit", "submit", L, S);
        act(bt, "approve", "approve", L, S);
        act(bt, "reject", "reject", L, S, S);
        act(bt, "reverseApprove", "reverseApprove", L, S);
        act(bt, "close", "close", L, S, S);
        act(bt, "closeOrder", "closeOrder", L, S);
        act(bt, "reopen", "reopenOrder", L, S);
        act(bt, "void", "voidBill", L, S, S);
        act(bt, "unvoid", "unvoidBill", L, S);
        act(bt, "delete", "deleteBill", L);
    }

    /** 采购类：submit(L,S) + 无 close */
    private void purchaseNoClose(String bt) {
        act(bt, "submit", "submit", L, S);
        act(bt, "approve", "approve", L, S);
        act(bt, "reject", "reject", L, S, S);
        act(bt, "reverseApprove", "reverseApprove", L, S);
        act(bt, "closeOrder", "closeOrder", L, S);
        act(bt, "reopen", "reopenOrder", L, S);
        act(bt, "void", "voidBill", L, S, S);
        act(bt, "unvoid", "unvoidBill", L, S);
        act(bt, "delete", "deleteBill", L);
    }

    private void act(String billType, String action, String method, Class<?>... paramTypes) {
        String suffix = ACTION_BUTTON_SUFFIX.get(action);
        String label = ACTION_LABELS.getOrDefault(action, action);
        actionMatrix.computeIfAbsent(billType, k -> new LinkedHashMap<>())
                .put(action, new ActionSpec(method, label, paramTypes, argStyle(paramTypes), suffix));
    }

    /** 参数样式（仅作可读性，实际调度按 paramTypes 精确匹配） */
    private static String argStyle(Class<?>[] t) {
        if (t.length == 1) return "ID";
        if (t.length == 2) {
            if (t[1] == S) return "ID_OPERATOR";
            if (t[1] == LST) return "ID_OPERATOR_LIST";
            if (t[1] == BD) return "ID_AMOUNT";
            return "ID_EXTRA";
        }
        if (t.length == 3) return "ID_OPERATOR_REASON";
        return "ID_OPERATOR_OPERATOR_OPERATOR";
    }

    // ==================================================================
    // 下推关系登记（仅登记已核实存在后端生成接口的链路）
    // ==================================================================

    private void buildPushMatrix() {
        push("SALE_QUOTE", "SALE_ORDER", "pushToOrder", L, S);
        push("SALE_ORDER", "SALE_DELIVERY", "pushToDelivery", L, S);
        push("SALE_ORDER", "PURCHASE_ORDER", "pushToPurchase", L, S);
        push("SALE_DELIVERY", "SALE_INVOICE", "pushToInvoice", L, S);
        push("PURCHASE_REQUEST", "PURCHASE_ORDER", "pushToOrder", L, S);
        push("PURCHASE_ORDER", "PURCHASE_INBOUND", "pushToInbound", L, S);
        push("PURCHASE_INBOUND", "PURCHASE_INVOICE", "pushToInvoice", L, S);
        // 生产工单下推（无需额外业务参数的三条）
        push("PRODUCTION_ORDER", "MATERIAL_REQUISITION", "pushToMaterialRequisition", L);
        push("PRODUCTION_ORDER", "PRODUCTION_RECEIPT", "pushToProductionReceipt", L);
        push("PRODUCTION_ORDER", "SUBCONTRACT_ORDER", "pushToSubcontractOrder", L);
        // 生产工单 → 工序汇报单：原生方法为多参（需指定工序），
        // 界面【下推】按钮实际不传工序，故登记"不指定工序"的单参便捷重载，与界面产出完全一致。
        push("PRODUCTION_ORDER", "OPERATION_REPORT", "pushToOperationReport", L);
        // 工序汇报单下推
        push("OPERATION_REPORT", "PRODUCTION_RECEIPT", "pushToProductionReceipt", L);
        // 委外订单下推
        push("SUBCONTRACT_ORDER", "SUBCONTRACT_DELIVERY", "pushPick", L);
        push("SUBCONTRACT_ORDER", "SUBCONTRACT_RECEIPT", "pushInbound", L);
        // 打样单转 BOM
        push("SAMPLE_ORDER", "BOM", "convertToBom", L, S);
    }

    private void push(String source, String target, String method, Class<?>... paramTypes) {
        pushMatrix.computeIfAbsent(source, k -> new ArrayList<>())
                .add(new PushTarget(target, labelOf(target), method, paramTypes, argStyle(paramTypes)));
    }

    private void register(String billType, String label, String idField, Object service) {
        registry.put(billType, new BillMeta(label, idField, service));
    }

    private String labelOf(String billType) {
        BillMeta meta = registry.get(billType);
        if (meta != null) return meta.label();
        return extraLabels.getOrDefault(billType, billType);
    }

    // ==================================================================
    // 对外查询
    // ==================================================================

    /** 取单据主表 Service（找不到返回 null） */
    public Object service(String billType) {
        BillMeta meta = registry.get(billType);
        if (meta != null) return meta.service();
        return extraServices.get(billType);
    }

    /** 取单据中文名 */
    public String label(String billType) {
        return labelOf(billType);
    }

    /** 取单据主键 Java 字段名 */
    public String idField(String billType) {
        BillMeta meta = registry.get(billType);
        return meta == null ? null : meta.idField();
    }

    /** 全部已登记的 billType（2026-09-28 批次3 补登后为 43 种） */
    public Set<String> allBillTypes() {
        return registry.keySet();
    }

    /** 返回 "billType（中文名）" 逗号拼接串，供工具描述用 */
    public String describableList() {
        return registry.entrySet().stream()
                .map(e -> e.getKey() + "（" + e.getValue().label() + "）")
                .collect(Collectors.joining("，"));
    }

    /** 该单据支持的动作（保持登记顺序）；无登记返回空 Map */
    public LinkedHashMap<String, ActionSpec> actions(String billType) {
        LinkedHashMap<String, ActionSpec> m = actionMatrix.get(billType);
        return m == null ? new LinkedHashMap<>() : m;
    }

    /** 取单个动作规格（不支持返回 null） */
    public ActionSpec actionSpec(String billType, String action) {
        if (billType == null || action == null) return null;
        LinkedHashMap<String, ActionSpec> m = actionMatrix.get(billType);
        return m == null ? null : m.get(action);
    }

    /** 该单据是否支持该动作 */
    public boolean supports(String billType, String action) {
        return actionSpec(billType, action) != null;
    }

    /** 该单据可下推的目标（无则空列表） */
    public List<PushTarget> pushTargets(String sourceType) {
        List<PushTarget> list = pushMatrix.get(sourceType);
        return list == null ? Collections.emptyList() : list;
    }

    /** 该下推组合是否已登记（有后端接口） */
    public boolean supportsPush(String sourceType, String targetType) {
        return pushTargets(sourceType).stream().anyMatch(t -> t.targetType().equals(targetType));
    }

    /** 全部有下推能力的源单类型（保持登记顺序） */
    public Set<String> pushSourceTypes() {
        return pushMatrix.keySet();
    }

    /** 是否为「后端无生成接口」的下推关系（需引导到界面选单） */
    public boolean isKnownNoBackendRelation(String sourceType, String targetType) {
        if (sourceType == null || targetType == null) return false;
        return NO_BACKEND_RELATIONS.contains(sourceType + "->" + targetType);
    }

    /** 「界面选单」引导路径：目标单据中文名（前端新建页路由） */
    public String guidePath(String sourceType, String targetType) {
        String label = labelOf(targetType);
        String route = TARGET_ROUTES.get(targetType);
        return route == null ? label : label + "（" + route + "）";
    }

    // ==================================================================
    // 数据结构
    // ==================================================================

    /**
     * 单据动作规格。
     *
     * @param method           目标 Service 方法名
     * @param label            动作中文名
     * @param paramTypes       精确参数类型（调度按此匹配，不做模糊匹配）
     * @param argStyle         参数样式（仅作可读性）
     * @param actionCodeSuffix 角色按钮码后缀（null 表示无对应按钮）
     */
    public record ActionSpec(String method, String label, Class<?>[] paramTypes,
                             String argStyle, String actionCodeSuffix) {
    }

    /**
     * 下推目标规格。
     *
     * @param targetType  目标单据类型
     * @param targetLabel 目标单据中文名
     * @param method      源单 Service 的下推方法名
     * @param paramTypes  精确参数类型
     * @param argStyle    参数样式（仅作可读性）
     */
    public record PushTarget(String targetType, String targetLabel, String method,
                             Class<?>[] paramTypes, String argStyle) {
    }

    private record BillMeta(String label, String idField, Object service) {
    }
}
