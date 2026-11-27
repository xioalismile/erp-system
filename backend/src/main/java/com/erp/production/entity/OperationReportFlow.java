package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序报工流水（2026-10-25）：每笔报工一条，按人记账。
 *
 * <p><b>一本账口径</b>：工序「已报工」与个人「已报」都由本表汇总；
 * 扫码报工、工序汇报单两条路都只写流水，不再各自累加（防重复计）。</p>
 *
 * <p>撤销报工只把 flow_status 置「已冲销」，不物理删除，保留追溯。</p>
 */
@Data
@TableName("operation_report_flow")
public class OperationReportFlow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 工单工序行ID */
    @TableField("operation_row_id")
    private Long operationRowId;

    /** 工单ID / 工单号（冗余，报表直接取） */
    @TableField("order_id")
    private Long orderId;

    @TableField("order_no")
    private String orderNo;

    /** 实际生产员工（代报时 = 被代报的临时工） */
    @TableField("employee_id")
    private Long employeeId;

    @TableField("employee_name")
    private String employeeName;

    /** 本次合格数量 */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 本次报废数量 */
    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 来源：扫码报工 / 工序汇报单 */
    @TableField("source")
    private String source;

    /** 来源单据ID / 单号（工序汇报单来源时填） */
    @TableField("source_bill_id")
    private Long sourceBillId;

    @TableField("source_bill_no")
    private String sourceBillNo;

    /** 操作人（登录账号） */
    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    /** 1 = 代报（登录人代替所选员工报工） */
    @TableField("is_proxy")
    private Integer isProxy;

    /** 有效 / 已冲销 */
    @TableField("flow_status")
    private String flowStatus;

    /** 防重号（连点/重试只成功一次） */
    @TableField("idem_key")
    private String idemKey;

    /** 报工时间 */
    @TableField("report_time")
    private LocalDateTime reportTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
