package com.erp.produce.mrp.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("mrp_plan")
public class MrpPlan {

    @TableId(value = "id", type = IdType.AUTO)
    @ExportField(label = "计划ID", order = 0, hidden = true)
    private Long id;

    @TableField("plan_no")
    @ExportField(label = "计划编号", order = 1, required = true)
    private String planNo;

    @TableField("plan_name")
    @ExportField(label = "计划名称", order = 2, required = true)
    private String planName;

    @TableField("plan_date")
    @ExportField(label = "计划日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate planDate;

    @TableField("demand_source")
    @ExportField(label = "需求来源", order = 4)
    private String demandSource;

    @TableField("demand_order_ids")
    @ExportField(label = "需求订单ID", order = 5, hidden = true)
    private String demandOrderIds;

    @TableField("plan_status")
    @ExportField(label = "计划状态", order = 6)
    private Integer planStatus;

    @TableField("total_net_demand")
    @ExportField(label = "净需求总量", order = 7, numberFormat = "0.00")
    private BigDecimal totalNetDemand;

    @TableField("total_purchase_suggestion")
    @ExportField(label = "采购建议总量", order = 8, numberFormat = "0.00")
    private BigDecimal totalPurchaseSuggestion;

    @TableField("total_produce_suggestion")
    @ExportField(label = "生产建议总量", order = 9, numberFormat = "0.00")
    private BigDecimal totalProduceSuggestion;

    @TableField("remark")
    @ExportField(label = "备注", order = 10)
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 结案状态: 0=未结案 1=已结案 */
    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态(反作废恢复用) */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 计划展望期天数（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("plan_horizon")
    private Integer planHorizon;

    /** 需求时界天数（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("time_fence_days")
    private Integer timeFenceDays;

    /** 是否包含销售订单（0=否 1=是，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("include_so")
    private Integer includeSo;

    /** 是否包含预测（0=否 1=是，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("include_forecast")
    private Integer includeForecast;

    /** 是否包含安全库存（0=否 1=是，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("include_safety_stock")
    private Integer includeSafetyStock;

    /** 是否包含未清采购订单（0=否 1=是，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("include_open_po")
    private Integer includeOpenPo;

    /** 是否包含未完工生产工单（0=否 1=是，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("include_open_mo")
    private Integer includeOpenMo;

    /** 是否包含现有库存（0=否 1=是，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("include_onhand_stock")
    private Integer includeOnhandStock;

    /** 批量策略类型（如 LOT_FOR_LOT / FIXED_QTY，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("lot_size_type")
    private String lotSizeType;

    /** 批量大小（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("lot_size")
    private BigDecimal lotSize;

    /** 最小订货量（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("min_order_qty")
    private BigDecimal minOrderQty;

    /** 状态中文文案(非数据库字段, 供前端渲染: 草稿/待审核/已生效/已执行/已作废) */
    @TableField(exist = false)
    private String status;

    @TableField(exist = false)
    private List<MrpPlanItem> items;
}
