package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产工单 - 对齐 PG production_order 表
 */
@Data
@TableName("production_order")
public class WorkOrder {

    @TableId(value = "order_id", type = IdType.AUTO)
    @ExportField(label = "工单ID", order = 0, hidden = true)
    private Long orderId;

    @TableField("order_no")
    @ExportField(label = "工单号", order = 1, required = true)
    private String orderNo;

    @TableField("order_date")
    @ExportField(label = "工单日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate orderDate;

    @TableField("bom_id")
    @ExportField(label = "BOM ID", order = 3, hidden = true)
    private Long bomId;

    @TableField("material_id")
    @ExportField(label = "物料ID", order = 4, hidden = true)
    private Long materialId;

    @TableField("material_code")
    @ExportField(label = "物料编码", order = 5, required = true)
    private String materialCode;

    @TableField("material_name")
    @ExportField(label = "物料名称", order = 6)
    private String materialName;

    @TableField("model")
    @ExportField(label = "规格型号", order = 7)
    private String model;

    @TableField("unit_id")
    @ExportField(label = "单位ID", order = 8, hidden = true)
    private Long unitId;

    @TableField("quantity")
    @ExportField(label = "生产数量", order = 9, numberFormat = "0.00", required = true)
    private BigDecimal quantity;

    @TableField("completed_quantity")
    @ExportField(label = "已完成数量", order = 10, numberFormat = "0.00")
    private BigDecimal completedQuantity;

    /** 已报工合格量(报工审核累计, 与入库回写的completed_quantity分离, 避免重复计算) */
    @TableField("reported_quantity")
    @ExportField(label = "已报工合格量", order = 11, numberFormat = "0.00")
    private BigDecimal reportedQuantity;

    @TableField("rejected_quantity")
    @ExportField(label = "报废数量", order = 12, numberFormat = "0.00")
    private BigDecimal rejectedQuantity;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 12, hidden = true)
    private Long warehouseId;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 13, hidden = true)
    private Long departmentId;

    @TableField("manager_id")
    @ExportField(label = "负责人ID", order = 14, hidden = true)
    private Long managerId;

    @TableField("routing_id")
    @ExportField(label = "工艺路线ID", order = 15, hidden = true)
    private Long routingId;

    @TableField("bom_version")
    @ExportField(label = "BOM版本", order = 16)
    private String bomVersion;

    @TableField("routing_version")
    @ExportField(label = "工艺路线版本", order = 17)
    private String routingVersion;

    @TableField("work_center_id")
    @ExportField(label = "工作中心ID", order = 18, hidden = true)
    private Long workCenterId;

    @TableField("priority")
    @ExportField(label = "优先级", order = 19)
    private String priority;

    @TableField("plan_start_date")
    @ExportField(label = "计划开始日期", order = 20, dateFormat = "yyyy-MM-dd")
    private LocalDate planStartDate;

    @TableField("plan_end_date")
    @ExportField(label = "计划结束日期", order = 21, dateFormat = "yyyy-MM-dd")
    private LocalDate planEndDate;

    @TableField("actual_start_date")
    @ExportField(label = "实际开始日期", order = 22, dateFormat = "yyyy-MM-dd")
    private LocalDate actualStartDate;

    @TableField("actual_end_date")
    @ExportField(label = "实际结束日期", order = 23, dateFormat = "yyyy-MM-dd")
    private LocalDate actualEndDate;

    @TableField("lead_time_days")
    @ExportField(label = "提前期天数", order = 24)
    private Integer leadTimeDays;

    @TableField("is_urgent")
    @ExportField(label = "是否紧急", order = 25)
    private Short isUrgent;

    @TableField("is_closed")
    @ExportField(label = "是否关闭", order = 26)
    private Short isClosed;

    @TableField("source_type")
    @ExportField(label = "来源类型", order = 27)
    private String sourceType;

    @TableField("source_no")
    @ExportField(label = "来源单号", order = 28)
    private String sourceNo;

    @TableField("sales_order_no")
    @ExportField(label = "销售订单号", order = 29)
    private String salesOrderNo;

    @TableField("source_sale_order_id")
    private Long sourceSaleOrderId;

    @TableField("source_sale_order_item_id")
    private Long sourceSaleOrderItemId;

    @TableField("remark")
    @ExportField(label = "备注", order = 30)
    private String remark;

    @TableField("status")
    @ExportField(label = "状态", order = 31)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 32)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 33)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 34, dateFormat = "yyyy-MM-dd HH:mm:ss")
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

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 工单用料明细(非数据库字段) */
    @TableField(exist = false)
    private List<WorkOrderMaterial> materials;

    /** 工单成品明细(非数据库字段) - 支持一个工单多个成品 */
    @TableField(exist = false)
    private List<WorkOrderProduct> products;

    /**
     * 工单工序清单（非库字段，2026-09-27 第7期）：
     * 工单编辑页配置的工序（选工艺路线自动带出 / 手工从「工序/工位」主档挑选），
     * 保存时同步写入「工单工序」表；数组顺序 = 工序号 = 车间看板展示顺序。
     */
    @TableField(exist = false)
    private List<WorkOrderOperation> operations;

    /** 表头「计税方式」（含税价 / 不含税价 / 不计税）：映射数据库列 tax_calc_method，随单据保存/回读 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;
}
