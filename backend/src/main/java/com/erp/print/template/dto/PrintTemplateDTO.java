package com.erp.print.template.dto;

import com.erp.print.template.entity.PrintTemplate;
import com.erp.print.template.entity.PrintTemplateField;
import lombok.Data;

import java.util.List;

@Data
public class PrintTemplateDTO extends PrintTemplate {

    private List<PrintTemplateField> fields;
}
