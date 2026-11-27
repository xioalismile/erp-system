package com.erp.inventory.batch.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("batch_info")
public class BatchInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_no")
    private String batchNo;

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

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("available_qty")
    private BigDecimal availableQty;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;

    /** 供应商批号（化妆品批记录：追溯树上游展示） */
    @TableField("supplier_batch_no")
    private String supplierBatchNo;

    /** 批次质量状态：待检/合格/不合格/已放行/已冻结（独立于库存语义的 status 列；历史批次回填合格） */
    @TableField("qc_status")
    private String qcStatus;

    /** 检验结论：合格/不合格/让步接收 */
    @TableField("qc_result")
    private String qcResult;

    /** 放行人（放行单生效时回写） */
    @TableField("released_by")
    private String releasedBy;

    /** 放行时间（放行单生效时回写） */
    @TableField("released_at")
    private LocalDateTime releasedAt;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
