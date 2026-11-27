package com.erp.finance.wage.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工序工资核算汇总行（按员工聚合，页面主表格数据源）。
 */
@Data
public class WageSummaryDTO {

    private String period;

    private Long deptId;

    private String deptName;

    private Long employeeId;

    private String employeeName;

    /** 合格数量 = Σ qualified_quantity */
    private BigDecimal qualifiedQuantity;

    /** 计件工资 = Σ(合格数量 × 计件单价) */
    private BigDecimal pieceAmount;

    /** 计时工资 = Σ((工时+加班工时) × 工时费率) */
    private BigDecimal timeAmount;

    /** 合计 = 计件工资 + 计时工资 */
    private BigDecimal totalAmount;
}
