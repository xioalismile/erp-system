package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("location")
public class Location {

    @TableId(value = "bin_id", type = IdType.AUTO)
    private Long binId;

    @TableField("bin_code")
    private String binCode;

    @TableField("bin_name")
    private String binName;

    @TableField("stock_id")
    private Long stockId;

    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
