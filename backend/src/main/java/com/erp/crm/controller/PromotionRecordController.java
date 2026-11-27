package com.erp.crm.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.PromotionRecord;
import com.erp.crm.service.PromotionRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 推广记录（销售过程动作量化 · 2026-10-07）
 *
 * <p>支撑「每周朋友圈推广 3 次」「RFID 素材覆盖率 100%」两条过程指标。
 * 朋友圈本期按"截图必填 + 主管抽查"判定；企业微信自动判断列入后续单独立项。</p>
 */
@RestController
@RequestMapping("/api/crm/promotion")
@RequiredArgsConstructor
public class PromotionRecordController {

    private final PromotionRecordService promotionRecordService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 分页查询推广记录 */
    @GetMapping("/page")
    public Result<PageResult<PromotionRecord>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(promotionRecordService.pageQuery(
                current, size, keyword, materialType, startDate, endDate, scopeCtx));
    }

    /** 推广记录详情（含发送对象客户清单） */
    @GetMapping("/{id}")
    public Result<PromotionRecord> detail(@PathVariable Long id) {
        return Result.success(promotionRecordService.getDetail(id));
    }

    /** 新增推广记录 */
    @PostMapping
    public Result<PromotionRecord> create(@RequestBody PromotionRecord record) {
        promotionRecordService.savePromotion(record);
        return Result.success(record);
    }

    /** 修改推广记录 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PromotionRecord record) {
        promotionRecordService.updatePromotion(id, record);
        return Result.success();
    }

    /** 删除推广记录（同时删除明细） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promotionRecordService.removePromotion(id);
        return Result.success();
    }

    /** 本周推广统计（销售过程页「本周任务」区用） */
    @GetMapping("/week-stats")
    public Result<Map<String, Object>> weekStats(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long staffId,
            jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        LocalDate day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return Result.success(promotionRecordService.weekStats(day, staffId, scopeCtx));
    }
}
