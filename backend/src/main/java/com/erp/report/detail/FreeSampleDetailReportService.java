package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 免费样品订单明细报表 Service（与销售/生产等明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link SampleBillDetailReportConfigs#freeSampleConfig()}）。
 */
@Service
public class FreeSampleDetailReportService extends BillDetailReportEngine {

    public FreeSampleDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SampleBillDetailReportConfigs.freeSampleConfig());
    }
}
