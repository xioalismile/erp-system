package com.erp.ai.controller;

import com.erp.ai.service.AiChatHistoryStore;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 小楷 · 历史会话（2026-10-22）。
 *
 * 口径：
 *  ① 只返回**当前登录人**自己的会话，别人的会话既看不到也删不掉；
 *  ② 会话记录永久保留，删除是用户主动动作（单人单会话，不可恢复）；
 *  ③ 读消息用于把历史会话接回面板继续聊，上下文由会话号自然延续。
 */
@RestController
@RequestMapping("/api/ai/chat/sessions")
@RequiredArgsConstructor
public class AiChatSessionController {

    private final AiChatHistoryStore historyStore;

    /** 我的历史会话列表（按最近对话时间倒序） */
    @GetMapping("")
    public Result<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId(request);
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(historyStore.listSessions(userId, 200));
    }

    /** 某个会话的全部消息（仅本人可读） */
    @GetMapping("/{sessionId}/messages")
    public Result<List<Map<String, Object>>> messages(HttpServletRequest request, @PathVariable String sessionId) {
        Long userId = SecurityUtils.getCurrentUserId(request);
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(historyStore.messages(userId, sessionId));
    }

    /** 删除自己的某个会话（别人的会话删除条数为 0） */
    @DeleteMapping("/{sessionId}")
    public Result<Map<String, Object>> delete(HttpServletRequest request, @PathVariable String sessionId) {
        Long userId = SecurityUtils.getCurrentUserId(request);
        if (userId == null) return Result.error(401, "未登录");
        int deleted = historyStore.deleteSession(userId, sessionId);
        if (deleted <= 0) return Result.error(404, "会话不存在或不属于当前登录人");
        return Result.success(Map.of("deleted", deleted));
    }
}
