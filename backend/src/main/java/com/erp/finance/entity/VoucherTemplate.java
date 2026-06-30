package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭证模板 - 对齐 PG voucher_template 表
 */
@Data
@TableName("voucher_template")
public class VoucherTemplate {

    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    @TableField("template_code")
    private String templateCode;

    @TableField("template_name")
    private String templateName;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("voucher_type")
    private String voucherType;

    @TableField("is_auto_create")
    private Short isAutoCreate;

    @TableField("template_content")
    private String templateContent;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
