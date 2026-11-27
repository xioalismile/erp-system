package com.erp.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import com.erp.approval.entity.ApprovalTask;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.dashboard.dto.*;
import com.erp.dashboard.service.DashboardService;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.inventory.entity.StockCheck;
import com.erp.inventory.entity.StockTransfer;
import com.erp.inventory.mapper.StockCheckMapper;
import com.erp.inventory.mapper.StockTransferMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.mapper.EmployeeMapper;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import com.erp.purchase.entity.*;
import com.erp.purchase.mapper.*;
import com.erp.sale.entity.*;
import com.erp.sale.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个人工作台相关查询实现
 * 注意：保留原有 DashboardServiceImpl 的全局统计方法不被破坏，本类作为补充。
 * 实际上 DashboardServiceImpl 已 implements DashboardService，新方法在 DashboardServiceImpl 中实现。
 * 本类作为辅助工具类被 DashboardServiceImpl 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyDashboardHelper {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleDeliveryMapper saleDeliveryMapper;
    private final SaleReturnMapper saleReturnMapper;
    private final SaleInvoiceMapper saleInvoiceMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final StockTransferMapper stockTransferMapper;
    private final StockCheckMapper stockCheckMapper;
    private final WorkOrderMapper workOrderMapper;
    private final OperationReportMapper operationReportMapper;
    private final ProductionReceiptMapper productionReceiptMapper;
    /** 工单实际工序（2026-09-27 第4期：工序到期提醒改扫真实工序，不再扫工艺路线模板） */
    private final WorkOrderOperationMapper workOrderOperationMapper;
    /** 工序负责人（员工）→ 登录账号 的解析（工序到期通知给负责人，而不只是制单人） */
    private final EmployeeMapper employeeMapper;
    private final SysUserMapper sysUserMapper;
    private final AccountsReceivableMapper accountsReceivableMapper;
    private final AccountsPayableMapper accountsPayableMapper;
    private final CustomerMapper customerMapper;
    private final MaterialMapper materialMapper;
    private final ApprovalRuntimeService approvalRuntimeService;

    /** 【2026-11-06 化妆品批记录】待检验（批次检验记录未出结论） */
    private final com.erp.qc.mapper.QcRecordMapper qcRecordMapper;
    /** 【2026-11-06 化妆品批记录】待放行（放行单未生效） + 留样到期提醒 */
    private final com.erp.production.mapper.ReleaseOrderMapper releaseOrderMapper;
    private final com.erp.production.mapper.SampleRetentionMapper sampleRetentionMapper;

    // 待审核状态字符串（系统统一为"待审核"）
    private static final String PENDING_APPROVAL = "待审核";
    private static final String DRAFT = "草稿";

    // ===== 顶部统计 =====

    public MyOverviewDTO getMyOverview(String username, Long employeeId, List<String> roles) {
        MyOverviewDTO dto = new MyOverviewDTO();
        if (roles == null) roles = Collections.emptyList();
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("MANAGER");
        boolean isSales = roles.contains("SALES");
        boolean isPurchase = roles.contains("PURCHASE");
        boolean isWarehouse = roles.contains("WAREHOUSE");
        boolean isProduction = roles.contains("PRODUCTION");
        boolean isFinance = roles.contains("FINANCE");

        // 1. 待我审批
        dto.setPendingApprovalCount(getMyPendingApprovals(username, employeeId, roles).size());

        // 2. 即将超期 / 已超期
        dto.setOverdueCount(getMyOverdueTasks(username, employeeId, roles).size());

        // 3. 我的草稿
        dto.setMyDraftCount(getMyDrafts(username, employeeId, roles).size());

        // 4. 应收应付跟进
        dto.setArApFollowUpCount(getMyArApFollowUp(username, employeeId, roles).size());

        // 5. 我的销售金额（销售员或管理员可见）
        if (isAdmin || isSales) {
            dto.setMySaleAmount(calcMySaleAmount(username, employeeId, isAdmin));
        }
        // 6. 我的采购金额（采购员或管理员可见）
        if (isAdmin || isPurchase) {
            dto.setMyPurchaseAmount(calcMyPurchaseAmount(username, employeeId, isAdmin));
        }
        // 7. 库存预警
        if (isAdmin || isWarehouse) {
            dto.setInventoryWarningCount(calcInventoryWarning());
        }
        return dto;
    }

    // ===== 待我审批 =====

    public List<MyTodoDTO> getMyPendingApprovals(String username, Long employeeId, List<String> roles) {
        if (roles == null) roles = Collections.emptyList();
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("MANAGER");
        boolean isSales = roles.contains("SALES");
        boolean isPurchase = roles.contains("PURCHASE");
        boolean isWarehouse = roles.contains("WAREHOUSE");
        boolean isProduction = roles.contains("PRODUCTION");
        boolean isFinance = roles.contains("FINANCE");

        List<MyTodoDTO> list = new ArrayList<>();

        // 销售：管理员看全部，销售员只看自己的
        if (isAdmin || isSales) {
            addSalePendingApprovals(list, username, isAdmin);
        }
        if (isAdmin || isPurchase) {
            addPurchasePendingApprovals(list, username, isAdmin);
        }
        if (isAdmin || isWarehouse) {
            addInventoryPendingApprovals(list, username, isAdmin);
        }
        if (isAdmin || isProduction) {
            addProductionPendingApprovals(list, username, isAdmin);
        }
        // 应收应付没有"待审核"流程，跳过

        // 5. 审批引擎待办任务（当前用户作为审批人被指派的待办，不区分模块角色）
        addApprovalEngineTasks(list, username);

        // 6. 【化妆品批记录】待检验 / 待放行 / 留样到期
        if (isAdmin || isProduction || isWarehouse) {
            addBatchRecordTodos(list);
        }

        // 按 type+id 去重（同一单据可能同时命中模块查询与引擎待办）
        List<MyTodoDTO> deduped = new ArrayList<>(list.stream().collect(
                Collectors.toMap(t -> t.getType() + "#" + t.getId(), t -> t, (a, b) -> a,
                        java.util.LinkedHashMap::new)).values());
        deduped.sort(Comparator.comparing(MyTodoDTO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return deduped;
    }

    /**
     * 仅「审批引擎真实指派给我的待办」（不含模块维度的待审核单据）。
     * 供通知推送使用：铃铛「待审批」必须与「我的待办」页面同源，
     * 否则会把不属于该用户的待审批单据推给非审批人（历史 bug：按 username 猜角色 → 误判为 ADMIN → 全员推送）。
     */
    public List<MyTodoDTO> getEnginePendingApprovals(String username) {
        List<MyTodoDTO> list = new ArrayList<>();
        addApprovalEngineTasks(list, username);
        return list;
    }

    /** 审批引擎单据类型 → 中文名/前端路由 */
    private static final Map<String, String[]> BILL_TYPE_META = Map.ofEntries(
            Map.entry("SALE_QUOTE", new String[]{"销售报价单", "/sale/quotes/"}),
            Map.entry("SALE_ORDER", new String[]{"销售订单", "/sale/orders/"}),
            Map.entry("SALE_CONTRACT", new String[]{"销售合同", "/sale/contracts/"}),
            Map.entry("SALE_DELIVERY", new String[]{"销售出库单", "/sale/deliveries/"}),
            Map.entry("SALE_RETURN", new String[]{"销售退货单", "/sale/returns/"}),
            Map.entry("SALE_INVOICE", new String[]{"销售发票", "/sale/invoices/"}),
            Map.entry("PURCHASE_REQUEST", new String[]{"采购申请单", "/purchase/requests/"}),
            Map.entry("PURCHASE_ORDER", new String[]{"采购订单", "/purchase/orders/"}),
            Map.entry("PURCHASE_CONTRACT", new String[]{"采购合同", "/purchase/contracts/"}),
            Map.entry("PURCHASE_INBOUND", new String[]{"采购入库单", "/purchase/inbounds/"}),
            Map.entry("PURCHASE_RETURN", new String[]{"采购退货单", "/purchase/returns/"}),
            Map.entry("PURCHASE_INVOICE", new String[]{"采购发票", "/purchase/invoices/"}),
            Map.entry("STOCK_TRANSFER", new String[]{"库存调拨单", "/inventory/stock-transfers/edit/"}),
            Map.entry("STOCK_CHECK", new String[]{"库存盘点单", "/inventory/stock-checks/edit/"}),
            Map.entry("MATERIAL_REQUISITION", new String[]{"生产领料单", "/inventory/material-requisitions/"}),
            Map.entry("MATERIAL_RETURN", new String[]{"生产退料单", "/production/returns/"}),
            Map.entry("MATERIAL_SUPPLEMENT", new String[]{"生产补料单", "/production/supplements/"}),
            Map.entry("OTHER_ISSUE", new String[]{"其他出库单", "/inventory/other-issues/"}),
            Map.entry("OTHER_RECEIPT", new String[]{"其他入库单", "/inventory/other-receipts/"}),
            Map.entry("WORK_ORDER", new String[]{"生产工单", "/production/workorders/"}),
            Map.entry("PRODUCTION_ORDER", new String[]{"生产工单", "/production/workorders/"}),
            Map.entry("OPERATION_REPORT", new String[]{"工序汇报", "/production/operation-reports/"}),
            Map.entry("PRODUCTION_RECEIPT", new String[]{"生产入库单", "/production/receipts/"}),
            Map.entry("BOM", new String[]{"BOM清单", "/masterdata/bom/"}),
            Map.entry("SAMPLE_ORDER", new String[]{"打样单", "/sample/edit/"}),
            Map.entry("FREE_SAMPLE", new String[]{"免费样品订单", "/freesample/edit/"}),
            Map.entry("SUBCONTRACT_ORDER", new String[]{"委外加工单", "/subcontract/orders/"}),
            Map.entry("SUBCONTRACT_DELIVERY", new String[]{"委外发料单", "/subcontract/picks/"}),
            Map.entry("SUBCONTRACT_RECEIPT", new String[]{"委外入库单", "/subcontract/inbounds/"}),
            Map.entry("SUBCONTRACT_RETURN", new String[]{"委外退料单", "/subcontract/returns/"}),
            Map.entry("SUBCONTRACT_SUPPLEMENT", new String[]{"委外补料单", "/subcontract/supplements/"}),
            Map.entry("PRODUCE_ASSEMBLE", new String[]{"生产组装单", "/production/assemble/"}),
            Map.entry("PRODUCE_DISASSEMBLE", new String[]{"生产拆卸单", "/production/disassemble/"}),
            Map.entry("PURCHASE_EXPENSE", new String[]{"采购费用单", null}),
            Map.entry("PURCHASE_PRE_PAYMENT", new String[]{"采购预付款单", "/finance/purchase-pre-payments/edit/"}),
            Map.entry("SALE_PRE_RECEIPT", new String[]{"销售预收款单", "/finance/sale-pre-receipts/edit/"}),
            Map.entry("PAYMENT_RECEIPT", new String[]{"收款单", "/finance/receipt/edit/"}),
            Map.entry("PAYMENT_VOUCHER", new String[]{"付款单", "/finance/payment/edit/"}),
            Map.entry("WAGE_SETTLEMENT", new String[]{"工序工资核算", "/finance/wage-settlement"}),
            Map.entry("OPERATION_EXPENSE", new String[]{"费用分摊", "/finance/operation-expense/"}),
            Map.entry("CUSTOMER_CREDIT", new String[]{"客户授信", null}));

    /** 合并审批引擎的待办任务到工作台列表（审批人视角） */
    private void addApprovalEngineTasks(List<MyTodoDTO> list, String username) {
        List<ApprovalTask> tasks;
        try {
            tasks = approvalRuntimeService.getMyPendingTasks(username);
        } catch (Exception e) {
            return; // 引擎查询异常不阻塞原工作台列表
        }
        if (tasks == null) return;
        for (ApprovalTask t : tasks) {
            if (t.getBillType() == null || t.getBillId() == null) continue;
            MyTodoDTO dto = new MyTodoDTO();
            dto.setId(t.getBillId());
            dto.setType(t.getBillType());
            String[] meta = BILL_TYPE_META.get(t.getBillType());
            dto.setTypeName(meta != null ? meta[0] : t.getBillType());
            dto.setBillNo(t.getBillNo());
            dto.setTitle((meta != null ? meta[0] : t.getBillType()) + (t.getBillNo() != null ? " " + t.getBillNo() : ""));
            dto.setStatus("待审核");
            dto.setApprovalStatus(PENDING_APPROVAL);
            dto.setCreator(t.getSubmitBy());
            dto.setCreateTime(t.getSubmitTime());
            if (meta != null && meta[1] != null && !meta[1].isEmpty()) dto.setRoutePath(meta[1] + t.getBillId());
            list.add(dto);
        }
    }

    private void addSalePendingApprovals(List<MyTodoDTO> list, String username, boolean isAdmin) {
        // 2026-11-19 防脏数据：审批状态与单据状态必须同为「待审核」才进待办
        // （历史版本免审批提交曾只改单据状态不改审批状态，导致已生效单据混进待审批列表）
        LambdaQueryWrapper<SaleOrder> soWrapper = new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getApprovalStatus, PENDING_APPROVAL)
                .eq(SaleOrder::getStatus, PENDING_APPROVAL);
        if (!isAdmin) soWrapper.eq(SaleOrder::getCreatedBy, username);
        for (SaleOrder o : saleOrderMapper.selectList(soWrapper)) {
            list.add(buildTodo(o.getOrderId(), "SALE_ORDER", "销售订单",
                    o.getOrderNo(), o.getCustomerShortName(), o.getStatus(),
                    o.getApprovalStatus(), o.getOrderDate(), o.getDeliveryDate(),
                    o.getTotalAmount(), o.getCreatedBy(), o.getCreatedAt(),
                    "/sale/orders/" + o.getOrderId()));
        }

        LambdaQueryWrapper<SaleDelivery> sdWrapper = new LambdaQueryWrapper<SaleDelivery>()
                .eq(SaleDelivery::getApprovalStatus, PENDING_APPROVAL)
                .eq(SaleDelivery::getStatus, PENDING_APPROVAL);
        if (!isAdmin) sdWrapper.eq(SaleDelivery::getCreatedBy, username);
        for (SaleDelivery d : saleDeliveryMapper.selectList(sdWrapper)) {
            list.add(buildTodo(d.getDeliveryId(), "SALE_DELIVERY", "销售出库",
                    d.getDeliveryNo(), null, d.getStatus(), d.getApprovalStatus(),
                    d.getDeliveryDate(), null, d.getTotalAmount(), d.getCreatedBy(),
                    d.getCreatedAt(), "/sale/deliveries/" + d.getDeliveryId()));
        }

        LambdaQueryWrapper<SaleReturn> srWrapper = new LambdaQueryWrapper<SaleReturn>()
                .eq(SaleReturn::getApprovalStatus, PENDING_APPROVAL)
                .eq(SaleReturn::getStatus, PENDING_APPROVAL);
        if (!isAdmin) srWrapper.eq(SaleReturn::getCreatedBy, username);
        for (SaleReturn r : saleReturnMapper.selectList(srWrapper)) {
            list.add(buildTodo(r.getReturnId(), "SALE_RETURN", "销售退货",
                    r.getReturnNo(), null, r.getStatus(), r.getApprovalStatus(),
                    r.getReturnDate(), null, r.getTotalAmount(), r.getCreatedBy(),
                    r.getCreatedAt(), "/sale/returns/" + r.getReturnId()));
        }

        LambdaQueryWrapper<SaleInvoice> siWrapper = new LambdaQueryWrapper<SaleInvoice>()
                .eq(SaleInvoice::getApprovalStatus, PENDING_APPROVAL)
                .eq(SaleInvoice::getStatus, PENDING_APPROVAL);
        if (!isAdmin) siWrapper.eq(SaleInvoice::getCreatedBy, username);
        for (SaleInvoice i : saleInvoiceMapper.selectList(siWrapper)) {
            list.add(buildTodo(i.getInvoiceId(), "SALE_INVOICE", "销售发票",
                    i.getInvoiceNo(), null, i.getStatus(), i.getApprovalStatus(),
                    i.getInvoiceDate(), null, i.getTotalAmount(), i.getCreatedBy(),
                    i.getCreatedAt(), "/sale/invoices/" + i.getInvoiceId()));
        }
    }

    private void addPurchasePendingApprovals(List<MyTodoDTO> list, String username, boolean isAdmin) {
        LambdaQueryWrapper<PurchaseOrder> poWrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getApprovalStatus, PENDING_APPROVAL)
                .eq(PurchaseOrder::getStatus, PENDING_APPROVAL);
        if (!isAdmin) poWrapper.eq(PurchaseOrder::getCreatedBy, username);
        for (PurchaseOrder o : purchaseOrderMapper.selectList(poWrapper)) {
            list.add(buildTodo(o.getOrderId(), "PURCHASE_ORDER", "采购订单",
                    o.getOrderNo(), o.getSupplierName(), o.getStatus(),
                    o.getApprovalStatus(), o.getOrderDate(), o.getDeliveryDate(),
                    o.getTotalAmount(), o.getCreatedBy(), o.getCreatedAt(),
                    "/purchase/orders/" + o.getOrderId()));
        }

        LambdaQueryWrapper<PurchaseInbound> piWrapper = new LambdaQueryWrapper<PurchaseInbound>()
                .eq(PurchaseInbound::getStatus, PENDING_APPROVAL)
                .eq(PurchaseInbound::getApprovalStatus, PENDING_APPROVAL);
        if (!isAdmin) piWrapper.eq(PurchaseInbound::getCreatedBy, username);
        for (PurchaseInbound i : purchaseInboundMapper.selectList(piWrapper)) {
            list.add(buildTodo(i.getReceiptId(), "PURCHASE_INBOUND", "采购入库",
                    i.getReceiptNo(), i.getSupplierName(), i.getStatus(), null,
                    i.getInDate(), null, i.getTotalAmount(), i.getCreatedBy(),
                    i.getCreatedAt(), "/purchase/inbounds/" + i.getReceiptId()));
        }

        LambdaQueryWrapper<PurchaseReturn> prWrapper = new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getStatus, PENDING_APPROVAL)
                .eq(PurchaseReturn::getApprovalStatus, PENDING_APPROVAL);
        if (!isAdmin) prWrapper.eq(PurchaseReturn::getCreatedBy, username);
        for (PurchaseReturn r : purchaseReturnMapper.selectList(prWrapper)) {
            list.add(buildTodo(r.getReturnId(), "PURCHASE_RETURN", "采购退货",
                    r.getReturnNo(), r.getSupplierName(), r.getStatus(), null,
                    r.getReturnDate(), null, r.getTotalAmount(), r.getCreatedBy(),
                    r.getCreatedAt(), "/purchase/returns/" + r.getReturnId()));
        }

        LambdaQueryWrapper<PurchaseInvoice> pinvWrapper = new LambdaQueryWrapper<PurchaseInvoice>()
                .eq(PurchaseInvoice::getStatus, PENDING_APPROVAL)
                .eq(PurchaseInvoice::getApprovalStatus, PENDING_APPROVAL);
        if (!isAdmin) pinvWrapper.eq(PurchaseInvoice::getCreatedBy, username);
        for (PurchaseInvoice i : purchaseInvoiceMapper.selectList(pinvWrapper)) {
            list.add(buildTodo(i.getInvoiceId(), "PURCHASE_INVOICE", "采购发票",
                    i.getInvoiceNo(), i.getSupplierName(), i.getStatus(), null,
                    i.getInvoiceDate(), null, i.getTaxTotalAmount(), i.getCreatedBy(),
                    i.getCreatedAt(), "/purchase/invoices/" + i.getInvoiceId()));
        }
    }

    private void addInventoryPendingApprovals(List<MyTodoDTO> list, String username, boolean isAdmin) {
        LambdaQueryWrapper<StockTransfer> stWrapper = new LambdaQueryWrapper<StockTransfer>()
                .eq(StockTransfer::getApprovalStatus, PENDING_APPROVAL)
                .eq(StockTransfer::getStatus, PENDING_APPROVAL);
        if (!isAdmin) stWrapper.eq(StockTransfer::getCreatedBy, username);
        for (StockTransfer t : stockTransferMapper.selectList(stWrapper)) {
            list.add(buildTodo(t.getTransferId(), "STOCK_TRANSFER", "库存调拨",
                    t.getTransferNo(), null, t.getStatus(), t.getApprovalStatus(),
                    t.getTransferDate(), null, null, t.getCreatedBy(), t.getCreatedAt(),
                    "/inventory/stock-transfers/edit/" + t.getTransferId()));
        }

        LambdaQueryWrapper<StockCheck> scWrapper = new LambdaQueryWrapper<StockCheck>()
                .eq(StockCheck::getApprovalStatus, PENDING_APPROVAL)
                .eq(StockCheck::getStatus, PENDING_APPROVAL);
        if (!isAdmin) scWrapper.eq(StockCheck::getCreatedBy, username);
        for (StockCheck c : stockCheckMapper.selectList(scWrapper)) {
            list.add(buildTodo(c.getCheckId(), "STOCK_CHECK", "库存盘点",
                    c.getCheckNo(), null, c.getStatus(), c.getApprovalStatus(),
                    c.getCheckDate(), null, null, c.getCreatedBy(), c.getCreatedAt(),
                    "/inventory/stock-checks/edit/" + c.getCheckId()));
        }
    }

    private void addProductionPendingApprovals(List<MyTodoDTO> list, String username, boolean isAdmin) {
        // 生产工单走"下达"流程不走审批，不进入待审批列表
        // 工序汇报单
        LambdaQueryWrapper<OperationReport> orWrapper = new LambdaQueryWrapper<OperationReport>()
                .eq(OperationReport::getApprovalStatus, PENDING_APPROVAL)
                .eq(OperationReport::getStatus, PENDING_APPROVAL);
        if (!isAdmin) orWrapper.eq(OperationReport::getCreatedBy, username);
        for (OperationReport r : operationReportMapper.selectList(orWrapper)) {
            list.add(buildTodo(r.getReportId(), "OPERATION_REPORT", "工序汇报",
                    r.getReportNo(), r.getOperationName(), r.getStatus(),
                    r.getApprovalStatus(), r.getReportDate(), null,
                    r.getTotalQuantity() == null ? null : new BigDecimal(r.getTotalQuantity().toString()),
                    r.getCreatedBy(), r.getCreatedAt(),
                    "/production/operation-reports/" + r.getReportId()));
        }

        // 生产入库单
        LambdaQueryWrapper<ProductionReceipt> prWrapper = new LambdaQueryWrapper<ProductionReceipt>()
                .eq(ProductionReceipt::getApprovalStatus, PENDING_APPROVAL)
                .eq(ProductionReceipt::getStatus, PENDING_APPROVAL);
        if (!isAdmin) prWrapper.eq(ProductionReceipt::getCreatedBy, username);
        for (ProductionReceipt r : productionReceiptMapper.selectList(prWrapper)) {
            list.add(buildTodo(r.getReceiptId(), "PRODUCTION_RECEIPT", "生产入库单",
                    r.getReceiptNo(), r.getProductionOrderNo(), r.getStatus(),
                    r.getApprovalStatus(), r.getReceiptDate(), null,
                    r.getTotalAmount() == null ? null : new BigDecimal(r.getTotalAmount().toString()),
                    r.getCreatedBy(), r.getCreatedAt(),
                    "/production/receipts/" + r.getReceiptId()));
        }
    }

    // ===== 即将超期 / 已超期 =====

    public List<MyTodoDTO> getMyOverdueTasks(String username, Long employeeId, List<String> roles) {
        if (roles == null) roles = Collections.emptyList();
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("MANAGER");
        boolean isSales = roles.contains("SALES");
        boolean isPurchase = roles.contains("PURCHASE");
        boolean isProduction = roles.contains("PRODUCTION");
        boolean isFinance = roles.contains("FINANCE");

        LocalDate today = LocalDate.now();
        LocalDate soonThreshold = today.plusDays(7);
        List<MyTodoDTO> list = new ArrayList<>();

        // 已生效未完成且 due_date <= today+7
        if (isAdmin || isSales) {
            // 销售订单：未关闭且 delivery_date <= today+7
            LambdaQueryWrapper<SaleOrder> soWrapper = new LambdaQueryWrapper<SaleOrder>()
                    .eq(SaleOrder::getApprovalStatus, "已生效")
                    .isNotNull(SaleOrder::getDeliveryDate)
                    .le(SaleOrder::getDeliveryDate, soonThreshold);
            if (!isAdmin) soWrapper.eq(SaleOrder::getCreatedBy, username);
            for (SaleOrder o : saleOrderMapper.selectList(soWrapper)) {
                if (isClosedStatus(o.getStatus())) continue;
                list.add(buildTodo(o.getOrderId(), "SALE_ORDER", "销售订单",
                        o.getOrderNo(), o.getCustomerShortName(), o.getStatus(),
                        o.getApprovalStatus(), o.getOrderDate(), o.getDeliveryDate(),
                        o.getTotalAmount(), o.getCreatedBy(), o.getCreatedAt(),
                        "/sale/orders/" + o.getOrderId()));
            }
        }

        if (isAdmin || isPurchase) {
            // 采购订单：未关闭且 delivery_date <= today+7
            LambdaQueryWrapper<PurchaseOrder> poWrapper = new LambdaQueryWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getApprovalStatus, "已生效")
                    .isNotNull(PurchaseOrder::getDeliveryDate)
                    .le(PurchaseOrder::getDeliveryDate, soonThreshold);
            if (!isAdmin) poWrapper.eq(PurchaseOrder::getCreatedBy, username);
            for (PurchaseOrder o : purchaseOrderMapper.selectList(poWrapper)) {
                if (isClosedStatus(o.getStatus())) continue;
                list.add(buildTodo(o.getOrderId(), "PURCHASE_ORDER", "采购订单",
                        o.getOrderNo(), o.getSupplierName(), o.getStatus(),
                        o.getApprovalStatus(), o.getOrderDate(), o.getDeliveryDate(),
                        o.getTotalAmount(), o.getCreatedBy(), o.getCreatedAt(),
                        "/purchase/orders/" + o.getOrderId()));
            }
        }

        if (isAdmin || isProduction) {
            // 生产工单：已生效未完成且 plan_end_date <= today+7
            LambdaQueryWrapper<WorkOrder> woWrapper = new LambdaQueryWrapper<WorkOrder>()
                    .eq(WorkOrder::getApprovalStatus, "已生效")
                    .isNotNull(WorkOrder::getPlanEndDate)
                    .le(WorkOrder::getPlanEndDate, soonThreshold);
            if (!isAdmin) woWrapper.eq(WorkOrder::getCreatedBy, username);
            for (WorkOrder w : workOrderMapper.selectList(woWrapper)) {
                if (isClosedStatus(w.getStatus()) || "已完成".equals(w.getStatus())) continue;
                list.add(buildTodo(w.getOrderId(), "WORK_ORDER", "生产工单",
                        w.getOrderNo(), w.getMaterialName(), w.getStatus(),
                        w.getApprovalStatus(), w.getOrderDate(), w.getPlanEndDate(),
                        null, w.getCreatedBy(), w.getCreatedAt(),
                        "/production/workorders/" + w.getOrderId()));
            }

            // 收集所有 plan_end_date <= today+7 的"超期工单"映射：workOrderNo -> planEndDate
            // 用于工序汇报/生产入库单的"超期未完成"判断
            LambdaQueryWrapper<WorkOrder> urgentWoWrapper = new LambdaQueryWrapper<WorkOrder>()
                    .eq(WorkOrder::getApprovalStatus, "已生效")
                    .isNotNull(WorkOrder::getPlanEndDate)
                    .le(WorkOrder::getPlanEndDate, soonThreshold);
            List<WorkOrder> urgentWorkOrders = workOrderMapper.selectList(urgentWoWrapper);
            // 索引：工单号 -> 计划结束日期
            Map<String, LocalDate> woNoToDate = new HashMap<>();
            Map<Long, LocalDate> woIdToDate = new HashMap<>();
            for (WorkOrder w : urgentWorkOrders) {
                if (isClosedStatus(w.getStatus()) || "已完成".equals(w.getStatus())) continue;
                if (w.getOrderNo() != null) woNoToDate.put(w.getOrderNo(), w.getPlanEndDate());
                if (w.getOrderId() != null) woIdToDate.put(w.getOrderId(), w.getPlanEndDate());
            }

            // 工序汇报单：已生效未完成且关联工单超期
            if (!woNoToDate.isEmpty()) {
                LambdaQueryWrapper<OperationReport> orWrapper = new LambdaQueryWrapper<OperationReport>()
                        .eq(OperationReport::getApprovalStatus, "已生效")
                        .in(OperationReport::getProductionOrderNo, woNoToDate.keySet());
                if (!isAdmin) orWrapper.eq(OperationReport::getCreatedBy, username);
                for (OperationReport r : operationReportMapper.selectList(orWrapper)) {
                    if (isClosedStatus(r.getStatus()) || "已完成".equals(r.getStatus())) continue;
                    LocalDate dueDate = woNoToDate.get(r.getProductionOrderNo());
                    list.add(buildTodo(r.getReportId(), "OPERATION_REPORT", "工序汇报",
                            r.getReportNo(), r.getOperationName(), r.getStatus(),
                            r.getApprovalStatus(), r.getReportDate(), dueDate,
                            r.getTotalQuantity() == null ? null : new BigDecimal(r.getTotalQuantity().toString()),
                            r.getCreatedBy(), r.getCreatedAt(),
                            "/production/operation-reports/" + r.getReportId()));
                }
            }

            // 生产入库单：已生效未完成且关联工单超期
            if (!woIdToDate.isEmpty() || !woNoToDate.isEmpty()) {
                LambdaQueryWrapper<ProductionReceipt> prWrapper = new LambdaQueryWrapper<ProductionReceipt>()
                        .eq(ProductionReceipt::getApprovalStatus, "已生效");
                // 优先用 production_order_id 匹配，再用工单号匹配
                if (!woIdToDate.isEmpty()) {
                    prWrapper.in(ProductionReceipt::getProductionOrderId, woIdToDate.keySet());
                }
                if (!isAdmin) prWrapper.eq(ProductionReceipt::getCreatedBy, username);
                List<ProductionReceipt> prList = productionReceiptMapper.selectList(prWrapper);
                // 对未通过 ID 匹配的，再用工单号匹配
                Set<String> matchedNos = new HashSet<>();
                for (ProductionReceipt r : prList) {
                    if (isClosedStatus(r.getStatus()) || "已完成".equals(r.getStatus())) continue;
                    LocalDate dueDate = r.getProductionOrderId() != null ? woIdToDate.get(r.getProductionOrderId()) : null;
                    if (dueDate == null && r.getProductionOrderNo() != null) {
                        dueDate = woNoToDate.get(r.getProductionOrderNo());
                    }
                    if (dueDate == null) continue;
                    matchedNos.add(r.getProductionOrderNo());
                    list.add(buildTodo(r.getReceiptId(), "PRODUCTION_RECEIPT", "生产入库单",
                            r.getReceiptNo(), r.getProductionOrderNo(), r.getStatus(),
                            r.getApprovalStatus(), r.getReceiptDate(), dueDate,
                            r.getTotalAmount() == null ? null : new BigDecimal(r.getTotalAmount().toString()),
                            r.getCreatedBy(), r.getCreatedAt(),
                            "/production/receipts/" + r.getReceiptId()));
                }
                // 用工单号再查一批（之前 ID 没匹配上的）
                Set<String> remainingNos = new HashSet<>(woNoToDate.keySet());
                remainingNos.removeAll(matchedNos);
                if (!remainingNos.isEmpty()) {
                    LambdaQueryWrapper<ProductionReceipt> prWrapper2 = new LambdaQueryWrapper<ProductionReceipt>()
                            .eq(ProductionReceipt::getApprovalStatus, "已生效")
                            .in(ProductionReceipt::getProductionOrderNo, remainingNos);
                    if (!isAdmin) prWrapper2.eq(ProductionReceipt::getCreatedBy, username);
                    for (ProductionReceipt r : productionReceiptMapper.selectList(prWrapper2)) {
                        if (isClosedStatus(r.getStatus()) || "已完成".equals(r.getStatus())) continue;
                        LocalDate dueDate = woNoToDate.get(r.getProductionOrderNo());
                        if (dueDate == null) continue;
                        list.add(buildTodo(r.getReceiptId(), "PRODUCTION_RECEIPT", "生产入库单",
                                r.getReceiptNo(), r.getProductionOrderNo(), r.getStatus(),
                                r.getApprovalStatus(), r.getReceiptDate(), dueDate,
                                r.getTotalAmount() == null ? null : new BigDecimal(r.getTotalAmount().toString()),
                                r.getCreatedBy(), r.getCreatedAt(),
                                "/production/receipts/" + r.getReceiptId()));
                    }
                }
            }

            // 工序完工到期提醒（2026-09-27 第 4 期修正三条口径）：
            //  ① 改扫「工单实际工序」work_order_operation —— 此前误扫工艺路线模板 routing_operation，
            //     模板上的工序不是车间正在做的那道，导致"工序到期"根本不提醒；
            //  ② 状态判定改用新 8 值口径：已完工 / 已取消 = 终态，不再提醒
            //     （此前比对旧值"已完成"，而工序状态早已改成"已完工"→ 已完工的工序会被反复提醒）；
            //  ③ 接收人 = 工序负责人（员工→登录账号）优先，负责人未绑定账号时回落到工单制单人，
            //     让提醒真正到"在做事的人"手里，而不是永远只推给开单的人。
            LambdaQueryWrapper<WorkOrderOperation> wooWrapper = new LambdaQueryWrapper<WorkOrderOperation>()
                    .isNotNull(WorkOrderOperation::getPlanEndDate)
                    .le(WorkOrderOperation::getPlanEndDate, soonThreshold)
                    .and(w -> w.isNull(WorkOrderOperation::getOperationStatus)
                            .or(ne -> ne.notIn(WorkOrderOperation::getOperationStatus, "已完工", "已取消")));
            for (WorkOrderOperation op : workOrderOperationMapper.selectList(wooWrapper)) {
                WorkOrder wo = op.getOrderId() == null ? null : workOrderMapper.selectById(op.getOrderId());
                String receiver = resolveOperationReceiver(op, wo);
                // 非管理员仍只看"与我有关"（同改造前口径，避免越权看到他人工序）
                if (!isAdmin && !Objects.equals(username, receiver)) continue;
                String opStatus = op.getOperationStatus() == null ? "待派工" : op.getOperationStatus();
                String woNo = op.getOrderNo() != null ? op.getOrderNo() : (wo != null ? wo.getOrderNo() : null);
                list.add(buildTodo(op.getId(), "WORK_ORDER_OPERATION", "工序进度",
                        op.getOperationCode() == null ? ("工序" + op.getLineNo()) : op.getOperationCode(),
                        (op.getOperationName() == null ? "" : op.getOperationName())
                                + (woNo == null ? "" : ("（工单 " + woNo + "）")),
                        opStatus, null,
                        null, op.getPlanEndDate(),
                        null, receiver, op.getCreateTime(),
                        wo != null ? ("/production/workorders/" + wo.getOrderId()) : "/production/workorders"));
            }
        }

        if (isAdmin || isFinance) {
            // 应收（【2026-10-19 设计图 v3】系统无应收单：改按销售订单口径——
            // 已生效、未收完、收款截止（发货日期，无发货取订单日期）≤ today+7）
            LambdaQueryWrapper<SaleOrder> arSoWrapper = new LambdaQueryWrapper<SaleOrder>()
                    .eq(SaleOrder::getStatus, "已生效")
                    .le(SaleOrder::getDeliveryDate, soonThreshold);
            if (!isAdmin) arSoWrapper.eq(SaleOrder::getCreatedBy, username);
            for (SaleOrder so : saleOrderMapper.selectList(arSoWrapper)) {
                BigDecimal net = so.getNetAmount() == null ? BigDecimal.ZERO : so.getNetAmount();
                BigDecimal rec = so.getReceivedAmount() == null ? BigDecimal.ZERO : so.getReceivedAmount();
                BigDecimal balance = net.subtract(rec);
                if (balance.compareTo(BigDecimal.ZERO) <= 0) continue; // 已收完的不进待办
                LocalDate due = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
                list.add(buildTodo(so.getOrderId(), "SO_AR", "销售订单收款",
                        so.getOrderNo(), so.getCustomerName(), so.getStatus(),
                        so.getStatus(), so.getOrderDate(), due,
                        balance, so.getCreatedBy(), so.getCreatedAt(),
                        "/finance/receipt"));
            }

            // 应付
            LambdaQueryWrapper<AccountsPayable> apWrapper = new LambdaQueryWrapper<AccountsPayable>()
                    .eq(AccountsPayable::getApprovalStatus, "已生效")
                    .ne(AccountsPayable::getStatus, "已结清")
                    .isNotNull(AccountsPayable::getDueDate)
                    .le(AccountsPayable::getDueDate, soonThreshold);
            if (!isAdmin) apWrapper.eq(AccountsPayable::getCreatedBy, username);
            for (AccountsPayable ap : accountsPayableMapper.selectList(apWrapper)) {
                list.add(buildTodo(ap.getApId(), "AP", "应付账款",
                        ap.getApNo(), ap.getSupplierName(), ap.getStatus(),
                        ap.getApprovalStatus(), ap.getApDate(), ap.getDueDate(),
                        ap.getBalanceAmount(), ap.getCreatedBy(), ap.getCreatedAt(),
                        "/finance/report/ap-summary"));
            }
        }

        // 按剩余天数升序（最紧急的在前）
        list.sort(Comparator.comparing(MyTodoDTO::getRemainingDays,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    /**
     * 小铃铛「超期/即将超期」通知的取数口径 = 工作台口径再收敛为「只与我有关」。
     *
     * <p><b>为什么必须单开一个入口</b>：通知是发给「某个账号」的个人提醒，不能因为该账号是管理员，
     * 就把全公司的超期单推到他铃铛里（121 实测：ADMIN 账号的铃铛挂着别人的销售订单/应收超期，用户明确
     * 要求"只能看当前账号的信息"）。工作台卡片的"管理员看全公司"属管理视角，**保持原样不动**，
     * 因此这里用独立入口，而不是改原方法的判定。</p>
     */
    public List<MyTodoDTO> getMyOverdueTasksSelfOnly(String username, Long employeeId, List<String> roles) {
        return keepSelfRelated(getMyOverdueTasks(username, employeeId, roles), username);
    }

    /**
     * 小铃铛「应收应付」通知的取数口径 = 工作台口径再收敛为「只与我有关」（同上）。
     */
    public List<MyTodoDTO> getMyArApFollowUpSelfOnly(String username, Long employeeId, List<String> roles) {
        return keepSelfRelated(getMyArApFollowUp(username, employeeId, roles), username);
    }

    /**
     * 只保留"与我有关"的待办：creator（来源单据 created_by）等于当前登录名。
     * 与列表页数据权限同为 created_by 维度，保证「铃铛看得到的」 ⊆ 「页面看得到的」。
     */
    private List<MyTodoDTO> keepSelfRelated(List<MyTodoDTO> todos, String username) {
        if (todos == null) return new ArrayList<>();
        if (username == null || username.isBlank()) return new ArrayList<>();
        return todos.stream()
                .filter(t -> t != null && username.equals(t.getCreator()))
                .collect(Collectors.toList());
    }

    // ===== 我的草稿 =====

    public List<MyTodoDTO> getMyDrafts(String username, Long employeeId, List<String> roles) {
        if (roles == null) roles = Collections.emptyList();
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("MANAGER");
        boolean isSales = roles.contains("SALES");
        boolean isPurchase = roles.contains("PURCHASE");
        boolean isWarehouse = roles.contains("WAREHOUSE");
        boolean isProduction = roles.contains("PRODUCTION");
        boolean isFinance = roles.contains("FINANCE");

        List<MyTodoDTO> list = new ArrayList<>();

        // 管理员看所有人的草稿，普通用户只看自己的
        // 已生效未关闭的算"完成态"，不进草稿列表
        // 草稿判定：approval_status='草稿' OR status='草稿'

        if (isAdmin || isSales) {
            // 销售订单草稿
            for (SaleOrder o : saleOrderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                    .eq(SaleOrder::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, SaleOrder::getCreatedBy, username)
                    .orderByDesc(SaleOrder::getCreatedAt))) {
                list.add(buildTodo(o.getOrderId(), "SALE_ORDER", "销售订单",
                        o.getOrderNo(), o.getCustomerShortName(), o.getStatus(),
                        o.getApprovalStatus(), o.getOrderDate(), o.getDeliveryDate(),
                        o.getTotalAmount(), o.getCreatedBy(), o.getCreatedAt(),
                        "/sale/orders/" + o.getOrderId()));
            }
            for (SaleDelivery d : saleDeliveryMapper.selectList(new LambdaQueryWrapper<SaleDelivery>()
                    .eq(SaleDelivery::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, SaleDelivery::getCreatedBy, username)
                    .orderByDesc(SaleDelivery::getCreatedAt))) {
                list.add(buildTodo(d.getDeliveryId(), "SALE_DELIVERY", "销售出库",
                        d.getDeliveryNo(), null, d.getStatus(), d.getApprovalStatus(),
                        d.getDeliveryDate(), null, d.getTotalAmount(), d.getCreatedBy(),
                        d.getCreatedAt(), "/sale/deliveries/" + d.getDeliveryId()));
            }
            for (SaleReturn r : saleReturnMapper.selectList(new LambdaQueryWrapper<SaleReturn>()
                    .eq(SaleReturn::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, SaleReturn::getCreatedBy, username)
                    .orderByDesc(SaleReturn::getCreatedAt))) {
                list.add(buildTodo(r.getReturnId(), "SALE_RETURN", "销售退货",
                        r.getReturnNo(), null, r.getStatus(), r.getApprovalStatus(),
                        r.getReturnDate(), null, r.getTotalAmount(), r.getCreatedBy(),
                        r.getCreatedAt(), "/sale/returns/" + r.getReturnId()));
            }
            for (SaleInvoice i : saleInvoiceMapper.selectList(new LambdaQueryWrapper<SaleInvoice>()
                    .eq(SaleInvoice::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, SaleInvoice::getCreatedBy, username)
                    .orderByDesc(SaleInvoice::getCreatedAt))) {
                list.add(buildTodo(i.getInvoiceId(), "SALE_INVOICE", "销售发票",
                        i.getInvoiceNo(), null, i.getStatus(), i.getApprovalStatus(),
                        i.getInvoiceDate(), null, i.getTotalAmount(), i.getCreatedBy(),
                        i.getCreatedAt(), "/sale/invoices/" + i.getInvoiceId()));
            }
        }

        if (isAdmin || isPurchase) {
            for (PurchaseOrder o : purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, PurchaseOrder::getCreatedBy, username)
                    .orderByDesc(PurchaseOrder::getCreatedAt))) {
                list.add(buildTodo(o.getOrderId(), "PURCHASE_ORDER", "采购订单",
                        o.getOrderNo(), o.getSupplierName(), o.getStatus(),
                        o.getApprovalStatus(), o.getOrderDate(), o.getDeliveryDate(),
                        o.getTotalAmount(), o.getCreatedBy(), o.getCreatedAt(),
                        "/purchase/orders/" + o.getOrderId()));
            }
            for (PurchaseInbound i : purchaseInboundMapper.selectList(new LambdaQueryWrapper<PurchaseInbound>()
                    .eq(PurchaseInbound::getStatus, DRAFT)
                    .eq(!isAdmin, PurchaseInbound::getCreatedBy, username)
                    .orderByDesc(PurchaseInbound::getCreatedAt))) {
                list.add(buildTodo(i.getReceiptId(), "PURCHASE_INBOUND", "采购入库",
                        i.getReceiptNo(), i.getSupplierName(), i.getStatus(), null,
                        i.getInDate(), null, i.getTotalAmount(), i.getCreatedBy(),
                        i.getCreatedAt(), "/purchase/inbounds/" + i.getReceiptId()));
            }
            for (PurchaseReturn r : purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturn>()
                    .eq(PurchaseReturn::getStatus, DRAFT)
                    .eq(!isAdmin, PurchaseReturn::getCreatedBy, username)
                    .orderByDesc(PurchaseReturn::getCreatedAt))) {
                list.add(buildTodo(r.getReturnId(), "PURCHASE_RETURN", "采购退货",
                        r.getReturnNo(), r.getSupplierName(), r.getStatus(), null,
                        r.getReturnDate(), null, r.getTotalAmount(), r.getCreatedBy(),
                        r.getCreatedAt(), "/purchase/returns/" + r.getReturnId()));
            }
        }

        if (isAdmin || isWarehouse) {
            for (StockTransfer t : stockTransferMapper.selectList(new LambdaQueryWrapper<StockTransfer>()
                    .eq(StockTransfer::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, StockTransfer::getCreatedBy, username)
                    .orderByDesc(StockTransfer::getCreatedAt))) {
                list.add(buildTodo(t.getTransferId(), "STOCK_TRANSFER", "库存调拨",
                        t.getTransferNo(), null, t.getStatus(), t.getApprovalStatus(),
                        t.getTransferDate(), null, null, t.getCreatedBy(), t.getCreatedAt(),
                        "/inventory/stock-transfers/edit/" + t.getTransferId()));
            }
            for (StockCheck c : stockCheckMapper.selectList(new LambdaQueryWrapper<StockCheck>()
                    .eq(StockCheck::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, StockCheck::getCreatedBy, username)
                    .orderByDesc(StockCheck::getCreatedAt))) {
                list.add(buildTodo(c.getCheckId(), "STOCK_CHECK", "库存盘点",
                        c.getCheckNo(), null, c.getStatus(), c.getApprovalStatus(),
                        c.getCheckDate(), null, null, c.getCreatedBy(), c.getCreatedAt(),
                        "/inventory/stock-checks/edit/" + c.getCheckId()));
            }
        }

        if (isAdmin || isProduction) {
            for (WorkOrder w : workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                    .eq(WorkOrder::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, WorkOrder::getCreatedBy, username)
                    .orderByDesc(WorkOrder::getCreatedAt))) {
                list.add(buildTodo(w.getOrderId(), "WORK_ORDER", "生产工单",
                        w.getOrderNo(), w.getMaterialName(), w.getStatus(),
                        w.getApprovalStatus(), w.getOrderDate(), w.getPlanEndDate(),
                        null, w.getCreatedBy(), w.getCreatedAt(),
                        "/production/workorders/" + w.getOrderId()));
            }
            // 工序汇报单草稿
            for (OperationReport r : operationReportMapper.selectList(new LambdaQueryWrapper<OperationReport>()
                    .eq(OperationReport::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, OperationReport::getCreatedBy, username)
                    .orderByDesc(OperationReport::getCreatedAt))) {
                list.add(buildTodo(r.getReportId(), "OPERATION_REPORT", "工序汇报",
                        r.getReportNo(), r.getOperationName(), r.getStatus(),
                        r.getApprovalStatus(), r.getReportDate(), null,
                        r.getTotalQuantity() == null ? null : new BigDecimal(r.getTotalQuantity().toString()),
                        r.getCreatedBy(), r.getCreatedAt(),
                        "/production/operation-reports/" + r.getReportId()));
            }
            // 生产入库单草稿
            for (ProductionReceipt r : productionReceiptMapper.selectList(new LambdaQueryWrapper<ProductionReceipt>()
                    .eq(ProductionReceipt::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, ProductionReceipt::getCreatedBy, username)
                    .orderByDesc(ProductionReceipt::getCreatedAt))) {
                list.add(buildTodo(r.getReceiptId(), "PRODUCTION_RECEIPT", "生产入库单",
                        r.getReceiptNo(), r.getProductionOrderNo(), r.getStatus(),
                        r.getApprovalStatus(), r.getReceiptDate(), null,
                        r.getTotalAmount() == null ? null : new BigDecimal(r.getTotalAmount().toString()),
                        r.getCreatedBy(), r.getCreatedAt(),
                        "/production/receipts/" + r.getReceiptId()));
            }
        }

        if (isAdmin || isFinance) {
            // 【2026-10-19 设计图 v3】系统无应收单：删除"应收账款草稿"待办（无数据来源）；应付草稿待办保留
            for (AccountsPayable ap : accountsPayableMapper.selectList(new LambdaQueryWrapper<AccountsPayable>()
                    .eq(AccountsPayable::getApprovalStatus, DRAFT)
                    .eq(!isAdmin, AccountsPayable::getCreatedBy, username)
                    .orderByDesc(AccountsPayable::getCreatedAt))) {
                list.add(buildTodo(ap.getApId(), "AP", "应付账款",
                        ap.getApNo(), ap.getSupplierName(), ap.getStatus(),
                        ap.getApprovalStatus(), ap.getApDate(), ap.getDueDate(),
                        ap.getTotalAmount(), ap.getCreatedBy(), ap.getCreatedAt(),
                        "/finance/report/ap-summary"));
            }
        }

        list.sort(Comparator.comparing(MyTodoDTO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    // ===== 应收应付跟进 =====

    public List<MyTodoDTO> getMyArApFollowUp(String username, Long employeeId, List<String> roles) {
        if (roles == null) roles = Collections.emptyList();
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("MANAGER");
        boolean isFinance = roles.contains("FINANCE");
        if (!isAdmin && !isFinance) return Collections.emptyList();

        LocalDate today = LocalDate.now();
        List<MyTodoDTO> list = new ArrayList<>();

        // 应收（【2026-10-19 设计图 v3】系统无应收单：改按销售订单口径——
        // 已生效、未收完、收款截止（发货日期，无发货取订单日期）< today 即逾期跟进）
        LambdaQueryWrapper<SaleOrder> arSoWrapper = new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getStatus, "已生效")
                .lt(SaleOrder::getDeliveryDate, today);
        if (!isAdmin) arSoWrapper.eq(SaleOrder::getCreatedBy, username);
        for (SaleOrder so : saleOrderMapper.selectList(arSoWrapper)) {
            BigDecimal net = so.getNetAmount() == null ? BigDecimal.ZERO : so.getNetAmount();
            BigDecimal rec = so.getReceivedAmount() == null ? BigDecimal.ZERO : so.getReceivedAmount();
            BigDecimal balance = net.subtract(rec);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) continue; // 已收完的不进跟进
            LocalDate due = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
            list.add(buildTodo(so.getOrderId(), "SO_AR", "销售订单收款",
                    so.getOrderNo(), so.getCustomerName(), so.getStatus(),
                    so.getStatus(), so.getOrderDate(), due,
                    balance, so.getCreatedBy(), so.getCreatedAt(),
                    "/finance/receipt"));
        }

        // 应付
        LambdaQueryWrapper<AccountsPayable> apWrapper = new LambdaQueryWrapper<AccountsPayable>()
                .eq(AccountsPayable::getApprovalStatus, "已生效")
                .ne(AccountsPayable::getStatus, "已结清")
                .lt(AccountsPayable::getDueDate, today);
        if (!isAdmin) apWrapper.eq(AccountsPayable::getCreatedBy, username);
        for (AccountsPayable ap : accountsPayableMapper.selectList(apWrapper)) {
            list.add(buildTodo(ap.getApId(), "AP", "应付账款",
                    ap.getApNo(), ap.getSupplierName(), ap.getStatus(),
                    ap.getApprovalStatus(), ap.getApDate(), ap.getDueDate(),
                    ap.getBalanceAmount(), ap.getCreatedBy(), ap.getCreatedAt(),
                    "/finance/report/ap-summary"));
        }

        list.sort(Comparator.comparing(MyTodoDTO::getDueDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    // ===== 辅助方法 =====

    /**
     * 工序到期提醒的接收人：优先"工序负责人"绑定的登录账号；负责人不存在/未绑定账号时回落到工单制单人。
     *
     * <p>必须返回**登录名**而不是员工姓名 —— 通知链路最后按「来源单据 createdBy == 当前登录名」过滤
     * （见 {@link #keepSelfRelated}），给员工姓名会导致谁都收不到。</p>
     */
    private String resolveOperationReceiver(WorkOrderOperation op, WorkOrder wo) {
        if (op != null && op.getAssigneeId() != null) {
            try {
                Employee emp = employeeMapper.selectById(op.getAssigneeId());
                if (emp != null && emp.getUserId() != null) {
                    SysUser u = sysUserMapper.selectById(emp.getUserId());
                    if (u != null && u.getUsername() != null && !u.getUsername().isBlank()) {
                        return u.getUsername();
                    }
                }
            } catch (Exception e) {
                // 取不到账号就回落，不阻断工作台与通知生成
            }
        }
        return wo != null ? wo.getCreatedBy() : null;
    }

    /**
     * 化妆品批记录待办：待检验 / 待放行 / 留样到期。
     * 三类都跳「生产管理 → 批记录中心」，用户在处理页完成检验、放行与留样。
     */
    private void addBatchRecordTodos(List<MyTodoDTO> list) {
        final String ROUTE = "/production/batch-record-center";
        try {
            // 1) 待检验：批次检验记录尚未出结论
            List<com.erp.qc.entity.QcRecord> pendingQc = qcRecordMapper.selectList(
                    new QueryWrapper<com.erp.qc.entity.QcRecord>()
                            .isNull("result")
                            .in("qc_stage", "来料检", "成品检")
                            .orderByDesc("record_id")
                            .last("LIMIT 50"));
            for (com.erp.qc.entity.QcRecord qc : pendingQc) {
                list.add(buildTodo(qc.getRecordId(), "BATCH_QC", "待检验",
                        qc.getRecordNo(), qc.getBatchNo(), "待检验", null,
                        null, null, null, qc.getInspectorName(), qc.getCreatedAt(), ROUTE));
            }
            // 2) 待放行：放行单尚未生效
            List<com.erp.production.entity.ReleaseOrder> pendingRelease = releaseOrderMapper.selectList(
                    new QueryWrapper<com.erp.production.entity.ReleaseOrder>()
                            .in("status", "草稿", "待审核")
                            .orderByDesc("release_id")
                            .last("LIMIT 50"));
            for (com.erp.production.entity.ReleaseOrder r : pendingRelease) {
                list.add(buildTodo(r.getReleaseId(), "BATCH_RELEASE", "待放行",
                        r.getReleaseNo(), r.getBatchNo(), r.getStatus(), r.getApprovalStatus(),
                        null, null, null, r.getCreatedBy(), r.getCreatedAt(), ROUTE));
            }
            // 3) 留样到期提醒：保存期已到且尚未提醒
            List<com.erp.production.entity.SampleRetention> due = sampleRetentionMapper.listDue();
            for (com.erp.production.entity.SampleRetention s : due) {
                list.add(buildTodo(s.getRetentionId(), "BATCH_SAMPLE", "留样到期",
                        s.getRetentionNo(), s.getBatchNo(), s.getStatus(), null,
                        null, s.getKeepUntil(), null, s.getCreatedBy(), s.getCreatedAt(), ROUTE));
            }
        } catch (Exception e) {
            log.warn("[批记录] 工作台待办加载失败（不影响其他待办）: {}", e.getMessage());
        }
    }

    private MyTodoDTO buildTodo(Long id, String type, String typeName, String billNo,
                                String partyName, String status, String approvalStatus,
                                LocalDate billDate, LocalDate dueDate, BigDecimal amount,
                                String creator, java.time.LocalDateTime createTime, String routePath) {
        MyTodoDTO dto = new MyTodoDTO();
        dto.setId(id);
        dto.setType(type);
        dto.setTypeName(typeName);
        dto.setBillNo(billNo);
        dto.setPartyName(partyName);
        dto.setTitle((partyName != null ? partyName + " - " : "") + typeName);
        dto.setStatus(status);
        dto.setApprovalStatus(approvalStatus);
        dto.setBillDate(billDate);
        dto.setDueDate(dueDate);
        dto.setAmount(amount);
        dto.setCreator(creator);
        dto.setCreateTime(createTime);
        dto.setRoutePath(routePath);

        if (dueDate != null) {
            int days = (int) ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
            dto.setRemainingDays(days);
            if (days < 0) {
                dto.setUrgency("OVERDUE"); // 已超期
            } else if (days <= 3) {
                dto.setUrgency("URGENT"); // 紧急
            } else if (days <= 7) {
                dto.setUrgency("SOON"); // 即将到期
            } else {
                dto.setUrgency("NORMAL");
            }
        }
        return dto;
    }

    private boolean isClosedStatus(String status) {
        return "已关闭".equals(status) || "已取消".equals(status) || "已完成".equals(status);
    }

    private BigDecimal calcMySaleAmount(String username, Long employeeId, boolean isAdmin) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LambdaQueryWrapper<SaleOrder> wrapper = new LambdaQueryWrapper<SaleOrder>()
                .ge(SaleOrder::getOrderDate, monthStart)
                .eq(SaleOrder::getApprovalStatus, "已生效");
        if (!isAdmin) {
            wrapper.eq(SaleOrder::getCreatedBy, username);
        }
        return saleOrderMapper.selectList(wrapper).stream()
                .map(SaleOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcMyPurchaseAmount(String username, Long employeeId, boolean isAdmin) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .ge(PurchaseOrder::getOrderDate, monthStart)
                .eq(PurchaseOrder::getApprovalStatus, "已生效");
        if (!isAdmin) {
            wrapper.eq(PurchaseOrder::getCreatedBy, username);
        }
        return purchaseOrderMapper.selectList(wrapper).stream()
                .map(PurchaseOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Integer calcInventoryWarning() {
        // 简化：库存低于安全库存的物料数
        List<Material> materials = materialMapper.selectList(null);
        // 库存查询需要 inventoryMapper，但本类未注入，简化返回 0
        // 真实环境应注入 inventoryMapper 后计算
        return 0;
    }
}
