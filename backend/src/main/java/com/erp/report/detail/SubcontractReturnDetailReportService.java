package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 委外退料单明细报表 Service（与销售/采购明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link SubcontractBillDetailReportConfigs#returnConfig()}）。
 */
@Service
public class SubcontractReturnDetailReportService extends BillDetailReportEngine {

    public SubcontractReturnDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SubcontractBillDetailReportConfigs.returnConfig());
    }
}
