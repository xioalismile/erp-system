package com.erp.test.fulltest;

import com.erp.export.registry.BillExcelRegistry;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入导出「全实体遍历」端到端回归测试：遍历 {@link BillExcelRegistry} 里**每一个**实体，
 * 真实调用后端接口，并把响应体**用 POI 真正解析一遍**，断言它是一张有表头、有非空单元格的 xlsx。
 *
 * <p><b>为什么必须解析内容，而不是只看状态码/大小</b>——历史上这类故障有三种都返回 HTTP 200：</p>
 * <ol>
 *   <li>实体漏登记 → body 是 {@code {"code":400,"message":"不支持的实体类型: xxx"}}（2026-09-15 实锤）；</li>
 *   <li>EasyExcel 依赖升级 → body 是合法 xlsx 但数据行单元格全空（2026-09-14 实锤，28KB 的"空"文件同样 200）；</li>
 *   <li>实体零个 {@code @ExportField}（如库存现存量）→ body 是合法 xlsx，20 行 0 单元格（2026-09-15 实测）。</li>
 * </ol>
 * <p>所以判据 = 「HTTP 200 + ZIP 魔数 PK + POI 可解析 + 表头非空 + 非空单元格数 &gt; 0」。</p>
 *
 * <p><b>鉴权方式</b>：不依赖任何账号密码（本地库 admin 口令未必是测试基类里的那个），直接用后端默认
 * 开发密钥自签 tenant1/admin 的 HS256 token；环境改过密钥时用 {@code -Derp.test.jwt-secret=xxx} 覆盖。</p>
 *
 * <p>跑法（项目默认跳过测试，且需要后端已在 8080 运行）：
 * {@code mvn -o test -Derp.test.skip=false -Dmaven.compiler.useIncrementalCompilation=false -Dtest=BillExcelTraversalHttpTest}</p>
 */
@DisplayName("导入导出全实体遍历（HTTP 端到端）")
class BillExcelTraversalHttpTest {

    private static final String BASE_URL = System.getProperty("erp.test.base-url", "http://localhost:8080");
    private static final String JWT_SECRET =
            System.getProperty("erp.test.jwt-secret", "DevLocalFixedJwtSecretKey_Erp2026_DoNotChange");
    private static final int MIN_XLSX_BYTES = 1000;

    private static String token;

    @BeforeAll
    static void init() throws Exception {
        RestAssured.baseURI = BASE_URL;
        token = acquireToken();
    }

    @Test
    @DisplayName("遍历登记表每个可导出实体：模板与数据必须都是「有表头且有值」的真 xlsx")
    void everyRegisteredEntityExportsRealXlsx() {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (BillExcelRegistry.Bill bill : BillExcelRegistry.all()) {
            if (!bill.exportable()) {
                continue;
            }
            checked++;
            Response tpl = RestAssured.given().header("Authorization", "Bearer " + token)
                    .when().get("/api/export/template/" + bill.code());
            collect(failures, bill, "下载模板", tpl);

            Response data = RestAssured.given().header("Authorization", "Bearer " + token)
                    .when().post("/api/export/data/" + bill.code());
            collect(failures, bill, "导出数据", data);
        }
        assertTrue(checked > 50, "遍历到的可导出实体只有 " + checked + " 个，登记表疑似被截断");
        assertEquals(0, failures.size(),
                "以下实体导出没有产出「有表头且有值」的真 xlsx：\n  " + String.join("\n  ", failures));
    }

    @Test
    @DisplayName("遍历登记表每个可导入实体：导入模板字段清单接口必须可用")
    void everyImportableEntityHasTemplateFields() {
        List<String> failures = new ArrayList<>();
        for (BillExcelRegistry.Bill bill : BillExcelRegistry.all()) {
            if (!bill.importable()) {
                continue;
            }
            Response resp = RestAssured.given().header("Authorization", "Bearer " + token)
                    .when().get("/api/export/template/" + bill.code() + "/fields");
            if (resp.statusCode() != 200) {
                failures.add(bill.code() + " http=" + resp.statusCode() + " body=" + brief(resp.asByteArray()));
            }
        }
        assertEquals(0, failures.size(),
                "以下实体的「导入模板字段清单」接口不可用（导入弹窗的自定义模板字段会打不开）：\n  "
                        + String.join("\n  ", failures));
    }

    private void collect(List<String> failures, BillExcelRegistry.Bill bill, String action, Response resp) {
        byte[] body = resp.asByteArray();
        String problem = null;
        if (resp.statusCode() != 200) {
            problem = "http=" + resp.statusCode();
        } else if (body.length < 2 || body[0] != 'P' || body[1] != 'K') {
            problem = "返回体不是 xlsx（ZIP 魔数缺失）";
        } else if (body.length < MIN_XLSX_BYTES) {
            problem = "文件过小 " + body.length + "B";
        } else {
            problem = inspectXlsx(body);
        }
        if (problem != null) {
            failures.add(bill.code() + "(" + bill.name() + ") " + action + " -> " + problem
                    + " | bytes=" + body.length + " body=" + brief(body));
        }
    }

