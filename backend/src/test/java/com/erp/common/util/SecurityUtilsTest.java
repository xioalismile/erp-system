package com.erp.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：SecurityUtils 是全系统权限判断 + 数据范围过滤的基础工具。
 * 覆盖风险：
 *   1. getCurrentUserId / getCurrentUsername / getCurrentEmployeeId / getCurrentDeptId
 *      → 正确从 request.getAttribute 读取，支持 Long 直接取 / String 转 Long / 非法字符串不抛错
 *   2. getCurrentRoles / hasAnyRole → List<String> 直接取，非 List 类型安全降级为空列表
 *   3. isAdmin → 包含 ADMIN 或 MANAGER 返回 true
 *   4. isSalesOnly / isPurchaseOnly / isWarehouseOnly / isProductionOnly / isFinanceOnly
 *      → 仅普通角色（非 ADMIN / MANAGER）返回 true
 *   5. 各主管角色判断：isSalesManager / isPurchaseManager / isWarehouseManager /
 *      isProductionManager / isFinanceManager
 *   6. shouldFilterBySelf / shouldFilterByDept / isRegularEmployee 组合判断
 *   7. getCreatedByFilter / getEmployeeIdFilter → 数据权限过滤字段取值
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityUtils 权限与数据范围过滤工具测试")
class SecurityUtilsTest {

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        reset(request);
    }

    // ==================== 用户ID / 用户名 / 员工ID / 部门ID ====================

    @Test
    @DisplayName("getCurrentUserId：Long 属性直接返回")
    void getCurrentUserId_longAttribute_shouldReturnDirectly() {
        when(request.getAttribute("currentUserId")).thenReturn(1001L);
        assertEquals(1001L, SecurityUtils.getCurrentUserId(request));
    }

    @Test
    @DisplayName("getCurrentUserId：String 属性可转 Long")
    void getCurrentUserId_stringAttribute_shouldParseLong() {
        when(request.getAttribute("currentUserId")).thenReturn("2002");
        assertEquals(2002L, SecurityUtils.getCurrentUserId(request));
    }

    @Test
    @DisplayName("getCurrentUserId：null 属性 → null，不抛NPE")
    void getCurrentUserId_nullAttribute_shouldReturnNull() {
        when(request.getAttribute("currentUserId")).thenReturn(null);
        assertNull(SecurityUtils.getCurrentUserId(request));
    }

    @Test
    @DisplayName("getCurrentUsername：直接 toString 返回")
    void getCurrentUsername_anyNonNull_shouldReturnString() {
        when(request.getAttribute("currentUsername")).thenReturn("zhangsan");
        assertEquals("zhangsan", SecurityUtils.getCurrentUsername(request));

        when(request.getAttribute("currentUsername")).thenReturn(12345);
        assertEquals("12345", SecurityUtils.getCurrentUsername(request));
    }

    @Test
    @DisplayName("getCurrentUsername：null → null")
    void getCurrentUsername_null_shouldReturnNull() {
        when(request.getAttribute("currentUsername")).thenReturn(null);
        assertNull(SecurityUtils.getCurrentUsername(request));
    }

    @Test
    @DisplayName("getCurrentEmployeeId：支持 Long / String(可解析)；非法字符串 → null 不抛错")
    void getCurrentEmployeeId_variousTypes_shouldHandleGracefully() {
        // Long
        when(request.getAttribute("currentEmployeeId")).thenReturn(50L);
        assertEquals(50L, SecurityUtils.getCurrentEmployeeId(request));

        // String 可解析
        when(request.getAttribute("currentEmployeeId")).thenReturn("60");
        assertEquals(60L, SecurityUtils.getCurrentEmployeeId(request));

        // null
        when(request.getAttribute("currentEmployeeId")).thenReturn(null);
        assertNull(SecurityUtils.getCurrentEmployeeId(request));

        // String 不可解析 → null 不抛错
        when(request.getAttribute("currentEmployeeId")).thenReturn("not_a_number");
        assertNull(SecurityUtils.getCurrentEmployeeId(request));
    }

    @Test
    @DisplayName("getCurrentDeptId：类型转换与非法值容错")
    void getCurrentDeptId_variousTypes_shouldHandleGracefully() {
        when(request.getAttribute("currentDeptId")).thenReturn(10L);
        assertEquals(10L, SecurityUtils.getCurrentDeptId(request));

        when(request.getAttribute("currentDeptId")).thenReturn("20");
        assertEquals(20L, SecurityUtils.getCurrentDeptId(request));

        when(request.getAttribute("currentDeptId")).thenReturn(null);
        assertNull(SecurityUtils.getCurrentDeptId(request));

        // 非法值不抛异常
        when(request.getAttribute("currentDeptId")).thenReturn("xxx");
        assertNull(SecurityUtils.getCurrentDeptId(request));
    }

    // ==================== 角色相关 ====================

    private void setRoles(String... roles) {
        when(request.getAttribute("currentRoles")).thenReturn(List.of(roles));
    }

    @Test
    @DisplayName("getCurrentRoles：List 属性直接返回")
    void getCurrentRoles_listAttribute_shouldReturnSame() {
        setRoles("ADMIN", "SALES");
        List<String> result = SecurityUtils.getCurrentRoles(request);
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of("ADMIN", "SALES")));
    }

    @Test
    @DisplayName("getCurrentRoles：null → 空列表；非 List → 空列表（不抛ClassCastException）")
    void getCurrentRoles_nullOrNonList_shouldReturnEmptyList() {
        when(request.getAttribute("currentRoles")).thenReturn(null);
        assertTrue(SecurityUtils.getCurrentRoles(request).isEmpty());

        // 非 List 类型 → 降级为空，不抛错
        when(request.getAttribute("currentRoles")).thenReturn("ADMIN,SALES");
        assertTrue(SecurityUtils.getCurrentRoles(request).isEmpty());

        when(request.getAttribute("currentRoles")).thenReturn(123);
        assertTrue(SecurityUtils.getCurrentRoles(request).isEmpty());
    }

    @Test
    @DisplayName("hasAnyRole：命中任一返回 true；全不命中返回 false；空/入参 → false")
    void hasAnyRole_variousCases_shouldBeCorrect() {
        setRoles("SALES", "VIEWER");

        assertTrue(SecurityUtils.hasAnyRole(request, "SALES"));
        assertTrue(SecurityUtils.hasAnyRole(request, "VIEWER", "OTHER"));
        assertTrue(SecurityUtils.hasAnyRole(request, "ADMIN", "SALES"));

        assertFalse(SecurityUtils.hasAnyRole(request, "ADMIN"));
        assertFalse(SecurityUtils.hasAnyRole(request, "OTHER1", "OTHER2"));

        // null/空角色
        assertFalse(SecurityUtils.hasAnyRole(request));
        assertFalse(SecurityUtils.hasAnyRole(request, new String[0]));
    }

    // ==================== 单角色判断 ====================

    @Test
    @DisplayName("isAdmin：ADMIN 或 MANAGER → true")
    void isAdmin_adminOrManager_shouldReturnTrue() {
        setRoles("ADMIN");
        assertTrue(SecurityUtils.isAdmin(request));

        setRoles("MANAGER");
        assertTrue(SecurityUtils.isAdmin(request));

        setRoles("ADMIN", "FINANCE");
        assertTrue(SecurityUtils.isAdmin(request));

        setRoles("SALES");
        assertFalse(SecurityUtils.isAdmin(request));

        setRoles("SALES_MANAGER"); // 销售主管不算系统管理员
        assertFalse(SecurityUtils.isAdmin(request));
    }

    @Test
    @DisplayName("isSalesOnly：仅 SALES（非 ADMIN / MANAGER）→ true")
    void isSalesOnly_onlySalesRole_shouldReturnTrue() {
        setRoles("SALES");
        assertTrue(SecurityUtils.isSalesOnly(request));

        // SALES + 管理员 → 不算"仅销售"
        setRoles("SALES", "ADMIN");
        assertFalse(SecurityUtils.isSalesOnly(request));

        setRoles("SALES", "MANAGER");
        assertFalse(SecurityUtils.isSalesOnly(request));

        // 不含 SALES
        setRoles("PURCHASE");
        assertFalse(SecurityUtils.isSalesOnly(request));
    }

    @Test
    @DisplayName("isPurchaseOnly：仅 PURCHASE（非管理岗）→ true")
    void isPurchaseOnly_onlyPurchaseRole_shouldReturnTrue() {
        setRoles("PURCHASE");
        assertTrue(SecurityUtils.isPurchaseOnly(request));

        setRoles("PURCHASE", "ADMIN");
        assertFalse(SecurityUtils.isPurchaseOnly(request));
    }

    @Test
    @DisplayName("各普通员工角色：WAREHOUSE / PRODUCTION / FINANCE → Only 方法判断正确")
    void isXxxOnly_regularRoles_shouldBeCorrect() {
        // 仓库员
        setRoles("WAREHOUSE");
        assertTrue(SecurityUtils.isWarehouseOnly(request));
        setRoles("WAREHOUSE", "MANAGER");
        assertFalse(SecurityUtils.isWarehouseOnly(request));

        // 生产员
        setRoles("PRODUCTION");
        assertTrue(SecurityUtils.isProductionOnly(request));
        setRoles("PRODUCTION", "ADMIN");
        assertFalse(SecurityUtils.isProductionOnly(request));

        // 财务员
        setRoles("FINANCE");
        assertTrue(SecurityUtils.isFinanceOnly(request));
        setRoles("FINANCE", "MANAGER");
        assertFalse(SecurityUtils.isFinanceOnly(request));
    }

    @Test
    @DisplayName("各主管角色判断：SALES_MANAGER / PURCHASE_MANAGER 等（非ADMIN/MANAGER）")
    void isXxxManager_supervisorRoles_shouldBeCorrect() {
        // 销售主管
        setRoles("SALES_MANAGER");
        assertTrue(SecurityUtils.isSalesManager(request));
        setRoles("SALES_MANAGER", "MANAGER");
        assertFalse(SecurityUtils.isSalesManager(request)); // 兼系统管理不算仅销售主管

        // 采购主管
        setRoles("PURCHASE_MANAGER");
        assertTrue(SecurityUtils.isPurchaseManager(request));

        // 仓库主管
        setRoles("WAREHOUSE_MANAGER");
        assertTrue(SecurityUtils.isWarehouseManager(request));

        // 生产主管
        setRoles("PRODUCTION_MANAGER");
        assertTrue(SecurityUtils.isProductionManager(request));

        // 财务主管
        setRoles("FINANCE_MANAGER");
        assertTrue(SecurityUtils.isFinanceManager(request));
    }

    // ==================== 组合判断 ====================

    @Test
    @DisplayName("shouldFilterBySelf：普通销售/采购员 → true；管理岗或其他人 → false")
    void shouldFilterBySelf_onlyRegularSalesOrPurchase_shouldReturnTrue() {
        setRoles("SALES");
        assertTrue(SecurityUtils.shouldFilterBySelf(request));

        setRoles("PURCHASE");
        assertTrue(SecurityUtils.shouldFilterBySelf(request));

        setRoles("SALES_MANAGER");
        assertFalse(SecurityUtils.shouldFilterBySelf(request));

        setRoles("ADMIN");
        assertFalse(SecurityUtils.shouldFilterBySelf(request));

        setRoles("WAREHOUSE");
        assertFalse(SecurityUtils.shouldFilterBySelf(request));
    }

    @Test
    @DisplayName("shouldFilterByDept：ADMIN/MANAGER → false；主管/普通员工 → true")
    void shouldFilterByDept_nonAdmin_shouldNeedFilter() {
        // 管理员：看全部，不过滤
        setRoles("ADMIN");
        assertFalse(SecurityUtils.shouldFilterByDept(request));
        setRoles("MANAGER");
        assertFalse(SecurityUtils.shouldFilterByDept(request));

        // 主管：按部门过滤
        setRoles("SALES_MANAGER");
        assertTrue(SecurityUtils.shouldFilterByDept(request));
        setRoles("PURCHASE_MANAGER");
        assertTrue(SecurityUtils.shouldFilterByDept(request));

        // 普通员工：按自己过滤（也返回 true）
        setRoles("SALES");
        assertTrue(SecurityUtils.shouldFilterByDept(request));
        setRoles("PURCHASE");
        assertTrue(SecurityUtils.shouldFilterByDept(request));
    }

    @Test
    @DisplayName("isRegularEmployee：所有非管理岗普通员工角色 → true")
    void isRegularEmployee_allRegularRoles_shouldReturnTrue() {
        String[] regularRoles = {"SALES", "PURCHASE", "WAREHOUSE", "PRODUCTION", "FINANCE"};
        for (String role : regularRoles) {
            setRoles(role);
            assertTrue(SecurityUtils.isRegularEmployee(request),
                    "角色 " + role + " 应被视为普通员工");
        }

        // 加上管理员就不算
        setRoles("SALES", "ADMIN");
        assertFalse(SecurityUtils.isRegularEmployee(request));

        // 主管不算
        setRoles("SALES_MANAGER");
        assertFalse(SecurityUtils.isRegularEmployee(request));
    }

    // ==================== 数据范围过滤字段 ====================

    @Test
    @DisplayName("getCreatedByFilter：管理员 / 主管级 → null（不过滤）；普通员工 → 当前用户名")
    void getCreatedByFilter_permissionHierarchy_shouldBeCorrect() {
        // 准备用户名
        when(request.getAttribute("currentUsername")).thenReturn("testuser");

        // ADMIN → null
        setRoles("ADMIN");
        assertNull(SecurityUtils.getCreatedByFilter(request));

        // MANAGER → null
        setRoles("MANAGER");
        assertNull(SecurityUtils.getCreatedByFilter(request));

        // 销售主管 → null（主管级看本部门全部）
        setRoles("SALES_MANAGER");
        assertNull(SecurityUtils.getCreatedByFilter(request));

        // 采购主管 → null
        setRoles("PURCHASE_MANAGER");
        assertNull(SecurityUtils.getCreatedByFilter(request));

        // 其他主管也 → null
        setRoles("WAREHOUSE_MANAGER");
        assertNull(SecurityUtils.getCreatedByFilter(request));

        // 普通员工 → 返回用户名
        setRoles("SALES");
        assertEquals("testuser", SecurityUtils.getCreatedByFilter(request));

        setRoles("FINANCE");
        assertEquals("testuser", SecurityUtils.getCreatedByFilter(request));
    }

    @Test
    @DisplayName("getEmployeeIdFilter：管理级 → null；销售/采购普通 → employeeId；其他角色 → null")
    void getEmployeeIdFilter_roleAndEmployeeId_shouldBeCorrect() {
        when(request.getAttribute("currentEmployeeId")).thenReturn(888L);

        // ADMIN / 主管 → null
        setRoles("ADMIN");
        assertNull(SecurityUtils.getEmployeeIdFilter(request));

        setRoles("SALES_MANAGER");
        assertNull(SecurityUtils.getEmployeeIdFilter(request));

        // 普通销售 → employeeId
        setRoles("SALES");
        assertEquals(888L, SecurityUtils.getEmployeeIdFilter(request));

        // 普通采购 → employeeId
        setRoles("PURCHASE");
        assertEquals(888L, SecurityUtils.getEmployeeIdFilter(request));

        // 仓库员 → null（不由员工ID过滤）
        setRoles("WAREHOUSE");
        assertNull(SecurityUtils.getEmployeeIdFilter(request));
    }
}
