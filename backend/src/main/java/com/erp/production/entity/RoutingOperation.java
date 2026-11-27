package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工艺路线工序明细 - 对齐 PG routing_operation 表
 */
@Data
@TableName("routing_operation")
public class RoutingOperation {

    @TableId(value = "operation_id", type = IdType.AUTO)
    private Long operationId;

    @TableField("routing_id")
    private Long routingId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("operation_code")
    private String operationCode;

    @TableField("operation_name")
    private String operationName;

    @TableField("workstation_type")
    private String workstationType;

    @TableField("workstation_id")
    private Long workstationId;

    @TableField("work_center_id")
    private Long workCenterId;

    /** 工作中心名称 */
    @TableField("work_center_name")
    private String workCenterName;

    /** 是否委外 */
    @TableField("is_outsourced")
    private Short isOutsourced;

    @TableField("setup_hours")
    private BigDecimal setupHours;

    @TableField("process_hours")
    private BigDecimal processHours;

    /**
     * 【工时体系 2026-10-07】单件加工工时（小时）。
     * 默认带出该物料的「单件标准工时」，可手工改。
     * 工单下达生成工序实例时：unitHours &gt; 0 → work_order_operation.process_hours = unitHours × 该成品行计划数量；
     * unitHours 为 0/空 → 沿用原 processHours（老行为，零破坏）。
     */
    @TableField("unit_hours")
    private BigDecimal unitHours;

    @TableField("wait_hours")
    private BigDecimal waitHours;

    @TableField("move_hours")
    private BigDecimal moveHours;

    @TableField("total_hours")
    private BigDecimal totalHours;

    @TableField("batch_size")
    private Integer batchSize;

    @TableField("workers_required")
    private Integer workersRequired;

    /** 前序工序号 */
    @TableField("prev_process_no")
    private String prevProcessNo;

    @TableField("is_qc_required")
    private Short isQcRequired;

    @TableField("is_critical")
    private Short isCritical;

    /**
     * 工序类型：状态类型 / 数量类型 / 入库类型 / 金额类型。
     * 决定该工序在工单上"点进去能做什么"（操作面板按类型渲染）。
     */
    @TableField("operation_type")
    private String operationType;

    /** 计件单价（单件工资），用于工序工资核算；0 = 计件单价未配置 */
    @TableField("piece_rate")
    private BigDecimal pieceRate;

    /**
     * 【第 13 期】父工序行号：指向本工艺路线内另一道工序的 line_no。
     * 为空 = 顶层工序；父子可任意层级，但必须同一路线内，且不允许自引用/成环。
     */
    @TableField("parent_line_no")
    private Integer parentLineNo;

    /** 【第 13 期】计划金额（仅「金额类型」工序可填；其它类型留空） */
    @TableField("plan_amount")
    private BigDecimal planAmount;

    /** 含税价（按表头「计税方式」由「计件单价」换算，2026-10-09 计税口径铺开） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由「计件单价」换算，2026-10-09 计税口径铺开） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    /**
     * 【2026-10-09】本道工序「谁可以改负责人」：全部人 / 当前负责人 / 指定角色 / 工单开单人。
     * 空 = 回落所属工艺路线设置，再回落「全部人」（= 升级前行为）。
     * 由「工艺路线 → 工序明细」逐行设置；工单上每道工序按各自的值判定，互不影响。
     */
    @TableField("assign_scope")
    private String assignScope;

    /** 【2026-10-09】选「指定角色」时的角色编码清单（逗号分隔 sys_role.role_code；仅该档位使用） */
    @TableField("assign_role_ids")
    private String assignRoleIds;

    /**
     * 【2026-10-25】本道工序是否允许扫码「我来做」（是/否，默认/空 = 是）。
     * 关闭后扫码只能看，不能认领；不影响系统派工与管理员改派。
     */
    @TableField("allow_scan_claim")
    private String allowScanClaim;

    /** 【2026-11-26】工序稳定标识：工艺路线保存是全删全插，工序自定义字段必须绑这个 key 才能不丢 */
    @TableField("operation_key")
    private String operationKey;

    /** 【第 13 期】看板/明细非库字段：父工序名称（按 parent_line_no 解析后回显，便于列表直接显示） */
    @TableField(exist = false)
    private String parentOperationName;

    /** 【2026-11-26】本工序自己的自定义字段定义（非库字段，存 routing_operation_field） */
    @TableField(exist = false)
    private List<RoutingOperationField> fieldDefs;

    @TableField("next_operation_line_no")
    private Integer nextOperationLineNo;

    @TableField("previous_operation_line_no")
    private Integer previousOperationLineNo;

    /** 工序计划完成时间 */
    @TableField("plan_end_date")
    private LocalDate planEndDate;

    /** 工序实际完成时间 */
    @TableField("actual_end_date")
    private LocalDate actualEndDate;

    /** 工序状态：待开始/进行中/已完成 */
    @TableField("operation_status")
    private String operationStatus;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
