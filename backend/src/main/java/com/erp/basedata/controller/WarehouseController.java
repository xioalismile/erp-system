package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.scope.DataScopeResolver;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 仓库 Controller
 * 路由: /api/basedata/warehouse
 */
@RestController
@RequestMapping("/api/basedata/warehouse")
public class WarehouseController extends BaseCrudController<Warehouse, Long> {

    private final WarehouseService warehouseService;
    private final DataScopeResolver dataScopeResolver;

    public WarehouseController(WarehouseService warehouseService, DataScopeResolver dataScopeResolver) {
        this.warehouseService = warehouseService;
        this.dataScopeResolver = dataScopeResolver;
    }

    @Override
    protected IService<Warehouse> getService() { return warehouseService; }

    @Override
    protected String getCodeField() { return "stock_code"; }

    @Override
    protected String getIdField() { return "stock_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='warehouse'） */
    @Override
    protected String getCustomFieldEntityType() { return "warehouse"; }

    @Override
    protected List<String> getSearchFields() { return List.of("stock_code", "stock_name"); }

    /**
     * 数据权限（2026-09-02 体系落地）：
     * 可见仓库 = 数据范围内的仓库（created_by 按角色 data_scope） ∪ 员工权限补充白名单仓库。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<Warehouse> qw) {
        // 2026-10-09 收口：数据范围 + 仓库白名单，唯一一份；仓库下拉与本档案列表同口径
        dataScopeResolver.applyVisibleWarehouses(qw, currentRequest());
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "address",
            "created_at",
            "created_by",
            "is_bin_control",
            "is_mrp_available",
            "manager_id",
            "remark",
            "status",
            "stock_code",
            "stock_id",
            "stock_name",
            "stock_type",
            "updated_at",
            "updated_by"
        );
    }
}
