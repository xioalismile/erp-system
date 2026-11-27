package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("settlement_mode")
public class SettlementMode {

    @TableId(value = "settle_mode_id", type = IdType.AUTO)
    @ExportField(label = "结算方式ID", order = 0, hidden = true)
    private Long settleModeId;

    @TableField("settle_mode_code")
    @ExportField(label = "结算方式编码", order = 1, required = true)
    private String settleModeCode;

    @TableField("settle_mode_name")
    @ExportField(label = "结算方式名称", order = 2, required = true)
    private String settleModeName;

    @TableField("settle_type")
    @ExportField(label = "结算类型", order = 3)
    private String settleType;

    @TableField("sort_order")
    @ExportField(label = "排序号", order = 4)
    private Integer sortOrder;

    @TableField("status")
    @ExportField(label = "状态", order = 5)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 6)
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