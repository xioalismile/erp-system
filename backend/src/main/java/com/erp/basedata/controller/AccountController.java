package com.erp.basedata.controller;

import com.erp.basedata.entity.Account;
import com.erp.basedata.service.AccountService;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 会计科目 Controller
 * 路由: /api/basedata/account
 */
@RestController
@RequestMapping("/api/basedata/account")
public class AccountController extends BaseCrudController<Account, Long> {

    private final AccountService accountService;
    private final DataScopeResolver dataScopeResolver;

    public AccountController(AccountService accountService, DataScopeResolver dataScopeResolver) {
        this.accountService = accountService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 数据权限（2026-09-28 统一口径）：可见数据 = 角色数据范围内的记录
     * （仅本人＝自己创建的；本部门＝部门成员创建的；全部＝不过滤）。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<Account> qw) {
        dataScopeResolver.resolve(currentRequest()).applyTo(qw, null);
    }

    @Override
    protected IService<Account> getService() { return accountService; }

    @Override
    protected String getCodeField() { return "account_code"; }

    @Override
    protected String getIdField() { return "account_id"; }

    // 会计科目不接入自定义字段（与"不接入字段权限/不进 EntityMetadataRegistry"同口径）——按用户要求全面注释，勿恢复
    // /** 自定义字段搜索实体类型（sys_custom_field.entity_type='account'） */
    // @Override
    // protected String getCustomFieldEntityType() { return "account"; }

    @Override
    protected List<String> getSearchFields() { return List.of("account_code", "account_name", "account_full_name"); }

    /**
     * 返回全量会计科目列表，前端依据 parent_account_id 构建树
     */
    @GetMapping("/tree")
    public Result<List<Account>> tree() {
        // 数据权限（2026-09-28）：树与列表同口径 —— 否则出现"列表已收窄、树仍是全量"两套口径
        QueryWrapper<Account> qw = new QueryWrapper<>();
        applyDataScopeFilter(qw);
        return Result.success(accountService.list(qw));
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "account_code",
            "account_full_name",
            "account_id",
            "account_level",
            "account_name",
            "account_type",
            "auxiliary_accounting",
            "balance_direction",
            "cash_flow_item",
            "created_at",
            "created_by",
            "is_cash_account",
            "is_leaf",
            "is_qc_required",
            "is_qc_required_quantity",
            "parent_account_id",
            "remark",
            "status",
            "updated_at",
            "updated_by"
        );
    }
}
