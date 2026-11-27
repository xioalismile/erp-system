package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("warehouse")
public class Warehouse {

    @TableId(value = "stock_id", type = IdType.AUTO)
    @ExportField(label = "仓库ID", order = 0, hidden = true)
    private Long stockId;

    @TableField("stock_code")
    @ExportField(label = "仓库编码", order = 1, required = true)
    private String stockCode;

    @TableField("stock_name")
    @ExportField(label = "仓库名称", order = 2, required = true)
    private String stockName;

    @TableField("stock_type")
    @ExportField(label = "仓库类型", order = 3)
    private String stockType;

    @TableField("address")
    @ExportField(label = "地址", order = 4)
    private String address;

    @TableField("manager_id")
    @ExportField(label = "负责人ID", order = 5)
    private Long managerId;

    @TableField("is_bin_control")
    @ExportField(label = "是否仓位管理", order = 6)
    private Short isBinControl;

    @TableField("is_mrp_available")
    @ExportField(label = "是否参与MRP", order = 7)
    private Short isMrpAvailable;

    @TableField("status")
    @ExportField(label = "状态", order = 8)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 9)
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