package com.erp.report.custom.core;

import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 自定义报表 ↔ 自定义字段（sys_custom_field / sys_custom_field_value）桥接支持。
 *
 * <p>职责：
 * <ol>
 *   <li>把「数据源表名」映射到自定义字段的 entity_type（两者并不总是相等，如 unit_of_measure ↔ unit），
 *       映射唯一来源是 {@link CustomFieldService#resolveTableName(String)}；</li>
 *   <li>提供某张表上启用的自定义字段清单（报表引用名 = {@code cf_ + field_key}），供元数据输出与白名单校验；</li>
 *   <li>生成自定义字段在 SQL 中的取值表达式：值统一存文本，数值/日期按类型 CAST，
 *       并用正则守卫把脏数据降级为 NULL（避免整条查询 500）。</li>
 * </ol>
 *
 * <p>安全约定：entity_type / field_key 均来自数据库中的字段定义（非请求参数），
 * 且拼接前用 {@link #SAFE_KEY} 做二次断言，与 CustomFieldServiceImpl 入库校验同源；
 * 用户输入值一律由 SqlBuilder 以 {@code ?} 占位传入，本类不接触用户值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCustomFieldSupport {

    /** 报表字段引用前缀：自定义字段一律以 cf_ 开头，与系统字段（@TableField）不会冲突 */
    public static final String CF_PREFIX = "cf_";
    /** field_key / entity_type 安全字符白名单（与 CustomFieldServiceImpl.saveField 入库校验同源） */
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    /** 数值文本正则（守卫 CAST：不匹配的行给 NULL，不让 PG 抛 invalid input syntax） */
    private static final String NUMERIC_RE = "'^-?[0-9]+(\\.[0-9]+)?$'";
    /** 日期文本正则 */
    private static final String DATE_RE = "'^[0-9]{4}-[0-9]{2}-[0-9]{2}'";

    private final CustomFieldService customFieldService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 一个自定义字段在报表侧的元数据 */
    public static class CfField {
        /** 业务表名（= 报表数据源 key / join 目标表名） */
        public final String table;
        /** 自定义字段 entity_type */
        public final String entityType;
        /** 原始 field_key */
        public final String fieldKey;
        /** 报表字段引用名：cf_{fieldKey} */
        public final String ref;
        public final String label;
        /** 报表内统一类型：string/number/date/datetime/select/bool */
        public final String type;
        public final List<Map<String, String>> options;

        CfField(String table, String entityType, String fieldKey, String label,
                String type, List<Map<String, String>> options) {
            this.table = table;
            this.entityType = entityType;
            this.fieldKey = fieldKey;
            this.ref = CF_PREFIX + fieldKey;
            this.label = label;
            this.type = type;
            this.options = options;
        }
    }

    /** 是否自定义字段引用名 */
    public boolean isCustomRef(String field) {
        return field != null && field.startsWith(CF_PREFIX) && field.length() > CF_PREFIX.length();
    }

    /** 某张业务表上启用的自定义字段（无定义 / 表不支持自定义字段 → 空列表） */
    public List<CfField> fieldsOfTable(String table) {
        if (table == null || table.isBlank()) return Collections.emptyList();
        String entityType = customFieldService.resolveEntityType(table);
        if (entityType == null) return Collections.emptyList();
        List<CustomField> defs;
        try {
            defs = customFieldService.listEnabledByEntity(entityType);
        } catch (Exception e) {
            // 定义表缺失 / 租户库未建表等场景降级为空，绝不阻断报表
            log.warn("[自定义报表] 读取自定义字段定义失败，按无自定义字段处理: table={}, err={}", table, e.getMessage());
            return Collections.emptyList();
        }
        if (defs == null || defs.isEmpty()) return Collections.emptyList();
        List<CfField> out = new ArrayList<>(defs.size());
        for (CustomField cf : defs) {
            if (cf == null || cf.getFieldKey() == null || cf.getFieldLabel() == null) continue;
            if (!SAFE_KEY.matcher(entityType).matches() || !SAFE_KEY.matcher(cf.getFieldKey()).matches()) {
                log.warn("[自定义报表] 跳过非法标识的自定义字段: entityType={}, fieldKey={}", entityType, cf.getFieldKey());
                continue;
            }
            out.add(new CfField(table, entityType, cf.getFieldKey(), cf.getFieldLabel(),
                    mapType(cf.getFieldType()), parseOptions(cf.getFieldOptions())));
        }
        return out;
    }

    /** 精确取某表上的某个自定义字段（ref 形如 cf_xxx）；不存在返回 null */
    public CfField find(String table, String ref) {
        if (!isCustomRef(ref)) return null;
        for (CfField f : fieldsOfTable(table)) {
            if (f.ref.equals(ref)) return f;
        }
        return null;
    }

    /**
     * 自定义字段元数据指纹：字段新增/改名/启停/删除都会变化，供前端元数据缓存失效。
     * 单条轻量查询（sys_custom_field 为小表），失败降级为空指纹。
     */
    public String fingerprint() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS cnt,"
                            + " COALESCE(MAX(COALESCE(updated_at, created_at))::text, '') AS ts"
                            + " FROM sys_custom_field WHERE is_enabled = 1");
            return "cf" + row.get("cnt") + "-" + row.get("ts");
        } catch (Exception e) {
            log.warn("[自定义报表] 自定义字段指纹计算失败，按无自定义字段处理: {}", e.getMessage());
            return "cf0";
        }
    }

    /** 数值取值表达式：脏数据 → NULL（不抛 invalid input syntax） */
    public String numericExpr(String alias) {
        return "(CASE WHEN " + alias + ".field_value ~ " + NUMERIC_RE
                + " THEN " + alias + ".field_value::numeric END)";
    }

    /** 日期取值表达式（date/datetime 统一按 timestamp 比较，PG 可隐式兼容 date） */
    public String dateExpr(String alias) {
        return "(CASE WHEN " + alias + ".field_value ~ " + DATE_RE
                + " THEN " + alias + ".field_value::timestamp END)";
    }

    /** 按字段类型生成取值表达式 */
    public String valueExpr(CfField f, String alias) {
        return switch (f.type) {
            case "number" -> numericExpr(alias);
            case "date", "datetime" -> dateExpr(alias);
            default -> alias + ".field_value";
        };
    }

    /** 自定义字段 field_type → 报表内统一类型（与 ApprovalController.mapCustomFieldType 同口径，另补 datetime/switch） */
    private String mapType(String fieldType) {
        if (fieldType == null) return "string";
        return switch (fieldType) {
            case "number", "formula", "amount" -> "number";
            case "date" -> "date";
            case "datetime" -> "datetime";
            case "select" -> "select";
            case "switch" -> "bool";
            default -> "string";
        };
    }

    /** select 选项 JSON（[{"label":"x","value":"y"}]）解析；失败返回空列表 */
    private List<Map<String, String>> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return Collections.emptyList();
        try {
            List<Map<String, String>> parsed = objectMapper.readValue(optionsJson,
                    new TypeReference<List<Map<String, String>>>() {
                    });
            Map<String, String> seen = new LinkedHashMap<>();
            for (Map<String, String> o : parsed) {
                if (o == null) continue;
                String v = o.get("value");
                if (v == null) continue;
                seen.putIfAbsent(v, o.get("label") == null ? v : o.get("label"));
            }
            List<Map<String, String>> out = new ArrayList<>(seen.size());
            seen.forEach((v, l) -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("value", v);
                m.put("label", l);
                out.add(m);
            });
            return out;
        } catch (Exception e) {
            log.warn("[自定义报表] 解析自定义字段选项失败，按无选项处理: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
