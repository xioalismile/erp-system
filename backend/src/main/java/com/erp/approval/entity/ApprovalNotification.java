package com.erp.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批通知 - 通知接收人审批任务到达/结果
 */
@Data
@TableName("approval_notification")
public class ApprovalNotification {
    @TableId(value = "notification_id", type = IdType.AUTO)
    private Long notificationId;
    private Long receiverId;
    private String receiverName;
    private String billType;
    private Long billId;
    private String billNo;
    private Long taskId;
    private String nodeName;
    /** task 待办 / result 结果 / cc 抄送 / timeout 超时 */
    private String notifyType;
    private String title;
    private String content;
    private Short isRead;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
}
