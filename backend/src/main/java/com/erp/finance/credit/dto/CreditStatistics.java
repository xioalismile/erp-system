package com.erp.finance.credit.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditStatistics {
    private BigDecimal totalCreditLimit;
    private BigDecimal totalUsedAmount;
    private BigDecimal totalAvailableAmount;
    private Integer warningCustomerCount;
    private Integer frozenCustomerCount;
    private Integer totalCustomerCount;
}
