package com.erp.produce.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionProgressDTO {

    private Long orderId;

    private String orderNo;

    private String productName;

    private BigDecimal planQty;

    private BigDecimal finishedQty;

    private BigDecimal progressRate;

    private String status;
}