package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存调拨单(对应 stock_transfer 表)
 */
@Data
@TableName("stock_transfer")
public class StockTransfer {

    @TableId(value = "transfer_id", type = IdType.AUTO)
    @ExportField(label = "调拨单ID", order = 1, hidden = true)
    private Long transferId;

    @TableField("transfer_no")
    @ExportField(label = "调拨单号", order = 2, required = true)
    private String transferNo;

    @TableField("transfer_date")
    @ExportField(label = "调拨日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate transferDate;

    @TableField("from_stock_id")
    @ExportField(label = "调出库存ID", order = 4, hidden = true)
    private Long fromStockId;

    @TableField("to_stock_id")
    @ExportField(label = "调入库存ID", order = 5, hidden = true)
    private Long toStockId;

    @TableField("handler_id")
    @ExportField(label = "经办人ID", order = 6)
    private Long handlerId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 7)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 8, numberFormat = "0.00")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "调拨总额", order = 9, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("transfer_type")
    @ExportField(label = "调拨类型", order = 10, required = true)
    private String transferType;

    @TableField("remark")
    @ExportField(label = "备注", order = 11)
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

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 结案状态：0=未结案 1=已结案（前端反结案按钮按此字段判断；本模块 /close 同时将 status 置为"已关闭"） */
    @TableField("close_status")
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private java.time.LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;

    /** 表头折扣金额（调拨单分摊折扣 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    /** 表头税额（调拨单税额 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 表头净额（扣除折扣与税额后的净额 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 表头「计税方式」（含税价 / 不含税价 / 不计税）：映射数据库列 tax_calc_method，随单据保存/回读；明细据此换算 price_with_tax / price_without_tax */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    /** 调拨明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<StockTransferItem> items;
}
