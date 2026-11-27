package com.erp.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.service.WorkOrderOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：生产「工序操作面板」的 7 个界面操作（状态流转 / 返工 / 报废 / 一键推补料 / 一键推入库 / 录入质检 / 发通知）。
 *
 * <p>背景（2026-09-30 批次8）：工序操作面板的 7 个动作 Service 签名是**多参数**
 * （如 {@code act(Long,String,Map,Long,String)}、{@code confirmRework(Long,BigDecimal,String,Long,String)}），
 * 塞不进「单据动作矩阵」（矩阵只支持"单据ID+动作+原因"），故单独做一个工具。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>7 个 action 与面板端点 1:1</b>：flow=/{id}/action、rework=/{id}/rework、scrap=/{id}/scrap、
 *       pushSupplement=/{id}/push-supplement、pushReceipt=/{id}/push-receipt、qc=/{id}/qc、notify=/{id}/notify；</li>
 *   <li><b>工序定位双通道</b>：优先用 operationId（工单工序主键），也支持 workOrderNo + operationName 组合定位；</li>
 *   <li><b>写操作走既有两段式确认</b>：本工具按写工具处理，由 {@code ReActAgentService} 拦截后走
 *       {@code AiConfirmService} 弹卡片，用户确认才真正执行，本类不自造确认逻辑；</li>
 *   <li><b>权限复用既有口径</b>：暴露/执行层统一复用「生产工单」菜单权限（不新造权限码）；</li>
 *   <li><b>操作人一律取当前登录态</b>（AiDataScope），不接受 AI 传值，与界面端点一致（审计要求）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOperationActionTool implements AiTool {

    /** 状态流转（/{id}/action）里允许的工序状态动作（= WorkOperationStatus 的动作常量，中文原样） */
    private static final List<String> STATUS_ACTIONS = List.of(
            "派工", "开工", "暂停", "恢复", "完工", "数量报工",
            "检验通过", "检验退回", "取消", "恢复工序", "更新计划");

    private final WorkOrderOperationService workOrderOperationService;
    private final WorkOrderMapper workOrderMapper;

    @Override
    public String getName() {
        return "work_operation_action";
    }

    @Override
    public String getDescription() {
        return "对生产工单的某道工序执行「工序操作面板」上的操作（7 种）。"
                + "action 取值：flow 状态流转（报工/开工/暂停/完工/取消等） / rework 返工（回退确认） / "
                + "scrap 报废（判废确认） / pushSupplement 一键推补料（按判废量×主BOM生成补料领料单草稿） / "
                + "pushReceipt 一键推入库（生成成品入库单草稿） / qc 录入质检（按质检模板判定） / notify 发通知（@下道负责人等）。"
                + "定位工序二选一：operationId（工单工序主键，需先查到），或 workOrderNo + operationName。"
                + "flow 需再传 statusAction（派工/开工/暂停/恢复/完工/数量报工/检验通过/检验退回/取消/恢复工序/更新计划）；"
                + "数量报工传 quantity（产出数）与 rejectedQuantity（不良/报废数）；返工/报废/入库传 quantity。"
                + "约束：需按顺序执行的工艺路线，开工/报工前必须前道已完工；质检工序完工后先落「完工待检」，"
                + "录入质检判定合格才算已完工；取消必须填取消原因。"
                + "操作前会弹确认卡片，需用户确认后才会真正执行；操作人取当前登录账号。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", Map.of("type", "string",
                "description", "要执行的操作",
                "enum", new ArrayList<>(List.of(
                        "flow", "rework", "scrap", "pushSupplement", "pushReceipt", "qc", "notify"))));
        props.put("operationId", Map.of("type", "integer",
                "description", "工单工序主键（work_order_operation.id）。与 workOrderNo+operationName 二选一"));
        props.put("workOrderNo", Map.of("type", "string",
                "description", "工单号（与 operationName 搭配定位工序；不给 operationId 时用）"));
        props.put("operationName", Map.of("type", "string",
                "description", "工序名称（与 workOrderNo 搭配定位工序）"));
        props.put("statusAction", Map.of("type", "string",
                "description", "仅 action=flow 时使用：状态动作",
                "enum", new ArrayList<>(STATUS_ACTIONS)));
        props.put("quantity", Map.of("type", "number",
                "description", "数量：数量报工的产出数 / 返工数量 / 报废数量 / 入库数量"));
        props.put("rejectedQuantity", Map.of("type", "number",
                "description", "数量报工时的不良（报废）数量，可空"));
        props.put("inputQuantity", Map.of("type", "number",
                "description", "数量报工时的投入数量，可空（空则由系统按上道产出推算）"));
        props.put("assigneeId", Map.of("type", "integer",
                "description", "派工/重新派工/更新计划时的负责人员工ID"));
        props.put("assigneeName", Map.of("type", "string",
                "description", "负责人姓名（配合 assigneeId 显示）"));
        props.put("planEndDate", Map.of("type", "string",
                "description", "计划完工日期 yyyy-MM-dd（派工/重新派工/更新计划时可传）"));
        props.put("amount", Map.of("type", "number",
                "description", "金额类型工序的本次金额（会累加到「已确认金额」）"));
        props.put("remark", Map.of("type", "string",
                "description", "备注/原因（取消工序时必须填取消原因）"));
        props.put("inspectQty", Map.of("type", "number",
                "description", "录入质检-抽检的抽样数"));
        props.put("qualifiedQty", Map.of("type", "number",
                "description", "录入质检-全检的合格数"));
        props.put("defectQty", Map.of("type", "number",
                "description", "录入质检的不合格数/判废数"));
        props.put("handleType", Map.of("type", "string",
                "description", "录入质检判定不合格时的处理方式",
                "enum", new ArrayList<>(List.of("补料", "返工回退", "报废"))));
        props.put("handleOperationName", Map.of("type", "string",
                "description", "返工回退的目标工序名称"));
        props.put("handleRemark", Map.of("type", "string",
                "description", "不合格处理备注"));
        props.put("items", Map.of("type", "array",
                "description", "录入质检的逐项实测明细，形如 [{itemName:'外径', measuredValue:'10.02'}]",
                "items", Map.of("type", "object")));
        props.put("receiverEmployeeIds", Map.of("type", "array",
                "description", "发通知的接收人员工ID数组",
                "items", Map.of("type", "integer")));
        props.put("content", Map.of("type", "string",
                "description", "发通知的自定义内容，可空（空则用默认模板）"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("action"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        // 操作人一律取服务端认证上下文，绝不接受 AI 传值（与界面端点一致）
        Long operatorId = scope != null ? scope.getUserId() : null;
        String operatorName = scope != null ? scope.getUsername() : null;
        try {
            String action = SettingSupport.str(params == null ? null : params.get("action"));
            if (action == null) {
                return fail("参数缺失：需要 action（要执行的工序操作）");
            }
            // 1) 定位工序（operationId 优先，其次 workOrderNo + operationName）
            WorkOrderOperation op = resolveOperation(params);
            if (op == null) {
                return fail("未定位到该工序：请提供 operationId（工单工序主键），"
                        + "或 workOrderNo（工单号）+ operationName（工序名称）；若都不确定，请先查该工单的工序清单后重试。");
            }
            return switch (action) {
                case "flow" -> doFlow(op, params, operatorId, operatorName);
                case "rework" -> doRework(op, params, operatorId, operatorName);
                case "scrap" -> doScrap(op, params, operatorId, operatorName);
                case "pushSupplement" -> doPushSupplement(op, params, operatorId, operatorName);
                case "pushReceipt" -> doPushReceipt(op, params, operatorId, operatorName);
                case "qc" -> doQc(op, params, operatorId, operatorName);
                case "notify" -> doNotify(op, params, operatorId, operatorName);
                default -> fail("不支持的操作「" + action + "」。支持：flow 状态流转 / rework 返工 / scrap 报废 / "
                        + "pushSupplement 一键推补料 / pushReceipt 一键推入库 / qc 录入质检 / notify 发通知。");
            };
        } catch (com.erp.config.BusinessException e) {
            // 业务校验不通过（前置状态/顺序/数量/主BOM 等不满足）：属预期拒绝，只记 INFO、不打印堆栈
            log.info("[AI工序操作] 业务拒绝 action={} operator={}: {}",
                    params == null ? null : params.get("action"), operatorName, e.getMessage());
            return fail("执行失败: " + e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[AI工序操作] 执行异常 action={} operator={}: {}", params == null ? null : params.get("action"),
                    operatorName, msg, e);
            return fail("执行失败: " + msg);
        }
    }

    // ==================================================================
    // 7 个动作
    // ==================================================================

    /** 状态流转（/{id}/action）：把 statusAction 与业务参数组装成 payload，交给统一状态机入口 */
    private Map<String, Object> doFlow(WorkOrderOperation op, Map<String, Object> params,
                                       Long operatorId, String operatorName) {
        String statusAction = SettingSupport.str(params.get("statusAction"));
        if (statusAction == null) {
            return fail("执行「状态流转」需要提供 statusAction（如 开工 / 数量报工 / 取消）。可用：" + String.join("、", STATUS_ACTIONS));
        }
        if (!STATUS_ACTIONS.contains(statusAction)) {
            return fail("不支持的工序状态动作「" + statusAction + "」。可用：" + String.join("、", STATUS_ACTIONS));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", statusAction);
        putIfPresent(payload, "quantity", params.get("quantity"));
        putIfPresent(payload, "rejectedQuantity", params.get("rejectedQuantity"));
        putIfPresent(payload, "inputQuantity", params.get("inputQuantity"));
        putIfPresent(payload, "assigneeId", params.get("assigneeId"));
        putIfPresent(payload, "assigneeName", params.get("assigneeName"));
        putIfPresent(payload, "planEndDate", params.get("planEndDate"));
        putIfPresent(payload, "amount", params.get("amount"));
        putIfPresent(payload, "remark", params.get("remark"));

        WorkOrderOperation updated = workOrderOperationService.act(op.getId(), statusAction, payload, operatorId, operatorName);
        return ok("已对" + ref(op) + "执行「" + statusAction + "」，当前状态：" + nz(updated.getOperationStatus()));
    }

    /** 返工（回退确认，/{id}/rework）：只累计回退数量、不改工序状态 */
    private Map<String, Object> doRework(WorkOrderOperation op, Map<String, Object> params,
                                         Long operatorId, String operatorName) {
        BigDecimal qty = SettingSupport.decimal(params.get("quantity"));
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return fail("执行「返工（回退确认）」需要提供大于 0 的 quantity（本次回退数量）。");
        }
        String remark = SettingSupport.str(params.get("remark"));
        WorkOrderOperation updated = workOrderOperationService.confirmRework(op.getId(), qty, remark, operatorId, operatorName);
        return ok("已对" + ref(op) + "确认回退数量 " + plain(qty)
                + "（累计回退 " + plain(updated.getReworkQuantity()) + "）；该动作只登记数量，不改工序状态。");
    }

    /** 报废（判废确认，/{id}/scrap）：只累计判废数量、不改工序状态；判废量决定能否下推补料 */
    private Map<String, Object> doScrap(WorkOrderOperation op, Map<String, Object> params,
                                        Long operatorId, String operatorName) {
        BigDecimal qty = SettingSupport.decimal(params.get("quantity"));
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return fail("执行「报废（判废确认）」需要提供大于 0 的 quantity（本次判废数量）。");
        }
        String remark = SettingSupport.str(params.get("remark"));
        WorkOrderOperation updated = workOrderOperationService.confirmScrap(op.getId(), qty, remark, operatorId, operatorName);
        return ok("已对" + ref(op) + "确认判废数量 " + plain(qty)
                + "（累计判废 " + plain(updated.getScrapQuantity()) + "）；该动作只登记数量，不改工序状态。");
    }

    /** 一键推补料（/{id}/push-supplement）：按尚未下推的判废量 × 主BOM用量生成补料领料单草稿 */
    private Map<String, Object> doPushSupplement(WorkOrderOperation op, Map<String, Object> params,
                                                 Long operatorId, String operatorName) {
        String remark = SettingSupport.str(params.get("remark"));
        String billNo = workOrderOperationService.pushSupplement(op.getId(), remark, operatorId, operatorName);
        return ok("已对" + ref(op) + "下推补料，生成补料领料单（草稿）：" + nz(billNo));
    }

    /** 一键推入库（/{id}/push-receipt）：生成成品入库单草稿，数量不得超过剩余可入库量 */
    private Map<String, Object> doPushReceipt(WorkOrderOperation op, Map<String, Object> params,
                                              Long operatorId, String operatorName) {
        BigDecimal qty = SettingSupport.decimal(params.get("quantity"));
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return fail("执行「一键推入库」需要提供大于 0 的 quantity（本次入库数量）。");
        }
        Map<String, Object> data = workOrderOperationService.pushReceipt(op.getId(), qty, operatorId, operatorName);
        Object billNo = data == null ? null : data.get("billNo");
        return ok("已对" + ref(op) + "发起入库单，生成成品入库单（草稿）：" + (billNo == null ? "" : billNo));
    }

    /** 录入质检（/{id}/qc）：仅「完工待检」可用，按质检模板判定；不合格必须给处理方式 */
    private Map<String, Object> doQc(WorkOrderOperation op, Map<String, Object> params,
                                     Long operatorId, String operatorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "inspectQty", params.get("inspectQty"));
        putIfPresent(payload, "qualifiedQty", params.get("qualifiedQty"));
        putIfPresent(payload, "defectQty", params.get("defectQty"));
        putIfPresent(payload, "handleType", params.get("handleType"));
        putIfPresent(payload, "handleOperationName", params.get("handleOperationName"));
        putIfPresent(payload, "handleRemark", params.get("handleRemark"));
        putIfPresent(payload, "remark", params.get("remark"));
        Object items = params.get("items");
        if (items instanceof List<?> list && !list.isEmpty()) {
            payload.put("items", list);
        }
        Map<String, Object> data = workOrderOperationService.submitInspection(op.getId(), payload, operatorId, operatorName);
        Object result = data == null ? null : data.get("result");
        Object handleBillNo = data == null ? null : data.get("handleBillNo");
        StringBuilder msg = new StringBuilder("已对").append(ref(op)).append("录入质检，判定：")
                .append(result == null ? "" : result);
        if (handleBillNo != null) {
            msg.append("；已生成补料领料单（草稿）：").append(handleBillNo);
        }
        return ok(msg.toString());
    }

    /** 发通知（/{id}/notify）：给所选员工发系统通知（未绑定登录账号的员工会被跳过） */
    private Map<String, Object> doNotify(WorkOrderOperation op, Map<String, Object> params,
                                         Long operatorId, String operatorName) {
        List<Long> receivers = parseIds(params.get("receiverEmployeeIds"));
        if (receivers.isEmpty()) {
            return fail("执行「发通知」需要提供至少一个接收人 receiverEmployeeIds（员工ID数组）。");
        }
        String content = SettingSupport.str(params.get("content"));
        Map<String, Object> data = workOrderOperationService.notifyOperation(op.getId(), receivers, content, operatorId, operatorName);
        Object sent = data == null ? null : data.get("sent");
        Object names = data == null ? null : data.get("receivers");
        return ok("已对" + ref(op) + "发送工序通知：送达 " + (sent == null ? 0 : sent)
                + " 人（" + (names == null ? "" : names) + "）。");
    }

    // ==================================================================
    // 工序定位
    // ==================================================================

    /**
     * 定位工序行：优先 operationId；否则用 workOrderNo + operationName 组合定位。
     * 组合定位时按「工单号 → 工单主键 → 同工单内工序名称」逐级查（同名工序取行号最小的一条）。
     */
    private WorkOrderOperation resolveOperation(Map<String, Object> params) {
        Long operationId = SettingSupport.longVal(params == null ? null : params.get("operationId"));
        if (operationId != null) {
            return workOrderOperationService.getById(operationId);
        }
        String orderNo = SettingSupport.str(params.get("workOrderNo"));
        String operationName = SettingSupport.str(params.get("operationName"));
        if (orderNo == null || operationName == null) {
            return null;
        }
        WorkOrder order = workOrderMapper.selectOne(new QueryWrapper<WorkOrder>()
                .eq("order_no", orderNo).last("LIMIT 1"));
        if (order == null) {
            return null;
        }
        List<WorkOrderOperation> ops = workOrderOperationService.list(new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", order.getOrderId())
                .eq("operation_name", operationName)
                .orderByAsc("line_no"));
        return (ops == null || ops.isEmpty()) ? null : ops.get(0);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 工序的业务化引用（面向用户，不出现内部主键）：如「工单【SO-...】的工序【10 车削】」 */
    private static String ref(WorkOrderOperation op) {
        StringBuilder sb = new StringBuilder("工单【").append(nz(op.getOrderNo())).append("】的工序【");
        if (op.getLineNo() != null) sb.append(op.getLineNo()).append(" ");
        sb.append(nz(op.getOperationName())).append("】");
        return sb.toString();
    }

    /** 只把非空参数放进 payload（服务端对空值的处理与界面一致：没填就不改） */
    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) return;
        if (value instanceof String s && s.isBlank()) return;
        map.put(key, value);
    }

    /** 解析员工ID数组：兼容数组/单值/字符串数字；非法元素跳过 */
    private static List<Long> parseIds(Object raw) {
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                Long v = SettingSupport.longVal(o);
                if (v != null) ids.add(v);
            }
        } else {
            Long v = SettingSupport.longVal(raw);
            if (v != null) ids.add(v);
        }
        return ids;
    }

    /** 数量去尾零显示（1.0000 → 1）；用于面向用户的提示文案 */
    private static String plain(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).stripTrailingZeros().toPlainString();
    }

    /** 空值兜底：返回空串（提示文案里不出现 null） */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("message", message);
        return r;
    }

    private static Map<String, Object> fail(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("message", message);
        return r;
    }
}
