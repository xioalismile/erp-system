package com.erp.masterdata.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.erp.export.annotation.ExportField;
import com.erp.masterdata.entity.Material;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 货品（物料）查重规则服务。
 *
 * <p>规则来源：设置中心「系统参数设置 → 界面显示 → 货品查重字段」对应的 sys_config 键
 * {@link #CFG_KEY}，值为字段标识 JSON 数组，例如 {@code ["materialCode","materialName"]}。</p>
 *
 * <p><b>语义</b>：所选字段<b>各自</b>不允许重复（OR 语义）——默认「物料编码 + 物料名称」，
 * 与改造前（编码唯一 + 名称查重 + 导入"编码优先/名称兜底"）逐条一致；
 * <b>清空配置 = 不查重</b>（唯一"彻底关闭"方式）。</p>
 *
 * <p><b>可选字段</b>：物料档案全部系统字段（有 {@code @ExportField} 且未标 hidden、且为真实表列）
 * + 全部已启用自定义字段（formula 只读计算列除外），选项动态生成、不落库。</p>
 *
 * <p><b>安全</b>：参与 SQL 的列名只能来自 {@link #selectableFields()} 白名单（由实体注解生成），
 * 配置里出现的未知/非法/已停用字段一律丢弃；所有取值查询均走参数化占位符，不做字符串拼接。</p>
 *
 * <p><b>降级</b>：配置缺失 / boolean 时代遗留值 / JSON 解析失败 → 一律回落默认
 * 「物料编码 + 物料名称」（= 改造前行为），绝不因配置问题拦错或报错。</p>
 */
@Slf4j
@Service
public class MaterialDedupService {

    /** 设置中心：货品查重字段（sys_config.config_key） */
    public static final String CFG_KEY = "sys.spec.duplicate.check";
    /** 自定义字段值所属实体标识 */
    public static final String ENTITY_TYPE = "material";

    /** 默认查重字段：物料编码 + 物料名称（= 改造前行为） */
    public static final List<String> DEFAULT_FIELDS = List.of("materialCode", "materialName");

    /** 不参与查重的系统字段：图片 URL 查重无意义（同一张图可用于多个物料） */
    private static final Set<String> EXCLUDED_FIELDS = Set.of("imageUrls");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final MaterialService materialService;
    private final CustomFieldService customFieldService;

    /** 实体字段读取缓存：fieldKey -> Field */
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

    public MaterialDedupService(JdbcTemplate jdbcTemplate,
                               MaterialService materialService,
                               CustomFieldService customFieldService) {
        this.jdbcTemplate = jdbcTemplate;
        this.materialService = materialService;
        this.customFieldService = customFieldService;
    }

    /** 可查重字段定义 */
    public static class DedupField {
        /** 字段标识：系统字段=camelCase（如 materialCode）；自定义字段=custom_N */
        public final String fieldKey;
        /** 中文标签 */
        public final String label;
        /** 数据库列名；自定义字段为 null（值存 EAV 表 sys_custom_field_value） */
        public final String column;
        /** 是否自定义字段 */
        public final boolean custom;

        public DedupField(String fieldKey, String label, String column, boolean custom) {
            this.fieldKey = fieldKey;
            this.label = label;
            this.column = column;
            this.custom = custom;
        }
    }

    // ==================== 字段清单 ====================

    /** 全部可查重字段（系统字段在前、自定义字段在后），保持插入顺序 */
    public LinkedHashMap<String, DedupField> selectableFields() {
        LinkedHashMap<String, DedupField> map = new LinkedHashMap<>();
        for (Field f : Material.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            ExportField ef = f.getAnnotation(ExportField.class);
            if (ef == null || ef.hidden()) continue;
            TableField tf = f.getAnnotation(TableField.class);
            if (tf != null && !tf.exist()) continue;
            if (EXCLUDED_FIELDS.contains(f.getName())) continue;
            String column = (tf != null && !tf.value().isEmpty()) ? tf.value() : camelToSnake(f.getName());
            map.put(f.getName(), new DedupField(f.getName(), ef.label(), column, false));
        }
        try {
            for (CustomField cf : customFieldService.listEnabledByEntity(ENTITY_TYPE)) {
                if (cf.getFieldKey() == null || cf.getFieldKey().isBlank()) continue;
                // formula 为只读计算列（导入模板同样不含），不参与查重
                if ("formula".equalsIgnoreCase(cf.getFieldType())) continue;
                String label = (cf.getFieldLabel() != null && !cf.getFieldLabel().isBlank())
                        ? cf.getFieldLabel() : cf.getFieldKey();
                map.put(cf.getFieldKey(), new DedupField(cf.getFieldKey(), label, null, true));
            }
        } catch (Exception e) {
            // 自定义字段读取失败只影响自定义字段选项，不影响系统字段查重
            log.warn("[货品查重] 读取物料自定义字段失败，本次仅启用系统字段: {}", e.getMessage());
        }
        return map;
    }

    /** 当前生效的查重字段；返回空列表 = 不查重 */
    public List<DedupField> activeFields() {
        LinkedHashMap<String, DedupField> all = selectableFields();
        List<DedupField> result = new ArrayList<>();
        for (String key : readFieldKeys()) {
            DedupField d = all.get(key);
            if (d != null) {
                result.add(d);
            } else if (key != null && !key.isBlank()) {
                // 字段被删除/停用/标识非法：自动丢弃，绝不因配置问题报错
                log.debug("[货品查重] 忽略无效查重字段: {}", key);
            }
        }
        return result;
    }

    /** 是否启用查重 */
    public boolean isEnabled() {
        return !activeFields().isEmpty();
    }

    /** 设置中心下拉选项：[{value,label,group}] */
    public List<Map<String, Object>> listFieldOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        for (DedupField d : selectableFields().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", d.fieldKey);
            m.put("label", d.label);
            m.put("group", d.custom ? "自定义字段" : "系统字段");
            options.add(m);
        }
        return options;
    }

    // ==================== 查重校验 ====================

    /**
     * 组合唯一（AND）：物料保存/编辑时校验。
     * 勾选字段（编码除外）中非空值全部相同的物料才算重复，任一字段不同即不重复；空字段不参与组合。
     * 物料编码单独唯一由 MaterialController.save/update 的硬校验负责（编码撞单绝不放过）。
     *
     * @param entity        待保存物料（系统字段）
     * @param customValues  本次提交的自定义字段值（fieldKey -> value）；保存/编辑时由前端随主表一并传入
     * @param excludeId     编辑时排除自身（新增传 null）
     */
    public String checkUnique(Material entity, Map<String, Object> customValues, Long excludeId) {
        if (entity == null) return null;
        List<DedupField> fields = activeFields().stream()
                .filter(d -> !"materialCode".equals(d.fieldKey))
                .toList();
        if (fields.isEmpty()) return null;
        String key = combinationKey(entity, customValues, fields);
        if (key == null) return null;   // 全部字段为空 → 无组合，不查重
        Map<String, Long> index = buildCombinationIndex();
        Long hitId = index.get(key);
        if (hitId != null && !hitId.equals(excludeId)) {
            List<String> hitLabels = new ArrayList<>();
            for (DedupField d : fields) {
                String v = dedupValue(entity, customValues, d);
                if (v == null || v.isBlank()) continue;
                hitLabels.add(d.label + "[" + v.trim() + "]");
            }
            return "【设置中心-货品查重】" + String.join(" + ", hitLabels) + " 组合已存在，不允许保存";
        }
        return null;
    }

    /**
     * 批量查询：某查重字段在库中已存在的值 → 命中的物料ID列表（物料导入预检查/确认共用）。
     * 系统字段查主表，自定义字段查 EAV 值表；空值不参与匹配。
     */
    public Map<String, List<Long>> findExistingIds(DedupField field, Collection<String> values) {
        Map<String, List<Long>> result = new HashMap<>();
        if (field == null || values == null || values.isEmpty()) return result;
        List<String> vals = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (vals.isEmpty()) return result;

        if (field.custom) {
            String placeholders = String.join(",", Collections.nCopies(vals.size(), "?"));
            String sql = "SELECT entity_id, field_value FROM sys_custom_field_value "
                    + "WHERE entity_type = ? AND field_key = ? AND field_value IN (" + placeholders + ")";
            List<Object> args = new ArrayList<>();
            args.add(ENTITY_TYPE);
            args.add(field.fieldKey);
            args.addAll(vals);
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
                for (Map<String, Object> row : rows) {
                    Object v = row.get("field_value");
                    Object id = row.get("entity_id");
                    if (v == null || id == null) continue;
                    result.computeIfAbsent(String.valueOf(v), k -> new ArrayList<>())
                            .add(Long.valueOf(String.valueOf(id)));
                }
            } catch (Exception e) {
                log.warn("[货品查重] 批量查询自定义字段值失败 field={}, err={}", field.fieldKey, e.getMessage());
            }
            return result;
        }

        List<Material> list = materialService.list(new QueryWrapper<Material>()
                .in(field.column, new ArrayList<>(vals))
                .orderByAsc("material_id"));
        if (list == null) return result;
        for (Material m : list) {
            String v = fieldValue(m, field.fieldKey);
            if (v == null || v.isBlank()) continue;
            result.computeIfAbsent(v.trim(), k -> new ArrayList<>()).add(m.getMaterialId());
        }
        return result;
    }

    // ==================== 组合唯一（AND）====================

    /**
     * 组合唯一：构造库中现有物料的「组合键 → 物料ID」映射。
     * 组合键 = 勾选字段（编码除外）中非空值的 "fieldKey=value" 按字段顺序拼接；
     * 全部字段为空 → 无键（不参与组合）。空字段不参与组合，编码不参与组合（编码单独唯一）。
     */
    public Map<String, Long> buildCombinationIndex() {
        List<DedupField> fields = activeFields().stream()
                .filter(d -> !"materialCode".equals(d.fieldKey))
                .toList();
        if (fields.isEmpty()) return Collections.emptyMap();
        List<Material> all = materialService.list();
        if (all == null || all.isEmpty()) return Collections.emptyMap();
        Map<Long, Map<String, String>> customByEntity = loadCustomValuesByEntity(fields);
        Map<String, Long> index = new LinkedHashMap<>();
        for (Material m : all) {
            String key = combinationKey(m, customByEntity.get(m.getMaterialId()), fields);
            if (key != null) index.putIfAbsent(key, m.getMaterialId());
        }
        return index;
    }

    /** 组合键：勾选字段（编码除外）非空值按字段顺序拼接；全空返回 null。 */
    public String combinationKey(Material m, Map<String, ?> customValues, List<DedupField> fields) {
        List<String> parts = new ArrayList<>();
        for (DedupField d : fields) {
            String v = dedupValue(m, customValues, d);
            if (v == null || v.isBlank()) continue;
            parts.add(d.fieldKey + "=" + v.trim());
        }
        return parts.isEmpty() ? null : String.join("||", parts);
    }

    /** 取某查重字段的 trim 后非空值；空/空白 → null（系统字段读实体，自定义字段读 customValues）。 */
    private String dedupValue(Material m, Map<String, ?> customValues, DedupField d) {
        String v;
        if (d.custom) {
            Object raw = (customValues == null) ? null : customValues.get(d.fieldKey);
            v = (raw == null) ? null : String.valueOf(raw).trim();
            if (v != null && v.isEmpty()) v = null;
        } else {
            v = fieldValue(m, d.fieldKey);
        }
        return v;
    }

    /** 批量加载物料自定义字段值（entityId -> fieldKey -> value），仅勾选的自定义字段。 */
    private Map<Long, Map<String, String>> loadCustomValuesByEntity(List<DedupField> fields) {
        List<DedupField> customFields = fields.stream().filter(d -> d.custom).toList();
        if (customFields.isEmpty()) return Collections.emptyMap();
        List<String> keys = customFields.stream().map(d -> d.fieldKey).toList();
        String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(ENTITY_TYPE);
        args.addAll(keys);
        String sql = "SELECT entity_id, field_key, field_value FROM sys_custom_field_value "
                + "WHERE entity_type = ? AND field_key IN (" + placeholders + ")";
        Map<Long, Map<String, String>> result = new HashMap<>();
        try {
            for (Map<String, Object> row : jdbcTemplate.queryForList(sql, args.toArray())) {
                Object id = row.get("entity_id");
                Object k = row.get("field_key");
                Object v = row.get("field_value");
                if (id == null || k == null || v == null) continue;
                result.computeIfAbsent(Long.valueOf(String.valueOf(id)), x -> new HashMap<>())
                        .put(String.valueOf(k), String.valueOf(v).trim());
            }
        } catch (Exception e) {
            log.warn("[货品查重] 批量加载自定义字段值失败: {}", e.getMessage());
        }
        return result;
    }

    /** 读取物料实体的系统字段值（trim 后返回；未知字段返回 null） */
    public String fieldValue(Material m, String fieldKey) {
        if (m == null || fieldKey == null) return null;
        try {
            Field f = fieldCache.computeIfAbsent(fieldKey, k -> {
                try {
                    Field fld = Material.class.getDeclaredField(k);
                    fld.setAccessible(true);
                    return fld;
                } catch (Exception e) {
                    return null;
                }
            });
            if (f == null) return null;
            Object v = f.get(m);
            if (v == null) return null;
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 读取配置中的查重字段标识。
     * <p>缺失 / boolean 时代遗留值 / JSON 解析失败 → 默认「物料编码 + 物料名称」；
     * 解析出空数组或空串 → 不查重（用户显式清空）。</p>
     */
    private List<String> readFieldKeys() {
        String raw = null;
        try {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ?", String.class, CFG_KEY);
            if (values != null && !values.isEmpty()) {
                raw = values.get(0);
            }
        } catch (Exception e) {
            log.warn("[货品查重] 读取配置 {} 失败，按默认字段处理: {}", CFG_KEY, e.getMessage());
        }
        if (raw == null || raw.isBlank()) return DEFAULT_FIELDS;
        String s = raw.trim();
        // boolean 时代遗留值（升级 SQL 未执行的库）：回落默认，保证行为与改造前一致
        if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) return DEFAULT_FIELDS;
        if (s.startsWith("[")) {
            try {
                List<?> arr = JSON.readValue(s, List.class);
                List<String> keys = new ArrayList<>();
                for (Object o : arr) {
                    if (o != null) keys.add(String.valueOf(o).trim());
                }
                return keys;
            } catch (Exception e) {
                log.warn("[货品查重] 配置 {} 解析失败，已回落默认 物料编码+物料名称: {}", CFG_KEY, e.getMessage());
                return DEFAULT_FIELDS;
            }
        }
        // 兼容逗号分隔写法（人工直接改库的场景）
        List<String> keys = new ArrayList<>();
        for (String part : s.split(",")) {
            if (!part.isBlank()) keys.add(part.trim());
        }
        return keys.isEmpty() ? DEFAULT_FIELDS : keys;
    }

    private static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
