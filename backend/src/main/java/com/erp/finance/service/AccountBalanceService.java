package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.entity.AccountBalance;

import java.util.List;

/**
 * 科目余额服务
 */
public interface AccountBalanceService extends IService<AccountBalance> {

    /**
     * 按期间和科目查询余额
     */
    List<AccountBalance> listBalance(Integer periodYear, Integer periodMonth, String accountCode);

    /**
     * 分页查询科目余额
     */
    PageResult<AccountBalance> pageBalance(int current, int size, Integer periodYear,
                                            Integer periodMonth, String accountCode);
}
