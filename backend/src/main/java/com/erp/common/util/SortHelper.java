package com.erp.common.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.erp.common.util.SortHelper;

/**
 * 列表排序统一工具（2026-08-18 全系统排序适配）。
 *
 * <p>安全机制：前端传来的 sortBy 必须命中白名单（数据库列名集合）才生效，
 * 杜绝 SQL 注入；未命中或未传时返回 false，由调用方保持原默认排序。</p>
 *
 * <p>约定：</p>
 * <ul>
 *     <li>前端传驼峰 prop（如 orderNo），本工具自动转下划线列名（order_no）再校验</li>
 *     <li>sortOrder 仅接受 asc / desc（大小写不敏感），其它值视为未排序</li>
 *     <li>各 ServiceImpl 定义自己的 SORTABLE_COLUMNS 白名单常量</li>
 *     <li>展示字段（如 customerName 表里实际是 customer_short_name）通过 ALIASES 兜底映射</li>
 * </ul>
 */
public final class SortHelper {

    private SortHelper() {}

    /**
     * 常见展示字段 → 真实列名的候选映射（按优先级）。
     * 前端列 prop 多为关联对象名称（customerName/salerName 等），
     * 单据表实际冗余存储的是简称/编号/ID 列；排序时按候选顺序找第一个命中白名单的列。
     */
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("customer_name", List.of("customer_short_name", "customer_no", "customer_id")),
            Map.entry("saler_name", List.of("saler_id")),
            Map.entry("buyer_name", List.of("buyer_id")),
            Map.entry("supplier_name", List.of("supplier_short_name", "supplier_no", "supplier_id")),
            Map.entry("employee_name", List.of("employee_id")),
            Map.entry("dept_name", List.of("department_id", "dept_id")),
            Map.entry("department_name", List.of("department_id", "dept_id")),
            Map.entry("material_name", List.of("material_id", "material_code")),
            Map.entry("warehouse_name", List.of("warehouse_id")),
            Map.entry("unit_name", List.of("unit_id")),
            Map.entry("currency_name", List.of("currency_id")),
            Map.entry("account_name", List.of("account_id")),
            Map.entry("creator_name", List.of("created_by", "create_by")),
            Map.entry("operator_name", List.of("operator_id")),
            Map.entry("handler_name", List.of("handler_id")),
            Map.entry("manager_name", List.of("manager_id")),
            Map.entry("work_center_name", List.of("work_center_id")),
            Map.entry("workstation_name", List.of("workstation_id")),
            Map.entry("location_name", List.of("location_id")),
            Map.entry("batch_no", List.of("batch_id")),
            // 采购入库单：实体字段 inDate 映射的真实列是 receipt_date（前端列 prop 为 inDate）
            Map.entry("in_date", List.of("receipt_date")),
            // 采购发票：实体字段 taxTotalAmount（价税合计）映射的真实列是 net_amount
            Map.entry("tax_total_amount", List.of("net_amount"))
    );

    /** 驼峰转下划线：orderNo -> order_no */
    public static String camelToSnake(String s) {
        if (s == null || s.isBlank()) return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 将用户排序应用到 QueryWrapper。
     *
     * @return true = 白名单校验通过并已应用；false = 未传/非法/不在白名单，调用方走默认排序
     */
    public static boolean applyToWrapper(QueryWrapper<?> wrapper, String sortBy, String sortOrder, Set<String> sortableColumns) {
        if (wrapper == null || sortableColumns == null || sortableColumns.isEmpty()) return false;
        if (sortBy == null || sortBy.isBlank()) return false;
        String col = resolveColumn(camelToSnake(sortBy.trim()), sortableColumns);
        if (col == null) return false;
        boolean asc = "asc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
        boolean desc = "desc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
        if (!asc && !desc) return false;
        wrapper.orderBy(true, asc, col);
        return true;
    }

    /** 列名解析：直接命中白名单，否则按 ALIASES 候选顺序找第一个命中白名单的真实列 */
    private static String resolveColumn(String col, Set<String> sortableColumns) {
        if (sortableColumns.contains(col)) return col;
        List<String> candidates = ALIASES.get(col);
        if (candidates == null) return null;
        for (String c : candidates) {
            if (sortableColumns.contains(c)) return c;
        }
        return null;
    }

    /** 便捷方法：构建列名时做基础合法性兜底（字母数字下划线），双保险 */
    public static boolean isValidColumn(String col) {
        return col != null && col.matches("[a-z_][a-z0-9_]*");
    }

    /**
     * 空值安全的“最新在前”默认排序（2026-09-11 全系统默认排序修复）。
     *
     * <p>背景：PostgreSQL 中 {@code ORDER BY created_at DESC} 默认把 NULL 排在最前
     * （NULLS FIRST）。历史导入/直插数据的 created_at 为空时会被顶到列表最上方，
     * 表现为“默认排序不是最新的单据在最前面”。</p>
     *
     * <p>生成 SQL：{@code ORDER BY created_at is null ASC, created_at DESC}
     * —— created_at 为空的排到最后，其余按创建时间倒序。</p>
     */
    public static void orderByNewest(QueryWrapper<?> wrapper) {
        if (wrapper == null) return;
        wrapper.orderByAsc("created_at is null");
        wrapper.orderByDesc("created_at");
    }

    /**
     * 空值安全的“最新在前”默认排序 + 主键倒序兜底（同一时间戳也能稳定排序）。
     *
     * @param entityClass 查询实体类，用于解析真实主键列；解析不到时退化为无兜底排序
     */
    public static void orderByNewest(QueryWrapper<?> wrapper, Class<?> entityClass) {
        orderByNewest(wrapper);
        if (wrapper == null || entityClass == null) return;
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo != null && tableInfo.getKeyColumn() != null && !tableInfo.getKeyColumn().isBlank()) {
            wrapper.orderByDesc(tableInfo.getKeyColumn());
        }
    }
}