package com.erp.produce.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OutputTrendDTO {

    private List<String> hours;

    private List<BigDecimal> quantities;

    private List<BigDecimal> values;
}