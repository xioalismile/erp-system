package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.ReferenceCheckService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 客户 Controller
 * 路由: /api/basedata/customer
 */
@RestController
@RequestMapping("/api/basedata/customer")
public class CustomerController extends BaseCrudController<Customer, Long> {

    private final CustomerService customerService;
    private final ReferenceCheckService referenceCheckService;
    private final DataScopeResolver dataScopeResolver;

    public CustomerController(CustomerService customerService, ReferenceCheckService referenceCheckService,
                              DataScopeResolver dataScopeResolver) {
        this.customerService = customerService;
        this.referenceCheckService = referenceCheckService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 删除前校验：被业务数据引用的客户禁止删除（防止单据悬空引用） */
    @Override
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        String err = referenceCheckService.checkCustomer(id);
        if (err != null) return Result.error(409, err);
        return super.delete(id);
    }

    /** 批量删除前逐个校验引用 */
    @Override
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            String err = referenceCheckService.checkCustomer(id);
            if (err != null) return Result.error(409, "ID=" + id + " " + err);
        }
        return super.batchDelete(ids);
    }

    @Override
    protected IService<Customer> getService() { return customerService; }

    @Override
    protected String getCodeField() { return "customer_code"; }

    @Override
    protected String getIdField() { return "customer_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("customer_code", "customer_name", "short_name"); }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='customer'） */
    @Override
    protected String getCustomFieldEntityType() { return "customer"; }

    /**
     * 客户可见口径（2026-10-09 收口）：数据范围 + 客户白名单 + 档案域准入（仅正式客户/存量档案客户）。
     * 统一调用 DataScopeResolver#applyVisibleCustomers —— 客户档案列表、列表页「客户」筛选下拉、
     * 单据选客户下拉共用这一份，禁止再各写一套（历史现象：下拉能翻出全公司客户）。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<Customer> qw) {
        dataScopeResolver.applyVisibleCustomers(qw, currentRequest());
    }

    /**
     * 建档时间区间过滤（数据看板"新客户贡献"穿透用）。
     * 客户档案有 created_at 列；参数为空/非法时不加条件，默认查询行为不变。
     */
    @Override
    protected void applyCreatedRangeFilter(QueryWrapper<Customer> qw, String createdFrom, String createdTo) {
        java.time.LocalDate from = parseDate(createdFrom);
        java.time.LocalDate to = parseDate(createdTo);
        if (from != null) qw.ge("created_at", from.atStartOfDay());
        if (to != null) qw.lt("created_at", to.plusDays(1).atStartOfDay());
    }

    private static java.time.LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return java.time.LocalDate.parse(s.trim()); } catch (Exception e) { return null; }
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "address",
            "annual_revenue",
            "assigned_sales_id",
            "bank_account",
            "bank_name",
            "business_opportunity_count",
            "company_scale",
            "complaint_count",
            "contact_person",
            "contact_phone",
            "created_at",
            "created_by",
            "credit_limit",
            "crm_status",
            "currency_id",
            "customer_code",
            "customer_demand",
            "customer_id",
            "customer_level",
            "customer_name",
            "customer_preference",
            "customer_type",
            "default_price_level_id",
            "default_saler_id",
            "email",
            "ext_data",
            "family_background",
            "follow_up_count",
            "important_level",
            "industry",
            "intention_level",
            "is_blacklist",
            "is_top",
            "last_contact_date",
            "last_follow_up_date",
            "last_order_date",
            "legal_person",
            "main_business",
            "next_contact_date",
            "order_count",
            "owner_user_id",
            "payment_term_id",
            "pool_enter_date",
            "pool_type",
            "potential_value",
            "project_type",
            "refund_amount",
            "remark",
            "sales_stage",
            "satisfaction_score",
            "short_name",
            "source_detail",
            "source_type",
            "status",
            "tag",
            "tax_no",
            "tax_register_no",
            "top_time",
            "total_order_amount",
            "updated_at",
            "updated_by",
            "website",
            "wechat"
        );
    }
}
