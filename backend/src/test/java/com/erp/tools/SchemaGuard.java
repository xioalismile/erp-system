package com.erp.tools;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * SchemaGuard - 部署前实体类与目标数据库 schema 校验工具（部署四件套之 schema 守卫）
 *
 * 用途：实体类（代码真相源）有、但目标数据库没有的列 → 自动生成幂等 ALTER ADD COLUMN；
 *       缺表 → 自动生成 CREATE TABLE IF NOT EXISTS 建表；类型不匹配 → 仅报告不自动改（防截断数据）。
 *       只增不改不删：绝不 DROP / MODIFY COLUMN，缺表建表也只在表不存在时执行。
 *
 * 多库支持：默认校验业务库（erp_db，跳过 com.erp.tenant.entity 租户实体）；
 *          加 --platform 参数则只校验租户实体（sys_tenant/sys_user_tenant，对应 platform 库 erp_platform）。
 *
 * 运行方式（部署脚本调用，或手动）：
 *   java -cp "backend.jar;依赖..." com.erp.tools.SchemaGuard --url jdbc:postgresql://host:5432/db --user u --password p [--apply] [--out D:\path\out.sql] [--platform]
 * 退出码：0 = 通过（缺列已补齐、缺表已建）；2 = 运行环境错误。
 */
public class SchemaGuard {

    // ============ 命令行参数 ============
    private static String url;
    private static String user;
    private static String password;
    private static boolean apply = false;
    private static String outFile;

    // 实体扫描根包（与生产代码包一致）
    private static final String BASE_PACKAGE = "com.erp";
    // 多租户租户实体包：这些实体对应的表（sys_tenant/sys_user_tenant）存放在 platform 库(erp_platform)，
    // 与业务库(erp_db)物理分离。--platform 模式只校验这些表；默认模式跳过它们。
    private static final String TENANT_ENTITY_PACKAGE = "com.erp.tenant.entity";
    // 是否为 platform 库校验模式（--platform），默认 false=业务库校验
    private static boolean platformMode = false;
    // 表名 -> 主键列定义（如 "id BIGSERIAL" / "id BIGINT"），用于缺表自动建表时生成 PRIMARY KEY
    private static final Map<String, String> PRIMARY_KEY_DEF = new HashMap<>();
    // classpath 下实体 class 所在目录（由 --classes 参数或默认 target/classes 推断）
    private static String classesDir = "target/classes";

