package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {

    /** 客户授信状态 - 默认未申请（未发起过授信或分类变更后重置） */
    public static final String CREDIT_STATUS_NONE = "未申请";
    /** 客户授信状态 - 授信审批中 */
    public static final String CREDIT_STATUS_PENDING = "审批中";
    /** 客户授信状态 - 授信审批已通过（月结客户后续销售合同免逐单审批） */
    public static final String CREDIT_STATUS_APPROVED = "已通过";
    /** 客户授信状态 - 授信审批已驳回（可修改首单合同后重新提交再次申请） */
    public static final String CREDIT_STATUS_REJECTED = "已驳回";
    /** 客户授信状态 - 已失效（付款管控分类变更自动失效，需重新发起授信） */
    public static final String CREDIT_STATUS_INVALID = "已失效";

    @TableId(value = "customer_id", type = IdType.AUTO)
    @ExportField(label = "客户ID", order = 0, hidden = true)
    private Long customerId;

    @TableField("customer_code")
    @ExportField(label = "客户编码", order = 1, required = true)
    private String customerCode;

    @TableField("customer_name")
    @ExportField(label = "客户名称", order = 2, required = true)
    private String customerName;

    @TableField("short_name")
    @ExportField(label = "简称", order = 3)
    private String shortName;

    @TableField("customer_type")
    @ExportField(label = "客户类型", order = 4)
    private String customerType;

    @TableField("customer_level")
    @ExportField(label = "客户等级", order = 5)
    private String customerLevel;

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

    @TableField("credit_limit")
    @ExportField(label = "信用额度", order = 13)
    private BigDecimal creditLimit;

    /** 默认税率（2026-11-08）：单据选择客户后带入明细行税率，只填空行，不覆盖手工改过的值 */
    @TableField("tax_rate")
    @ExportField(label = "默认税率", order = 45)
    private BigDecimal taxRate;

    @TableField("payment_term_id")
    @ExportField(label = "付款条件ID", order = 14)
    private Long paymentTermId;

    /** 账期天数（2026-09-28）：应收到期日计算口径——优先取单据上的付款条件的账期，其次取本字段 */
    @TableField("credit_days")
    @ExportField(label = "账期天数", order = 14)
    private Integer creditDays;

    @TableField("currency_id")
    @ExportField(label = "货币ID", order = 15)
    private Long currencyId;

    @TableField("default_saler_id")
    @ExportField(label = "默认销售员ID", order = 16)
    private Long defaultSalerId;

    @TableField("default_price_level_id")
    @ExportField(label = "默认价格等级ID", order = 17)
    private Long defaultPriceLevelId;

    /** 默认发货仓库ID（库存-仓库 stock_id）：客户档案「默认发货仓」下拉原先不落库（2026-09-20 补列） */
    @TableField("default_delivery_warehouse_id")
    private Long defaultDeliveryWarehouseId;

    @TableField("status")
    @ExportField(label = "状态", order = 18)
    private String status;

    @TableField("tax_register_no")
    @ExportField(label = "税务登记号", order = 19)
    private String taxRegisterNo;

    @TableField("legal_person")
    @ExportField(label = "法人代表", order = 20)
    private String legalPerson;

    @TableField("source_type")
    @ExportField(label = "客户来源", order = 21)
    private String sourceType;

    /** 付款管控分类：现结/月结（精简后仅两档，2026-09-07 铁律：不写死分类->审批行为映射，仅作审批条件路由字段） */
    @TableField("payment_category")
    @ExportField(label = "付款管控分类", order = 22)
    private String paymentCategory;

    /** 客户授信状态（月结客户一次性授信审批专用，字段不参与导入导出模板）：
     *  未申请/审批中/已通过/已驳回/已失效；分类变更自动失效 */
    @TableField("credit_approval_status")
    private String creditApprovalStatus;

    /** 授信审批通过时间（系统写入，不参与导入导出模板） */
    @TableField("credit_approved_at")
    private LocalDateTime creditApprovedAt;

    /** 授信审批通过人（系统写入，不参与导入导出模板） */
    @TableField("credit_approved_by")
    private String creditApprovedBy;

    @TableField("source_detail")
    @ExportField(label = "来源详情", order = 22)
    private String sourceDetail;

    @TableField("industry")
    @ExportField(label = "所属行业", order = 23)
    private String industry;

    @TableField("company_scale")
    @ExportField(label = "公司规模", order = 24)
    private String companyScale;

    @TableField("annual_revenue")
    @ExportField(label = "年营业额", order = 25)
    private BigDecimal annualRevenue;

    @TableField("wechat")
    @ExportField(label = "微信", order = 26)
    private String wechat;

    /** 「微信」字段首次由空变非空的时间（用于"新增意向微信"当日口径，2026-10-07） */
    @TableField("wechat_set_at")
    private LocalDateTime wechatSetAt;

    @TableField("website")
    @ExportField(label = "公司网站", order = 27)
    private String website;

    @TableField("crm_status")
    @ExportField(label = "CRM状态", order = 28)
    private String crmStatus;

    /** 自定义字段快照（JSONB，仅 is_search=1 的高频字段同步） */
    @TableField(value = "ext_data")
    private String extData;

    @TableField("last_contact_date")
    @ExportField(label = "最后联系日期", order = 29, dateFormat = "yyyy-MM-dd")
    private LocalDate lastContactDate;

    @TableField("next_contact_date")
    @ExportField(label = "下次联系日期", order = 30, dateFormat = "yyyy-MM-dd")
    private LocalDate nextContactDate;

    @TableField("important_level")
    @ExportField(label = "重要程度", order = 31)
    private String importantLevel;

    @TableField("potential_value")
    @ExportField(label = "潜在价值", order = 32)
    private BigDecimal potentialValue;

    @TableField("satisfaction_score")
    @ExportField(label = "满意度评分", order = 33)
    private Integer satisfactionScore;

    @TableField("refund_amount")
    @ExportField(label = "累计退款金额", order = 34)
    private BigDecimal refundAmount;

    @TableField("complaint_count")
    @ExportField(label = "投诉次数", order = 35)
    private Integer complaintCount;

    @TableField("business_opportunity_count")
    @ExportField(label = "商机数量", order = 36)
    private Integer businessOpportunityCount;

    @TableField("order_count")
    @ExportField(label = "订单数量", order = 37)
    private Integer orderCount;

    @TableField("total_order_amount")
    @ExportField(label = "累计订单金额", order = 38)
    private BigDecimal totalOrderAmount;

    @TableField("tag")
    @ExportField(label = "客户标签", order = 39)
    private String tag;

    @TableField("sales_stage")
    @ExportField(label = "销售阶段", order = 40)
    private String salesStage;

    @TableField("intention_level")
    @ExportField(label = "意向等级", order = 41)
    private String intentionLevel;

    @TableField("assigned_sales_id")
    @ExportField(label = "分配销售员ID", order = 42)
    private Long assignedSalesId;

    @TableField("follow_up_count")
    @ExportField(label = "跟进次数", order = 43)
    private Integer followUpCount;

    @TableField("is_blacklist")
    @ExportField(label = "是否黑名单", order = 44)
    private Integer isBlacklist;

    @TableField("pool_type")
    @ExportField(label = "客户池类型", order = 45)
    private Integer poolType;

    @TableField("pool_enter_date")
    @ExportField(label = "进入公海日期", order = 46)
    private LocalDateTime poolEnterDate;

    @TableField("last_order_date")
    @ExportField(label = "最后开单日期", order = 47, dateFormat = "yyyy-MM-dd")
    private LocalDate lastOrderDate;

    @TableField("last_follow_up_date")
    @ExportField(label = "最后跟进日期", order = 48, dateFormat = "yyyy-MM-dd")
    private LocalDate lastFollowUpDate;

    @TableField("is_top")
    @ExportField(label = "是否置顶", order = 49)
    private Integer isTop;

    @TableField("top_time")
    @ExportField(label = "置顶时间", order = 50)
    private LocalDateTime topTime;

    @TableField("owner_user_id")
    @ExportField(label = "归属用户ID", order = 51)
    private Long ownerUserId;

    @TableField("main_business")
    @ExportField(label = "主营业务", order = 52)
    private String mainBusiness;

    @TableField("project_type")
    @ExportField(label = "项目类型", order = 53)
    private String projectType;

    @TableField("customer_demand")
    @ExportField(label = "客户需求", order = 54)
    private String customerDemand;

    @TableField("customer_preference")
    @ExportField(label = "客户喜好", order = 55)
    private String customerPreference;

    @TableField("family_background")
    @ExportField(label = "家庭背景", order = 56)
    private String familyBackground;

    @TableField("remark")
    @ExportField(label = "备注", order = 57)
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