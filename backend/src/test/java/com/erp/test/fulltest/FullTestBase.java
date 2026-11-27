package com.erp.test.fulltest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;

/**
 * 全方位测试基类
 * 提供: 双角色token(测试租户管理员/普通用户)、通用请求封装、数据清理、断言工具
 *
 * ============ 多租户铁律（2026-09-20 改造，勿回退）============
 * 1) 禁止写死 `admin` / `Erp@2026`：平台超管登录返回的 token **不含 tenantId**
 *    （JwtUtil.generateToken 不带该 claim），而 TenantRoutingDataSource 在取不到 dbName 时
 *    一律回落 default 池 = 主库 erp_db → 测试数据会静默污染主库。
 * 2) 必须走三步闸门：login → POST /api/tenant/select → 用**新 token** 发业务请求。
 * 3) 目标环境与账号口令一律从系统属性/环境变量读，代码零凭据（守则 03）。
 *
 * 配置示例（PowerShell）：
 *   mvn -o test -Derp.test.skip=false -Dmaven.compiler.useIncrementalCompilation=false `
 *       -Derp.test.base-url=https://kaiyu.pro `
 *       -Derp.test.admin-user=autotest_admin `
 *       -Derp.test.admin-password=xxxxxx `
 *       -Derp.test.tenant-code=autotest `
 *       -Dtest=BillExcelTraversalHttpTest
 */
public abstract class FullTestBase {

    protected static final String BASE_URL = prop("erp.test.base-url", "ERP_TEST_BASE_URL", "http://localhost:8080");
    protected static final String TENANT_ADMIN_USER = prop("erp.test.admin-user", "ERP_TEST_ADMIN_USER", "");
    protected static final String TENANT_ADMIN_PASSWORD = prop("erp.test.admin-password", "ERP_TEST_ADMIN_PASSWORD", "");
    protected static final String TENANT_CODE = prop("erp.test.tenant-code", "ERP_TEST_TENANT_CODE", "");
    protected static final String TENANT_USER = prop("erp.test.user-name", "ERP_TEST_USER_NAME", "");
    protected static final String TENANT_USER_PASSWORD = prop("erp.test.user-password", "ERP_TEST_USER_PASSWORD", "");

    protected static String adminToken;
    protected static String userToken;
    /** 已选定的测试租户（null = 未选租户，此时任何业务请求都会落主库） */
    protected static Long tenantId;
    protected static String tenantCode;
    protected static final DataCleaner cleaner = new DataCleaner();

    /** 测试数据唯一前缀；带租户标识，避免跨租户互相清理 */
    protected static final String TEST_PREFIX = "FT"
            + (TENANT_CODE.isEmpty() ? "" : TENANT_CODE.toUpperCase() + "-")
            + System.currentTimeMillis() % 100000;

    /** 读配置：系统属性优先，其次环境变量，最后默认值 */
    private static String prop(String sysKey, String envKey, String dflt) {
        String v = System.getProperty(sysKey);
        if (v == null || v.isEmpty()) v = System.getenv(envKey);
        return (v == null || v.isEmpty()) ? dflt : v;
    }

