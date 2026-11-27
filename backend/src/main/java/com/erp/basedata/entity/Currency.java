package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("currency")
public class Currency {

    @TableId(value = "currency_id", type = IdType.AUTO)
    @ExportField(label = "币种ID", order = 0, hidden = true)
    private Long currencyId;

    @TableField("currency_code")
    @ExportField(label = "币种编码", order = 1, required = true)
    private String currencyCode;

    @TableField("currency_name")
    @ExportField(label = "币种名称", order = 2, required = true)
    private String currencyName;

    @TableField("currency_symbol")
    @ExportField(label = "币种符号", order = 3)
    private String currencySymbol;

    @TableField("is_base_currency")
    @ExportField(label = "是否本位币", order = 4)
    private Short isBaseCurrency;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 5)
    private java.math.BigDecimal exchangeRate;

    @TableField("status")
    @ExportField(label = "状态", order = 6)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 7)
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 102, hidden = true)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新人", order = 103, hidden = true)
    private String updatedBy;
}