package com.erp.freesample.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.freesample.entity.FreeSampleOrder;
import com.erp.freesample.service.FreeSampleOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 免费样品订单 Controller
 * 路由: /api/free-sample
 * 业务：业务员录入免费样品订单（金额为0），仓库接单后直接发货，关联客户跟进记录
 * 状态流转: 草稿 → 待审核 → 已生效 → 已发货 → 已完成
 */
@RestController
@RequestMapping("/api/free-sample")
@RequiredArgsConstructor
public class FreeSampleOrderController {

    private final FreeSampleOrderService freeSampleOrderService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<FreeSampleOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(freeSampleOrderService.pageList(current, size, keyword, status, customSearch, headerFilters));
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public Result<FreeSampleOrder> detail(@PathVariable Long id) {
        return Result.success(freeSampleOrderService.getById(id));
    }

    /** 创建 */
    @PostMapping
    public Result<FreeSampleOrder> create(@RequestBody FreeSampleOrder order, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) {
            if (order.getCreatedBy() == null) order.setCreatedBy(username);
            order.setUpdatedBy(username);
        }
        return Result.success(freeSampleOrderService.saveWithItems(order));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<FreeSampleOrder> update(@PathVariable Long id, @RequestBody FreeSampleOrder order, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) order.setUpdatedBy(username);
        return Result.success(freeSampleOrderService.update(id, order));
    }

    /** 删除：仅草稿状态可删 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        freeSampleOrderService.delete(id);
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        freeSampleOrderService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        freeSampleOrderService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 发货: 已生效 → 已发货（参数 expressNo, expressCompany） */
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        freeSampleOrderService.ship(id, body.get("expressNo"), body.get("expressCompany"),
                (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 完成: 已发货 → 已完成 */
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, HttpServletRequest req) {
        freeSampleOrderService.complete(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 生成单号 FS-yyyyMMdd-0001 */
    @GetMapping("/generate-no")
    public Result<Map<String, Object>> generateNo() {
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", freeSampleOrderService.generateOrderNo());
        return Result.success(data);
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        freeSampleOrderService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        freeSampleOrderService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        freeSampleOrderService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        freeSampleOrderService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        freeSampleOrderService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        freeSampleOrderService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
