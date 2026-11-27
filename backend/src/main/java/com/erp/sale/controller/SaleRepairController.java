package com.erp.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.sale.entity.SaleRepair;
import com.erp.sale.service.SaleRepairService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 售后维修单。
 *
 * <p>库存动作（收件入库 / 配件领用 / 返还）在本控制器只触发"生成其他入库单/其他出库单草稿"，
 * 真正过账仍由库存单据自己的提交/审核完成。</p>
 */
@RestController
@RequestMapping("/api/sale/repairs")
@RequiredArgsConstructor
public class SaleRepairController {

    private final SaleRepairService saleRepairService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<SaleRepair>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customerId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch) {
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(saleRepairService.pageQuery(p[0], p[1], keyword, status, startDate, endDate,
                customerId, scopeCtx, sortBy, sortOrder, customSearch));
    }

    /** 新单号预取 */
    @GetMapping("/next-no")
    public Result<String> nextNo() {
        return Result.success(saleRepairService.generateRepairNo());
    }

    @GetMapping("/{id}")
    public Result<SaleRepair> detail(@PathVariable Long id) {
        return Result.success(saleRepairService.getDetail(id));
    }

    @PostMapping
    public Result<SaleRepair> create(@RequestBody SaleRepair bill, HttpServletRequest request) {
        String operator = (String) request.getAttribute("currentUsername");
        bill.setRepairId(null);
        bill.setCreatedBy(operator);
        bill.setUpdatedBy(operator);
        return Result.success(saleRepairService.saveRepairWithItems(bill, bill.getItems(), bill.getParts()));
    }

    @PutMapping("/{id}")
    public Result<SaleRepair> update(@PathVariable Long id, @RequestBody SaleRepair bill, HttpServletRequest request) {
        bill.setRepairId(id);
        bill.setUpdatedBy((String) request.getAttribute("currentUsername"));
        return Result.success(saleRepairService.saveRepairWithItems(bill, bill.getItems(), bill.getParts()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleRepairService.deleteBill(id);
        return Result.success();
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        saleRepairService.submitForApproval(id);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest request) {
        saleRepairService.approve(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        saleRepairService.reject(id, (String) request.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest request) {
        saleRepairService.reverseApprove(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id,
                                 @RequestBody(required = false) Map<String, String> body,
                                 HttpServletRequest request) {
        String reason = body == null ? null : body.get("reason");
        saleRepairService.voidBill(id, (String) request.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    @PostMapping("/{id}/start")
    public Result<Void> startRepair(@PathVariable Long id, HttpServletRequest request) {
        saleRepairService.startRepair(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/{id}/complete")
    public Result<Void> completeRepair(@PathVariable Long id,
                                       @RequestBody Map<String, String> body,
                                       HttpServletRequest request) {
        saleRepairService.completeRepair(id, (String) request.getAttribute("currentUsername"), body.get("result"));
        return Result.success();
    }

    @PostMapping("/{id}/receive-in")
    public Result<Void> receiveIn(@PathVariable Long id, HttpServletRequest request) {
        saleRepairService.receiveIn(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/{id}/issue-parts")
    public Result<Void> issueParts(@PathVariable Long id, HttpServletRequest request) {
        saleRepairService.issueParts(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/{id}/return-to-customer")
    public Result<Void> returnToCustomer(@PathVariable Long id, HttpServletRequest request) {
        saleRepairService.returnToCustomer(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 转换新：body.lines = [{repairItemId, substituteMaterialId}] */
    @PostMapping("/{id}/transfer-exchange")
    public Result<SaleRepair> transferToExchange(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        return Result.success(saleRepairService.transferToExchange(id, lines,
                (String) request.getAttribute("currentUsername")));
    }

    @PostMapping("/{id}/transfer-return")
    public Result<SaleRepair> transferToReturn(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(saleRepairService.transferToReturn(id,
                (String) request.getAttribute("currentUsername")));
    }
}
