package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生产工单进度对外分享（2026-11-25）。
 *
 * <p>手机端「工序进度」生成带过期时间的分享链接 / 小程序卡片，微信用户免登录查看该工单工序进度。
 * 只记录令牌与展示配置，不存客户、价格、成本等敏感字段；过期或停止后立即失效。</p>
 */
@Data
@TableName("work_order_share")
public class WorkOrderShare {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 分享令牌：{租户ID}.{随机会话串}，公开接口据此定位租户与分享记录 */
    @TableField("token")
    private String token;

    /** 生产工单主键 */
    @TableField("order_id")
    private Long orderId;

    /** 工单号（冗余，分享记录列表直接展示） */
    @TableField("order_no")
    private String orderNo;

    /** 分享标题（自定义） */
    @TableField("title")
    private String title;

    /** 分享说明（自定义） */
    @TableField("remark")
    private String remark;

    /** 失效时间 */
    @TableField("expire_at")
    private LocalDateTime expireAt;

    /** 1=有效，0=已停止 */
    @TableField("status")
    private Integer status;

    /** 公开页是否显示负责人 */
    @TableField("show_assignee")
    private Integer showAssignee;

    /** 公开页是否显示计划日期 */
    @TableField("show_plan_date")
    private Integer showPlanDate;

    /** 公开页是否显示物料编码 */
    @TableField("show_material_code")
    private Integer showMaterialCode;

    /** 访问次数 */
    @TableField("view_count")
    private Integer viewCount;

    /** 最近一次访问时间 */
    @TableField("last_view_at")
    private LocalDateTime lastViewAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_by_name")
    private String createdByName;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
