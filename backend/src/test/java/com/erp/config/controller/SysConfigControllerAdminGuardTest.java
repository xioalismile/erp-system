package com.erp.config.controller;

import com.erp.common.Result;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：系统配置写接口的管理员守卫。
 *
 * Bug 来源：SysConfigController 类注释声明"系统配置写操作仅 ADMIN 角色可执行（安全审计 V-004b）"，
 * 但该守卫只落在 POST /refresh 上，PUT /{key} 与 PUT /batch 两个写接口完全没有角色校验；
 * 而 AuthInterceptor 只对 /api/admin/** 做管理员判定，/api/sys-config/** 仅校验 token
 * → 任意已登录用户（含只读角色）都能改写全局配置（审批开关、库存负库存开关、AI 写开关等）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("系统配置 - 写接口管理员权限守卫回归测试")
class SysConfigControllerAdminGuardTest {

    @Mock private SysConfigService sysConfigService;

    private SysConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new SysConfigController(sysConfigService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 模拟 AuthInterceptor 注入的 currentRoles 请求属性 */
    private void loginAs(String... roleCodes) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (roleCodes != null) {
            request.setAttribute("currentRoles", Arrays.asList(roleCodes));
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /** 模拟 AuthInterceptor 注入的完整属性（角色 + 权限码 + 菜单路径） */
    private void loginWith(List<String> roleCodes, List<String> permissions, List<String> menuPaths) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (roleCodes != null) request.setAttribute("currentRoles", roleCodes);
        if (permissions != null) request.setAttribute("currentPermissions", permissions);
        if (menuPaths != null) request.setAttribute("currentMenuPaths", menuPaths);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private SysConfig cfg(String value) {
        SysConfig c = new SysConfig();
        c.setConfigValue(value);
        return c;
    }

    @Test
    @DisplayName("PUT /{key} - 只读角色：403 且不写库")
    void updateValue_nonAdmin_shouldBeRejected() {
        loginAs("VIEWER");

        Result<Void> res = controller.updateValue("approval.enabled", cfg("false"));

        assertEquals(403, res.getCode(), "非管理员改配置必须被拒绝，实际: " + res.getCode());
        verify(sysConfigService, never()).updateValue(anyString(), anyString());
    }

    @Test
    @DisplayName("PUT /{key} - 未取到角色属性：403 且不写库")
    void updateValue_withoutRoles_shouldBeRejected() {
        loginAs((String[]) null);

        Result<Void> res = controller.updateValue("system.name", cfg("被篡改"));

        assertEquals(403, res.getCode());
        verify(sysConfigService, never()).updateValue(anyString(), anyString());
    }

    @Test
    @DisplayName("PUT /{key} - 管理员：放行")
    void updateValue_admin_shouldPass() {
        loginAs("ADMIN");
        when(sysConfigService.updateValue(eq("system.name"), eq("楷煜ERP"))).thenReturn(true);

        Result<Void> res = controller.updateValue("system.name", cfg("楷煜ERP"));

        assertEquals(200, res.getCode(), "管理员应可写配置，实际: " + res.getCode());
        verify(sysConfigService, times(1)).updateValue("system.name", "楷煜ERP");
    }

    @Test
    @DisplayName("PUT /batch - 非管理员：403 且不写库")
    void batchUpdate_nonAdmin_shouldBeRejected() {
        loginAs("SALES_REP");

        Result<Void> res = controller.batchUpdate(List.of(cfg("true")));

        assertEquals(403, res.getCode());
        verify(sysConfigService, never()).batchUpdate(any());
    }

    @Test
    @DisplayName("PUT /batch - 平台超级管理员：放行（口径与 AuthInterceptor.fullAdmin 一致）")
    void batchUpdate_superAdmin_shouldPass() {
        loginAs("SUPER_ADMIN");
        when(sysConfigService.batchUpdate(any())).thenReturn(true);

        Result<Void> res = controller.batchUpdate(List.of(cfg("true")));

        assertEquals(200, res.getCode(), "SUPER_ADMIN 应可写配置，实际: " + res.getCode());
        verify(sysConfigService, times(1)).batchUpdate(any());
    }

    @Test
    @DisplayName("POST /refresh - 非管理员：仍保持 403（原守卫不被削弱）")
    void refreshCache_nonAdmin_shouldBeRejected() {
        loginAs("VIEWER");

        Result<Void> res = controller.refreshCache();

        assertEquals(403, res.getCode());
        verify(sysConfigService, never()).refreshCache();
    }

    // ===== 2026-10-08 回归：设置中心「写」收紧为"必须显式勾选写按钮"（用户口径） =====

    @Test
    @DisplayName("PUT /batch - 只有设置中心菜单码 system:settings:view、没勾写按钮：拒绝（默认关闭）")
    void batchUpdate_settingsMenuOnly_shouldBeRejected() {
        loginWith(List.of("SALES"),
                List.of("system:settings:view", "sale:orders:query"),
                List.of("/system/settings", "/system/settings?item=sys-param"));

        Result<Void> res = controller.batchUpdate(List.of(cfg("true")));

        assertEquals(403, res.getCode(), "未勾写按钮应拒绝，实际: " + res.getCode());
        verify(sysConfigService, never()).batchUpdate(any());
    }

    @Test
    @DisplayName("PUT /batch - 勾了「系统参数设置」的保存按钮 system:sys-param:save：放行")
    void batchUpdate_subPageSaveButton_shouldPass() {
        loginWith(List.of("SALES"),
                List.of("system:settings:view", "system:sys-param:save"),
                List.of("/system/settings"));
        when(sysConfigService.batchUpdate(any())).thenReturn(true);

        Result<Void> res = controller.batchUpdate(List.of(cfg("true")));

        assertEquals(200, res.getCode(), "勾了设置子页写按钮应可写，实际: " + res.getCode());
        verify(sysConfigService, times(1)).batchUpdate(any());
    }

    @Test
    @DisplayName("PUT /batch - 勾了「设置中心」的保存按钮 system:settings:save：放行")
    void batchUpdate_settingsSaveButton_shouldPass() {
        loginWith(List.of("SALES"),
                List.of("system:settings:view", "system:settings:save"),
                List.of("/system/settings"));
        when(sysConfigService.batchUpdate(any())).thenReturn(true);

        Result<Void> res = controller.batchUpdate(List.of(cfg("true")));

        assertEquals(200, res.getCode(), "勾了设置中心写按钮应可写，实际: " + res.getCode());
        verify(sysConfigService, times(1)).batchUpdate(any());
    }

    @Test
    @DisplayName("PUT /batch - 仅有历史遗留分组容器码 system:sub-business:save、无真实写按钮：拒绝")
    void batchUpdate_containerJunkCode_shouldBeRejected() {
        loginWith(List.of("SALES"),
                List.of("system:settings:view", "system:sub-business:save", "system:sub-custom:edit"),
                List.of("/system/settings"));

        Result<Void> res = controller.batchUpdate(List.of(cfg("true")));

        assertEquals(403, res.getCode(), "容器码不应算作授权，实际: " + res.getCode());
        verify(sysConfigService, never()).batchUpdate(any());
    }

    @Test
    @DisplayName("PUT /batch - 无设置权限且无设置中心菜单路径：拒绝")
    void batchUpdate_noSettingsAccess_shouldBeRejected() {
        loginWith(List.of("VIEWER"), List.of("sale:orders:query"), List.of("/sale/orders"));

        Result<Void> res = controller.batchUpdate(List.of(cfg("true")));

        assertEquals(403, res.getCode());
        verify(sysConfigService, never()).batchUpdate(any());
    }
}
