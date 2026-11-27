package com.erp.production.controller;

import com.erp.common.Result;
import com.erp.production.entity.ReleaseOrder;
import com.erp.production.service.ReleaseOrderService;
import com.erp.production.service.impl.ReleaseOrderServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 放行单 Controller（化妆品批记录）。
 * 路由: /api/production/release-orders
 *
 * <p>从批次一键生成草稿 → 提交（审批开关=RELEASE_ORDER）→ 审核生效回写批次「已放行」+ 自动留样。</p>
 */
@RestController
@RequestMapping("/api/production/release-orders")
@RequiredArgsConstructor
public class ReleaseOrderController {

    private final ReleaseOrderService releaseOrderService;
    private final ReleaseOrderServiceImpl releaseOrderStateMachine;

    /** 从批次一键生成放行单草稿 */
    @PostMapping("/create-from-batch/{batchId}")
    public Result<ReleaseOrder> createFromBatch(@PathVariable Long batchId, HttpServletRequest request) {
        return Result.success(releaseOrderService.createDraftFromBatch(batchId, request));
    }

    /** 保存草稿 */
    @PostMapping("/save-draft")
    public Result<Boolean> saveDraft(@RequestBody ReleaseOrder bill) {
        releaseOrderStateMachine.saveDraft(bill);
        return Result.success(true);
    }

    /** 提交（审批开=待审核；审批关=直接生效并回写批次+自动留样） */
    @PostMapping("/{id}/submit")
    public Result<Boolean> submit(@PathVariable Long id, HttpServletRequest request) {
        releaseOrderStateMachine.submitForApprovalById(id, com.erp.common.util.SecurityUtils.getCurrentUsername(request));
        return Result.success(true);
    }

    /** 审核通过（生效） */
    @PostMapping("/{id}/approve")
    public Result<Boolean> approve(@PathVariable Long id, HttpServletRequest request) {
        releaseOrderStateMachine.approve(id, com.erp.common.util.SecurityUtils.getCurrentUsername(request));
        return Result.success(true);
    }

    /** 驳回（回草稿） */
    @PostMapping("/{id}/reject")
    public Result<Boolean> reject(@PathVariable Long id,
                                  @RequestBody(required = false) java.util.Map<String, String> body,
                                  HttpServletRequest request) {
        String reason = body == null ? null : body.get("reason");
        releaseOrderStateMachine.reject(id, com.erp.common.util.SecurityUtils.getCurrentUsername(request), reason);
        return Result.success(true);
    }

    /** 重置草稿（批次回退合格） */
    @PostMapping("/{id}/reverse-approve")
    public Result<Boolean> reverseApprove(@PathVariable Long id, HttpServletRequest request) {
        releaseOrderStateMachine.reverseApprove(id, com.erp.common.util.SecurityUtils.getCurrentUsername(request));
        return Result.success(true);
    }

    /** 作废（批次回退合格） */
    @PostMapping("/{id}/void")
    public Result<Boolean> voidBill(@PathVariable Long id,
                                    @RequestBody(required = false) java.util.Map<String, String> body,
                                    HttpServletRequest request) {
        String reason = body == null ? null : body.get("reason");
        releaseOrderStateMachine.voidBill(id, com.erp.common.util.SecurityUtils.getCurrentUsername(request), reason);
        return Result.success(true);
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Boolean> unvoid(@PathVariable Long id, HttpServletRequest request) {
        releaseOrderStateMachine.unvoidBill(id, com.erp.common.util.SecurityUtils.getCurrentUsername(request));
        return Result.success(true);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<ReleaseOrder> detail(@PathVariable Long id) {
        return Result.success(releaseOrderStateMachine.getById(id));
    }
}
