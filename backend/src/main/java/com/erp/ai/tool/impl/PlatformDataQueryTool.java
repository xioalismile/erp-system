package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.config.service.SysConfigService;
import com.erp.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台级数据只读查询工具（仅平台管理员可用）。
 *
 * <p>背景：小楷原先把「租户」「部署版本」当普通业务对象登记进通用查询框架，但这两类数据
 * <b>不在租户库里</b>（租户登记在平台库 sys_tenant，部署版本登记在部署控制台库），
 * 走通用查询会「查了报错 / 查了空」——属"假能力"。本工具改为调用平台侧既有服务取数：
 * <ul>
 *   <li>type=tenant         → 租户服务（平台库 platformJdbcTemplate）</li>
 *   <li>type=deploy_version → 部署版本服务 listVersions（部署控制台库）</li>
 * </ul>
 *
 * <p><b>重要（2026-10-01 修复）</b>：部署模块 {@code com.erp.deploy} 在客户包（{@code mvn -P customer}，
 * 121 客户部署使用）被编译期整体排除，pom 明文规定"该包内的类不得被其他包引用"。故此处
 * <b>不在编译期依赖该包</b>，改为运行期按类名反射获取（{@link #loadDeployVersions()}）：
 * 全量包正常返回，客户包取不到则如实降级为"当前版本不含部署升级模块"。
 *
 * <p>安全：只接受固定 type 白名单（tenant / deploy_version），<b>不接受任意表名</b>，杜绝注入；
 * 仅平台管理员（SUPER_ADMIN / ADMIN 或通配权限）可执行；总开关
 * {@code ai.tool.platform_query.enabled} 默认关闭（与其它新增能力一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformDataQueryTool implements AiTool {

    /** 工具名（ToolRegistry 暴露/权限判定共用同一常量字符串） */
    public static final String NAME = "platform_data_query";
    /** 工具总开关（默认关闭） */
    public static final String SWITCH_KEY = "ai.tool.platform_query.enabled";
    /** 部署版本服务的全限定类名（客户包不存在该类，故用字符串反射，避免编译期依赖） */
    private static final String DEPLOY_VERSION_SERVICE_CLASS = "com.erp.deploy.service.DeployVersionService";

    private static final String TYPE_TENANT = "tenant";
    private static final String TYPE_DEPLOY_VERSION = "deploy_version";
    private static final Set<String> ALLOWED_TYPES = Set.of(TYPE_TENANT, TYPE_DEPLOY_VERSION);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenantService tenantService;
    private final SysConfigService sysConfigService;
    private final ApplicationContext applicationContext;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "【平台级数据查询】查询平台管理数据，仅平台/超级管理员可用。"
                + "type=tenant 查租户（客户公司）清单（编码/名称/管理员/用户数/状态/到期日/版本）；"
                + "type=deploy_version 查部署版本库（版本号/包类型/说明/大小/操作人/时间）。"
                + "这两类数据属于平台级、不在业务对象清单里，只能通过本工具查询。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("type", "string");
        type.put("description", "要查询的平台数据类型，只能取 tenant（租户）或 deploy_version（部署版本）");
        type.put("enum", List.of(TYPE_TENANT, TYPE_DEPLOY_VERSION));
        props.put("type", type);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("type"));
        return params;
    }

    /** 无权限上下文（旧签名）一律拒绝：平台数据必须携带登录身份判定。 */
    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return Map.of("success", false, "message", "无权限：平台级数据（租户/部署版本）仅平台管理员可查询");
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        if (!isPlatformAdmin(scope)) {
            return Map.of("success", false, "message", "无权限：平台级数据（租户/部署版本）仅平台管理员可查询");
        }
        if (!sysConfigService.getBooleanValue(SWITCH_KEY, false)) {
            return Map.of("success", false, "message", "平台级数据查询已被后台关闭（" + SWITCH_KEY + "=false）");
        }
        String type = params == null ? null : String.valueOf(params.get("type"));
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            return Map.of("success", false, "message", "type 只能取 tenant（租户）或 deploy_version（部署版本）");
        }
        try {
            if (TYPE_TENANT.equals(type)) {
                return queryTenants();
            }
            return queryDeployVersions();
        } catch (Exception e) {
            log.error("[AI平台级查询] 失败 type={}", type, e);
            return Map.of("success", false, "message", "查询失败: " + e.getMessage());
        }
    }

    /**
     * 平台管理员判定：与 AuthInterceptor 对 /api/admin/** 的 fullAdmin 口径一致
     * （SUPER_ADMIN / ADMIN 角色，或通配权限 '*'），<b>不新造权限码</b>。
     */
    private boolean isPlatformAdmin(AiDataScope scope) {
        if (scope == null) {
            return false;
        }
        List<String> roles = scope.getRoles();
        if (roles != null && (roles.contains("SUPER_ADMIN") || roles.contains("ADMIN"))) {
            return true;
        }
        return scope.hasPermission("*");
    }

    private Map<String, Object> queryTenants() {
        List<Map<String, Object>> rows = tenantService.getAllTenants(null);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tenantId", r.get("tenant_id"));
            item.put("tenantCode", blankIfNull(r.get("tenant_code")));
            item.put("tenantName", blankIfNull(r.get("tenant_name")));
            item.put("adminUsername", blankIfNull(r.get("admin_username")));
            item.put("userCount", r.get("user_count"));
            item.put("maxUsers", r.get("max_users"));
            item.put("status", blankIfNull(r.get("status")));
            item.put("expireDate", blankIfNull(r.get("expire_date")));
            item.put("version", blankIfNull(r.get("version")));
            data.add(item);
        }
        return result(TYPE_TENANT, data);
    }

    private Map<String, Object> queryDeployVersions() {
        List<?> versions = loadDeployVersions();
        if (versions == null) {
            return Map.of("success", false, "message",
                    "当前版本不含「部署升级」模块（客户包已裁剪），无法查询部署版本");
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Object v : versions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("versionNo", blankIfNull(invokeGetter(v, "getVersionNo")));
            item.put("buildMode",
                    "customer".equals(String.valueOf(invokeGetter(v, "getBuildMode"))) ? "客户包" : "全量包");
            item.put("note", blankIfNull(invokeGetter(v, "getNote")));
            Object size = invokeGetter(v, "getZipSize");
            item.put("zipSizeMb", size instanceof Number
                    ? String.format("%.2f", ((Number) size).doubleValue() / 1024.0 / 1024.0)
                    : null);
            item.put("operator", blankIfNull(invokeGetter(v, "getOperator")));
            Object createdAt = invokeGetter(v, "getCreatedAt");
            item.put("createdAt", createdAt instanceof LocalDateTime
                    ? ((LocalDateTime) createdAt).format(DT_FMT)
                    : "");
            data.add(item);
        }
        return result(TYPE_DEPLOY_VERSION, data);
    }

    /**
     * 运行期反射取「部署版本」清单。
     *
     * <p>客户包（{@code mvn -P customer}）在编译期已排除 {@code com.erp.deploy} 包，
     * 此处 {@code Class.forName} 会失败 → 返回 null，由调用方如实降级提示；
     * 全量包正常反射调用 {@code listVersions()}。这样既满足 pom"deploy 包不得被其他包引用"的防护，
     * 又让全量包保留完整能力。
     */
    private List<?> loadDeployVersions() {
        try {
            Class<?> svcClass = Class.forName(DEPLOY_VERSION_SERVICE_CLASS);
            Object svc = applicationContext.getBean(svcClass);
            Object ret = svcClass.getMethod("listVersions").invoke(svc);
            return ret instanceof List ? (List<?>) ret : null;
        } catch (ClassNotFoundException e) {
            log.info("[AI平台级查询] 当前版本不含部署升级模块（com.erp.deploy 已被客户包排除）");
            return null;
        } catch (Exception e) {
            log.warn("[AI平台级查询] 部署版本服务调用失败: {}", e.getMessage());
            return null;
        }
    }

    /** 反射调用无参 getter；失败返回 null（单个字段取不到不应让整次查询失败） */
    private static Object invokeGetter(Object target, String getter) {
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> result(String type, List<Map<String, Object>> data) {
        return Map.of(
                "success", true,
                "type", type,
                "data", data,
                "total", data.size()
        );
    }

    private static String blankIfNull(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
