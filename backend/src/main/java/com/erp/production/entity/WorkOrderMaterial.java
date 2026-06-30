package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 工单用料明细 - 生产所需原材料
 */
@Data
@TableName("work_order_material")
public class WorkOrderMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;

    /** 需求数量 = 计划生产量 × BOM用量 */
    private BigDecimal requiredQuantity;

    /** 已领料数量 */
    private BigDecimal issuedQuantity;

    /** 仓库 */
    private String warehouse;

    @TableLogic
    private Integer deleted;
}
