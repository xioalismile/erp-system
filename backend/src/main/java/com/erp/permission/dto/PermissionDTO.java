package com.erp.permission.dto;

import lombok.Data;

@Data
public class PermissionDTO {
    private String fieldKey;
    private Integer visible;
    private Integer editable;
    private Integer required;
}
