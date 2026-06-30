package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
@TableName("sale_order")
public class SaleOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 销售订单编号: SO+年月日+4位流水 */
    private String orderNo;

    private LocalDate orderDate;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String contactPerson;
    private String contactPhone;
    private String deliveryAddress;

    /** 发货方式: 快递/物流/自提/客户自取 */
    private String deliveryMethod;

    /** 物流单号 */
    private String logisticsNo;

    private String salesperson;
    private String department;
    private String currency;
    private BigDecimal exchangeRate;
    private String paymentMethod;
    private LocalDate expectedDeliveryDate;

    /** 实际发货日期 */
    private LocalDate actualShipDate;

    // ========== 金额字段 ==========
    /** 订单总额(明细金额合计) */
    private BigDecimal totalAmount;

    /** 运费 */
    private BigDecimal freight;

    /** 整单优惠金额 */
    private BigDecimal discountAmount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 价税合计 = totalAmount + freight - discountAmount + taxAmount */
    private BigDecimal totalWithTax;

    // ========== 状态字段 ==========
    /** 草稿/已确认/已发货/已开票/已完成/已取消/已退货 */
    private String status;

    /** 来源渠道: MANUAL/AI/WEB/DINGTALK/WECOM */
    private String sourceChannel;

    /** 开票状态: 未开票/部分开票/已开票 */
    private String invoiceStatus;

    /** 发票号 */
    private String invoiceNo;

    /** 收款状态: 未收款/部分收款/已收款 */
    private String paymentStatus;

    /** 已收金额 */
    private BigDecimal receivedAmount;

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

    /** 明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<SaleOrderItem> items;
}
