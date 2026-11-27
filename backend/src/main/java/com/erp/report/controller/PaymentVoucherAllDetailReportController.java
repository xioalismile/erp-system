package com.erp.report.controller;

import com.erp.common.Result;
import com.erp.report.detail.BillDetailReportEngine;
import com.erp.report.detail.PaymentVoucherAllDetailReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 付款单全明细（核销 + 付款）：列表页明细模式专用。 */
@RestController
@RequestMapping("/api/report/finance/payment-all-detail")
public class PaymentVoucherAllDetailReportController extends AbstractBillDetailReportController {

    private final PaymentVoucherAllDetailReportService service;

    public PaymentVoucherAllDetailReportController(PaymentVoucherAllDetailReportService service) {
        this.service = service;
    }

    @Override
    protected BillDetailReportEngine engine() {
        return service;
    }

    @Override
    protected String exportFilePrefix() {
        return "付款单全明细";
    }

    @GetMapping("/columns")
    public Result<Map<String, Object>> columns() {
        return doColumns();
    }

    @GetMapping
    public Result<Map<String, Object>> page(HttpServletRequest request,
                                            @RequestParam Map<String, String> params,
                                            @RequestParam(required = false) Integer current,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String cols) {
        return doPage(request, params, current, page, size, cols);
    }

    @GetMapping("/export")
    public void export(HttpServletRequest request,
                       HttpServletResponse response,
                       @RequestParam Map<String, String> params,
                       @RequestParam(required = false) String cols) {
        doExport(request, response, params, cols);
    }
}
