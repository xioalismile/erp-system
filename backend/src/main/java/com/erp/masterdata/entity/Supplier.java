package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {

    @TableId(value = "supplier_id", type = IdType.AUTO)
    @ExportField(label = "供应商ID", order = 0, hidden = true)
    private Long supplierId;

    @TableField("supplier_code")
    @ExportField(label = "供应商编码", order = 1, required = true)
    private String supplierCode;

    @TableField("supplier_name")
    @ExportField(label = "供应商名称", order = 2, required = true)
    private String supplierName;

    @TableField("short_name")
    @ExportField(label = "简称", order = 3)
    private String shortName;

    @TableField("supplier_type")
    @ExportField(label = "供应商类型", order = 4)
    private String supplierType;

    @TableField("supplier_level")
    @ExportField(label = "供应商等级", order = 5)
    private String supplierLevel;

    @TableField("tax_no")
    @ExportField(label = "税号", order = 6)
    private String taxNo;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 7)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 8)
    private String contactPhone;

    @TableField("email")
    @ExportField(label = "邮箱", order = 9)
    private String email;

    @TableField("address")
    @ExportField(label = "地址", order = 10)
    private String address;

    @TableField("bank_name")
    @ExportField(label = "开户银行", order = 11)
    private String bankName;

    @TableField("bank_account")
    @ExportField(label = "银行账号", order = 12)
    private String bankAccount;

    @TableField("payment_term_id")
    @ExportField(label = "付款条件ID", order = 13)
    private Long paymentTermId;

    /** 账期天数（2026-09-28）：应付到期日计算口径——优先取单据上的付款条件的账期，其次取本字段 */
    @TableField("credit_days")
    @ExportField(label = "账期天数", order = 13)
    private Integer creditDays;

    @TableField("currency_id")
    @ExportField(label = "货币ID", order = 14)
    private Long currencyId;

    @TableField("default_buyer_id")
    @ExportField(label = "默认采购员ID", order = 15)
    private Long defaultBuyerId;

    @TableField("delivery_days")
    @ExportField(label = "交货天数", order = 16)
    private Integer deliveryDays;

    /** 默认税率（2026-11-08）：采购单据选择供应商后带入明细行税率，只填空行 */
    @TableField("tax_rate")
    @ExportField(label = "默认税率", order = 20)
    private BigDecimal taxRate;

    /** 默认付款方式（2026-11-08）：采购单据选择供应商后带入表头付款方式 */
    @TableField("payment_method")
    @ExportField(label = "默认付款方式", order = 21)
    private String paymentMethod;

    @TableField("status")
    @ExportField(label = "状态", order = 17)
    private String status;

    @TableField("legal_person")
    @ExportField(label = "法人代表", order = 18)
    private String legalPerson;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 19)
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 102, hidden = true)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新人", order = 103, hidden = true)
    private String updatedBy;
}