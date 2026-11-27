package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.approval.entity.ApprovalFlow;
import com.erp.approval.entity.ApprovalNode;
import com.erp.approval.entity.ApprovalNodeApprover;
import com.erp.approval.service.ApprovalFlowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增/修改审批流程（设置中心 → 审批设置），走强制确认。
 * flowId 为空走 saveFlow（新增），否则走 updateFlow（修改，节点会整体重建）。
 * 重要：修改时节点为整体重建，必须完整传 nodes（否则原有节点会被清空）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingApprovalFlowSaveTool implements AiTool {

    private final ApprovalFlowService approvalFlowService;
    /** 自建 ObjectMapper 必须注册 JavaTimeModule（历史坑：AI 链路 LocalDate 序列化崩溃） */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String getName() {
        return "setting_approval_flow_save";
    }

    @Override
    public String getDescription() {
        return "新增或修改审批流程（执行前会弹确认卡片）。不传 flowId 为新增，传 flowId 为修改。"
                + "必填：flowName(流程名称)、billType(单据类型，如 SALE_ORDER)、nodes(节点数组，非空)。"
                + "可选：flowId、flowCode(流程编码，留空由系统生成)、enabled(是否启用，默认true)、description、priority、conditionExpr。"
                + "nodes 每项：nodeName(节点名)、nodeOrder(节点序号，从1开始)、"
                + "approverMode(single单人/any任选一人/all全部审批/sequence依次审批)、"
                + "approvers(审批人数组，元素可为姓名字符串如 \"张三\"，或对象 {approverName,userId,employeeId})、description。"
                + "注意：修改流程时节点为整体重建，必须完整传 nodes，否则原节点会被清空。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> nodeProps = new LinkedHashMap<>();
        nodeProps.put("type", "object");
        Map<String, Object> nodeFields = new LinkedHashMap<>();
        nodeFields.put("nodeName", Map.of("type", "string", "description", "节点名称"));
        nodeFields.put("nodeOrder", Map.of("type", "integer", "description", "节点序号，从1开始"));
        nodeFields.put("approverMode", Map.of("type", "string", "description", "审批模式：single/any/all/sequence"));
        nodeFields.put("approvers", Map.of("type", "array", "description",
                "审批人数组，元素为姓名或其对象", "items", Map.of("type", "object")));
        nodeFields.put("description", Map.of("type", "string", "description", "节点说明"));
        nodeProps.put("properties", nodeFields);
        nodeProps.put("required", List.of("nodeName"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("flowId", Map.of("type", "integer", "description", "流程ID，修改时必传；新增不传"));
        props.put("flowCode", Map.of("type", "string", "description", "流程编码，可空（留空由系统生成）"));
        props.put("flowName", Map.of("type", "string", "description", "流程名称，必填"));
        props.put("billType", Map.of("type", "string", "description", "单据类型，必填，如 SALE_ORDER"));
        props.put("enabled", Map.of("type", "boolean", "description", "是否启用，默认 true"));
        props.put("description", Map.of("type", "string", "description", "流程说明"));
        props.put("priority", Map.of("type", "integer", "description", "优先级（数字越小越优先）"));
        props.put("nodes", Map.of("type", "array", "description", "节点数组，必填非空", "items", nodeProps));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("flowName", "billType", "nodes"));
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
            String flowName = SettingSupport.str(p.get("flowName"));
            String billType = SettingSupport.str(p.get("billType"));
            Object nodesObj = p.get("nodes");

            if (flowName == null || billType == null) {
                return Map.of("success", false, "message", "参数缺失：需要 flowName、billType");
            }
            if (!(nodesObj instanceof List<?> rawNodes) || rawNodes.isEmpty()) {
                return Map.of("success", false, "message", "参数缺失：nodes 必须是非空数组");
            }

            ApprovalFlow flow = new ApprovalFlow();
            flow.setFlowId(flowId);
            flow.setFlowName(flowName);
            flow.setBillType(billType);
            if (p.containsKey("flowCode")) flow.setFlowCode(SettingSupport.str(p.get("flowCode")));
            if (p.containsKey("description")) flow.setDescription(SettingSupport.str(p.get("description")));
            if (p.containsKey("priority")) flow.setPriority(SettingSupport.integer(p.get("priority")));
            if (p.containsKey("enabled")) flow.setEnabled((short) (SettingSupport.bool(p.get("enabled"), true) ? 1 : 0));

            List<ApprovalNode> nodes = new ArrayList<>();
            int idx = 0;
            for (Object o : rawNodes) {
                idx++;
                if (!(o instanceof Map)) {
                    return Map.of("success", false, "message", "第 " + idx + " 个节点格式不正确");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> nm = (Map<String, Object>) o;
                ApprovalNode node = new ApprovalNode();
                node.setNodeName(SettingSupport.str(nm.get("nodeName")));
                node.setNodeOrder(SettingSupport.integer(nm.get("nodeOrder")));
                node.setApproverMode(SettingSupport.str(nm.get("approverMode")));
                node.setDescription(SettingSupport.str(nm.get("description")));
                if (node.getNodeName() == null) {
                    return Map.of("success", false, "message", "第 " + idx + " 个节点缺少 nodeName");
                }
                List<ApprovalNodeApprover> approvers = new ArrayList<>();
                if (nm.get("approvers") instanceof List<?> rawApprovers) {
                    for (Object ao : rawApprovers) {
                        if (ao instanceof String name) {
                            ApprovalNodeApprover a = new ApprovalNodeApprover();
                            a.setApproverName(name);
                            approvers.add(a);
                        } else if (ao instanceof Map) {
                            approvers.add(mapper.convertValue(ao, ApprovalNodeApprover.class));
                        }
                    }
                }
                node.setApprovers(approvers);
                nodes.add(node);
            }
            flow.setNodes(nodes);

            boolean isNew = flowId == null;
            if (isNew) {
                approvalFlowService.saveFlow(flow);
            } else {
                approvalFlowService.updateFlow(flow);
            }
            log.info("[AI设置] 用户{} {}审批流程 name={} billType={} flowId={} 节点数={}",
                    scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                    flowName, billType, flow.getFlowId(), nodes.size());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", (isNew ? "已新增" : "已修改") + "审批流程「" + flowName + "」（"
                    + nodes.size() + " 个节点）。");
            if (flow.getFlowId() != null) result.put("flowId", flow.getFlowId());
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 保存审批流程失败", e);
            return Map.of("success", false, "message", "保存审批流程失败: " + e.getMessage());
        }
    }
}
