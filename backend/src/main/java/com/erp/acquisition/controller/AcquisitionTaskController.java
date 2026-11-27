package com.erp.acquisition.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.acquisition.entity.AcquisitionResult;
import com.erp.acquisition.entity.AcquisitionTask;
import com.erp.acquisition.service.AcquisitionResultService;
import com.erp.acquisition.service.AcquisitionTaskService;
import com.erp.common.PageResult;
import com.erp.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/acquisition/task")
@RequiredArgsConstructor
public class AcquisitionTaskController {

    private final AcquisitionTaskService acquisitionTaskService;
    private final AcquisitionResultService acquisitionResultService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<AcquisitionTask>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(acquisitionTaskService.pageQuery(current, size, keyword, platform, status, scopeCtx, sortBy, sortOrder));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AcquisitionTask> detail(@PathVariable Long id) {
        return Result.success(acquisitionTaskService.getDetail(id));
    }

    /** 创建任务 */
    @PostMapping
    public Result<AcquisitionTask> create(@RequestBody AcquisitionTask task) {
        return Result.success(acquisitionTaskService.createTask(task));
    }

    /** 更新任务 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AcquisitionTask task) {
        task.setTaskId(id);
        acquisitionTaskService.updateTask(task);
        return Result.success();
    }

    /** 删除任务（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        acquisitionTaskService.deleteTask(id);
        return Result.success();
    }

    /** 启动采集任务 */
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        acquisitionTaskService.startTask(id);
        return Result.success();
    }

    /** 暂停任务 */
    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id) {
        acquisitionTaskService.pauseTask(id);
        return Result.success();
    }

    /**
     * 采集服务回调更新任务状态
     * 采集服务执行采集过程中通过此接口回写任务状态/采集数量/错误信息
     * 接收状态: running / completed / failed / paused
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        Integer capturedCount = body.get("capturedCount") == null ? null
                : Integer.valueOf(String.valueOf(body.get("capturedCount")));
        String errorMessage = body.get("errorMessage") == null ? null : String.valueOf(body.get("errorMessage"));
        acquisitionTaskService.updateStatus(id, status, capturedCount, errorMessage);
        return Result.success();
    }

    /** 保存企查查登录Cookie */
    @PostMapping("/{id}/login-cookie")
    public Result<Void> saveLoginCookie(@PathVariable Long id, @RequestBody Map<String, String> body) {
        acquisitionTaskService.saveLoginCookie(id, body.get("cookie"));
        return Result.success();
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);

        QueryWrapper<AcquisitionTask> totalW = new QueryWrapper<AcquisitionTask>().eq("deleted", 0);
        scopeCtx.applyTo(totalW, null);
        long totalTasks = acquisitionTaskService.count(totalW);
        QueryWrapper<AcquisitionTask> completedW = new QueryWrapper<AcquisitionTask>().eq("deleted", 0).eq("status", "completed");
        scopeCtx.applyTo(completedW, null);
        long completedTasks = acquisitionTaskService.count(completedW);
        QueryWrapper<AcquisitionTask> runningW = new QueryWrapper<AcquisitionTask>().eq("deleted", 0).eq("status", "running");
        scopeCtx.applyTo(runningW, null);
        long runningTasks = acquisitionTaskService.count(runningW);
        QueryWrapper<AcquisitionTask> failedW = new QueryWrapper<AcquisitionTask>().eq("deleted", 0).eq("status", "failed");
        scopeCtx.applyTo(failedW, null);
        long failedTasks = acquisitionTaskService.count(failedW);
        QueryWrapper<AcquisitionTask> pendingW = new QueryWrapper<AcquisitionTask>().eq("deleted", 0).eq("status", "pending");
        scopeCtx.applyTo(pendingW, null);
        long pendingTasks = acquisitionTaskService.count(pendingW);

        QueryWrapper<AcquisitionResult> capturedW = new QueryWrapper<AcquisitionResult>().eq("deleted", 0);
        scopeCtx.applyTo(capturedW, null);
        long capturedCount = acquisitionResultService.count(capturedW);
        QueryWrapper<AcquisitionResult> importedW = new QueryWrapper<AcquisitionResult>().eq("deleted", 0).eq("review_status", "imported");
        scopeCtx.applyTo(importedW, null);
        long importedCount = acquisitionResultService.count(importedW);
        QueryWrapper<AcquisitionResult> pendingReviewW = new QueryWrapper<AcquisitionResult>().eq("deleted", 0).eq("review_status", "pending");
        scopeCtx.applyTo(pendingReviewW, null);
        long pendingReviewCount = acquisitionResultService.count(pendingReviewW);

        Map<String, Object> statusDistribution = new HashMap<>();
        statusDistribution.put("pending", pendingTasks);
        statusDistribution.put("running", runningTasks);
        statusDistribution.put("completed", completedTasks);
        statusDistribution.put("failed", failedTasks);

        Map<String, Object> data = new HashMap<>();
        data.put("totalTasks", totalTasks);
        data.put("completedTasks", completedTasks);
        data.put("runningTasks", runningTasks);
        data.put("failedTasks", failedTasks);
        data.put("capturedCount", capturedCount);
        data.put("importedCount", importedCount);
        data.put("pendingReviewCount", pendingReviewCount);
        data.put("statusDistribution", statusDistribution);
        return Result.success(data);
    }
}
