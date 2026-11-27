package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售人员年度目标分配
 */
@Data
@TableName("sales_target_employee")
public class SalesTargetEmployee {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("target_year")
    private Integer targetYear;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("employee_name")
    private String employeeName;

    @TableField("dept_id")
    private Long deptId;

    @TableField("dept_name")
    private String deptName;

    /** 年度目标金额 */
    @TableField("annual_target_amount")
    private BigDecimal annualTargetAmount;

    @TableField("q1_target")
    private BigDecimal q1Target;

    @TableField("q2_target")
    private BigDecimal q2Target;

    @TableField("q3_target")
    private BigDecimal q3Target;

    @TableField("q4_target")
    private BigDecimal q4Target;

    /** 新客户数目标 */
    @TableField("new_customer_target")
    private Integer newCustomerTarget;

    /** 新客户数实际 */
    @TableField("new_customer_actual")
    private Integer newCustomerActual;

    /** 订单数目标 */
    @TableField("order_count_target")
    private Integer orderCountTarget;

    /** 订单数实际 */
    @TableField("order_count_actual")
    private Integer orderCountActual;

    /** 实际完成金额（由销售订单+客户数据自动汇总） */
    @TableField("actual_amount")
    private BigDecimal actualAmount;

    /** 完成率(%) */
    @TableField("completion_rate")
    private BigDecimal completionRate;

    /** 分配状态: 已分配/待分配 */
    @TableField("allocation_status")
    private String allocationStatus;

    private String remark;

    @TableField("create_date")
    private LocalDateTime createDate;

    @TableField("update_date")
    private LocalDateTime updateDate;

    @TableField("create_by")
    private String createBy;

    @TableField("update_by")
    private String updateBy;

    // ===== 季度口径（仅计算和前端展示，不落库） =====

    /** 季度实际金额 */
    @TableField(exist = false)
    private BigDecimal quarterActualAmount;

    /** 季度订单数实际 */
    @TableField(exist = false)
    private Integer quarterOrderCountActual;

    /** 季度新客户数实际 */
    @TableField(exist = false)
    private Integer quarterNewCustomerActual;

    /** 季度目标金额 */
    @TableField(exist = false)
    private BigDecimal quarterTargetAmount;

    /** 季度完成率(%) */
    @TableField(exist = false)
    private BigDecimal quarterCompletionRate;
}
