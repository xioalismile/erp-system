package com.erp.ai.tool.impl;

import com.erp.ai.bill.BillActionPermissionResolver;
import com.erp.ai.bill.BillActionRegistry;
import com.erp.ai.bill.BillActionRegistry.PushTarget;
import com.erp.ai.bill.BillCodeResolver;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 写工具：单据下推（源单 → 目标单草稿）。
 *
 * <p>仅调用源单 Service 的下推方法（不调用任何 Controller），按能力矩阵
 * {@link PushTarget#paramTypes()} 精确反射。权限判定走 {@link BillActionPermissionResolver}
 * 的 action={@code pushTo} 口径。
 *
 * <p>对"后端无生成接口"的 13 条关系返回界面选单引导；对未登记组合返回该源单可下推目标清单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillPushDownTool implements AiTool {

    private final BillActionRegistry billActionRegistry;
    private final BillActionPermissionResolver permissionResolver;
    private final BillCodeResolver billCodeResolver;

    @Override
    public String getName() {
        return "bill_pushdown";
    }

    @Override
    public String getDescription() {
        return "把一张已生效的源单下推生成下游单据草稿（如 销售订单→销售出库单、采购订单→采购入库单、"
                + "生产工单→生产领料单/生产入库单、委外订单→委外发料单、打样单→BOM 等）。"
                + "执行前会弹确认卡片。使用前必须先用对应的 query_ 工具查到源单主键 sourceId。"
                + "targetType 必须是该源单支持的目标（可先调用 bill_action_list 查询）；"
                + "系统对部分组合当前仅支持界面选单预填（后端无生成接口），会明确提示。"
                + "有下推能力的源单类型：" + pushSourceText() + "。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("sourceType", Map.of("type", "string",
                "description", "源单类型",
                "enum", new ArrayList<>(billActionRegistry.pushSourceTypes())));
        props.put("sourceId", Map.of("type", "integer",
                "description", "源单主键ID（内部编号，先用对应 query_ 工具查到主键后再执行；禁止把该ID说给用户听）"));
        props.put("targetType", Map.of("type", "string",
                "description", "目标单据类型，必须是该源单支持的目标（可先调用 bill_action_list 查询）"));
        props.put("reason", Map.of("type", "string",
                "description", "可选。下推备注（当前后端下推接口未接收该参数，暂不消费）"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("sourceType", "sourceId", "targetType"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String operator = scope != null ? scope.getUsername() : null;
        try {
            String sourceType = SettingSupport.str(params == null ? null : params.get("sourceType"));
            Long sourceId = SettingSupport.longVal(params == null ? null : params.get("sourceId"));
            String targetType = SettingSupport.str(params == null ? null : params.get("targetType"));

            if (sourceType == null || sourceId == null || targetType == null) {
                return fail("参数缺失：需要 sourceType、sourceId、targetType");
            }
            if (!billActionRegistry.pushSourceTypes().contains(sourceType)) {
                return fail("不支持的单据类型: " + sourceType + "。可下推的源单类型：" + pushSourceText());
            }

            PushTarget target = billActionRegistry.pushTargets(sourceType).stream()
                    .filter(t -> t.targetType().equals(targetType))
                    .findFirst().orElse(null);
            if (target == null) {
                return fail(unsupportedCombination(sourceType, targetType));
            }

            String deny = permissionResolver.check(sourceType, "pushTo", scope);
            if (deny != null) {
                return fail(deny);
            }

            Object service = billActionRegistry.service(sourceType);
            InvocationOutcome outcome = dispatch(service, target, sourceId, operator);
            if (!outcome.found) {
                log.error("[AI单据下推] 能力矩阵登记错误：sourceType={} targetType={} method={} paramTypes={} 未在 Service 上找到",
                        sourceType, targetType, target.method(), Arrays.toString(target.paramTypes()));
                return fail("系统内部下推映射异常，请联系管理员");
            }
            if (outcome.error != null) {
                String msg = outcome.error.getMessage() != null && !outcome.error.getMessage().isBlank()
                        ? outcome.error.getMessage() : outcome.error.getClass().getSimpleName();
                log.error("[AI单据下推] 用户{} 源单{} id={} → 目标{} 失败: {}",
                        operator, sourceType, sourceId, targetType, msg);
                return fail("下推失败: " + msg);
            }

            String sourceRef = billCodeResolver.describe(sourceType, sourceId, billLabelStyle(scope));
            String message = "已由" + sourceRef + " 下推生成【" + target.targetLabel()
                    + "】" + billNoOf(outcome.value) + "（草稿）";
            log.info("[AI单据下推] 用户{} 源单{} id={} → 目标{}", operator, sourceType, sourceId, targetType);
            return Map.of("success", true, "message", message);
        } catch (Exception e) {
            log.error("[AI单据下推] 执行异常", e);
            return fail("下推失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 不支持组合：区分"后端无接口（界面选单）"与"完全未登记" */
    private String unsupportedCombination(String sourceType, String targetType) {
        String srcLabel = billActionRegistry.label(sourceType);
        String tgtLabel = billActionRegistry.label(targetType);
        if (billActionRegistry.isKnownNoBackendRelation(sourceType, targetType)) {
            return "系统当前对『" + srcLabel + "→" + tgtLabel + "』采用界面选单预填方式（后端无生成接口），"
                    + "请到「" + billActionRegistry.guidePath(sourceType, targetType) + "」用【选单】新建。"
                    + "如需我直接生成，请让管理员先开发该链路。";
        }
        return "不支持的下推组合：" + sourceType + " → " + targetType + "。该单据可下推的目标：" + targetText(sourceType) + "。";
    }

    private String targetText(String sourceType) {
        List<PushTarget> targets = billActionRegistry.pushTargets(sourceType);
        if (targets.isEmpty()) {
            return "无";
        }
        return targets.stream()
                .map(t -> t.targetType() + "（" + t.targetLabel() + "）")
                .collect(Collectors.joining("、"));
    }

    private String pushSourceText() {
        return billActionRegistry.pushSourceTypes().stream()
                .map(t -> t + "（" + billActionRegistry.label(t) + "）")
                .collect(Collectors.joining("，"));
    }

    /** 按下推矩阵登记的签名精确反射调用；args = [sourceId, operator] */
    private InvocationOutcome dispatch(Object service, PushTarget target, Long sourceId, String operator) {
        if (service == null) {
            return new InvocationOutcome(false, null, null);
        }
        Class<?>[] types = target.paramTypes();
        Object[] args = new Object[types.length];
        args[0] = sourceId;
        if (types.length > 1) args[1] = operator;

        Method method = null;
        for (Method m : service.getClass().getMethods()) {
            if (!m.getName().equals(target.method())) continue;
            if (!Arrays.equals(m.getParameterTypes(), types)) continue;
            method = m;
            break;
        }
        if (method == null) {
            return new InvocationOutcome(false, null, null);
        }
        try {
            return new InvocationOutcome(true, method.invoke(service, args), null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException() != null ? e.getTargetException() : e;
            return new InvocationOutcome(true, null, cause);
        } catch (Exception e) {
            return new InvocationOutcome(true, null, e);
        }
    }

    /** 取返回的下游单据号（无则退回主键ID） */
    private String billNoOf(Object value) {
        if (value == null) {
            return "（已生成）";
        }
        String[] getters = {
                "getOrderNo", "getDeliveryNo", "getInvoiceNo", "getQuoteNo", "getContractNo", "getReturnNo",
                "getReceiptNo", "getInboundNo", "getVoucherNo", "getRequisitionNo", "getTransferNo",
                "getCheckNo", "getIssueNo", "getPickNo", "getReportNo", "getExpenseNo", "getPaymentNo",
                "getBomCode", "getSampleNo", "getCode", "getNo"
        };
        for (String g : getters) {
            try {
                Method m = value.getClass().getMethod(g);
                Object r = m.invoke(value);
                if (r != null && !String.valueOf(r).isBlank()) {
                    return String.valueOf(r);
                }
            } catch (Exception ignore) {
                // 该实体无此 getter，继续尝试下一个
            }
        }
        try {
            Method m = value.getClass().getMethod("getId");
            Object r = m.invoke(value);
            if (r != null) {
                return String.valueOf(r);
            }
        } catch (Exception ignore) {
            // 无 getId
        }
        return "（已生成）";
    }

    /** 用户在小助手设置里选定的单据标识方式；未选/非法时按默认"单号"（scope 可能为 null） */
    private String billLabelStyle(AiDataScope scope) {
        if (scope == null || scope.getDisplayPrefs() == null) return BillCodeResolver.STYLE_CODE;
        return BillCodeResolver.normalizeStyle(scope.getDisplayPrefs().get(BillCodeResolver.PREF_KEY_BILL_LABEL_STYLE));
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }

    private static final class InvocationOutcome {
        final boolean found;
        final Object value;
        final Throwable error;

        InvocationOutcome(boolean found, Object value, Throwable error) {
            this.found = found;
            this.value = value;
            this.error = error;
        }
    }
}
