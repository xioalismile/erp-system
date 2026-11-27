package com.erp.produce.assemble.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.produce.assemble.entity.ProduceAssemble;
import com.erp.produce.assemble.service.ProduceAssembleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 生产组装单Controller
 */
@RestController
@RequestMapping("/api/produce/assemble")
@RequiredArgsConstructor
public class ProduceAssembleController {

    private final ProduceAssembleService produceAssembleService;

    /** 统一数据权限解析件（按角色 sys_role.data_scope 过滤，禁止各页面自拼） */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    @org.springframework.beans.factory.annotation.Autowired
    private BillCascadeDeleteService billCascadeDeleteService;

    /**
     * 分页查询
     * 统一入参：current/page/size + keyword/status/startDate/endDate
     * 旧入参 serial_no/product_name/dateline_start/dateline_end 全部保留（旧链接与其它页面仍在传）
     */
    @GetMapping
    public Result<PageResult<ProduceAssemble>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            // 状态收字符串（中文文案或数字串），中文→数字码由服务层转换（2026-10-14 筛选条件自定义）
            @RequestParam(required = false) String status,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(value = "serial_no", required = false) String serialNo,
            @RequestParam(value = "product_name", required = false) String productName,
            @RequestParam(value = "dateline_start", required = false) String datelineStart,
            @RequestParam(value = "dateline_end", required = false) String datelineEnd,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(produceAssembleService.pageQuery(p[0], p[1], keyword, status, sortBy, sortOrder,
                serialNo, productName, datelineStart, datelineEnd, customSearch, startDate, endDate, headerFilters));
    }

    /**
     * 仪表盘统计（与列表同数据权限口径：按 creator 过滤；「本月」= 当月1号 ~ 当月最后一天）
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        // 数据权限与列表逐字相同（统一数据权限：按角色 data_scope 过滤 creator 列）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProduceAssemble> draftW =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProduceAssemble>().eq("status", 0);
        dataScopeResolver.applyIfLimited(draftW, null, "creator");
        long draftAssembles = produceAssembleService.count(draftW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProduceAssemble> pendingW =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProduceAssemble>().eq("status", 1);
        dataScopeResolver.applyIfLimited(pendingW, null, "creator");
        long pendingAssembles = produceAssembleService.count(pendingW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProduceAssemble> approvedW =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProduceAssemble>().eq("status", 2);
        dataScopeResolver.applyIfLimited(approvedW, null, "creator");
        long approvedAssembles = produceAssembleService.count(approvedW);
        // 本月组装：dateline 是 LocalDateTime → 起始 >= 当月1号0点（统一件），结束 < 次月1号0点
        java.time.LocalDate monthStart = java.time.LocalDate.now().withDayOfMonth(1);
        java.time.LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProduceAssemble> monthW =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "dateline", monthStart.toString(), null);
        monthW.lt("dateline", monthEnd.plusDays(1).atStartOfDay());
        dataScopeResolver.applyIfLimited(monthW, null, "creator");
        long monthAssembles = produceAssembleService.count(monthW);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthAssembles", monthAssembles);
        data.put("draftAssembles", draftAssembles);
        data.put("pendingAssembles", pendingAssembles);
        data.put("approvedAssembles", approvedAssembles);
        return Result.success(data);
    }

    /**
     * 获取详情
     */
    @GetMapping("/{id}")
    public Result<ProduceAssemble> detail(@PathVariable Long id) {
        return Result.success(produceAssembleService.getDetail(id));
    }

    /**
     * 新建组装单
     */
    @PostMapping
    public Result<ProduceAssemble> create(@RequestBody ProduceAssemble assemble, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) {
            assemble.setCreator(username);
            assemble.setModifier(username);
        }
        produceAssembleService.saveWithDetails(assemble);
        return Result.success(assemble);
    }

    /**
     * 更新组装单
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProduceAssemble assemble, HttpServletRequest req) {
        assemble.setId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) {
            assemble.setModifier(username);
        }
        produceAssembleService.saveWithDetails(assemble);
        return Result.success();
    }

    /**
     * 提交审批: 草稿 → 待审批
     */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        produceAssembleService.submit(id);
        return Result.success();
    }

    /**
     * 审批通过: 待审批 → 生效
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        String approver = (String) req.getAttribute("currentUsername");
        produceAssembleService.approve(id, approver);
        return Result.success();
    }

    /**
     * 驳回: 待审批 → 草稿
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        produceAssembleService.reject(id, body.get("reason"));
        return Result.success();
    }

    /**
     * 撤销: 待审批 → 草稿
     */
    @PostMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        produceAssembleService.revoke(id);
        return Result.success();
    }

    /**
     * 作废: 草稿/待审批 → 作废
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        produceAssembleService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /**
     * 重置草稿: 生效 → 草稿 (逆向库存操作)
     */
    @PostMapping("/{id}/reset")
    public Result<Void> resetToDraft(@PathVariable Long id) {
        produceAssembleService.resetToDraft(id);
        return Result.success();
    }

    /**
     * 删除组装单 (仅草稿族可删除)
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 整单删除统一走状态机：草稿校验 + 下游校验 + 明细/BOM行级联删除
        produceAssembleService.deleteBill(id);
        return Result.success();
    }

    /**
     * 生成单据编号
     */
    @GetMapping("/serial-no")
    public Result<String> generateSerialNo() {
        return Result.success(produceAssembleService.generateSerialNo());
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        produceAssembleService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        produceAssembleService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        produceAssembleService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 生效 → close_status=1（status 保持生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        produceAssembleService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        produceAssembleService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 复制单据: 生成新草稿并返回新单(含新id) */
    @PostMapping("/{id}/copy")
    public Result<ProduceAssemble> copy(@PathVariable Long id) {
        return Result.success(produceAssembleService.copyBill(id));
    }

}