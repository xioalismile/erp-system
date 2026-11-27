package com.erp.produce.mrp.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.produce.mrp.entity.MrpPlan;
import com.erp.produce.mrp.service.MrpService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/mrp-plan")
@RequiredArgsConstructor
public class MrpController {

    private final MrpService mrpService;

    @GetMapping
    public Result<PageResult<MrpPlan>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(mrpService.pageQuery(current, size, keyword, status, sortBy, sortOrder, headerFilters));
    }

    @GetMapping("/{id}")
    public Result<MrpPlan> detail(@PathVariable Long id) {
        return Result.success(mrpService.getDetail(id));
    }

    @PostMapping
    public Result<MrpPlan> create(@RequestBody MrpPlan plan) {
        mrpService.createPlan(plan);
        return Result.success(plan);
    }

    /** 更新计划基本信息(仅草稿态可改) */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MrpPlan plan) {
        mrpService.updatePlan(id, plan);
        return Result.success();
    }

    /** 审核: 草稿(0)/已运算(1) → 已生效(2) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        mrpService.approvePlan(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 执行: 已生效(2) → 已执行(3) */
    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id, HttpServletRequest req) {
        mrpService.executePlan(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 驳回: 待审核(1) → 草稿(0) */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        mrpService.rejectPlan(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 重置草稿: 已生效(2) → 草稿(0) */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        mrpService.reverseApprovePlan(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        mrpService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        mrpService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效类(2/3) → close_status=1 */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        mrpService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        mrpService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    @PutMapping("/{id}/run")
    public Result<Void> runMrp(@PathVariable Long id) {
        mrpService.runMrp(id);
        return Result.success();
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        mrpService.confirmPlan(id);
        return Result.success();
    }

    @PostMapping("/{id}/generate-purchase")
    public Result<Void> generatePurchase(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> params) {
        List<Long> itemIds = null;
        if (params != null && params.get("itemIds") != null) {
            itemIds = (List<Long>) params.get("itemIds");
        }
        mrpService.generatePurchaseOrder(id, itemIds);
        return Result.success();
    }

    @PostMapping("/{id}/generate-produce")
    public Result<Void> generateProduce(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> params) {
        List<Long> itemIds = null;
        if (params != null && params.get("itemIds") != null) {
            itemIds = (List<Long>) params.get("itemIds");
        }
        mrpService.generateProduceOrder(id, itemIds);
        return Result.success();
    }

    /**
     * 根据MRP计划生成委外订单
     * suggest_type=SUBCONTRACT 的明细生成委外订单，并回写销售订单的已转委外数量
     */
    @PostMapping("/{id}/generate-subcontract")
    public Result<Void> generateSubcontract(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> params) {
        List<Long> itemIds = null;
        if (params != null && params.get("itemIds") != null) {
            itemIds = (List<Long>) params.get("itemIds");
        }
        mrpService.generateSubcontractOrder(id, itemIds);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mrpService.deletePlan(id);
        return Result.success();
    }

    @GetMapping("/generate-no")
    public Result<Map<String, String>> generateNo() {
        return Result.success(Map.of("planNo", mrpService.generatePlanNo()));
    }
}
