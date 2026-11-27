package com.erp.freesample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 免费样品订单明细实体（对应 free_sample_order_item 表）
 * 行号 line_no 从1开始，步长1
 */
@Data
@TableName("free_sample_order_item")
public class FreeSampleOrderItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("order_id")
    private Long orderId;

    /** 行号：从1开始，步长1 */
    @TableField("line_no")
    private Integer lineNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("model")
    private String model;

    @TableField("color")
    private String color;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_id")
    private Long unitId;

    @TableField("unit_name")
    private String unitName;

    @TableField("bin_id")
    private Long binId;

    @TableField("bin_name")
    private String binName;

    @TableField("remark")
    private String remark;
}
