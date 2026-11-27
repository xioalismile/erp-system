package com.erp.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个人工作台任务条目 DTO
 * 用于：待我审批 / 即将超期 / 我的草稿 / 应收应付跟进 等任务列表
 */
@Data
public class MyTodoDTO {
    /** 单据ID */
    private Long id;
    /** 单据类型代码：SALE_ORDER/SALE_DELIVERY/SALE_RETURN/SALE_INVOICE/PURCHASE_ORDER/PURCHASE_INBOUND/PURCHASE_RETURN/PURCHASE_INVOICE/STOCK_TRANSFER/STOCK_CHECK/WORK_ORDER/AR/AP */
    private String type;
    /** 单据类型中文名 */
    private String typeName;
    /** 单据号 */
    private String billNo;
    /** 标题（客户/供应商名 + 单据类型） */
    private String title;
    /** 对方单位（客户/供应商名） */
    private String partyName;
    /** 当前状态 */
    private String status;
    /** 审核状态 */
    private String approvalStatus;
    /** 单据日期 */
    private LocalDate billDate;
    /** 到期/交货日期 */
    private LocalDate dueDate;
    /** 剩余天数（负数=已超期，0=今天到期，正数=未来N天到期） */
    private Integer remainingDays;
    /** 超期等级：OVERDUE/URGENT/SOON/NORMAL */
    private String urgency;
    /** 金额 */
    private BigDecimal amount;
    /** 创建人 */
    private String creator;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 前端跳转路由 */
    private String routePath;
}
