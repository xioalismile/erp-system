package com.erp.acquisition.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.acquisition.entity.AcquisitionResult;
import com.erp.acquisition.entity.AcquisitionTask;
import com.erp.acquisition.mapper.AcquisitionResultMapper;
import com.erp.acquisition.mapper.AcquisitionTaskMapper;
import com.erp.acquisition.service.AcquisitionResultService;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.crm.entity.Lead;
import com.erp.crm.service.LeadService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class AcquisitionResultServiceImpl extends ServiceImpl<AcquisitionResultMapper, AcquisitionResult>
        implements AcquisitionResultService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "ai_raw_result",
            "area",
            "business_scope",
            "company_name",
            "company_status",
            "contact_address",
            "contact_email",
            "contact_phone",
            "create_date",
            "created_at",
            "credit_code",
            "customer_name",
            "deleted",
            "establish_date",
            "imported_lead_id",
            "industry",
            "legal_person",
            "registered_address",
            "registered_capital",
            "reject_reason",
            "remark",
            "result_id",
            "review_status",
            "reviewed_by",
            "reviewed_date",
            "screenshot_path",
            "source_url",
            "task_id"
        );

    private final LeadService leadService;
    private final CustomerService customerService;
    // 直接注入Mapper而非Service，避免与AcquisitionTaskServiceImpl形成循环依赖
    private final AcquisitionTaskMapper acquisitionTaskMapper;

    @Override
    public PageResult<AcquisitionResult> pageQuery(int current, int size, Long taskId, String keyword, String reviewStatus,
                                                   com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                                   String sortBy, String sortOrder) {
        QueryWrapper<AcquisitionResult> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        // 数据权限（2026-09-17 补齐）：采集结果按「所属采集任务」的归属过滤，与任务列表同口径
        List<Long> visibleTaskIds = visibleTaskIdsByScope(scopeCtx);
        if (visibleTaskIds != null) {
            if (visibleTaskIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), 0L, current, size);
            }
            wrapper.in("task_id", visibleTaskIds);
        }
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("company_name", keyword)
                    .or().like("legal_person", keyword)
                    .or().like("credit_code", keyword));
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            wrapper.eq("review_status", reviewStatus);
        }
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) wrapper.orderByDesc("create_date");
        Page<AcquisitionResult> page = new Page<>(current, size);
        Page<AcquisitionResult> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public long countPending(Long taskId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<AcquisitionResult> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).eq("review_status", "pending");
        List<Long> visibleTaskIds = visibleTaskIdsByScope(scopeCtx);
        if (visibleTaskIds != null) {
            if (visibleTaskIds.isEmpty()) return 0L;
            wrapper.in("task_id", visibleTaskIds);
        }
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        return count(wrapper);
    }

    /**
     * 当前用户可见的采集任务ID集合（采集结果的数据权限跟随任务归属）。
     *
     * @return null = 不过滤（全部数据范围）；空列表 = 无任何可见任务（调用方必须直接返回 0 条，禁止不加条件）
     */
    private List<Long> visibleTaskIdsByScope(com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        if (scopeCtx == null || scopeCtx.isAll()) {
            return null;
        }
        QueryWrapper<AcquisitionTask> taskWrapper = new QueryWrapper<>();
        taskWrapper.eq("deleted", 0).select("task_id");
        scopeCtx.applyTo(taskWrapper, null);
        return acquisitionTaskMapper.selectList(taskWrapper).stream()
                .map(AcquisitionTask::getTaskId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public AcquisitionResult getDetail(Long id) {
        AcquisitionResult result = getById(id);
        if (result == null) throw new BusinessException("采集结果不存在");
        return result;
    }

    @Override
    @Transactional
    public List<AcquisitionResult> batchSave(List<AcquisitionResult> results) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }
        for (AcquisitionResult result : results) {
            if (result.getReviewStatus() == null) {
                result.setReviewStatus("pending");
            }
            if (result.getDeleted() == null) {
                result.setDeleted(0);
            }
            result.setCreateDate(LocalDateTime.now());
        }
        saveBatch(results);

        // 更新对应任务的已采集数量
        if (!results.isEmpty() && results.get(0).getTaskId() != null) {
            AcquisitionTask task = acquisitionTaskMapper.selectById(results.get(0).getTaskId());
            if (task != null) {
                int oldCount = task.getCapturedCount() == null ? 0 : task.getCapturedCount();
                task.setCapturedCount(oldCount + results.size());
                task.setModifyDate(LocalDateTime.now());
                acquisitionTaskMapper.updateById(task);
            }
        }
        log.info("批量保存采集结果: {} 条", results.size());
        return results;
    }

    @Override
    @Transactional
    public void review(Long id, String reviewStatus, Long reviewerId, String rejectReason) {
        AcquisitionResult result = getById(id);
        if (result == null) throw new BusinessException("采集结果不存在");
        if (reviewStatus == null || reviewStatus.isBlank()) {
            throw new BusinessException("审核状态不能为空");
        }
        if (!"approved".equals(reviewStatus) && !"rejected".equals(reviewStatus)) {
            throw new BusinessException("无效的审核状态: " + reviewStatus + " (只允许 approved/rejected)");
        }
        if ("rejected".equals(reviewStatus) && (rejectReason == null || rejectReason.isBlank())) {
            throw new BusinessException("拒绝时必须填写拒绝原因");
        }

        // 拒绝：仅更新状态
        if ("rejected".equals(reviewStatus)) {
            result.setReviewStatus(reviewStatus);
            result.setReviewedBy(reviewerId);
            result.setReviewedDate(LocalDateTime.now());
            result.setRejectReason(rejectReason);
            updateById(result);
            log.info("采集结果审核驳回: ID={}", id);
            return;
        }

        // 通过：先更新为 approved 状态，再自动导入CRM并写入公海
        String oldStatus = result.getReviewStatus();
        result.setReviewStatus(reviewStatus);
        result.setReviewedBy(reviewerId);
        result.setReviewedDate(LocalDateTime.now());
        updateById(result);
        log.info("采集结果审核通过: ID={}", id);

        // 已导入过的不重复导入（避免重复审核触发重复写入）
        if ("imported".equals(oldStatus) || result.getImportedLeadId() != null) {
            log.info("采集结果已导入过，跳过自动写入公海: ID={} importedLeadId={}", id, result.getImportedLeadId());
            return;
        }

        // 自动写入公海
        try {
            autoImportToPool(result);
        } catch (Exception e) {
            log.error("审核失败：自动写入公海失败 resultId={}: {}", id, e.getMessage(), e);
            throw new BusinessException("审核失败：自动写入公海失败（" + result.getCompanyName() + "）：" + e.getMessage());
        }
    }

    /**
     * 审批通过后自动写入公海：创建Lead → 转化为Customer到公海池
     */
    private void autoImportToPool(AcquisitionResult result) {
        if (result.getCompanyName() == null || result.getCompanyName().isBlank()) {
            log.warn("采集结果公司名称为空，跳过自动写入公海: resultId={}", result.getResultId());
            return;
        }

        // 按 companyName 在 customer 表查重
        QueryWrapper<Customer> dupWrapper = new QueryWrapper<>();
        dupWrapper.eq("customer_name", result.getCompanyName()).last("LIMIT 1");
        Customer existing = customerService.getOne(dupWrapper);
        if (existing != null) {
            log.info("客户已存在，跳过自动写入公海: {}", result.getCompanyName());
            return;
        }

        // 创建 Lead 对象，从 AcquisitionResult 复制全部字段（11+个字段）
        Lead lead = new Lead();
        lead.setCompanyName(result.getCompanyName());
        lead.setLeadName(result.getCompanyName());
        lead.setContactPerson(result.getLegalPerson());
        lead.setContactPhone(result.getContactPhone());
        lead.setContactEmail(result.getContactEmail());
        lead.setIndustry(result.getIndustry());
        lead.setDemandDesc(result.getBusinessScope());
        lead.setSourceType("企查查");
        // 采集结果扩展字段
        lead.setCreditCode(result.getCreditCode());
        lead.setRegisteredCapital(result.getRegisteredCapital());
        lead.setRegisteredAddress(result.getRegisteredAddress());
        lead.setContactAddress(result.getContactAddress());
        lead.setArea(result.getArea());
        lead.setEstablishDate(result.getEstablishDate());
        lead.setCompanyStatus(result.getCompanyStatus());
        lead.setSourceUrl(result.getSourceUrl());

        leadService.saveLead(lead);

        // 转化为公海客户
        Long customerId = leadService.convertToCustomer(lead.getLeadId(), true);

        // 更新 result: reviewStatus=imported, importedLeadId=新创建的lead的ID
        result.setReviewStatus("imported");
        result.setImportedLeadId(lead.getLeadId());
        updateById(result);

        // 更新对应 task 的 importedCount
        if (result.getTaskId() != null) {
            AcquisitionTask task = acquisitionTaskMapper.selectById(result.getTaskId());
            if (task != null) {
                int oldImported = task.getImportedCount() == null ? 0 : task.getImportedCount();
                task.setImportedCount(oldImported + 1);
                task.setModifyDate(LocalDateTime.now());
                acquisitionTaskMapper.updateById(task);
            }
        }

        log.info("采集结果审批通过自动写入公海成功: resultId={} → leadId={} → customerId={}",
                result.getResultId(), lead.getLeadId(), customerId);
    }

    @Override
    @Transactional
    public int batchImportToLeads(List<Long> resultIds, Long assignedSalesId) {
        if (resultIds == null || resultIds.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        int seq = 0;
        for (Long resultId : resultIds) {
            seq++;
            AcquisitionResult result = getById(resultId);
            if (result == null) {
                log.warn("采集结果不存在，跳过: resultId={}", resultId);
                continue;
            }
            if (!"approved".equals(result.getReviewStatus())) {
                log.warn("采集结果未通过审核，跳过: resultId={} 状态={}", resultId, result.getReviewStatus());
                continue;
            }
            if (result.getCompanyName() == null || result.getCompanyName().isBlank()) {
                log.warn("采集结果公司名称为空，跳过: resultId={}", resultId);
                continue;
            }

            // 按 companyName 在 customer 表查重
            QueryWrapper<Customer> dupWrapper = new QueryWrapper<>();
            dupWrapper.eq("customer_name", result.getCompanyName()).last("LIMIT 1");
            Customer existing = customerService.getOne(dupWrapper);
            if (existing != null) {
                log.info("客户已存在，跳过导入: {}", result.getCompanyName());
                continue;
            }

            // 创建 Lead 对象，从 AcquisitionResult 复制全部字段（11+个字段）
            Lead lead = new Lead();
            // 基础字段
            lead.setCompanyName(result.getCompanyName());
            lead.setLeadName(result.getCompanyName());
            lead.setContactPerson(result.getLegalPerson());
            lead.setContactPhone(result.getContactPhone());
            lead.setContactEmail(result.getContactEmail());
            lead.setIndustry(result.getIndustry());
            lead.setDemandDesc(result.getBusinessScope());
            lead.setSourceType("企查查");
            lead.setAssignedSalesId(assignedSalesId);
            // 采集结果扩展字段（解决"11个字段但能用的只有2个"问题）
            lead.setCreditCode(result.getCreditCode());
            lead.setRegisteredCapital(result.getRegisteredCapital());
            lead.setRegisteredAddress(result.getRegisteredAddress());
            lead.setContactAddress(result.getContactAddress());
            lead.setArea(result.getArea());
            lead.setEstablishDate(result.getEstablishDate());
            lead.setCompanyStatus(result.getCompanyStatus());
            lead.setSourceUrl(result.getSourceUrl());

            try {
                leadService.saveLead(lead);

                // 如果未指定销售员，自动转化为公海客户（与审批通过自动写入公海逻辑一致）
                Long customerId = null;
                if (assignedSalesId == null) {
                    customerId = leadService.convertToCustomer(lead.getLeadId(), true);
                }

                // 更新 result: reviewStatus=imported, importedLeadId=新创建的lead的ID
                result.setReviewStatus("imported");
                result.setImportedLeadId(lead.getLeadId());
                updateById(result);

                // 更新对应 task 的 importedCount
                if (result.getTaskId() != null) {
                    AcquisitionTask task = acquisitionTaskMapper.selectById(result.getTaskId());
                    if (task != null) {
                        int oldImported = task.getImportedCount() == null ? 0 : task.getImportedCount();
                        task.setImportedCount(oldImported + 1);
                        task.setModifyDate(LocalDateTime.now());
                        acquisitionTaskMapper.updateById(task);
                    }
                }

                successCount++;
                log.info("采集结果导入线索成功: resultId={} → leadId={} customerId={}", resultId, lead.getLeadId(), customerId);
            } catch (Exception e) {
                log.error("导入线索失败: 第{}条 resultId={}", seq, resultId, e);
                throw new BusinessException("导入线索失败：第 " + seq + " 条（线索 " + result.getCompanyName() + "）导入失败：" + e.getMessage());
            }
        }

        log.info("批量导入CRM线索完成: 共{}条，成功{}条", resultIds.size(), successCount);
        return successCount;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        AcquisitionResult result = getById(id);
        if (result == null) throw new BusinessException("采集结果不存在");
        result.setDeleted(1);
        updateById(result);
        // 同步减少任务的 capturedCount
        if (result.getTaskId() != null) {
            AcquisitionTask task = acquisitionTaskMapper.selectById(result.getTaskId());
            if (task != null) {
                int oldCount = task.getCapturedCount() == null ? 0 : task.getCapturedCount();
                task.setCapturedCount(Math.max(0, oldCount - 1));
                task.setModifyDate(LocalDateTime.now());
                acquisitionTaskMapper.updateById(task);
            }
        }
        log.info("采集结果已删除: id={}", id);
    }

    @Override
    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int count = 0;
        int seq = 0;
        for (Long id : ids) {
            seq++;
            try {
                deleteById(id);
                count++;
            } catch (Exception e) {
                log.warn("批量删除失败: 第{}条 id={}", seq, id, e);
                throw new BusinessException("批量删除失败：第 " + seq + " 条（ID " + id + "）删除失败：" + e.getMessage());
            }
        }
        log.info("批量删除采集结果完成: 共{}条，成功{}条", ids.size(), count);
        return count;
    }

    @Override
    @Transactional
    public int deleteByTaskId(Long taskId) {
        if (taskId == null) return 0;
        QueryWrapper<AcquisitionResult> wrapper = new QueryWrapper<>();
        wrapper.eq("task_id", taskId).eq("deleted", 0);
        List<AcquisitionResult> results = list(wrapper);
        for (AcquisitionResult r : results) {
            r.setDeleted(1);
        }
        if (!results.isEmpty()) {
            updateBatchById(results);
        }
        // 重置任务的 capturedCount 为 0
        AcquisitionTask task = acquisitionTaskMapper.selectById(taskId);
        if (task != null) {
            task.setCapturedCount(0);
            task.setModifyDate(LocalDateTime.now());
            acquisitionTaskMapper.updateById(task);
        }
        log.info("按任务删除采集结果: taskId={}, 删除{}条", taskId, results.size());
        return results.size();
    }
}
