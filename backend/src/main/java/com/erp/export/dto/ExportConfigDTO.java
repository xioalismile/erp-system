package com.erp.export.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExportConfigDTO {
    private String entityName;
    private String templateName;
    private List<FieldConfig> fields;

    @Data
    public static class FieldConfig {
        private String fieldName;
        private String label;
        private int order;
        private boolean required;
        private String dateFormat;
        private String numberFormat;
        private boolean hidden;
    }
}