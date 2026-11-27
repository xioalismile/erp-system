package com.erp.ai.tool.support;

import java.util.List;
import java.util.Set;

/**
 * AI 设置类工具共享常量与取值工具。
 *
 * 设计原则：所有白名单（可配置分组、可配实体、字段类型）均写死在这里，
 * AI 不能扩展取值范围；黑名单（AI 自身配置）不可配置关闭。
 */
public final class SettingSupport {

    private SettingSupport() {
    }

    /** sys_config 可读写分组白名单（= 设置中心数据驱动页面实际使用的分组） */
    public static final Set<String> CONFIG_GROUPS = Set.of(
            "basic", "sys_param", "purchase", "sale", "inventory", "subcontract",
            "employee_perm", "menu_name", "ui", "notification", "batch_no", "logistics", "approval"
    );

    /**
     * AI 不得读写的配置前缀：AI 自身的工具开关 / 数据政策 / 配额。
     * 防止 AI 给自己提权或关闭数据脱敏，硬约束不可配置。
     */
    public static final List<String> AI_OWNED_KEY_PREFIXES = List.of("ai.");

    public static boolean isAiOwnedKey(String key) {
        if (key == null || key.isBlank()) return false;
        return AI_OWNED_KEY_PREFIXES.stream().anyMatch(key::startsWith);
    }

    /**
     * 已下线的配置项：设置中心界面不再渲染，后端代码亦无消费方（行保留在库中仅为历史兼容）。
     * 客户/供应商编号已统一由「设置中心 → 自动编号设置」的「客户档案 / 供应商档案」规则管理，
     * 系统参数里的旧开关与专用页重复，故 AI 也不再列出/修改，避免误报"可以改开关"。
     */
    public static final Set<String> DEPRECATED_CONFIG_KEYS =
            Set.of("sys.auto.customer.no", "sys.auto.supplier.no");

    public static boolean isDeprecatedKey(String key) {
        return key != null && DEPRECATED_CONFIG_KEYS.contains(key);
    }

    /** 自定义字段可配置实体白名单（与设置中心 CustomFieldConfig.vue 的 entityList 一致） */
    public static final Set<String> CUSTOM_FIELD_ENTITY_TYPES = Set.of(
            "customer", "supplier", "material", "opportunity",
            "sale_quote", "sale_quote_item", "sale_contract", "sale_contract_item",
            "sale_order", "sale_order_item", "sale_delivery", "sale_delivery_item",
            "sale_return", "sale_return_item", "sale_invoice", "sale_invoice_item",
            "purchase_request", "purchase_request_item", "purchase_order", "purchase_order_item",
            "purchase_inbound", "purchase_inbound_item", "purchase_return", "purchase_return_item",
            "purchase_contract", "purchase_contract_item", "purchase_invoice", "purchase_invoice_item",
            "stock_transfer", "stock_transfer_item", "other_receipt", "other_receipt_item",
            "other_issue", "other_issue_item", "material_requisition", "material_requisition_item",
            "stock_check", "stock_check_item",
            "produce_assemble", "produce_disassemble",
            "bom", "bom_item", "free_sample_order", "free_sample_order_item"
    );

