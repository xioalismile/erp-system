package com.erp.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

/**
 * 测试环境配置 + 多租户闸门工具（2026-09-20 新增）
 *
 * ============ 多租户铁律（勿回退）============
 * 1) 禁止写死 `admin` / `Erp@2026`：平台超管登录返回的 token **不含 tenantId**
 *    （JwtUtil.generateToken 不带该 claim），TenantRoutingDataSource 取不到 dbName 时
 *    一律回落 default 池 = 主库 erp_db → 测试数据会静默污染主库。
 * 2) 必须走三步闸门：login → POST /api/tenant/select → 用**新 token** 发业务请求。
 * 3) 目标环境与账号口令一律从系统属性/环境变量读，代码零凭据。
 *
 * 配置示例（PowerShell）：
 *   mvn -o test -Dmaven.compiler.useIncrementalCompilation=false `
 *       -Derp.test.base-url=https://kaiyu.pro `
 *       -Derp.test.admin-user=&lt;租户管理员账号&gt; -Derp.test.admin-password=&lt;口令&gt; `
 *       -Derp.test.tenant-code=&lt;租户编码&gt;
 */
public final class TestEnv {

    private TestEnv() {
    }

    /** 读配置：系统属性优先，其次环境变量，最后默认值 */
    private static String prop(String sysKey, String envKey, String dflt) {
        String v = System.getProperty(sysKey);
        if (v == null || v.isEmpty()) v = System.getenv(envKey);
        return (v == null || v.isEmpty()) ? dflt : v;
    }

    /** 被测服务地址 */
    public static String baseUrl() {
        return prop("erp.test.base-url", "ERP_TEST_BASE_URL", "http://localhost:8080");
    }

    /** 测试租户管理员账号（禁止使用平台超管 admin，其 token 无 tenantId 会写主库） */
    public static String tenantAdminUser() {
        return prop("erp.test.admin-user", "ERP_TEST_ADMIN_USER", "");
    }

    /** 测试租户管理员口令 */
    public static String tenantAdminPassword() {
        return prop("erp.test.admin-password", "ERP_TEST_ADMIN_PASSWORD", "");
    }

    /** 目标租户编码；为空时若账号只归属 1 个租户则自动选中该租户 */
    public static String tenantCode() {
        return prop("erp.test.tenant-code", "ERP_TEST_TENANT_CODE", "");
    }

    /** 租户管理员用户 ID（自签 token 用） */
    public static String tenantUserId() {
        return prop("erp.test.tenant-admin-id", "ERP_TEST_TENANT_ADMIN_ID", "1");
    }

    /** 租户管理员用户名（自签 token / X-Operator 兜底用） */
    public static String tenantUserName() {
        return prop("erp.test.tenant-admin-name", "ERP_TEST_TENANT_ADMIN_NAME", "admin");
    }

    /** 本地开发自签 JWT 的密钥 */
    public static String jwtSecret() {
        return prop("erp.test.jwt-secret", "ERP_TEST_JWT_SECRET", "DevLocalFixedJwtSecretKey_Erp2026_DoNotChange");
    }

    /** X-Operator 请求头取值：优先租户管理员账号，未配置时退回租户管理员用户名 */
    public static String operatorName() {
        String u = tenantAdminUser();
        return u.isEmpty() ? tenantUserName() : u;
    }

    /**
     * 登录并完成租户选择（三步闸门），返回**带 tenantId** 的 token。
     * 凭据为空或任一步失败一律抛 IllegalStateException，不做静默降级。
     */
    public static String loginAndSelectTenant(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalStateException(
                    "缺少测试租户管理员凭据 —— 本套测试禁止使用平台超管 admin（其 token 无 tenantId，业务请求会落主库 erp_db）。\n" +
                    "请通过系统属性或环境变量提供：\n" +
                    "  -Derp.test.admin-user=<租户管理员账号>  -Derp.test.admin-password=<口令>  -Derp.test.tenant-code=<租户编码>\n" +
                    "或环境变量 ERP_TEST_ADMIN_USER / ERP_TEST_ADMIN_PASSWORD / ERP_TEST_TENANT_CODE");
        }
        String target = baseUrl();
        Response login = RestAssured.given()
                .baseUri(target)
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/api/auth/login");
        if (login.statusCode() != 200) {
            throw new IllegalStateException("登录失败: " + username + " HTTP " + login.statusCode()
                    + " body=" + login.asString());
        }
        String loginToken = login.path("data.token");
        if (loginToken == null || loginToken.isEmpty()) {
            throw new IllegalStateException("登录失败: token 为空, user=" + username + " body=" + login.asString());
        }
        // 闸门第 3 步：必须选租户，否则后续请求会落主库
        return selectTenant(target, loginToken, username);
    }

    /** 查询我的租户并选中 tenantCode() 指定的那一个，换回带 tenantId 的 token */
    @SuppressWarnings("unchecked")
    private static String selectTenant(String target, String loginToken, String username) {
        Response my = RestAssured.given()
                .baseUri(target)
                .header("Authorization", "Bearer " + loginToken)
                .when()
                .get("/api/tenant/my");
        if (my.statusCode() != 200) {
            throw new IllegalStateException("查询我的租户失败: HTTP " + my.statusCode() + " body=" + my.asString());
        }
        List<Map<String, Object>> list = null;
        Object data = my.jsonPath().get("data");
        if (data instanceof List) list = (List<Map<String, Object>>) data;
        if (list == null) list = List.of();

        String code = tenantCode();
        Map<String, Object> targetTenant = null;
        for (Map<String, Object> t : list) {
            Object c = t.get("tenantCode");
            if (!code.isEmpty() && c != null && c.toString().equals(code)) {
                targetTenant = t;
                break;
            }
        }
        if (targetTenant == null && code.isEmpty() && list.size() == 1) targetTenant = list.get(0);
        if (targetTenant == null) {
            StringBuilder codes = new StringBuilder();
            for (Map<String, Object> t : list) codes.append(t.get("tenantCode")).append(' ');
            throw new IllegalStateException("找不到测试租户 tenantCode=\"" + code + "\"（账号 " + username
                    + "）。实际可选: " + (codes.length() == 0 ? "(空)" : codes.toString().trim()));
        }
        Object tid = targetTenant.get("tenantId");
        if (tid == null) {
            throw new IllegalStateException("租户记录缺少 tenantId: " + targetTenant);
        }
        Long tenantId = (tid instanceof Number) ? ((Number) tid).longValue() : Long.valueOf(tid.toString());

        Response sel = RestAssured.given()
                .baseUri(target)
                .header("Authorization", "Bearer " + loginToken)
                .contentType(ContentType.JSON)
                .body(Map.of("tenantId", tenantId))
                .when()
                .post("/api/tenant/select");
        if (sel.statusCode() != 200) {
            throw new IllegalStateException("选择租户失败: HTTP " + sel.statusCode() + " body=" + sel.asString());
        }
        Object t = null;
        try {
            t = sel.jsonPath().get("data.token");
        } catch (Exception ignore) {
            // data 直接是字符串 token 的返回形态
        }
        String newToken = (t == null) ? sel.jsonPath().getString("data") : t.toString();
        if (newToken == null || newToken.isEmpty()) {
            throw new IllegalStateException("选择租户后未返回新 token: " + sel.asString());
        }
        System.out.println("[TestEnv] 已选定租户: id=" + tenantId + " code=" + targetTenant.get("tenantCode")
                + " 账号=" + username + " 目标=" + target);
        return newToken;
    }
}
