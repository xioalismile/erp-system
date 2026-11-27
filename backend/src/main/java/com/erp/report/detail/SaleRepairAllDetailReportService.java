package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 售后维修单全明细报表 Service（维修明细 + 配件领用两表合并）。
 *
 * <p>与其它"两张明细"单据共用 {@link BillDetailReportUnion}：引擎仍按"主表 + 一张明细表"工作，
 * 差异只是合并后的配置由 {@link BillDetailReportUnionConfigs#repair(JdbcTemplate)} 提供。</p>
 */
@Service
public class SaleRepairAllDetailReportService extends BillDetailReportEngine {

    public SaleRepairAllDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, BillDetailReportUnionConfigs.repair(jdbcTemplate));
    }
}
