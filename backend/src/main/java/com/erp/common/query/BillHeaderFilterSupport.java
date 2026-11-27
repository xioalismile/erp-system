package com.erp.common.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单据列表「表头字段过滤」统一件（2026-10-14 列表页筛选条件自定义 一期）。
 *
 * <p>背景：列表页筛选条件开放自定义后，用户可把销售订单编辑页的任意表头字段
 * 勾为搜索条件（备注 / 部门 / 币种 / 价税合计区间 …）。汇总列表查询此前只认
 * 关键词 / 状态 / 日期 / 客户 / 销售员 几个固定入参，自定义字段搜到后端对不上
 * 就是"点了没反应"。本件给列表查询补一个<b>字段白名单 + 参数化</b>的通用过滤入口。</p>
 *
 * <p>入参约定（与单据明细报表引擎 BillDetailReportEngine 完全同键，前端一套条件定义
 * 汇总 / 明细两种模式通用）：</p>
 * <ul>
 *   <li>文本 / 精确：{@code f_<key>}（前端已剥掉 f_ 前缀传入本类）</li>
 *   <li>日期区间：{@code f_<key>From} / {@code f_<key>To}（含端点）</li>
 *   <li>数值区间：{@code f_<key>Min} / {@code f_<key>Max}</li>
 * </ul>
 *
 * <p>安全约定（与 {@link ListQuerySupport} 同一口径）：</p>
 * <ul>
 *   <li>列名一律来自本类登记的<b>代码常量白名单</b>，绝不接受前端传列名；</li>
 *   <li>所有值一律参数绑定，本类不做任何字符串拼接 SQL；</li>
 *   <li>非法 / 空白入参（非数字、非日期、白名单外字段）一律<b>忽略该条件</b>，绝不阻断列表查询。</li>
 * </ul>
 */
public final class BillHeaderFilterSupport {

    private BillHeaderFilterSupport() {
    }

    /** 过滤值形态 */
    public enum Kind {
        /** 文本模糊（LIKE %v%） */
        TEXT,
        /** 长整型精确（主档 / 外键 ID） */
        EQ_LONG,
        /** 字符串精确（枚举类，如单据状态） */
        EQ_STRING,
        /** 日期区间（f_KFrom / f_KTo） */
        DATE,
        /** 数值区间（f_KMin / f_KMax） */
        NUMBER
    }

    /** 单个字段的过滤规格：列名（代码常量）+ 形态 */
    public record Spec(String column, Kind kind) {}

    /** 实体 → (字段键 → 规格)；LinkedHashMap 保证应用顺序稳定 */
    private static final Map<String, Map<String, Spec>> REGISTRY = new LinkedHashMap<>();

    /**
     * 登记某实体的字段过滤白名单（应用启动期由各实体注册一次；重复注册整体覆盖）。
     */
    public static void register(String entity, Map<String, Spec> specs) {
        if (entity != null && specs != null && !specs.isEmpty()) {
            REGISTRY.put(entity, specs);
        }
    }

    /**
     * 只读视图（2026-11-22 AI 查询筛选白名单自动注册 · 立项一期）。
     *
     * <p>供 AI 查询层读取"该单据在列表页开放了哪些筛选条件"——两种登记合并
     * （属性名登记优先，列名登记补缺），未登记返回空 Map。仅读取元数据，
     * 不改变 {@link #apply} 的执行路径；登记内容仍是代码常量白名单。</p>
     */
    public static Map<String, Spec> specView(String entity) {
        if (entity == null) return Collections.emptyMap();
        Map<String, Spec> out = new LinkedHashMap<>();
        Map<String, Spec> prop = PROP_REGISTRY.get(entity);
        if (prop != null) {
            out.putAll(prop);
        }
        Map<String, Spec> col = REGISTRY.get(entity);
        if (col != null) {
            col.forEach(out::putIfAbsent);
        }
        return Collections.unmodifiableMap(out);
    }

    // ==================== 属性名注册（2026-10-14 全面推广新增） ====================

    /** 实体 → (字段键 → 规格)，Spec.column 优先按「实体属性名」解析列名，解析不到再原样当列名 */
    private static final Map<String, Map<String, Spec>> PROP_REGISTRY = new LinkedHashMap<>();
    private static final Map<String, Class<?>> PROP_ENTITY = new HashMap<>();
    /** 属性名 → 列名 解析缓存（首次使用时解析；空 map = 解析失败，此后不再重试） */
    private static final Map<String, Map<String, String>> PROP_COLUMNS = new ConcurrentHashMap<>();

    /**
     * 按「实体属性名」登记过滤白名单（全面推广批次的推荐入口）。
     *
     * <p>与 {@link #register(String, Map)} 的区别：Spec.column 先被当作实体属性名，
     * 由 MyBatis-Plus 元数据在首次使用时解析成真实列名 —— 避免 30+ 个单据逐一手写列名
     * 造成笔误（写错列名 = 该条件静默失效或 SQL 报错）。解析不到同名属性时
     * （如按名称筛选但列是外键 ID 的场景），Spec.column 原样作为列名使用。</p>
     */
    public static void registerProps(String entity, Class<?> entityClass, Map<String, Spec> byProperty) {
        if (entity != null && entityClass != null && byProperty != null && !byProperty.isEmpty()) {
            PROP_REGISTRY.put(entity, new LinkedHashMap<>(byProperty));
            PROP_ENTITY.put(entity, entityClass);
            PROP_COLUMNS.remove(entity);
        }
    }

