package com.erp.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批流程定义 - 每个单据类型一个流程
 */
@Data
@TableName("approval_flow")
public class ApprovalFlow {
    @TableId(value = "flow_id", type = IdType.AUTO)
    private Long flowId;
    private String flowCode;
    private String flowName;
    private String billType;
    private Short enabled;
    private String description;
    /** 触发条件JSON，如 {"payment_category":"月结客户","min_amount":50000} */
    @TableField("condition_expr")
    private String conditionExpr;
    /** 流程优先级（数字越小优先级越高，同billType多流程时按优先级匹配） */
    @TableField("priority")
    private Integer priority;
    /** 审批结果抄送接收人ID列表（JSON数组：[1,2,3]），审批通过/驳回时 + 发起人 自动推送小铃铛通知；可为空 */
    @TableField("notify_user_ids")
    private String notifyUserIds;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;

    @TableField(exist = false)
    private List<ApprovalNode> nodes;
    @TableField(exist = false)
    private Integer nodeCount;
}
