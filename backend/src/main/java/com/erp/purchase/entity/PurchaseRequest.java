package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购申请单(对应 purchase_request 表)
 * 文档3.1 表头字段(22个)
 * 单据状态流转: 草稿 → 待审批 → 已审批 → 已关闭
 */
@Data
@TableName("purchase_request")
public class PurchaseRequest {

    @TableId(value = "request_id", type = IdType.AUTO)
    @ExportField(label = "申请单ID", order = 0, hidden = true)
    private Long requestId;

    /** 申请单号 FBillNo, 自动生成, 如 PR-20260622-0001 */
    @TableField("request_no")
    @ExportField(label = "申请单号", order = 1, required = true)
    private String requestNo;

    /** 申请日期 FApplyDate */
    @TableField("request_date")
    @ExportField(label = "申请日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate requestDate;

    /** 申请部门 FApplyDeptID */
    @TableField("department_id")
    @ExportField(label = "部门ID", order = 3)
    private Long departmentId;

    /** 申请部门名称 FApplyDeptName */
    @TableField("apply_dept_name")
    @ExportField(label = "申请部门", order = 4)
    private String applyDeptName;

    /** 申请人 FApplierID */
    @TableField("applicant_id")
    @ExportField(label = "申请人ID", order = 5)
    private Long applicantId;

    /** 申请人名称 FApplierName */
    @TableField("applier_name")
    @ExportField(label = "申请人", order = 6)
    private String applierName;

    /** 申请类型 FApplyType: 普通采购/紧急采购/生产采购/研发采购/设备采购 */
    @TableField("apply_type")
    @ExportField(label = "申请类型", order = 7)
    private String applyType;

    /** 需求日期 FDemandDate */
    @TableField("demand_date")
    @ExportField(label = "需求日期", order = 8, dateFormat = "yyyy-MM-dd")
    private LocalDate demandDate;

    /** 结算方式ID（基础资料-结算方式）：本表原先无此列，前端下拉选了不落库（2026-09-20 补列） */
    @TableField("settle_mode_id")
    private Long settleModeId;

    /** 审批状态 FBillStatus: 草稿/待审批/已审批/已关闭 */
    @TableField("status")
    @ExportField(label = "状态", order = 9)
    private String status;

    /** 申请总数量 FTotalQty, 自动汇总表体申请数量 */
    @TableField("total_qty")
    @ExportField(label = "总数量", order = 10, numberFormat = "0.00")
    private BigDecimal totalQty;

    /** 来源类型 FSourceType: 销售订单/MRP/手工 */
    @TableField("source_type")
    @ExportField(label = "来源类型", order = 11)
    private String sourceType;

    /** 来源单号 FSourceBillNo */
    @TableField("source_bill_no")
    @ExportField(label = "来源单号", order = 12)
    private String sourceBillNo;

    /** 付款条件ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("payment_term_id")
    @ExportField(label = "付款条件", order = 18)
    private Long paymentTermId;

    /** 是否紧急 FIsUrgent: 0-否, 1-是 */
    @TableField("is_urgent")
    @ExportField(label = "是否紧急", order = 13)
    private Integer isUrgent;

    /** 审批人(用户名), 数据库列 approved_by */
    @TableField("approved_by")
    @ExportField(label = "审批人", order = 14)
    private String approvedBy;

    /** 审批时间, 数据库列 approved_at */
    @TableField("approved_at")
    @ExportField(label = "审批时间", order = 15, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    /** 是否关闭 FIsClosed: 0-未关闭, 1-已关闭 */
    @TableField("is_closed")
    @ExportField(label = "是否关闭", order = 16)
    private Integer isClosed;

    /** 备注 FRemark */
    @TableField("remark")
    @ExportField(label = "备注", order = 17)
    private String remark;

    /** 制单人名称(兼容现有列) */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("org_code")
    private String orgCode;

    /** 审批状态标签: 草稿/待审核/已生效/驳回, 数据库列 approval_status（状态流转用） */
    @TableField("approval_status")
    private String approvalStatus;

    /** 作废前状态码(0草稿 1待审核 2已生效), 供反作废恢复, 数据库列 bill_before_status */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    /** 作废人, 数据库列 voided_by */
    @TableField("voided_by")
    private String voidedBy;

    /** 作废时间, 数据库列 voided_at */
    @TableField("voided_at")
    private LocalDateTime voidedAt;

    /** 作废原因, 数据库列 void_reason */
    @TableField("void_reason")
    private String voidReason;

    /** 结案状态：0=未结案 1=已结案（结案后 status 保持已审批，前端按此字段渲染已结案） */
    @TableField("close_status")
    private Integer closeStatus;

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

    /** 结案人, 数据库列 closed_by */
    @TableField("closed_by")
    private String closedBy;

    /** 结案时间, 数据库列 closed_at */
    @TableField("closed_at")
    private LocalDateTime closedAt;

    /** 结案原因, 数据库列 closed_reason */
    @TableField("closed_reason")
    private String closedReason;

    /** 采购申请明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<PurchaseRequestItem> items;
}
