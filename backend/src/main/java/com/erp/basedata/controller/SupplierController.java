package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.config.SysParamValidator;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.ReferenceCheckService;
import com.erp.masterdata.service.SupplierService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 供应商 Controller
 * 路由: /api/basedata/supplier
 */
@RestController
@RequestMapping("/api/basedata/supplier")
public class SupplierController extends BaseCrudController<Supplier, Long> {

    private final SupplierService supplierService;
    private final SysParamValidator sysParamValidator;
    private final ReferenceCheckService referenceCheckService;
    private final DataScopeResolver dataScopeResolver;

    public SupplierController(SupplierService supplierService, SysParamValidator sysParamValidator,
                              ReferenceCheckService referenceCheckService,
                              DataScopeResolver dataScopeResolver) {
        this.supplierService = supplierService;
        this.sysParamValidator = sysParamValidator;
        this.referenceCheckService = referenceCheckService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 删除前校验：被业务数据引用的供应商禁止删除（防止单据悬空引用） */
    @Override
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        String err = referenceCheckService.checkSupplier(id);
        if (err != null) return Result.error(409, err);
        return super.delete(id);
    }

    /** 批量删除前逐个校验引用 */
    @Override
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            String err = referenceCheckService.checkSupplier(id);
            if (err != null) return Result.error(409, "ID=" + id + " " + err);
        }
        return super.batchDelete(ids);
    }

    @Override
    protected IService<Supplier> getService() { return supplierService; }

    @Override
    protected String getCodeField() { return "supplier_code"; }

    @Override
    protected String getIdField() { return "supplier_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='supplier'） */
    @Override
    protected String getCustomFieldEntityType() { return "supplier"; }

    @Override
    protected List<String> getSearchFields() { return List.of("supplier_code", "supplier_name", "short_name"); }

    /**
     * 数据权限（2026-09-02 体系落地）：
     * 可见供应商 = 数据范围内的供应商（created_by 按角色 data_scope） ∪ 员工权限补充白名单供应商。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<Supplier> qw) {
        // 2026-10-09 收口：数据范围 + 供应商白名单，唯一一份；供应商下拉与本档案列表同口径
        dataScopeResolver.applyVisibleSuppliers(qw, currentRequest());
    }

    /**
     * 重写保存：编码留空时交由 Service 按「自动编号设置 → 供应商档案」规则取号。
     *
     * <p>历史上这里有一条基于系统参数 {@code sys.auto.supplier.no} 的本地取号（SUP+日期+序号），
     * 与专用页规则重复且会盖掉用户配置的前缀/日期/流水格式，已移除，改为单一事实来源 = 专用页。</p>
     */
    @Override
    @PostMapping
    public Result<Void> save(@RequestBody Supplier entity) {
        // 编码唯一性校验（编码留空 = 交由 Service 按编号规则自动生成，不能拿空串查重）
        if (entity.getSupplierCode() != null && !entity.getSupplierCode().isBlank()) {
            long count = supplierService.count(new QueryWrapper<Supplier>().eq("supplier_code", entity.getSupplierCode()));
            if (count > 0) {
                return Result.error(409, "编码已存在");
            }
        }
        // === 设置中心：供应商名称查重（sys.supplier.duplicate.check）===
        if (entity.getSupplierName() != null && !entity.getSupplierName().isBlank()) {
            long nameCount = supplierService.count(new QueryWrapper<Supplier>().eq("supplier_name", entity.getSupplierName()));
            sysParamValidator.validateSupplierNameDuplicate(entity.getSupplierName(), nameCount > 0);
        }
        supplierService.save(entity);
        return Result.success();
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "address",
            "bank_account",
            "bank_name",
            "contact_person",
            "contact_phone",
            "created_at",
            "created_by",
            "currency_id",
            "default_buyer_id",
            "delivery_days",
            "email",
            "legal_person",
            "payment_term_id",
            "remark",
            "short_name",
            "status",
            "supplier_code",
            "supplier_id",
            "supplier_level",
            "supplier_name",
            "supplier_type",
            "tax_no",
            "updated_at",
            "updated_by"
        );
    }
}
