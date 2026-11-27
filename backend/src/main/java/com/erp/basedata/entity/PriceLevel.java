package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("price_level")
public class PriceLevel {

    @TableId(value = "price_level_id", type = IdType.AUTO)
    @ExportField(label = "价格等级ID", order = 0, hidden = true)
    private Long priceLevelId;

    @TableField("price_level_code")
    @ExportField(label = "价格等级编码", order = 1, required = true)
    private String priceLevelCode;

    @TableField("price_level_name")
    @ExportField(label = "价格等级名称", order = 2, required = true)
    private String priceLevelName;

    @TableField("price_adjust_rate")
    @ExportField(label = "价格调整率", order = 3)
    private java.math.BigDecimal priceAdjustRate;

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