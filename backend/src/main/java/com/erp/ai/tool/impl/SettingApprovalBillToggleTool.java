package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.approval.service.ApprovalRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：开关某单据类型的审批（设置中心 → 审批设置 / 单据级审批开关），走强制确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingApprovalBillToggleTool implements AiTool {

    private final ApprovalRuntimeService approvalRuntimeService;

    @Override
    public String getName() {
        return "setting_approval_bill_toggle";
    }

    @Override
    public String getDescription() {
        return "开启或关闭某单据类型的审批（执行前会弹确认卡片）。对应系统参数 approval.bill.<单据类型>.enabled。"
                + "必填参数：billType(单据类型，如 SALE_ORDER)、enabled(true开启/false关闭)。"
                + "关闭后该类单据提交时不进入审批流程，请谨慎操作。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("billType", Map.of("type", "string", "description", "单据类型，必填，如 SALE_ORDER"));
        props.put("enabled", Map.of("type", "boolean", "description", "是否开启审批：true开启/false关闭，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("billType", "enabled"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Map<String, Object> p = params == null ? Map.of() : params;
            String billType = SettingSupport.str(p.get("billType"));
            if (billType == null || !p.containsKey("enabled")) {
                return Map.of("success", false, "message", "参数缺失：需要 billType 和 enabled");
            }
            boolean enabled = SettingSupport.bool(p.get("enabled"), true);
            String operator = scope != null ? scope.getUsername() : null;
            approvalRuntimeService.updateBillApprovalSwitch(billType, enabled, operator);
            log.info("[AI设置] 用户{}{}单据审批开关 billType={}",
                    operator, enabled ? "开启" : "关闭", billType);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已" + (enabled ? "开启" : "关闭") + "单据类型【" + billType + "】的审批。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 切换单据审批开关失败", e);
            return Map.of("success", false, "message", "切换单据审批开关失败: " + e.getMessage());
        }
    }
}
