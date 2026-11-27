package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成本核算配置实体
 * 对应表: cost_accounting_config
 * 来源: 2026-07-01 客户录音访谈需求
 *
 * 用于配置系统的默认成本核算方法:
 * - moving_avg:    移动加权平均法
 * - weighted_avg:  全月一次加权平均法
 * - fixed:         固定成本法（按物料 fixed_cost 字段取值）
 */
@Data
@TableName("cost_accounting_config")
public class CostAccountingConfig {

    /** 配置ID */
    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    /** 配置名称 */
    @TableField("config_name")
    private String configName;

    /** 默认核算方法: moving_avg / weighted_avg / fixed */
    @TableField("default_method")
    private String defaultMethod;

    /** 是否启用: 0-禁用, 1-启用 */
    @TableField("is_enabled")
    private Integer isEnabled;

    /** 备注 */
    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
