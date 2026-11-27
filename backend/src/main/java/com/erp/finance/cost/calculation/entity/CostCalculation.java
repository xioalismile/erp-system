package com.erp.finance.cost.calculation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("cost_calculation")
public class CostCalculation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("calc_no")
    private String calcNo;

    @TableField("calc_name")
    private String calcName;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("calc_date")
    private LocalDate calcDate;

    @TableField("calc_method")
    private String calcMethod;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("direct_material_cost")
    private BigDecimal directMaterialCost;

    @TableField("direct_labor_cost")
    private BigDecimal directLaborCost;

    @TableField("manufacturing_overhead")
    private BigDecimal manufacturingOverhead;

    /** 质检报废损失（2026-11-07）：不合格进报废仓的不合格品成本，计入总成本 */
    @TableField("scrap_loss")
    private BigDecimal scrapLoss;

    @TableField("total_cost")
    private BigDecimal totalCost;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("unit_cost")
    private BigDecimal unitCost;

    @TableField("status")
    private String status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;
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

    @TableField("remark")
    private String remark;

    /** 核算期间 yyyy-MM */
    @TableField("period")
    private String period;

    /** 核算版本号（反确认后递增，保留审计痕迹） */
    @TableField("version")
    private Integer version;

    /** 确认时间 */
    @TableField(value = "confirmed_at", updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime confirmedAt;

    /** 反确认时间 */
    @TableField(value = "reversed_at", updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime reversedAt;

    /** 确认人 */
    @TableField(value = "confirmed_by", updateStrategy = FieldStrategy.IGNORED)
    private String confirmedBy;

    /** 反确认人 */
    @TableField(value = "reversed_by", updateStrategy = FieldStrategy.IGNORED)
    private String reversedBy;

    /** 结案状态：0=未结案 1=已结案（结案后 status 保持已生效，前端按此字段渲染已结案） */
    @TableField("close_status")
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private java.time.LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 表头「计税方式」（含税价/不含税价/不计税）：映射数据库列 tax_calc_method，随单据保存/回读并驱动明细换算 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    @TableField(exist = false)
    private List<CostCalculationItem> items;
}