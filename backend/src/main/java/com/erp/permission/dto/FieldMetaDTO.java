package com.erp.permission.dto;

import lombok.Data;

@Data
public class FieldMetaDTO {
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private Integer defaultVisible;
    private Integer defaultEditable;
    private Integer defaultRequired;
    private Integer sortOrder;
}
