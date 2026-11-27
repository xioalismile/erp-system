package com.erp.dashboard.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作台审批结果DTO
 */
@Data
@NoArgsConstructor
public class ApproveResultDTO {
    private boolean success;
    private Long id;
    private String type;
    private String errorMessage;

    public ApproveResultDTO(boolean success, Long id, String type, String errorMessage) {
        this.success = success;
        this.id = id;
        this.type = type;
        this.errorMessage = errorMessage;
    }
}
