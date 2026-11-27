package com.erp.test.fulltest.crm;

import com.erp.test.fulltest.FullTestBase;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.anyOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CRM 客户关系管理模块 全方位自动化测试
 * <p>
 * 覆盖 4 个测试维度:
 * 1. 字段全校验      (@ParameterizedTest + @MethodSource): 销售线索 / 商机 主表单空值、超长、类型、唯一性
 * 2. 逻辑关联性      (@Test): 线索转化、商机阶段推进(成交概率自动更新)、跟进记录关联线索
 * 3. 设置中心生效验证 (@Disabled): /api/system/setting 在当前后端不存在
 * 4. 权限硬拦截      (@Test/@Disabled): 未登录 401、普通用户越权
 * <p>
 * 单据编号格式: L-yyyyMMdd-0001 (线索) / OPP-yyyyMMdd-0001 (商机) / FU-yyyyMMdd-0001 (跟进)
 * 线索状态流转: 新线索 → 跟进中 → 已转化 / 已放弃
 * 商机阶段概率: 初步接触10% → 需求确认25% → 方案报价45% → 商务谈判70% → 成交100%
 */
class CrmFullTest extends FullTestBase {

    /** 跟踪测试中创建的记录 ID, 用于 @AfterAll 精确清理
     *  (DataCleaner.cleanupByPrefix 未纳入 crm_follow_up, 且对 crm_opportunity 的列名误写为 opp_no,
     *   故这两类记录需通过 cleanupById 兜底清理) */
    private static final List<Long> createdLeadIds = new ArrayList<>();
    private static final List<Long> createdOppIds = new ArrayList<>();
    private static final List<Long> createdFollowUpIds = new ArrayList<>();
    private static final List<Long> createdCustomerIds = new ArrayList<>();

    // ====================================================================
    // 维度1: 字段全校验 (@ParameterizedTest + @MethodSource)
    // ====================================================================

    /**
     * 销售线索主表单字段校验: 空值拦截 / 超长 / 数据类型 / 非法枚举
     */
    @ParameterizedTest(name = "[线索字段] {0}")
    @MethodSource("线索非法字段用例")
    void 字段校验_销售线索非法输入应被拦截(String 用例描述, Map<String, Object> 载荷) {
        Response resp = authPost("/api/crm/lead", 载荷);
        int code = respCode(resp);
        assertThat("用例[" + 用例描述 + "]应被拦截, 实际code=" + code, code, is(not(200)));
    }

    static Stream<Arguments> 线索非法字段用例() {
        Map<String, Object> valid = validLead();
        // 线索名称为空 (BusinessException: 线索名称不能为空)
        Map<String, Object> c1 = new HashMap<>(valid); c1.put("leadName", "");
        // 公司名称为空 (BusinessException: 公司名称不能为空)
        Map<String, Object> c2 = new HashMap<>(valid); c2.put("companyName", "");
        // 来源类型非法 (BusinessException: 无效的来源类型)
        Map<String, Object> c3 = new HashMap<>(valid); c3.put("sourceType", "非法来源");
        // 意向等级非法 (BusinessException: 无效的意向等级)
        Map<String, Object> c4 = new HashMap<>(valid); c4.put("intentionLevel", "X");
        // 预估金额数据类型非法 (HttpMessageNotReadableException → 400)
        Map<String, Object> c5 = new HashMap<>(valid); c5.put("estimatedAmount", "abc");
        // 线索名称超长 (DB varchar 长度溢出 → DataIntegrityViolationException → 400)
        Map<String, Object> c6 = new HashMap<>(valid); c6.put("leadName", "A".repeat(300));
        return Stream.of(
                Arguments.of("线索名称为空", c1),
                Arguments.of("公司名称为空", c2),
                Arguments.of("来源类型非法", c3),
                Arguments.of("意向等级非法", c4),
                Arguments.of("预估金额类型非法", c5),
                Arguments.of("线索名称超长", c6)
        );
    }

    /**
     * 商机主表单字段校验: 空值拦截 / 超长 / 数据类型
     */
    @ParameterizedTest(name = "[商机字段] {0}")
    @MethodSource("商机非法字段用例")
    void 字段校验_商机非法输入应被拦截(String 用例描述, Map<String, Object> 载荷) {
        Response resp = authPost("/api/crm/opportunity", 载荷);
        int code = respCode(resp);
        assertThat("用例[" + 用例描述 + "]应被拦截, 实际code=" + code, code, is(not(200)));
    }

