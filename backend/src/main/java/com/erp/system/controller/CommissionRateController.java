package com.erp.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.system.entity.CommissionRate;
import com.erp.system.mapper.CommissionRateMapper;
import com.erp.system.service.CommissionRateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提成率配置管理 - 历史版本留痕
 *
 * 端点：
 *   GET  /api/system/commission-rate/current          当前生效版本
 *   GET  /api/system/commission-rate/effective?date=  某天生效的版本
 *   GET  /api/system/commission-rate/range?startDate=&endDate=  区间内所有版本
 *   GET  /api/system/commission-rate/history?page=1&size=20  历史版本分页
 *   POST /api/system/commission-rate                  新增一版（留痕）
 */
@Slf4j
@RestController
@RequestMapping("/api/system/commission-rate")
@RequiredArgsConstructor
public class CommissionRateController {

    private final CommissionRateService commissionRateService;
    private final CommissionRateMapper commissionRateMapper;

    /** 当前生效版本 */
    @GetMapping("/current")
    public Result<CommissionRate> current() {
        return Result.success(commissionRateService.getCurrent());
    }

    /** 某天生效的版本（按订单日期匹配历史提成率） */
    @GetMapping("/effective")
    public Result<CommissionRate> effectiveAt(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(commissionRateService.getEffectiveAt(date));
    }

    /** 区间内所有生效的版本（用于跨月分段计算） */
    @GetMapping("/range")
    public Result<List<CommissionRate>> rangeVersions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(commissionRateService.getRangeVersions(startDate, endDate));
    }

    /** 历史版本分页列表 */
    @GetMapping("/history")
    public Result<Map<String, Object>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<CommissionRate> records = commissionRateService.listHistory(page, size);
        int total = commissionRateService.countAll();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("current", page);
        data.put("size", size);
        return Result.success(data);
    }

    /** 新增一版（自动关闭上一版，留痕保存） */
    @PostMapping
    public Result<CommissionRate> createVersion(@RequestBody CommissionRate body, HttpServletRequest req) {
        String operator = SecurityUtils.getCurrentUsername(req);
        try {
            CommissionRate saved = commissionRateService.createVersion(body, operator);
            return Result.success(saved);
        } catch (IllegalArgumentException e) {
            log.warn("新增提成率版本失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}
