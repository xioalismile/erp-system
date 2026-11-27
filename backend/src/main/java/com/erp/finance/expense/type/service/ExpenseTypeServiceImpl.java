package com.erp.finance.expense.type.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.finance.expense.type.entity.ExpenseType;
import com.erp.finance.expense.type.mapper.ExpenseTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.erp.common.util.SortHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class ExpenseTypeServiceImpl extends ServiceImpl<ExpenseTypeMapper, ExpenseType>
        implements ExpenseTypeService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "cost_sharing_method",
            "create_time",
            "created_at",
            "expense_category",
            "id",
            "remark",
            "status",
            "type_code",
            "type_name",
            "update_time"
        );

    @Override
    public PageResult<ExpenseType> pageQuery(int current, int size, String keyword, String expenseCategory, Integer status, String sortBy, String sortOrder) {
        QueryWrapper<ExpenseType> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("type_code", keyword)
                    .or().like("type_name", keyword)
                    .or().like("remark", keyword));
        }
        if (expenseCategory != null && !expenseCategory.isBlank()) {
            wrapper.eq("expense_category", expenseCategory);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, ExpenseType.class);
        Page<ExpenseType> page = new Page<>(current, size);
        Page<ExpenseType> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public ExpenseType getDetail(Long id) {
        ExpenseType expenseType = super.getById(id);
        if (expenseType == null) {
            throw new BusinessException("费用类型不存在");
        }
        return expenseType;
    }

    @Override
    public ExpenseType saveExpenseType(ExpenseType expenseType) {
        if (expenseType.getId() == null) {
            if (expenseType.getStatus() == null) {
                expenseType.setStatus(1);
            }
            expenseType.setCreateTime(LocalDateTime.now());
            expenseType.setUpdateTime(LocalDateTime.now());
            baseMapper.insert(expenseType);
            log.info("新增费用类型: {}", expenseType.getTypeName());
        } else {
            ExpenseType existing = super.getById(expenseType.getId());
            if (existing == null) {
                throw new BusinessException("费用类型不存在");
            }
            expenseType.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(expenseType);
            log.info("修改费用类型: {}", expenseType.getTypeName());
        }
        return getDetail(expenseType.getId());
    }

    @Override
    public boolean delete(Long id) {
        ExpenseType expenseType = super.getById(id);
        if (expenseType == null) {
            throw new BusinessException("费用类型不存在");
        }
        boolean ok = removeById(id);
        log.info("删除费用类型: {} 结果: {}", expenseType.getTypeName(), ok);
        return ok;
    }

    @Override
    public List<ExpenseType> listByCategory(String expenseCategory) {
        QueryWrapper<ExpenseType> wrapper = new QueryWrapper<>();
        wrapper.eq("expense_category", expenseCategory);
        wrapper.eq("status", 1);
        wrapper.orderByAsc("type_code");
        return list(wrapper);
    }
}
