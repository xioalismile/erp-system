package com.erp.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务 - 运行时：每个待办单据节点生成一条任务
 */
@Data
@TableName("approval_task")
public class ApprovalTask {
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;
    private Long flowId;
    private Long nodeId;
    private String billType;
    private Long billId;
    private String billNo;
    /** pending 待办 / approved 通过 / rejected 驳回 / transferred 转交 */
    private String taskStatus;
    private String currentApprover;
    private String assigneeIds;
    private String comment;
    private String submitBy;
    private LocalDateTime submitTime;
    private LocalDateTime finishTime;
    private Integer nodeOrder;
}
