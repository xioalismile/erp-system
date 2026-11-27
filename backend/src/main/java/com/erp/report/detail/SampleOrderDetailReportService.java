package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 打样单明细报表 Service（与销售/生产等明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link SampleBillDetailReportConfigs#sampleOrderConfig()}）。
 */
@Service
public class SampleOrderDetailReportService extends BillDetailReportEngine {

    public SampleOrderDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SampleBillDetailReportConfigs.sampleOrderConfig());
    }
}
