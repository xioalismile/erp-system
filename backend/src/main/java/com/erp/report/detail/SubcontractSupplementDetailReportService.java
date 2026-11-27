package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 委外补料单明细报表 Service（与销售/采购明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link SubcontractBillDetailReportConfigs#supplementConfig()}）。
 */
@Service
public class SubcontractSupplementDetailReportService extends BillDetailReportEngine {

    public SubcontractSupplementDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SubcontractBillDetailReportConfigs.supplementConfig());
    }
}
