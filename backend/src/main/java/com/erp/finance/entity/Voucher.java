package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会计凭证主表 - 对齐 PG voucher 表
 */
@Data
@TableName("voucher")
public class Voucher {

    @TableId(value = "voucher_id", type = IdType.AUTO)
    private Long voucherId;

    @TableField("voucher_no")
    private String voucherNo;

    @TableField("voucher_date")
    private LocalDate voucherDate;

    @TableField("voucher_type")
    private String voucherType;

    @TableField("period_year")
    private Integer periodYear;

    @TableField("period_month")
    private Integer periodMonth;

    @TableField("total_debit")
    private BigDecimal totalDebit;

    @TableField("total_credit")
    private BigDecimal totalCredit;

    @TableField("attachment_count")
    private Integer attachmentCount;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("maker_id")
    private Long makerId;

    @TableField("maker_name")
    private String makerName;

    @TableField("poster_id")
    private Long posterId;

    @TableField("poster_name")
    private String posterName;

    @TableField("checker_id")
    private Long checkerId;

    @TableField("checker_name")
    private String checkerName;

    @TableField("cashier_id")
    private Long cashierId;

    @TableField("cashier_name")
    private String cashierName;

    @TableField("is_reversed")
    private Short isReversed;

    @TableField("reversed_voucher_id")
    private Long reversedVoucherId;

    @TableField("reversed_voucher_no")
    private String reversedVoucherNo;

    @TableField("reverse_voucher_id")
    private Long reverseVoucherId;

    @TableField("reverse_voucher_no")
    private String reverseVoucherNo;

    @TableField("remark")
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;

    /** 凭证分录列表(非数据库字段) */
    @TableField(exist = false)
    private List<VoucherEntry> entries;
}
