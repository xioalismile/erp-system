package com.erp.ai.controller;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.task.AiTaskService;
import com.erp.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 小楷多步骤任务（任务卡查询 / 重跑失败步骤 / 撤销本次新建）。
 * 只允许任务发起人访问；任务链本身的执行入口是对话里的写工具（确认卡一次性确认）。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/task")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService taskService;

    /** 任务详情（任务卡刷新/断点回看） */
    @GetMapping("/{taskId}")
    public Result<Map<String, Object>> detail(@PathVariable Long taskId, HttpServletRequest request) {
        try {
            return Result.success(taskService.getTask(taskId, AiDataScope.from(request)));
        } catch (IllegalArgumentException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            log.warn("[小楷任务] 查询失败 taskId={}: {}", taskId, e.getMessage());
            return Result.error("任务查询失败：" + e.getMessage());
        }
    }

    /** 会话下的最近任务（刷新后恢复任务卡） */
    @GetMapping("/by-session/{sessionId}")
    public Result<List<Map<String, Object>>> bySession(@PathVariable String sessionId, HttpServletRequest request) {
        try {
            return Result.success(taskService.listBySession(sessionId, AiDataScope.from(request)));
        } catch (Exception e) {
            return Result.error("任务查询失败：" + e.getMessage());
        }
    }

    /** 只重跑失败/未执行的步骤（已成功的步骤不重复执行） */
    @PostMapping("/{taskId}/retry-failed")
    public Result<Map<String, Object>> retryFailed(@PathVariable Long taskId, HttpServletRequest request) {
        try {
            Map<String, Object> out = taskService.retryFailed(taskId, AiDataScope.from(request));
            return Boolean.TRUE.equals(out.get("success")) ? Result.success(out) : Result.error(String.valueOf(out.get("message")));
        } catch (IllegalArgumentException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            log.warn("[小楷任务] 重跑失败 taskId={}: {}", taskId, e.getMessage());
            return Result.error("重跑失败：" + e.getMessage());
        }
    }

    /** 撤销本次新建（仅本任务创建、且未被引用的记录；逐条反向撤销） */
    @PostMapping("/{taskId}/undo")
    public Result<Map<String, Object>> undo(@PathVariable Long taskId, HttpServletRequest request) {
        try {
            Map<String, Object> out = taskService.undo(taskId, AiDataScope.from(request));
            // 撤销结果（含"部分撤销/遇引用停止"）都要回传任务卡最新状态，让前端同步步骤状态与按钮；
            // 是否成功看 out.success，不用 Result.error 把数据吞掉。
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            log.warn("[小楷任务] 撤销失败 taskId={}: {}", taskId, e.getMessage());
            return Result.error("撤销失败：" + e.getMessage());
        }
    }
}
