package com.erp.ai.tool.impl;

import com.erp.ai.bill.BillActionPermissionResolver;
import com.erp.ai.bill.BillActionRegistry;
import com.erp.ai.bill.BillItemHandler;
import com.erp.ai.bill.BillItemRegistry;
import com.erp.ai.bill.BillTypeIdentityGuard;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写操作工具：修改单据**明细行**的字段（首批接入销售订单：单价/数量/税率/折扣率）。
 *
 * <p><b>一致性</b>：一律走原单据 Service 的整单保存（如 {@code SaleOrderService.saveOrderWithItems}），
 * 不直连子表 —— 金额/税额/合计重算、设置中心各项校验、已生效/已完结不可改，全部与界面一致。
 *
 * <p><b>安全</b>：① patch 只允许适配器声明的白名单字段；② 定位支持 明细行ID 或 行号；
 * ③ 写操作 ⇒ 强制确认卡片（{@link com.erp.ai.tool.ToolRegistry#isWriteTool}）；
 * ④ 权限与单据动作同口径（{@link BillActionPermissionResolver}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillItemUpdateTool implements AiTool {

    private final BillItemRegistry billItemRegistry;
    private final BillActionRegistry billActionRegistry;
    private final BillActionPermissionResolver permissionResolver;
    private final BillTypeIdentityGuard billTypeIdentityGuard;

    @Override
    public String getName() {
        return "bill_item_update";
    }

    @Override
    public String getDescription() {
        return "【写操作，需用户确认】修改某张单据**明细行**的字段（如把某行单价改成 1）。"
                + "调用前先用 bill_item_list 看清行号与现值；用 itemId（明细行ID）或 lineNo（行号）指明改哪一行。"
                + "金额/税额/合计由系统自动重算，不要传。"
                + "各单据允许改的字段：" + describeEditableFields()
                + "。用户没指明是哪一行时，先问清楚再调用，不要猜。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("billType", Map.of("type", "string",
                "description", "单据类型（与 bill_action 同一套枚举）",
                "enum", new ArrayList<>(billItemRegistry.allBillTypes())));
        props.put("billId", Map.of("type", "integer",
                "description", "单据主键的数字 ID（如销售订单的 orderId=258），严禁传单号"));
        props.put("itemId", Map.of("type", "integer",
                "description", "要修改的明细行ID（bill_item_list 结果里的「明细行ID」）；与 lineNo 二选一"));
        props.put("lineNo", Map.of("type", "integer",
                "description", "要修改的明细行号（bill_item_list 结果里的「行号」）；与 itemId 二选一"));
        props.put("patch", Map.of("type", "object",
                "description", "要修改的字段（键=字段英文名，值=新值）。例：{\"unitPrice\": 1}"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("billType", "billId", "patch"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String billType = SettingSupport.str(params == null ? null : params.get("billType"));
            Long billId = SettingSupport.longVal(params == null ? null : params.get("billId"));
            Long itemId = SettingSupport.longVal(params == null ? null : params.get("itemId"));
            Integer lineNo = SettingSupport.integer(params == null ? null : params.get("lineNo"));
            Object patchObj = params == null ? null : params.get("patch");
            if (billType == null || billId == null || !(patchObj instanceof Map)) {
                return fail("参数缺失：需要 billType、billId（主键数字 ID）和 patch（要改的字段）");
            }
            if (itemId == null && lineNo == null) {
                return fail("请指明要改哪一行：传 itemId（明细行ID）或 lineNo（行号），可先用 bill_item_list 查看行清单");
            }
            BillItemHandler handler = billItemRegistry.get(billType);
            if (handler == null) {
                return fail("【" + billActionRegistry.label(billType) + "】暂不支持编辑明细行。当前已支持：" + supportedList());
            }
            String deny = permissionResolver.check(billType, "update", scope);
            if (deny != null) {
                return fail(deny);
            }
            // 身份校验：一表多身份的单据（生产退料单 / 生产补料单）必须核对记录自身类型，
            // 否则可能把另一种单据的明细行改掉（整单保存会重算金额并落库）
            String identityDeny = billTypeIdentityGuard.check(billType, billId);
            if (identityDeny != null) {
                return fail(identityDeny);
            }
            Map<String, Object> patch = (Map<String, Object>) patchObj;
            if (patch.isEmpty()) {
                return fail("patch 为空：请给出要修改的字段，例如 {\"unitPrice\": 1}");
            }
            List<String> editable = handler.editableFields();
            List<String> illegal = patch.keySet().stream().filter(k -> !editable.contains(k)).toList();
            if (!illegal.isEmpty()) {
                return fail("不支持修改字段 " + illegal + "；本单据允许改：" + editable
                        + "（金额/税额/合计由系统自动重算）");
            }
            Object bill = handler.loadBill(billId);
            if (bill == null) {
                return fail("没找到该单据（billId=" + billId + "），请先用查询工具确认主键");
            }
            List<?> items = handler.loadItems(billId);
            if (items.isEmpty()) {
                return fail("该单据还没有明细行，无法修改；请先在界面上补录物料明细");
            }
            // 定位目标行
            Object target = null;
            int targetIdx = -1;
            for (int i = 0; i < items.size(); i++) {
                Object it = items.get(i);
                boolean hit = itemId != null
                        ? itemId.equals(handler.itemId(it))
                        : lineNo.equals(handler.lineNo(it));
                if (hit) {
                    target = it;
                    targetIdx = i;
                    break;
                }
            }
            if (target == null && itemId == null && lineNo != null) {
                // 兜底：单据明细无行序字段（lineNo 恒为 null）时，把传来的「行号」当作行下标定位
                // （bill_item_list 展示的「行号」= 下标+1，与行号一致）
                boolean anyLineNo = items.stream().anyMatch(it -> handler.lineNo(it) != null);
                if (!anyLineNo && lineNo >= 1 && lineNo <= items.size()) {
                    target = items.get(lineNo - 1);
                    targetIdx = lineNo - 1;
                }
            }
            if (target == null) {
                return fail("没找到该明细行（" + (itemId != null ? "itemId=" + itemId : "lineNo=" + lineNo) + "）。"
                        + "该单现有行号：" + lineNos(handler, items) + "，可先用 bill_item_list 核对");
            }
            Map<String, Object> before = handler.toItemMap(target, targetIdx + 1);
            Map<String, Object> billBefore = handler.billSummary(bill);
            try {
                handler.applyPatch(target, patch);
            } catch (IllegalArgumentException e) {
                return fail(e.getMessage());
            }
            // 整单保存（与原 Service 同链路：重算 + 校验 + 状态检查）
            handler.save(bill, items);

            // 回读：整单保存是"全删全插"，主键会变 → 用行号定位同一行
            Integer savedLineNo = handler.lineNo(target) != null ? handler.lineNo(target) : targetIdx + 1;
            Object billAfter = handler.loadBill(billId);
            List<?> itemsAfter = handler.loadItems(billId);
            Object targetAfter = null;
            int idxAfter = -1;
            for (int i = 0; i < itemsAfter.size(); i++) {
                if (savedLineNo.equals(handler.lineNo(itemsAfter.get(i)))) {
                    targetAfter = itemsAfter.get(i);
                    idxAfter = i;
                    break;
                }
            }
            // 兜底：单据明细无行序字段（lineNo 为 null）时，按"行下标"回定位
            // （整单保存为全删全插但保持行顺序，同一下标即同一业务行）
            if (targetAfter == null && targetIdx >= 0 && targetIdx < itemsAfter.size()) {
                targetAfter = itemsAfter.get(targetIdx);
                idxAfter = targetIdx;
                savedLineNo = targetIdx + 1;
            }
            Map<String, Object> after = targetAfter != null
                    ? handler.toItemMap(targetAfter, idxAfter + 1) : Map.of();
            Map<String, Object> billAfterSummary = billAfter != null ? handler.billSummary(billAfter) : Map.of();

            StringBuilder sb = new StringBuilder("已修改【").append(billActionRegistry.label(billType)).append(" ")
                    .append(handler.billNo(billAfter != null ? billAfter : bill)).append("】第 ").append(savedLineNo).append(" 行：");
            for (String k : patch.keySet()) {
                sb.append(changeText(fieldCn(k), before.get(fieldCn(k)), after.get(fieldCn(k)))).append("；");
            }
            sb.append("该行金额 ").append(before.get("金额")).append(" → ").append(after.get("金额"))
                    .append("，单据合计 ").append(billBefore.get("合计金额")).append(" → ").append(billAfterSummary.get("合计金额"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", sb.toString());
            result.put("data", targetAfter != null ? List.of(after) : List.of());
            result.put("billSummary", billAfterSummary);
            log.info("[AI明细行修改] 成功 billType={} billId={} line={} patch={}", billType, billId, savedLineNo, patch);
            return result;
        } catch (Exception e) {
            // 原样把后端业务规则（状态不允许/单价校验等）带给 AI，由它如实转述
            log.warn("[AI明细行修改] 失败: {}", e.getMessage());
            return fail("修改明细失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 字段英文名 → 中文（与 bill_item_list 的列名保持一致，便于对照；新增单据的自定义可改列在此补登记） */
    private String fieldCn(String field) {
        return switch (field) {
            case "unitPrice" -> "单价";
            case "price" -> "单价";
            case "quantity" -> "数量";
            case "taxRate" -> "税率";
            case "discountRate" -> "折扣率";
            case "requiredQuantity" -> "需领数量";
            case "qualifiedQuantity" -> "合格数量";
            case "rejectedQuantity" -> "不合格数量";
            case "transferQty" -> "转移数量";
            case "scrapQty" -> "报废数量";
            case "reworkQty" -> "返修数量";
            case "amount" -> "金额";
            case "returnAmount" -> "退回金额";
            case "processingFee" -> "加工费";
            case "scrapRate" -> "损耗率";
            case "laborHours" -> "工时";
            default -> field;
        };
    }

    private String changeText(String cn, Object before, Object after) {
        return cn + " " + (before == null ? "-" : before) + " → " + (after == null ? "-" : after);
    }

    private String lineNos(BillItemHandler handler, List<?> items) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Object it : items) {
            if (sb.length() > 0) sb.append("、");
            Integer ln = handler.lineNo(it);
            sb.append(ln != null ? ln : i);
            i++;
        }
        return sb.toString();
    }

    private String describeEditableFields() {
        StringBuilder sb = new StringBuilder();
        for (String t : billItemRegistry.allBillTypes()) {
            BillItemHandler h = billItemRegistry.get(t);
            if (sb.length() > 0) sb.append("；");
            sb.append(billActionRegistry.label(t)).append("(").append(t).append(")=").append(h.editableFields());
        }
        return sb.length() == 0 ? "（暂无）" : sb.toString();
    }

    private String supportedList() {
        StringBuilder sb = new StringBuilder();
        for (String t : billItemRegistry.allBillTypes()) {
            if (sb.length() > 0) sb.append("、");
            sb.append(billActionRegistry.label(t)).append("(").append(t).append(")");
        }
        return sb.length() == 0 ? "（暂无）" : sb.toString();
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }
}
