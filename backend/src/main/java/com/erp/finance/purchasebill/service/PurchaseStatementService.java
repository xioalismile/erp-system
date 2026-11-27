package com.erp.finance.purchasebill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.purchasebill.entity.PurchaseStatement;
import com.erp.finance.purchasebill.entity.PurchaseStatementItem;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseStatementService extends IService<PurchaseStatement> {

    PageResult<PurchaseStatement> pageQuery(int current, int size, String keyword, Integer status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    PurchaseStatement getDetail(Long id);

    PurchaseStatement createStatement(Long supplierId, LocalDate startDate, LocalDate endDate);

    void confirm(Long id);

    void close(Long id);

    PurchaseStatement saveStatement(PurchaseStatement statement, List<PurchaseStatementItem> items);

    boolean delete(Long id);
}
