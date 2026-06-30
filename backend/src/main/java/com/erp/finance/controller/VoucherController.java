package com.erp.finance.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.entity.AccountBalance;
import com.erp.finance.entity.Voucher;
import com.erp.finance.entity.VoucherTemplate;
import com.erp.finance.service.AccountBalanceService;
import com.erp.finance.service.VoucherService;
import com.erp.finance.service.VoucherTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 总账控制器 - 会计凭证/凭证模板/科目余额
 * 路径前缀: /api/finance/gl
 * 注意: 不能用 /api/finance/vouchers，因为FinanceController已占用该路径用于PaymentVoucher
 */
@RestController
@RequestMapping("/api/finance/gl")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherTemplateService templateService;
    private final AccountBalanceService balanceService;

    // ========== 会计凭证 ==========

    @GetMapping("/vouchers")
    public Result<PageResult<Voucher>> pageVoucher(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String voucherNo,
            @RequestParam(required = false) String voucherType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer periodYear,
            @RequestParam(required = false) Integer periodMonth) {
        return Result.success(voucherService.pageVoucher(current, size, voucherNo, voucherType, status, periodYear, periodMonth));
    }

    @GetMapping("/vouchers/{id}")
    public Result<Voucher> voucherDetail(@PathVariable Long id) {
        return Result.success(voucherService.getVoucherDetail(id));
    }

    @PostMapping("/vouchers")
    public Result<Voucher> createVoucher(@RequestBody Voucher voucher, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        if (voucher.getMakerName() == null) voucher.setMakerName(operator);
        if (voucher.getCreatedBy() == null) voucher.setCreatedBy(operator);
        return Result.success(voucherService.createVoucher(voucher));
    }

    @PutMapping("/vouchers/{id}")
    public Result<Voucher> updateVoucher(@PathVariable Long id, @RequestBody Voucher voucher, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        voucher.setVoucherId(id);
        voucher.setUpdatedBy(operator);
        return Result.success(voucherService.updateVoucher(voucher));
    }

    @DeleteMapping("/vouchers/{id}")
    public Result<Void> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return Result.success();
    }

    @PostMapping("/vouchers/{id}/audit")
    public Result<Void> auditVoucher(@PathVariable Long id, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        voucherService.auditVoucher(id, operator);
        return Result.success();
    }

    @PostMapping("/vouchers/{id}/post")
    public Result<Void> postVoucher(@PathVariable Long id, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        voucherService.postVoucher(id, operator);
        return Result.success();
    }

    // ========== 凭证模板 ==========

    @GetMapping("/templates")
    public Result<List<VoucherTemplate>> listTemplates() {
        return Result.success(templateService.listAll());
    }

    @PostMapping("/templates")
    public Result<VoucherTemplate> saveTemplate(@RequestBody VoucherTemplate template, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        if (template.getCreatedBy() == null) template.setCreatedBy(operator);
        return Result.success(templateService.saveTemplate(template));
    }

    @PutMapping("/templates/{id}")
    public Result<VoucherTemplate> updateTemplate(@PathVariable Long id, @RequestBody VoucherTemplate template, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        template.setTemplateId(id);
        template.setUpdatedBy(operator);
        return Result.success(templateService.updateTemplate(template));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success();
    }

    // ========== 科目余额 ==========

    @GetMapping("/balance")
    public Result<PageResult<AccountBalance>> pageBalance(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer periodYear,
            @RequestParam(required = false) Integer periodMonth,
            @RequestParam(required = false) String accountCode) {
        return Result.success(balanceService.pageBalance(current, size, periodYear, periodMonth, accountCode));
    }
}
