package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.task.AiTaskService;
import com.erp.ai.tool.AiTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷多步骤任务链（写，需用户确认一次）。
 *
 * <p>适用：用户的诉求需要"先建 A → 再建 B → 再提交/审核/下推 C"这类多步串行操作。
 * 一条任务只弹一张确认卡，确认后按顺序执行；前置失败时下游自动标"未执行（前置失败）"。
 */
@Component
@RequiredArgsConstructor
public class TaskPlanExecuteTool implements AiTool {

    private final AiTaskService taskService;

    @Override
    public String getName() {
        return AiTaskService.TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "多步骤任务（写，需用户确认一次）：当用户的要求需要按顺序做多件事时用它，"
                + "例如「先建单位/物料/BOM，再建销售订单」「建完物料后提交审核并下推采购」。"
                + "一条任务只弹一张确认卡，确认后按步骤顺序执行；不会每步都让用户点一次。"
                + "参数 steps 是数组，每项 {title: 步骤名称, tool: 要调用的工具名, params: 该工具参数, dependsOn: 依赖的步骤序号数组}。"
                + "tool 必须写系统里已有的工具名（如 create_basedata_material / batch_create_records / bill_action / bill_pushdown）。"
                + "**有前置关系的步骤必须写 dependsOn**（如第2步依赖第1步 = dependsOn:[1]）；这样第1步失败时第2步会标「未执行（前置失败）」。"
                + "后面步骤要引用前面刚建数据的编码/主键时，用占位符 {{序号.字段}}：如物料编码 {{1.code}}、"
                + "批量创建里第1条结果 {{1.results.0.code}}、主键 {{1.id}}、名称 {{1.name}}。"
                + "failPolicy 可选：MARK_DOWNSTREAM（默认，前置失败则下游不执行）/ SKIP（跳过失败继续）/ STOP（遇错整链停止）。"
                + "最多 30 步；只读查询也能作为步骤放进任务里（如先查库存再开单）。"
                + "**不要用它替代单条写入**：用户只让建一条数据时，仍用普通创建工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("type", "string");
        title.put("description", "步骤名称（面向用户，如「创建物料」「创建BOM」「创建销售订单」）");

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "string");
        tool.put("description", "要调用的工具名（系统已注册的工具，如 create_basedata_material / batch_create_records / bill_action）");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("description", "该工具的参数；可写占位符 {{序号.code}} 引用前面步骤的结果");

        Map<String, Object> dependsOn = new LinkedHashMap<>();
        dependsOn.put("type", "array");
        dependsOn.put("description", "依赖的步骤序号（从1开始）；前置步骤失败时本步不执行");
        dependsOn.put("items", Map.of("type", "integer"));

        Map<String, Object> stepProps = new LinkedHashMap<>();
        stepProps.put("title", title);
        stepProps.put("tool", tool);
        stepProps.put("params", params);
        stepProps.put("dependsOn", dependsOn);

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", "object");
        step.put("properties", stepProps);
        step.put("required", List.of("title", "tool", "params"));

        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("type", "array");
        steps.put("description", "按执行顺序排列的步骤，最多 30 步");
        steps.put("items", step);

        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("type", "string");
        goal.put("description", "这条任务要达成的目标（用户原话摘要）");

        Map<String, Object> failPolicy = new LinkedHashMap<>();
        failPolicy.put("type", "string");
        failPolicy.put("description", "失败策略：MARK_DOWNSTREAM（默认）/ SKIP / STOP");
        failPolicy.put("enum", List.of(AiTaskService.POLICY_MARK_DOWNSTREAM,
                AiTaskService.POLICY_SKIP, AiTaskService.POLICY_STOP));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("goal", goal);
        props.put("steps", steps);
        props.put("failPolicy", failPolicy);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("goal", "steps"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        AiDataScope scope = new AiDataScope();
        scope.setUserId(userId);
        return taskService.execute(scope, params);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        return taskService.execute(scope, params);
    }
}
