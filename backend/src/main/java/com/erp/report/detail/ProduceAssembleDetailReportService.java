package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 组装单明细报表 Service（与其它明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link ProductionExtraBillDetailReportConfigs#assembleConfig()}）。
 */
@Service
public class ProduceAssembleDetailReportService extends BillDetailReportEngine {

    public ProduceAssembleDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, ProductionExtraBillDetailReportConfigs.assembleConfig());
    }
}
