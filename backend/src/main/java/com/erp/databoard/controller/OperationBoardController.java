package com.erp.databoard.controller;

import com.erp.common.Result;
import com.erp.databoard.service.OperationBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 经营看板（总经理视角 · 2026-10-07）
 *
 * <p>本接口只提供「生产 / 采购 / 质量 / 人力」四块；「销售 / 利润 / 回款 / 库存」由前端并行调用既有
 * 数据看板三接口（/api/data-board/sale|finance|inventory），保证同一口径、不重复实现。</p>
 */
@RestController
@RequestMapping("/api/data-board/operation")
@RequiredArgsConstructor
public class OperationBoardController {

    private final OperationBoardService operationBoardService;

    @GetMapping
    public Result<Map<String, Object>> board(@RequestParam(required = false, defaultValue = "month") String period) {
        return Result.success(operationBoardService.board(period));
    }
}
