package com.erp.finance.expense.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.expense.purchase.entity.PurchaseExpense;
import com.erp.finance.expense.purchase.entity.PurchaseExpenseItem;

import java.util.List;

public interface PurchaseExpenseService extends IService<PurchaseExpense> {

    PageResult<PurchaseExpense> pageQuery(int current, int size, String keyword, Integer status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    PurchaseExpense getDetail(Long id);

    PurchaseExpense saveExpense(PurchaseExpense expense, List<PurchaseExpenseItem> items);

    void audit(Long id);

    void voidExpense(Long id);

    void allocateCost(Long id);

    boolean delete(Long id);
}