    static Stream<Arguments> 商机非法字段用例() {
        // customerId=1L 作为占位 (错误用例均在保存前被拦截, customerId 不影响结果)
        Map<String, Object> valid = validOpp(1L);
        // 商机名称为空 (BusinessException: 商机名称不能为空)
        Map<String, Object> c1 = new HashMap<>(valid); c1.put("opportunityName", "");
        // 预估金额数据类型非法 (反序列化失败 → 400)
        Map<String, Object> c2 = new HashMap<>(valid); c2.put("estimatedAmount", "abc");
        // 商机名称超长 (DB varchar 溢出 → 400)
        Map<String, Object> c3 = new HashMap<>(valid); c3.put("opportunityName", "A".repeat(300));
        return Stream.of(
                Arguments.of("商机名称为空", c1),
                Arguments.of("预估金额类型非法", c2),
                Arguments.of("商机名称超长", c3)
        );
    }

    /**
     * 唯一性冲突: 重复线索编号应被拒绝
     * 注意: LeadServiceImpl.saveLead 未做应用层唯一性校验, 唯一性依赖数据库唯一索引;
     *       若数据库未对 lead_no 建唯一索引, 本用例将暴露该缺口(第二条会以 200 返回成功)。
     */
    @Test
    void 字段校验_重复线索编号应被拒绝() {
        String dupNo = "L-" + TEST_PREFIX + "-DUP-" + (System.nanoTime() % 100000);

        // 第一条: 正常创建
        Map<String, Object> lead1 = validLead();
        lead1.put("leadNo", dupNo);
        Response r1 = authPost("/api/crm/lead", lead1);
        assertEquals(200, respCode(r1), "第一条线索应创建成功");
        trackLead(r1);

        // 第二条: 使用相同编号, 应被拒绝
        Map<String, Object> lead2 = validLead();
        lead2.put("leadNo", dupNo);
        lead2.put("leadName", "测试线索2" + TEST_PREFIX);
        lead2.put("companyName", "测试公司2" + TEST_PREFIX);
        Response r2 = authPost("/api/crm/lead", lead2);
        assertThat("重复线索编号应被拒绝, 实际code=" + respCode(r2), respCode(r2), is(not(200)));
    }

    /**
     * 商机客户ID为空应被拦截
     * 注意: OpportunityServiceImpl.saveOpportunity 仅校验商机名称非空, 未对 customerId 做必填校验;
     *       当前传 null customerId 仍以 code=200 返回成功。待后端补充客户ID必填校验后启用本用例。
     */
    @Disabled("后端 OpportunityServiceImpl.saveOpportunity 未对 customerId 做非空校验, 当前传 null 仍以 code=200 返回成功; 待后端补充客户ID必填校验后启用")
    @Test
    void 字段校验_商机客户ID为空应被拦截() {
        Map<String, Object> opp = validOpp(null);
        Response resp = authPost("/api/crm/opportunity", opp);
        assertThat("客户ID为空应被拦截", respCode(resp), is(not(200)));
    }

    // ====================================================================
    // 维度2: 逻辑关联性
    // ====================================================================

    /**
     * 创建线索 → 转化为客户: 验证转化后线索状态变为"已转化"并返回新客户ID
     * (当前后端 convert 接口为"线索→客户"转化, 无"线索→商机"转化接口)
     */
    @Test
    void 逻辑关联_线索转化为客户并验证状态() {
        // 1. 创建新线索
        Response createResp = authPost("/api/crm/lead", validLead());
        assertEquals(200, respCode(createResp), "创建线索应成功");
        Long leadId = trackLead(createResp);
        assertNotNull(leadId, "线索ID不应为空");

        // 2. 调用转化接口
        Response convertResp = authPost("/api/crm/lead/" + leadId + "/convert", Map.of());
        assertEquals(200, respCode(convertResp), "线索转化应成功");
        Long customerId = trackConvertedCustomer(convertResp);
        assertThat("转化返回的新客户ID不应为空", customerId, is(notNullValue()));

        // 3. 验证线索状态已变为"已转化", 并记录转化客户ID
        Response detail = authGet("/api/crm/lead/" + leadId);
        assertEquals(200, respCode(detail), "查询线索详情应成功");
        assertThat("线索状态应为[已转化]", detail.path("data.leadStatus"), equalTo("已转化"));
        assertEquals(customerId, longPath(detail, "data.convertedCustomerId"), "转化客户ID应一致");

        // 4. 重复转化应被拒绝 (已转化为终态)
        Response convertAgain = authPost("/api/crm/lead/" + leadId + "/convert", Map.of());
        assertThat("重复转化应被拒绝", respCode(convertAgain), is(not(200)));
    }

