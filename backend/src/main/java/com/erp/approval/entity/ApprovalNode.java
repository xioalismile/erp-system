package com.erp.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批节点 - 流程下的多个节点，按 node_order 顺序流转
 */
@Data
@TableName("approval_node")
public class ApprovalNode {
    @TableId(value = "node_id", type = IdType.AUTO)
    private Long nodeId;
    private Long flowId;
    private String nodeName;
    private Integer nodeOrder;
    /** single 单人 / any 任选一人 / all 全部审批 / sequence 依次审批 */
    private String approverMode;
    private Long positionId;
    private String description;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<ApprovalNodeApprover> approvers;
    @TableField(exist = false)
    private Integer approverCount;
}
