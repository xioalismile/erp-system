package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户跟进预警配置 */
@Data
@TableName("customer_warning_config")
public class CustomerWarningConfig {

    @TableId(value = "warning_id", type = IdType.AUTO)
    private Long warningId;

    /** 预警类型: no_follow_up-未跟进预警, no_order-未开单预警 */
    @TableField("warning_type")
    private String warningType;

    /** 阈值天数 */
    @TableField("threshold_days")
    private Integer thresholdDays;

    @TableField("is_enabled")
    private Integer isEnabled;

    /** 接收角色 */
    @TableField("receiver_role")
    private String receiverRole;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
