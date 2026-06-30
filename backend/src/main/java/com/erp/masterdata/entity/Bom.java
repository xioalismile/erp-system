package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("bom")
public class Bom {

    @TableId(value = "bom_id", type = IdType.AUTO)
    private Long bomId;

    @TableField("bom_code")
    private String bomCode;

    @TableField("bom_name")
    private String bomName;

    @TableField("bom_version")
    private String bomVersion;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("unit_id")
    private Long unitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("bom_type")
    private String bomType;

    @TableField("is_primary")
    private Short isPrimary;

    @TableField("is_enabled")
    private Short isEnabled;

    @TableField("effective_date")
    private LocalDate effectiveDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("substitute_bom_code")
    private String substituteBomCode;

    @TableField("scrap_rate")
    private BigDecimal scrapRate;

    @TableField("remark")
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** BOM 明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<BomItem> items;
}
