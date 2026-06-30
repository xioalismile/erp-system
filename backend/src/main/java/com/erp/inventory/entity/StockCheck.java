package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存盘点单(对应 stock_check 表)
 */
@Data
@TableName("stock_check")
public class StockCheck {

    @TableId(value = "check_id", type = IdType.AUTO)
    private Long checkId;

    @TableField("check_no")
    private String checkNo;

    @TableField("check_date")
    private LocalDate checkDate;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("stock_type")
    private String stockType;

    @TableField("handler_id")
    private Long handlerId;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("book_amount")
    private BigDecimal bookAmount;

    @TableField("check_amount")
    private BigDecimal checkAmount;

    @TableField("profit_loss_amount")
    private BigDecimal profitLossAmount;

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

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;

    /** 盘点明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<StockCheckItem> items;
}
