package com.erp.price.controller;

import com.erp.common.Result;
import com.erp.price.service.PriceFetchService;
import com.erp.price.vo.PriceFetchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 单据取价接口（2026-09-20 价格策略修复）。
 * <p>
 * 单据编辑页在「选物料 / 改数量」时调用本接口，按设置中心的「取价优先级」拿到单价后回填明细。
 * 只读接口，不做任何落库；保存仍不校验（保持既定口径），严格校验在提交环节。
 * </p>
 */
@RestController
@RequestMapping("/api/price")
@RequiredArgsConstructor
public class PriceFetchController {

    private final PriceFetchService priceFetchService;

    /**
     * 取价
     *
     * @param side       sale=销售（默认）/ purchase=采购
     * @param partnerId  销售=客户ID；采购=供应商ID（可空）
     * @param materialId 物料ID（必填）
     * @param quantity   数量（策略数量区间匹配用，可空）
     * @param taxType    计税方式（可空）
     */
    @GetMapping("/fetch")
    public Result<PriceFetchResult> fetch(
            @RequestParam(defaultValue = "sale") String side,
            @RequestParam(required = false) Long partnerId,
            @RequestParam Long materialId,
            @RequestParam(required = false) BigDecimal quantity,
            @RequestParam(required = false) String taxType) {
        return Result.success(priceFetchService.fetch(side, partnerId, materialId, quantity, taxType));
    }
}
