package com.erp.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点审批人 - 一个节点可有多个候选审批人
 */
@Data
@TableName("approval_node_approver")
public class ApprovalNodeApprover {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private Long userId;
    private Long employeeId;
    private String approverName;
    private LocalDateTime createTime;
}
