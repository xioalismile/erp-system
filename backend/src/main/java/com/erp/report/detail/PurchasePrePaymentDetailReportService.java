package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 采购预付款单明细报表 Service（与销售/采购明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link FinanceBillDetailReportConfigs#purchasePrePaymentConfig()}）。
 */
@Service
public class PurchasePrePaymentDetailReportService extends BillDetailReportEngine {

    public PurchasePrePaymentDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, FinanceBillDetailReportConfigs.purchasePrePaymentConfig());
    }
}
