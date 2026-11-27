package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 付款单 · 全明细报表（核销明细 + 付款明细合并；列表页明细模式使用）。 */
@Service
public class PaymentVoucherAllDetailReportService extends BillDetailReportEngine {

    public PaymentVoucherAllDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, () -> BillDetailReportUnionConfigs.voucher(jdbcTemplate));
    }
}
