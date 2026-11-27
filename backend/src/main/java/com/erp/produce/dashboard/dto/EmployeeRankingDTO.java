package com.erp.produce.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EmployeeRankingDTO {

    private Long employeeId;

    private String employeeName;

    private BigDecimal outputQty;

    private BigDecimal workHours;

    private BigDecimal efficiency;

    private Integer orderCount;
}