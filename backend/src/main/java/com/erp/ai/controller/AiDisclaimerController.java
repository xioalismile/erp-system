package com.erp.ai.controller;

import com.erp.ai.service.AiDisclaimerService;
import com.erp.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 小楷助手「服务协议与免责声明」同意状态接口。
 *
 * <p>闸门口径：前端在小楷**全部入口**（浮球点击 / 语音唤醒 / 纯语音后台执行 / 通知跳转）
 * 先查本接口，未同意则先弹协议、不执行任何 AI 调用。
 * 接口异常时前端按"未同意"处理（宁可多弹，不可漏放）。
 *
 * <p>权限：仅要求登录态（任意角色都要读自己的同意状态），不额外挂权限码。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/disclaimer")
@RequiredArgsConstructor
public class AiDisclaimerController {

    private final AiDisclaimerService aiDisclaimerService;

    /**
     * 查询当前账号的同意状态。
     *
     * @param version 当前协议版本号（协议文本写死在前端常量，由前端上报）
     * @return {currentVersion, accepted, acceptedVersion, acceptedAt}
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestParam(required = false) String version,
                                              HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(aiDisclaimerService.status(userId, version));
    }

    /** 记录同意（勾选"我已阅读并同意"后调用） body: {version} */
    @PostMapping("/accept")
    public Result<Map<String, Object>> accept(@RequestBody(required = false) Map<String, Object> body,
                                              HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        Object v = body == null ? null : body.get("version");
        String version = v == null ? "" : String.valueOf(v).trim();
        if (version.isEmpty()) {
            return Result.error("协议版本号缺失，无法记录同意");
        }
        String acceptedAt = aiDisclaimerService.accept(userId, currentUsername(request), version);
        return Result.success(Map.of("accepted", true, "acceptedVersion", version, "acceptedAt", acceptedAt));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("currentUserId");
        return v instanceof Long ? (Long) v : null;
    }

    private String currentUsername(HttpServletRequest request) {
        Object v = request.getAttribute("currentUsername");
        return v == null ? "" : String.valueOf(v);
    }
}
