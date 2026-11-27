package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.approval.entity.ApprovalCondition;
import com.erp.approval.entity.ApprovalFlow;
import com.erp.approval.entity.ApprovalNode;
import com.erp.approval.entity.ApprovalNodeApprover;
import com.erp.approval.service.ApprovalFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询审批流程（设置中心 → 审批设置）。
 * flowId 有值时返回流程详情(含节点)+条件；否则按 billType/keyword 返回流程列表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingApprovalFlowListTool implements AiTool {

    private final ApprovalFlowService approvalFlowService;

    @Override
    public String getName() {
        return "setting_approval_flow_list";
    }

    @Override
    public String getDescription() {
        return "查询审批流程（只读）。传 flowId 返回该流程详情（含节点、审批人）及其条件模板；"
                + "不传 flowId 时按单据类型/关键词返回流程列表。"
                + "参数：flowId(可选，流程ID)、billType(可选，单据类型如 SALE_ORDER)、keyword(可选，按流程编码或名称过滤)。"
                + "修改流程请用 setting_approval_flow_save。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("flowId", Map.of("type", "integer", "description", "可选，流程ID；传则返回详情+条件"));
        props.put("billType", Map.of("type", "string", "description", "可选，单据类型，如 SALE_ORDER"));
        props.put("keyword", Map.of("type", "string", "description", "可选，按流程编码或名称模糊过滤"));
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
            Map<String, Object> p = params == null ? Map.of() : params;
            Long flowId = SettingSupport.longVal(p.get("flowId"));
            String billType = SettingSupport.str(p.get("billType"));
            String keyword = SettingSupport.str(p.get("keyword"));

            Map<String, Object> result = new LinkedHashMap<>();
            if (flowId != null) {
                ApprovalFlow flow = approvalFlowService.getFlowDetail(flowId);
                List<ApprovalCondition> conditions = approvalFlowService.getConditions(flowId);
                List<Map<String, Object>> conditionItems = new ArrayList<>();
                if (conditions != null) {
                    for (ApprovalCondition c : conditions) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("conditionId", c.getConditionId());
                        m.put("fieldKey", c.getFieldKey());
                        m.put("operator", c.getOperator());
                        m.put("valueLow", c.getValueLow());
                        m.put("valueHigh", c.getValueHigh());
                        m.put("valueText", c.getValueText());
                        m.put("targetNodeOrder", c.getTargetNodeOrder());
                        m.put("description", c.getDescription());
                        conditionItems.add(m);
                    }
                }
                result.put("success", true);
                result.put("flow", toFlowMap(flow, true));
                result.put("conditions", conditionItems);
                result.put("message", "流程「" + flow.getFlowName() + "」共 "
                        + (flow.getNodes() == null ? 0 : flow.getNodes().size()) + " 个节点、"
                        + conditionItems.size() + " 个条件。");
                return result;
            }

            List<ApprovalFlow> flows = approvalFlowService.listFlows(billType, keyword);
            List<Map<String, Object>> items = new ArrayList<>();
            if (flows != null) {
                for (ApprovalFlow f : flows) {
                    items.add(toFlowMap(f, false));
                }
            }
            result.put("success", true);
            result.put("total", items.size());
            result.put("items", items);
            result.put("message", "共查到 " + items.size() + " 个审批流程。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询审批流程失败", e);
            return Map.of("success", false, "message", "查询审批流程失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toFlowMap(ApprovalFlow f, boolean withNodes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flowId", f.getFlowId());
        m.put("flowCode", f.getFlowCode());
        m.put("flowName", f.getFlowName());
        m.put("billType", f.getBillType());
        m.put("enabled", f.getEnabled());
        m.put("description", f.getDescription());
        m.put("priority", f.getPriority());
        m.put("conditionExpr", f.getConditionExpr());
        m.put("notifyUserIds", f.getNotifyUserIds());
        if (withNodes && f.getNodes() != null) {
            List<Map<String, Object>> nodeItems = new ArrayList<>();
            for (ApprovalNode n : f.getNodes()) {
                Map<String, Object> nm = new LinkedHashMap<>();
                nm.put("nodeId", n.getNodeId());
                nm.put("nodeName", n.getNodeName());
                nm.put("nodeOrder", n.getNodeOrder());
                nm.put("approverMode", n.getApproverMode());
                nm.put("description", n.getDescription());
                List<String> names = new ArrayList<>();
                if (n.getApprovers() != null) {
                    for (ApprovalNodeApprover a : n.getApprovers()) {
                        names.add(a.getApproverName());
                    }
                }
                nm.put("approvers", names);
                nodeItems.add(nm);
            }
            m.put("nodes", nodeItems);
        }
        return m;
    }
}
