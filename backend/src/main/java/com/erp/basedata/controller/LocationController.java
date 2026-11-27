package com.erp.basedata.controller;

import com.erp.basedata.entity.Location;
import com.erp.basedata.service.LocationService;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 货位 Controller
 * 路由: /api/basedata/location
 */
@RestController
@RequestMapping("/api/basedata/location")
public class LocationController extends BaseCrudController<Location, Long> {

    private final LocationService locationService;
    private final DataScopeResolver dataScopeResolver;

    public LocationController(LocationService locationService, DataScopeResolver dataScopeResolver) {
        this.locationService = locationService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 数据权限（2026-09-28 统一口径）：可见数据 = 角色数据范围内的记录
     * （仅本人＝自己创建的；本部门＝部门成员创建的；全部＝不过滤）。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<Location> qw) {
        dataScopeResolver.resolve(currentRequest()).applyTo(qw, null);
    }

    @Override
    protected IService<Location> getService() { return locationService; }

    @Override
    protected String getCodeField() { return "bin_code"; }

    @Override
    protected String getIdField() { return "bin_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='location'） */
    @Override
    protected String getCustomFieldEntityType() { return "location"; }

    @Override
    protected List<String> getSearchFields() { return List.of("bin_code", "bin_name"); }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "bin_code",
            "bin_id",
            "bin_name",
            "created_at",
            "created_by",
            "remark",
            "status",
            "stock_id",
            "updated_at",
            "updated_by"
        );
    }
}
