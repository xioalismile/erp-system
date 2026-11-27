package com.erp.ai.tool.impl;

import com.erp.ai.bill.BillActionPermissionResolver;
import com.erp.ai.bill.BillActionRegistry;
import com.erp.ai.bill.BillActionRegistry.ActionSpec;
import com.erp.ai.bill.BillActionRegistry.PushTarget;
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

/**
 * 只读工具：单据能力查询（每个单据支持的动作 + 可下推目标）。
 *
 * <p>用途：解决"AI 不知道每张单支持哪些动作、只能盲试"的问题——先查询再执行。
 * 只读，不消耗写开关；权限仅走 {@link BillActionPermissionResolver#checkList}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillActionListTool implements AiTool {

    private final BillActionRegistry billActionRegistry;
    private final BillActionPermissionResolver permissionResolver;

    @Override
    public String getName() {
        return "bill_action_list";
    }

    @Override
    public String getDescription() {
        return "【只读】查询业务单据支持的动作与可下推目标。"
                + "传入 billType 返回该单据的：中文名、支持的动作清单（含动作中文名、是否必须提供 reason）、可下推目标；"
                + "不传 billType 返回全部单据的简要清单（动作数量/可下推数量）。"
                + "在执行 bill_action 或 bill_pushdown 前，若不确定该单据支持什么，先调用本工具查询。"
                + "billType 取值：" + billActionRegistry.describableList() + "。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("billType", Map.of("type", "string",
                "description", "可选。单据类型；不传则返回全部单据的简要清单",
                "enum", new ArrayList<>(billActionRegistry.allBillTypes())));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of());
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
            if (billType != null) {
                if (!billActionRegistry.allBillTypes().contains(billType)) {
                    return fail("不支持的单据类型: " + billType
                            + "。可用单据类型: " + billActionRegistry.describableList());
                }
                String deny = permissionResolver.checkList(scope, List.of(billType));
                if (deny != null) {
                    return fail(deny);
                }
                return Map.of("success", true, "message", describeOne(billType));
            }

            String deny = permissionResolver.checkList(scope, billActionRegistry.allBillTypes());
            if (deny != null) {
                return fail(deny);
            }
            return Map.of("success", true, "message", describeAll());
        } catch (Exception e) {
            log.error("[AI单据动作清单] 执行异常", e);
            return fail("查询失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 单张单据：中文名 + 支持动作（含是否必填 reason）+ 可下推目标 */
    private String describeOne(String billType) {
        StringBuilder sb = new StringBuilder("【").append(billActionRegistry.label(billType)).append("】\n");
        LinkedHashMap<String, ActionSpec> actions = billActionRegistry.actions(billType);
        sb.append("支持的动作（").append(actions.size()).append("）：\n");
        for (Map.Entry<String, ActionSpec> e : actions.entrySet()) {
            sb.append("- ").append(e.getKey()).append("（").append(e.getValue().label());
            if (reasonRequired(e.getKey(), e.getValue())) {
                sb.append("，必须提供 reason");
            }
            sb.append("）\n");
        }
        List<PushTarget> targets = billActionRegistry.pushTargets(billType);
        if (targets.isEmpty()) {
            sb.append("可下推目标：无");
        } else {
            sb.append("可下推目标：");
            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(targets.get(i).targetType()).append("（").append(targets.get(i).targetLabel()).append("）");
            }
        }
        return sb.toString();
    }

    /** 全部单据简要清单 */
    private String describeAll() {
        StringBuilder sb = new StringBuilder("共 ").append(billActionRegistry.allBillTypes().size())
                .append(" 种单据（动作数量，可下推数量）：\n");
        for (String billType : billActionRegistry.allBillTypes()) {
            sb.append("- ").append(billType).append(" ").append(billActionRegistry.label(billType))
                    .append("：").append(billActionRegistry.actions(billType).size()).append(" 个动作，")
                    .append(billActionRegistry.pushTargets(billType).size()).append(" 个可下推目标\n");
        }
        return sb.toString();
    }

    private boolean reasonRequired(String action, ActionSpec spec) {
        if (!("reject".equals(action) || "close".equals(action) || "void".equals(action))) {
            return false;
        }
        Class<?>[] types = spec.paramTypes();
        return types.length == 3 && types[1] == String.class && types[2] == String.class;
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }
}
