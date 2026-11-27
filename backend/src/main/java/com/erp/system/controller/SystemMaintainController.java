package com.erp.system.controller;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.system.service.SystemMaintainService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据维护 - 一键清空资料（危险操作，仅超管可用）
 *
 * 端点：
 *   POST /api/system/maintain/clear-business  清空全部业务单据（保留基础档案）
 *   POST /api/system/maintain/clear-all       清空全部资料（业务单据 + 基础档案）
 *
 * 安全规范：仅 SUPER_ADMIN / ADMIN 角色可调用，其余一律 403；
 *          永不触碰系统用户（admin 自动恢复）/角色/权限/菜单/字典/系统参数/自定义报表。
 */
@Slf4j
@RestController
@RequestMapping("/api/system/maintain")
@RequiredArgsConstructor
public class SystemMaintainController {

    private final SystemMaintainService systemMaintainService;

    @PostMapping("/clear-business")
    public Result<Map<String, Object>> clearBusiness(HttpServletRequest request) {
        if (!checkPermission(request)) {
            return Result.error(403, "无权限执行数据清空操作，仅系统管理员可用");
        }
        Map<String, Object> result = systemMaintainService.clearBusiness();
        systemMaintainService.writeOperateLog("clearBusiness", result,
                SecurityUtils.getCurrentUsername(request));
        return Result.success(result);
    }

    @PostMapping("/clear-all")
    public Result<Map<String, Object>> clearAll(HttpServletRequest request) {
        if (!checkPermission(request)) {
            return Result.error(403, "无权限执行数据清空操作，仅系统管理员可用");
        }
        Map<String, Object> result = systemMaintainService.clearAll();
        systemMaintainService.writeOperateLog("clearAll", result,
                SecurityUtils.getCurrentUsername(request));
        return Result.success(result);
    }

    private boolean checkPermission(HttpServletRequest request) {
        return SecurityUtils.hasAnyRole(request, "SUPER_ADMIN", "ADMIN")
                || SecurityUtils.hasVersionAdminRole(request);
    }
}
