package com.erp.ai.tool.impl;

import com.erp.ai.bill.BillActionPermissionResolver;
import com.erp.ai.bill.BillActionRegistry;
import com.erp.ai.bill.BillActionRegistry.ActionSpec;
import com.erp.ai.bill.BillActionRegistry.PushTarget;
import com.erp.ai.bill.BillCodeResolver;
import com.erp.ai.bill.BillTypeIdentityGuard;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.approval.entity.ApprovalTask;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.system.service.SysOperateLogService;
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
 * 写工具：业务单据流程动作（提交/审核/驳回/重置草稿/关闭/结案/反结案/作废/反作废/删除 + 各单据扩展动作）。
 *
 * <p>与旧实现的根本差异：动作调度**不再逐个试错候选方法名**，而是按
 * {@link BillActionRegistry} 能力矩阵登记的 {@code method + paramTypes} **精确反射**；
 * 不支持的动作会明确返回"该单据支持的动作清单 + 可下推目标"，而不是笼统失败。
 *
 * <p>审批类动作（approve/reject）优先走审批引擎（存在进行中审批任务时），否则回退到单据 Service。
 * 权限判定统一走 {@link BillActionPermissionResolver}（单一适配点）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillActionTool implements AiTool {

    private final BillActionRegistry billActionRegistry;
    private final BillActionPermissionResolver permissionResolver;
    private final BillCodeResolver billCodeResolver;
    private final BillTypeIdentityGuard billTypeIdentityGuard;
    private final ApprovalRuntimeService approvalRuntimeService;
    private final SysOperateLogService sysOperateLogService;

    @Override
    public String getName() {
        return "bill_action";
    }

    @Override
    public String getDescription() {
        return "对业务单据执行流程动作，如 submit 提交 / approve 审核 / reject 驳回 / reverseApprove 重置草稿 / "
                + "close 关闭 / closeOrder 结案 / reopen 反结案 / void 作废 / unvoid 反作废 / delete 删除，"
                + "以及部分单据的扩展动作（release 下达 / start 开工 / complete 完工 / startCheck 开始盘点 / "
                + "finishCheck 完成盘点 / issue 发料 / ship 发货 / allocateCost 费用分摊 / enable 启用 / "
                + "disable 停用 / setDefault 设为默认 / startSample 开始打样 等）。"
                + "执行前会弹确认卡片，需用户确认后才会真正执行。"
                + "使用前必须先用对应的 query_ 工具查到单据主键 billId。"
                + "**每个单据支持的动作不同**，建议先调用 bill_action_list 查询该单据支持哪些动作；"
                + "若执行了不支持的动作，系统会返回该单据支持的动作清单与可下推目标，请据此改调用，不要臆测。"
                + "驳回/关闭/作废通常必须提供 reason。approve/reject 在存在进行中审批任务时会优先走审批引擎。"
                + "billType 取值：" + billActionRegistry.describableList() + "。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("billType", Map.of("type", "string",
                "description", "单据类型",
                "enum", new ArrayList<>(billActionRegistry.allBillTypes())));
        props.put("billId", Map.of("type", "integer",
                "description", "单据主键ID（内部编号，先用对应 query_ 工具查到主键后再执行；禁止把该ID说给用户听）"));
        props.put("action", Map.of("type", "string",
                "description", "流程动作。每个单据支持的动作不同，建议先调用 bill_action_list 查询该单据支持的动作；"
                        + "驳回/关闭/作废通常需同时提供 reason",
                "enum", new ArrayList<>(BillActionRegistry.ACTION_LABELS.keySet())));
        props.put("reason", Map.of("type", "string",
                "description", "可选。驳回/关闭/作废等动作的原因说明"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("billType", "billId", "action"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String billType = SettingSupport.str(params == null ? null : params.get("billType"));
        Long billId = SettingSupport.longVal(params == null ? null : params.get("billId"));
        String action = SettingSupport.str(params == null ? null : params.get("action"));
        String reason = SettingSupport.str(params == null ? null : params.get("reason"));
        if (billType == null || billId == null || action == null) {
            return fail("参数缺失：需要 billType、billId、action");
        }
        return executeOne(billType, billId, action, reason, scope);
    }

    /**
     * 对**单张**单据执行一个流程动作（对外暴露，供 {@code bill_batch_action} 批量工具逐张复用）。
     *
     * <p>为什么单独抽出：批量工具必须与单张工具走**完全相同**的调度（能力矩阵精确反射、审批引擎优先、
     * 原因必填校验、审计日志），复制一份必然两处漂移。批量工具只负责循环 + 汇总，不重复这段逻辑。
     *
     * @return {@code {success, message}}；成功/失败的 message 均直接面向用户（含业务单号）
     */
    public Map<String, Object> executeOne(String billType, Long billId, String action, String reason, AiDataScope scope) {
        // 操作人取自服务端认证上下文，绝不接受 AI 传值
        String operator = scope != null ? scope.getUsername() : null;
        try {
            if (billType == null || billId == null || action == null) {
                return fail("参数缺失：需要 billType、billId、action");
            }
            if (!billActionRegistry.allBillTypes().contains(billType)) {
                return fail("不支持的单据类型: " + billType + "。可用单据类型: " + billActionRegistry.describableList());
            }
            String label = billActionRegistry.label(billType);
            String actionLabel = BillActionRegistry.ACTION_LABELS.getOrDefault(action, action);
            // 单据"人话标识"（单号/客户名，按用户在小助手设置里选的口径）：
            // 确认卡片、执行结果、语音播报、审计日志统一用它，不再把内部主键报给用户
            String billRef = billCodeResolver.describe(billType, billId, billLabelStyle(scope));

            // 1) 动作是否支持：不支持必须给出「支持的动作清单 + 可下推目标」
            ActionSpec spec = billActionRegistry.actionSpec(billType, action);
            if (spec == null) {
                return fail("【" + label + "】不支持『" + actionLabel + "』。该单据支持的动作："
                        + supportedActionText(billType) + "。可下推：" + pushTargetText(billType) + "。");
            }

            // 2) 权限（单一适配点）
            String deny = permissionResolver.check(billType, action, scope);
            if (deny != null) {
                return fail(deny);
            }

            // 2.1) 身份校验：一表多身份的单据（生产退料单 / 生产补料单）必须核对记录自身类型，
            // 权限按声明类型放行后，若不核对就可能作废/删除另一种单据
            String identityDeny = billTypeIdentityGuard.check(billType, billId);
            if (identityDeny != null) {
                return fail(identityDeny);
            }

            // 3) 审批类动作优先走审批引擎
            if ("approve".equals(action) || "reject".equals(action)) {
                Map<String, Object> engineResult = tryApprovalEngine(billType, billId, action, actionLabel, billRef, operator, reason);
                if (engineResult != null) {
                    if (Boolean.TRUE.equals(engineResult.get("success"))) {
                        writeAiAuditLog(billType, label, billId, actionLabel, operator);
                    }
                    return engineResult;
                }
            }

            // 4) 需要额外业务参数的动作（发货/核销/反核销）：当前对话无法采集，明确引导到界面
            if (needsExtraParams(spec.paramTypes())) {
                return fail("执行『" + actionLabel + "』需要额外业务参数（如快递单号/核销明细/金额），"
                        + "当前对话无法采集，请到【" + label + "】界面手动执行。");
            }

            // 5) 原因必填校验（reason 落在第 3 个参数的 reject/close/void）
            if (reasonRequired(action, spec.paramTypes()) && reason == null) {
                return fail("执行『" + actionLabel + "』必须提供原因 reason");
            }

            // 6) 精确反射调度
            Object service = billActionRegistry.service(billType);
            InvocationOutcome outcome = dispatch(service, spec, billId, operator, reason);
            if (!outcome.found) {
                log.error("[AI单据动作] 能力矩阵登记错误：billType={} action={} method={} paramTypes={} 未在 Service 上找到",
                        billType, action, spec.method(), Arrays.toString(spec.paramTypes()));
                return fail("系统内部动作映射异常，请联系管理员");
            }
            if (outcome.error != null) {
                String msg = messageOf(outcome.error);
                log.error("[AI单据动作] 用户{} 单据{} id={} 动作={} 失败: {}", operator, billType, billId, action, msg);
                return fail("执行『" + actionLabel + "』失败: " + msg);
            }

            log.info("[AI单据动作] 用户{} 单据{} id={} 动作={}", operator, billType, billId, action);
            writeAiAuditLog(billType, label, billId, actionLabel, operator);
            String message = "已对" + billRef + " 执行『" + actionLabel + "』";
            if (outcome.value != null && !(outcome.value instanceof Boolean)) {
                message = message + "，返回：" + outcome.value;
            }
            return Map.of("success", true, "message", message);
        } catch (Exception e) {
            log.error("[AI单据动作] 执行异常", e);
            return fail("执行失败: " + messageOf(e));
        }
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 审计标记：AI 代执行的写操作额外记一条操作日志，便于事后追溯（用户选定的"审计兜底"方案）。
     *
     * <p>为什么必须单独写：单据操作日志由 {@code BillOperateLogAspect} 在 HTTP Controller 层记录，
     * 而 AI 是**反射直接调用 Service**、不经过切面 → 若不补记，AI 代执行的操作在"操作日志"里完全查不到。
     *
     * <p>为什么不给 operator 加"（AI助手）"后缀：operator 在 {@code ApprovalRuntimeService.approve()}
     * 里被用于**审批人身份校验**（"您不是当前节点的指定审批人"），加后缀会让 AI 审批直接被拒。
     * 故 operator 保持原值，只在本日志的"姓名"与摘要上打标。
     *
     * <p>日志写失败**绝不影响**业务结果（业务此时已执行成功），仅告警。
     */
    private void writeAiAuditLog(String billType, String billTypeLabel, Long billId, String actionLabel, String operator) {
        try {
            // 单据编号列写业务单号（与 BillOperateLogAspect 的"单据编号"口径一致），
            // 解析不到单号时才退回 #内部ID —— 操作日志是给管理员追溯用的，必须能对上界面上的单号
            String billNo = billCodeResolver.auditBillNo(billType, billId);
            String summary = "【AI助手代执行】对单据 " + billNo + "（" + billTypeLabel + "）执行「" + actionLabel + "」，发起人：" + operator;
            sysOperateLogService.saveBillLog(
                    billTypeLabel,
                    billNo,
                    actionLabel,
                    summary,
                    operator,
                    operator + "（AI助手代执行）",
                    "AI小助手",
                    null);
        } catch (Exception e) {
            log.warn("[AI单据动作] 写审计日志失败（不影响业务）: {}", e.getMessage());
        }
    }

    /** 审批引擎分支：返回 null 表示无进行中审批任务，需回退到单据 Service */
    private Map<String, Object> tryApprovalEngine(String billType, Long billId, String action,
                                                  String actionLabel, String billRef, String operator, String reason) {
        ApprovalTask task = approvalRuntimeService.getRunningTask(billType, billId);
        if (task == null) {
            return null;
        }
        try {
            if ("approve".equals(action)) {
                approvalRuntimeService.approve(task.getTaskId(), operator, reason);
            } else {
                approvalRuntimeService.reject(task.getTaskId(), operator, reason);
            }
            log.info("[AI单据动作] 用户{} 审批引擎处理 单据{} id={} 动作={} taskId={}",
                    operator, billType, billId, action, task.getTaskId());
            return Map.of("success", true, "message",
                    "已对" + billRef + " 执行『" + actionLabel + "』（审批流程）");
        } catch (Exception e) {
            String msg = messageOf(e);
            log.error("[AI单据动作] 审批引擎执行失败 单据{} id={} 动作={}: {}", billType, billId, action, msg);
            return fail("执行『" + actionLabel + "』失败: " + msg);
        }
    }

    /** 该单据支持的动作中文清单（保持登记顺序） */
    private String supportedActionText(String billType) {
        return billActionRegistry.actions(billType).values().stream()
                .map(ActionSpec::label)
                .collect(Collectors.joining("、"));
    }

    /** 该单据可下推目标中文清单 */
    private String pushTargetText(String billType) {
        List<PushTarget> targets = billActionRegistry.pushTargets(billType);
        if (targets.isEmpty()) {
            return "无";
        }
        return targets.stream().map(PushTarget::targetLabel).distinct().collect(Collectors.joining("、"));
    }

    /** 是否是需要额外业务参数的动作（参数多于 3 个，或第 2/3 个参数不是操作人/原因这类 String） */
    private boolean needsExtraParams(Class<?>[] types) {
        if (types.length > 3) return true;
        for (int i = 1; i < types.length; i++) {
            if (types[i] != String.class) return true;
        }
        return false;
    }

    /** reject/close/void 且签名确含原因参数（(Long,String,String)）时 reason 必填 */
    private boolean reasonRequired(String action, Class<?>[] types) {
        if (!("reject".equals(action) || "close".equals(action) || "void".equals(action))) {
            return false;
        }
        return types.length == 3 && types[1] == String.class && types[2] == String.class;
    }

    /**
     * 按能力矩阵登记的签名**精确**反射调用（禁止候选名单试错）。
     * Service 多为 JDK/CGLIB 代理，必须用 getMethods() 遍历。
     */
    private InvocationOutcome dispatch(Object service, ActionSpec spec, Long billId, String operator, String reason) {
        if (service == null) {
            return new InvocationOutcome(false, null, null);
        }
        Class<?>[] types = spec.paramTypes();
        Object[] args = new Object[types.length];
        args[0] = billId;
        if (types.length > 1) args[1] = operator;
        if (types.length > 2) args[2] = reason;

        Method target = null;
        for (Method m : service.getClass().getMethods()) {
            if (!m.getName().equals(spec.method())) continue;
            if (!Arrays.equals(m.getParameterTypes(), types)) continue;
            target = m;
            break;
        }
        if (target == null) {
            return new InvocationOutcome(false, null, null);
        }
        try {
            return new InvocationOutcome(true, target.invoke(service, args), null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException() != null ? e.getTargetException() : e;
            return new InvocationOutcome(true, null, cause);
        } catch (Exception e) {
            return new InvocationOutcome(true, null, e);
        }
    }

    /** 取异常消息（业务提示原样返回，无消息时退回异常类名） */
    private String messageOf(Throwable e) {
        if (e == null) return "未知错误";
        return e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** 用户在小助手设置里选定的单据标识方式；未选/非法时按默认"单号"（scope 可能为 null） */
    private String billLabelStyle(AiDataScope scope) {
        if (scope == null || scope.getDisplayPrefs() == null) return BillCodeResolver.STYLE_CODE;
        return BillCodeResolver.normalizeStyle(scope.getDisplayPrefs().get(BillCodeResolver.PREF_KEY_BILL_LABEL_STYLE));
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }

    /** 反射调度结果：found=是否命中方法；value=返回值；error=业务异常 */
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
