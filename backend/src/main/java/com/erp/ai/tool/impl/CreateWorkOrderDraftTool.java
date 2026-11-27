package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.service.AiEntityMatchService;
import com.erp.ai.tool.AiTool;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.production.entity.Routing;
import com.erp.production.entity.WorkOrder;
import com.erp.production.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：照着读到的内容创建**生产工单草稿**（含自动匹配选上）。
 *
 * <p>用户口径（2026-10-10 二期）：
 * ① 物料/仓库/部门/工作中心按**编码或名称自动匹配选中**（匹配不到多条时列出来让用户确认，不猜）；
 * ② 文件里**系统还没有的物料**：自动先建物料（带单位与图片）再建工单；
 * ③ 工艺路线自动取该物料的**主工艺路线**（用户可在确认卡片里看到用的是哪条）；
 * ④ **只保存为草稿** —— 提交/生效由用户自己在界面上操作（生效时系统按工艺路线生成工序实例）。
 *
 * <p>工序：本工具**不建工序实例**。工单的工序由「工艺路线」在该单**提交/生效时自动生成**
 * （见 WorkOrderService.submit/approve），所以只要把工艺路线选对，工序就自然有了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateWorkOrderDraftTool implements AiTool {

    private final AiEntityMatchService matchService;
    private final WorkOrderService workOrderService;
    private final MaterialService materialService;

    @Override
    public String getName() {
        return "create_work_order_draft";
    }

    @Override
    public String getDescription() {
        return "创建**生产工单草稿**（自动匹配物料/仓库/部门/工作中心并选上；只存草稿，不提交不审核）。"
                + "适用场景：用户给出生产任务（自己的话、读到的 Word/图片工单内容）并要求\"建工单/开生产单\"。"
                + "必填：materialName 或 materialCode（要生产什么）、quantity（数量）。"
                + "可选：routingName/routingCode（工艺路线，不传则自动取该物料的主工艺路线）、"
                + "warehouseName、departmentName、workCenterName、planStartDate、planEndDate（yyyy-MM-dd）、remark。"
                + "**系统里没有这个物料时**：默认会自动先建物料（autoCreateMaterial 默认 true），"
                + "可用 materialSpec（规格型号）/materialUnit（单位）/materialImages（图片地址数组，如文档里抽出的图）一起建。"
                + "**注意**：工艺路线的工序由工单提交/生效时自动生成，本工具不建工序；"
                + "若该物料没有配工艺路线，会在结果里给出提示（工单仍会建成草稿）。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("materialName", Map.of("type", "string", "description", "要生产的物料名称（与 materialCode 至少一个）"));
        props.put("materialCode", Map.of("type", "string", "description", "要生产的物料编码（与 materialName 至少一个）"));
        props.put("quantity", Map.of("type", "number", "description", "生产数量，必须大于 0"));
        props.put("routingName", Map.of("type", "string", "description", "工艺路线名称（可选；不传则自动取该物料的主工艺路线）"));
        props.put("routingCode", Map.of("type", "string", "description", "工艺路线编码（可选，优先于名称）"));
        props.put("warehouseName", Map.of("type", "string", "description", "仓库名称（可选，按名称自动匹配）"));
        props.put("departmentName", Map.of("type", "string", "description", "生产部门名称（可选）"));
        props.put("workCenterName", Map.of("type", "string", "description", "工作中心名称（可选）"));
        props.put("planStartDate", Map.of("type", "string", "description", "计划开始日期 yyyy-MM-dd（可选）"));
        props.put("planEndDate", Map.of("type", "string", "description", "计划结束日期 yyyy-MM-dd（可选）"));
        props.put("remark", Map.of("type", "string", "description", "备注（可选）"));
        props.put("autoCreateMaterial", Map.of("type", "boolean", "description", "物料不存在时是否自动新建（默认 true）"));
        props.put("materialSpec", Map.of("type", "string", "description", "自动新建物料时的规格型号（可选）"));
        props.put("materialUnit", Map.of("type", "string", "description", "自动新建物料时的基本单位名称（可选，如 个/件/米）"));
        Map<String, Object> images = new LinkedHashMap<>();
        images.put("type", "array");
        images.put("description", "自动新建物料时带入的图片地址（可选，如 document_read 抽出的图片地址）");
        images.put("items", Map.of("type", "string"));
        props.put("materialImages", images);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("quantity"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        Long userId = scope != null ? scope.getUserId() : null;
        Map<String, Object> p = params == null ? Map.of() : params;
        try {
            String materialName = str(p.get("materialName"));
            String materialCode = str(p.get("materialCode"));
            if (isBlank(materialName) && isBlank(materialCode)) {
                return fail("请提供要生产的物料名称或物料编码（materialName / materialCode）");
            }
            BigDecimal quantity = decimal(p.get("quantity"));
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                return fail("生产数量（quantity）必须大于 0");
            }

            // ===== 1. 物料：匹配 → 缺则按用户口径自动新建 =====
            Long materialId;
            String saveMaterialCode;
            String saveMaterialName;
            String saveModel;
            Long unitId;
            boolean materialCreated = false;
            Map<String, Object> materialMatch = matchService.match("material",
                    isBlank(materialCode) ? materialName : materialCode);
            if (!Boolean.TRUE.equals(materialMatch.get("success"))) {
                return fail(str(materialMatch.get("message")));
            }
            Object matched = materialMatch.get("matched");
            if (matched instanceof Map<?, ?> row) {
                materialId = longOf(row.get("id"));
                saveMaterialCode = str(row.get("code"));
                saveMaterialName = str(row.get("name"));
                saveModel = str(row.get("spec"));
                unitId = longOf(row.get("unitId"));
            } else {
                Object candidates = materialMatch.get("candidates");
                if (candidates instanceof List<?> l && !l.isEmpty()) {
                    return needConfirm("物料「" + (isBlank(materialCode) ? materialName : materialCode)
                            + "」在系统里匹配到多条，请先确认用哪一个", l);
                }
                boolean autoCreate = !(p.get("autoCreateMaterial") instanceof Boolean b) || b;
                if (!autoCreate) {
                    return fail("系统里没有物料「" + (isBlank(materialCode) ? materialName : materialCode)
                            + "」，且未允许自动新建（autoCreateMaterial=false）。请先在物料档案里建好，或改为允许自动新建。");
                }
                if (isBlank(materialName)) {
                    return fail("自动新建物料需要物料名称（materialName）");
                }
                Material created = createMaterial(p, userId);
                if (created == null) {
                    return fail("自动新建物料「" + materialName + "」失败，请先在物料档案里手工建好再试");
                }
                materialId = created.getMaterialId();
                saveMaterialCode = created.getMaterialCode();
                saveMaterialName = created.getMaterialName();
                saveModel = created.getModel();
                unitId = created.getBaseUnitId();
                materialCreated = true;
            }

            // ===== 2. 工艺路线：优先用户指定，否则取该物料的主工艺路线 =====
            Long routingId = null;
            String routingVersion = null;
            String routingLabel = null;
            String routingWarning = null;
            String routingKeyword = !isBlank(str(p.get("routingCode"))) ? str(p.get("routingCode")) : str(p.get("routingName"));
            if (!isBlank(routingKeyword)) {
                Map<String, Object> rm = matchService.match("routing", routingKeyword);
                if (!Boolean.TRUE.equals(rm.get("success"))) return fail(str(rm.get("message")));
                if (rm.get("matched") instanceof Map<?, ?> row) {
                    routingId = longOf(row.get("id"));
                    routingLabel = str(row.get("code")) + " " + str(row.get("name"));
                } else if (rm.get("candidates") instanceof List<?> l && !l.isEmpty()) {
                    return needConfirm("工艺路线「" + routingKeyword + "」匹配到多条，请先确认用哪一个", l);
                } else {
                    return fail("系统里没有工艺路线「" + routingKeyword + "」");
                }
            } else {
                Routing r = matchService.primaryRoutingOfMaterial(materialId);
                if (r != null) {
                    routingId = r.getRoutingId();
                    routingVersion = r.getRoutingVersion();
                    routingLabel = r.getRoutingCode() + " " + r.getRoutingName();
                } else {
                    routingWarning = "该物料还没有配置工艺路线，工单提交后不会生成工序；请先在【工艺路线】里建好并设为该物料的主路线"
                            + "（或在本轮直接指定工艺路线）。";
                }
            }

            // ===== 3. 可选引用：仓库 / 部门 / 工作中心（匹配到多条时停下来让用户确认，不猜） =====
            List<Map<String, Object>> pending = new ArrayList<>();
            Long warehouseId = resolveRef("warehouse", str(p.get("warehouseName")), pending);
            if (!pending.isEmpty()) return pending.get(0);
            Long departmentId = resolveRef("department", str(p.get("departmentName")), pending);
            if (!pending.isEmpty()) return pending.get(0);
            Long workCenterId = resolveRef("workcenter", str(p.get("workCenterName")), pending);
            if (!pending.isEmpty()) return pending.get(0);

            // ===== 4. 建工单（只存草稿） =====
            WorkOrder order = new WorkOrder();
            order.setMaterialId(materialId);
            order.setMaterialCode(saveMaterialCode);
            order.setMaterialName(saveMaterialName);
            order.setModel(saveModel);
            order.setUnitId(unitId);
            order.setQuantity(quantity);
            order.setRoutingId(routingId);
            order.setRoutingVersion(routingVersion);
            order.setWarehouseId(warehouseId);
            order.setDepartmentId(departmentId);
            order.setWorkCenterId(workCenterId);
            order.setPlanStartDate(date(p.get("planStartDate")));
            order.setPlanEndDate(date(p.get("planEndDate")));
            order.setRemark(str(p.get("remark")));
            order.setStatus("草稿");
            order.setOrderDate(LocalDate.now());
            order.setCreatedBy(userId != null ? userId.toString() : "AI");

            workOrderService.saveOrderWithItems(order, null);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("orderId", order.getOrderId());
            result.put("orderNo", order.getOrderNo());
            result.put("status", order.getStatus());
            result.put("material", saveMaterialCode + " " + saveMaterialName);
            result.put("quantity", quantity);
            result.put("materialCreated", materialCreated);
            result.put("routing", routingLabel);
            if (routingWarning != null) result.put("warning", routingWarning);
            result.put("message", "已创建生产工单草稿「" + order.getOrderNo() + "」："
                    + saveMaterialName + " × " + quantity.toPlainString()
                    + (routingLabel != null ? "，工艺路线：" + routingLabel : "")
                    + (materialCreated ? "（物料为本次自动新建）" : "")
                    + "。请在【生产工单】里核对后自行提交；提交生效后系统会按工艺路线自动生成工序。");
            log.info("[AI建工单] 成功 orderNo={} material={} routing={} materialCreated={}",
                    order.getOrderNo(), saveMaterialCode, routingId, materialCreated);
            return result;
        } catch (Exception e) {
            log.error("[AI建工单] 失败", e);
            return fail("创建生产工单失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 自动新建物料：编码由系统生成（不传）。
     * 基本单位是数据库 NOT NULL 列，必须给值 —— 按传入单位名称匹配，匹配不到时取第一个启用单位兜底。
     */
    private Material createMaterial(Map<String, Object> p, Long userId) {
        try {
            Material m = new Material();
            m.setMaterialName(str(p.get("materialName")));
            String spec = str(p.get("materialSpec"));
            if (!isBlank(spec)) m.setModel(spec);
            String unit = str(p.get("materialUnit"));
            Long unitId = matchService.resolveUnitId(unit);
            if (unitId == null) {
                log.warn("[AI建工单] 系统里没有任何计量单位，无法自动新建物料");
                return null;
            }
            m.setBaseUnitId(unitId);
            List<String> images = new ArrayList<>();
            Object imgObj = p.get("materialImages");
            if (imgObj instanceof List<?> l) {
                for (Object o : l) {
                    String s = str(o);
                    if (!isBlank(s)) images.add(s);
                }
            }
            if (!images.isEmpty()) {
                m.setImageUrls(String.join(",", images));
                m.setMainImage(images.get(0));
            }
            m.setStatus("启用");
            if (userId != null) m.setCreatedBy(userId.toString());
            materialService.save(m);
            log.info("[AI建工单] 已自动新建物料 code={} name={} 图片{}张",
                    m.getMaterialCode(), m.getMaterialName(), images.size());
            return m;
        } catch (Exception e) {
            log.warn("[AI建工单] 自动新建物料失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 匹配可选引用（仓库/部门/工作中心）。
     * 匹配到多条时**不猜**：把"需确认"结果塞进 pending，由调用方立即返回让用户先确认。
     */
    private Long resolveRef(String type, String keyword, List<Map<String, Object>> pending) {
        if (isBlank(keyword)) return null;
        Map<String, Object> m = matchService.match(type, keyword);
        if (m.get("matched") instanceof Map<?, ?> row) {
            return longOf(row.get("id"));
        }
        if (m.get("candidates") instanceof List<?> l && !l.isEmpty()) {
            pending.add(needConfirm("「" + keyword + "」匹配到多条，请先确认用哪一个", l));
        }
        return null;
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

    private LocalDate date(Object o) {
        String s = str(o);
        if (isBlank(s)) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }
}
