package com.erp.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EmployeeRankingDTO {
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private BigDecimal amount;
    private Integer orderCount;
}
