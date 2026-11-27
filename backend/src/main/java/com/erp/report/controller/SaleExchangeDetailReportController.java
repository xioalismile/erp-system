package com.erp.report.controller;

import com.erp.common.Result;
import com.erp.report.detail.SaleExchangeDetailReportService;
import com.erp.report.detail.BillDetailReportEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 销售换货单明细报表接口。基础路径 {@code /api/report/sale/exchange-detail}；
 * 与其它单据明细报表共用 {@link AbstractBillDetailReportController} 的同一套方法体。
 *
 * <p>2026-11-22：列表页「销售换货单」登记了明细模式但后端缺此接口，进入页面会弹「接口不存在」；
 * 本控制器补齐后，换货单列表的汇总/明细双视图与导出恢复可用。
 */
@RestController
@RequestMapping("/api/report/sale/exchange-detail")
public class SaleExchangeDetailReportController extends AbstractBillDetailReportController {

    private final SaleExchangeDetailReportService service;

    public SaleExchangeDetailReportController(SaleExchangeDetailReportService service) {
        this.service = service;
    }

    @Override
    protected BillDetailReportEngine engine() {
        return service;
    }

    @Override
    protected String exportFilePrefix() {
        return "销售换货单明细报表";
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
