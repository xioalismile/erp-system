package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.common.scope.DataScopeResolver;
import com.erp.crm.entity.PromotionRecord;

import java.time.LocalDate;
import java.util.Map;

/**
 * 推广记录（销售过程动作量化 · 2026-10-07）
 */
public interface PromotionRecordService extends IService<PromotionRecord> {

    PageResult<PromotionRecord> pageQuery(int current, int size, String keyword, String materialType,
                                          String startDate, String endDate,
                                          DataScopeResolver.DataScopeContext scopeCtx);

    PromotionRecord getDetail(Long id);

    void savePromotion(PromotionRecord record);

    void updatePromotion(Long id, PromotionRecord record);

    void removePromotion(Long id);

    /** 本周推广统计：推广次数（朋友圈类且有截图）、记录总数、覆盖客户去重数、RFID 覆盖客户去重数 */
    Map<String, Object> weekStats(LocalDate anyDayInWeek, Long staffId,
                                  DataScopeResolver.DataScopeContext scopeCtx);

    String generatePromotionNo();
}
