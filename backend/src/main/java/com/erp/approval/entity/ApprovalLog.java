package com.erp.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批历史日志 - 每个节点的处理记录，便于追溯
 */
@Data
@TableName("approval_log")
public class ApprovalLog {
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;
    private Long taskId;
    private Long flowId;
    private Long nodeId;
    private String billType;
    private Long billId;
    private String billNo;
    private String nodeName;
    private Integer nodeOrder;
    private String approver;
    /** approve / reject / transfer / submit */
    private String action;
    private String comment;
    private LocalDateTime actionTime;
}
