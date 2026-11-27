package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提成率配置历史留痕表 - 每次修改新增一版，按生效日期匹配历史版本
 */
@Data
@TableName("commission_rate")
public class CommissionRate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 版本号，递增 */
    @TableField("version_no")
    private Integer versionNo;

    /** 计算基础: order=订单 / contract=合同 / collection=回款 */
    @TableField("basis")
    private String basis;

    /** 基础提成率（小数，如0.05表示5%） */
    @TableField("base_rate")
    private BigDecimal baseRate;

    /** 超额倍数阈值（如1.20表示完成率>=120%触发奖励） */
    @TableField("over_achievement_multiplier")
    private BigDecimal overAchievementMultiplier;

    /** 超额奖励率（小数，如0.03表示额外3%） */
    @TableField("over_achievement_bonus")
    private BigDecimal overAchievementBonus;

    /** 生效日期（含） */
    @TableField("effective_date")
    private LocalDate effectiveDate;

    /** 失效日期（含），NULL表示当前生效 */
    @TableField("expiry_date")
    private LocalDate expiryDate;

    /** 是否当前生效版本: 1=是 0=否 */
    @TableField("is_current")
    private Short isCurrent;

    @TableField("remark")
    private String remark;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
