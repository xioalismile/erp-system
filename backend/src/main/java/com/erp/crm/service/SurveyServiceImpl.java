package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.crm.entity.Survey;
import com.erp.crm.mapper.SurveyMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyServiceImpl extends ServiceImpl<SurveyMapper, Survey> implements SurveyService {

    private final CustomerService customerService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    private static final List<String> STATUS_FLOW = List.of("草稿", "进行中", "已完成", "已分析");
    private static final Set<String> VALID_SURVEY_TYPES = Set.of("交易后", "定期回访", "投诉回访", "新客户");

    @Override
    public PageResult<Survey> pageQuery(int current, int size, String keyword, String status,
                                         String surveyType, Long customerId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<Survey> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("survey_no", keyword)
                    .or().like("survey_name", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("contact_person", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (surveyType != null && !surveyType.isBlank()) {
            wrapper.eq("survey_type", surveyType);
        }
        if (customerId != null) {
            wrapper.eq("customer_id", customerId);
        }
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建的；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<Survey> page = new Page<>(current, size);
        Page<Survey> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Survey getDetail(Long id) {
        Survey survey = getById(id);
        if (survey == null) throw new BusinessException("满意度调查不存在");
        return survey;
    }

    @Override
    @Transactional
    public void saveSurvey(Survey survey) {
        // 输入验证
        if (survey.getSurveyName() == null || survey.getSurveyName().isBlank()) {
            throw new BusinessException("调查名称不能为空");
        }
        if (survey.getSurveyType() != null && !VALID_SURVEY_TYPES.contains(survey.getSurveyType())) {
            throw new BusinessException("无效的调查类型: " + survey.getSurveyType());
        }
        if (survey.getOverallScore() != null && (survey.getOverallScore() < 0 || survey.getOverallScore() > 5)) {
            throw new BusinessException("总体评分必须在 0-5 之间");
        }

        if (survey.getSurveyNo() == null || survey.getSurveyNo().isBlank()) {
            survey.setSurveyNo(generateSurveyNo());
        }
        if (survey.getStatus() == null) survey.setStatus("草稿");
        if (survey.getOverallScore() == null) survey.setOverallScore(0);
        if (survey.getProductScore() == null) survey.setProductScore(0);
        if (survey.getServiceScore() == null) survey.setServiceScore(0);
        if (survey.getPriceScore() == null) survey.setPriceScore(0);
        if (survey.getDeliveryScore() == null) survey.setDeliveryScore(0);
        if (survey.getAfterSalesScore() == null) survey.setAfterSalesScore(0);
        if (survey.getSurveyDate() == null) survey.setSurveyDate(LocalDate.now());

        // 文档规范: 非草稿状态需校验总体评分范围 1-5
        if (!"草稿".equals(survey.getStatus())) {
            validateOverallScore(survey.getOverallScore());
        }

        save(survey);
        log.info("满意度调查保存成功: {} 状态: {}", survey.getSurveyNo(), survey.getStatus());
    }

    private void validateOverallScore(Integer overallScore) {
        if (overallScore == null || overallScore < 1 || overallScore > 5) {
            throw new BusinessException("总体评分必须在 1-5 之间");
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        if (status == null || !STATUS_FLOW.contains(status)) {
            throw new BusinessException("无效的状态: " + status);
        }
        Survey survey = getById(id);
        if (survey == null) throw new BusinessException("满意度调查不存在");
        String current = survey.getStatus();
        if (current == null || !STATUS_FLOW.contains(current)) {
            throw new BusinessException("当前状态无效: " + current);
        }
        int currentIdx = STATUS_FLOW.indexOf(current);
        int targetIdx = STATUS_FLOW.indexOf(status);
        // 文档规范: 状态只能正向流转, 不能回退
        if (targetIdx < currentIdx) {
            throw new BusinessException("状态不能回退: " + current + " → " + status);
        }
        if (targetIdx == currentIdx) {
            return;
        }
        // 非草稿状态切换需校验总体评分
        if (!"草稿".equals(status)) {
            validateOverallScore(survey.getOverallScore());
        }
        survey.setStatus(status);
        updateById(survey);
        log.info("满意度调查状态更新: {} → {}", survey.getSurveyNo(), status);
    }

    @Override
    @Transactional
    public void complete(Long id) {
        Survey survey = getById(id);
        if (survey == null) throw new BusinessException("满意度调查不存在");
        if (!"草稿".equals(survey.getStatus()) && !"进行中".equals(survey.getStatus())) {
            throw new BusinessException("只有草稿/进行中状态可完成, 当前状态: " + survey.getStatus());
        }
        validateOverallScore(survey.getOverallScore());
        survey.setStatus("已完成");
        updateById(survey);

        // 文档规范: 完成调查后, 同步更新客户满意度评分
        if (survey.getCustomerId() != null) {
            Customer customer = customerService.getById(survey.getCustomerId());
            if (customer != null) {
                customer.setSatisfactionScore(survey.getOverallScore());
                customerService.updateById(customer);
            }
        }
        log.info("满意度调查完成: {} 客户: {} 满意度: {}",
                survey.getSurveyNo(), survey.getCustomerId(), survey.getOverallScore());
    }

    @Override
    @Transactional
    public void analyze(Long id, String analyzeResult) {
        Survey survey = getById(id);
        if (survey == null) throw new BusinessException("满意度调查不存在");
        if (!"已完成".equals(survey.getStatus())) {
            throw new BusinessException("只有已完成状态可分析, 当前状态: " + survey.getStatus());
        }
        survey.setStatus("已分析");
        survey.setAnalyzeResult(analyzeResult);
        updateById(survey);
        log.info("满意度调查分析完成: {}", survey.getSurveyNo());
    }

    @Override
    public String generateSurveyNo() {
        String docType = "SURVEY";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SUR-" + dateStr + "-";
            QueryWrapper<Survey> wrapper = new QueryWrapper<>();
            wrapper.likeRight("survey_no", prefix).orderByDesc("survey_no").last("LIMIT 1");
            Survey last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getSurveyNo() != null) {
                try {
                    seq = Integer.parseInt(last.getSurveyNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }
}
