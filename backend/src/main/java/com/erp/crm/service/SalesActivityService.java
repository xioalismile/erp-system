package com.erp.crm.service;

import com.erp.common.scope.DataScopeResolver;

import java.time.LocalDate;
import java.util.Map;

/**
 * 销售过程动作量化 · 指标统计（2026-10-07）
 *
 * <p>取数口径见立项登记表第七节（实现唯一依据，禁止实现时改口径）。
 * 全部按数据范围过滤：本人 / 本部门 / 全部。</p>
 */
public interface SalesActivityService {

    /** 每日任务 + 本周任务 + 今日任务清单 */
    Map<String, Object> daily(LocalDate date, Long staffId, DataScopeResolver.DataScopeContext ctx);

    /** 过程奖金核算（月度） */
    Map<String, Object> bonus(int year, int month, Long staffId, DataScopeResolver.DataScopeContext ctx);
}
