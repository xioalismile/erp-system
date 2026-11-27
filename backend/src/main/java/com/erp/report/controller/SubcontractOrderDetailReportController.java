package com.erp.report.controller;

import com.erp.common.Result;
import com.erp.report.detail.SubcontractOrderDetailReportService;
import com.erp.report.detail.BillDetailReportEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 委外加工单明细报表接口。基础路径 {@code /api/report/subcontract/order-detail}；方法体与其它明细报表完全一致（同一基类）。
 */
@RestController
@RequestMapping("/api/report/subcontract/order-detail")
public class SubcontractOrderDetailReportController extends AbstractBillDetailReportController {

    private final SubcontractOrderDetailReportService service;

    public SubcontractOrderDetailReportController(SubcontractOrderDetailReportService service) {
        this.service = service;
    }

    @Override
    protected BillDetailReportEngine engine() {
        return service;
    }

    @Override
    protected String exportFilePrefix() {
        return "委外加工单明细报表";
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
