package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {

    @TableId(value = "supplier_id", type = IdType.AUTO)
    private Long supplierId;

    @TableField("supplier_code")
    private String supplierCode;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("short_name")
    private String shortName;

    @TableField("supplier_type")
    private String supplierType;

    @TableField("supplier_level")
    private String supplierLevel;

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

    @TableField("payment_term_id")
    private Long paymentTermId;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("default_buyer_id")
    private Long defaultBuyerId;

    @TableField("delivery_days")
    private Integer deliveryDays;

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
