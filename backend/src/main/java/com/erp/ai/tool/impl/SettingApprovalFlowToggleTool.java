package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.approval.entity.ApprovalFlow;
import com.erp.approval.service.ApprovalFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：启用/停用审批流程（设置中心 → 审批设置），走强制确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingApprovalFlowToggleTool implements AiTool {

    private final ApprovalFlowService approvalFlowService;

    @Override
    public String getName() {
        return "setting_approval_flow_toggle";
    }

    @Override
    public String getDescription() {
        return "启用或停用某个审批流程（执行前会弹确认卡片）。"
                + "必填参数：flowId(流程ID)、enabled(true启用/false停用)。"
                + "停用后该流程不参与单据审批匹配，请谨慎操作。先通过 setting_approval_flow_list 确认 flowId。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("flowId", Map.of("type", "integer", "description", "流程ID，必填"));
        props.put("enabled", Map.of("type", "boolean", "description", "是否启用：true启用/false停用，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("flowId", "enabled"));
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
            Long flowId = SettingSupport.longVal(p.get("flowId"));
            if (flowId == null || !p.containsKey("enabled")) {
                return Map.of("success", false, "message", "参数缺失：需要 flowId 和 enabled");
            }
            boolean enabled = SettingSupport.bool(p.get("enabled"), true);
            ApprovalFlow flow = approvalFlowService.getById(flowId);
            if (flow == null) {
                return Map.of("success", false, "message", "审批流程不存在: " + flowId);
            }
            approvalFlowService.setEnabled(flowId, (short) (enabled ? 1 : 0));
            log.info("[AI设置] 用户{}{}审批流程 flowId={} name={}",
                    scope != null ? scope.getUsername() : "?", enabled ? "启用" : "停用",
                    flowId, flow.getFlowName());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已" + (enabled ? "启用" : "停用") + "审批流程「" + flow.getFlowName() + "」。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 切换审批流程状态失败", e);
            return Map.of("success", false, "message", "切换审批流程状态失败: " + e.getMessage());
        }
    }
}
