package com.erp.sample.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.masterdata.entity.Bom;
import com.erp.sample.entity.SampleOrder;
import com.erp.sample.service.SampleOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打样单 Controller
 * 路由: /api/sample/orders
 * 文档5.9 打样单接口
 */
@RestController
@RequestMapping("/api/sample/orders")
@RequiredArgsConstructor
public class SampleOrderController {

    private final SampleOrderService sampleOrderService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    @org.springframework.beans.factory.annotation.Autowired
    private BillCascadeDeleteService billCascadeDeleteService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<SampleOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customSearch,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(sampleOrderService.pageQuery(current, size, keyword, status, customSearch, scopeCtx, sortBy, sortOrder, headerFilters));
    }

    /** 全量列表 */
    @GetMapping("/list")
    public Result<List<SampleOrder>> list() {
        return Result.success(sampleOrderService.list());
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public Result<SampleOrder> detail(@PathVariable Long id) {
        return Result.success(sampleOrderService.getDetail(id));
    }

    /** 新增 */
    @PostMapping
    public Result<SampleOrder> create(@RequestBody SampleOrder sample, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) {
            if (sample.getCreatedBy() == null) sample.setCreatedBy(username);
            sample.setUpdatedBy(username);
        }
        sampleOrderService.saveSampleWithItems(sample, sample.getItems());
        return Result.success(sample);
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<SampleOrder> update(@PathVariable Long id, @RequestBody SampleOrder sample, HttpServletRequest req) {
        sample.setSampleId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) sample.setUpdatedBy(username);
        sampleOrderService.saveSampleWithItems(sample, sample.getItems());
        return Result.success(sample);
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("sample_order", id);
        sampleOrderService.removeById(id);
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 开始打样: 已生效 → 打样中 */
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.startSample(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 完成打样: 打样中 → 已完成 */
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.completeSample(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 确认样品: 已完成 → 已确认 */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.confirmSample(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        sampleOrderService.cancel(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /**
     * 打样转BOM: 已确认 → 已转BOM
     * 核心接口：以打样单表头物料为父项，表体子件清单为BOM子项生成正式BOM
     */
    @PostMapping("/{id}/convert-to-bom")
    public Result<Bom> convertToBom(@PathVariable Long id, HttpServletRequest req) {
        Bom bom = sampleOrderService.convertToBom(id, (String) req.getAttribute("currentUsername"));
        return Result.success(bom);
    }

    /** 生成打样单号 */
    @GetMapping("/generate-no")
    public Result<Map<String, Object>> generateNo() {
        Map<String, Object> data = new HashMap<>();
        data.put("sampleNo", sampleOrderService.generateSampleNo());
        return Result.success(data);
    }

    /** 更新打样进度（状态已生效且进度=已完成时自动推送生成BOM草稿，返回提示语） */
    @PutMapping("/{id}/progress")
    public Result<String> updateProgress(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        String progress = body.get("progress");
        String message = sampleOrderService.updateProgress(id, progress, (String) req.getAttribute("currentUsername"));
        return Result.success(message);
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        Map<String, Object> data = new HashMap<>();
        QueryWrapper<SampleOrder> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, null);
        data.put("total", sampleOrderService.count(totalW));
        QueryWrapper<SampleOrder> draftW = new QueryWrapper<SampleOrder>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, null);
        data.put("draft", sampleOrderService.count(draftW));
        QueryWrapper<SampleOrder> inProgressW = new QueryWrapper<SampleOrder>().eq("status", "打样中");
        scopeCtx.applyTo(inProgressW, null);
        data.put("inProgress", sampleOrderService.count(inProgressW));
        QueryWrapper<SampleOrder> completedW = new QueryWrapper<SampleOrder>().eq("status", "已完成");
        scopeCtx.applyTo(completedW, null);
        data.put("completed", sampleOrderService.count(completedW));
        QueryWrapper<SampleOrder> confirmedW = new QueryWrapper<SampleOrder>().eq("status", "已确认");
        scopeCtx.applyTo(confirmedW, null);
        data.put("confirmed", sampleOrderService.count(confirmedW));
        QueryWrapper<SampleOrder> convertedBomW = new QueryWrapper<SampleOrder>().eq("status", "已转BOM");
        scopeCtx.applyTo(convertedBomW, null);
        data.put("convertedBom", sampleOrderService.count(convertedBomW));
        QueryWrapper<SampleOrder> cancelledW = new QueryWrapper<SampleOrder>().eq("status", "已取消");
        scopeCtx.applyTo(cancelledW, null);
        data.put("cancelled", sampleOrderService.count(cancelledW));
        return Result.success(data);
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        sampleOrderService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        sampleOrderService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持不变，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        sampleOrderService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
