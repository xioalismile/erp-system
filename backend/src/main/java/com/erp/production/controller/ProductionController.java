package com.erp.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.service.WorkOrderService;
import com.erp.production.vo.WorkOrderProgressVO;
import com.erp.subcontract.order.entity.SubcontractOrder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {

    private final WorkOrderService workOrderService;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤工单列表与卡片 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/workorders")
    public Result<PageResult<WorkOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(workOrderService.pageQuery(p[0], p[1], keyword, status, priority,
                startDate, endDate, employeeId, sortBy, sortOrder, headerFilters, customSearch));
    }

    @GetMapping("/workorders/{id}")
    public Result<WorkOrder> detail(@PathVariable Long id) {
        return Result.success(workOrderService.getDetail(id));
    }

    @GetMapping("/workorders/{id}/progress")
    public Result<WorkOrderProgressVO> getProgress(@PathVariable Long id) {
        return Result.success(workOrderService.getProgressDetail(id));
    }

    @PostMapping("/workorders")
    public Result<WorkOrder> create(@RequestBody WorkOrder wo) {
        workOrderService.saveOrderWithItems(wo, wo.getMaterials());
        // 新增后回传生成的主键与单号：前端新建保存需据此写回地址栏（否则 F5 丢草稿、后续提交无单据ID）
        return Result.success(wo);
    }

    @PutMapping("/workorders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WorkOrder wo) {
        wo.setOrderId(id);
        workOrderService.saveOrderWithItems(wo, wo.getMaterials());
        return Result.success();
    }

    /**
     * 【只保存工单工序清单】不校验整单状态、不改动单据其它字段（2026-09-27 第7期补充）。
     * 用于两类场景：① 草稿期在「工序明细」配好工序只想落库；② 已生效但生效时一道工序都没配时补选。
     * 已生效且已有工序 → 服务层抛业务异常（工序清单锁死）。
     */
    @PostMapping("/workorders/{id}/operations")
    public Result<List<WorkOrderOperation>> saveOperations(@PathVariable Long id,
                                                           @RequestBody List<WorkOrderOperation> operations) {
        return Result.success(workOrderService.saveOrderOperations(id, operations));
    }

    /** 提交: 草稿 → 待审核（免审批时提交即生效并生成工序实例） */
    @PostMapping("/workorders/{id}/submit")
    public Result<Void> submit(@PathVariable Long id,
                               @RequestHeader(value = "X-Operator", required = false) String operator,
                               HttpServletRequest req) {
        // 前端未传 X-Operator 时兜底取当前登录人，避免提交人落空串
        String op = (operator != null && !operator.isBlank())
                ? operator : (String) req.getAttribute("currentUsername");
        workOrderService.submit(id, op);
        return Result.success();
    }

    /** 审核: 待审核 → 已生效（并生成工序实例） */
    @PostMapping("/workorders/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator,
                                HttpServletRequest req) {
        String op = (operator != null && !operator.isBlank())
                ? operator : (String) req.getAttribute("currentUsername");
        workOrderService.approve(id, op);
        return Result.success();
    }

    @PostMapping("/workorders/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                             @RequestHeader(value = "X-Operator", required = false) String operator,
                             HttpServletRequest req) {
        String reason = body != null ? body.get("reason") : null;
        // 前端未传 X-Operator 时兜底取当前登录人，避免结案人落空串
        String op = (operator != null && !operator.isBlank())
                ? operator : (String) req.getAttribute("currentUsername");
        workOrderService.close(id, op, reason);
        return Result.success();
    }

    /** 驳回: 待审核 → 草稿 */
    @PostMapping("/workorders/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                               HttpServletRequest req) {
        workOrderService.rejectBill(id, (String) req.getAttribute("currentUsername"),
                body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 重置草稿(撤销生效): 已生效且未结案 → 草稿 */
    @PostMapping("/workorders/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        workOrderService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废: 草稿/已生效类(未结案) → 已作废 */
    @PostMapping("/workorders/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                                 HttpServletRequest req) {
        workOrderService.voidBill(id, (String) req.getAttribute("currentUsername"),
                body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废: 已作废 → 恢复作废前状态 */
    @PostMapping("/workorders/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        workOrderService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: 已结案 → 未结案 */
    @PostMapping("/workorders/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        workOrderService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除工单(仅草稿族可删) */
    @DeleteMapping("/workorders/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workOrderService.deleteBill(id);
        return Result.success();
    }

    @GetMapping("/workorders/generate-no")
    public Result<Map<String, String>> generateOrderNo() {
        Map<String, String> map = new HashMap<>();
        map.put("orderNo", workOrderService.generateOrderNo());
        return Result.success(map);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        // 数据权限：与列表逐字同口径（按角色 data_scope，统一数据权限）
        QueryWrapper<WorkOrder> totalW = new QueryWrapper<>();
        dataScopeResolver.applyIfLimited(totalW, null, "created_by");
        long total = workOrderService.count(totalW);
        stats.put("totalWorkOrders", total);
        // 【通用状态机口径】status 只有 草稿/待审核/已生效/已作废（金蝶/用友标准），业务闭环独立走 close_status：
        //   draft    = 草稿（未提交）
        //   pending  = 待审核（已提交，等审批）
        //   approved = 已生效 且 未结案（close_status 为空或 0）—— 可下推、可结案的在制工单
        //   closed   = 已生效 且 已结案（close_status = 1）—— 已闭环工单（含入库达标自动结案）
        // 四者互斥不重叠，合计 = 已提交口径下的全部业务工单。
        QueryWrapper<WorkOrder> draftW = new QueryWrapper<WorkOrder>().eq("status", "草稿");
        dataScopeResolver.applyIfLimited(draftW, null, "created_by");
        stats.put("draft", workOrderService.count(draftW));
        QueryWrapper<WorkOrder> pendingW = new QueryWrapper<WorkOrder>().eq("status", "待审核");
        dataScopeResolver.applyIfLimited(pendingW, null, "created_by");
        stats.put("pending", workOrderService.count(pendingW));
        QueryWrapper<WorkOrder> approvedW = new QueryWrapper<WorkOrder>().eq("status", "已生效")
                .and(w -> w.isNull("close_status").or().ne("close_status", 1));
        dataScopeResolver.applyIfLimited(approvedW, null, "created_by");
        stats.put("approved", workOrderService.count(approvedW));
        QueryWrapper<WorkOrder> closedW = new QueryWrapper<WorkOrder>()
                .eq("status", "已生效").eq("close_status", 1);
        dataScopeResolver.applyIfLimited(closedW, null, "created_by");
        stats.put("closed", workOrderService.count(closedW));
        // 卡片「本月工单」用：本月 order_date 区间（当月1号~当月最后一天；与列表同数据权限口径）
        QueryWrapper<WorkOrder> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "order_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        dataScopeResolver.applyIfLimited(monthW, null, "created_by");
        stats.put("monthOrders", workOrderService.count(monthW));
        return Result.success(stats);
    }

    // ===== 下推接口: 来源单 → 生产工单 =====

    /** 销售订单明细 → 生产工单 */
    @PostMapping("/workorders/from-sale-order/{saleOrderId}/{saleOrderItemId}")
    public Result<WorkOrder> convertFromSaleOrder(@PathVariable Long saleOrderId,
                                                   @PathVariable Long saleOrderItemId) {
        return Result.success(workOrderService.convertFromSaleOrder(saleOrderId, saleOrderItemId));
    }

    /** MRP计划明细 → 生产工单 */
    @PostMapping("/workorders/from-mrp/{mrpPlanItemId}")
    public Result<WorkOrder> convertFromMrp(@PathVariable Long mrpPlanItemId) {
        return Result.success(workOrderService.convertFromMrp(mrpPlanItemId));
    }

    // ===== 下推接口: 生产工单 → 下游单据 =====

    /** 生产工单 → 生产领料单 */
    @com.erp.common.bill.link.PushGuard(sourceType = "PRODUCTION_ORDER", targetType = "MATERIAL_REQUISITION")
    @PostMapping("/workorders/{id}/push-material-requisition")
    public Result<MaterialRequisition> pushMaterialRequisition(@PathVariable Long id) {
        return Result.success(workOrderService.pushToMaterialRequisition(id));
    }

    /** 生产工单 → 生产入库单 */
    @com.erp.common.bill.link.PushGuard(sourceType = "PRODUCTION_ORDER", targetType = "PRODUCTION_RECEIPT")
    @PostMapping("/workorders/{id}/push-production-receipt")
    public Result<ProductionReceipt> pushProductionReceipt(@PathVariable Long id) {
        return Result.success(workOrderService.pushToProductionReceipt(id));
    }
}
