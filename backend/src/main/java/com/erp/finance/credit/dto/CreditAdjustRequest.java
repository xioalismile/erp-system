package com.erp.finance.credit.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditAdjustRequest {
    private BigDecimal changeAmount;
    private String reason;
}
