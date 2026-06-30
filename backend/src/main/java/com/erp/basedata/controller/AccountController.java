package com.erp.basedata.controller;

import com.erp.basedata.entity.Account;
import com.erp.basedata.service.AccountService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会计科目 Controller
 * 路由: /api/basedata/account
 */
@RestController
@RequestMapping("/api/basedata/account")
public class AccountController extends BaseCrudController<Account, Long> {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected IService<Account> getService() { return accountService; }

    @Override
    protected String getCodeField() { return "account_code"; }

    @Override
    protected String getIdField() { return "account_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("account_code", "account_name", "account_full_name"); }

    /**
     * 返回全量会计科目列表，前端依据 parent_account_id 构建树
     */
    @GetMapping("/tree")
    public Result<List<Account>> tree() {
        return Result.success(accountService.list());
    }
}
