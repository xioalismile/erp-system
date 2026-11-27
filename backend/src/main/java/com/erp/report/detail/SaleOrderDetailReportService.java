package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 销售订单明细报表 Service。
 *
 * <p>本类**故意只有一行逻辑**：全部查询/筛选/权限/合计口径都在 {@link BillDetailReportEngine} 里，
 * 差异只在 {@link SaleBillDetailReportConfigs#orderConfig()}。三个销售单据明细报表共用同一引擎，
 * 保证"功能一模一样"，不会出现改一处漏两处。
 */
@Service
public class SaleOrderDetailReportService extends BillDetailReportEngine {

    public SaleOrderDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, SaleBillDetailReportConfigs.orderConfig());
    }
}
