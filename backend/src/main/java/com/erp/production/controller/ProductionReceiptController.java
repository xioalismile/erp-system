package com.erp.production.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.service.ProductionReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/receipts")
@RequiredArgsConstructor
public class ProductionReceiptController {

    private final ProductionReceiptService productionReceiptService;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤列表与卡片 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    @org.springframework.beans.factory.annotation.Autowired
    private BillCascadeDeleteService billCascadeDeleteService;

    @GetMapping("/page")
    public Result<PageResult<ProductionReceipt>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 自定义字段搜索（设置中心勾选「可搜索」的字段）：JSON {fieldKey: 值}
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(productionReceiptService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 列表页顶部统计卡片数据（数据权限与列表同口径：按角色 data_scope（统一数据权限），统一用列表的 created_by 列过滤） */
    @GetMapping("/dashboard")
    public Result<java.util.Map<String, Object>> dashboard() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("totalCount", countByScope(null, null, null));
        data.put("monthCount", countByScope(null, monthStart(), monthEnd()));
        data.put("draftCount", countByScope("草稿", null, null));
        data.put("pendingCount", countByScope("待审核", null, null));
        data.put("approvedCount", countByScope("已生效", null, null));
        return Result.success(data);
    }

    /**
     * 统计：状态（可空）+ 入库日期区间（可空）内的单据数。
     * 数据权限与列表逐字相同：同样按角色 data_scope（统一数据权限）过滤 created_by 列，
     * 避免出现「卡片数字 ≠ 列表条数」。
     */
    private long countByScope(String status, String startDate, String endDate) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProductionReceipt> w =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        com.erp.common.query.ListQuerySupport.applyDateRange(w, "receipt_date", startDate, endDate);
        dataScopeResolver.applyIfLimited(w, null, "created_by");
        return productionReceiptService.count(w);
    }

    /** 本月起始：当月 1 号 */
    private String monthStart() {
        return java.time.LocalDate.now().withDayOfMonth(1).toString();
    }

    /** 本月结束：当月最后一天（"1 号 ~ 最后一天"，与卡片点击套用的区间一致） */
    private String monthEnd() {
        return java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString();
    }

    @GetMapping("/list")
    public Result<List<ProductionReceipt>> list() {
        return Result.success(productionReceiptService.list());
    }

    @GetMapping("/{id}")
    public Result<ProductionReceipt> detail(@PathVariable Long id) {
        return Result.success(productionReceiptService.getDetail(id));
    }

    @PostMapping
    public Result<ProductionReceipt> create(@RequestBody ProductionReceipt receipt) {
        productionReceiptService.saveReceiptWithItems(receipt, receipt.getItems());
        return Result.success(receipt);
    }

    @PutMapping("/{id}")
    public Result<ProductionReceipt> update(@PathVariable Long id, @RequestBody ProductionReceipt receipt) {
        receipt.setReceiptId(id);
        productionReceiptService.saveReceiptWithItems(receipt, receipt.getItems());
        return Result.success(receipt);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 整单删除统一走状态机：草稿校验 + 下游校验 + 明细级联删除
        productionReceiptService.deleteBill(id);
        return Result.success();
    }

    /** 审核: 草稿 → 已生效 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        productionReceiptService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已生效 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        productionReceiptService.close(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason,
                               HttpServletRequest req) {
        productionReceiptService.cancel(id, reason);
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        productionReceiptService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        productionReceiptService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        productionReceiptService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        productionReceiptService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        productionReceiptService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
