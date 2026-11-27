package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 费用分摊单 · 全明细报表（费用明细 + 分摊明细合并；列表页明细模式使用）。 */
@Service
public class OperationExpenseAllDetailReportService extends BillDetailReportEngine {

    public OperationExpenseAllDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, () -> BillDetailReportUnionConfigs.expense(jdbcTemplate));
    }
}
