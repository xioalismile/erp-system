package com.erp.basedata.controller;

import com.erp.basedata.entity.BankAccount;
import com.erp.basedata.service.BankAccountService;
import com.erp.common.scope.DataScopeResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 银行账户（财务账户） Controller
 * 路由: /api/basedata/bank-account
 */
@RestController
@RequestMapping("/api/basedata/bank-account")
public class BankAccountController extends BaseCrudController<BankAccount, Long> {

    private final BankAccountService bankAccountService;
    private final DataScopeResolver dataScopeResolver;

    public BankAccountController(BankAccountService bankAccountService, DataScopeResolver dataScopeResolver) {
        this.bankAccountService = bankAccountService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 数据权限（2026-09-28 统一口径）：可见数据 = 角色数据范围内的记录
     * （仅本人＝自己创建的；本部门＝部门成员创建的；全部＝不过滤）。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<BankAccount> qw) {
        dataScopeResolver.resolve(currentRequest()).applyTo(qw, null);
    }

    @Override
    protected IService<BankAccount> getService() { return bankAccountService; }

    @Override
    protected String getCodeField() { return "account_no"; }

    @Override
    protected String getIdField() { return "account_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='bank_account'） */
    @Override
    protected String getCustomFieldEntityType() { return "bank_account"; }

    @Override
    protected List<String> getSearchFields() { return List.of("account_no", "account_name", "bank_name"); }

    /** 可排序列白名单（实体全部数据库列） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "account_id",
            "account_no",
            "account_name",
            "account_type",
            "bank_name",
            "created_at",
            "created_by",
            "currency_id",
            "current_balance",
            "is_active",
            "is_default",
            "opening_balance",
            "remark",
            "status",
            "updated_at",
            "updated_by"
        );
    }
}
