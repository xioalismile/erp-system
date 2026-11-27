package com.erp.common.bill;

import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.customfield.service.CustomFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据审批「通用接入件」：给未继承 {@link AbstractBillService} 的手写单据Service 复用审批引擎。
 *
 * <p>接入单据只需在自己的「提交」方法里两分支：
 * <pre>
 *     if (billApprovalAdapter.submitToApproval("STOCK_CHECK", id, no, submitter, bill)) {
 *         // 开关开：单据已置「待审核」并落库，且已生成审批待办
 *         // （无可用审批流程时 startApproval 抛错 → 整个提交事务回滚，单据留在草稿）
 *     } else {
 *         // 免审批：调用方自己走「提交即生效」（各自的 approve 逻辑；审核人留空，不写系统占位）
 *     }
 * </pre>
 * </p>
 *
 * <p>口径与 {@link AbstractBillService#submitForApproval} / {@link AbstractBillService#buildBillData} 完全一致，
 * 保证已接入的 3 张单据（销售订单/采购订单/销售合同）与本次接入的单据在
 * 「审批开关判断、billData 条件路由字段、自定义字段值」三处同源。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillApprovalAdapter {

    /** 审批开关网关（全局开关 approval.enabled + 单据级开关 approval.bill.{billType}.enabled） */
    private final ApprovalGateHelper approvalGateHelper;

    /** 自定义字段服务（构建审批条件 billData 用） */
    private final CustomFieldService customFieldService;

    /**
     * 审批运行时引擎（提交时创建审批任务）。
     * @Lazy 打破循环依赖：审批引擎按 BillApprovalCallback 收集全部单据Service，
     * 单据Service又依赖本接入件启动任务 —— 延迟代理避免启动期循环创建失败。
     */
    @Lazy
    @Autowired
    private ApprovalRuntimeService approvalRuntimeService;

    /**
     * 提交单据进入审批引擎（供手写单据Service 的「提交」分支调用）。
     *
     * @param billType   单据类型编码（与设置中心审批开关一致，如 STOCK_CHECK）
     * @param billId     单据主键
     * @param billNo     单据编号（审批任务/通知展示用）
     * @param submitter  提交人（登录用户名）
     * @param billEntity 单据实体（用于构建条件路由 billData）
     * @return true=已启动审批（调用方无需再走"提交即生效"）；false=免审批（调用方自行生效）
     * @throws com.erp.config.BusinessException 开关开但无可用流程 / 审批人无效 → 提交事务回滚
     */
    public boolean submitToApproval(String billType, Long billId, String billNo, String submitter, Object billEntity) {
        if (!approvalGateHelper.needApproval(billType)) {
            return false;
        }
        approvalRuntimeService.startApproval(billType, billId, billNo, submitter, buildBillData(billEntity, billType, billId));
        return true;
    }

    /**
     * 构建审批条件判断用的单据数据快照（billData），与 {@link AbstractBillService#buildBillData} 口径一致：
     *  - 表头全部字段（沿类层次反射，camelCase 与 snake_case 双写，条件配置可用任一格式）
     *  - 自定义字段值（key = 自定义字段 fieldKey，entityType = billType 小写）
     */
    public Map<String, Object> buildBillData(Object billEntity, String billType, Long billId) {
        Map<String, Object> billData = new HashMap<>();
        if (billEntity == null) return billData;
        // 1) 表头字段：沿类层次反射全部字段（含父类），跳过静态字段与集合类字段
        for (Class<?> c = billEntity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (List.class.isAssignableFrom(ft) || Map.class.isAssignableFrom(ft)) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(billEntity);
                    billData.put(f.getName(), v);                    // camelCase（如 contactPerson）
                    billData.put(camelToSnake(f.getName()), v);       // snake_case（如 contact_person）
                } catch (Exception ignore) {
                    // 反射读取失败的字段跳过，不阻断审批提交
                }
            }
        }
        // 2) 自定义字段值：entityType = billType 小写（与自定义字段配置口径一致，如 STOCK_CHECK → stock_check）
        try {
            if (billType != null && !billType.isBlank() && billId != null) {
                billData.putAll(customFieldService.getFieldValues(billType.toLowerCase(), billId));
            }
        } catch (Exception e) {
            log.warn("加载自定义字段值失败（不影响审批提交）: billType={}, id={}, err={}", billType, billId, e.getMessage());
        }
        return billData;
    }

    /** camelCase → snake_case（contactPerson → contact_person） */
    private static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (char ch : name.toCharArray()) {
            if (Character.isUpperCase(ch)) sb.append('_').append(Character.toLowerCase(ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 取当前登录用户名作为提交人（单据提交接口未传提交人时用）。
     * 口径与 {@link com.erp.config.AuditMetaObjectHandler} 一致：读 AuthInterceptor 注入的
     * request 属性 currentUsername；取不到（非 Web 线程/未登录）返回 null，由引擎按系统处理。
     */
    public static String currentSubmitter() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            Object username = attrs.getRequest().getAttribute("currentUsername");
            if (username instanceof String s && !s.isBlank()) return s;
        } catch (Exception ignore) {
            // 非 Web 上下文（定时任务等）不阻断提交
        }
        return null;
    }
}
