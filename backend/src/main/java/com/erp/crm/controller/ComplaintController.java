package com.erp.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.Complaint;
import com.erp.crm.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crm/complaint")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/page")
    public Result<PageResult<Complaint>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String complaintType,
            @RequestParam(required = false) String severityLevel,
            @RequestParam(required = false) Long customerId,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(complaintService.pageQuery(current, size, keyword, handleStatus,
                complaintType, severityLevel, customerId, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<Complaint> detail(@PathVariable Long id) {
        return Result.success(complaintService.getDetail(id));
    }

    @PostMapping
    public Result<Complaint> create(@RequestBody Complaint complaint) {
        complaintService.saveComplaint(complaint);
        return Result.success(complaint);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Complaint complaint) {
        complaint.setComplaintId(id);
        complaintService.updateById(complaint);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        complaintService.removeById(id);
        return Result.success();
    }

    /** 分配处理人 */
    @PutMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        complaintService.assignHandler(id, body.get("handlerId"));
        return Result.success();
    }

    /** 解决投诉 */
    @PutMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        complaintService.resolve(id, body.get("handleResult"));
        return Result.success();
    }

    /** 关闭投诉 */
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        complaintService.close(id, body.get("satisfactionScore"));
        return Result.success();
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);

        // 处理状态分布
        Map<String, Object> statusDist = new HashMap<>();
        QueryWrapper<Complaint> w1 = new QueryWrapper<Complaint>().eq("handle_status", "新建");
        scopeCtx.applyTo(w1, null);
        statusDist.put("新建", complaintService.count(w1));
        QueryWrapper<Complaint> w2 = new QueryWrapper<Complaint>().eq("handle_status", "处理中");
        scopeCtx.applyTo(w2, null);
        statusDist.put("处理中", complaintService.count(w2));
        QueryWrapper<Complaint> w3 = new QueryWrapper<Complaint>().eq("handle_status", "已解决");
        scopeCtx.applyTo(w3, null);
        statusDist.put("已解决", complaintService.count(w3));
        QueryWrapper<Complaint> w4 = new QueryWrapper<Complaint>().eq("handle_status", "已关闭");
        scopeCtx.applyTo(w4, null);
        statusDist.put("已关闭", complaintService.count(w4));

        // 投诉类型分布
        Map<String, Object> typeDist = new HashMap<>();
        QueryWrapper<Complaint> typeWrapper = new QueryWrapper<Complaint>().select("complaint_type", "COUNT(*) AS cnt").groupBy("complaint_type");
        scopeCtx.applyTo(typeWrapper, null);
        List<Map<String, Object>> typeList = complaintService.getBaseMapper().selectMaps(typeWrapper);
        if (typeList != null) {
            for (Map<String, Object> row : typeList) {
                String type = row.get("complaint_type") != null ? row.get("complaint_type").toString() : "未分类";
                typeDist.put(type, row.get("cnt"));
            }
        }

        // 严重级别分布
        Map<String, Object> severityDist = new HashMap<>();
        QueryWrapper<Complaint> s1 = new QueryWrapper<Complaint>().eq("severity_level", "高");
        scopeCtx.applyTo(s1, null);
        severityDist.put("高", complaintService.count(s1));
        QueryWrapper<Complaint> s2 = new QueryWrapper<Complaint>().eq("severity_level", "中");
        scopeCtx.applyTo(s2, null);
        severityDist.put("中", complaintService.count(s2));
        QueryWrapper<Complaint> s3 = new QueryWrapper<Complaint>().eq("severity_level", "低");
        scopeCtx.applyTo(s3, null);
        severityDist.put("低", complaintService.count(s3));

        QueryWrapper<Complaint> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, null);
        long totalComplaints = complaintService.count(totalW);

        QueryWrapper<Complaint> highPendingW = new QueryWrapper<Complaint>()
                .eq("severity_level", "高").in("handle_status", "新建", "处理中");
        scopeCtx.applyTo(highPendingW, null);
        long highSeverityPending = complaintService.count(highPendingW);

        // 平均满意度 (已关闭投诉)
        Double avgSatisfaction = 0.0;
        QueryWrapper<Complaint> avgW = new QueryWrapper<Complaint>().select("AVG(satisfaction_score)").isNotNull("close_date");
        scopeCtx.applyTo(avgW, null);
        List<Object> avgList = complaintService.getBaseMapper().selectObjs(avgW);
        if (avgList != null && !avgList.isEmpty() && avgList.get(0) != null) {
            avgSatisfaction = ((Number) avgList.get(0)).doubleValue();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("statusDist", statusDist);
        data.put("typeDist", typeDist);
        data.put("severityDist", severityDist);
        data.put("totalComplaints", totalComplaints);
        data.put("highSeverityPending", highSeverityPending);
        data.put("avgSatisfaction", avgSatisfaction);
        return Result.success(data);
    }
}
