package com.erp.basedata.controller;

import com.erp.basedata.entity.SettlementMode;
import com.erp.basedata.service.SettlementModeService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

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

    @Override
    protected List<String> getSearchFields() { return List.of("settle_mode_code", "settle_mode_name"); }
}
