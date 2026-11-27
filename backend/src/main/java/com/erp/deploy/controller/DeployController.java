package com.erp.deploy.controller;

import com.erp.common.Result;
import com.erp.deploy.entity.DeployLog;
import com.erp.deploy.entity.DeployServer;
import com.erp.deploy.service.DeployService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 客户升级部署/回滚控制器 —— 仅 SUPER_ADMIN（平台管理模块，客户部署包不含此代码路径的前端入口）
 */
@RestController
@RequestMapping("/api/admin/deploy")
@RequiredArgsConstructor
public class DeployController {

    private final DeployService deployService;
    private final com.erp.deploy.service.DeployVersionService versionService;

    // ---------- 服务器配置 ----------
    @GetMapping("/servers")
    public Result<List<DeployServer>> servers(HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        return Result.success(deployService.listServers());
    }

    @PostMapping("/server")
    public Result<DeployServer> saveServer(@RequestBody DeployServer server, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        try {
            return Result.success(deployService.saveServer(server));
        } catch (Exception e) {
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/server/{id}")
    public Result<Void> deleteServer(@PathVariable Long id, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        deployService.deleteServer(id);
        return Result.success(null);
    }

    @PostMapping("/server/{id}/test")
    public Result<String> testConnection(@PathVariable Long id, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        try {
            return Result.success(deployService.testConnection(id));
        } catch (Exception e) {
            return Result.error("连接失败: " + e.getMessage());
        }
    }

    // ---------- 部署 / 回滚 ----------
    @PostMapping("/deploy")
    public Result<Map<String, Object>> deploy(@RequestParam Long serverId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String note,
                                              HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        try {
            Long logId = deployService.startDeploy(serverId, file, note, username(req));
            return Result.success(Map.of("logId", logId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/rollback")
    public Result<Map<String, Object>> rollback(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        try {
            Long serverId = Long.valueOf(body.get("serverId"));
            String backupName = body.get("backupName");
            Long logId = versionService.startRollbackTracked(serverId, backupName, username(req));
            return Result.success(Map.of("logId", logId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/backups")
    public Result<List<String>> backups(@RequestParam Long serverId, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        try {
            return Result.success(deployService.listBackups(serverId));
        } catch (Exception e) {
            return Result.error("获取备份列表失败: " + e.getMessage());
        }
    }

    // ---------- 日志 ----------
    @GetMapping("/log/{id}")
    public Result<DeployLog> log(@PathVariable Long id, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        return Result.success(deployService.getLog(id));
    }

    @GetMapping("/logs")
    public Result<List<DeployLog>> logs(@RequestParam(defaultValue = "20") int limit, HttpServletRequest req) {
        if (!isSuperAdmin(req)) return Result.error("无权限");
        return Result.success(deployService.recentLogs(limit));
    }

    // ---------- 工具 ----------
    private boolean isSuperAdmin(HttpServletRequest req) {
        Object roles = req.getAttribute("currentRoles");
        return roles != null && roles.toString().contains("SUPER_ADMIN");
    }

    private String username(HttpServletRequest req) {
        Object u = req.getAttribute("currentUsername");
        return u == null ? "unknown" : u.toString();
    }
}
