package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sale_order_item")
public class SaleOrderItem {

    @TableId(value = "order_item_id", type = IdType.AUTO)
    private Long orderItemId;

    @TableField("order_id")
    private Long orderId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit_id")
    private Long unitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("delivered_quantity")
    private BigDecimal deliveredQuantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("discount_rate")
    private BigDecimal discountRate;

    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("delivery_date")
    private LocalDate deliveryDate;

    @TableField("delivery_days")
    private Integer deliveryDays;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

    @TableField("delivered_line_no")
    private Integer deliveredLineNo;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
