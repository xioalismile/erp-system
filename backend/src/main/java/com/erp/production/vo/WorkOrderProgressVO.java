package com.erp.production.vo;

import com.erp.production.entity.OperationReport;
import com.erp.production.entity.WorkOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorkOrderProgressVO {

    private WorkOrder workOrder;

    private List<OperationProgressVO> operations;

    private List<OperationReport> recentReports;

    private BigDecimal totalProgress;

    private String currentOperation;

    private Integer currentOperationIndex;
}
