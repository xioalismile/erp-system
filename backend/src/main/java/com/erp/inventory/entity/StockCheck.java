package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
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
    @ExportField(label = "盘点单ID", order = 1, hidden = true)
    private Long checkId;

    @TableField("check_no")
    @ExportField(label = "盘点单号", order = 2, required = true)
    private String checkNo;

    @TableField("check_date")
    @ExportField(label = "盘点日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate checkDate;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 4, required = true)
    private Long warehouseId;

    @TableField("stock_type")
    @ExportField(label = "库存类型", order = 5, required = true)
    private String stockType;

    @TableField("handler_id")
    @ExportField(label = "盘点人ID", order = 6)
    private Long handlerId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 7)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 8, numberFormat = "0.00")
    private BigDecimal exchangeRate;

    @TableField("book_amount")
    @ExportField(label = "账面金额", order = 9, numberFormat = "0.00")
    private BigDecimal bookAmount;

    @TableField("check_amount")
    @ExportField(label = "盘点金额", order = 10, numberFormat = "0.00")
    private BigDecimal checkAmount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("profit_loss_amount")
    @ExportField(label = "盘盈盘亏金额", order = 11, numberFormat = "0.00")
    private BigDecimal profitLossAmount;

    @TableField("remark")
    @ExportField(label = "备注", order = 12)
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

    // ===== 结案口径（表列已存在，与其它入库/其它出库/调拨单同款；补字段以支持「反结案」按钮口径） =====
    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

    /** 表头「计税方式」（含税价 / 不含税价 / 不计税）：映射数据库列 tax_calc_method，随单据保存/回读；明细据此换算 price_with_tax / price_without_tax */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    /** 盘点明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<StockCheckItem> items;
}
