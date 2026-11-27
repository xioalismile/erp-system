package com.erp.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审批条件模板 - 条件审批：金额>=X 走流程A
 */
@Data
@TableName("approval_condition")
public class ApprovalCondition {
    @TableId(value = "condition_id", type = IdType.AUTO)
    private Long conditionId;
    private Long flowId;
    private String fieldKey;
    private String operator;
    private BigDecimal valueLow;
    private BigDecimal valueHigh;
    /** 文本类条件值（字符串等于/不等于/包含、日期、下拉选项）；数字类条件仍用 valueLow/valueHigh */
    private String valueText;
    private Integer targetNodeOrder;
    private String description;
    private LocalDateTime createTime;
}
