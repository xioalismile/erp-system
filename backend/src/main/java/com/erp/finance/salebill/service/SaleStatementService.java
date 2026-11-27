package com.erp.finance.salebill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.salebill.entity.SaleStatement;
import com.erp.finance.salebill.entity.SaleStatementItem;

import java.time.LocalDate;
import java.util.List;

public interface SaleStatementService extends IService<SaleStatement> {

    PageResult<SaleStatement> pageQuery(int current, int size, String keyword, Integer status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    SaleStatement getDetail(Long id);

    SaleStatement createStatement(Long customerId, LocalDate startDate, LocalDate endDate);

    void confirm(Long id);

    void close(Long id);

    SaleStatement saveStatement(SaleStatement statement, List<SaleStatementItem> items);

    boolean delete(Long id);
}