    /**
     * 真解析一遍：能打开、表头非空、全表至少 1 个非空单元格。
     * @return null=通过；否则返回问题描述
     */
    private String inspectXlsx(byte[] body) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            int headerCells = header == null ? 0 : header.getPhysicalNumberOfCells();
            if (headerCells == 0) {
                return "xlsx 表头为空（登记了实体但没有任何导出列）";
            }
            DataFormatter fmt = new DataFormatter();
            int valueCells = 0;
            for (Row row : sheet) {
                for (Cell cell : row) {
                    try {
                        if (cell != null && !fmt.formatCellValue(cell).isBlank()) {
                            valueCells++;
                        }
                    } catch (Exception ignore) {
                        // 公式/富文本等个别单元格取值异常不影响整体判定
                    }
                }
            }
            if (valueCells == 0) {
                return "xlsx 全表 0 个非空单元格（有行无数据）";
            }
            return null;
        } catch (Exception e) {
            return "无法解析为 xlsx: " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
    }

    private String brief(byte[] body) {
        if (body == null || body.length == 0) {
            return "(empty)";
        }
        return new String(body, 0, Math.min(160, body.length), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    /**
     * 取得可用于本环境、且**已带 tenantId** 的 token。
     *
     * 获取顺序（2026-09-20 多租户改造）：
     *   ① 若配置了租户管理员账号（-Derp.test.admin-user/-password）→ 真实登录 + /api/tenant/select。
     *      这是**唯一**能保证请求落在测试租户库（而非主库 erp_db）的方式。
     *   ② 否则降级为自签 token —— 仅用于本机开发库，tenantId 用 -Derp.test.tenant-id 指定，
     *      默认 1（即默认租户 erp_db）。**禁止**用自签方式打 8cloud 等非本机环境。
     */
    private static String acquireToken() throws Exception {
        String user = System.getProperty("erp.test.admin-user", "");
        String pwd = System.getProperty("erp.test.admin-password", "");
        if (!user.isEmpty() && !pwd.isEmpty()) {
            return realLoginToken(user, pwd);
        }
        System.out.println("[BillExcelTraversalHttpTest] 未配置租户管理员账号，降级为自签 token"
                + "（仅适用于本机开发库；打 8cloud 请务必配置 -Derp.test.admin-user/-password）");
        return selfSignedToken();
    }

    /** 真实登录 + 选租户（三步闸门），返回带 tenantId 的 token */
    @SuppressWarnings("unchecked")
    private static String realLoginToken(String username, String password) {
        Response login = RestAssured.given().contentType(ContentType.JSON)
                .body(java.util.Map.of("username", username, "password", password))
                .when().post("/api/auth/login");
        if (login.statusCode() != 200) {
            throw new IllegalStateException("登录失败: " + username + " HTTP " + login.statusCode());
        }
        String loginToken = login.path("data.token");
        if (loginToken == null || loginToken.isEmpty()) {
            throw new IllegalStateException("登录成功但未返回 token: " + login.asString());
        }
        String wantCode = System.getProperty("erp.test.tenant-code", "");
        Response my = RestAssured.given().header("Authorization", "Bearer " + loginToken)
                .when().get("/api/tenant/my");
        java.util.List<java.util.Map<String, Object>> list = null;
        Object data = my.jsonPath().get("data");
        if (data instanceof java.util.List) list = (java.util.List<java.util.Map<String, Object>>) data;
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException("账号 " + username + " 未关联任何租户，无法确定测试租户。/api/tenant/my → " + my.asString());
        }
        java.util.Map<String, Object> target = null;
        for (java.util.Map<String, Object> t : list) {
            if (wantCode.equals(String.valueOf(t.get("tenantCode")))) { target = t; break; }
        }
        if (target == null && list.size() == 1) target = list.get(0);
        if (target == null) {
            throw new IllegalStateException("找不到测试租户 tenantCode=\"" + wantCode + "\"。实际: " + list.size() + " 个");
        }
        Object tid = target.get("tenantId");
        Response sel = RestAssured.given().header("Authorization", "Bearer " + loginToken)
                .contentType(ContentType.JSON)
                .body(java.util.Map.of("tenantId", tid))
                .when().post("/api/tenant/select");
        if (sel.statusCode() != 200) {
            throw new IllegalStateException("选择租户失败: HTTP " + sel.statusCode() + " body=" + sel.asString());
        }
        Object t2 = sel.jsonPath().get("data.token");
        String token = (t2 == null) ? sel.jsonPath().getString("data") : t2.toString();
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("选择租户后未返回新 token: " + sel.asString());
        }
        System.out.println("[BillExcelTraversalHttpTest] 已选定测试租户 tenantCode="
                + target.get("tenantCode") + " tenantId=" + tid);
        return token;
    }

    /**
     * 用后端默认开发密钥自签 HS256 token（claims 口径与 SecurityUtils 一致）。
     * 注意：tenantId 与 username 均可配置，禁止再写死 —— 写死 tenantId=1 会让请求落到主库 erp_db，
     * 自签 token 打到 8cloud 时会变成"在客户/自营主库上跑导入导出遍历"。
     */
    private static String selfSignedToken() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        String sub = System.getProperty("erp.test.tenant-admin-id", "1");
        String username = System.getProperty("erp.test.tenant-admin-name", "admin");
        long tid = Long.parseLong(System.getProperty("erp.test.tenant-id", "1"));
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"" + sub + "\",\"username\":\"" + username + "\",\"tenantId\":" + tid
                + ",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}";
        String data = b64(header) + "." + b64(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return data + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
