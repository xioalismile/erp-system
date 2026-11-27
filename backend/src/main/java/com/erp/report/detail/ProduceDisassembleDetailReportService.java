package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 拆卸单明细报表 Service（与其它明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link ProductionExtraBillDetailReportConfigs#disassembleConfig()}）。
 */
@Service
public class ProduceDisassembleDetailReportService extends BillDetailReportEngine {

    public ProduceDisassembleDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, ProductionExtraBillDetailReportConfigs.disassembleConfig());
    }
}
