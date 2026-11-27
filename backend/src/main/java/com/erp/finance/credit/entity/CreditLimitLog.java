package com.erp.finance.credit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("credit_limit_log")
public class CreditLimitLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("credit_limit_id")
    private Long creditLimitId;

    @TableField("change_type")
    private String changeType;

    @TableField("before_amount")
    private BigDecimal beforeAmount;

    @TableField("change_amount")
    private BigDecimal changeAmount;

    @TableField("after_amount")
    private BigDecimal afterAmount;

    @TableField("related_doc_id")
    private Long relatedDocId;

    @TableField("related_doc_no")
    private String relatedDocNo;

    @TableField("related_doc_type")
    private String relatedDocType;

    @TableField("reason")
    private String reason;

    @TableField("operator")
    private String operator;

    @TableField("operate_time")
    private LocalDateTime operateTime;

    @TableField("remark")
    private String remark;
}
