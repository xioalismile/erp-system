package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序共同负责人（2026-10-25 扫码认领）。
 *
 * <p>口径：扫码「我来做」= 加入为共同负责人，<b>不覆盖系统派工的主负责人</b>（assignee_id 不动）；
 * 只有工序处于「待派工」且工单已生效时，认领人才同时补为主负责人。</p>
 *
 * <p>个人数量：认领时可选填「我打算做多少」（claim_quantity），填了按此卡个人上限；
 * reported_quantity 是个人累计已报（由报工流水汇总，缓存列）。</p>
 */
@Data
@TableName("work_order_operation_worker")
public class WorkOrderOperationWorker {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 工单工序行ID（work_order_operation.id） */
    @TableField("operation_row_id")
    private Long operationRowId;

    /** 员工ID（员工档案） */
    @TableField("employee_id")
    private Long employeeId;

    /** 员工姓名（冗余，页面直接显示） */
    @TableField("employee_name")
    private String employeeName;

    /** 认领数量（可空；空 = 不设个人上限） */
    @TableField("claim_quantity")
    private BigDecimal claimQuantity;

    /** 个人累计已报（由报工流水汇总） */
    @TableField("reported_quantity")
    private BigDecimal reportedQuantity;

    /** 在岗 / 已退出 / 已结束 */
    @TableField("status")
    private String status;

    /** 认领时间 */
    @TableField("claim_time")
    private LocalDateTime claimTime;

    /** 退出/结束时间 */
    @TableField("exit_time")
    private LocalDateTime exitTime;

    /** 认领操作人（登录账号ID） */
    @TableField("claimed_by")
    private Long claimedBy;

    /** 认领操作人姓名 */
    @TableField("claimed_by_name")
    private String claimedByName;

    /** 备注 */
    @TableField("remark")
    private String remark;

    // ---------------- 非库字段（页面回显） ----------------

    /** 是否主负责人（主负责人不在参与人表时由服务层补一行展示） */
    @TableField(exist = false)
    private Boolean primaryAssignee;

    /** 个人剩余可报（认领数量 − 个人已报；未设认领数量时为空） */
    @TableField(exist = false)
    private BigDecimal remainingQuantity;
}
