package com.erp.common.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.util.SortHelper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 列表查询统一件（2026-09-16 「列表页查询口径通用化」P0）。
 *
 * <p>背景：各列表页/Controller 各自手写查询条件，导致三类反复出现的缺陷：</p>
 * <ol>
 *   <li><b>OR 未分组</b>：{@code wrapper.like("a",kw).or().like("b",kw)} 之后追加的状态/数据权限/EXISTS
 *       会被 AND 优先级吃掉（命中第一支的行绕过全部后续条件，含越权）——
 *       2026-09-16 采购合同事故的根因。这里用 {@link #applyKeywordGroup} 从机制上封死。</li>
 *   <li><b>分页参数名两套</b>：前端发 {@code page}、后端收 {@code current}（或反之）→ 翻页永远停在第 1 页。
 *       这里用 {@link #resolvePage} 统一解析（{@code current} 优先、旧名 {@code page} 兜底）。</li>
 *   <li><b>筛选维度口径不一</b>：日期区间 / 客户 / 供应商 / 员工（销售员、采购员）各页写法各异，
 *       甚至渲染了控件却没接后端 → 点了没反应。这里提供统一入口，未接入的维度不渲染。</li>
 * </ol>
 *
 * <p>安全约定（必须遵守）：</p>
 * <ul>
 *   <li>排序必须经 {@link #applySort}（内部走 {@link SortHelper} 的列名白名单），否则形成注入面；</li>
 *   <li>关键词列名、日期列名、ID 列名一律是 <b>代码常量</b>，不接受前端传列名；</li>
 *   <li>所有值一律参数绑定，本类不做任何字符串拼接 SQL；</li>
 *   <li>非法/空白入参一律 <b>忽略该条件</b>，绝不阻断列表查询。</li>
 * </ul>
 */
public final class ListQuerySupport {

    private ListQuerySupport() {
    }

    /** 默认每页条数 */
    public static final int DEFAULT_SIZE = 20;
    /** 每页条数上限（防一次拉爆） */
    public static final int MAX_SIZE = 500;

    /**
     * 统一分页解析：{@code current} 优先，旧参数名 {@code page} 兜底（保证老前端/旧链接不失效）。
     *
     * @return {@code [current, size]}，current ≥ 1、1 ≤ size ≤ {@link #MAX_SIZE}
     */
    public static int[] resolvePage(Integer current, Integer page, Integer size) {
        Integer c = (current != null && current > 0) ? current
                : ((page != null && page > 0) ? page : 1);
        int s = (size != null && size > 0) ? size : DEFAULT_SIZE;
        if (s > MAX_SIZE) s = MAX_SIZE;
        return new int[]{c, s};
    }

    /**
     * 排序入口（列名白名单校验，防注入）。内部委托 {@link SortHelper}，与全站既有排序口径一致。
     *
     * @return true = 已应用；false = 未传/不在白名单 → 调用方走默认排序
     */
    public static boolean applySort(QueryWrapper<?> wrapper, String sortBy, String sortOrder, Set<String> sortableColumns) {
        return SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, sortableColumns);
    }

    /**
     * 多列关键词 <b>强制成组</b>：生成 {@code AND (c1 LIKE ? OR c2 LIKE ? ...)}。
     *
     * <p>这是本项目唯一允许的"多列关键词"写法 —— 禁止任何页面再手写平铺的 {@code or()}。</p>
     *
     * @param columns 关键词匹配的列（代码常量，如 "order_no", "customer_short_name"）
     */
    public static void applyKeywordGroup(QueryWrapper<?> wrapper, String keyword, String... columns) {
        if (wrapper == null || keyword == null || keyword.isBlank() || columns == null) return;
        List<String> valid = new ArrayList<>();
        for (String c : columns) {
            if (c != null && !c.isBlank()) valid.add(c);
        }
        // 无可匹配列时直接返回：避免生成空的嵌套分组（会产出 AND () 这类非法 SQL）
        if (valid.isEmpty()) return;
        wrapper.and(inner -> {
            inner.like(valid.get(0), keyword);
            for (int i = 1; i < valid.size(); i++) {
                inner.or().like(valid.get(i), keyword);
            }
        });
    }

    /** "名称 → 关联主键"解析的默认条数上限（关键词按客户名称搜索时调用方传入，防止宽泛模糊词整表回捞） */
    public static final int ID_LOOKUP_LIMIT = 500;

    /**
     * 多列关键词 + 关联主键 <b>强制成组</b>：生成
     * {@code AND (c1 LIKE ? OR c2 LIKE ? ... OR idColumn IN (?,?,...))}。
     *
     * <p><b>为什么需要这个方法</b>：列表页说明书 2.2 节把关键词契约定义为「单据编号 + 客户名称」，
     * 但销售类单据表（报价 / 合同 / 出库 / 退货 / 发票）<b>只有 {@code customer_id}、没有客户名称列</b>
     * （{@code sale_contract.customer_short_name} 全表为空）。此前各页只能把关键词收敛成"只搜单号"，
     * 提示文案与真实能力不一致；把"客户名称 → 客户主键"的解析结果作为<b>同一 OR 分组里的一支</b>并入，
     * 即可在不加冗余列、不改表的前提下兑现说明书契约，同时不破坏 {@link #applyKeywordGroup}
     * 已确立的"成组"约束（平铺 OR 会让命中单号的行绕过状态筛选与数据权限）。</p>
     *
     * <p>边界（与说明书一致）：</p>
     * <ul>
     *   <li>关键词为空 → 不加任何条件（调用方无需先判空）；</li>
     *   <li>主键集合为空 → 退化为纯文本列匹配，<b>不生成空的嵌套分组</b>；</li>
     *   <li>文本列为空但主键集合非空 → 只按关联主键匹配；</li>
     *   <li>两者都为空 → 直接返回（不产出 {@code AND ()} 这类非法 SQL）。</li>
     * </ul>
     *
     * @param textColumns 文本匹配列（代码常量，不接受前端传列名）
     * @param idColumn    关联主键列名（代码常量，如 {@code "customer_id"}）
     * @param ids         由名称解析出的关联主键集合（可为空）
     */
    public static void applyKeywordGroupWithIds(QueryWrapper<?> wrapper, String keyword,
                                                List<String> textColumns, String idColumn, List<Long> ids) {
        if (wrapper == null || keyword == null || keyword.isBlank()) return;
        List<String> valid = new ArrayList<>();
        if (textColumns != null) {
            for (String c : textColumns) {
                if (c != null && !c.isBlank()) valid.add(c);
            }
        }
        List<Long> validIds = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) validIds.add(id);
            }
        }
        boolean byIds = idColumn != null && !idColumn.isBlank() && !validIds.isEmpty();
        // 既无可匹配文本列、也没有可用关联主键 → 不生成条件（避免 AND () 这类非法 SQL）
        if (valid.isEmpty() && !byIds) return;
        wrapper.and(inner -> {
            boolean started = false;
            for (String c : valid) {
                if (!started) {
                    inner.like(c, keyword);
                    started = true;
                } else {
                    inner.or().like(c, keyword);
                }
            }
            if (byIds) {
                if (started) {
                    inner.or().in(idColumn, validIds);
                } else {
                    inner.in(idColumn, validIds);
                }
            }
        });
    }

    /**
     * 日期区间过滤（含端点）。列名由各页声明（如 order_date / contract_date / receipt_date）。
     * 日期非法或为空 → 忽略该端点。
     */
    public static void applyDateRange(QueryWrapper<?> wrapper, String column, String startDate, String endDate) {
        if (wrapper == null || column == null || column.isBlank()) return;
        LocalDate sd = parseDate(startDate);
        LocalDate ed = parseDate(endDate);
        if (sd != null) wrapper.ge(column, sd);
        if (ed != null) wrapper.le(column, ed);
    }

    /**
     * 单个 ID 维度过滤（客户 / 供应商 / 员工等）。
     * 列名必须是代码常量；值非数字/为空 → 忽略该条件。
     */
    public static void applyId(QueryWrapper<?> wrapper, String column, String value) {
        if (wrapper == null || column == null || column.isBlank()) return;
        Long id = parseLong(value);
        if (id != null) wrapper.eq(column, id);
    }

    /**
     * 员工维度：统一入参 {@code employeeId} → 映射到本页实际列
     * （销售员 {@code saler_id} / 采购员 {@code buyer_id} / 经手人 {@code handler_id} / 申请人 {@code applicant_id} / {@code employee_id}）。
     *
     * <p>列名口径全站不统一，但**不改表、不改实体**：映射写在各页 Service 里。</p>
     */
    public static void applyEmployee(QueryWrapper<?> wrapper, String employeeId, String column) {
        applyId(wrapper, column, employeeId);
    }

    /** 宽松日期解析（ISO yyyy-MM-dd）；非法返回 null */
    public static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /** 宽松长整型解析；非法返回 null */
    public static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
