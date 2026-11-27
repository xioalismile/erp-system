package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 生产工单 · 全明细报表（成品明细 + 原料明细合并；列表页明细模式使用）。 */
@Service
public class ProductionOrderAllDetailReportService extends BillDetailReportEngine {

    public ProductionOrderAllDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, () -> BillDetailReportUnionConfigs.workOrder(jdbcTemplate));
    }
}
