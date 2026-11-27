package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 售后维修单：承接客户报修、收件、维修、配件领用、返还与收费。
 * 库存动作全部通过生成「其他入库单/其他出库单」落地，不自建库存逻辑。
 */
@Data
@TableName("sale_repair")
public class SaleRepair {

    @TableId(value = "repair_id", type = IdType.AUTO)
    private Long repairId;

    @TableField("repair_no")
    private String repairNo;

    @TableField("repair_date")
    private LocalDate repairDate;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;

    @TableField("contact_person")
    private String contactPerson;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_id")
    private Long sourceId;

    /** 寄修 / 上门 / 返厂 */
    @TableField("service_type")
    private String serviceType;

    /** 保内 / 保外 / 待判定 */
    @TableField("warranty_status")
    private String warrantyStatus;

    @TableField("warranty_basis")
    private String warrantyBasis;

    @TableField("warranty_months")
    private Integer warrantyMonths;

    /** 维修仓（客户件收件/返还） */
    @TableField("warehouse_id")
    private Long warehouseId;

    /** 备件仓（配件领用） */
    @TableField("parts_warehouse_id")
    private Long partsWarehouseId;

    @TableField("fault_description")
    private String faultDescription;

    @TableField("receive_date")
    private LocalDate receiveDate;

    @TableField("return_date")
    private LocalDate returnDate;

    @TableField("received_quantity")
    private BigDecimal receivedQuantity;

    @TableField("returned_quantity")
    private BigDecimal returnedQuantity;

    @TableField("repair_fee")
    private BigDecimal repairFee;

    @TableField("parts_fee")
    private BigDecimal partsFee;

    @TableField("total_fee")
    private BigDecimal totalFee;

    /** 已修复 / 无法修复 / 客户放弃 */
    @TableField("repair_result")
    private String repairResult;

    @TableField("transferred_bill_type")
    private String transferredBillType;

    @TableField("transferred_bill_no")
    private String transferredBillNo;

    /** 转出目标单据主键（销售换货单 / 销售退货单），用于界面点击跳转 */
    @TableField("transferred_bill_id")
    private Long transferredBillId;

    @TableField("received_inbound_id")
    private Long receivedInboundId;

    @TableField("received_inbound_no")
    private String receivedInboundNo;

    @TableField("returned_issue_id")
    private Long returnedIssueId;

    @TableField("returned_issue_no")
    private String returnedIssueNo;

    @TableField("receipt_id")
    private Long receiptId;

    @TableField("receipt_no")
    private String receiptNo;

    @TableField("total_quantity")
    private BigDecimal totalQuantity;

    @TableField("remark")
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField("org_code")
    private String orgCode;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(exist = false)
    private List<SaleRepairItem> items;

    @TableField(exist = false)
    private List<SaleRepairPart> parts;

    /** 表头自定义字段值（非数据库字段），保存时写入自定义字段值表，详情时回填 */
    @TableField(exist = false)
    private Map<String, Object> customFieldValues;
}
