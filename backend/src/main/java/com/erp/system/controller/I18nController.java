package com.erp.system.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.config.BusinessException;
import com.erp.system.entity.SysI18n;
import com.erp.system.service.I18nService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多语言语言包接口（2026-09-07 L1 立项）
 * <p>
 * 安全规范：
 * - GET /package/{lang} 为公开接口（登录页也要翻译），已加入 AuthInterceptor / InterceptorConfig /
 *   LicenseInterceptor 三处白名单；只返回“翻译键→译文”，不含任何业务数据/用户信息。
 * - GET /page 与 POST /save 仅管理员可用（口径与 AuthInterceptor 一致：SUPER_ADMIN/ADMIN 角色
 *   或持有通配权限 '*'），非管理员返回业务错误，不泄露数据。
 */
@Slf4j
@RestController
@RequestMapping("/api/system/i18n")
@RequiredArgsConstructor
public class I18nController {

    private final I18nService i18nService;

    /** 语言包（匿名）：前端启动/切换语言时拉取 */
    @GetMapping("/package/{lang}")
    public Result<Map<String, String>> packageByLang(@PathVariable String lang) {
        return Result.success(i18nService.packageByLang(lang));
    }

    /** 分页查询（管理员）：语言管理页 */
    @GetMapping("/page")
    public Result<PageResult<SysI18n>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String lang,
            HttpServletRequest request) {
        requireAdmin(request, "查询语言包");
        return Result.success(i18nService.pageItems(current, size, keyword, module, lang));
    }

    /** 批量保存译文（管理员） */
    @PostMapping("/save")
    public Result<Integer> save(@RequestBody List<SysI18n> rows, HttpServletRequest request) {
        requireAdmin(request, "保存译文");
        return Result.success(i18nService.saveTranslations(rows));
    }

    /** 管理员口径与 AuthInterceptor 保持一致：角色 SUPER_ADMIN/ADMIN 或通配权限 '*' */
    @SuppressWarnings("unchecked")
    private void requireAdmin(HttpServletRequest request, String action) {
        Object rolesAttr = request.getAttribute("currentRoles");
        Object permsAttr = request.getAttribute("currentPermissions");
        boolean fullAdmin = false;
        if (rolesAttr instanceof List<?> roles) {
            fullAdmin = roles.contains("SUPER_ADMIN") || roles.contains("ADMIN");
        }
        if (!fullAdmin && permsAttr instanceof List<?> perms) {
            fullAdmin = perms.contains("*");
        }
        if (!fullAdmin) {
            log.warn("非管理员尝试{}: user={}", action, request.getAttribute("currentUsername"));
            throw new BusinessException("无权限：仅管理员可维护语言包");
        }
    }
}
