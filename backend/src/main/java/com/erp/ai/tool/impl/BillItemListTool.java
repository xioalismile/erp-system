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
 * 只读工具：查看某张单据的**明细行**（物料/数量/单价/折扣/税率/金额…）。
 *
 * <p>用途：AI 改明细前必须先看到"有哪些行、当前值是多少"；也是"这张单为什么合计为 0"的答案来源。
 * 只读，不消耗写开关；权限与单据动作同口径（仅走菜单判定）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillItemListTool implements AiTool {

    private final BillItemRegistry billItemRegistry;
    private final BillActionRegistry billActionRegistry;
    private final BillActionPermissionResolver permissionResolver;
    private final BillTypeIdentityGuard billTypeIdentityGuard;

    @Override
    public String getName() {
        return "bill_item_list";
    }

    @Override
    public String getDescription() {
        return "【只读】查看某张单据的明细行（子表）：行号、物料编码/名称、数量、单价、折扣率、税率、金额等。"
                + "用户问「这张单的明细/有哪些物料/单价多少」、或你要修改明细行之前，都先调用它。"
                + "billType 与单据动作同一套枚举；billId 必须是**单据主键的数字 ID**（严禁传单号）。"
                + "当前已支持明细行读取的单据类型：" + billItemRegistry.allBillTypes()
                + "；其余单据会明确返回「暂不支持」。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("billType", Map.of("type", "string",
                "description", "单据类型（与 bill_action 同一套枚举）",
                "enum", new ArrayList<>(billItemRegistry.allBillTypes())));
        props.put("billId", Map.of("type", "integer",
                "description", "单据主键的数字 ID（如销售订单的 orderId=258），严禁传单号"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("billType", "billId"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String billType = SettingSupport.str(params == null ? null : params.get("billType"));
            Long billId = SettingSupport.longVal(params == null ? null : params.get("billId"));
            if (billType == null || billId == null) {
                return fail("参数缺失：需要 billType 和 billId（单据主键数字 ID）");
            }
            BillItemHandler handler = billItemRegistry.get(billType);
            if (handler == null) {
                return fail("【" + billActionRegistry.label(billType) + "】暂不支持查看/编辑明细行。"
                        + "当前已支持：" + supportedList());
            }
            String deny = permissionResolver.check(billType, null, scope);
            if (deny != null) {
                return fail(deny);
            }
            // 身份校验：一表多身份的单据（生产退料单 / 生产补料单）不得读到另一种单据的明细
            String identityDeny = billTypeIdentityGuard.check(billType, billId);
            if (identityDeny != null) {
                return fail(identityDeny);
            }
            Object bill = handler.loadBill(billId);
            if (bill == null) {
                return fail("没找到该单据（billId=" + billId + "），请先用查询工具确认主键");
            }
            List<?> items = handler.loadItems(billId);
            String billNo = handler.billNo(bill);
            List<Map<String, Object>> rows = new ArrayList<>();
            StringBuilder sb = new StringBuilder("【").append(billActionRegistry.label(billType)).append(" ")
                    .append(billNo == null ? "" : billNo).append("】共 ").append(items.size()).append(" 行明细");
            if (items.isEmpty()) {
                sb.append("（该单还没有明细行，需要先在界面上补录物料）");
            } else {
                sb.append("：\n");
                int i = 1;
                for (Object it : items) {
                    Map<String, Object> row = handler.toItemMap(it, i);
                    rows.add(row);
                    sb.append("- 第").append(row.get("行号")).append("行 ")
                            .append(row.get("物料编码")).append(" ").append(row.get("物料名称"))
                            .append("｜数量 ").append(row.get("数量"))
                            .append("｜单价 ").append(row.get("单价"))
                            .append("｜金额 ").append(row.get("金额")).append("\n");
                    i++;
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", sb.toString());
            result.put("data", rows);
            result.put("total", rows.size());
            return result;
        } catch (Exception e) {
            log.error("[AI明细行读取] 执行异常", e);
            return fail("查看明细失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
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
