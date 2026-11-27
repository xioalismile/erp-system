package com.erp.finance.expense.type.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.expense.type.entity.ExpenseType;

import java.util.List;

public interface ExpenseTypeService extends IService<ExpenseType> {

    PageResult<ExpenseType> pageQuery(int current, int size, String keyword, String expenseCategory, Integer status, String sortBy, String sortOrder);

    ExpenseType getDetail(Long id);

    ExpenseType saveExpenseType(ExpenseType expenseType);

    boolean delete(Long id);

    List<ExpenseType> listByCategory(String expenseCategory);
}
