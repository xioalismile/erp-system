package com.erp.inventory.serial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("serial_number")
public class SerialNumber {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("serial_no")
    private String serialNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit")
    private String unit;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("warehouse_name")
    private String warehouseName;

    @TableField("location_id")
    private Long locationId;

    @TableField("location_name")
    private String locationName;

    @TableField("batch_no")
    private String batchNo;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("status")
    private Integer status;

    @TableField("inbound_id")
    private Long inboundId;

    @TableField("inbound_no")
    private String inboundNo;

    @TableField("outbound_id")
    private Long outboundId;

    @TableField("outbound_no")
    private String outboundNo;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