    /** 实体类型 → 中文名（AI 回复与确认卡片展示用） */
    public static final java.util.Map<String, String> ENTITY_TYPE_LABELS = java.util.Map.ofEntries(
            java.util.Map.entry("customer", "客户"), java.util.Map.entry("supplier", "供应商"),
            java.util.Map.entry("material", "物料"), java.util.Map.entry("opportunity", "商机"),
            java.util.Map.entry("sale_quote", "销售报价单"), java.util.Map.entry("sale_quote_item", "销售报价明细"),
            java.util.Map.entry("sale_contract", "销售合同"), java.util.Map.entry("sale_contract_item", "销售合同明细"),
            java.util.Map.entry("sale_order", "销售订单"), java.util.Map.entry("sale_order_item", "销售订单明细"),
            java.util.Map.entry("sale_delivery", "销售出库单"), java.util.Map.entry("sale_delivery_item", "销售出库明细"),
            java.util.Map.entry("sale_return", "销售退货单"), java.util.Map.entry("sale_return_item", "销售退货明细"),
            java.util.Map.entry("sale_invoice", "销售发票"), java.util.Map.entry("sale_invoice_item", "销售发票明细"),
            java.util.Map.entry("purchase_request", "请购单"), java.util.Map.entry("purchase_request_item", "请购单明细"),
            java.util.Map.entry("purchase_order", "采购订单"), java.util.Map.entry("purchase_order_item", "采购订单明细"),
            java.util.Map.entry("purchase_inbound", "采购入库单"), java.util.Map.entry("purchase_inbound_item", "采购入库明细"),
            java.util.Map.entry("purchase_return", "采购退货单"), java.util.Map.entry("purchase_return_item", "采购退货明细"),
            java.util.Map.entry("purchase_contract", "采购合同"), java.util.Map.entry("purchase_contract_item", "采购合同明细"),
            java.util.Map.entry("purchase_invoice", "采购发票"), java.util.Map.entry("purchase_invoice_item", "采购发票明细"),
            java.util.Map.entry("stock_transfer", "调拨单"), java.util.Map.entry("stock_transfer_item", "调拨单明细"),
            java.util.Map.entry("other_receipt", "其他入库单"), java.util.Map.entry("other_receipt_item", "其他入库明细"),
            java.util.Map.entry("other_issue", "其他出库单"), java.util.Map.entry("other_issue_item", "其他出库明细"),
            java.util.Map.entry("material_requisition", "领料单"), java.util.Map.entry("material_requisition_item", "领料单明细"),
            java.util.Map.entry("stock_check", "盘点单"), java.util.Map.entry("stock_check_item", "盘点单明细"),
            java.util.Map.entry("produce_assemble", "组装单"), java.util.Map.entry("produce_disassemble", "拆卸单"),
            java.util.Map.entry("bom", "BOM"), java.util.Map.entry("bom_item", "BOM明细"),
            java.util.Map.entry("free_sample_order", "打样单"), java.util.Map.entry("free_sample_order_item", "打样单明细")
    );

    /** 自定义字段类型白名单 */
    public static final Set<String> CUSTOM_FIELD_TYPES = Set.of(
            "text", "textarea", "number", "select", "date", "datetime", "switch", "formula"
    );

    /** 字段类型 → 中文名（确认卡片展示用） */
    public static final java.util.Map<String, String> FIELD_TYPE_LABELS = java.util.Map.of(
            "text", "单行文本", "textarea", "多行文本", "number", "数字", "select", "下拉选择",
            "date", "日期", "datetime", "日期时间", "switch", "开关", "formula", "公式计算"
    );

    public static String entityLabel(String entityType) {
        if (entityType == null) return "?";
        return ENTITY_TYPE_LABELS.getOrDefault(entityType, entityType);
    }

    public static String fieldTypeLabel(String fieldType) {
        if (fieldType == null) return "?";
        return FIELD_TYPE_LABELS.getOrDefault(fieldType, fieldType);
    }

    /** 安全取字符串（去空白，空串转 null） */
    public static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /** 取布尔（兼容 true/false、"true"/"1"/"是"/1 等） */
    public static boolean bool(Object o, boolean defaultValue) {
        if (o == null) return defaultValue;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(o).trim().toLowerCase();
        if (s.isEmpty()) return defaultValue;
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "是".equals(s);
    }

    /** 取整数 */
    public static Integer integer(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.valueOf(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 取长整数（ID 字段用） */
    public static Long longVal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 取 BigDecimal */
    public static java.math.BigDecimal decimal(Object o) {
        if (o == null) return null;
        if (o instanceof java.math.BigDecimal bd) return bd;
        try {
            return new java.math.BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
