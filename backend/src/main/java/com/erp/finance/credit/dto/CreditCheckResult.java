package com.erp.finance.credit.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditCheckResult {
    private boolean pass;
    private BigDecimal availableAmount;
    private BigDecimal usedAmount;
    private boolean warning;
}
