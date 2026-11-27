package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 留样登记 - 对齐 PG sample_retention 表
 * 化妆品批记录：成品放行时自动生成留样任务（留样量默认=入库数量×1%，保存期限走设置中心参数），
 * 到期进工作台提醒。
 */
@Data
@TableName("sample_retention")
public class SampleRetention {

    @TableId(value = "retention_id", type = IdType.AUTO)
    private Long retentionId;

    @TableField("retention_no")
    private String retentionNo;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("retain_qty")
    private BigDecimal retainQty;

    @TableField("store_location")
    private String storeLocation;

    @TableField("retain_by")
    private String retainBy;

    @TableField("retain_date")
    private LocalDate retainDate;

    @TableField("keep_until")
    private LocalDate keepUntil;

    @TableField("remind_status")
    private String remindStatus;

    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
