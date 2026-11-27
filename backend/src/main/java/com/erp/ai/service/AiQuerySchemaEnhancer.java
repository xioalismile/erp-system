package com.erp.ai.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.erp.ai.dto.EntityCrudConfig;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.export.annotation.ExportField;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 查询配置「自动注册」增强器（2026-11-22 表达层机制重构 · 立项一期）。
 *
 * <p>目标（用户 2026-11-22 明确要求）：以后新增字段/功能<b>自动注册 AI 工具，不再人工补齐</b>。</p>
 *
 * <p>机制：不再逐条手写 86 个查询配置的展示列/筛选字段，而是启动时从三处"新功能开发天然必经"
 * 的结构性登记自动生成，手工配置降级为「例外覆盖」层：</p>
 * <ol>
 *   <li><b>展示列</b> = 手工展示列 ∪ 派生列 ∪ 实体注解候选（@ExportField 非隐藏、带中文标签、
 *       按登记顺序排列；自动剔除主档 ID/审计列/集合列），总列数设上限；手工列永远在前，
 *       历史修复成果（2026-09-14 全量显示口径修复）不受影响——列只增不改名、不重排。</li>
 *   <li><b>筛选字段</b> = 手工筛选白名单 ∪ 列表页表头筛选登记（BillHeaderFilterSupport，
 *       类型按实体属性校验：日期/数值/文本/ID 各自匹配才开放）；两者皆空时给默认筛选集
 *       （单号/状态/审批状态/关闭状态/日期区间），保证任何新单据"至少能查"。</li>
 *   <li><b>中文名映射</b> = 实体注解标签 ∪ 外键补全键 ∪ 派生列名，随查询结果以 labels 下发，
 *       前端列头不再依赖手工维护的字段名字典。</li>
 * </ol>
 *
 * <p>配套护栏：</p>
 * <ul>
 *   <li>无中文标签的实体业务字段 → 启动 WARN 点名（倒逼新字段按规范起名，见立项登记表设计要点 8）；</li>
 *   <li>生成结果沿用既有两道自检（字段反射校验 + 对库列校验）——自动生成列来自实体注解，
 *       天然通过反射校验；租户库缺列由对库自检兜底。</li>
 * </ul>
 */
@Slf4j
public final class AiQuerySchemaEnhancer {

    private AiQuerySchemaEnhancer() {
    }

    /** 展示列总上限（主键不计入；手工列 + 派生列 + 自动候选合并后截断） */
    private static final int DISPLAY_MAX = 14;

    /** 关键业务口径提权：字段中文名命中即优先进入展示列（日期/状态/金额/合计/数量/收款类） */
    private static final java.util.regex.Pattern KEYWORD_LABEL =
            java.util.regex.Pattern.compile("日期|状态|金额|合计|数量|款|天数");

