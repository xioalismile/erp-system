package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批生产记录头 - 对齐 PG batch_production_record 表
 * 化妆品批记录：以生产工单为单位，工单生效时预生成并固化配方/工艺路线快照（JSONB）；
 * 投料/工序/检验明细由聚合服务实时从现有单据推导，不在本表冗余。
 * 环境记录（温湿度）为可选填写区块，留空不拦。
 */
@Data
@TableName("batch_production_record")
public class BatchProductionRecord {

    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;

    @TableField("work_order_id")
    private Long workOrderId;

    @TableField("work_order_no")
    private String workOrderNo;

    @TableField("product_id")
    private Long productId;

    @TableField("product_code")
    private String productCode;

    @TableField("product_name")
    private String productName;

    @TableField("batch_no")
    private String batchNo;

    @TableField("bom_snapshot")
    private String bomSnapshot;

    @TableField("routing_snapshot")
    private String routingSnapshot;

    @TableField("env_temperature")
    private BigDecimal envTemperature;

    @TableField("env_humidity")
    private BigDecimal envHumidity;

    @TableField("env_recorder")
    private String envRecorder;

    @TableField("env_record_time")
    private LocalDateTime envRecordTime;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
