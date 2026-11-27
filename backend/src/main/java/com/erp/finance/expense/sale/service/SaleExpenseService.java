package com.erp.finance.expense.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.expense.sale.entity.SaleExpense;
import com.erp.finance.expense.sale.entity.SaleExpenseItem;

import java.util.List;

public interface SaleExpenseService extends IService<SaleExpense> {

    PageResult<SaleExpense> pageQuery(int current, int size, String keyword, Integer status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    SaleExpense getDetail(Long id);

    SaleExpense saveExpense(SaleExpense expense, List<SaleExpenseItem> items);

    void audit(Long id);

    void voidExpense(Long id);

    void allocateCost(Long id);

    boolean delete(Long id);
}
