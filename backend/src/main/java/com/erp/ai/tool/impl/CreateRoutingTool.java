package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.service.AiEntityMatchService;
import com.erp.ai.tool.AiTool;
import com.erp.production.entity.Routing;
import com.erp.production.entity.RoutingOperation;
import com.erp.production.service.RoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：创建**工艺路线主档（含工序明细）**。
 *
 * <p>为什么需要它：通用 `create_*` 工具只写单表字段，**带不了工序明细**（`Routing.operations`
 * 是非库字段列表，通用链路只 `service.save(entity)`）。用户口径（2026-10-10 二期）：
 * 「建工序」= 建**工艺路线主档里的工序** —— 走的正是 `saveRoutingWithOperations`。
 *
 * <p>自动匹配：物料按编码/名称匹配后自动选上（工艺路线通常绑一个物料）；工序里的工作中心按名称匹配。
 * 编码（路线编码、工序编码）一律由系统生成，不要传。
 *
 * <p>工单侧的工序实例**不在这里建**：它由工艺路线在工单提交/生效时自动生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateRoutingTool implements AiTool {

    private final AiEntityMatchService matchService;
    private final RoutingService routingService;

    @Override
    public String getName() {
        return "create_routing_with_operations";
    }

    @Override
    public String getDescription() {
        return "创建**工艺路线主档（含工序明细）**——即用户说的\"建工序/建工艺路线\"。"
                + "适用场景：用户给出零部件/产品的加工工序（自己的话，或读到的 Word/图片工艺卡内容），"
                + "要求\"建工艺路线/把工序录进系统\"。"
                + "必填：routingName（工艺路线名称）、operations（工序明细数组，每行至少 operationName）。"
                + "可选：routingCode（留空自动生成）、materialName/materialCode（这条路线绑定哪个物料，按名称或编码自动匹配）、"
                + "isPrimary（是否为主工艺，默认是）、remark。"
                + "工序行可带：operationName（工序名称，必填）、workCenterName（工作中心，按名称自动匹配）、"
                + "setupHours（准备工时）、processHours（加工工时）、unitHours（单件加工工时）、"
                + "isCritical（是否关键工序 true/false）、remark（备注）。"
                + "**注意**：工序号与编码由系统按顺序自动生成，不要传；"
                + "若要建的是普通物料/客户等主档，请用对应的 create_ 通用工具，不要用本工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("operationName", Map.of("type", "string", "description", "工序名称（必填，如 车削/铣削/装配）"));
        op.put("workCenterName", Map.of("type", "string", "description", "工作中心名称（可选，按名称自动匹配）"));
        op.put("setupHours", Map.of("type", "number", "description", "准备工时（小时，可选）"));
        op.put("processHours", Map.of("type", "number", "description", "加工工时（小时，可选）"));
        op.put("unitHours", Map.of("type", "number", "description", "单件加工工时（小时，可选）"));
        op.put("isCritical", Map.of("type", "boolean", "description", "是否关键工序（可选）"));
        op.put("remark", Map.of("type", "string", "description", "备注（可选）"));

        Map<String, Object> ops = new LinkedHashMap<>();
        ops.put("type", "array");
        ops.put("description", "工序明细（数组顺序即工序顺序）");
        ops.put("items", Map.of("type", "object", "properties", op));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("routingName", Map.of("type", "string", "description", "工艺路线名称（必填）"));
        props.put("routingCode", Map.of("type", "string", "description", "工艺路线编码（可选，留空由系统生成）"));
        props.put("materialName", Map.of("type", "string", "description", "这条工艺路线绑定的物料名称（可选）"));
        props.put("materialCode", Map.of("type", "string", "description", "这条工艺路线绑定的物料编码（可选，优先于名称）"));
        props.put("isPrimary", Map.of("type", "boolean", "description", "是否为主工艺路线（默认 true）"));
        props.put("remark", Map.of("type", "string", "description", "备注（可选）"));
        props.put("operations", ops);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("routingName", "operations"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        Long userId = scope != null ? scope.getUserId() : null;
        Map<String, Object> p = params == null ? Map.of() : params;
        try {
            String routingName = str(p.get("routingName"));
            if (isBlank(routingName)) return fail("请提供工艺路线名称（routingName）");

            Object opsObj = p.get("operations");
            if (!(opsObj instanceof List<?> rawOps) || rawOps.isEmpty()) {
                return fail("请提供工序明细（operations，至少一行工序）");
            }

            Routing routing = new Routing();
            routing.setRoutingName(routingName);
            if (!isBlank(str(p.get("routingCode")))) routing.setRoutingCode(str(p.get("routingCode")));
            routing.setStatus("启用");
            routing.setIsPrimary(Boolean.FALSE.equals(p.get("isPrimary")) ? (short) 0 : (short) 1);
            if (!isBlank(str(p.get("remark")))) routing.setRemark(str(p.get("remark")));
            if (userId != null) routing.setCreatedBy(userId.toString());

            // 绑定物料（按编码/名称自动匹配，匹配不到多条时停下来确认，不猜）
            String materialKeyword = !isBlank(str(p.get("materialCode"))) ? str(p.get("materialCode")) : str(p.get("materialName"));
            if (!isBlank(materialKeyword)) {
                Map<String, Object> mm = matchService.match("material", materialKeyword);
                if (!Boolean.TRUE.equals(mm.get("success"))) return fail(str(mm.get("message")));
                if (mm.get("matched") instanceof Map<?, ?> row) {
                    routing.setMaterialId(longOf(row.get("id")));
                    routing.setMaterialCode(str(row.get("code")));
                    routing.setMaterialName(str(row.get("name")));
                } else if (mm.get("candidates") instanceof List<?> l && !l.isEmpty()) {
                    return needConfirm("物料「" + materialKeyword + "」匹配到多条，请先确认用哪一个", l);
                } else {
                    return fail("系统里没有物料「" + materialKeyword + "」，请先建好物料再建它的工艺路线");
                }
            }

            // 工序明细：工作中心按名称自动匹配
            List<RoutingOperation> ops = new ArrayList<>();
            int seq = 0;
            for (Object o : rawOps) {
                if (!(o instanceof Map<?, ?> om)) continue;
                String opName = str(om.get("operationName"));
                if (isBlank(opName)) {
                    return fail("第 " + (seq + 1) + " 行工序缺少工序名称（operationName）");
                }
                seq++;
                RoutingOperation ro = new RoutingOperation();
                ro.setOperationName(opName);
                String wcName = str(om.get("workCenterName"));
                if (!isBlank(wcName)) {
                    Map<String, Object> wm = matchService.match("workcenter", wcName);
                    if (wm.get("matched") instanceof Map<?, ?> wcRow) {
                        ro.setWorkCenterId(longOf(wcRow.get("id")));
                        ro.setWorkCenterName(str(wcRow.get("name")));
                    } else if (wm.get("candidates") instanceof List<?> l && !l.isEmpty()) {
                        return needConfirm("第 " + seq + " 行的工作中心「" + wcName + "」匹配到多条，请先确认用哪一个", l);
                    } else {
                        // 工作中心不存在不算致命：保留名称、留空 ID，由用户后续在界面上补
                        ro.setWorkCenterName(wcName);
                    }
                }
                ro.setSetupHours(decimal(om.get("setupHours")));
                ro.setProcessHours(decimal(om.get("processHours")));
                ro.setUnitHours(decimal(om.get("unitHours")));
                ro.setIsCritical(Boolean.TRUE.equals(om.get("isCritical")) ? (short) 1 : (short) 0);
                ro.setRemark(str(om.get("remark")));
                ops.add(ro);
            }
            if (ops.isEmpty()) return fail("工序明细里没有有效的工序行（每行都需要 operationName）");

            routingService.saveRoutingWithOperations(routing, ops);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("routingId", routing.getRoutingId());
            result.put("routingCode", routing.getRoutingCode());
            result.put("routingName", routing.getRoutingName());
            result.put("operationCount", ops.size());
            if (routing.getMaterialCode() != null) {
                result.put("material", routing.getMaterialCode() + " " + routing.getMaterialName());
            }
            result.put("message", "已创建工艺路线「" + routing.getRoutingCode() + " " + routing.getRoutingName() + "」，共 "
                    + ops.size() + " 道工序"
                    + (routing.getMaterialCode() != null ? "，绑定物料：" + routing.getMaterialName() : "")
                    + "。工单引用这条路线后，提交生效时会自动生成对应工序。");
            log.info("[AI建工艺路线] 成功 code={} name={} 工序{}道 material={}",
                    routing.getRoutingCode(), routing.getRoutingName(), ops.size(), routing.getMaterialCode());
            return result;
        } catch (Exception e) {
            log.error("[AI建工艺路线] 失败", e);
            return fail("创建工艺路线失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private Map<String, Object> needConfirm(String message, List<?> candidates) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("needConfirm", true);
        m.put("message", message);
        m.put("candidates", candidates);
        return m;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Long longOf(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return null;
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal decimal(Object o) {
        if (o instanceof Number n) return new BigDecimal(n.toString());
        if (o == null) return null;
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
