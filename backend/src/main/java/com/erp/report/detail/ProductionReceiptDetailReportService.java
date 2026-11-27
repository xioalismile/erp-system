package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 生产侧单据明细报表 Service（列表页明细模式同源接口）。
 * 全部查询/筛选/权限/合计口径都在 {@link BillDetailReportEngine}，差异只在配置。
 */
@Service
public class ProductionReceiptDetailReportService extends BillDetailReportEngine {

    public ProductionReceiptDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, ProductionBillDetailReportConfigs.productionReceiptConfig());
    }
}