package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("purchase_inbound_item")
public class PurchaseInboundItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inboundId;
    private Integer lineNo;
    private Long purchaseOrderItemId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;

    @TableLogic
    private Integer deleted;
}
