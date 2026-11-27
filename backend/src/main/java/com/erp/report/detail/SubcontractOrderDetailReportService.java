package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 委外加工单明细报表 Service（与销售/采购明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link SubcontractBillDetailReportConfigs#orderConfig()}）。
 */
@Service
public class SubcontractOrderDetailReportService extends BillDetailReportEngine {

    public SubcontractOrderDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SubcontractBillDetailReportConfigs.orderConfig());
    }
}
