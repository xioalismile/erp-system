package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户公海池配置 */
@Data
@TableName("customer_pool_config")
public class CustomerPoolConfig {

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    @TableField("config_name")
    private String configName;

    /** 未跟进天数（超过则退回公海） */
    @TableField("no_follow_days")
    private Integer noFollowDays;

    /** 未开单天数（超过则退回公海） */
    @TableField("no_order_days")
    private Integer noOrderDays;

    /** 预警天数（提前多少天提醒） */
    @TableField("warning_days")
    private Integer warningDays;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
