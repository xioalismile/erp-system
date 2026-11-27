package com.erp.finance.wage.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 员工工时费率只读投影（计时工资取数来源：employee.hourly_rate）。
 * <p>独立于 basedata 的 Employee 实体，避免为读一列而改动共享实体、把全站员工查询绑定到新列；
 * 由 118_wage_settlement.sql 补列，列缺失时服务层降级为"无费率 → 计时工资按 0 计"。</p>
 */
@Data
public class EmployeeRateDTO {

    private Long employeeId;

    private String employeeName;

    private Long deptId;

    /** 工时费率（元/小时）；空 = 不计计时工资 */
    private BigDecimal hourlyRate;
}
