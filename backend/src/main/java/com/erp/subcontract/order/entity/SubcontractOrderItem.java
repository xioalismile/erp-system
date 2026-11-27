package com.erp.subcontract.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("subcontract_order_item")
public class SubcontractOrderItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

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

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("finished_qty")
    private BigDecimal finishedQty;

    @TableField("actual_qty")
    private BigDecimal actualQty;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 含税价（按表头「计税方式」由单价换算落库，口径对齐销售订单试点） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算落库，口径对齐销售订单试点） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("bom_id")
    private Long bomId;

    @TableField("source_sale_order_id")
    private Long sourceSaleOrderId;

    @TableField("source_sale_order_item_id")
    private Long sourceSaleOrderItemId;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("remark")
    private String remark;

    @TableField("detail_close_status")
    private Integer detailCloseStatus;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 单位ID（与已有 unit 文本并存，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("unit_id")
    private Long unitId;

    /** 供料方式（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("supply_type")
    private String supplyType;
}
