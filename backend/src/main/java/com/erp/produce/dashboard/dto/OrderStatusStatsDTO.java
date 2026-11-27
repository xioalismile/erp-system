package com.erp.produce.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderStatusStatsDTO {

    private String statusName;

    private Integer count;

    private BigDecimal percentage;

    public OrderStatusStatsDTO(String statusName, Integer count, BigDecimal percentage) {
        this.statusName = statusName;
        this.count = count;
        this.percentage = percentage;
    }
}