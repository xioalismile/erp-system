package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 采购申请单明细报表 Service（与销售订单明细报表共用同一引擎 ${@link BillDetailReportEngine}，
 * 差异只在 ${@link PurchaseBillDetailReportConfigs#requestConfig()}）。
 */
@Service
public class PurchaseRequestDetailReportService extends BillDetailReportEngine {

    public PurchaseRequestDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, PurchaseBillDetailReportConfigs.requestConfig());
    }
}
