package com.erp.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TrendDTO {
    private List<String> dates;
    private List<BigDecimal> amounts;
}
