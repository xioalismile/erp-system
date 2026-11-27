package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 销售发票明细报表 Service（与销售订单明细报表同一引擎，仅配置不同）。
 *
 * <p>注意：这与「销售发票报表」（`SaleInvoiceReport.vue` + `/api/report/sale/invoice*`，
 * 开票概览/趋势/客户分析/税额分析四个分析区块）是**两个不同的东西**：
 * 前者是拍平明细表，后者是分析报表，两者并存、互不覆盖。
 */
@Service
public class SaleInvoiceDetailReportService extends BillDetailReportEngine {

    public SaleInvoiceDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SaleBillDetailReportConfigs.invoiceConfig());
    }
}
