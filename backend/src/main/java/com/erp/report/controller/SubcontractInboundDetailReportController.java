package com.erp.report.controller;

import com.erp.common.Result;
import com.erp.report.detail.SubcontractInboundDetailReportService;
import com.erp.report.detail.BillDetailReportEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 委外入库单明细报表接口。基础路径 {@code /api/report/subcontract/inbound-detail}；方法体与其它明细报表完全一致（同一基类）。
 */
@RestController
@RequestMapping("/api/report/subcontract/inbound-detail")
public class SubcontractInboundDetailReportController extends AbstractBillDetailReportController {

    private final SubcontractInboundDetailReportService service;

    public SubcontractInboundDetailReportController(SubcontractInboundDetailReportService service) {
        this.service = service;
    }

    @Override
    protected BillDetailReportEngine engine() {
        return service;
    }

    @Override
    protected String exportFilePrefix() {
        return "委外入库单明细报表";
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
