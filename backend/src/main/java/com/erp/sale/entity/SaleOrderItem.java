package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("sale_order_item")
public class SaleOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer lineNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;

    /** 发货仓库 */
    private String warehouse;

    private BigDecimal quantity;

    /** 已发货数量 */
    private BigDecimal shippedQuantity;

    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal discountedPrice;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate deliveryDate;

    /** 是否赠品: 0否 1是 */
    private Integer isGift;

    private String remark;

    @TableLogic
    private Integer deleted;
}
