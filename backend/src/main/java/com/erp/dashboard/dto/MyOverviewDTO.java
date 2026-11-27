package com.erp.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 个人工作台顶部统计卡片 DTO
 */
@Data
public class MyOverviewDTO {
    /** 待我审批数 */
    private Integer pendingApprovalCount;
    /** 即将超期数（含已超期） */
    private Integer overdueCount;
    /** 我的草稿数 */
    private Integer myDraftCount;
    /** 应收应付跟进数（已生效未结清且过到期日） */
    private Integer arApFollowUpCount;
    /** 我的销售金额（销售员可见，本月） */
    private BigDecimal mySaleAmount;
    /** 我的采购金额（采购员可见，本月） */
    private BigDecimal myPurchaseAmount;
    /** 库存预警数（库管可见） */
    private Integer inventoryWarningCount;
}
