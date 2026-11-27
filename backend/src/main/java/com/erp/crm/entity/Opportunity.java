package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_opportunity")
public class Opportunity {

    @TableId(value = "opportunity_id", type = IdType.AUTO)
    @ExportField(label = "商机ID", order = 0, hidden = true)
    private Long opportunityId;

    @TableField("opportunity_no")
    @ExportField(label = "商机编号", order = 1, required = true)
    private String opportunityNo;

    @TableField("opportunity_name")
    @ExportField(label = "商机名称", order = 2, required = true)
    private String opportunityName;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 3, required = true)
    private Long customerId;

    @TableField("customer_name")
    @ExportField(label = "客户名称", order = 4)
    private String customerName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 5)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 6)
    private String contactPhone;

    @TableField("product_category")
    @ExportField(label = "产品类别", order = 7)
    private String productCategory;

    @TableField("product_detail")
    @ExportField(label = "产品详情", order = 8)
    private String productDetail;

    @TableField("estimated_amount")
    @ExportField(label = "预估金额", order = 9)
    private BigDecimal estimatedAmount;

    @TableField("probability")
    @ExportField(label = "成交概率(%)", order = 10)
    private Integer probability;

    @TableField("expected_close_date")
    @ExportField(label = "预计成交日期", order = 11, dateFormat = "yyyy-MM-dd")
    private LocalDate expectedCloseDate;

    @TableField("actual_close_date")
    @ExportField(label = "实际成交日期", order = 12, dateFormat = "yyyy-MM-dd")
    private LocalDate actualCloseDate;

    @TableField("sales_stage")
    @ExportField(label = "销售阶段", order = 13)
    private String salesStage;

    @TableField("stage_update_date")
    @ExportField(label = "阶段更新日期", order = 14, dateFormat = "yyyy-MM-dd")
    private LocalDate stageUpdateDate;

    @TableField("competitor")
    @ExportField(label = "竞争对手", order = 15)
    private String competitor;

    @TableField("win_reason")
    @ExportField(label = "赢单原因", order = 16)
    private String winReason;

    @TableField("lose_reason")
    @ExportField(label = "输单原因", order = 17)
    private String loseReason;

    @TableField("source_lead_id")
    @ExportField(label = "来源线索ID", order = 18)
    private Long sourceLeadId;

    @TableField("sale_order_id")
    @ExportField(label = "成交订单ID", order = 19, hidden = true)
    private Long saleOrderId;

    @TableField("sale_order_no")
    @ExportField(label = "成交订单号", order = 20)
    private String saleOrderNo;

    @TableField("actual_amount")
    @ExportField(label = "实际成交金额", order = 21)
    private BigDecimal actualAmount;

    @TableField("assigned_sales_id")
    @ExportField(label = "分配销售员ID", order = 19)
    private Long assignedSalesId;

    @TableField("assigned_dept_id")
    @ExportField(label = "分配部门ID", order = 20)
    private Long assignedDeptId;

    @TableField("follow_up_count")
    @ExportField(label = "跟进次数", order = 21)
    private Integer followUpCount;

    @TableField("last_follow_up_date")
    @ExportField(label = "最后跟进日期", order = 22, dateFormat = "yyyy-MM-dd")
    private LocalDate lastFollowUpDate;

    @TableField("next_follow_up_date")
    @ExportField(label = "下次跟进日期", order = 23, dateFormat = "yyyy-MM-dd")
    private LocalDate nextFollowUpDate;

    @TableField("is_closed")
    @ExportField(label = "是否已关闭", order = 24)
    private Integer isClosed;

    @TableField("close_date")
    @ExportField(label = "关闭日期", order = 25, dateFormat = "yyyy-MM-dd")
    private LocalDate closeDate;

    @TableField("remark")
    @ExportField(label = "备注", order = 26)
    private String remark;

    @TableField("creator_id")
    @ExportField(label = "创建人ID", order = 100, hidden = true)
    private Long creatorId;

    @TableField(value = "create_date", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 101, hidden = true)
    private LocalDateTime createDate;

    /** 创建人（数据权限过滤维度，AuditMetaObjectHandler 自动填充） */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField("modifier_id")
    @ExportField(label = "修改人ID", order = 102, hidden = true)
    private Long modifierId;

    @TableField(value = "modify_date", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "修改时间", order = 103, hidden = true)
    private LocalDateTime modifyDate;
}