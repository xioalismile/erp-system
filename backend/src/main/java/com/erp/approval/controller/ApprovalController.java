package com.erp.approval.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.approval.entity.*;
import com.erp.approval.service.ApprovalFlowService;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.common.Result;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.erp.report.custom.core.EntityMetadataRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 审批流程管理 + 运行时 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalFlowService flowService;
    private final ApprovalRuntimeService runtimeService;
    private final EntityMetadataRegistry entityMetadataRegistry;
    private final CustomFieldService customFieldService;

    private String currentUsername(HttpServletRequest req) {
        Object u = req.getAttribute("currentUsername");
        return u != null ? u.toString() : "system";
    }

    private Long currentUserId(HttpServletRequest req) {
        Object u = req.getAttribute("currentUserId");
        if (u == null) return null;
        try { return Long.parseLong(u.toString()); } catch (Exception e) { return null; }
    }

    // ============ 流程管理 ============
    @GetMapping("/flows")
    public Result<List<ApprovalFlow>> listFlows(
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String keyword) {
        return Result.success(flowService.listFlows(billType, keyword));
    }

    @GetMapping("/flows/{id}")
    public Result<ApprovalFlow> getFlow(@PathVariable Long id) {
        return Result.success(flowService.getFlowDetail(id));
    }

    @PostMapping("/flows")
    public Result<Void> saveFlow(@RequestBody ApprovalFlow flow, HttpServletRequest req) {
        flow.setCreateBy(currentUsername(req));
        flowService.saveFlow(flow);
        return Result.success();
    }

    @PutMapping("/flows/{id}")
    public Result<Void> updateFlow(@PathVariable Long id, @RequestBody ApprovalFlow flow, HttpServletRequest req) {
        flow.setFlowId(id);
        flow.setUpdateBy(currentUsername(req));
        flowService.updateFlow(flow);
        return Result.success();
    }

    @DeleteMapping("/flows/{id}")
    public Result<Void> deleteFlow(@PathVariable Long id) {
        flowService.deleteFlow(id);
        return Result.success();
    }

    @PutMapping("/flows/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam Short enabled) {
        flowService.setEnabled(id, enabled);
        return Result.success();
    }

    // ============ 条件模板 ============
    @GetMapping("/flows/{id}/conditions")
    public Result<List<ApprovalCondition>> getConditions(@PathVariable Long id) {
        return Result.success(flowService.getConditions(id));
    }

    @PostMapping("/flows/{id}/conditions")
    public Result<Void> saveConditions(@PathVariable Long id, @RequestBody List<ApprovalCondition> conditions) {
        flowService.saveConditions(id, conditions);
        return Result.success();
    }

    // ============ 单据级审批开关 ============
    @GetMapping("/bill-enabled/{billType}")
    public Result<Boolean> isBillApprovalEnabled(@PathVariable String billType) {
        return Result.success(runtimeService.isBillApprovalEnabled(billType));
    }

    @PutMapping("/bill-enabled/{billType}")
    public Result<Void> setBillApprovalEnabled(@PathVariable String billType, @RequestParam boolean enabled, HttpServletRequest req) {
        runtimeService.updateBillApprovalSwitch(billType, enabled, currentUsername(req));
        return Result.success();
    }

    @GetMapping("/bill-types")
    public Result<List<Map<String, Object>>> listBillTypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] billTypes = {
                // 销售管理 (7种)
                {"SALE_QUOTE", "销售报价单"}, {"SALE_ORDER", "销售订单"}, {"SALE_CONTRACT", "销售合同"},
                {"SALE_DELIVERY", "销售出库单"}, {"SALE_RETURN", "销售退货"}, {"SALE_INVOICE", "销售发票"},
                {"SALE_EXCHANGE", "销售换货单"},
                {"SALE_REPAIR", "售后维修单"},
                // 采购管理 (6种)
                {"PURCHASE_REQUEST", "采购申请单"}, {"PURCHASE_ORDER", "采购订单"}, {"PURCHASE_CONTRACT", "采购合同"},
                {"PURCHASE_INBOUND", "采购入库单"}, {"PURCHASE_RETURN", "采购退货单"}, {"PURCHASE_INVOICE", "采购发票"},
                // 生产管理 (7种)
                {"PRODUCTION_ORDER", "生产工单"}, {"PRODUCTION_RECEIPT", "生产入库单"}, {"MATERIAL_REQUISITION", "生产领料单"},
                {"PRODUCE_ASSEMBLE", "生产组装单"}, {"PRODUCE_DISASSEMBLE", "生产拆卸单"},
                {"MATERIAL_RETURN", "生产退料单"}, {"MATERIAL_SUPPLEMENT", "生产补料单"},
                // 委外管理 (5种)
                {"SUBCONTRACT_ORDER", "委外加工单"}, {"SUBCONTRACT_DELIVERY", "委外发料单"}, {"SUBCONTRACT_RECEIPT", "委外入库单"},
                {"SUBCONTRACT_RETURN", "委外退料单"}, {"SUBCONTRACT_SUPPLEMENT", "委外补料单"},
                // 打样/样品 (2种)
                {"SAMPLE_ORDER", "打样单"}, {"FREE_SAMPLE", "免费样品订单"},
                // 库存管理 (4种)
                {"STOCK_TRANSFER", "库存调拨单"}, {"STOCK_CHECK", "库存盘点单"},
                {"OTHER_RECEIPT", "其他入库单"}, {"OTHER_ISSUE", "其他出库单"},
                // 财务管理 (7种)
                {"PURCHASE_EXPENSE", "采购费用单"}, {"PURCHASE_PRE_PAYMENT", "预付款单"},
                {"PAYMENT_RECEIPT", "收款单"}, {"PAYMENT_VOUCHER", "付款单"},
                {"OPERATION_EXPENSE", "费用分摊"},
                {"SALE_PRE_RECEIPT", "销售预收款单"}, {"WAGE_SETTLEMENT", "工序工资核算"},
                // 其他单据 (2种)
                {"BOM", "BOM"},
                {"CUSTOMER_CREDIT", "客户授信（月结一次性审批）"}
        };
        for (String[] t : billTypes) {
            Map<String, Object> m = new HashMap<>();
            m.put("billType", t[0]);
            m.put("billName", t[1]);
            m.put("enabled", runtimeService.isBillApprovalEnabled(t[0]));
            list.add(m);
        }
        return Result.success(list);
    }

    // ============ 条件字段元数据（审批流程条件配置用） ============
    /**
     * 获取单据可用于审批条件的字段列表（表头字段 + 自定义字段）。
     * billType（如 SALE_ORDER）小写后既是 EntityMetadataRegistry 数据源 key（表名），
     * 也是自定义字段 entity_type，两者口径一致。
     */
    @GetMapping("/bill-types/{billType}/condition-fields")
    public Result<List<Map<String, Object>>> getConditionFields(@PathVariable String billType) {
        String dsKey = billType.toLowerCase();
        List<Map<String, Object>> fields = new ArrayList<>();
        // 1) 表头字段（来自自定义报表元数据注册表，含中文名与类型）
        EntityMetadataRegistry.DataSourceMeta meta = entityMetadataRegistry.getDataSource(dsKey);
        if (meta != null) {
            for (EntityMetadataRegistry.FieldMeta fm : meta.fields.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fieldKey", fm.field);
                m.put("label", fm.label);
                m.put("type", fm.type);
                m.put("custom", false);
                fields.add(m);
            }
        }
        // 2) 自定义字段（含 select 类型的选项，供条件值下拉）
        try {
            for (CustomField cf : customFieldService.listEnabledByEntity(dsKey)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fieldKey", cf.getFieldKey());
                m.put("label", cf.getFieldLabel());
                m.put("type", mapCustomFieldType(cf.getFieldType()));
                m.put("custom", true);
                if (cf.getFieldOptions() != null && !cf.getFieldOptions().isBlank()) {
                    m.put("options", parseFieldOptions(cf.getFieldOptions()));
                }
                fields.add(m);
            }
        } catch (Exception e) {
            log.warn("加载自定义字段元数据失败: billType={}, err={}", billType, e.getMessage());
        }
        // 3) 主数据透传虚拟字段：客户付款管控分类（存于客户档案，不在单据表内，提交时由单据服务填入 billData）
        if ("SALE_CONTRACT".equals(billType)) {
            Map<String, Object> payCat = new LinkedHashMap<>();
            payCat.put("fieldKey", "paymentCategory");
            payCat.put("label", "客户付款管控分类");
            payCat.put("type", "select");
            payCat.put("custom", false);
            payCat.put("options", List.of(
                    Map.of("value", "现结", "label", "现结"),
                    Map.of("value", "月结", "label", "月结")));
            fields.add(payCat);
        }
        // 客户授信(CUSTOMER_CREDIT)：billId=customerId，billData=客户档案快照，开放少量虚拟字段供条件路由
        if ("CUSTOMER_CREDIT".equals(billType)) {
            Map<String, Object> lv = new LinkedHashMap<>();
            lv.put("fieldKey", "customerLevel");
            lv.put("label", "客户等级");
            lv.put("type", "string");
            lv.put("custom", false);
            fields.add(lv);
            Map<String, Object> cl = new LinkedHashMap<>();
            cl.put("fieldKey", "creditLimit");
            cl.put("label", "信用额度");
            cl.put("type", "number");
            cl.put("custom", false);
            fields.add(cl);
            Map<String, Object> pc = new LinkedHashMap<>();
            pc.put("fieldKey", "paymentCategory");
            pc.put("label", "付款管控分类");
            pc.put("type", "select");
            pc.put("custom", false);
            pc.put("options", List.of(
                    Map.of("value", "现结", "label", "现结"),
                    Map.of("value", "月结", "label", "月结")));
            fields.add(pc);
        }
        return Result.success(fields);
    }

    /** 自定义字段类型 → 条件匹配类型（text/number/select/date） */
    private String mapCustomFieldType(String fieldType) {
        if (fieldType == null) return "string";
        return switch (fieldType) {
            case "number", "formula", "amount" -> "number";
            case "select" -> "select";
            case "date" -> "date";
            default -> "string";
        };
    }

    /** 解析字段选项 JSON [{"label":"x","value":"y"}] 为列表 */
    private List<Map<String, String>> parseFieldOptions(String optionsJson) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(optionsJson, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ============ 运行时任务 ============
    @GetMapping("/tasks/bill/{billType}/{billId}")
    public Result<List<ApprovalTask>> getBillTasks(@PathVariable String billType, @PathVariable Long billId) {
        return Result.success(runtimeService.getBillTasks(billType, billId));
    }

    @GetMapping("/tasks/my-pending")
    public Result<List<ApprovalTask>> getMyPendingTasks(HttpServletRequest req) {
        return Result.success(runtimeService.getMyPendingTasks(currentUsername(req)));
    }

    @PostMapping("/tasks/{taskId}/approve")
    public Result<ApprovalTask> approve(@PathVariable Long taskId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        return Result.success(runtimeService.approve(taskId, currentUsername(req), body.get("comment")));
    }

    @PostMapping("/tasks/{taskId}/reject")
    public Result<ApprovalTask> reject(@PathVariable Long taskId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        return Result.success(runtimeService.reject(taskId, currentUsername(req), body.get("comment")));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    public Result<ApprovalTask> transfer(@PathVariable Long taskId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        return Result.success(runtimeService.transfer(taskId, currentUsername(req), body.get("toApprover"), body.get("comment")));
    }

    @PostMapping("/tasks/{taskId}/select-approver")
    public Result<ApprovalTask> selectApprover(@PathVariable Long taskId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        return Result.success(runtimeService.selectApprover(taskId, currentUsername(req), body.get("approver")));
    }

    // ============ 审批历史 ============
    @GetMapping("/logs/bill/{billType}/{billId}")
    public Result<List<ApprovalLog>> getBillHistory(@PathVariable String billType, @PathVariable Long billId) {
        return Result.success(flowService.getBillApprovalHistory(billType, billId));
    }

    // ============ 通知 ============
    @GetMapping("/notifications")
    public Result<List<ApprovalNotification>> getNotifications(
            @RequestParam(required = false) Short isRead,
            HttpServletRequest req) {
        return Result.success(runtimeService.getNotifications(currentUserId(req), isRead));
    }

    @PutMapping("/notifications/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        runtimeService.markRead(id);
        return Result.success();
    }

    @GetMapping("/notifications/unread-count")
    public Result<Integer> getUnreadCount(HttpServletRequest req) {
        return Result.success(runtimeService.getUnreadCount(currentUserId(req)));
    }
}
