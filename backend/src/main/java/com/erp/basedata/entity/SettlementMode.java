package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算方式 - 对齐 PG settlement_mode 表
 */
@Data
@TableName("settlement_mode")
public class SettlementMode {

    @TableId(value = "settle_mode_id", type = IdType.AUTO)
    private Long settleModeId;

    @TableField("settle_mode_code")
    private String settleModeCode;

    @TableField("settle_mode_name")
    private String settleModeName;

    @TableField("settle_type")
    private String settleType;

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
