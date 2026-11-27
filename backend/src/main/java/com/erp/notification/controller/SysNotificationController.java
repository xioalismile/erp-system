package com.erp.notification.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.service.SysNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统通知 Controller
 * - 顶栏铃铛弹窗 / 通知列表页通用接口
 * - 所有接口基于当前登录用户 (从 HttpServletRequest 取 userId)
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class SysNotificationController {

    private final SysNotificationService notificationService;

    private Long currentUserId(HttpServletRequest req) {
        Object id = req.getAttribute("currentUserId");
        if (id instanceof Long) return (Long) id;
        if (id instanceof Number) return ((Number) id).longValue();
        if (id instanceof String) {
            try { return Long.parseLong((String) id); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /** 分页查询当前用户通知列表 */
    @GetMapping("/page")
    public Result<PageResult<SysNotification>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Short isRead,
            HttpServletRequest req,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        Long userId = currentUserId(req);
        return Result.success(notificationService.pageQuery(userId, type, isRead, current, size, sortBy, sortOrder));
    }

    /** 未读数统计 (返回 {total, byType: {approval: n, overdue: n, ...}}) */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(HttpServletRequest req) {
        Long userId = currentUserId(req);
        return Result.success(notificationService.unreadCount(userId));
    }

    /** 标记单条已读 (点击通知时调用) */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest req) {
        Long userId = currentUserId(req);
        notificationService.markRead(id, userId);
        return Result.success();
    }

    /** 标记全部已读 */
    @PostMapping("/read-all")
    public Result<Integer> markAllRead(HttpServletRequest req) {
        Long userId = currentUserId(req);
        return Result.success(notificationService.markAllRead(userId));
    }

    /** 批量标记已读 */
    @PostMapping("/batch-read")
    public Result<Integer> batchMarkRead(@RequestBody Map<String, List<Long>> body, HttpServletRequest req) {
        Long userId = currentUserId(req);
        List<Long> ids = body.get("ids");
        return Result.success(notificationService.batchMarkRead(ids, userId));
    }

    /** 删除单条 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        Long userId = currentUserId(req);
        notificationService.delete(id, userId);
        return Result.success();
    }

    /** 清空已读通知 */
    @PostMapping("/clear-read")
    public Result<Integer> clearRead(HttpServletRequest req) {
        Long userId = currentUserId(req);
        return Result.success(notificationService.clearRead(userId));
    }
}
