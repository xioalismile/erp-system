package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工艺路线主表 - 对齐 PG routing 表
 */
@Data
@TableName("routing")
public class Routing {

    @TableId(value = "routing_id", type = IdType.AUTO)
    @ExportField(label = "工艺路线ID", order = 0, hidden = true)
    private Long routingId;

    @TableField("routing_code")
    @ExportField(label = "工艺路线编码", order = 1, required = true)
    private String routingCode;

    @TableField("routing_name")
    @ExportField(label = "工艺路线名称", order = 2, required = true)
    private String routingName;

    @TableField("routing_version")
    @ExportField(label = "版本", order = 3)
    private String routingVersion;

    @TableField("material_id")
    @ExportField(label = "物料ID", order = 4, hidden = true)
    private Long materialId;

    /** 物料编码 */
    @TableField("material_code")
    @ExportField(label = "物料编码", order = 5)
    private String materialCode;

    /** 物料名称 */
    @TableField("material_name")
    @ExportField(label = "物料名称", order = 6)
    private String materialName;

    @TableField("bom_id")
    @ExportField(label = "BOM ID", order = 7, hidden = true)
    private Long bomId;

    @TableField("is_primary")
    @ExportField(label = "是否主工艺", order = 8)
    private Short isPrimary;

    @TableField("is_enabled")
    @ExportField(label = "是否启用", order = 9)
    private Short isEnabled;

    @TableField("effective_date")
    @ExportField(label = "生效日期", order = 10, dateFormat = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    @TableField("expiry_date")
    @ExportField(label = "失效日期", order = 11, dateFormat = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 工序总数 */
    @TableField("total_process_count")
    @ExportField(label = "工序总数", order = 12)
    private Integer totalProcessCount;

    @TableField("total_work_hours")
    @ExportField(label = "总工时", order = 13)
    private BigDecimal totalWorkHours;

    @TableField("remark")
    @ExportField(label = "备注", order = 14)
    private String remark;

    @TableField("status")
    @ExportField(label = "状态", order = 15)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 16)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 17)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 18, dateFormat = "yyyy-MM-dd")
    private LocalDateTime approvedAt;

    /** 【2026-09-28】自动通知下道工序负责人：1=本路线工序完工/检验通过时自动给下道工序负责人发系统通知；0=不发（默认关） */
    @TableField("auto_notify_next")
    @ExportField(label = "自动通知下道工序负责人", order = 19)
    private Short autoNotifyNext;

    /** 【2026-09-28】按顺序执行：1=前道工序未完工时后道工序不能开工；0=不限制（默认关） */
    @TableField("enforce_sequence")
    @ExportField(label = "按顺序执行", order = 20)
    private Short enforceSequence;

    /**
     * 【第 2 版 2026-10-07】投入自动带出上道产出：1=工序操作面板「本次投入」自动填上道工序的产出数量
     * （第一道无上道则填计划数量）；0=默认填计划数量。**默认 1（开）**，用户明确要求。
     */
    @TableField("auto_input_from_prev")
    @ExportField(label = "投入自动带出上道产出", order = 21)
    private Short autoInputFromPrev;

    /**
     * 【第 4 版 2026-10-08】谁可以改工序负责人：全部人 / 当前负责人 / 指定角色 / 工单开单人。
     * 默认「全部人」＝升级前"谁都能改负责人"，老数据升级后行为不变。
     */
    @TableField("assign_scope")
    @ExportField(label = "谁可以改负责人", order = 22)
    private String assignScope;

    /** 【第 4 版 2026-10-08】选「指定角色」时的角色编码清单（逗号分隔 sys_role.role_code；仅该档位使用） */
    @TableField("assign_role_ids")
    @ExportField(label = "可改负责人的角色", order = 23)
    private String assignRoleIds;

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

    /** 工序明细列表(非数据库字段) */
    @TableField(exist = false)
    @ExportField(label = "工序明细", order = 104, hidden = true)
    private List<RoutingOperation> operations;

    /**
     * 【2026-11-26】适用物料多选：一个工艺路线可绑定多个物料。
     * 存 routing_material 关联表；主表 material_id/material_code/material_name 保留为「第一个物料」兼容老逻辑。
     */
    @TableField(exist = false)
    private List<Long> materialIds;

    /** 适用物料明细（id / code / name），列表与编辑页回显用 */
    @TableField(exist = false)
    private List<Map<String, Object>> materials;

    /** 适用物料的展示文本（导出用：名称(编码)、名称(编码)） */
    @TableField(exist = false)
    private String materialNames;

    /** 适用分类多选：该分类及下级分类下的物料都适用 */
    @TableField(exist = false)
    private List<Long> categoryIds;

    /** 适用分类明细（id / name / fullName） */
    @TableField(exist = false)
    private List<Map<String, Object>> categories;

    /** 表头「计税方式」（含税价 / 不含税价 / 不计税）：映射数据库列 tax_calc_method，随单据保存/回读 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;
}
