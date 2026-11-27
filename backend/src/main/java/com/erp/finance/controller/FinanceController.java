package com.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.BillWriteoffDetail;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentReceiptItem;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.entity.PaymentVoucherItem;
import com.erp.finance.mapper.BillWriteoffDetailMapper;
import com.erp.finance.mapper.PaymentReceiptItemMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.PaymentVoucherItemMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.finance.service.FinanceService;
import com.erp.system.entity.SysDownloadLog;
import com.erp.system.mapper.SysDownloadLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final PaymentReceiptMapper paymentReceiptMapper;
    private final PaymentVoucherMapper paymentVoucherMapper;
    private final PaymentReceiptItemMapper paymentReceiptItemMapper;
    private final PaymentVoucherItemMapper paymentVoucherItemMapper;
    private final BillWriteoffDetailMapper billWriteoffDetailMapper;
    private final SysDownloadLogMapper sysDownloadLogMapper;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    // ========== 应收账款 ==========

    @GetMapping("/ar")
    public Result<PageResult<AccountsReceivable>> pageAr(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String arNo,
            HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(financeService.pageAr(current, size, customerName, status, arNo, scopeCtx));
    }

    @GetMapping("/ar/{id}")
    public Result<AccountsReceivable> arDetail(@PathVariable Long id) {
        return Result.success(financeService.getArDetail(id));
    }

    @PostMapping("/ar")
    public Result<AccountsReceivable> createAr(@RequestBody AccountsReceivable ar) {
        return Result.success(financeService.saveAr(ar));
    }

    @PutMapping("/ar/{id}")
    public Result<Void> updateAr(@PathVariable Long id, @RequestBody AccountsReceivable ar) {
        ar.setArId(id);
        financeService.updateAr(ar);
        return Result.success();
    }

    @DeleteMapping("/ar/{id}")
    public Result<Void> deleteAr(@PathVariable Long id) {
        financeService.deleteAr(id);
        return Result.success();
    }

    @PostMapping("/ar/from-sale-order/{saleOrderId}")
    public Result<AccountsReceivable> createArFromSaleOrder(@PathVariable Long saleOrderId) {
        return Result.success(financeService.createArFromSaleOrder(saleOrderId));
    }

    @GetMapping("/ar/source-bills")
    public Result<PageResult<Map<String, Object>>> listArSourceBills(
            @RequestParam(defaultValue = "sale_invoice") String sourceType,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(financeService.listArSourceBills(sourceType, current, size, keyword));
    }

    @PostMapping("/ar/{id}/receive")
    public Result<Void> receivePayment(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Long settleModeId = body.get("settleModeId") != null ? Long.valueOf(body.get("settleModeId").toString()) : null;
        String documentNo = (String) body.get("documentNo");
        String operator = (String) req.getAttribute("currentUsername");
        financeService.receivePayment(id, amount, settleModeId, documentNo, operator);
        return Result.success();
    }

    // ========== 应付账款 ==========

    @GetMapping("/ap")
    public Result<PageResult<AccountsPayable>> pageAp(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apNo,
            HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(financeService.pageAp(current, size, supplierName, status, apNo, scopeCtx));
    }

    @GetMapping("/ap/{id}")
    public Result<AccountsPayable> apDetail(@PathVariable Long id) {
        return Result.success(financeService.getApDetail(id));
    }

    @PostMapping("/ap")
    public Result<AccountsPayable> createAp(@RequestBody AccountsPayable ap) {
        return Result.success(financeService.saveAp(ap));
    }

    @PutMapping("/ap/{id}")
    public Result<Void> updateAp(@PathVariable Long id, @RequestBody AccountsPayable ap) {
        ap.setApId(id);
        financeService.updateAp(ap);
        return Result.success();
    }

    @DeleteMapping("/ap/{id}")
    public Result<Void> deleteAp(@PathVariable Long id) {
        financeService.deleteAp(id);
        return Result.success();
    }

    @PostMapping("/ap/from-purchase-order/{purchaseOrderId}")
    public Result<AccountsPayable> createApFromPurchaseOrder(@PathVariable Long purchaseOrderId) {
        return Result.success(financeService.createApFromPurchaseOrder(purchaseOrderId));
    }

    @PostMapping("/ap/{id}/pay")
    public Result<Void> makePayment(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Long settleModeId = body.get("settleModeId") != null ? Long.valueOf(body.get("settleModeId").toString()) : null;
        String documentNo = (String) body.get("documentNo");
        String operator = (String) req.getAttribute("currentUsername");
        financeService.makePayment(id, amount, settleModeId, documentNo, operator);
        return Result.success();
    }

    // ========== 收款记录 ==========

    @GetMapping("/receipts")
    public Result<PageResult<PaymentReceipt>> pageReceipt(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String receiptNo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            HttpServletRequest request,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围（替代 getCreatedByFilter 硬编码）
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(financeService.pageReceipt(p[0], p[1], customerName, status, receiptNo, keyword, customerId, employeeId, scopeCtx, sortBy, sortOrder, startDate, endDate, headerFilters));
    }

    /**
     * 收款单列表统计卡（2026-09-16 列表页查询口径通用化）。
     * 数据权限与列表逐字同口径：同为 DataScopeResolver 的 scopeCtx.applyTo(wrapper, null)。
     */
    @GetMapping("/receipts/dashboard")
    public Result<Map<String, Object>> receiptDashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 本月 = 当月1号 ~ 当月最后一天（按 receipt_date）
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        QueryWrapper<PaymentReceipt> totalW = new QueryWrapper<>();
        if (scopeCtx != null) scopeCtx.applyTo(totalW, null);

        QueryWrapper<PaymentReceipt> monthW = new QueryWrapper<>();
        monthW.ge("receipt_date", monthStart).le("receipt_date", monthEnd);
        if (scopeCtx != null) scopeCtx.applyTo(monthW, null);

        QueryWrapper<PaymentReceipt> draftW = new QueryWrapper<PaymentReceipt>().eq("status", "草稿");
        if (scopeCtx != null) scopeCtx.applyTo(draftW, null);

        QueryWrapper<PaymentReceipt> pendingW = new QueryWrapper<PaymentReceipt>().eq("status", "待审核");
        if (scopeCtx != null) scopeCtx.applyTo(pendingW, null);

        QueryWrapper<PaymentReceipt> confirmedW = new QueryWrapper<PaymentReceipt>().eq("status", "已确认");
        if (scopeCtx != null) scopeCtx.applyTo(confirmedW, null);

        Map<String, Object> data = new HashMap<>();
        data.put("totalCount", paymentReceiptMapper.selectCount(totalW));
        data.put("monthCount", paymentReceiptMapper.selectCount(monthW));
        data.put("draftCount", paymentReceiptMapper.selectCount(draftW));
        data.put("pendingCount", paymentReceiptMapper.selectCount(pendingW));
        data.put("confirmedCount", paymentReceiptMapper.selectCount(confirmedW));
        return Result.success(data);
    }

    @PostMapping("/receipts")
    public Result<PaymentReceipt> createReceipt(@RequestBody PaymentReceipt receipt) {
        return Result.success(financeService.createReceipt(receipt));
    }

    /**
     * 提交收款单（草稿→开审批则待审核/免审批则直接生效）
     */
    @PostMapping("/receipts/{id}/submit")
    public Result<Void> submitReceipt(@PathVariable Long id, HttpServletRequest req) {
        financeService.submitReceipt(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /**
     * 某来源单类型下已存在「未作废收款单」的来源单号。
     * 选单弹窗据此排除已开过收款单的来源单；收款单已作废的来源单重新回到候选。
     */
    @GetMapping("/receipts/active-source-nos")
    public Result<List<String>> activeSourceNos(@RequestParam(required = false) String sourceType) {
        return Result.success(financeService.listActiveReceiptSourceNos(sourceType));
    }

    /**
     * 收款单详情
     */
    @GetMapping("/receipts/{id}")
    public Result<PaymentReceipt> receiptDetail(@PathVariable Long id) {
        PaymentReceipt r = paymentReceiptMapper.selectById(id);
        if (r == null) return Result.success(null);
        // 组装明细：收款明细（cash_receipt_item）+ 核销明细（bill_writeoff_detail），避免重新打开明细丢失
        r.setItems(paymentReceiptItemMapper.selectList(new QueryWrapper<PaymentReceiptItem>()
                .eq("receipt_id", id).orderByAsc("sort_no", "item_id")));
        r.setWriteOffItems(billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "收款单").eq("bill_id", id).orderByAsc("writeoff_id")));
        return Result.success(r);
    }

    /**
     * 已生效收款单的核销金额汇总（按来源单据 + 来源明细行）。
     * 收款单选单弹窗用：销售出库单 / 销售发票没有「已收金额」列，已收/未收按核销明细反算。
     */
    @GetMapping("/receipts/writeoff-received")
    public Result<List<Map<String, Object>>> writeoffReceived(@RequestParam(required = false) String sourceTypes) {
        return Result.success(financeService.getWriteoffReceivedBySource(sourceTypes));
    }

    @PostMapping("/receipts/{id}/confirm")
    public Result<Void> confirmReceipt(@PathVariable Long id, HttpServletRequest req) {
        String confirmedBy = (String) req.getAttribute("currentUsername");
        financeService.confirmReceipt(id, confirmedBy);
        return Result.success();
    }

    /**
     * 按来源销售订单号查询收款记录（2026-10-12 销售订单列表「收款详情」弹窗）：
     * 含直接订单收款 + 发货路径（沿应收单来源订单号关联）两条路径的已冲销核销行。
     */
    @GetMapping("/receipts/by-source-order")
    public Result<List<Map<String, Object>>> receiptsBySourceOrder(@org.springframework.web.bind.annotation.RequestParam String orderNo) {
        return Result.success(financeService.listReceiptsBySourceOrder(orderNo));
    }

    /**
     * 重置草稿收款单（已确认→草稿），回减订单 received_amount
     */
    @PostMapping("/receipts/{id}/reverse-approve")
    public Result<Void> reverseReceipt(@PathVariable Long id, HttpServletRequest req) {
        financeService.reverseReceipt(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /**
     * 作废收款单（已确认/草稿→已作废），已确认单据回减订单 received_amount
     */
    @PostMapping("/receipts/{id}/void")
    public Result<Void> voidReceipt(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        String reason = body != null ? body.get("reason") : null;
        financeService.voidReceipt(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /**
     * 反作废收款单（已作废→草稿）
     * 注意：不自动回加 received_amount，需用户重新确认收款才会回写
     */
    @PostMapping("/receipts/{id}/unvoid")
    public Result<Void> unvoidReceipt(@PathVariable Long id, HttpServletRequest req) {
        financeService.unvoidReceipt(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /**
     * 删除收款单（仅草稿状态可删）
     */
    @DeleteMapping("/receipts/{id}")
    public Result<Void> deleteReceipt(@PathVariable Long id) {
        financeService.deleteReceipt(id);
        return Result.success();
    }

    /**
     * 驳回收款单（待审核→草稿）
     */
    @PostMapping("/receipts/{id}/reject")
    public Result<Void> rejectReceipt(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        String reason = body != null ? body.get("reason") : null;
        financeService.rejectReceipt(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /**
     * 结案收款单（已确认→已结案，终态）
     */
    @PostMapping("/receipts/{id}/close-order")
    public Result<Void> closeReceipt(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        String reason = body != null ? body.get("reason") : null;
        financeService.closeReceipt(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /**
     * 反结案收款单（已结案→已确认）
     */
    @PostMapping("/receipts/{id}/reopen")
    public Result<Void> reopenReceipt(@PathVariable Long id, HttpServletRequest req) {
        financeService.reopenReceipt(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    // ========== 付款记录 ==========

    @GetMapping("/vouchers")
    public Result<PageResult<PaymentVoucher>> pageVoucher(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String voucherNo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String supplierId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            HttpServletRequest request,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围（替代 getCreatedByFilter 硬编码）
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(financeService.pageVoucher(p[0], p[1], supplierName, status, voucherNo, keyword, supplierId, employeeId, scopeCtx, sortBy, sortOrder, startDate, endDate, headerFilters));
    }

    /**
     * 付款单列表统计卡（2026-09-16 列表页查询口径通用化）。
     * 数据权限与列表逐字同口径：同为 DataScopeResolver 的 scopeCtx.applyTo(wrapper, null)。
     */
    @GetMapping("/vouchers/dashboard")
    public Result<Map<String, Object>> voucherDashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 本月 = 当月1号 ~ 当月最后一天（按 payment_date）
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        QueryWrapper<PaymentVoucher> totalW = new QueryWrapper<>();
        if (scopeCtx != null) scopeCtx.applyTo(totalW, null);

        QueryWrapper<PaymentVoucher> monthW = new QueryWrapper<>();
        monthW.ge("payment_date", monthStart).le("payment_date", monthEnd);
        if (scopeCtx != null) scopeCtx.applyTo(monthW, null);

        QueryWrapper<PaymentVoucher> draftW = new QueryWrapper<PaymentVoucher>().eq("status", "草稿");
        if (scopeCtx != null) scopeCtx.applyTo(draftW, null);

        QueryWrapper<PaymentVoucher> pendingW = new QueryWrapper<PaymentVoucher>().eq("status", "待审核");
        if (scopeCtx != null) scopeCtx.applyTo(pendingW, null);

        QueryWrapper<PaymentVoucher> confirmedW = new QueryWrapper<PaymentVoucher>().eq("status", "已确认");
        if (scopeCtx != null) scopeCtx.applyTo(confirmedW, null);

        Map<String, Object> data = new HashMap<>();
        data.put("totalCount", paymentVoucherMapper.selectCount(totalW));
        data.put("monthCount", paymentVoucherMapper.selectCount(monthW));
        data.put("draftCount", paymentVoucherMapper.selectCount(draftW));
        data.put("pendingCount", paymentVoucherMapper.selectCount(pendingW));
        data.put("confirmedCount", paymentVoucherMapper.selectCount(confirmedW));
        return Result.success(data);
    }

    @PostMapping("/vouchers")
    public Result<PaymentVoucher> createVoucher(@RequestBody PaymentVoucher voucher) {
        return Result.success(financeService.createVoucher(voucher));
    }

    /**
     * 提交付款单（草稿→开审批则待审核/免审批则直接生效）
     */
    @PostMapping("/vouchers/{id}/submit")
    public Result<Void> submitVoucher(@PathVariable Long id, HttpServletRequest req) {
        financeService.submitVoucher(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /**
     * 付款凭证详情
     */
    @GetMapping("/vouchers/{id}")
    public Result<PaymentVoucher> voucherDetail(@PathVariable Long id) {
        PaymentVoucher v = paymentVoucherMapper.selectById(id);
        if (v == null) return Result.success(null);
        // 组装明细：付款明细（cash_payment_item）+ 核销明细（bill_writeoff_detail），避免重新打开明细丢失
        v.setItems(paymentVoucherItemMapper.selectList(new QueryWrapper<PaymentVoucherItem>()
                .eq("payment_id", id).orderByAsc("sort_no", "item_id")));
        v.setWriteOffItems(billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "付款单").eq("bill_id", id).orderByAsc("writeoff_id")));
        return Result.success(v);
    }

    @PostMapping("/vouchers/{id}/confirm")
    public Result<Void> confirmVoucher(@PathVariable Long id, HttpServletRequest req) {
        String confirmedBy = (String) req.getAttribute("currentUsername");
        financeService.confirmVoucher(id, confirmedBy);
        return Result.success();
    }

    /**
     * 驳回付款单（待审核→草稿）
     */
    @PostMapping("/vouchers/{id}/reject")
    public Result<Void> rejectVoucher(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        String reason = body != null ? body.get("reason") : null;
        financeService.rejectVoucher(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /**
     * 重置草稿付款单（已确认→草稿），回减应付单已付金额
     */
    @PostMapping("/vouchers/{id}/reverse-approve")
    public Result<Void> reverseVoucher(@PathVariable Long id, HttpServletRequest req) {
        financeService.reverseVoucher(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /**
     * 作废付款单（已确认/草稿→已作废），已确认单据回减应付单已付金额
     */
    @PostMapping("/vouchers/{id}/void")
    public Result<Void> voidVoucher(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        String reason = body != null ? body.get("reason") : null;
        financeService.voidVoucher(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /**
     * 反作废付款单（已作废→草稿）
     */
    @PostMapping("/vouchers/{id}/unvoid")
    public Result<Void> unvoidVoucher(@PathVariable Long id, HttpServletRequest req) {
        financeService.unvoidVoucher(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /**
     * 结案付款单（已确认→已结案，终态）
     */
    @PostMapping("/vouchers/{id}/close-order")
    public Result<Void> closeVoucher(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        String reason = body != null ? body.get("reason") : null;
        financeService.closeVoucher(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /**
     * 反结案付款单（已结案→已确认）
     */
    @PostMapping("/vouchers/{id}/reopen")
    public Result<Void> reopenVoucher(@PathVariable Long id, HttpServletRequest req) {
        financeService.reopenVoucher(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /**
     * 删除付款单（仅草稿状态可删）
     */
    @DeleteMapping("/vouchers/{id}")
    public Result<Void> deleteVoucher(@PathVariable Long id) {
        financeService.deleteVoucher(id);
        return Result.success();
    }

    // ========== 财务报表 ==========

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(financeService.getSummary());
    }

    @GetMapping("/aging")
    public Result<List<Map<String, Object>>> aging() {
        return Result.success(financeService.getAgingAnalysis());
    }

    // ========== 应付/应收 汇总报表 ==========

    /**
     * 应付账款汇总报表
     * 按供应商聚合应付账款数据，支持日期范围/币别/供应商/类型筛选
     */
    @GetMapping("/payable/summary")
    public Result<Map<String, Object>> payableSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currencyId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierType) {
        return Result.success(financeService.getPayableSummary(startDate, endDate, currencyId, supplierId, supplierType));
    }

    /**
     * 应付账款汇总报表导出
     * 前端以 blob 接收；后端复用汇总数据，由 ExcelService 实际生成
     */
    @GetMapping("/payable/summary/export")
    public Result<Map<String, Object>> payableSummaryExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currencyId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierType,
            HttpServletRequest request) {
        // 记录下载日志（设置中心「下载日志」页面）
        recordDownloadLog(request, "应付账款汇总导出");
        // 复用汇总接口数据，前端拿到 records 后可自行做 CSV/Excel 兜底
        return Result.success(financeService.getPayableSummary(startDate, endDate, currencyId, supplierId, supplierType));
    }

    /**
     * 应收账款汇总报表
     * 按客户聚合应收账款数据，支持日期范围/币别/客户/类型筛选
     */
    @GetMapping("/receivable/summary")
    public Result<Map<String, Object>> receivableSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currencyId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) Integer agingOver) {
        return Result.success(financeService.getReceivableSummary(startDate, endDate, currencyId, customerId, customerType,
                overdueOnly, agingOver));
    }

    /**
     * 应收账款汇总报表导出
     * 前端以 blob 接收；后端复用汇总数据
     */
    @GetMapping("/receivable/summary/export")
    public Result<Map<String, Object>> receivableSummaryExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currencyId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerType,
            HttpServletRequest request) {
        // 记录下载日志（设置中心「下载日志」页面）
        recordDownloadLog(request, "应收账款汇总导出");
        return Result.success(financeService.getReceivableSummary(startDate, endDate, currencyId, customerId, customerType));
    }

    /**
     * 记录一条下载日志（写入 sys_download_log，供设置中心「下载日志」页面查询）
     * 埋点失败不影响导出主流程
     */
    private void recordDownloadLog(HttpServletRequest request, String fileName) {
        try {
            SysDownloadLog downloadLog = new SysDownloadLog();
            downloadLog.setOperator((String) request.getAttribute("currentUsername"));
            downloadLog.setFileName(fileName);
            downloadLog.setFileType("excel");
            downloadLog.setFileSize(0L);
            downloadLog.setIp(request.getRemoteAddr());
            downloadLog.setDownloadTime(LocalDateTime.now());
            sysDownloadLogMapper.insert(downloadLog);
        } catch (Exception e) {
            log.warn("记录下载日志失败 fileName={}", fileName, e);
        }
    }
}
