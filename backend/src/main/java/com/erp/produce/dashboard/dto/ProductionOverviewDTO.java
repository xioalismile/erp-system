package com.erp.produce.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionOverviewDTO {

    private Integer todayOutputQty;

    private BigDecimal todayOutputValue;

    private Integer inProgressOrderCount;

    private Integer pendingOrderCount;

    private BigDecimal totalEfficiency;

    private BigDecimal onTimeDeliveryRate;

    private Integer workshopCount;

    private Integer employeeCount;
}