package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售人员月度目标（2026-10-07 · 目标五级分解）
 *
 * <p>只有"被手工覆盖过的月份"才落库；未覆盖的月份不落行，由「季度目标 ÷ 3」实时计算。
 * 尾差规则（用户 2026-10-07 拍板）：季→月的尾差补到该季最后一个月；月→周的尾差补到该月最后一个周；
 * 周→日的尾差补到该周最后一个工作日。</p>
 */
@Data
@TableName("sales_target_month")
public class SalesTargetMonth {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("target_year")
    private Integer targetYear;

    @TableField("employee_id")
    private Long employeeId;

    /** 月份 1~12 */
    @TableField("month_no")
    private Integer monthNo;

    @TableField("target_amount")
    private BigDecimal targetAmount;

    /** 是否手工覆盖（1=页面改过，重算时不被覆盖） */
    @TableField("is_manual")
    private Integer isManual;

    @TableField("create_by")
    private String createBy;

    @TableField("update_by")
    private String updateBy;

    @TableField("create_date")
    private LocalDateTime createDate;

    @TableField("update_date")
    private LocalDateTime updateDate;
}