    /** 属性名 → 列名（懒解析 + 缓存；失败缓存空 map，绝不阻断查询） */
    private static Map<String, String> resolveColumns(String entity) {
        Map<String, String> cached = PROP_COLUMNS.get(entity);
        if (cached != null) return cached;
        Class<?> clazz = PROP_ENTITY.get(entity);
        Map<String, String> m = new HashMap<>();
        if (clazz != null) {
            try {
                TableInfo ti = TableInfoHelper.getTableInfo(clazz);
                if (ti != null) {
                    if (ti.getKeyProperty() != null && ti.getKeyColumn() != null) {
                        m.put(ti.getKeyProperty(), ti.getKeyColumn());
                    }
                    for (TableFieldInfo tf : ti.getFieldList()) {
                        m.put(tf.getProperty(), tf.getColumn());
                    }
                }
            } catch (Throwable ignore) {
                // 元数据不可用：留空 map，条件全部忽略，行为与未登记一致
            }
        }
        Map<String, String> imm = Collections.unmodifiableMap(m);
        PROP_COLUMNS.put(entity, imm);
        return imm;
    }

    /**
     * 控制器统一提取（全面推广批次）：只挑 f_ 前缀参数、键剥掉前缀、空白值丢弃。
     * 各控制器不再各写一段 forEach。
     */
    public static Map<String, String> extractFilters(Map<String, String> allParams) {
        Map<String, String> out = new HashMap<>();
        if (allParams == null) return out;
        for (Map.Entry<String, String> e : allParams.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k != null && k.startsWith("f_") && v != null && !v.isBlank()) {
                out.put(k.substring(2), v);
            }
        }
        return out;
    }

    /**
     * 把 f_* 过滤参数翻译为 QueryWrapper 条件。
     *
     * @param wrapper 目标查询（调用方继续追加数据权限 / 自定义字段搜索等条件均安全）
     * @param entity  实体注册名（未登记 = 什么也不做，行为与改造前一致）
     * @param filters 键为字段键（不含 f_ 前缀）、值为用户输入；可为 null
     */
    public static void apply(QueryWrapper<?> wrapper, String entity, Map<String, String> filters) {
        if (wrapper == null || entity == null || filters == null || filters.isEmpty()) return;
        Map<String, Spec> specs = REGISTRY.get(entity);
        if (specs != null) {
            for (Map.Entry<String, Spec> e : specs.entrySet()) {
                applySpec(wrapper, e.getKey(), e.getValue(), filters);
            }
        }
        // 属性名注册：列名优先按实体属性名解析；解析不到 → 原样当列名（登记处"外键直写列名"口径，
        // 仍是代码常量、非前端输入）。元数据整体不可用（解析结果为空）时全部忽略，绝不带病查询。
        Map<String, Spec> propSpecs = PROP_REGISTRY.get(entity);
        if (propSpecs != null && !propSpecs.isEmpty()) {
            Map<String, String> cols = resolveColumns(entity);
            if (!cols.isEmpty()) {
                for (Map.Entry<String, Spec> e : propSpecs.entrySet()) {
                    Spec spec = e.getValue();
                    String col = cols.containsKey(spec.column()) ? cols.get(spec.column()) : spec.column();
                    applySpec(wrapper, e.getKey(), new Spec(col, spec.kind()), filters);
                }
            }
        }
    }

    /** 应用单条规格（col == null 或 spec == null = 忽略该条件） */
    private static void applySpec(QueryWrapper<?> wrapper, String key, Spec spec, Map<String, String> filters) {
        if (spec == null) return;
        String col = spec.column();
        Kind kind = spec.kind();
        switch (kind) {
            case TEXT -> {
                String v = trimToNull(filters.get(key));
                if (v != null) wrapper.like(col, v);
            }
            case EQ_LONG -> {
                String v = trimToNull(filters.get(key));
                Long id = parseLongOrNull(v);
                if (id != null) wrapper.eq(col, id);
            }
            case EQ_STRING -> {
                String v = trimToNull(filters.get(key));
                if (v != null) wrapper.eq(col, v);
            }
            case DATE -> {
                LocalDate from = parseDateOrNull(filters.get(key + "From"));
                LocalDate to = parseDateOrNull(filters.get(key + "To"));
                if (from != null) wrapper.ge(col, from);
                if (to != null) wrapper.le(col, to);
            }
            case NUMBER -> {
                BigDecimal min = parseDecimalOrNull(filters.get(key + "Min"));
                BigDecimal max = parseDecimalOrNull(filters.get(key + "Max"));
                if (min != null) wrapper.ge(col, min);
                if (max != null) wrapper.le(col, max);
            }
            default -> { /* 未登记形态：忽略 */ }
        }
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static Long parseLongOrNull(String v) {
        if (v == null) return null;
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimalOrNull(String v) {
        if (v == null) return null;
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return null;
        // 只接受 ISO 日期（YYYY-MM-DD）；时间戳形式截断到日期部分（容忍前端误传带时间）
        if (s.length() > 10 && s.charAt(10) == 'T') s = s.substring(0, 10);
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