    public static void main(String[] args) throws Exception {
        parseArgs(args);

        // 1. 扫描实体类
        Map<String, Map<String, String>> entitySchema = scanEntities();
        if (entitySchema.isEmpty()) {
            System.err.println("[FATAL] 未扫描到任何 @TableName 实体类，检查 --classes 路径: " + classesDir);
            System.exit(2);
        }
        System.out.println("[INFO] 扫描到实体类: " + entitySchema.size() + " 个");

        // 2. 连接目标库读取现有 schema
        Map<String, Set<String>> dbColumns = new HashMap<>();
        Map<String, String> dbColumnTypes = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision, numeric_scale "
                    + "FROM information_schema.columns WHERE table_schema = 'public'";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String table = rs.getString(1);
                    String column = rs.getString(2);
                    dbColumns.computeIfAbsent(table, k -> new HashSet<>()).add(column);
                    dbColumnTypes.put(table + "." + column, normalizeType(rs));
                }
            }
        } catch (SQLException e) {
            // 库不存在（SQLState 3D000，如首次启用多租户时 erp_platform 尚未创建）：
            // 跳过本次校验不阻塞部署，后续由 agent 建库 + 建表
            if ("3D000".equals(e.getSQLState())) {
                System.out.println("[INFO] 目标库不存在(" + url + ")，跳过本次校验（由部署 agent 建库建表）");
                System.exit(0);
            }
            System.err.println("[FATAL] 目标库连接/查询失败: " + e.getMessage());
            System.exit(2);
        }
        System.out.println("[INFO] 目标库表数: " + dbColumns.size());

        // 3. diff
        List<String> ddl = new ArrayList<>();
        List<String> createTableDdl = new ArrayList<>();
        List<String> typeMismatches = new ArrayList<>();

        for (Map.Entry<String, Map<String, String>> e : entitySchema.entrySet()) {
            String table = e.getKey();
            Map<String, String> cols = e.getValue();
            if (!dbColumns.containsKey(table)) {
                // 缺表：自动建表（CREATE TABLE IF NOT EXISTS，幂等，只增不改不删，绝不 DROP）
                createTableDdl.add(buildCreateTable(table, cols));
                continue;
            }
            Set<String> existing = dbColumns.get(table);
            for (Map.Entry<String, String> c : cols.entrySet()) {
                if (!existing.contains(c.getKey())) {
                    // 列名统一加双引号：避免 current_date 等 PostgreSQL 保留字引发语法错误
                    ddl.add("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS \"" + c.getKey() + "\" " + c.getValue() + ";");
                } else {
                    String dbType = dbColumnTypes.get(table + "." + c.getKey());
                    String entType = c.getValue();
                    if (!typeMatch(dbType, entType)) {
                        typeMismatches.add(table + "." + c.getKey() + " db=" + dbType + " entity=" + entType);
                    }
                }
            }
        }

        // 4. 输出报告
        System.out.println();
        System.out.println("========== SchemaGuard 校验报告 ==========");
        System.out.println("缺列（将生成幂等 ADD COLUMN）: " + ddl.size());
        for (String s : ddl) { System.out.println("  " + s); }
        System.out.println("缺表（将自动建表 CREATE TABLE IF NOT EXISTS）: " + createTableDdl.size());
        for (String s : createTableDdl) { System.out.println("  " + s); }
        System.out.println("类型不匹配（仅报告，不自动改）: " + typeMismatches.size());
        for (String s : typeMismatches) { System.out.println("  " + s); }
        System.out.println("==========================================");

        // 5. DDL 落盘留档（缺列补列 + 缺表建表 都落盘）
        if ((!ddl.isEmpty() || !createTableDdl.isEmpty()) && outFile != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("-- SchemaGuard generated ").append(java.time.LocalDateTime.now()).append("\n");
            sb.append("-- target: ").append(url).append("\n");
            for (String s : createTableDdl) { sb.append(s).append("\n"); }
            for (String s : ddl) { sb.append(s).append("\n"); }
            try {
                Files.write(Paths.get(outFile), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("[INFO] DDL 已写入: " + outFile);
            } catch (IOException ex) {
                System.err.println("[FATAL] 写 DDL 文件失败: " + ex.getMessage());
                System.exit(2);
            }
        }

        // 6. 应用（可选）：先建表再补列，全部幂等（IF NOT EXISTS），只增不改不删
        if (apply && (!ddl.isEmpty() || !createTableDdl.isEmpty())) {
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement st = conn.createStatement()) {
                for (String s : createTableDdl) { st.execute(s); }
                for (String s : ddl) { st.execute(s); }
                System.out.println("[INFO] 已建表 " + createTableDdl.size() + " 张、补列 " + ddl.size() + " 列");
            } catch (SQLException ex) {
                System.err.println("[FATAL] 应用 DDL 失败（已中止，防带病上线）: " + ex.getMessage());
                System.exit(2);
            }
        }

        // 7. 退出码：缺列已补、缺表已建，类型不匹配为警告级不阻塞，均通过
        System.exit(0);
    }

    // ============ 实体扫描 ============

    private static Map<String, Map<String, String>> scanEntities() throws IOException {
        Map<String, Map<String, String>> result = new HashMap<>();
        Path root = Paths.get(classesDir, BASE_PACKAGE.replace('.', '/'));
        if (!Files.isDirectory(root)) {
            System.err.println("[FATAL] classpath 目录不存在: " + root);
            System.exit(2);
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.toString().contains("$")) // 跳过内部类/lambda
                .forEach(p -> {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    String cls = BASE_PACKAGE + "." + rel.substring(0, rel.length() - 6).replace('/', '.');
                    try {
                        Class<?> clazz = Class.forName(cls, false, SchemaGuard.class.getClassLoader());
                        // 识别方式1：显式 @TableName
                        TableName tn = clazz.getAnnotation(TableName.class);
                        if (tn != null) {
                            addEntity(result, clazz, tn.value());
                            return;
                        }
                        // 识别方式2：Mapper 接口 extends BaseMapper<Xxx> → Xxx 为实体，表名按驼峰转下划线
                        if (clazz.isInterface() && BaseMapper.class.isAssignableFrom(clazz)) {
                            for (java.lang.reflect.Type gt : clazz.getGenericInterfaces()) {
                                if (gt instanceof java.lang.reflect.ParameterizedType) {
                                    java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) gt;
                                    if (pt.getRawType() == BaseMapper.class) {
                                        Class<?> entity = (Class<?>) pt.getActualTypeArguments()[0];
                                        addEntity(result, entity, camelToSnake(entity.getSimpleName()));
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        // 依赖缺失或加载失败的类直接跳过（非实体或环境问题不阻塞）
                        if (System.getProperty("schemaguard.debug") != null) {
                            System.err.println("[DEBUG] skip " + cls + ": " + t);
                        }
                    }
                });
        }
        return result;
    }

    private static void addEntity(Map<String, Map<String, String>> result, Class<?> clazz, String table) {
        // 实体自带 @TableName 时以注解值为准（Mapper 泛型路径传入的驼峰推断名仅作兜底）
        TableName tn = clazz.getAnnotation(TableName.class);
        if (tn != null && !tn.value().isEmpty()) {
            table = tn.value();
        }
        if (table == null || table.isEmpty()) return;
        // 实体分类：租户实体(com.erp.tenant.entity)只在 --platform 模式校验；业务实体只在默认模式校验
        boolean isTenantEntity = clazz.getName().startsWith(TENANT_ENTITY_PACKAGE);
        if (isTenantEntity != platformMode) return;
        Map<String, String> cols = extractColumns(clazz);
        if (cols.isEmpty()) return;
        // 提取主键定义（缺表自动建表时用于生成 PRIMARY KEY）
        String pkDef = extractPrimaryKeyDef(clazz);
        if (pkDef != null) {
            PRIMARY_KEY_DEF.putIfAbsent(table, pkDef);
        }
        result.merge(table, cols, (a, b) -> { a.putAll(b); return a; });
    }

    private static Map<String, String> extractColumns(Class<?> clazz) {
        Map<String, String> cols = new HashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
            if ("serialVersionUID".equals(f.getName())) continue;
            TableField tf = f.getAnnotation(TableField.class);
            if (tf != null && !tf.exist()) continue;
            String column;
            if (tf != null && !tf.value().isEmpty()) {
                column = stripQuotes(tf.value()); // 生产代码为避开保留字会写 "\"current_date\"" 之类，比较前去掉引号
            } else if (f.isAnnotationPresent(TableId.class)) {
                TableId tid = f.getAnnotation(TableId.class);
                column = tid.value().isEmpty() ? camelToSnake(f.getName()) : stripQuotes(tid.value());
            } else {
                column = camelToSnake(f.getName());
            }
            cols.put(column, mapType(f.getType()));
        }
        return cols;
    }

    /** 提取实体主键列定义（如 "id BIGSERIAL"）；IdType.AUTO 用 BIGSERIAL 自增，其余用 BIGINT */
    private static String extractPrimaryKeyDef(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(TableId.class)) {
                TableId tid = f.getAnnotation(TableId.class);
                String col = tid.value().isEmpty() ? camelToSnake(f.getName()) : stripQuotes(tid.value());
                boolean auto = tid.type() == IdType.AUTO;
                return col + " " + (auto ? "BIGSERIAL" : "BIGINT");
            }
        }
        return null;
    }

    /** 生成缺表建表语句（CREATE TABLE IF NOT EXISTS，主键自增 PRIMARY KEY，只增不改不删） */
    private static String buildCreateTable(String table, Map<String, String> cols) {
        String pkDef = PRIMARY_KEY_DEF.get(table);
        String pkCol = null;
        String pkType = null;
        if (pkDef != null) {
            int sp = pkDef.indexOf(' ');
            pkCol = pkDef.substring(0, sp);
            pkType = pkDef.substring(sp + 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(table).append(" (\n");
        boolean first = true;
        for (Map.Entry<String, String> c : cols.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            String colName = c.getKey();
            // 列名统一加双引号：避免 current_date 等 PostgreSQL 保留字引发语法错误
            if (colName.equals(pkCol)) {
                // 主键列：自增 BIGSERIAL + PRIMARY KEY
                sb.append("  \"").append(colName).append("\" ").append(pkType).append(" PRIMARY KEY");
            } else {
                sb.append("  \"").append(colName).append("\" ").append(c.getValue());
            }
        }
        sb.append("\n)");
        return sb.toString();
    }

    /** 去掉列名两侧的转义引号（PostgreSQL 双引号标识符） */
    private static String stripQuotes(String s) {
        if (s == null) return null;
        String r = s.trim();
        while (r.startsWith("\"") && r.endsWith("\"") && r.length() >= 2) {
            r = r.substring(1, r.length() - 1);
        }
        return r;
    }

    private static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_'); // 首字母大写不加下划线
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ============ 类型映射（Java → PostgreSQL）============

    private static String mapType(Class<?> type) {
        if (type == Long.class || type == long.class) return "BIGINT";
        if (type == Integer.class || type == int.class) return "INT";
        if (type == Short.class || type == short.class) return "SMALLINT";
        if (type == String.class) return "VARCHAR(255)";
        if (type == BigDecimal.class) return "NUMERIC(18,2)";
        if (type == Boolean.class || type == boolean.class) return "BOOLEAN";
        if (type == LocalDate.class) return "DATE";
        if (type == LocalDateTime.class) return "TIMESTAMP";
        if (type == LocalTime.class) return "TIME";
        if (type == java.util.Date.class) return "TIMESTAMP";
        if (type == Double.class || type == double.class || type == Float.class || type == float.class)
            return "DOUBLE PRECISION";
        if (type == byte[].class) return "BYTEA";
        // 集合/复杂类型（JSON 字段等）默认 JSONB
        if (java.util.Collection.class.isAssignableFrom(type) || java.util.Map.class.isAssignableFrom(type))
            return "JSONB";
        return "TEXT";
    }

    // ============ 类型比较（宽松归一化）============

    private static String normalizeType(ResultSet rs) throws SQLException {
        String dt = rs.getString(3);
        if (dt == null) return "UNKNOWN";
        switch (dt) {
            case "character varying": return "VARCHAR(" + rs.getInt(4) + ")";
            case "character": return "CHAR(" + rs.getInt(4) + ")";
            case "numeric": {
                int p = rs.getInt(5), s = rs.getInt(6);
                return s == 0 && p > 0 ? "NUMERIC(" + p + ",0)" : "NUMERIC";
            }
            case "timestamp without time zone": return "TIMESTAMP";
            case "timestamp with time zone": return "TIMESTAMPTZ";
            case "integer": return "INT";
            case "smallint": return "SMALLINT";
            case "bigint": return "BIGINT";
            case "double precision": return "DOUBLE PRECISION";
            case "boolean": return "BOOLEAN";
            case "date": return "DATE";
            case "text": return "TEXT";
            case "jsonb": return "JSONB";
            case "json": return "JSON";
            default: return dt.toUpperCase();
        }
    }

    private static boolean typeMatch(String dbType, String entityType) {
        if (dbType == null) return true;
        String d = dbType.toUpperCase().replaceAll("\\s+", "");
        String e = entityType.toUpperCase().replaceAll("\\s+", "");
        if (d.equals(e)) return true;
        // VARCHAR：库长度 >= 实体期望 → 兼容（更宽不会截断）；库更窄 → 报
        if (d.startsWith("VARCHAR") && e.startsWith("VARCHAR")) {
            return lenOf(d) >= lenOf(e);
        }
        // NUMERIC：库无精度（任意精度）→ 兼容；库精度小于实体 → 报
        if (d.startsWith("NUMERIC") && e.startsWith("NUMERIC")) {
            if (d.equals("NUMERIC")) return true;
            return cmpNumeric(d) >= cmpNumeric(e);
        }
        // 整型值域：库比实体宽 → 兼容（BIGINT >= INT >= SMALLINT）
        if (isIntType(d) && isIntType(e)) {
            return widthOf(d) >= widthOf(e);
        }
        // TIMESTAMP 家族互通
        if ((d.equals("TIMESTAMP") || d.equals("TIMESTAMPTZ")) && (e.equals("TIMESTAMP") || e.equals("TIMESTAMPTZ"))) {
            return true;
        }
        // TEXT 库列兼容实体 VARCHAR（更宽）
        if (d.equals("TEXT") && e.startsWith("VARCHAR")) return true;
        return false;
    }

    private static int lenOf(String t) {
        int s = t.indexOf('('), e2 = t.indexOf(')');
        if (s < 0 || e2 < 0) return 0;
        try { return Integer.parseInt(t.substring(s + 1, e2)); } catch (NumberFormatException ex) { return 0; }
    }

    /** 按精度*10+标度粗略比较数值宽度 */
    private static int cmpNumeric(String t) {
        int s = t.indexOf('('), e2 = t.indexOf(',');
        if (s < 0) return 0;
        try {
            int p = Integer.parseInt(t.substring(s + 1, e2 < 0 ? t.indexOf(')') : e2));
            int sc = e2 < 0 ? 0 : Integer.parseInt(t.substring(e2 + 1, t.indexOf(')')));
            return p * 10 + sc;
        } catch (NumberFormatException ex) { return 0; }
    }

    private static boolean isIntType(String t) {
        return t.equals("BIGINT") || t.equals("INT") || t.equals("SMALLINT");
    }

    private static int widthOf(String t) {
        switch (t) {
            case "BIGINT": return 3;
            case "INT": return 2;
            case "SMALLINT": return 1;
            default: return 0;
        }
    }

    // ============ 参数解析 ============

    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url": url = args[++i]; break;
                case "--user": user = args[++i]; break;
                case "--password": password = args[++i]; break;
                case "--apply": apply = true; break;
                case "--out": outFile = args[++i]; break;
                case "--classes": classesDir = args[++i]; break;
                case "--platform": platformMode = true; break;
                default:
                    System.err.println("未知参数: " + args[i]);
                    System.exit(2);
            }
        }
        if (url == null || user == null || password == null) {
            System.err.println("用法: SchemaGuard --url <jdbc> --user <u> --password <p> [--apply] [--out <file>] [--classes <dir>]");
            System.exit(2);
        }
    }
}
