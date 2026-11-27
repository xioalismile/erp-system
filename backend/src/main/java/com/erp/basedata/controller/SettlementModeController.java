package com.erp.basedata.controller;

import com.erp.basedata.entity.SettlementMode;
import com.erp.basedata.service.SettlementModeService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 结算方式 Controller
 * 路由: /api/basedata/settle
 */
@RestController
@RequestMapping("/api/basedata/settle")
public class SettlementModeController extends BaseCrudController<SettlementMode, Long> {

    private final SettlementModeService settlementModeService;

    public SettlementModeController(SettlementModeService settlementModeService) {
        this.settlementModeService = settlementModeService;
    }

    @Override
    protected IService<SettlementMode> getService() { return settlementModeService; }

    @Override
    protected String getCodeField() { return "settle_mode_code"; }

    @Override
    protected String getIdField() { return "settle_mode_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='settle'） */
    @Override
    protected String getCustomFieldEntityType() { return "settle"; }

    @Override
    protected List<String> getSearchFields() { return List.of("settle_mode_code", "settle_mode_name"); }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "created_at",
            "created_by",
            "remark",
            "settle_mode_code",
            "settle_mode_id",
            "settle_mode_name",
            "settle_type",
            "sort_order",
            "status",
            "updated_at",
            "updated_by"
        );
    }
}
