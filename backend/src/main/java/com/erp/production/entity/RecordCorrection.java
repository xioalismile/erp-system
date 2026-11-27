package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记录更正 - 对齐 PG record_correction 表
 * 化妆品批记录：只增不改体系下的唯一纠错通道。
 * 口径：必填原因 + 电子签名；原记录保持不变，最新一条更正记录为"当前有效值"展示口径。
 */
@Data
@TableName("record_correction")
public class RecordCorrection {

    @TableId(value = "correction_id", type = IdType.AUTO)
    private Long correctionId;

    @TableField("target_table")
    private String targetTable;

    @TableField("target_id")
    private Long targetId;

    @TableField("target_field")
    private String targetField;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    @TableField("reason")
    private String reason;

    @TableField("corrected_by")
    private String correctedBy;

    @TableField("corrected_at")
    private LocalDateTime correctedAt;

    @TableField("signed")
    private Integer signed;
}
