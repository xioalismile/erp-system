package com.erp.freesample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 免费样品订单实体（对应 free_sample_order 表）
 * 业务场景：业务员录入免费样品订单（金额为0），仓库接单后直接发货，关联客户跟进记录
 * 单据状态流转: 草稿 → 待审核 → 已生效 → 已发货 → 已完成
 */
@Data
@TableName("free_sample_order")
public class FreeSampleOrder {

    @TableId(value = "order_id", type = IdType.AUTO)
    private Long orderId;

    /** 单号 FS-yyyyMMdd-0001，连字符分隔 */
    @TableField("order_no")
    private String orderNo;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;

    /** 关联客户跟进记录ID */
    @TableField("follow_up_id")
    private Long followUpId;

    @TableField("sales_id")
    private Long salesId;

    @TableField("sales_name")
    private String salesName;

    @TableField("order_date")
    private LocalDate orderDate;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("warehouse_name")
    private String warehouseName;

    /** 发货是否已扣库存：1=已扣（作废/删除时按冻结成本冲回）；0=未扣 */
    @TableField("stock_deducted")
    private Integer stockDeducted;

    @TableField("express_no")
    private String expressNo;

    @TableField("express_company")
    private String expressCompany;

    @TableField("total_quantity")
    private BigDecimal totalQuantity;

    /** 订单金额：免费样品强制为 0.00 */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 状态: 草稿/待审核/已生效/已发货/已完成 */
    @TableField("status")
    private String status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    /** 审核人（免审批自动生效时留空） */
    @TableField("approved_by")
    private String approvedBy;

    /** 审核时间 */
    @TableField("approved_at")
    private LocalDateTime approvedAt;

    /** 作废前状态（数字code，供反作废恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField("delivery_date")
    private LocalDate deliveryDate;

    /** 结案状态：0=未结案 1=已结案（结案后 status 保持已生效，前端按此字段渲染已结案） */
    @TableField("close_status")
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private java.time.LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;

    @TableField("remark")
    private String remark;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：打通免费样品订单页的附件上传） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 订单明细列表（非数据库字段） */
    @TableField(exist = false)
    private List<FreeSampleOrderItem> items;
}
