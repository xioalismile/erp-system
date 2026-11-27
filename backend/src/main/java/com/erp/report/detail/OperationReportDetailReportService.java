package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 工序汇报单明细报表 Service（与其它明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link ProductionExtraBillDetailReportConfigs#operationReportConfig()}）。
 * 该单据无子表：引擎自动走「一行 = 一张单」模式。
 */
@Service
public class OperationReportDetailReportService extends BillDetailReportEngine {

    public OperationReportDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, ProductionExtraBillDetailReportConfigs.operationReportConfig());
    }
}
