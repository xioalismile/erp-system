package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Account;
import com.erp.basedata.mapper.AccountMapper;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    @Override
    public Account getByCode(String accountCode) {
        return getOne(new QueryWrapper<Account>().eq("account_code", accountCode));
    }

    @Override
    public boolean existsByCode(String accountCode) {
        return baseMapper.exists(new QueryWrapper<Account>().eq("account_code", accountCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String accountCode, Long accountId) {
        return baseMapper.exists(new QueryWrapper<Account>()
                .eq("account_code", accountCode).ne("account_id", accountId));
    }
}
