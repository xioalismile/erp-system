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
 * 写工具：删除审批流程（设置中心 → 审批设置），走强制确认。
 * 级联删除节点、审批人与条件模板，不可恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingApprovalFlowDeleteTool implements AiTool {

    private final ApprovalFlowService approvalFlowService;

    @Override
    public String getName() {
        return "setting_approval_flow_delete";
    }

    @Override
    public String getDescription() {
        return "删除一个审批流程（执行前会弹确认卡片）。会级联删除该流程的节点、审批人与条件模板，不可恢复，请谨慎操作。"
                + "必填参数：flowId(流程ID，先通过 setting_approval_flow_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("flowId", Map.of("type", "integer", "description", "流程ID，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("flowId"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Long flowId = SettingSupport.longVal(params == null ? null : params.get("flowId"));
            if (flowId == null) {
                return Map.of("success", false, "message", "参数缺失：需要 flowId");
            }
            ApprovalFlow flow = approvalFlowService.getById(flowId);
            if (flow == null) {
                return Map.of("success", false, "message", "审批流程不存在: " + flowId);
            }
            approvalFlowService.deleteFlow(flowId);
            log.info("[AI设置] 用户{}删除审批流程 flowId={} name={}",
                    scope != null ? scope.getUsername() : "?", flowId, flow.getFlowName());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已删除审批流程「" + flow.getFlowName() + "」及其节点、审批人、条件。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 删除审批流程失败", e);
            return Map.of("success", false, "message", "删除审批流程失败: " + e.getMessage());
        }
    }
}