    /** 技术/审计列：不进 AI 展示列候选 */
    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "createdAt", "updatedAt", "createdBy", "updatedBy", "orgCode",
            "billBeforeStatus", "attachmentUrls", "voidedBy", "voidedAt", "voidReason",
            "closedBy", "closedAt", "closedReason", "approvedBy", "approvedAt", "tenantId");

    /** 补全器回填的显示名 → 中文名（与 AiCrudAgentService.fillForeignDisplayNames 目标键一一对应） */
    private static final Map<String, String> FOREIGN_LABELS = Map.of(
            "customerName", "客户名称",
            "supplierName", "供应商名称",
            "materialName", "物料名称",
            "stockName", "仓库",
            "fromStockName", "调出仓库",
            "toStockName", "调入仓库",
            "warehouseName", "仓库",
            "employeeName", "员工");

    /** 启动增强汇总（供启动日志） */
    public record Summary(int enhanced, int displayExtended, int filterExtended,
                          int defaultFilterOnly, int unlabeledFields, List<String> unlabeledSamples) {
    }

    /** 对全部配置执行自动增强（在既有自检之后调用，幂等可重复执行） */
    public static Summary enhanceAll(Map<String, EntityCrudConfig> configs) {
        int displayExtended = 0;
        int filterExtended = 0;
        int defaultFilterOnly = 0;
        int unlabeled = 0;
        List<String> unlabeledSamples = new ArrayList<>();

        for (Map.Entry<String, EntityCrudConfig> entry : configs.entrySet()) {
            EntityCrudConfig cfg = entry.getValue();
            if (cfg == null || cfg.getEntityClass() == null) {
                continue;
            }
            Map<String, FieldInfo> fields = scanFields(cfg.getEntityClass());

            // ── 1) 展示列：手工 ∪ 派生 ∪ 注解候选（手工在前，只增不改名不重排） ──
            List<String> manual = cfg.getDisplayFields() == null
                    ? new ArrayList<>() : new ArrayList<>(cfg.getDisplayFields());
            LinkedHashSet<String> merged = new LinkedHashSet<>(manual);
            int beforeDisplay = merged.size();
            for (String key : AiDerivedColumns.derivedKeys(entry.getKey())) {
                merged.add(key);
            }
            List<FieldInfo> candidates = new ArrayList<>();
            List<FieldInfo> boosted = new ArrayList<>();
            for (FieldInfo fi : fields.values()) {
                if (fi.exportLabel == null || fi.hidden || fi.dbColumn == null) {
                    continue;
                }
                if (EXCLUDED_FIELDS.contains(fi.name) || fi.name.endsWith("Id") || fi.name.endsWith("By")) {
                    continue;
                }
                if (fi.type == LocalDateTime.class || Collection.class.isAssignableFrom(fi.type)
                        || fi.type.isArray() || Map.class.isAssignableFrom(fi.type)) {
                    continue;
                }
                // 关键业务口径提权（2026-11-22 实测修正）：交货日期这类日期/状态/金额/数量字段
                // 若按导出序号排队，会被联系人等低价值列挤出上限——交期超期这类用户高频口径必须可见
                if (fi.type == LocalDate.class || KEYWORD_LABEL.matcher(fi.exportLabel).find()) {
                    boosted.add(fi);
                } else {
                    candidates.add(fi);
                }
            }
            boosted.sort(Comparator.comparingInt(f -> f.order));
            candidates.sort(Comparator.comparingInt(f -> f.order));
            for (FieldInfo fi : boosted) {
                if (merged.size() >= DISPLAY_MAX) {
                    break;
                }
                merged.add(fi.name);
            }
            for (FieldInfo fi : candidates) {
                if (merged.size() >= DISPLAY_MAX) {
                    break;
                }
                merged.add(fi.name);
            }
            if (merged.size() > beforeDisplay) {
                displayExtended += merged.size() - beforeDisplay;
            }
            cfg.setDisplayFields(new ArrayList<>(merged));

            // ── 2) 筛选字段：手工 ∪ 列表筛选登记（类型匹配校验），皆空给默认筛选集 ──
            Set<String> filters = new LinkedHashSet<>();
            if (cfg.getFilterFields() != null) {
                filters.addAll(cfg.getFilterFields());
            }
            int beforeFilter = filters.size();
            Map<String, BillHeaderFilterSupport.Spec> specs = BillHeaderFilterSupport.specView(
                    decapitalize(cfg.getEntityClass().getSimpleName()));
            for (Map.Entry<String, BillHeaderFilterSupport.Spec> se : specs.entrySet()) {
                FieldInfo fi = fields.get(se.getKey());
                if (fi == null || fi.dbColumn == null) {
                    continue;
                }
                if (typeMatches(fi.type, se.getValue().kind())) {
                    filters.add(se.getKey());
                }
            }
            boolean fellBack = false;
            if (filters.isEmpty()) {
                fellBack = buildDefaultFilters(fields, cfg, filters);
            }
            if (filters.size() > beforeFilter) {
                filterExtended += filters.size() - beforeFilter;
            }
            if (fellBack) {
                defaultFilterOnly++;
            }
            cfg.setFilterFields(new ArrayList<>(filters));

            // ── 3) 中文名映射：注解标签 ∪ 外键补全键 ∪ 派生列名 ──
            Map<String, String> labels = new LinkedHashMap<>();
            for (FieldInfo fi : fields.values()) {
                if (fi.exportLabel != null && !fi.exportLabel.isBlank()) {
                    labels.put(fi.name, fi.exportLabel);
                }
            }
            FOREIGN_LABELS.forEach(labels::putIfAbsent);
            AiDerivedColumns.derivedLabels(entry.getKey()).forEach(labels::putIfAbsent);
            cfg.setLabels(labels);

            // ── 4) 无中文标签的业务字段点名（护栏） ──
            for (FieldInfo fi : fields.values()) {
                if (fi.dbColumn == null || fi.exportLabel != null
                        || EXCLUDED_FIELDS.contains(fi.name)
                        || fi.name.endsWith("Id") || fi.name.endsWith("By")) {
                    continue;
                }
                unlabeled++;
                if (unlabeledSamples.size() < 5) {
                    unlabeledSamples.add(cfg.getEntityClass().getSimpleName() + "." + fi.name);
                }
            }
        }
        return new Summary(configs.size(), displayExtended, filterExtended, defaultFilterOnly,
                unlabeled, unlabeledSamples);
    }

    /** 扫描实体全部字段（含父类），dbColumn != null = 数据库真实列 */
    private static Map<String, FieldInfo> scanFields(Class<?> clazz) {
        Map<String, FieldInfo> out = new LinkedHashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || out.containsKey(f.getName())) {
                    continue;
                }
                FieldInfo fi = new FieldInfo();
                fi.name = f.getName();
                fi.type = f.getType();
                TableId ti = f.getAnnotation(TableId.class);
                TableField tf = f.getAnnotation(TableField.class);
                if (ti != null) {
                    fi.dbColumn = !ti.value().isEmpty() ? ti.value() : fi.name;
                } else if (tf != null && tf.exist()) {
                    fi.dbColumn = !tf.value().isEmpty() ? tf.value() : fi.name;
                }
                ExportField ef = f.getAnnotation(ExportField.class);
                if (ef != null) {
                    fi.exportLabel = ef.label();
                    fi.hidden = ef.hidden();
                    fi.order = ef.order();
                }
                out.put(fi.name, fi);
            }
        }
        return out;
    }

    /** 默认筛选集：状态类 + 单号关键词 + 全部日期字段区间（保证新单据"至少能筛"） */
    private static boolean buildDefaultFilters(Map<String, FieldInfo> fields, EntityCrudConfig cfg,
                                               Set<String> out) {
        int cap = 8;
        for (String k : List.of("status", "approvalStatus", "closeStatus")) {
            FieldInfo fi = fields.get(k);
            if (fi != null && fi.dbColumn != null
                    && (fi.type == String.class || fi.type == Integer.class) && out.size() < cap) {
                out.add(k);
            }
        }
        if (cfg.getNameField() != null) {
            FieldInfo fi = fields.get(cfg.getNameField());
            if (fi != null && fi.dbColumn != null && fi.type == String.class && out.size() < cap) {
                out.add(cfg.getNameField());
            }
        }
        List<String> dateFields = new ArrayList<>();
        for (FieldInfo fi : fields.values()) {
            if (fi.dbColumn != null && fi.type == LocalDate.class && fi.name.endsWith("Date")) {
                dateFields.add(fi.name);
            }
        }
        Collections.sort(dateFields);
        for (String k : dateFields) {
            if (out.size() >= cap) {
                break;
            }
            out.add(k);
        }
        return !out.isEmpty();
    }

    /** 筛选规格类型与实体属性类型必须匹配，杜绝"名字像、类型对不上"的假筛选 */
    private static boolean typeMatches(Class<?> type, BillHeaderFilterSupport.Kind kind) {
        return switch (kind) {
            case EQ_LONG -> type == Long.class || type == Integer.class;
            case EQ_STRING, TEXT -> type == String.class;
            case DATE -> type == LocalDate.class;
            case NUMBER -> type == BigDecimal.class;
        };
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** 字段扫描结果 */
    private static class FieldInfo {
        String name;
        Class<?> type;
        String dbColumn;
        String exportLabel;
        boolean hidden;
        int order = Integer.MAX_VALUE;
    }
}
