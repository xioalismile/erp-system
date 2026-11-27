package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 销售预收款单明细报表 Service（与销售/采购明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link FinanceBillDetailReportConfigs#salePreReceiptConfig()}）。
 */
@Service
public class SalePreReceiptDetailReportService extends BillDetailReportEngine {

    public SalePreReceiptDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, FinanceBillDetailReportConfigs.salePreReceiptConfig());
    }
}
