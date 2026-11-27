package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.crm.entity.Survey;

public interface SurveyService extends IService<Survey> {

    PageResult<Survey> pageQuery(int current, int size, String keyword, String status,
                                  String surveyType, Long customerId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    Survey getDetail(Long id);

    void saveSurvey(Survey survey);

    /** 更新状态: 草稿/进行中/已完成/已分析 */
    void updateStatus(Long id, String status);

    /** 分析: 已完成 → 已分析 */
    void analyze(Long id, String analyzeResult);

    /** 完成: 草稿/进行中 → 已完成, 同步更新客户满意度评分 */
    void complete(Long id);

    String generateSurveyNo();
}
