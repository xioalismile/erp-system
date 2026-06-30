package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 采购入库单
 */
@Data
@TableName("purchase_inbound")
public class PurchaseInbound {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号: PIN+年月日+4位流水 */
    private String inboundNo;

    private LocalDate inboundDate;

    private Long purchaseOrderId;
    private String purchaseOrderNo;

    private Long supplierId;
    private String supplierName;

    private String warehouse;
    private String operator;

    /** 草稿/已确认 */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<PurchaseInboundItem> items;
}
