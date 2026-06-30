package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("purchase_order_item")
public class PurchaseOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer lineNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;

    /** 入库仓库 */
    private String warehouse;

    private BigDecimal quantity;

    /** 已入库数量 */
    private BigDecimal receivedQuantity;

    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal discountedPrice;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate deliveryDate;

    /** 建议下单日期(MRP建议) */
    private LocalDate suggestedOrderDate;

    /** 来源销售订单明细ID(转单时记录) */
    private Long sourceOrderItemId;

    private String remark;

    @TableLogic
    private Integer deleted;
}
