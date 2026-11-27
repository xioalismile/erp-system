package com.erp.finance.credit.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditCheckRequest {
    private Long customerId;
    private BigDecimal amount;
}
