package com.erp.price.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.price.sale.entity.SalePriceStrategy;
import com.erp.price.sale.service.SalePriceStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/price/sale-strategy")
@RequiredArgsConstructor
public class SalePriceStrategyController {

    private final SalePriceStrategyService salePriceStrategyService;

    @GetMapping("/page")
    public Result<PageResult<SalePriceStrategy>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(salePriceStrategyService.listPage(current, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<SalePriceStrategy> get(@PathVariable Long id) {
        return Result.success(salePriceStrategyService.getById(id));
    }

    @PostMapping
    public Result<SalePriceStrategy> save(@RequestBody SalePriceStrategy strategy) {
        salePriceStrategyService.save(strategy, strategy.getItems());
        // 返回落库后的实体（id + 系统自动生成的策略编码），供前端回显
        return Result.success(strategy);
    }

    @PutMapping
    public Result<Void> update(@RequestBody SalePriceStrategy strategy) {
        salePriceStrategyService.update(strategy, strategy.getItems());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        salePriceStrategyService.delete(id);
        return Result.success();
    }

    @PostMapping("/toggle-status/{id}")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        salePriceStrategyService.toggleStatus(id);
        return Result.success();
    }

    /** 新增页默认优先级：下一个可用号（保证不与现有策略重复） */
    @GetMapping("/next-priority")
    public Result<Integer> nextPriority() {
        return Result.success(salePriceStrategyService.nextPriority());
    }

    @GetMapping("/calculate")
    public Result<BigDecimal> calculatePrice(
            @RequestParam Long customerId,
            @RequestParam Long materialId,
            @RequestParam(required = false) BigDecimal quantity,
            @RequestParam(required = false) String taxType) {
        return Result.success(salePriceStrategyService.calculatePrice(customerId, materialId, quantity, taxType));
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        salePriceStrategyService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        salePriceStrategyService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        salePriceStrategyService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        salePriceStrategyService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
