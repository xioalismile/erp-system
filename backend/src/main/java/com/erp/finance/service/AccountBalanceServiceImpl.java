package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.finance.entity.AccountBalance;
import com.erp.finance.mapper.AccountBalanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 科目余额服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBalanceServiceImpl extends ServiceImpl<AccountBalanceMapper, AccountBalance>
        implements AccountBalanceService {

    private final AccountBalanceMapper balanceMapper;

    @Override
    public List<AccountBalance> listBalance(Integer periodYear, Integer periodMonth, String accountCode) {
        QueryWrapper<AccountBalance> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("account_code");
        if (periodYear != null) wrapper.eq("period_year", periodYear);
        if (periodMonth != null) wrapper.eq("period_month", periodMonth);
        if (accountCode != null && !accountCode.isBlank()) wrapper.like("account_code", accountCode);
        return balanceMapper.selectList(wrapper);
    }

    @Override
    public PageResult<AccountBalance> pageBalance(int current, int size, Integer periodYear,
                                                   Integer periodMonth, String accountCode) {
        QueryWrapper<AccountBalance> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("account_code");
        if (periodYear != null) wrapper.eq("period_year", periodYear);
        if (periodMonth != null) wrapper.eq("period_month", periodMonth);
        if (accountCode != null && !accountCode.isBlank()) wrapper.like("account_code", accountCode);
        Page<AccountBalance> page = new Page<>(current, size);
        Page<AccountBalance> result = balanceMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }
}