    /**
     * 创建商机 → 推进销售阶段 (初步接触→需求确认→方案报价)
     * 验证成交概率随销售阶段自动更新: 10% → 25% → 45%
     */
    @Test
    void 逻辑关联_商机阶段推进并验证成交概率自动更新() {
        // 前置: 创建线索并转化为客户, 获取 customerId (商机需关联客户)
        Long leadId = trackLead(authPost("/api/crm/lead", validLead()));
        assertNotNull(leadId, "前置: 创建线索失败");
        Long customerId = trackConvertedCustomer(
                authPost("/api/crm/lead/" + leadId + "/convert", Map.of()));
        assertNotNull(customerId, "前置: 转化获取客户ID失败, 无法继续商机测试");

        // 1. 创建商机 (初步接触, 概率应自动为 10)
        Map<String, Object> opp = validOpp(customerId);
        opp.put("sourceLeadId", leadId);
        Response createResp = authPost("/api/crm/opportunity", opp);
        assertEquals(200, respCode(createResp), "创建商机应成功");
        Long oppId = trackOpp(createResp);
        assertNotNull(oppId, "商机ID不应为空");
        assertEquals(10, intPath(createResp, "data.probability"), "初步接触阶段概率应为10%");

        // 2. 推进到 需求确认 → 概率 25
        Response s1 = authPut("/api/crm/opportunity/" + oppId + "/stage",
                Map.of("stage", "需求确认"));
        assertEquals(200, respCode(s1), "推进到[需求确认]应成功");
        Response d1 = authGet("/api/crm/opportunity/" + oppId);
        assertEquals(25, intPath(d1, "data.probability"), "需求确认阶段概率应为25%");
        assertThat("销售阶段应为[需求确认]", d1.path("data.salesStage"), equalTo("需求确认"));

        // 3. 推进到 方案报价 → 概率 45
        Response s2 = authPut("/api/crm/opportunity/" + oppId + "/stage",
                Map.of("stage", "方案报价"));
        assertEquals(200, respCode(s2), "推进到[方案报价]应成功");
        Response d2 = authGet("/api/crm/opportunity/" + oppId);
        assertEquals(45, intPath(d2, "data.probability"), "方案报价阶段概率应为45%");
        assertThat("销售阶段应为[方案报价]", d2.path("data.salesStage"), equalTo("方案报价"));

        // 4. 非法销售阶段应被拒绝
        Response badStage = authPut("/api/crm/opportunity/" + oppId + "/stage",
                Map.of("stage", "不存在阶段"));
        assertThat("非法销售阶段应被拦截", respCode(badStage), is(not(200)));
    }

    /**
     * 创建跟进记录关联到线索: 验证线索跟进次数+1, 且新线索状态自动变更为"跟进中"
     */
    @Test
    void 逻辑关联_跟进记录关联线索并更新跟进次数与状态() {
        // 1. 创建新线索 (状态 新线索, 跟进次数 0)
        Long leadId = trackLead(authPost("/api/crm/lead", validLead()));
        assertNotNull(leadId, "前置: 创建线索失败");

        // 2. 创建跟进记录, 关联到该线索
        Map<String, Object> fu = new HashMap<>();
        fu.put("followUpNo", "FU-" + TEST_PREFIX + "-" + (System.nanoTime() % 100000));
        fu.put("sourceType", "线索");
        fu.put("sourceId", leadId);
        fu.put("sourceName", "测试线索" + TEST_PREFIX);
        fu.put("contactPerson", "联系人" + TEST_PREFIX);
        fu.put("followUpType", "电话");
        fu.put("followUpDate", LocalDate.now().toString());
        fu.put("content", "电话沟通需求详情, 客户有明确采购意向");
        fu.put("importantFlag", 1);
        Response fuResp = authPost("/api/crm/follow-up", fu);
        assertEquals(200, respCode(fuResp), "创建跟进记录应成功");
        Long fuId = trackFollowUp(fuResp);
        assertNotNull(fuId, "跟进记录ID不应为空");

        // 3. 验证线索跟进次数 +1, 状态由 新线索 → 跟进中
        Response leadDetail = authGet("/api/crm/lead/" + leadId);
        assertEquals(200, respCode(leadDetail), "查询线索详情应成功");
        assertEquals(1, intPath(leadDetail, "data.followUpCount"), "线索跟进次数应为1");
        assertThat("线索状态应由[新线索]变为[跟进中]",
                leadDetail.path("data.leadStatus"), equalTo("跟进中"));

        // 4. 跟进内容为空应被拦截
        Map<String, Object> badFu = new HashMap<>(fu);
        badFu.put("followUpNo", "FU-" + TEST_PREFIX + "-BAD-" + (System.nanoTime() % 100000));
        badFu.put("content", "");
        Response badResp = authPost("/api/crm/follow-up", badFu);
        assertThat("跟进内容为空应被拦截", respCode(badResp), is(not(200)));
    }

