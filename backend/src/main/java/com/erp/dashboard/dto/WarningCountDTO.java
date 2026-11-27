package com.erp.dashboard.dto;

import lombok.Data;

@Data
public class WarningCountDTO {
    private Integer belowMinCount;
    private Integer aboveMaxCount;
    private Integer expiredCount;
    private Integer totalCount;
}
