package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.service.MaterialRequisitionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/material-requisitions")
@RequiredArgsConstructor
public class MaterialRequisitionController {

    private final MaterialRequisitionService materialRequisitionService;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤，替代旧 SecurityUtils.getCreatedByFilter 硬编码 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    @org.springframework.beans.factory.annotation.Autowired
    private BillCascadeDeleteService billCascadeDeleteService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<MaterialRequisition>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        if (billType != null && !billType.isBlank()) {
            return Result.success(materialRequisitionService.pageQueryByBillType(p[0], p[1], keyword, status, billType, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, headerFilters));
        }
        return Result.success(materialRequisitionService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, headerFilters));
    }

    /**
     * 列表页顶部统计卡片数据（与列表同数据权限口径：按角色 data_scope（统一数据权限）过滤）
     * billType 可空：为空=统计全部类型（兼容老调用）；传 PICK/RETURN/SUPPLEMENT=按单据类型统计
     */
    @GetMapping("/dashboard")
    public Result<java.util.Map<String, Object>> dashboard(
            @RequestParam(required = false) String billType,
            HttpServletRequest request) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthCount", countThisMonth(billType));
        data.put("draftCount", countByStatus("草稿", billType));
        data.put("pendingCount", countByStatus("待审核", billType));
        data.put("approvedCount", countByStatus("已生效", billType));
        return Result.success(data);
    }

    /** 统计：指定状态单据数（同列表数据权限口径：按角色 data_scope；billType 非空时按单据类型隔离） */
    private long countByStatus(String status, String billType) {
        QueryWrapper<MaterialRequisition> w = new QueryWrapper<MaterialRequisition>().eq("status", status);
        if (billType != null && !billType.isBlank()) w.eq("bill_type", billType);
        dataScopeResolver.applyIfLimited(w, null, "created_by");
        return materialRequisitionService.count(w);
    }

    /** 统计：本月（1 号至当月最后一天，与卡片筛选区间一致）单据数（按领料日期 requisition_date；billType 非空时按单据类型隔离） */
    private long countThisMonth(String billType) {
        QueryWrapper<MaterialRequisition> w = new QueryWrapper<MaterialRequisition>();
        if (billType != null && !billType.isBlank()) w.eq("bill_type", billType);
        com.erp.common.query.ListQuerySupport.applyDateRange(w, "requisition_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        dataScopeResolver.applyIfLimited(w, null, "created_by");
        return materialRequisitionService.count(w);
    }

    /** 全量列表 */
    @GetMapping("/list")
    public Result<List<MaterialRequisition>> list() {
        return Result.success(materialRequisitionService.list(new QueryWrapper<MaterialRequisition>()
                .orderByAsc("created_at is null").orderByDesc("created_at")));
    }

    /** 详情(含明细) */
    @GetMapping("/{id}")
    public Result<MaterialRequisition> detail(@PathVariable Long id) {
        return Result.success(materialRequisitionService.getDetail(id));
    }

    /** 新增 */
    @PostMapping
    public Result<MaterialRequisition> create(@RequestBody MaterialRequisition requisition) {
        materialRequisitionService.saveRequisitionWithItems(requisition, requisition.getItems());
        return Result.success(requisition);
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialRequisition requisition) {
        requisition.setRequisitionId(id);
        materialRequisitionService.saveRequisitionWithItems(requisition, requisition.getItems());
        return Result.success();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 2026-11-16：删除统一走单据状态机（草稿直接删；已生效先对冲库存再删；级联清明细+按编号清库存流水）
        materialRequisitionService.deleteBill(id);
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 (扣减库存) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam(required = false) String approver,
                                HttpServletRequest req) {
        String operator = (approver != null && !approver.isBlank())
                ? approver
                : (String) req.getAttribute("currentUsername");
        materialRequisitionService.approve(id, operator);
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        materialRequisitionService.submitForApproval(id);
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        materialRequisitionService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已生效 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        String closer = (String) req.getAttribute("currentUsername");
        materialRequisitionService.close(id, closer, reason);
        return Result.success();
    }

    /** 反结案: 已关闭 → 已生效 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        materialRequisitionService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        materialRequisitionService.cancel(id, reason);
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        materialRequisitionService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        materialRequisitionService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        materialRequisitionService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
