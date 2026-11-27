package com.erp.ai.tool.impl;

import com.erp.ai.bill.BillActionPermissionResolver;
import com.erp.ai.bill.BillActionRegistry;
import com.erp.ai.bill.BillActionRegistry.ActionSpec;
import com.erp.ai.bill.BillCodeResolver;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 写工具：业务单据**批量**流程动作（提交 / 审核 / 删除 / 重置草稿）。
 *
 * <p>背景（2026-09-30 批次6）：系统列表页已有统一的"批量操作条"（批量提交/批量审批/批量删除/批量导出），
 * 而小助手此前只能一单单操作。本工具让用户可以说「把这几张采购订单提交」「把选中的草稿单据删掉」。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>逐张执行、逐张报结果</b>：成功 N 张 / 失败 M 张 + 每张失败原因，**绝不做整体回滚**；</li>
 *   <li><b>复用单张工具</b>：真正干活的调度（能力矩阵精确反射、审批引擎优先、原因必填校验、审计日志）
 *       直接调用 {@link BillActionTool#executeOne}，不复制一份，避免两处口径漂移；</li>
 *   <li><b>权限逐张校验</b>：统一走 {@link BillActionPermissionResolver}，无权限的逐张拒绝并说明；</li>
 *   <li><b>数量上限 50</b>：超过则要求用户缩小范围；</li>
 *   <li><b>动作范围 = 界面批量条口径</b>：只放界面真实存在的四个语义，不放开其它动作。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillBatchActionTool implements AiTool {

    /** 单次批量上限（超过则要求用户缩小范围） */
    private static final int MAX_BATCH = 50;

    /** 允许的批量动作（= 界面批量条里真实存在的四种语义，不放开其它动作） */
    private static final List<String> BATCH_ACTIONS = List.of("submit", "approve", "delete", "reverseApprove");

    private final BillActionRegistry billActionRegistry;
    private final BillActionPermissionResolver permissionResolver;
    private final BillActionTool billActionTool;
    private final BillCodeResolver billCodeResolver;

    @Override
    public String getName() {
        return "bill_batch_action";
    }

    @Override
    public String getDescription() {
        return "对**多张**同类业务单据批量执行同一流程动作（一次最多 50 张）。"
                + "支持动作：submit 批量提交 / approve 批量审核 / delete 批量删除 / reverseApprove 批量重置草稿。"
                + "参数：billType（单据类型）、action（上面四种）、ids（单据主键数组，先用对应 query_ 工具查到每张单的主键）、"
                + "reason（可选）。"
                + "**逐张执行、逐张返回结果**（成功几张、失败几张、每张失败原因），不会因个别失败而整体回滚；"
                + "权限逐张校验，无权限的逐张拒绝并说明。"
                + "执行前会弹确认卡片（写明单据类型、动作、张数、单号清单），需用户确认后才会真正执行。"
                + "billType 取值：" + billActionRegistry.describableList() + "。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("billType", Map.of("type", "string",
                "description", "单据类型",
                "enum", new ArrayList<>(billActionRegistry.allBillTypes())));
        props.put("action", Map.of("type", "string",
                "description", "批量动作：submit 提交 / approve 审核 / delete 删除 / reverseApprove 重置草稿",
                "enum", new ArrayList<>(BATCH_ACTIONS)));
        props.put("ids", Map.of("type", "array",
                "description", "单据主键数组（先用对应 query_ 工具查到每张单的主键后填入；禁止把主键说给用户听）",
                "items", Map.of("type", "integer")));
        props.put("reason", Map.of("type", "string",
                "description", "可选。批量动作的原因说明"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("billType", "action", "ids"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String billType = SettingSupport.str(params == null ? null : params.get("billType"));
            String action = SettingSupport.str(params == null ? null : params.get("action"));
            String reason = SettingSupport.str(params == null ? null : params.get("reason"));
            List<Long> ids = parseIds(params == null ? null : params.get("ids"));

            if (billType == null || action == null) {
                return fail("参数缺失：需要 billType、action、ids");
            }
            if (!billActionRegistry.allBillTypes().contains(billType)) {
                return fail("不支持的单据类型: " + billType + "。可用单据类型: " + billActionRegistry.describableList());
            }
            String label = billActionRegistry.label(billType);
            if (!BATCH_ACTIONS.contains(action)) {
                return fail("批量操作只支持「提交 / 审核 / 删除 / 重置草稿」四种动作；不支持『"
                        + BillActionRegistry.ACTION_LABELS.getOrDefault(action, action) + "』。");
            }
            String actionLabel = BillActionRegistry.ACTION_LABELS.getOrDefault(action, action);
            if (ids.isEmpty()) {
                return fail("请先提供要批量操作的「" + label + "」单据（ids 为空）。");
            }
            if (ids.size() > MAX_BATCH) {
                return fail("单次批量最多 " + MAX_BATCH + " 张，本次提供了 " + ids.size()
                        + " 张，请缩小范围后分批执行。");
            }
            if (billActionRegistry.actionSpec(billType, action) == null) {
                return fail("【" + label + "】不支持『" + actionLabel + "』。该单据支持的动作："
                        + supportedActionText(billType) + "。");
            }

            int successCount = 0;
            int failCount = 0;
            StringBuilder detail = new StringBuilder();
            for (Long id : ids) {
                if (id == null) continue;
                String billRef = billCodeResolver.describe(billType, id, billLabelStyle(scope));
                // 权限逐张校验：无权限的逐张拒绝并说明原因
                String deny = permissionResolver.check(billType, action, scope);
                if (deny != null) {
                    failCount++;
                    detail.append("\n· ").append(billRef).append(" 失败：").append(deny);
                    continue;
                }
                Map<String, Object> one = billActionTool.executeOne(billType, id, action, reason, scope);
                if (Boolean.TRUE.equals(one.get("success"))) {
                    successCount++;
                    detail.append("\n· ").append(billRef).append(" 成功");
                } else {
                    failCount++;
                    detail.append("\n· ").append(billRef).append(" 失败：")
                            .append(one.get("message") == null ? "未知原因" : one.get("message"));
                }
            }
            String message = "批量「" + actionLabel + "」【" + label + "】完成：成功 " + successCount
                    + " 张，失败 " + failCount + " 张。" + detail;
            log.info("[AI批量单据动作] 用户{} 单据{} 动作{} 成功{} 失败{}",
                    scope != null ? scope.getUsername() : null, billType, action, successCount, failCount);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", successCount > 0);
            result.put("message", message);
            result.put("total", successCount + failCount);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            return result;
        } catch (Exception e) {
            log.error("[AI批量单据动作] 执行异常", e);
            return fail("执行失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 解析 ids 参数：兼容数组/数字/字符串数字；非法元素跳过 */
    private List<Long> parseIds(Object raw) {
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                Long v = SettingSupport.longVal(o);
                if (v != null) ids.add(v);
            }
        } else if (raw != null) {
            Long v = SettingSupport.longVal(raw);
            if (v != null) ids.add(v);
        }
        return ids;
    }

    /** 该单据支持的动作中文清单（保持登记顺序） */
    private String supportedActionText(String billType) {
        return billActionRegistry.actions(billType).values().stream()
                .map(ActionSpec::label)
                .collect(Collectors.joining("、"));
    }

    /** 用户在小助手设置里选定的单据标识方式；未选/非法时按默认"单号"（scope 可能为 null） */
    private String billLabelStyle(AiDataScope scope) {
        if (scope == null || scope.getDisplayPrefs() == null) return BillCodeResolver.STYLE_CODE;
        return BillCodeResolver.normalizeStyle(scope.getDisplayPrefs().get(BillCodeResolver.PREF_KEY_BILL_LABEL_STYLE));
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }
}