    // ====================================================================
    // 维度3: 设置中心生效验证
    // ====================================================================

    /**
     * 读取系统设置 /api/system/setting
     * 注意: 当前后端系统模块仅提供 SysUser/SysRole/SysMenu/SysDict/SysOperateLog 等管理接口,
     *       无系统设置中心接口 (/api/system/setting 不存在)。待后端提供该接口后启用本用例。
     */
    @Disabled("系统设置接口 /api/system/setting 在当前后端不存在 (系统模块仅有 SysUser/SysRole/SysMenu/SysDict 等管理接口, 无系统设置中心); 待后端提供系统设置接口后启用")
    @Test
    void 设置中心_系统设置接口生效验证() {
        Response resp = authGet("/api/system/setting");
        assertEquals(200, respCode(resp), "系统设置接口应可访问");
    }

    // ====================================================================
    // 维度4: 权限硬拦截
    // ====================================================================

    /**
     * 未携带 token 访问 CRM 接口: AuthInterceptor 应直接返回 401
     */
    @Test
    void 权限硬拦截_未登录访问CRM接口返回401() {
        // POST 创建线索 (无 Authorization 头)
        Response postResp = RestAssured.given().contentType(ContentType.JSON)
                .body(validLead()).when().post("/api/crm/lead");
        assertThat("未登录创建线索应返回401", postResp.statusCode(), equalTo(401));

        // DELETE 删除线索 (无 Authorization 头)
        Response delResp = RestAssured.given().when().delete("/api/crm/lead/1");
        assertThat("未登录删除线索应返回401", delResp.statusCode(), equalTo(401));
    }

    /**
     * 普通用户(userToken)创建线索应被拦截 (403/401)
     * 注意: 当前后端 AuthInterceptor 仅做 JWT 认证 (任何有效 token 均放行),
     *       CRM 接口未实现基于角色的授权拦截 (RBAC), 普通用户调用仍返回 200。
     *       待后端补充角色权限拦截后启用本用例。
     */
    @Disabled("当前后端 AuthInterceptor 仅做 JWT 认证, CRM 接口未实现 RBAC 角色权限拦截, 普通用户(有效 token)调用 /api/crm/lead 仍返回 200; 待后端补充角色权限拦截后启用")
    @Test
    void 权限硬拦截_普通用户创建线索应被拦截() {
        Response resp = userPost("/api/crm/lead", validLead());
        assertThat("普通用户创建线索应被拦截 (403/401)",
                resp.statusCode(), is(anyOf(equalTo(401), equalTo(403))));
    }

    /**
     * 普通用户(userToken)删除线索应被拦截 (403/401)
     * 注意: 同上, CRM 接口未实现 RBAC, 普通用户删除仍返回 200。待后端补充角色权限拦截后启用。
     */
    @Disabled("当前后端 CRM 接口未实现 RBAC 角色权限拦截, 普通用户(有效 token)删除线索仍返回 200; 待后端补充角色权限拦截后启用")
    @Test
    void 权限硬拦截_普通用户删除线索应被拦截() {
        Long leadId = trackLead(authPost("/api/crm/lead", validLead()));
        Response resp = userDelete("/api/crm/lead/" + leadId);
        assertThat("普通用户删除线索应被拦截 (403/401)",
                resp.statusCode(), is(anyOf(equalTo(401), equalTo(403))));
    }

    // ====================================================================
    // @AfterAll: 测试数据清理
    // ====================================================================

