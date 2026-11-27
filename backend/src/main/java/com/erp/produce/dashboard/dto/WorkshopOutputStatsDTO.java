package com.erp.produce.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkshopOutputStatsDTO {

    private Long workshopId;

    private String workshopName;

    private BigDecimal outputQty;

    private BigDecimal outputValue;

    private BigDecimal efficiency;
}