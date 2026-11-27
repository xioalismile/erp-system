package com.erp.produce.assemble.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产拆卸单主表实体类
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@TableName("produce_disassemble")
public class ProduceDisassemble {

    @TableId(value = "id", type = IdType.AUTO)
    @ExportField(label = "拆卸单ID", order = 0, hidden = true)
    private Long id;

    @TableField("serial_no")
    @ExportField(label = "拆卸单号", order = 1, required = true)
    private String serialNo;

    @TableField("dept")
    @ExportField(label = "部门", order = 3)
    private String dept;

    @TableField("wh_id")
    private Long whId;

    @TableField("wh_name")
    @ExportField(label = "仓库", order = 4)
    private String whName;

    @TableField("wh_id_bom")
    private Long whIdBom;

    @TableField("wh_name_bom")
    @ExportField(label = "BOM仓库", order = 5)
    private String whNameBom;

    @TableField("is_tax")
    private Integer isTax;

    @TableField("tax_type")
    private Integer taxType;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("currency_rate")
    private BigDecimal currencyRate;

    @TableField("amount")
    @ExportField(label = "金额", order = 6, numberFormat = "0.00")
    private BigDecimal amount;

    @TableField("base_amount")
    @ExportField(label = "本位币金额", order = 7, numberFormat = "0.00")
    private BigDecimal baseAmount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 8, numberFormat = "0.00")
    private BigDecimal taxAmount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("tax_rate")
    @ExportField(label = "税率", order = 9)
    private BigDecimal taxRate;

    /** 表头「计税方式」（含税价 / 不含税价 / 不计税）：映射数据库列 tax_calc_method，随单据保存/回读 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    @TableField("print_num")
    private Integer printNum;

    @TableField("images")
    private String images;

    @TableField("files")
    private String files;

    @TableField("notes")
    @ExportField(label = "备注", order = 15)
    private String notes;

    @TableField("dateline")
    @ExportField(label = "单据日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDateTime dateline;

    @TableField("status")
    private Integer status;

    @TableField("flow_id")
    private String flowId;

    @TableField("flow_status")
    private String flowStatus;

    @TableField("flow_status_name")
    private String flowStatusName;

    @TableField("flow_operator")
    private String flowOperator;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    @ExportField(label = "状态", order = 10)
    private String approvalStatus;

    @TableField("etime")
    private LocalDateTime etime;

    @TableField(value = "creator", fill = FieldFill.INSERT)
    @ExportField(label = "制单人", order = 11)
    private String creator;

    @TableField(value = "ctime", fill = FieldFill.INSERT)
    private LocalDateTime ctime;

    @TableField(value = "modifier", fill = FieldFill.INSERT_UPDATE)
    private String modifier;

    @TableField(value = "utime", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime utime;

    @TableField("salesman")
    private String salesman;

    @TableField("salesman_str")
    @ExportField(label = "业务员", order = 12)
    private String salesmanStr;

    @TableField("source_type")
    @ExportField(label = "来源单类型", order = 13)
    private String sourceType;

    @TableField("source_id")
    @ExportField(label = "来源单ID", order = 14, hidden = true)
    private Long sourceId;

    /** 结案状态: 0=未结案 1=已结案 */
    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，反作废时恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 拆卸单明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<ProduceDisassembleDetail> produceDisassembleDetail;

    /** 拆卸单BOM明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<ProduceDisassembleBom> produceDisassembleBom;

    /** 创建人名称(非数据库字段) */
    @TableField(exist = false)
    private String creatorStr;

    /** 修改人名称(非数据库字段) */
    @TableField(exist = false)
    private String modifierStr;
}