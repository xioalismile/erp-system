package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公司年度销售目标
 */
@Data
@TableName("sales_target_company")
public class SalesTargetCompany {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("target_year")
    private Integer targetYear;

    /** 年度销售目标金额 */
    @TableField("annual_amount")
    private BigDecimal annualAmount;

    /** 下年度自动增长率(%)，默认30 */
    @TableField("growth_rate")
    private BigDecimal growthRate;

    /** 是否自动生成下年度目标: 1=是 0=否 */
    @TableField("auto_generate_next_year")
    private Integer autoGenerateNextYear;

    private String description;

    private String status;

    @TableField("create_date")
    private LocalDateTime createDate;

    @TableField("update_date")
    private LocalDateTime updateDate;

    @TableField("create_by")
    private String createBy;

    @TableField("update_by")
    private String updateBy;

    /** 已分配金额（运行时计算，不入库） */
    @TableField(exist = false)
    private BigDecimal allocatedAmount;

    /** 未分配金额（运行时计算，不入库） */
    @TableField(exist = false)
    private BigDecimal unallocatedAmount;

    /** 已分配销售人数（运行时计算，不入库） */
    @TableField(exist = false)
    private Integer allocatedCount;
}
