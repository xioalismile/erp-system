package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工单工序实例 - 工单下达时基于工艺路线快照生成, 隔离不同工单的工序状态
 * 对齐 PG work_order_operation 表
 */
@Data
@TableName("work_order_operation")
public class WorkOrderOperation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 工单ID */
    @TableField("order_id")
    private Long orderId;

    /** 工单号 */
    @TableField("order_no")
    private String orderNo;

    /** 工艺路线ID */
    @TableField("routing_id")
    private Long routingId;

    /** 所属成品明细行ID（第10期：多商品工单里，这道工序属于哪个成品行；为空=老数据/整单共用） */
    @TableField("order_product_id")
    private Long orderProductId;

    /** 工序ID(来源 routing_operation.operation_id) */
    @TableField("operation_id")
    private Long operationId;

    /** 工序编码 */
    @TableField("operation_code")
    private String operationCode;

    /** 工序名称 */
    @TableField("operation_name")
    private String operationName;

    /** 行号 */
    @TableField("line_no")
    private Integer lineNo;

    /** 工作中心 */
    @TableField("work_center_id")
    private Long workCenterId;

    /** 工位 */
    @TableField("workstation_id")
    private Long workstationId;

    /** 准备工时 */
    @TableField("setup_hours")
    private BigDecimal setupHours;

    /** 加工工时 */
    @TableField("process_hours")
    private BigDecimal processHours;

    /** 计划数量 */
    @TableField("plan_quantity")
    private BigDecimal planQuantity;

    /** 已报工合格量 */
    @TableField("reported_quantity")
    private BigDecimal reportedQuantity;

    /** 报废量 */
    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 状态: 待开始/进行中/已完成 */
    @TableField("operation_status")
    private String operationStatus;

    /** 工序类型（工艺路线快照）：状态类型/数量类型/入库类型/金额类型 */
    @TableField("operation_type")
    private String operationType;

    /** 工序负责人（员工ID，派工时写入） */
    @TableField("assignee_id")
    private Long assigneeId;

    /** 工序负责人姓名（冗余，列表/看板直接显示） */
    @TableField("assignee_name")
    private String assigneeName;

    /** 投入数量（上道合格数；首道 = 工单数量） */
    @TableField("input_quantity")
    private BigDecimal inputQuantity;

    /** 产出数量（本道合格数） */
    @TableField("output_quantity")
    private BigDecimal outputQuantity;

    /** 计件单价（工艺路线快照） */
    @TableField("piece_rate")
    private BigDecimal pieceRate;

    /** 是否委外 */
    @TableField("is_outsourced")
    private Integer isOutsourced;

    /** 是否质检 */
    @TableField("is_qc_required")
    private Integer isQcRequired;

    /** 【第 12 期】本道工序绑定的质检模板ID（为空 = 不绑定，按物料/工序自动解析） */
    @TableField("qc_template_id")
    private Long qcTemplateId;

    /** 【第 12 期】质检判定出的合格数（= 下一道工序的可开工数量，下道报工/开工不得超过） */
    @TableField("approve_qty")
    private BigDecimal approveQty;

    /** 【第 12 期】本道可开工数量（= 上道 approve_qty，上道质检通过后写入） */
    @TableField("can_start_qty")
    private BigDecimal canStartQty;

    /** 是否关键工序 */
    @TableField("is_critical")
    private Integer isCritical;

    /** 计划开始日期 */
    @TableField("plan_start_date")
    private LocalDate planStartDate;

    /** 计划完成日期 */
    @TableField("plan_end_date")
    private LocalDate planEndDate;

    /** 实际开始日期 */
    @TableField("actual_start_date")
    private LocalDate actualStartDate;

    /** 实际完成日期 */
    @TableField("actual_end_date")
    private LocalDate actualEndDate;

    /** 回退/返修数量：下道工序发现不良后退回本道的数量（车间回退场景，2026-09-27 第4期） */
    @TableField("rework_quantity")
    private BigDecimal reworkQuantity;

    /** 判废数量：本道师傅判定"回退料中不能用"的数量（决定能否下推补料） */
    @TableField("scrap_quantity")
    private BigDecimal scrapQuantity;

    /** 已下推补料的判废数量（可下推量 = 判废数量 - 已下推量，保证不重复补料） */
    @TableField("scrap_pushed_quantity")
    private BigDecimal scrapPushedQuantity;

    /** 最近一次下推生成的补料领料单号（页面回显用） */
    @TableField("supplement_bill_no")
    private String supplementBillNo;

    /** 前道工序行号 */
    @TableField("prev_line_no")
    private Integer prevLineNo;

    /** 后道工序行号 */
    @TableField("next_line_no")
    private Integer nextLineNo;

    /** 【第 13 期】父工序行号（工艺路线快照）：工单生效生成时按本成品行内 line_no 解析成 parent_row_id */
    @TableField("parent_line_no")
    private Integer parentLineNo;

    /** 【第 13 期】父工序行ID（work_order_operation.id；为空 = 顶层工序）：看板据此汇总子工序进度 */
    @TableField("parent_row_id")
    private Long parentRowId;

    /** 【第 13 期】计划金额（工艺路线快照，仅「金额类型」工序） */
    @TableField("plan_amount")
    private BigDecimal planAmount;

    /** 【第 13 期】已确认金额（金额类型工序登记金额时累加；为空视为 0） */
    @TableField("confirmed_amount")
    private BigDecimal confirmedAmount;

    /** 含税价（按表头「计税方式」由「计件单价」换算，2026-10-09 计税口径铺开） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由「计件单价」换算，2026-10-09 计税口径铺开） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    /** 【第 2 版 2026-10-07】工序附件（逗号分隔文件 URL；面板上传/删除，随「保存」或任一动作落库） */
    @TableField("attachments")
    private String attachments;

    /**
     * 【工时体系 2026-10-07】工序实际工时（累计有效工时，小时）。
     * 口径：开工/恢复开始计时，暂停/完工/检验通过/取消停表并结算（now − timingSince 累加进来）；
     * 重新派工清零。跨天与放假一律靠人工点「暂停」停表，不做班次自动扣减。
     */
    @TableField("actual_hours")
    private BigDecimal actualHours;

    /** 【工时体系 2026-10-07】本段计时起点（为空 = 未在计时） */
    @TableField("timing_since")
    private LocalDateTime timingSince;

    /** 行备注（明细行的备注） */
    @TableField("remark")
    private String remark;

    /**
     * 【2026-11-25】工序自定义字段值（非库字段）：按「生产工序」自定义字段对象存取，
     * 存值仍走通用自定义字段值表；工单保存/工序动作时由服务层写入，前端只做展示与提交。
     */
    @TableField(exist = false)
    private Map<String, Object> customFields;

    /** 【2026-11-26】来源工艺路线工序标识：按此取该工序自己的自定义字段定义 */
    @TableField("routing_operation_key")
    private String routingOperationKey;

    /** 【2026-11-26】本工序自己的字段定义（非库字段，由服务层按 routing_operation_key 回填） */
    @TableField(exist = false)
    private java.util.List<Map<String, Object>> operationFieldDefs;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---------------- 车间看板（线路图）非库字段 ----------------

    /** 看板输出：工序行ID（= id，前端按此 key 读取，避免与工单ID混淆） */
    @TableField(exist = false)
    private Long operationRowId;

    /** 看板输出：工作中心名称（工单工序表无此列，需 join work_center 档案） */
    @TableField(exist = false)
    private String workCenterName;

    /** 看板输出：工单成品名称（取 production_order.material_name） */
    @TableField(exist = false)
    private String materialName;

    // ---------------- 【第 13 期】看板可视化输出（非库字段） ----------------

    /** 看板输出：已入库量（入库类型工序：按来源工序行汇总的入库单数量合计，已作废不计） */
    @TableField(exist = false)
    private BigDecimal receivedQty;

    /** 看板输出：直接子工序数（父子工序汇总节点用；无子工序时为空 = 非汇总节点） */
    @TableField(exist = false)
    private Integer childCount;

    /** 看板输出：直接子工序中已完工的条数 */
    @TableField(exist = false)
    private Integer childFinishedCount;

    /** 看板输出：子工序汇总进度(%)（只认叶子节点，按计划数量加权：Σ叶子产出 / Σ叶子计划量 × 100） */
    @TableField(exist = false)
    private BigDecimal childProgress;

    /** 看板输出：超期天数（计划完工日期已过且未完工/未取消；未超期为空） */
    @TableField(exist = false)
    private Integer overdueDays;

    /** 看板输出：商品规格（取所属成品明细行的规格） */
    @TableField(exist = false)
    private String specification;

    /** 手机端工序页：物料主图（按 materialId 取物料档案主图，非库字段） */
    @TableField(exist = false)
    private String materialImage;

    /** 【第 2 版】看板输出：物料编码（取所属成品明细行，回落整单物料）：进度跟踪表格「物料编码」列 */
    @TableField(exist = false)
    private String materialCode;

    /** 看板输出：成品物料ID（取所属成品明细行，回落整单物料）：站点设置展示「物料自定义字段」时按它取值 */
    @TableField(exist = false)
    private Long materialId;

    /** 看板输出：父工序名称（父工序可能落在另一条线路，故由后端回填） */
    @TableField(exist = false)
    private String parentOperationName;

    /** 看板输出：所属生产工单的附件（只读带入工序报工面板；图片可预览、文件可下载） */
    @TableField(exist = false)
    private String billAttachments;

    /**
     * 【第 14 期】看板输出：是否未派工。
     * 判定口径（唯一事实来源在后端，前端只渲染）：工序状态为「待派工」<b>或</b> 负责人({@code assigneeId}) 为空。
     */
    @TableField(exist = false)
    private Boolean unassigned;

    // ---------------- 【2026-10-25】扫码认领与按人报工（非库字段） ----------------

    /** 共同负责人（在岗）：主负责人不在参与人表时由服务层补一行展示 */
    @TableField(exist = false)
    private java.util.List<WorkOrderOperationWorker> workers;

    /** 本道工序是否允许扫码认领（是/否） */
    @TableField(exist = false)
    private String allowScanClaim;

    /** 当前登录人能否扫码「我来做」 */
    @TableField(exist = false)
    private Boolean canClaim;

    /** 不能认领/报工的原因（可直接展示给用户） */
    @TableField(exist = false)
    private String claimDisabledReason;

    /** 当前登录人的认领数量（未认领为空） */
    @TableField(exist = false)
    private BigDecimal myClaimQuantity;

    /** 当前登录人是否在本道工序的在岗参与人里（主负责人或已认领） */
    @TableField(exist = false)
    private Boolean myJoined;

    /** 当前登录人的累计已报 */
    @TableField(exist = false)
    private BigDecimal myReportedQuantity;

    /** 当前登录人的剩余可报（未设认领数量时为空） */
    @TableField(exist = false)
    private BigDecimal myRemainingQuantity;

    /** 本道工序还可认领多少（计划 − 已认领 − 已报） */
    @TableField(exist = false)
    private BigDecimal remainingClaimable;
}
