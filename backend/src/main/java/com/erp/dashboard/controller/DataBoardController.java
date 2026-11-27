package com.erp.dashboard.controller;

import com.erp.common.Result;
import com.erp.dashboard.service.DataBoardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据看板（财务/销售/仓储三大板块，指标口径见 DataBoardService）。
 * 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定统计范围——
 * 仅本人=只统计自己创建/经手的单据；本部门=部门成员的；全部数据=全量统计。
 */
@RestController
@RequestMapping("/api/data-board")
@RequiredArgsConstructor
public class DataBoardController {

    private final DataBoardService dataBoardService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/finance")
    public Result<Map<String, Object>> getFinanceBoard(@RequestParam(defaultValue = "month") String period,
                                                        HttpServletRequest request) {
        return Result.success(dataBoardService.getFinanceBoard(period, dataScopeResolver.resolve(request)));
    }

    @GetMapping("/sale")
    public Result<Map<String, Object>> getSaleBoard(@RequestParam(defaultValue = "month") String period,
                                                    HttpServletRequest request) {
        return Result.success(dataBoardService.getSaleBoard(period, dataScopeResolver.resolve(request)));
    }

    @GetMapping("/inventory")
    public Result<Map<String, Object>> getInventoryBoard(@RequestParam(defaultValue = "month") String period,
                                                         HttpServletRequest request) {
        return Result.success(dataBoardService.getInventoryBoard(period, dataScopeResolver.resolve(request)));
    }
}