    @BeforeAll
    static void init() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.urlEncodingEnabled = true;
        if (TENANT_ADMIN_USER.isEmpty() || TENANT_ADMIN_PASSWORD.isEmpty()) {
            throw new IllegalStateException(
                    "缺少测试租户管理员凭据 —— 本套测试禁止使用平台超管 admin（其 token 无 tenantId，会写主库 erp_db）。\n" +
                    "请通过系统属性或环境变量提供：\n" +
                    "  -Derp.test.admin-user=<租户管理员账号>  -Derp.test.admin-password=<口令>  -Derp.test.tenant-code=<租户编码>\n" +
                    "或环境变量 ERP_TEST_ADMIN_USER / ERP_TEST_ADMIN_PASSWORD / ERP_TEST_TENANT_CODE");
        }
        adminToken = login(TENANT_ADMIN_USER, TENANT_ADMIN_PASSWORD);
        // 普通用户 token（权限测试用），未配置则降级为租户管理员，调用方用 Assumptions 跳过权限断言
        userToken = TENANT_USER.isEmpty() ? null : loginSilently(TENANT_USER, TENANT_USER_PASSWORD);
        if (userToken == null) userToken = adminToken;
        System.out.println("[FullTestBase] 初始化完成 target=" + BASE_URL
                + " tenant=" + tenantCode + "(tenantId=" + tenantId + ") prefix=" + TEST_PREFIX);
    }

    /**
     * 登录并完成租户选择（三步闸门），返回**带 tenantId** 的 token。
     * 失败即抛异常，不做任何静默降级。
     */
    protected static String login(String username, String password) {
        Response resp = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/api/auth/login");
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("登录失败: " + username + " HTTP " + resp.statusCode());
        }
        String loginToken = resp.path("data.token");
        if (loginToken == null || loginToken.isEmpty()) {
            throw new IllegalStateException("登录失败: token 为空, user=" + username);
        }
        // 闸门第 3 步：必须选租户，否则后续请求会落主库
        return selectTenant(loginToken, username);
    }

    /** 查询我的租户并选中 TENANT_CODE 指定的那一个，换回带 tenantId 的 token */
    @SuppressWarnings("unchecked")
    private static String selectTenant(String loginToken, String username) {
        if (tenantId == null) {
            Response my = RestAssured.given().header("Authorization", "Bearer " + loginToken)
                    .when().get("/api/tenant/my");
            List<Map<String, Object>> list = null;
            try {
                Object data = my.jsonPath().get("data");
                if (data instanceof List) list = (List<Map<String, Object>>) data;
            } catch (Exception ignore) {
                // 保持 list = null，下面统一按"未找到"处理
            }
            if (list == null) list = List.of();
            Map<String, Object> target = null;
            for (Map<String, Object> t : list) {
                Object code = t.get("tenantCode");
                if (code != null && code.toString().equals(TENANT_CODE)) { target = t; break; }
            }
            if (target == null && list.size() == 1 && TENANT_CODE.isEmpty()) target = list.get(0);
            if (target == null) {
                StringBuilder codes = new StringBuilder();
                for (Map<String, Object> t : list) codes.append(t.get("tenantCode")).append(' ');
                throw new IllegalStateException("找不到测试租户 tenantCode=\"" + TENANT_CODE
                        + "\"（账号 " + username + "）。实际可选: " + (codes.length() == 0 ? "(空)" : codes.toString().trim()));
            }
            Object tid = target.get("tenantId");
            tenantId = (tid instanceof Number) ? ((Number) tid).longValue() : Long.valueOf(String.valueOf(tid));
            tenantCode = String.valueOf(target.get("tenantCode"));
        }
        Response sel = RestAssured.given().header("Authorization", "Bearer " + loginToken)
                .contentType(ContentType.JSON)
                .body(Map.of("tenantId", tenantId))
                .when().post("/api/tenant/select");
        if (sel.statusCode() != 200) {
            throw new IllegalStateException("选择租户失败: HTTP " + sel.statusCode() + " body=" + sel.asString());
        }
        Object t = sel.jsonPath().get("data.token");
        String newToken = (t == null) ? sel.jsonPath().getString("data") : t.toString();
        if (newToken == null || newToken.isEmpty()) {
            throw new IllegalStateException("选择租户后未返回新 token: " + sel.asString());
        }
        return newToken;
    }

    /** 静默登录, 失败返回 null（用于可选的普通用户账号） */
    protected static String loginSilently(String username, String password) {
        try {
            return login(username, password);
        } catch (Exception e) {
            System.out.println("[FullTestBase] 静默登录失败(该账号未配置/不可用): " + username + " → " + e.getMessage());
            return null;
        }
    }

    // ==================== admin 请求方法 ====================

    protected Response authGet(String path) {
        return RestAssured.given().header("Authorization", "Bearer " + adminToken).when().get(path);
    }

    protected Response authGet(String path, Map<String, ?> params) {
        return RestAssured.given().header("Authorization", "Bearer " + adminToken)
                .queryParams(params).when().get(path);
    }

    protected Response authPost(String path, Object body) {
        return RestAssured.given().header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON).body(body).when().post(path);
    }

    protected Response authPut(String path, Object body) {
        return RestAssured.given().header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON).body(body).when().put(path);
    }

    protected Response authDelete(String path) {
        return RestAssured.given().header("Authorization", "Bearer " + adminToken).when().delete(path);
    }

    // ==================== 普通用户请求方法(权限测试用) ====================

    protected Response userGet(String path) {
        return RestAssured.given().header("Authorization", "Bearer " + userToken).when().get(path);
    }

    protected Response userPost(String path, Object body) {
        return RestAssured.given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON).body(body).when().post(path);
    }

    protected Response userDelete(String path) {
        return RestAssured.given().header("Authorization", "Bearer " + userToken).when().delete(path);
    }

    // ==================== 辅助方法 ====================

    /** 获取列表中第一条记录的ID */
    protected Long getFirstId(String listPath) {
        Response resp = authGet(listPath);
        if (resp.statusCode() == 200) {
            Object id = resp.path("data.records[0].id");
            if (id == null) id = resp.path("data.records[0].materialId");
            if (id == null) id = resp.path("data.records[0].customerId");
            if (id == null) id = resp.path("data.records[0].supplierId");
            if (id == null) id = resp.path("data.records[0].requisitionId");
            if (id == null) id = resp.path("data.records[0].orderId");
            if (id != null) return ((Number) id).longValue();
        }
        return null;
    }

    /** 生成唯一编码 */
    protected static String uniqueCode(String prefix) {
        return prefix + "-" + TEST_PREFIX + "-" + System.nanoTime() % 10000;
    }
}
