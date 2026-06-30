package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {

    @TableId(value = "customer_id", type = IdType.AUTO)
    private Long customerId;

    @TableField("customer_code")
    private String customerCode;

    @TableField("customer_name")
    private String customerName;

    @TableField("short_name")
    private String shortName;

    @TableField("customer_type")
    private String customerType;

    @TableField("customer_level")
    private String customerLevel;

    @TableField("tax_no")
    private String taxNo;

    @TableField("contact_person")
    private String contactPerson;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("email")
    private String email;

    @TableField("address")
    private String address;

    @TableField("bank_name")
    private String bankName;

    @TableField("bank_account")
    private String bankAccount;

    @TableField("credit_limit")
    private BigDecimal creditLimit;

    @TableField("payment_term_id")
    private Long paymentTermId;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("default_saler_id")
    private Long defaultSalerId;

    @TableField("default_price_level_id")
    private Long defaultPriceLevelId;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
