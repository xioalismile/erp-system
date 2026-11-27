package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 销售退货单明细报表 Service（与销售/采购/库存明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link SaleExtraBillDetailReportConfigs#returnConfig()}）。
 */
@Service
public class SaleReturnDetailReportService extends BillDetailReportEngine {

    public SaleReturnDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SaleExtraBillDetailReportConfigs.returnConfig());
    }
}
