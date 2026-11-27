package com.erp.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopMaterialDTO {
    private Long materialId;
    private String materialCode;
    private String materialName;
    private BigDecimal quantity;
    private BigDecimal amount;
}
