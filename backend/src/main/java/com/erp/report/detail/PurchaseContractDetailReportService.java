package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 采购合同明细报表 Service（与销售订单明细报表共用同一引擎 ${@link BillDetailReportEngine}，
 * 差异只在 ${@link PurchaseBillDetailReportConfigs#contractConfig()}）。
 */
@Service
public class PurchaseContractDetailReportService extends BillDetailReportEngine {

    public PurchaseContractDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, PurchaseBillDetailReportConfigs.contractConfig());
    }
}
