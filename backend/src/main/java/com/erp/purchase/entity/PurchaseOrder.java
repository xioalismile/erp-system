package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
@TableName("purchase_order")
public class PurchaseOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采购订单编号: PO+年月日+4位流水 */
    private String orderNo;

    private LocalDate orderDate;

    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private String contactPerson;
    private String contactPhone;
    private String deliveryAddress;

    private String buyer;
    private String department;
    private String currency;
    private BigDecimal exchangeRate;
    private String paymentMethod;
    private LocalDate expectedDeliveryDate;

    /** 来源类型: MANUAL手动 / SALE_ORDER销售转采购 / MRP MRP生成 / AI AI创建 */
    private String sourceType;

    /** 来源销售订单号(转单时记录) */
    private String sourceOrderNo;

    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalWithTax;

    /** 草稿/已确认/已入库/已开票/已完成/已取消 */
    private String status;

    /** 入库状态: 未入库/部分入库/已入库 */
    private String inboundStatus;

    private String auditor;
    private LocalDate auditDate;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<PurchaseOrderItem> items;
}
