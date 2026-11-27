package com.erp.deploy.controller;

import com.erp.common.Result;
import com.erp.deploy.entity.DeployLog;
import com.erp.deploy.entity.DeployVersion;
import com.erp.deploy.service.DeployService;
import com.erp.deploy.service.DeployVersionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 部署版本管理（本地控制台）：一键打包 + 版本库 + 选版本部署
 * 仅 SUPER_ADMIN；客户包（-P customer）不编译平台管理前端入口，客户环境无此路由的UI入口
 */
@RestController
@RequestMapping("/api/admin/deploy-version")
@RequiredArgsConstructor
public class DeployVersionController {

    private final DeployVersionService versionService;
    private final DeployService deployService;

    /** 一键打包（异步，返回 buildLogId 轮询日志） */
    @PostMapping("/build")
    public Result<Map<String, Object>> build(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        try {
            String buildMode = body.getOrDefault("buildMode", "full");
            String note = body.get("note");
            Long logId = versionService.startBuild(buildMode, note, username(req));
            return Result.success(Map.of("logId", logId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /** 版本列表 */
    @GetMapping("/versions")
    public Result<List<DeployVersion>> versions(HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        return Result.success(versionService.listVersions());
    }

    /** 删除版本（仅文件+记录） */
    @DeleteMapping("/version/{id}")
    public Result<Void> deleteVersion(@PathVariable Long id, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        versionService.deleteVersion(id);
        return Result.success();
    }

    /** 从版本部署到服务器 */
    @PostMapping("/deploy")
    public Result<Map<String, Object>> deploy(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        try {
            Long versionId = Long.valueOf(body.get("versionId").toString());
            Long serverId = Long.valueOf(body.get("serverId").toString());
            Long logId = versionService.deployVersion(versionId, serverId, username(req));
            return Result.success(Map.of("logId", logId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /** 打包/部署/回滚统一实时日志查询（BUILD 日志由版本服务提供实时缓冲） */
    @GetMapping("/log/{id}")
    public Result<DeployLog> log(@PathVariable Long id, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        DeployLog dl = deployService.getLog(id);
        if (dl == null) return Result.error("日志不存在");
        // BUILD 运行中：叠加版本服务的实时缓冲
        if ("RUNNING".equals(dl.getStatus()) && "BUILD".equals(dl.getAction())) {
            String live = versionService.getLiveLogText(id);
            if (live != null) dl.setLogText(live);
        }
        return Result.success(dl);
    }

    private boolean isSuperAdmin(HttpServletRequest req) {
        Object roles = req.getAttribute("currentRoles");
        return roles != null && roles.toString().contains("SUPER_ADMIN");
    }

    private String username(HttpServletRequest req) {
        Object u = req.getAttribute("currentUsername");
        return u == null ? "unknown" : u.toString();
    }
}
