package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 销售出库明细报表 Service（与销售订单明细报表同一引擎，仅配置不同）。
 */
@Service
public class SaleDeliveryDetailReportService extends BillDetailReportEngine {

    public SaleDeliveryDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SaleBillDetailReportConfigs.deliveryConfig());
    }
}
