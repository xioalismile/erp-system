package com.erp.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知实体 (对应 sys_notification 表)
 * 通用消息通知，由 NotificationScheduler 定时扫描各业务模块生成
 * 接收人维度，支持已读/未读状态，支持跳转到原始单据
 */
@Data
@TableName("sys_notification")
public class SysNotification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 接收人ID (sys_user.user_id) */
    @TableField("receiver_id")
    private Long receiverId;

    /** 接收人姓名 */
    @TableField("receiver_name")
    private String receiverName;

    /** 通知类型: approval(待审批) / overdue(已超期) / soon_overdue(即将超期) / draft(草稿) / ar_ap(应收应付跟进) */
    @TableField("type")
    private String type;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    /** 来源单据类型: sale_order/purchase_order/work_order/routing_operation/operation_report 等 */
    @TableField("source_type")
    private String sourceType;

    /** 来源单据ID */
    @TableField("source_id")
    private Long sourceId;

    /** 来源单据编号 */
    @TableField("source_no")
    private String sourceNo;

    /** 前端跳转路径 (如 /sale/orders/123) */
    @TableField("route_path")
    private String routePath;

    /** 紧急度: OVERDUE / URGENT / SOON / NORMAL */
    @TableField("urgency")
    private String urgency;

    /** 是否已读: 0未读 / 1已读 */
    @TableField("is_read")
    private Short isRead;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;
}
