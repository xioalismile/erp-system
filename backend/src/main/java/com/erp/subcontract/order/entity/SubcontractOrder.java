package com.erp.subcontract.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("subcontract_order")
public class SubcontractOrder {

    @TableId(value = "id", type = IdType.AUTO)
    @ExportField(label = "委外单ID", order = 0, hidden = true)
    private Long id;

    @TableField("order_no")
    @ExportField(label = "委外单号", order = 1, required = true)
    private String orderNo;

    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 2, hidden = true)
    private Long supplierId;

    @TableField("supplier_name")
    @ExportField(label = "供应商", order = 3)
    private String supplierName;

    @TableField("process_item")
    @ExportField(label = "委外工序号", order = 4)
    private String processItem;

    @TableField("order_date")
    @ExportField(label = "委外日期", order = 5, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate orderDate;

    @TableField("expect_date")
    @ExportField(label = "计划开工日期", order = 6, dateFormat = "yyyy-MM-dd")
    private LocalDate expectDate;

    @TableField("actual_date")
    @ExportField(label = "计划完工日期", order = 7, dateFormat = "yyyy-MM-dd")
    private LocalDate actualDate;

    @TableField("total_quantity")
    @ExportField(label = "委外数量", order = 8)
    private BigDecimal totalQuantity;

    @TableField("finished_quantity")
    @ExportField(label = "已完工数量", order = 9)
    private BigDecimal finishedQuantity;

    @TableField("unit_price")
    @ExportField(label = "加工单价", order = 10, numberFormat = "0.0000")
    private BigDecimal unitPrice;

    @TableField("total_amount")
    @ExportField(label = "加工费总额", order = 11, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("tax_rate")
    @ExportField(label = "税率(%)", order = 12)
    private BigDecimal taxRate;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 13, numberFormat = "0.00")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    @ExportField(label = "净额", order = 14, numberFormat = "0.00")
    private BigDecimal netAmount;

    @TableField("currency_code")
    @ExportField(label = "币种", order = 15)
    private String currencyCode;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 16, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField("base_total_amount")
    @ExportField(label = "本位币加工费", order = 17, numberFormat = "0.00")
    private BigDecimal baseTotalAmount;

    @TableField("base_tax_amount")
    @ExportField(label = "本位币税额", order = 18, numberFormat = "0.00")
    private BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    @ExportField(label = "本位币净额", order = 19, numberFormat = "0.00")
    private BigDecimal baseNetAmount;

    @TableField("source_bill_type")
    @ExportField(label = "源单据类型", order = 20)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 21)
    private String sourceBillNo;

    @TableField("status")
    @ExportField(label = "状态", order = 22)
    private Integer status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    @TableField("close_status")
    @ExportField(label = "关闭状态", order = 23)
    private Integer closeStatus;

    @TableField("remark")
    @ExportField(label = "备注", order = 24)
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 100, hidden = true)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 101, hidden = true)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 102, hidden = true)
    private LocalDateTime updateTime;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 委外物料ID（表头物料，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("material_id")
    private Long materialId;

    /** 委外物料编码（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("material_code")
    private String materialCode;

    /** 委外物料名称（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("material_name")
    private String materialName;

    /** 单位ID（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("unit_id")
    private Long unitId;

    /** 单位名称（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("unit_name")
    private String unitName;

    /** 表头「计税方式」（含税价/不含税价/不计税）：映射数据库列 tax_calc_method，随单据保存/回读并驱动明细换算 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField(exist = false)
    private List<SubcontractOrderItem> items;
}
