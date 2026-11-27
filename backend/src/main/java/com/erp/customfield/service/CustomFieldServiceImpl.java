package com.erp.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.entity.CustomFieldValue;
import com.erp.customfield.mapper.CustomFieldMapper;
import com.erp.customfield.mapper.CustomFieldValueMapper;
import com.erp.tenant.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CustomFieldServiceImpl extends ServiceImpl<CustomFieldMapper, CustomField> implements CustomFieldService {

    private final CustomFieldValueMapper valueMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 字段定义缓存：key = entityType，value = 启用的字段列表 */
    private Cache<String, List<CustomField>> fieldCache;

    /**
     * 业务表是否有 ext_data 列（按表名缓存探测结果）。
     *
     * <p>为什么必须先探测、不能直接发 SQL 再 catch：PostgreSQL 事务内任何语句报错都会把
     * 整个事务置为 aborted 状态，之后即使异常被 catch 掉，提交时执行的其实是 ROLLBACK。
     * 而 {@code syncToExtData} 是在 {@code saveFieldValues(@Transactional)} 内部、值都 upsert 完之后
     * 才被调用的 —— 对不存在的列直接发 SQL 会把"刚刚写入的自定义字段值"一起静默回滚：
     * 接口照样返回成功、前端弹"保存成功"，但库里一条值都没有（物料表就是这种表）。</p>
     */
    private final Map<String, Boolean> extDataColumnCache = new ConcurrentHashMap<>();

    public CustomFieldServiceImpl(CustomFieldValueMapper valueMapper) {
        this.valueMapper = valueMapper;
    }

    @PostConstruct
    public void init() {
        fieldCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
        try {
            ensureTablesExist();
            // 启动阶段没有租户上下文，预热只会按 default 数据源（租户1的库）加载，
            // 反而把租户1的自定义字段灌进全局缓存。改为各租户首次访问时懒加载。
            log.info("CustomField system initialized.");
        } catch (Exception e) {
            log.warn("CustomField init failed: {}", e.getMessage());
        }
    }

    /** 兜底建表（首次启动） */
    private void ensureTablesExist() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_custom_field", Integer.class);
        } catch (Exception e) {
            log.info("sys_custom_field table not found, creating...");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_custom_field (
                    field_id BIGSERIAL PRIMARY KEY,
                    entity_type VARCHAR(50) NOT NULL,
                    field_key VARCHAR(50) NOT NULL,
                    field_label VARCHAR(100) NOT NULL,
                    field_type VARCHAR(20) NOT NULL,
                    field_options TEXT,
                    default_value VARCHAR(500),
                    placeholder VARCHAR(200),
                    precision_val INT,
                    min_value NUMERIC(20,4),
                    max_value NUMERIC(20,4),
                    formula_expr TEXT,
                    formula_deps TEXT,
                    section_name VARCHAR(50),
                    sort_order INT DEFAULT 0,
                    is_required SMALLINT DEFAULT 0,
                    is_list_show SMALLINT DEFAULT 0,
                    is_drop_show SMALLINT DEFAULT 0,
                    is_search SMALLINT DEFAULT 0,
                    is_enabled SMALLINT DEFAULT 1,
                    remark VARCHAR(500),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP,
                    created_by VARCHAR(50),
                    updated_by VARCHAR(50)
                )
                """);
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_field_entity_key ON sys_custom_field(entity_type, field_key)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_custom_field_entity ON sys_custom_field(entity_type, is_enabled, sort_order)");
        }
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_custom_field_value", Integer.class);
        } catch (Exception e) {
            log.info("sys_custom_field_value table not found, creating...");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_custom_field_value (
                    value_id BIGSERIAL PRIMARY KEY,
                    entity_type VARCHAR(50) NOT NULL,
                    entity_id BIGINT NOT NULL,
                    field_key VARCHAR(50) NOT NULL,
                    field_value TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP,
                    created_by VARCHAR(50),
                    updated_by VARCHAR(50)
                )
                """);
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_field_value ON sys_custom_field_value(entity_type, entity_id, field_key)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_cfv_entity ON sys_custom_field_value(entity_type, entity_id)");
        }
        // 兜底补列：老库（表已存在但缺 is_drop_show 列）启动时自动补齐，避免"列不存在"导致接口报错
        // 幂等：ADD COLUMN IF NOT EXISTS，重复执行零副作用（与 db/72_custom_field_drop_show.sql 等价）
        try {
            jdbcTemplate.execute("ALTER TABLE sys_custom_field ADD COLUMN IF NOT EXISTS is_drop_show SMALLINT NOT NULL DEFAULT 0");
        } catch (Exception e) {
            log.warn("ensure column sys_custom_field.is_drop_show failed: {}", e.getMessage());
        }
    }

    /** 缓存 key 必须带租户前缀，否则多租户共用一个 JVM 时会互相读到对方的自定义字段定义 */
    private String cacheKey(String entityType) {
        return TenantContextHolder.cacheKeyPrefix() + entityType;
    }

    // ========== 字段定义管理 ==========

    @Override
    public List<CustomField> listByEntity(String entityType) {
        QueryWrapper<CustomField> qw = new QueryWrapper<>();
        qw.eq("entity_type", entityType).orderByAsc("sort_order");
        // 【2026-11-26】工艺路线工序字段的镜像（rof_）不在设置中心展示：
        // 它们由「工艺路线 → 每道工序」维护，镜像只用于工序实例取值与打印
        qw.and(w -> w.notLike("field_key", "rof_%").or().isNull("field_key"));
        return list(qw);
    }

    @Override
    public List<CustomField> listEnabledByEntity(String entityType) {
        List<CustomField> cached = fieldCache.getIfPresent(cacheKey(entityType));
        if (cached != null) return cached;
        QueryWrapper<CustomField> qw = new QueryWrapper<>();
        qw.eq("entity_type", entityType).eq("is_enabled", 1).orderByAsc("sort_order");
        List<CustomField> result = list(qw);
        fieldCache.put(cacheKey(entityType), result);
        return result;
    }

    /**
     * 自动生成字段标识：custom_ + 序号（从1递增，同实体类型下唯一）
     */
    private String generateFieldKey(String entityType, String fieldLabel) {
        // 先尝试用 label 的拼音首字母或简单 slug（中文环境下用 custom_N 递增方式）
        QueryWrapper<CustomField> qw = new QueryWrapper<>();
        qw.eq("entity_type", entityType)
                .likeRight("field_key", "custom_")
                .orderByDesc("field_key")
                .last("LIMIT 1");
        CustomField last = getOne(qw);
        int seq = 1;
        if (last != null && last.getFieldKey() != null) {
            try {
                String numPart = last.getFieldKey().substring("custom_".length());
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception ignored) {
            }
        }
        String candidate = "custom_" + seq;
        // 确保唯一性
        while (true) {
            QueryWrapper<CustomField> exists = new QueryWrapper<>();
            exists.eq("entity_type", entityType).eq("field_key", candidate);
            if (count(exists) == 0) break;
            seq++;
            candidate = "custom_" + seq;
        }
        return candidate;
    }

    @Override
    @Transactional
    public CustomField saveField(CustomField field) {
        if (field.getEntityType() == null || field.getEntityType().isBlank()) {
            throw new BusinessException("实体类型不能为空");
        }
        if (field.getFieldLabel() == null || field.getFieldLabel().isBlank()) {
            throw new BusinessException("字段名称不能为空");
        }
        if (field.getFieldType() == null || field.getFieldType().isBlank()) {
            throw new BusinessException("字段类型不能为空");
        }
        // 新增时自动生成 fieldKey
        if (field.getFieldId() == null) {
            if (field.getFieldKey() == null || field.getFieldKey().isBlank()) {
                field.setFieldKey(generateFieldKey(field.getEntityType(), field.getFieldLabel()));
            }
        }
        // 校验 field_key 格式：英文字母数字下划线
        if (!field.getFieldKey().matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new BusinessException("字段标识必须以字母开头，只能包含字母数字下划线");
        }
        // formula 类型必须有表达式
        if ("formula".equals(field.getFieldType()) && (field.getFormulaExpr() == null || field.getFormulaExpr().isBlank())) {
            throw new BusinessException("公式字段必须填写公式表达式");
        }
        // select 类型必须有选项
        if ("select".equals(field.getFieldType()) && (field.getFieldOptions() == null || field.getFieldOptions().isBlank())) {
            throw new BusinessException("下拉选择字段必须配置选项");
        }
        // 唯一性校验：同实体类型下 field_key 唯一
        QueryWrapper<CustomField> uq = new QueryWrapper<>();
        uq.eq("entity_type", field.getEntityType()).eq("field_key", field.getFieldKey());
        if (field.getFieldId() != null) uq.ne("field_id", field.getFieldId());
        if (count(uq) > 0) {
            throw new BusinessException("字段标识已存在：" + field.getFieldKey());
        }
        // 默认值
        if (field.getSortOrder() == null) field.setSortOrder(0);
        if (field.getIsRequired() == null) field.setIsRequired(0);
        if (field.getIsListShow() == null) field.setIsListShow(0);
        if (field.getIsDropShow() == null) field.setIsDropShow(0);
        if (field.getIsSearch() == null) field.setIsSearch(0);
        if (field.getIsEnabled() == null) field.setIsEnabled(1);

        if (field.getFieldId() == null) {
            save(field);
        } else {
            updateById(field);
        }
        // 刷新缓存
        fieldCache.invalidate(cacheKey(field.getEntityType()));
        log.info("保存自定义字段: entityType={}, fieldKey={}, label={}", field.getEntityType(), field.getFieldKey(), field.getFieldLabel());
        return field;
    }

    @Override
    @Transactional
    public boolean deleteField(Long fieldId) {
        CustomField field = getById(fieldId);
        if (field == null) return false;
        // 删除字段定义
        boolean ok = removeById(fieldId);
        // 删除所有该字段的值
        QueryWrapper<CustomFieldValue> vw = new QueryWrapper<>();
        vw.eq("entity_type", field.getEntityType()).eq("field_key", field.getFieldKey());
        valueMapper.delete(vw);
        // 刷新缓存
        fieldCache.invalidate(cacheKey(field.getEntityType()));
        log.info("删除自定义字段: fieldId={}, fieldKey={}", fieldId, field.getFieldKey());
        return ok;
    }

    @Override
    public Map<String, List<CustomField>> batchListByEntities(List<String> entityTypes) {
        Map<String, List<CustomField>> result = new HashMap<>();
        for (String et : entityTypes) {
            result.put(et, listEnabledByEntity(et));
        }
        return result;
    }

    // ========== 字段值管理 ==========

    @Override
    public Map<String, Object> getFieldValues(String entityType, Long entityId) {
        QueryWrapper<CustomFieldValue> qw = new QueryWrapper<>();
        qw.eq("entity_type", entityType).eq("entity_id", entityId);
        List<CustomFieldValue> values = valueMapper.selectList(qw);
        Map<String, Object> result = new HashMap<>();
        for (CustomFieldValue v : values) {
            result.put(v.getFieldKey(), v.getFieldValue());
        }
        return result;
    }

    @Override
    public Map<Long, Map<String, Object>> batchGetFieldValues(String entityType, List<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return Collections.emptyMap();
        QueryWrapper<CustomFieldValue> qw = new QueryWrapper<>();
        qw.eq("entity_type", entityType).in("entity_id", entityIds);
        List<CustomFieldValue> values = valueMapper.selectList(qw);
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (CustomFieldValue v : values) {
            result.computeIfAbsent(v.getEntityId(), k -> new HashMap<>())
                    .put(v.getFieldKey(), v.getFieldValue());
        }
        return result;
    }

    @Override
    @Transactional
    public boolean saveFieldValues(String entityType, Long entityId, Map<String, Object> values) {
        if (values == null || values.isEmpty()) values = new HashMap<>();
        // 获取该实体的字段定义，用于过滤无效字段和识别 is_search 字段
        List<CustomField> fields = listEnabledByEntity(entityType);
        Set<String> validKeys = fields.stream().map(CustomField::getFieldKey).collect(Collectors.toSet());
        List<CustomField> searchFields = fields.stream()
                .filter(f -> f.getIsSearch() != null && f.getIsSearch() == 1)
                .collect(Collectors.toList());

        // 后端计算公式字段值（确保公式字段一定有值）
        List<CustomField> formulaFields = fields.stream()
                .filter(f -> "formula".equals(f.getFieldType()) && f.getFormulaExpr() != null)
                .collect(Collectors.toList());
        for (CustomField ff : formulaFields) {
            try {
                BigDecimal result = evaluateFormulaExpr(ff.getFormulaExpr(), values);
                if (result != null) {
                    values.put(ff.getFieldKey(), result.doubleValue());
                }
            } catch (Exception e) {
                log.warn("公式计算失败: fieldKey={}, expr={}, err={}", ff.getFieldKey(), ff.getFormulaExpr(), e.getMessage());
            }
        }

        // upsert 每个字段值
        Map<String, Object> extDataForSync = new HashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            if (!validKeys.contains(key)) {
                log.debug("跳过无效字段key: {}", key);
                continue;
            }
            Object val = entry.getValue();
            String strVal = val == null ? null : String.valueOf(val);

            // 查询是否已存在
            QueryWrapper<CustomFieldValue> qw = new QueryWrapper<>();
            qw.eq("entity_type", entityType).eq("entity_id", entityId).eq("field_key", key);
            CustomFieldValue existing = valueMapper.selectOne(qw);

            if (existing == null) {
                CustomFieldValue v = new CustomFieldValue();
                v.setEntityType(entityType);
                v.setEntityId(entityId);
                v.setFieldKey(key);
                v.setFieldValue(strVal);
                valueMapper.insert(v);
            } else {
                existing.setFieldValue(strVal);
                valueMapper.updateById(existing);
            }

            // 收集需要同步到 ext_data 的字段
            if (searchFields.stream().anyMatch(f -> f.getFieldKey().equals(key))) {
                extDataForSync.put(key, val);
            }
        }

        // 同步 is_search=1 的字段到业务表 ext_data JSONB 列
        if (!extDataForSync.isEmpty()) {
            syncToExtData(entityType, entityId, extDataForSync);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean deleteFieldValues(String entityType, Long entityId) {
        QueryWrapper<CustomFieldValue> qw = new QueryWrapper<>();
        qw.eq("entity_type", entityType).eq("entity_id", entityId);
        int n = valueMapper.delete(qw);
        // 清空 ext_data
        String tableName = resolveTableName(entityType);
        if (tableName != null && hasExtDataColumn(tableName)) {
            try {
                jdbcTemplate.update("UPDATE " + tableName + " SET ext_data = NULL WHERE " + resolveIdColumn(entityType) + " = ?", entityId);
            } catch (Exception e) {
                log.warn("删除自定义字段失败：清理字段值失败: entityType={}, entityId={}, {}", entityType, entityId, e.getMessage(), e);
                throw new BusinessException("删除自定义字段失败：清理字段值失败：" + e.getMessage());
            }
        }
        return n > 0;
    }

    private static final Pattern FORMULA_VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * 公式表达式求值（与前端 formulaEvaluator.js 一致的逻辑）
     * 支持 {{fieldKey}} 变量引用 + - * / () 四则运算
     */
    private BigDecimal evaluateFormulaExpr(String expr, Map<String, Object> data) {
        if (expr == null || expr.isBlank()) return null;
        try {
            // 1. 替换 {{fieldKey}} 为实际值
            Matcher matcher = FORMULA_VAR_PATTERN.matcher(expr);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String key = matcher.group(1);
                Object v = data != null ? data.get(key) : null;
                String replacement = "0";
                if (v != null && !v.toString().isBlank()) {
                    try {
                        BigDecimal n = new BigDecimal(v.toString());
                        replacement = n.toPlainString();
                    } catch (Exception ignored) {
                    }
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            String jsExpr = sb.toString();
            // 2. 安全校验：仅允许数字、运算符、括号、小数点、空格
            if (!jsExpr.matches("^[\\d+\\-*/().\\s]+$")) {
                log.warn("[formula] 表达式包含非法字符: {}", jsExpr);
                return null;
            }
            // 3. 求值（使用简单的表达式计算器，避免 ScriptEngine）
            double result = simpleEvaluate(jsExpr);
            if (Double.isNaN(result) || Double.isInfinite(result)) return null;
            return BigDecimal.valueOf(result);
        } catch (Exception e) {
            log.warn("[formula] 求值失败: {}, err: {}", expr, e.getMessage());
            return null;
        }
    }

    /**
     * 简单的四则运算表达式求值（支持 + - * / 和括号）
     */
    private double simpleEvaluate(String expr) {
        // 用递归下降法太复杂，这里用 Java 的 javax.script 会有兼容性问题
        // 改用简单的栈实现
        expr = expr.replaceAll("\\s+", "");
        return evalExpr(expr, new int[]{0});
    }

    private double evalExpr(String expr, int[] pos) {
        double result = evalTerm(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '+' || op == '-') {
                pos[0]++;
                double term = evalTerm(expr, pos);
                result = op == '+' ? result + term : result - term;
            } else {
                break;
            }
        }
        return result;
    }

    private double evalTerm(String expr, int[] pos) {
        double result = evalFactor(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '*' || op == '/') {
                pos[0]++;
                double factor = evalFactor(expr, pos);
                result = op == '*' ? result * factor : result / factor;
            } else {
                break;
            }
        }
        return result;
    }

    private double evalFactor(String expr, int[] pos) {
        if (pos[0] < expr.length() && expr.charAt(pos[0]) == '(') {
            pos[0]++;
            double result = evalExpr(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') {
                pos[0]++;
            }
            return result;
        }
        int start = pos[0];
        if (pos[0] < expr.length() && (expr.charAt(pos[0]) == '+' || expr.charAt(pos[0]) == '-')) {
            pos[0]++;
        }
        while (pos[0] < expr.length() && (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.')) {
            pos[0]++;
        }
        String num = expr.substring(start, pos[0]);
        return Double.parseDouble(num);
    }

    /** 探测业务表是否存在 ext_data 列（结果按表名缓存，避免重复查询 catalog） */
    private boolean hasExtDataColumn(String tableName) {
        return extDataColumnCache.computeIfAbsent(tableName, t -> {
            try {
                Integer n = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE column_name = 'ext_data' AND table_name = ? "
                                + "AND table_schema = ANY(current_schemas(false))",
                        Integer.class, t);
                return n != null && n > 0;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // 只有"列不存在"类错误（42703 / column ... does not exist）才允许按不存在处理；
                // 其它错误（连接失败、权限不足等）必须如实抛出，否则 ext_data 同步会被静默跳过
                if (msg.contains("42703") || (msg.contains("column") && msg.contains("does not exist"))) {
                    log.warn("探测 ext_data 列失败，按不存在处理: table={}, err={}", t, e.getMessage());
                    return false;
                }
                log.warn("保存自定义字段失败：探测扩展数据列失败: table={}, err={}", t, e.getMessage());
                throw new BusinessException("保存自定义字段失败：探测扩展数据列失败：" + e.getMessage());
            }
        });
    }

    /** 同步 is_search=1 的字段值到业务表 ext_data JSONB 列 */
    private void syncToExtData(String entityType, Long entityId, Map<String, Object> values) {
        String tableName = resolveTableName(entityType);
        if (tableName == null) return;
        // 没有 ext_data 列的表直接跳过：绝不能在这里发一条必然报错的 SQL，
        // 否则会把外层的自定义字段值保存事务整体置为 aborted（见 extDataColumnCache 注释）
        if (!hasExtDataColumn(tableName)) return;
        String idColumn = resolveIdColumn(entityType);
        try {
            // 先读取现有 ext_data（合并，避免覆盖其他字段）
            Map<String, Object> existing = new HashMap<>();
            String json = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(ext_data::text, '{}') FROM " + tableName + " WHERE " + idColumn + " = ?",
                    String.class, entityId);
            if (json != null && !json.isBlank()) {
                existing = objectMapper.readValue(json, Map.class);
            }
            existing.putAll(values);
            String mergedJson = objectMapper.writeValueAsString(existing);
            jdbcTemplate.update("UPDATE " + tableName + " SET ext_data = ?::jsonb WHERE " + idColumn + " = ?",
                    mergedJson, entityId);
        } catch (Exception e) {
            log.warn("保存自定义字段失败：同步扩展数据失败: entityType={}, entityId={}, err={}", entityType, entityId, e.getMessage());
            throw new BusinessException("保存自定义字段失败：同步扩展数据失败：" + e.getMessage());
        }
    }

    /**
     * 实体类型 → 表名映射（静态唯一事实来源）。
     * 反查表（表名 → 实体类型）与枚举全部由本表派生，禁止另写一份 switch/Map 造成口径漂移。
     */
    private static final Map<String, String> ENTITY_TABLE_MAP = new LinkedHashMap<>();
    /** 表名 → 实体类型（反查；同一表出现多实体时取先注册者） */
    private static final Map<String, String> TABLE_ENTITY_MAP = new LinkedHashMap<>();
    static {
        // 基础资料
        ENTITY_TABLE_MAP.put("customer", "customer");
        ENTITY_TABLE_MAP.put("opportunity", "crm_opportunity");
        ENTITY_TABLE_MAP.put("material", "material");
        ENTITY_TABLE_MAP.put("supplier", "supplier");
        // 基础资料·档案（2026-09-15 开放自定义字段，表名以实体 @TableName 为准）
        ENTITY_TABLE_MAP.put("material_category", "material_category");
        ENTITY_TABLE_MAP.put("unit", "unit_of_measure");
        ENTITY_TABLE_MAP.put("warehouse", "warehouse");
        ENTITY_TABLE_MAP.put("location", "location");
        ENTITY_TABLE_MAP.put("department", "department");
        ENTITY_TABLE_MAP.put("employee", "employee");
        ENTITY_TABLE_MAP.put("currency", "currency");
        ENTITY_TABLE_MAP.put("settle", "settlement_mode");
        ENTITY_TABLE_MAP.put("price_level", "price_level");
        ENTITY_TABLE_MAP.put("bank_account", "bank_account");
        // ENTITY_TABLE_MAP.put("account", "account");  // 会计科目不接入自定义字段（按用户要求全面注释，勿恢复）
        // 销售模块
        ENTITY_TABLE_MAP.put("sale_order", "sale_order");
        ENTITY_TABLE_MAP.put("sale_order_item", "sale_order_item");
        ENTITY_TABLE_MAP.put("sale_delivery", "sale_delivery");
        ENTITY_TABLE_MAP.put("sale_delivery_item", "sale_delivery_item");
        ENTITY_TABLE_MAP.put("sale_quote", "sale_quote");
        ENTITY_TABLE_MAP.put("sale_quote_item", "sale_quote_item");
        ENTITY_TABLE_MAP.put("sale_contract", "sale_contract");
        ENTITY_TABLE_MAP.put("sale_contract_item", "sale_contract_item");
        ENTITY_TABLE_MAP.put("sale_return", "sale_return");
        ENTITY_TABLE_MAP.put("sale_return_item", "sale_return_item");
        // 替换货（2026-11-17）：销售换货单表头/明细支持自定义字段
        ENTITY_TABLE_MAP.put("sale_exchange", "sale_exchange");
        ENTITY_TABLE_MAP.put("sale_exchange_item", "sale_exchange_item");
        // 售后维修（2026-11-18）：维修单表头/维修明细/配件领用支持自定义字段
        ENTITY_TABLE_MAP.put("sale_repair", "sale_repair");
        ENTITY_TABLE_MAP.put("sale_repair_item", "sale_repair_item");
        ENTITY_TABLE_MAP.put("sale_repair_part", "sale_repair_part");
        ENTITY_TABLE_MAP.put("sale_invoice", "sale_invoice");
        ENTITY_TABLE_MAP.put("sale_invoice_item", "sale_invoice_item");
        // 采购模块
        ENTITY_TABLE_MAP.put("purchase_order", "purchase_order");
        ENTITY_TABLE_MAP.put("purchase_order_item", "purchase_order_item");
        // 采购入库单：真实表名是 purchase_receipt / purchase_receipt_item
        // （实体 @TableName("purchase_receipt")）。原映射写成 purchase_inbound 指向不存在的表，
        // 会让「采购入库单」实体的 ext_data 同步永远静默失效（resolveTableName 返回的表不存在）
        ENTITY_TABLE_MAP.put("purchase_inbound", "purchase_receipt");
        ENTITY_TABLE_MAP.put("purchase_inbound_item", "purchase_receipt_item");
        ENTITY_TABLE_MAP.put("purchase_return", "purchase_return");
        ENTITY_TABLE_MAP.put("purchase_return_item", "purchase_return_item");
        ENTITY_TABLE_MAP.put("purchase_request", "purchase_request");
        ENTITY_TABLE_MAP.put("purchase_request_item", "purchase_request_item");
        ENTITY_TABLE_MAP.put("purchase_contract", "purchase_contract");
        ENTITY_TABLE_MAP.put("purchase_contract_item", "purchase_contract_item");
        ENTITY_TABLE_MAP.put("purchase_invoice", "purchase_invoice");
        ENTITY_TABLE_MAP.put("purchase_invoice_item", "purchase_invoice_item");
        // 库存模块
        ENTITY_TABLE_MAP.put("stock_transfer", "stock_transfer");
        ENTITY_TABLE_MAP.put("stock_transfer_item", "stock_transfer_item");
        ENTITY_TABLE_MAP.put("other_receipt", "other_receipt");
        ENTITY_TABLE_MAP.put("other_receipt_item", "other_receipt_item");
        ENTITY_TABLE_MAP.put("other_issue", "other_issue");
        ENTITY_TABLE_MAP.put("other_issue_item", "other_issue_item");
        ENTITY_TABLE_MAP.put("material_requisition", "material_requisition");
        ENTITY_TABLE_MAP.put("material_requisition_item", "material_requisition_item");
        ENTITY_TABLE_MAP.put("stock_check", "stock_check");
        ENTITY_TABLE_MAP.put("stock_check_item", "stock_check_item");
        // 生产模块
        ENTITY_TABLE_MAP.put("produce_assemble", "produce_assemble");
        ENTITY_TABLE_MAP.put("produce_disassemble", "produce_disassemble");
        // 生产入库单 / 报工单（2026-11-24 用户口径「生产入库单、报工单、委外 5 类加入自定义功能」）
        ENTITY_TABLE_MAP.put("production_receipt", "production_receipt");
        ENTITY_TABLE_MAP.put("production_receipt_item", "production_receipt_item");
        ENTITY_TABLE_MAP.put("operation_report", "operation_report");
        // 生产工序（工单内工序进度节点）：2026-11-25 用户口径「工序进度节点支持新增自定义字段和填写内容」
        ENTITY_TABLE_MAP.put("work_order_operation", "work_order_operation");
        // 委外模块（2026-11-24 同上）
        ENTITY_TABLE_MAP.put("subcontract_order", "subcontract_order");
        ENTITY_TABLE_MAP.put("subcontract_order_item", "subcontract_order_item");
        ENTITY_TABLE_MAP.put("subcontract_pick", "subcontract_pick");
        ENTITY_TABLE_MAP.put("subcontract_pick_item", "subcontract_pick_item");
        ENTITY_TABLE_MAP.put("subcontract_inbound", "subcontract_inbound");
        ENTITY_TABLE_MAP.put("subcontract_inbound_item", "subcontract_inbound_item");
        ENTITY_TABLE_MAP.put("subcontract_return", "subcontract_return");
        ENTITY_TABLE_MAP.put("subcontract_return_item", "subcontract_return_item");
        ENTITY_TABLE_MAP.put("subcontract_supplement", "subcontract_supplement");
        ENTITY_TABLE_MAP.put("subcontract_supplement_item", "subcontract_supplement_item");
        // 【第 13 期】生产工单：此前漏接线（机制通用，生产模块只挂了组装/拆卸单）→
        // 车间排程看板「站点设置」的候选要含「工单自定义字段」，故本期把主档接上
        ENTITY_TABLE_MAP.put("production_order", "production_order");
        // 其他
        // 打样模块·打样单（2026-10-09 接入自定义字段）：此前打样单从未登记，导致其页面/列表
        // 虽与免费样品单同属"打样管理"，却收不到任何自定义字段（设置中心里那个"打样单"对象
        // 实际指向 free_sample_order，已在本轮更正标签为"免费样品订单"）
        ENTITY_TABLE_MAP.put("sample_order", "sample_order");
        ENTITY_TABLE_MAP.put("sample_order_item", "sample_order_item");
        ENTITY_TABLE_MAP.put("free_sample_order", "free_sample_order");
        ENTITY_TABLE_MAP.put("free_sample_order_item", "free_sample_order_item");
        ENTITY_TABLE_MAP.put("bom", "bom");
        ENTITY_TABLE_MAP.put("bom_item", "bom_item");
        // 财务模块（自定义字段挂表头级实体；收款/付款明细无行级独立ID，值随表头存）
        ENTITY_TABLE_MAP.put("sale_pre_receipt", "sale_pre_receipt");
        ENTITY_TABLE_MAP.put("purchase_pre_payment", "purchase_pre_payment");
        ENTITY_TABLE_MAP.put("cash_receipt", "cash_receipt");
        ENTITY_TABLE_MAP.put("cash_payment", "cash_payment");
        for (Map.Entry<String, String> e : ENTITY_TABLE_MAP.entrySet()) {
            TABLE_ENTITY_MAP.putIfAbsent(e.getValue(), e.getKey());
        }
    }

    @Override
    public String resolveTableName(String entityType) {
        return entityType == null ? null : ENTITY_TABLE_MAP.get(entityType);
    }

    @Override
    public String resolveEntityType(String tableName) {
        return tableName == null ? null : TABLE_ENTITY_MAP.get(tableName);
    }

    @Override
    public Set<String> allEntityTypes() {
        return ENTITY_TABLE_MAP.keySet();
    }

    /** 实体类型 → 主键列名映射 */
    private String resolveIdColumn(String entityType) {
        return switch (entityType) {
            // 基础资料
            case "customer" -> "customer_id";
            case "opportunity" -> "opportunity_id";
            case "material" -> "material_id";
            case "supplier" -> "supplier_id";
            // 基础资料·档案（2026-09-15 开放自定义字段；此处必须与 ENTITY_TABLE_MAP 的真实主键列一致，
            // 否则 syncToExtData/deleteFieldValues 会拼出 `WHERE id = ?` 触发 PG 语法错误 ->
            // 整个 @Transactional 被 aborted -> 刚写入的自定义字段值被静默回滚，界面报"保存成功"库里却没值）
            case "material_category" -> "category_id";
            case "unit" -> "unit_id";
            case "warehouse" -> "stock_id";
            case "location" -> "bin_id";
            case "department" -> "dept_id";
            case "employee" -> "employee_id";
            case "currency" -> "currency_id";
            case "settle" -> "settle_mode_id";
            case "price_level" -> "price_level_id";
            case "bank_account" -> "account_id";
            // 销售模块
            case "sale_order" -> "order_id";
            case "sale_order_item" -> "order_item_id";
            case "sale_delivery" -> "delivery_id";
            case "sale_delivery_item" -> "delivery_item_id";
            case "sale_quote" -> "quote_id";
            case "sale_quote_item" -> "quote_item_id";
            case "sale_contract" -> "contract_id";
            case "sale_contract_item" -> "item_id";
            case "sale_return" -> "return_id";
            case "sale_return_item" -> "return_item_id";
            case "sale_invoice" -> "invoice_id";
            case "sale_invoice_item" -> "invoice_item_id";
            // 采购模块
            case "purchase_order" -> "order_id";
            case "purchase_order_item" -> "order_item_id";
            case "purchase_inbound" -> "receipt_id";
            case "purchase_inbound_item" -> "receipt_item_id";
            case "purchase_return" -> "return_id";
            case "purchase_return_item" -> "return_item_id";
            case "purchase_request" -> "request_id";
            case "purchase_request_item" -> "request_item_id";
            case "purchase_contract" -> "contract_id";
            case "purchase_contract_item" -> "contract_item_id";
            case "purchase_invoice" -> "invoice_id";
            case "purchase_invoice_item" -> "invoice_item_id";
            // 库存模块
            case "stock_transfer" -> "transfer_id";
            case "stock_transfer_item" -> "transfer_item_id";
            case "other_receipt" -> "receipt_id";
            case "other_receipt_item" -> "receipt_item_id";
            case "other_issue" -> "issue_id";
            case "other_issue_item" -> "issue_item_id";
            case "material_requisition" -> "requisition_id";
            case "material_requisition_item" -> "requisition_item_id";
            case "stock_check" -> "check_id";
            case "stock_check_item" -> "check_item_id";
            // 生产模块
            case "produce_assemble" -> "id";
            case "produce_disassemble" -> "id";
            case "production_order" -> "order_id";
            case "production_receipt" -> "receipt_id";
            case "production_receipt_item" -> "receipt_item_id";
            case "operation_report" -> "report_id";
            // 工单工序实例主键列 = id（表 work_order_operation）
            case "work_order_operation" -> "id";
            // 委外模块（主键均为 id）
            case "subcontract_order", "subcontract_order_item",
                 "subcontract_pick", "subcontract_pick_item",
                 "subcontract_inbound", "subcontract_inbound_item",
                 "subcontract_return", "subcontract_return_item",
                 "subcontract_supplement", "subcontract_supplement_item" -> "id";
            // 其他
            case "sample_order" -> "sample_id";
            case "sample_order_item" -> "item_id";
            case "free_sample_order" -> "order_id";
            case "free_sample_order_item" -> "item_id";
            case "bom" -> "bom_id";
            case "bom_item" -> "bom_item_id";
            // 财务模块主键
            case "sale_pre_receipt" -> "id";
            case "purchase_pre_payment" -> "id";
            case "cash_receipt" -> "receipt_id";
            case "cash_payment" -> "payment_id";
            default -> "id";
        };
    }

    @Override
    public void refreshCache() {
        // 只失效当前租户的 key，不再预热（预热会按当前线程的租户上下文灌数据）
        String prefix = TenantContextHolder.cacheKeyPrefix();
        fieldCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        log.info("CustomField cache refreshed. tenant={}", TenantContextHolder.cachePrefix());
    }
}
