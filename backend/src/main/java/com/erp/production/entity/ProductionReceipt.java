package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产入库单 - 对齐 PG production_receipt 表
 */
@Data
@TableName("production_receipt")
public class ProductionReceipt {

    @TableId(value = "receipt_id", type = IdType.AUTO)
    @ExportField(label = "入库单ID", order = 0, hidden = true)
    private Long receiptId;

    @TableField("receipt_no")
    @ExportField(label = "入库单号", order = 1, required = true)
    private String receiptNo;

    @TableField("receipt_date")
    @ExportField(label = "入库日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate receiptDate;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 3, hidden = true)
    private Long warehouseId;

    @TableField("production_order_id")
    @ExportField(label = "生产工单ID", order = 4, hidden = true)
    private Long productionOrderId;

    @TableField("production_order_no")
    @ExportField(label = "生产工单号", order = 5)
    private String productionOrderNo;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 6, hidden = true)
    private Long departmentId;

    @TableField("handler_id")
    @ExportField(label = "经办人ID", order = 7, hidden = true)
    private Long handlerId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 8, hidden = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 9, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "总金额", order = 10, numberFormat = "0.00")
    private BigDecimal totalAmount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("total_quantity")
    @ExportField(label = "总数量", order = 11, numberFormat = "0.00")
    private BigDecimal totalQuantity;

    @TableField("qualified_quantity")
    @ExportField(label = "合格数量", order = 12, numberFormat = "0.00")
    private BigDecimal qualifiedQuantity;

    @TableField("rejected_quantity")
    @ExportField(label = "报废数量", order = 13, numberFormat = "0.00")
    private BigDecimal rejectedQuantity;

    /** 质检开关（2026-11-07）：开启后必须先质检，按合格/不合格处理结果入库 */
    @TableField("qc_enabled")
    private Integer qcEnabled;

    @TableField("qc_template_id")
    private Long qcTemplateId;

    @TableField("qc_status")
    private String qcStatus;

    @TableField("source_bill_type")
    @ExportField(label = "源单据类型", order = 14)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 15)
    private String sourceBillNo;

    /** 来源工单工序行ID（第11期：入库类型工序一键发起入库单时写入，用于按工序控制剩余可入库量） */
    @TableField("source_operation_id")
    @ExportField(label = "来源工序行ID", order = 15, hidden = true)
    private Long sourceOperationId;

    @TableField("work_order_no")
    @ExportField(label = "工单号", order = 16)
    private String workOrderNo;

    @TableField("remark")
    @ExportField(label = "备注", order = 17)
    private String remark;

    @TableField("status")
    @ExportField(label = "状态", order = 18)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 19)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 20)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 21, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

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

    /** 结案状态: 0=未结案 1=已结案 */
    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，反作废时恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

    /** 附件URL列表（多附件以逗号分隔）；DB 列早已存在，此前只是实体缺映射 → 附件上传不落库 */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 表头「计税方式」（含税价 / 不含税价 / 不计税）：映射数据库列 tax_calc_method，随单据保存/回读；明细据此换算 price_with_tax / price_without_tax */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    /** 明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<ProductionReceiptItem> items;
}
