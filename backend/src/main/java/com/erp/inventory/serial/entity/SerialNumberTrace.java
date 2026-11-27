package com.erp.inventory.serial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("serial_number_trace")
public class SerialNumberTrace {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("serial_number_id")
    private Long serialNumberId;

    @TableField("serial_no")
    private String serialNo;

    @TableField("operation_type")
    private String operationType;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("warehouse_name")
    private String warehouseName;

    @TableField("location_id")
    private Long locationId;

    @TableField("location_name")
    private String locationName;

    @TableField("related_doc_id")
    private Long relatedDocId;

    @TableField("related_doc_no")
    private String relatedDocNo;

    @TableField("related_doc_type")
    private String relatedDocType;

    @TableField("operation_time")
    private LocalDateTime operationTime;

    @TableField("operator")
    private String operator;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