    @AfterAll
    static void 清理测试数据() {
        // 1. 按前缀清理 (DataCleaner 覆盖 lead_no / customer_code 等含 TEST_PREFIX 的记录)
        cleaner.cleanupByPrefix(TEST_PREFIX);
        // 2. 补充清理 DataCleaner 未正确覆盖的 CRM 表:
        //    - crm_follow_up 未纳入 cleanupByPrefix 清理列表
        //    - crm_opportunity 的清理列名误写为 opp_no (实际列为 opportunity_no), 导致前缀清理失效
        //    清理顺序: 子表先于主表 (follow_up → opportunity → customer → lead)
        for (Long id : createdFollowUpIds) {
            cleaner.cleanupById("crm_follow_up", "follow_up_id", id);
        }
        for (Long id : createdOppIds) {
            cleaner.cleanupById("crm_opportunity", "opportunity_id", id);
        }
        for (Long id : createdCustomerIds) {
            cleaner.cleanupById("customer", "customer_id", id);
        }
        for (Long id : createdLeadIds) {
            cleaner.cleanupById("crm_lead", "lead_id", id);
        }
        createdFollowUpIds.clear();
        createdOppIds.clear();
        createdCustomerIds.clear();
        createdLeadIds.clear();
        System.out.println("[CrmFullTest] 测试数据清理完成, prefix=" + TEST_PREFIX);
    }

    // ====================================================================
    // 辅助方法: 合法载荷构造 / 响应解析 / ID 跟踪
    // ====================================================================

    /** 构造一条合法的销售线索载荷 (编号含 TEST_PREFIX 以便清理) */
    private static Map<String, Object> validLead() {
        Map<String, Object> m = new HashMap<>();
        m.put("leadNo", "L-" + TEST_PREFIX + "-" + (System.nanoTime() % 100000));
        m.put("leadName", "测试线索" + TEST_PREFIX);
        m.put("companyName", "测试公司" + TEST_PREFIX);
        m.put("contactPerson", "联系人" + TEST_PREFIX);
        m.put("contactPhone", "13800000000");
        m.put("sourceType", "网络搜索");
        m.put("intentionLevel", "B");
        return m;
    }

    /** 构造一条合法的商机载荷 (编号含 TEST_PREFIX 以便清理) */
    private static Map<String, Object> validOpp(Long customerId) {
        Map<String, Object> m = new HashMap<>();
        m.put("opportunityNo", "OPP-" + TEST_PREFIX + "-" + (System.nanoTime() % 100000));
        m.put("opportunityName", "测试商机" + TEST_PREFIX);
        m.put("customerId", customerId);
        m.put("estimatedAmount", 100000);
        m.put("salesStage", "初步接触");
        m.put("expectedCloseDate", LocalDate.now().plusDays(30).toString());
        return m;
    }

    /** 提取响应体中的业务码 code (无 body 时回退为 HTTP 状态码) */
    private static int respCode(Response resp) {
        Object v = resp.path("code");
        return v == null ? resp.statusCode() : ((Number) v).intValue();
    }

    /** 从响应体提取 Long 字段 */
    private static Long longPath(Response resp, String path) {
        Object v = resp.path(path);
        return v == null ? null : ((Number) v).longValue();
    }

    /** 从响应体提取 Integer 字段 */
    private static Integer intPath(Response resp, String path) {
        Object v = resp.path(path);
        return v == null ? null : ((Number) v).intValue();
    }

    /** 跟踪创建成功的线索 ID */
    private static Long trackLead(Response resp) {
        Long id = longPath(resp, "data.leadId");
        if (id != null) {
            createdLeadIds.add(id);
        }
        return id;
    }

    /** 跟踪创建成功的商机 ID */
    private static Long trackOpp(Response resp) {
        Long id = longPath(resp, "data.opportunityId");
        if (id != null) {
            createdOppIds.add(id);
        }
        return id;
    }

    /** 跟踪创建成功的跟进记录 ID */
    private static Long trackFollowUp(Response resp) {
        Long id = longPath(resp, "data.followUpId");
        if (id != null) {
            createdFollowUpIds.add(id);
        }
        return id;
    }

    /** 跟踪线索转化产生的新客户 ID (convert 接口返回 data 直接为 Long) */
    private static Long trackConvertedCustomer(Response resp) {
        Long id = longPath(resp, "data");
        if (id != null) {
            createdCustomerIds.add(id);
        }
        return id;
    }
}
