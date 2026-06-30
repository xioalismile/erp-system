package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Account;

public interface AccountService extends IService<Account> {
    Account getByCode(String accountCode);
    boolean existsByCode(String accountCode);
    boolean existsByCodeExcludeId(String accountCode, Long accountId);
}
