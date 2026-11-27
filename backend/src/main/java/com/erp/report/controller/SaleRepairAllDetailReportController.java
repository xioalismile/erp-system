package com.erp.report.controller;

import com.erp.common.Result;
import com.erp.report.detail.BillDetailReportEngine;
import com.erp.report.detail.SaleRepairAllDetailReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 售后维修单全明细报表接口。基础路径 {@code /api/report/sale/repair-all-detail}；
 * 与其它单据明细报表共用 {@link AbstractBillDetailReportController} 的同一套方法体。
 *
 * <p>2026-11-27：售后维修单编辑页有两张明细但列表页一直没有明细模式（静态体检点名的漏项）。
 * 本控制器提供合并后的全明细（维修明细 + 配件领用，含「明细类型」列）。</p>
 */
@RestController
@RequestMapping("/api/report/sale/repair-all-detail")
public class SaleRepairAllDetailReportController extends AbstractBillDetailReportController {

    private final SaleRepairAllDetailReportService service;

    public SaleRepairAllDetailReportController(SaleRepairAllDetailReportService service) {
        this.service = service;
    }

    @Override
    protected BillDetailReportEngine engine() {
        return service;
    }

    @Override
    protected String exportFilePrefix() {
        return "售后维修单全明细";
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
