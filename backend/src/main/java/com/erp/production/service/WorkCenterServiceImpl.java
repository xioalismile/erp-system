package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.production.entity.WorkCenter;
import com.erp.production.mapper.WorkCenterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class WorkCenterServiceImpl extends ServiceImpl<WorkCenterMapper, WorkCenter> implements WorkCenterService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "capacity_per_day",
            "capacity_type",
            "capacity_unit",
            "created_at",
            "created_by",
            "department_id",
            "efficiency",
            "is_active",
            "is_outsourced",
            "labor_rate",
            "location",
            "manager_id",
            "max_workers",
            "overhead_rate",
            "remark",
            "status",
            "supplier_id",
            "updated_at",
            "updated_by",
            "work_center_code",
            "work_center_id",
            "work_center_name",
            "work_center_type"
        );

    @Override
    public PageResult<WorkCenter> pageQuery(int current, int size, String keyword, String status,
                                            String supplierId, String employeeId, String sortBy, String sortOrder) {
        QueryWrapper<WorkCenter> wrapper = new QueryWrapper<>();
        // 关键词多列强制成组（平铺 or() 会让命中第一列的行绕过状态筛选与数据权限）
        com.erp.common.query.ListQuerySupport.applyKeywordGroup(wrapper, keyword, "work_center_code", "work_center_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 供应商维度（委外供应商 supplier_id）与员工维度（负责人 manager_id）
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "manager_id");
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, WorkCenter.class);
        Page<WorkCenter> page = new Page<>(current, size);
        Page<WorkCenter> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public boolean existsByCode(String code) {
        return count(new QueryWrapper<WorkCenter>().eq("work_center_code", code)) > 0;
    }

    @Override
    public boolean existsByCodeExcludeId(String code, Long id) {
        return count(new QueryWrapper<WorkCenter>()
                .eq("work_center_code", code)
                .ne("work_center_id", id)) > 0;
    }

    @Override
    public void validateOutsourced(WorkCenter workCenter) {
        if (workCenter == null) return;
        if (workCenter.getIsOutsourced() != null && workCenter.getIsOutsourced() == (short) 1) {
            if (workCenter.getSupplierId() == null) {
                throw new BusinessException("委外工作中心必须关联供应商");
            }
        }
    }

    @Override
    @Transactional
    public void enable(Long id, String operator) {
        WorkCenter workCenter = getById(id);
        if (workCenter == null) throw new BusinessException("工作中心不存在");
        if (!"停用".equals(workCenter.getStatus())) {
            throw new BusinessException("只有停用状态可启用, 当前: " + workCenter.getStatus());
        }
        workCenter.setStatus("启用");
        workCenter.setIsActive((short) 1);
        workCenter.setUpdatedBy(operator);
        updateById(workCenter);
        log.info("工作中心启用: {} 操作人: {}", workCenter.getWorkCenterCode(), operator);
    }

    @Override
    @Transactional
    public void disable(Long id, String operator) {
        WorkCenter workCenter = getById(id);
        if (workCenter == null) throw new BusinessException("工作中心不存在");
        if (!"启用".equals(workCenter.getStatus())) {
            throw new BusinessException("只有启用状态可停用, 当前: " + workCenter.getStatus());
        }
        workCenter.setStatus("停用");
        workCenter.setIsActive((short) 0);
        workCenter.setUpdatedBy(operator);
        updateById(workCenter);
        log.info("工作中心停用: {} 操作人: {}", workCenter.getWorkCenterCode(), operator);
    }
}
