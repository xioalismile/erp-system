package com.erp.system.onboarding;

import com.erp.tenant.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按行业初始化引擎（2026-10-11 开账引导）。
 *
 * <p>职责：按行业模板一次性写入 —— 部门 / 计量单位 / 业务角色（含菜单授权）/ 仓库 /
 * 演示基础档案（物料·客户·供应商）/ 系统字段叫法偏好；并落引导状态。</p>
 *
 * <p>硬性口径：</p>
 * <ul>
 *   <li><b>幂等</b>：逐项"已存在即跳过"，重复执行 = 补缺，绝不删改客户已自建的数据；</li>
 *   <li><b>事务</b>：整个初始化一个事务，任一步失败整体回滚，引导状态保持未完成可重试；</li>
 *   <li><b>只加不减</b>：不 UPDATE/DELETE 任何既有业务行；字段叫法只补缺（用户改过的名字不动）；</li>
 *   <li><b>版本隔离</b>：角色菜单授权 ∩ 租户版本可见菜单（与登录下发口径一致）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryInitService {

    private final IndustryCatalog catalog;

    /** 引导状态等配置统一走配置服务（自带按租户隔离的缓存同步；直写库会让缓存里的旧值一直活着） */
    private final com.erp.config.service.SysConfigService sysConfigService;

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /** 平台库（查租户版本用；单库环境可能没有 → 版本过滤降级为不过滤） */
    @Autowired(required = false)
    @Qualifier("platformJdbcTemplate")
    private JdbcTemplate platformJdbcTemplate;

    /** 引导状态键（存于各租户库 sys_config） */
    public static final String CFG_STATUS = "tenant.onboarding.status";
    public static final String CFG_INDUSTRY = "tenant.onboarding.industry";
    public static final String CFG_TIME = "tenant.onboarding.time";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_SKIPPED = "skipped";

    /** 小楷/REST 共用：行业清单（向导选项 + 预览） */
    public List<Map<String, Object>> listIndustries() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (IndustryCatalog.Industry ind : catalog.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", ind.code);
            m.put("name", ind.name);
            m.put("desc", ind.desc);
            m.put("departmentCount", ind.departments.size());
            m.put("roleNames", ind.roles.stream().map(r -> r.name).toArray());
            m.put("unitCount", ind.units.size());
            m.put("warehouseNames", ind.warehouses.stream().map(w -> w[1]).toArray());
            m.put("fieldRenames", ind.fieldRenames);
            list.add(m);
        }
        return list;
    }

    /** 预览（不写库）：给确认卡/小楷确认用 */
    public Map<String, Object> preview(String industryCode, Integer employeeCount) {
        IndustryCatalog.Industry ind = catalog.ofOrDefault(industryCode);
        int n = IndustryCatalog.demoCount(employeeCount);
        int tier = IndustryCatalog.scaleTier(employeeCount);
        int count = employeeCount == null ? 5 : employeeCount;
        // 按规模过滤：部门/角色/仓库只保留 minScale <= 当前人数 的项
        List<String[]> depts = ind.departments.stream().filter(d -> IndustryCatalog.deptMinScale(d) <= count).toList();
        List<IndustryCatalog.RoleDef> roles = ind.roles.stream().filter(r -> r.minScale <= count).toList();
        List<String[]> whs = ind.warehouses.stream().filter(w -> IndustryCatalog.whMinScale(w) <= count).toList();

        // 按当前租户版本过滤角色菜单：只展示该版本实际拥有的菜单，避免预览出现"不属于本版本的菜单"
        String version = tenantVersion();
        List<String> roleDisplays = new ArrayList<>();
        for (IndustryCatalog.RoleDef r : roles) {
            List<String> moduleNames = resolveRoleMenuModules(r.routePrefixes, version);
            if (moduleNames.isEmpty()) {
                roleDisplays.add(r.name + "（无可用菜单）");
            } else {
                roleDisplays.add(r.name + "（" + String.join("、", moduleNames) + "）");
            }
        }

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("industryCode", ind.code);
        plan.put("industryName", ind.name);
        plan.put("employeeCount", count);
        plan.put("scaleTier", tier);
        plan.put("scaleLabel", scaleLabel(tier));
        plan.put("demoPerTable", n);
        plan.put("departments", depts.stream().map(d -> d[1]).toArray());
        plan.put("roles", roleDisplays);
        plan.put("units", ind.units.stream().map(u -> u[1]).toArray());
        plan.put("warehouses", whs.stream().map(w -> w[1]).toArray());
        plan.put("materialCount", Math.min(n, ind.materials.size()));
        plan.put("basicSettings", new String[]{"付款条件", "结算方式", "价格等级", "币种", "物料分类"});
        plan.put("fieldRenames", ind.fieldRenames);
        return plan;
    }

    /**
     * 按租户版本解析角色实际能拿到的菜单模块名（去重保序）。
     * 与初始化时的版本过滤口径一致：STANDARD 版看不到 CRM/生产/委外等菜单。
     */
    private List<String> resolveRoleMenuModules(List<String> prefixes, String version) {
        List<String> modules = new ArrayList<>();
        String menuFilter = versionFilter(version);
        for (String prefix : prefixes) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT route_path FROM sys_menu m " +
                            "WHERE m.route_path LIKE ? ESCAPE '\\' AND m.route_path IS NOT NULL AND m.route_path != '' " +
                            "AND (" + menuFilter + ") ORDER BY route_path",
                    prefix + "%");
            for (Map<String, Object> row : rows) {
                String route = String.valueOf(row.get("route_path"));
                String name = moduleLabel(route);
                if (name != null && !modules.contains(name)) {
                    modules.add(name);
                }
            }
        }
        return modules;
    }

    /** 路由 → 模块人话名（按一级路径归类） */
    private static String moduleLabel(String route) {
        String seg = route.startsWith("/") ? route.substring(1).split("/")[0] : route.split("/")[0];
        switch (seg) {
            case "sale": return "销售";
            case "purchase": return "采购";
            case "inventory": return "库存";
            case "production": return "生产";
            case "finance": return "财务";
            case "crm": return "客户管理";
            case "basedata": return "基础资料";
            case "databoard": return "数据看板";
            case "dashboard": return "工作台";
            case "masterdata": return "主数据";
            case "subcontract": return "委外";
            case "ecommerce": return "电商";
            case "report": return "报表";
            case "system": return "系统";
            case "logistics": return "物流";
            case "sample": return "打样";
            default: return null;
        }
    }

    private static String scaleLabel(int tier) {
        switch (tier) {
            case 0: return "微型团队（≤5人）";
            case 1: return "小型公司（6~20人）";
            case 2: return "中型企业（21~50人）";
            default: return "规模化企业（50人以上）";
        }
    }

    /** 菜单前缀 → 人话（确认卡可读） */
    private static List<String> shortPrefixes(List<String> prefixes) {
        List<String> out = new ArrayList<>();
        for (String p : prefixes) {
            switch (p) {
                case "/sale": out.add("销售"); break;
                case "/purchase": out.add("采购"); break;
                case "/inventory": out.add("库存"); break;
                case "/production": out.add("生产"); break;
                case "/production/qc-record": out.add("质检报表"); break;
                case "/masterdata/qc-template": out.add("质检模板"); break;
                case "/finance": out.add("财务"); break;
                case "/crm": out.add("客户管理"); break;
                case "/basedata/customer": out.add("客户档案"); break;
                case "/basedata/supplier": out.add("供应商档案"); break;
                case "/basedata/warehouse": out.add("仓库档案"); break;
                case "/basedata/location": out.add("货位档案"); break;
                case "/databoard": out.add("数据看板"); break;
                case "/dashboard": out.add("工作台"); break;
                default: out.add(p); break;
            }
        }
        return out;
    }

    /**
     * 执行初始化（幂等 + 事务）。
     *
     * @param industryCode  行业编码（null/未知 → 通用兜底）
     * @param employeeCount 员工人数（决定演示资料条数分档）
     * @param operator      操作人（审计用）
     * @return 生成结果汇总（逐项数量），供向导完成报告与小楷回复
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> initialize(String industryCode, Integer employeeCount, String operator) {
        IndustryCatalog.Industry ind = catalog.ofOrDefault(industryCode);
        int n = IndustryCatalog.demoCount(employeeCount);
        int count = employeeCount == null ? 5 : employeeCount;
        // 按规模过滤：小公司不建冗余部门/角色/仓库
        List<String[]> depts = ind.departments.stream().filter(d -> IndustryCatalog.deptMinScale(d) <= count).toList();
        List<IndustryCatalog.RoleDef> roles = ind.roles.stream().filter(r -> r.minScale <= count).toList();
        List<String[]> whs = ind.warehouses.stream().filter(w -> IndustryCatalog.whMinScale(w) <= count).toList();
        String user = operator == null || operator.trim().isEmpty() ? "开账引导" : operator;
        log.info("[行业初始化] 开始: industry={}, count={}, tierDepts={}, tierRoles={}, tierWhs={}, operator={}, tenant={}",
                ind.code, n, depts.size(), roles.size(), whs.size(), user, TenantContextHolder.getTenantDbName());

        ensureFieldLabelTable();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("industryCode", ind.code);
        result.put("industryName", ind.name);
        result.put("departments", ensureDepartments(depts));
        result.put("units", ensureUnits(ind));
        result.put("warehouses", ensureWarehouses(whs));
        result.put("roles", ensureRoles(roles));
        result.put("materials", ensureDemoMaterials(ind, n, user));
        result.put("basicSettings", ensureBasicSettings(user));
        result.put("fieldRenames", applyFieldRenames(ind));

        upsertConfig(CFG_STATUS, STATUS_DONE);
        upsertConfig(CFG_INDUSTRY, ind.code);
        upsertConfig(CFG_TIME, String.valueOf(System.currentTimeMillis()));

        log.info("[行业初始化] 完成: {}", result);
        return result;
    }

    // ==================== 各步幂等写入 ====================

    /** 部门（父子两级按编码排序插入；父部门先建） */
    private int ensureDepartments(List<String[]> depts) {
        int created = 0;
        Map<String, Long> idByCode = new LinkedHashMap<>();
        // 先查已有
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT dept_code, dept_id FROM department WHERE dept_code IS NOT NULL")) {
            idByCode.put(String.valueOf(row.get("dept_code")), ((Number) row.get("dept_id")).longValue());
        }
        // 父在前（模板数据保证父编码排序在前）
        List<String[]> ordered = new ArrayList<>(depts);
        ordered.sort((a, b) -> {
            String pa = a[2] == null || a[2].isEmpty() ? "" : a[2];
            String pb = b[2] == null || b[2].isEmpty() ? "" : b[2];
            int c = pa.compareTo(pb);
            return c != 0 ? c : a[0].compareTo(b[0]);
        });
        for (String[] d : ordered) {
            String code = d[0], name = d[1], parentCode = d[2], type = d[3];
            if (idByCode.containsKey(code)) continue;
            Long parentId = (parentCode == null || parentCode.isEmpty()) ? null : idByCode.get(parentCode);
            jdbcTemplate.update(
                    "INSERT INTO department (dept_code, dept_name, parent_dept_id, dept_type, status, created_by) " +
                            "VALUES (?, ?, ?, ?, '启用', ?)",
                    code, name, parentId, type == null || type.isEmpty() ? "业务部门" : type, user_());
            Long id = jdbcTemplate.queryForObject(
                    "SELECT dept_id FROM department WHERE dept_code = ?", Long.class, code);
            idByCode.put(code, id);
            created++;
        }
        return created;
    }

    /** 计量单位 */
    private int ensureUnits(IndustryCatalog.Industry ind) {
        int created = 0;
        for (String[] u : ind.units) {
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM unit_of_measure WHERE unit_code = ?", Integer.class, u[0]);
            if (c > 0) continue;
            jdbcTemplate.update(
                    "INSERT INTO unit_of_measure (unit_code, unit_name, unit_group, is_base_unit, status, created_by) " +
                            "VALUES (?, ?, ?, ?, '启用', ?)",
                    u[0], u[1], u[2], Integer.parseInt(u[3]), user_());
            created++;
        }
        return created;
    }

    /** 仓库 */
    private int ensureWarehouses(List<String[]> whs) {
        int created = 0;
        for (String[] w : whs) {
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM warehouse WHERE stock_code = ?", Integer.class, w[0]);
            if (c > 0) continue;
            jdbcTemplate.update(
                    "INSERT INTO warehouse (stock_code, stock_name, stock_type, status, created_by) " +
                            "VALUES (?, ?, ?, '启用', ?)",
                    w[0], w[1], w[2] == null || w[2].isEmpty() ? "普通仓库" : w[2], user_());
            created++;
        }
        return created;
    }

    /**
     * 业务角色 + 菜单授权（版本过滤）。
     * 角色已存在 → 只补缺菜单授权；不存在 → 建角色再授权。
     */
    private int ensureRoles(List<IndustryCatalog.RoleDef> roles) {
        String version = tenantVersion();
        int createdRoles = 0;
        for (IndustryCatalog.RoleDef role : roles) {
            Long roleId = findRoleId(role.code);
            if (roleId == null) {
                syncRoleIdSequence();
                roleId = jdbcTemplate.queryForObject(
                        "INSERT INTO sys_role (role_code, role_name, role_type, data_scope, is_system, status, is_enabled, created_at) " +
                                "VALUES (?, ?, '业务角色', ?, 0, '启用', 1, CURRENT_TIMESTAMP) RETURNING role_id",
                        Long.class, role.code, role.name, role.dataScope);
                createdRoles++;
                log.info("[行业初始化] 建业务角色: {} ({})", role.name, role.code);
            }
            // 授权：按前缀匹配 route_path + 版本过滤，DISTINCT 防 route_path 重复行撞唯一键
            String menuFilter = versionFilter(version);
            for (String prefix : role.routePrefixes) {
                jdbcTemplate.update(
                        "INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at) " +
                                "SELECT DISTINCT ?, m.route_path, '菜单', 1, CURRENT_TIMESTAMP FROM sys_menu m " +
                                "WHERE m.route_path LIKE ? ESCAPE '\\' AND m.route_path IS NOT NULL AND m.route_path != '' " +
                                "AND (" + menuFilter + ") " +
                                "AND NOT EXISTS (SELECT 1 FROM sys_role_permission p WHERE p.role_id = ? " +
                                "                AND p.menu_id = m.route_path AND p.permission_type = '菜单')",
                        roleId, prefix + "%", roleId);
            }
        }
        return createdRoles;
    }

    /** 演示物料（编码 WL001 递增；撞已有编码即跳过） */
    private int ensureDemoMaterials(IndustryCatalog.Industry ind, int n, String user) {
        // 基本单位：优先"个"，否则第一个单位
        Long baseUnitId = jdbcTemplate.queryForObject(
                "SELECT unit_id FROM unit_of_measure WHERE unit_name = '个' LIMIT 1", Long.class);
        if (baseUnitId == null) {
            baseUnitId = jdbcTemplate.queryForObject(
                    "SELECT unit_id FROM unit_of_measure ORDER BY unit_id LIMIT 1", Long.class);
        }
        if (baseUnitId == null) {
            log.warn("[行业初始化] 无可用计量单位，演示物料跳过");
            return 0;
        }
        boolean hasRemark = columnExists("material", "remark");
        int created = 0;
        int seq = 1;
        for (String[] m : ind.materials) {
            if (created >= n) break;
            String code = String.format("WL%03d", seq++);
            int exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM material WHERE material_code = ?", Integer.class, code);
            if (exists > 0) continue;
            String sql = "INSERT INTO material (material_code, material_name, model, material_type, base_unit_id, " +
                    "sale_price, purchase_price, status, created_by" + (hasRemark ? ", remark" : "") + ") " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, '正常', ?" + (hasRemark ? ", ?" : "") + ")";
            Object[] args = hasRemark
                    ? new Object[]{code, m[0], m[1], m[2], baseUnitId, demoPrice(seq), demoCost(seq), user, "演示资料，可删除"}
                    : new Object[]{code, m[0], m[1], m[2], baseUnitId, demoPrice(seq), demoCost(seq), user};
            jdbcTemplate.update(sql, args);
            created++;
        }
        return created;
    }

    private static java.math.BigDecimal demoPrice(int seq) {
        return java.math.BigDecimal.valueOf(20 + seq * 5);
    }

    private static java.math.BigDecimal demoCost(int seq) {
        return java.math.BigDecimal.valueOf(12 + seq * 3);
    }

    /**
     * 基础设置初始化（付款条件 / 结算方式 / 价格等级 / 币种）。
     * 幂等：已存在同编码则跳过。
     */
    private Map<String, Integer> ensureBasicSettings(String user) {
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("paymentTerms", ensurePaymentTerms(user));
        r.put("settlementModes", ensureSettlementModes(user));
        r.put("priceLevels", ensurePriceLevels(user));
        r.put("currencies", ensureCurrencies(user));
        r.put("materialCategories", ensureMaterialCategories(user));
        return r;
    }

    /** 付款条件 */
    private int ensurePaymentTerms(String user) {
        int created = 0;
        String[][] terms = {
                {"CASH", "现款现货", "0"},
                {"MT30", "月结30天", "30"},
                {"MT60", "月结60天", "60"},
                {"MT90", "账期90天", "90"},
        };
        for (String[] t : terms) {
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_term WHERE payment_term_code = ?", Integer.class, t[0]);
            if (c > 0) continue;
            jdbcTemplate.update(
                    "INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, status, is_enabled, created_by) " +
                            "VALUES (?, ?, ?, '启用', 1, ?)",
                    t[0], t[1], Integer.parseInt(t[2]), user);
            created++;
        }
        return created;
    }

    /** 结算方式 */
    private int ensureSettlementModes(String user) {
        int created = 0;
        String[][] modes = {
                {"CASH", "现金", "通用"},
                {"BANK", "银行转账", "通用"},
                {"CHECK", "支票", "通用"},
                {"BILL", "汇票", "通用"},
                {"ONLINE", "在线支付", "通用"},
        };
        int sort = 1;
        for (String[] m : modes) {
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM settlement_mode WHERE settle_mode_code = ?", Integer.class, m[0]);
            if (c > 0) { sort++; continue; }
            jdbcTemplate.update(
                    "INSERT INTO settlement_mode (settle_mode_code, settle_mode_name, settle_type, sort_order, status, is_enabled, created_by) " +
                            "VALUES (?, ?, ?, ?, '启用', 1, ?)",
                    m[0], m[1], m[2], sort++, user);
            created++;
        }
        return created;
    }

    /** 价格等级 */
    private int ensurePriceLevels(String user) {
        int created = 0;
        String[][] levels = {
                {"PL01", "标准价格"},
                {"PL02", "零售价"},
                {"PL03", "批发价"},
                {"PL04", "VIP价"},
        };
        int sort = 1;
        for (String[] l : levels) {
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM price_level WHERE price_level_code = ?", Integer.class, l[0]);
            if (c > 0) { sort++; continue; }
            jdbcTemplate.update(
                    "INSERT INTO price_level (price_level_code, price_level_name, sort_order, status, is_enabled, created_by) " +
                            "VALUES (?, ?, ?, '启用', 1, ?)",
                    l[0], l[1], sort++, user);
            created++;
        }
        return created;
    }

    /** 币种 */
    private int ensureCurrencies(String user) {
        int created = 0;
        // {code, name, symbol, isBase, isDefault, rate}
        Object[][] curs = {
                {"CNY", "人民币", "¥", 1, 1, "1"},
                {"USD", "美元", "$", 0, 0, "7.2"},
                {"EUR", "欧元", "€", 0, 0, "7.8"},
                {"HKD", "港币", "HK$", 0, 0, "0.92"},
                {"JPY", "日元", "¥", 0, 0, "0.048"},
        };
        for (Object[] cur : curs) {
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM currency WHERE currency_code = ?", Integer.class, cur[0]);
            if (c > 0) continue;
            jdbcTemplate.update(
                    "INSERT INTO currency (currency_code, currency_name, currency_symbol, is_base_currency, is_default, exchange_rate, status, is_enabled, created_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, '启用', 1, ?)",
                    cur[0], cur[1], cur[2], cur[3], cur[4], new java.math.BigDecimal((String) cur[5]), user);
            created++;
        }
        return created;
    }

    /** 物料分类（平铺一级分类；幂等按 category_code 判重） */
    private int ensureMaterialCategories(String user) {
        int created = 0;
        String[][] cats = {
                {"MC01", "原材料"},
                {"MC02", "半成品"},
                {"MC03", "成品"},
                {"MC04", "包装物"},
                {"MC05", "辅料"},
                {"MC06", "外购件"},
        };
        int sort = 1;
        for (String[] cat : cats) {
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM material_category WHERE category_code = ?", Integer.class, cat[0]);
            if (c > 0) { sort++; continue; }
            jdbcTemplate.update(
                    "INSERT INTO material_category (category_code, category_name, parent_id, category_level, sort_order, status, created_by) " +
                            "VALUES (?, ?, NULL, 1, ?, '启用', ?)",
                    cat[0], cat[1], sort++, user);
            created++;
        }
        return created;
    }

    /** 系统字段叫法（只补缺：用户已改过的名字绝不动） */
    private Map<String, String> applyFieldRenames(IndustryCatalog.Industry ind) {
        Map<String, String> applied = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : ind.fieldRenames.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty()) continue;
            int c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_field_label WHERE field_key = ?", Integer.class, e.getKey());
            if (c > 0) continue;
            jdbcTemplate.update(
                    "INSERT INTO sys_field_label (field_key, display_name, updated_by) VALUES (?, ?, ?)",
                    e.getKey(), e.getValue(), user_());
            applied.put(e.getKey(), e.getValue());
        }
        return applied;
    }

    // ==================== 基础设施 ====================

    /** 字段显示名表（幂等建表；主库/租户库/初始化时三重兜底） */
    public static void ensureFieldLabelTableOn(JdbcTemplate target) {
        target.execute("CREATE TABLE IF NOT EXISTS sys_field_label ("
                + " id BIGSERIAL PRIMARY KEY,"
                + " field_key VARCHAR(100) NOT NULL UNIQUE,"
                + " display_name VARCHAR(100) NOT NULL,"
                + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " updated_by VARCHAR(50))");
    }

    private void ensureFieldLabelTable() {
        ensureFieldLabelTableOn(jdbcTemplate);
    }

    /** 与登录下发口径完全一致：默认租户(ID=1)不受版本限制=ULTIMATE；其他租户取 sys_tenant.version */
    private String tenantVersion() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || platformJdbcTemplate == null) return null;
        if (tenantId == 1L) return "ULTIMATE";
        try {
            List<Map<String, Object>> rows = platformJdbcTemplate.queryForList(
                    "SELECT version FROM sys_tenant WHERE tenant_id = ?", tenantId);
            if (rows.isEmpty()) return null;
            Object v = rows.get(0).get("version");
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            log.warn("[行业初始化] 读取租户版本失败（按不过滤处理）: {}", e.getMessage());
            return null;
        }
    }

    /** 版本过滤片段（与登录下发口径一致：STANDARD=标准+通用，CRM=标准+CRM+通用，旗舰=全部） */
    private static String versionFilter(String version) {
        if (version == null) return "1=1";
        switch (version.toUpperCase()) {
            case "STANDARD": return "(m.version IS NULL OR m.version = 'STANDARD')";
            case "CRM": return "(m.version IS NULL OR m.version IN ('STANDARD','CRM'))";
            default: return "1=1";
        }
    }

    private Long findRoleId(String roleCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT role_id FROM sys_role WHERE role_code = ? AND is_enabled = 1", roleCode);
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("role_id")).longValue();
    }

    /** 模板种子用显式 ID 插入不推进序列；补建角色前同步防主键冲突（与建租户链路同坑同修） */
    private void syncRoleIdSequence() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT setval(pg_get_serial_sequence('sys_role','role_id'), " +
                            "(SELECT COALESCE(MAX(role_id),1) FROM sys_role))", Long.class);
        } catch (Exception e) {
            log.warn("[行业初始化] 同步角色ID序列失败(不阻断): {}", e.getMessage());
        }
    }

    private boolean columnExists(String table, String column) {
        try {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void upsertConfig(String key, String value) {
        // 走配置服务 upsert：写库同时同步按租户隔离的进程内缓存。
        // 历史坑（2026-10-12 向导 UI 实测抓出）：原先 jdbcTemplate 直写库，缓存里的旧状态
        // （pending）一直活着 → 初始化完成后刷新页面又弹向导，直到缓存过期。
        sysConfigService.updateValue(key, value);
    }

    /** 当前登录人（取不到 = 系统代执行，如建租户自动初始化） */
    private static String user_() {
        try {
            org.springframework.web.context.request.RequestAttributes attrs =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                String u = com.erp.common.util.SecurityUtils.getCurrentUsername(sra.getRequest());
                if (u != null && !u.trim().isEmpty()) return u.trim();
            }
        } catch (Exception ignore) {
            // 无请求上下文（建租户自动初始化）走兜底
        }
        return "开账引导";
    }
}
