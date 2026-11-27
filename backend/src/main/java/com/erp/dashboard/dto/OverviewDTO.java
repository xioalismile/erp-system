package com.erp.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OverviewDTO {
    private BigDecimal todaySaleAmount;
    private BigDecimal todaySaleCompare;
    private BigDecimal todayPurchaseAmount;
    private BigDecimal todayPurchaseCompare;
    private BigDecimal totalInventoryValue;
    private Integer pendingApprovalCount;
    private Integer saleOrderCount;
    private Integer purchaseOrderCount;
}
