package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("payment_term")
public class PaymentTerm {

    @TableId(value = "payment_term_id", type = IdType.AUTO)
    @ExportField(label = "付款条件ID", order = 0, hidden = true)
    private Long paymentTermId;

    @TableField("payment_term_code")
    @ExportField(label = "付款条件编码", order = 1, required = true)
    private String paymentTermCode;

    @TableField("payment_term_name")
    @ExportField(label = "付款条件名称", order = 2, required = true)
    private String paymentTermName;

    @TableField("payment_days")
    @ExportField(label = "付款天数", order = 3)
    private Integer paymentDays;

    @TableField("discount_days")
    @ExportField(label = "折扣天数", order = 4)
    private Integer discountDays;

    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 5)
    private java.math.BigDecimal discountRate;

    @TableField("advance_rate")
    @ExportField(label = "预付款比例", order = 6)
    private java.math.BigDecimal advanceRate;

    @TableField("status")
    @ExportField(label = "状态", order = 7)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 8)
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