package com.erp.common.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 单据列表「按自定义字段搜索」公共件。
 *
 * <p>背景：基础资料列表走 {@code BaseCrudController}，其 {@code applyCustomSearch} 已支持 customSearch 入参；
 * 但各业务单据列表（销售 / 采购 / 库存 / 生产 / 委外 / 财务）都是各自手写的 Controller，不继承基类，
 * 于是"设置中心里勾了可搜索"在这些页面上一直搜不了。本类把这套 EXISTS 子查询抽出来，
 * 各单据 Controller 只需在自己的 page 方法里加一行调用即可。</p>
 *
 * <p>语义与 BaseCrudController 完全一致：
 * <ul>
 *   <li>数据源是 EAV 值表 {@code sys_custom_field_value}（唯一 source of truth），不依赖业务表 ext_data 列</li>
 *   <li>fieldKey 必须过白名单正则（防注入）；值一律参数绑定</li>
 *   <li>任何异常 / 非法输入一律忽略该条件，<b>绝不阻断列表查询</b></li>
 * </ul>
 *
 * <p>用法（单据 Controller 的 page 方法内）：
 * <pre>
 * CustomFieldSearchHelper.apply(qw, "sale_order", customSearch, "order_id");
 * </pre>
 */
@Slf4j
public final class CustomFieldSearchHelper {

    private static final ObjectMapper OM = new ObjectMapper();
    /** 字段标识白名单：字母开头 + 字母数字下划线（与 sys_custom_field.field_key 生成规则一致） */
    private static final String KEY_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*$";

    private CustomFieldSearchHelper() {
    }

    /**
     * 把 customSearch（JSON：{fieldKey: 值}）追加为 EXISTS 条件。
     *
     * @param qw           列表查询条件（QueryWrapper）
     * @param entityType   sys_custom_field.entity_type，如 sale_order
     * @param customSearch 前端传入的 JSON 字符串，可为空（为空则不加任何条件）
     * @param idColumn     业务表主键列名，如 order_id（必须与真实列名逐字一致）
     */
    public static void apply(QueryWrapper<?> qw, String entityType, String customSearch, String idColumn) {
        if (qw == null || entityType == null || entityType.isBlank()
                || customSearch == null || customSearch.isBlank()
                || idColumn == null || idColumn.isBlank()) {
            return;
        }
        Map<String, String> conditions;
        try {
            conditions = OM.readValue(customSearch, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return; // JSON 非法：忽略自定义搜索条件，不影响原有查询
        }
        if (conditions == null || conditions.isEmpty()) return;
        for (Map.Entry<String, String> en : conditions.entrySet()) {
            String key = en.getKey();
            String val = en.getValue();
            if (key == null || val == null || val.isBlank() || !key.matches(KEY_PATTERN)) continue;
            qw.apply(
                "EXISTS (SELECT 1 FROM sys_custom_field_value v WHERE v.entity_type = {0} AND v.entity_id = "
                    + idColumn + " AND v.field_key = '" + key + "' AND v.field_value ILIKE {1})",
                entityType, "%" + val + "%"
            );
        }
    }

    /**
     * 业务单据列表「按自定义字段搜索」时，各单据实体 → 真实主键列名映射。
     * 抽在这里是为了避免每个 Controller 各写一遍、写错列名（写错会 SQL 报错）。
     *
     * <p>⚠ 列名以真实库为准（psql 查 pg_index 核实过），新增实体时必须同样核实，
     * 并与 {@code CustomFieldServiceImpl.resolveIdColumn} 保持一致。</p>
     *
     * @return 主键列名；未登记的单据返回 null（调用方应跳过自定义字段搜索）
     */
    public static String idColumnOf(String entityType) {
        if (entityType == null) return null;
        return switch (entityType) {
            // 销售
            case "sale_quote" -> "quote_id";
            case "sale_contract" -> "contract_id";
            case "sale_order" -> "order_id";
            case "sale_delivery" -> "delivery_id";
            case "sale_return" -> "return_id";
            case "sale_invoice" -> "invoice_id";
            // 采购（采购入库单真实表是 purchase_receipt）
            case "purchase_request" -> "request_id";
            case "purchase_order" -> "order_id";
            case "purchase_inbound" -> "receipt_id";
            case "purchase_return" -> "return_id";
            case "purchase_contract" -> "contract_id";
            case "purchase_invoice" -> "invoice_id";
            // 库存
            case "stock_transfer" -> "transfer_id";
            case "other_receipt" -> "receipt_id";
            case "other_issue" -> "issue_id";
            case "material_requisition" -> "requisition_id";
            case "stock_check" -> "check_id";
            // 生产（组装/拆卸单主键就是 id）
            case "produce_assemble" -> "id";
            case "produce_disassemble" -> "id";
            // 生产工单：真实表 production_order，主键列 order_id（与 CustomFieldServiceImpl.resolveIdColumn 保持一致）
            case "production_order" -> "order_id";
            // 生产入库单 / 报工单（2026-11-24 列表页按自定义字段搜索）
            case "production_receipt" -> "receipt_id";
            case "operation_report" -> "report_id";
            // 委外模块表头实体（主键均为 id；与 CustomFieldServiceImpl.resolveIdColumn 保持一致）
            case "subcontract_order", "subcontract_pick", "subcontract_inbound",
                 "subcontract_return", "subcontract_supplement" -> "id";
            // 其他
            case "bom" -> "bom_id";
            // 打样单（sample_order）主键是 sample_id；免费样品订单（free_sample_order）主键是 order_id
            case "sample_order" -> "sample_id";
            case "free_sample_order" -> "order_id";
            // CRM
            case "opportunity" -> "opportunity_id";
            default -> null;
        };
    }
}
