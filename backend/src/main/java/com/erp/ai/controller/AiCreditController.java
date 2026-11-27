package com.erp.ai.controller;

import com.erp.ai.credit.AiCreditService;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小楷助手积分（员工端，2026-10-24）。
 * <p>员工只能查看自己的积分余额与流水，不能调整；管理端接口在 AI 用量管理与平台客户管理。</p>
 */
@RestController
@RequestMapping("/api/ai/credit")
@RequiredArgsConstructor
public class AiCreditController {

    private final AiCreditService aiCreditService;

    /** 当前登录人的积分钱包：个人优先，未分配则展示公司共享积分 */
    @GetMapping("/wallet")
    public Result<Map<String, Object>> wallet(HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId(request);
        String username = SecurityUtils.getCurrentUsername(request);
        Map<String, Object> data = aiCreditService.wallet(userId, username);
        data.put("ledger", aiCreditService.ledger(userId, 20));
        return Result.success(data);
    }

    /** 当前登录人的最近积分流水 */
    @GetMapping("/ledger")
    public Result<Map<String, Object>> ledger(@RequestParam(defaultValue = "20") int limit,
                                              HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ledger", aiCreditService.ledger(userId, limit));
        return Result.success(data);
    }
}
