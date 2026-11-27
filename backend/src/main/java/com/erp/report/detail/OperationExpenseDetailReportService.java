package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 费用分摊单明细报表 Service（与销售/采购明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link FinanceBillDetailReportConfigs#operationExpenseConfig()}；
 * 明细口径 = 分摊明细 operation_expense_allocation，与编辑页「分摊明细」页签逐列对齐）。
 */
@Service
public class OperationExpenseDetailReportService extends BillDetailReportEngine {

    public OperationExpenseDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, FinanceBillDetailReportConfigs.operationExpenseConfig());
    }
}
