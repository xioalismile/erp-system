package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单工序操作日志 - 对齐 PG work_operation_log 表。
 *
 * <p>口径：<b>只增不改不删</b>。记录"谁、什么时间、对哪道工序做了什么、状态从哪到哪、数量变化"，
 * 既用于追溯（守则 11 的业务影响留痕），也用于工序绩效与工序工资核算的取数依据。</p>
 *
 * <p>写入方：① 工序操作面板（人工动作，action_type = 派工/开工/暂停/…）；
 * ② 单据驱动（工序汇报单审核，action_type = 单据驱动）——
 * 单据驱动也必须留痕，否则"人工按钮推进 vs 单据推进"分不清（守则 11 的假闭环坑）。</p>
 */
@Data
@TableName("work_operation_log")
public class WorkOperationLog {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 工单ID */
    @TableField("order_id")
    private Long orderId;

    /** 工单号 */
    @TableField("order_no")
    private String orderNo;

    /** 工单工序行ID（work_order_operation.id） */
    @TableField("operation_row_id")
    private Long operationRowId;

    /** 工序编码 */
    @TableField("operation_code")
    private String operationCode;

    /** 工序名称 */
    @TableField("operation_name")
    private String operationName;

    /** 动作：派工/开工/暂停/恢复/完工/检验通过/检验退回/取消/数量报工/入库/金额登记/单据驱动 */
    @TableField("action_type")
    private String actionType;

    /** 变更前状态 */
    @TableField("from_status")
    private String fromStatus;

    /** 变更后状态 */
    @TableField("to_status")
    private String toStatus;

    /** 本次数量（报工/入库时的合格数） */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 本次报废数 */
    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 操作人ID */
    @TableField("operator_id")
    private Long operatorId;

    /** 操作人姓名 */
    @TableField("operator_name")
    private String operatorName;

    /** 操作时间 */
    @TableField("operate_time")
    private LocalDateTime operateTime;

    /** 备注（如暂停原因、退回原因） */
    @TableField("remark")
    private String remark;
}
