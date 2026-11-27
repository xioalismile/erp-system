package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 生产领料单明细报表 Service（与销售/采购明细报表共用同一引擎 {@link BillDetailReportEngine}，
 * 差异只在 {@link InventoryBillDetailReportConfigs#materialRequisitionConfig()}）。
 */
@Service
public class MaterialRequisitionDetailReportService extends BillDetailReportEngine {

    public MaterialRequisitionDetailReportService(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver) {
        super(jdbcTemplate, dataScopeResolver, InventoryBillDetailReportConfigs.materialRequisitionConfig());
    }
}
