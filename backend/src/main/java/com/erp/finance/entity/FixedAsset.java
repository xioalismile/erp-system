package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 固定资产主表 - 对齐 PG fixed_asset 表
 */
@Data
@TableName("fixed_asset")
public class FixedAsset {

    @ExportField(label = "资产ID", order = 1, required = true)
    @TableId(value = "asset_id", type = IdType.AUTO)
    private Long assetId;

    @ExportField(label = "资产编码", order = 2, required = true)
    @TableField("asset_code")
    private String assetCode;

    @ExportField(label = "资产名称", order = 3, required = true)
    @TableField("asset_name")
    private String assetName;

    @ExportField(label = "资产类型", order = 4, required = true)
    @TableField("asset_type")
    private String assetType;

    @ExportField(label = "规格型号", order = 5, required = false)
    @TableField("specification")
    private String specification;

    @ExportField(label = "品牌", order = 6, required = false)
    @TableField("brand")
    private String brand;

    @ExportField(label = "序列号", order = 7, required = false)
    @TableField("serial_no")
    private String serialNo;

    @ExportField(label = "购置日期", order = 8, required = true)
    @TableField("purchase_date")
    private LocalDate purchaseDate;

    @ExportField(label = "启用日期", order = 9, required = true)
    @TableField("use_start_date")
    private LocalDate useStartDate;

    @ExportField(label = "供应商ID", order = 10, required = false)
    @TableField("supplier_id")
    private Long supplierId;

    @ExportField(label = "购置金额", order = 11, required = true)
    @TableField("purchase_amount")
    private BigDecimal purchaseAmount;

    @ExportField(label = "净值", order = 12, required = true)
    @TableField("net_value")
    private BigDecimal netValue;

    @ExportField(label = "折旧方法", order = 13, required = true)
    @TableField("depreciation_method")
    private String depreciationMethod;

    @ExportField(label = "使用年限(年)", order = 14, required = false)
    @TableField("service_life_years")
    private Integer serviceLifeYears;

    @ExportField(label = "残值率", order = 15, required = false)
    @TableField("residual_rate")
    private BigDecimal residualRate;

    @ExportField(label = "残值", order = 16, required = false)
    @TableField("residual_value")
    private BigDecimal residualValue;

    @ExportField(label = "月折旧额", order = 17, required = false)
    @TableField("depreciation_per_month")
    private BigDecimal depreciationPerMonth;

    @ExportField(label = "累计折旧", order = 18, required = false)
    @TableField("accumulated_depreciation")
    private BigDecimal accumulatedDepreciation;

    @ExportField(label = "本期折旧", order = 19, required = false)
    @TableField("current_depreciation")
    private BigDecimal currentDepreciation;

    @ExportField(label = "部门ID", order = 20, required = false)
    @TableField("department_id")
    private Long departmentId;

    @ExportField(label = "存放地点", order = 21, required = false)
    @TableField("location")
    private String location;

    @ExportField(label = "保管人", order = 22, required = false)
    @TableField("custodian")
    private String custodian;

    @ExportField(label = "来源单据类型", order = 23, required = false)
    @TableField("source_bill_type")
    private String sourceBillType;

    @ExportField(label = "来源单据号", order = 24, required = false)
    @TableField("source_bill_no")
    private String sourceBillNo;

    @ExportField(label = "卡片号", order = 25, required = false)
    @TableField("card_no")
    private String cardNo;

    @ExportField(label = "是否打印标签", order = 26, required = false)
    @TableField("is_tag_print")
    private Short isTagPrint;

    @ExportField(label = "照片URL", order = 27, required = false)
    @TableField("photo_urls")
    private String photoUrls;

    @ExportField(label = "备注", order = 28, required = false)
    @TableField("remark")
    private String remark;

    @ExportField(label = "附件URL", order = 29, required = false)
    @TableField("attachment_urls")
    private String attachmentUrls;

    @ExportField(label = "状态", order = 30, required = false)
    @TableField("status")
    private String status;

    @ExportField(label = "处置状态", order = 31, required = false)
    @TableField("disposal_status")
    private String disposalStatus;

    @ExportField(label = "处置日期", order = 32, required = false)
    @TableField("disposal_date")
    private LocalDate disposalDate;

    @ExportField(label = "处置金额", order = 33, required = false)
    @TableField("disposal_amount")
    private BigDecimal disposalAmount;

    @ExportField(label = "处置原因", order = 34, required = false)
    @TableField("disposal_reason")
    private String disposalReason;

    @ExportField(label = "资产科目编码", order = 35, required = false)
    @TableField("asset_account_code")
    private String assetAccountCode;

    @ExportField(label = "折旧科目编码", order = 36, required = false)
    @TableField("depreciation_account_code")
    private String depreciationAccountCode;

    @ExportField(label = "折旧费用科目编码", order = 37, required = false)
    @TableField("depreciation_expense_account_code")
    private String depreciationExpenseAccountCode;

    @ExportField(label = "折旧状态", order = 38, required = false)
    @TableField("depreciation_status")
    private String depreciationStatus;

    @ExportField(label = "使用月数", order = 39, required = false)
    @TableField("useful_life_months")
    private Integer usefulLifeMonths;

    @ExportField(label = "审批状态", order = 40, required = false)
    @TableField("approval_status")
    private String approvalStatus;

    @ExportField(label = "审批人", order = 41, required = false)
    @TableField("approved_by")
    private String approvedBy;

    @ExportField(label = "审批时间", order = 42, required = false)
    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @ExportField(label = "创建时间", order = 43, required = false)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @ExportField(label = "更新时间", order = 44, required = false)
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @ExportField(label = "创建人", order = 45, required = false)
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @ExportField(label = "更新人", order = 46, required = false)
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @ExportField(label = "组织编码", order = 47, required = false)
    @TableField("org_code")
    private String orgCode;
}
