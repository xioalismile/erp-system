package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验记录实测明细：逐项实测值与单项判定结果。
 */
@Data
@TableName("qc_record_item")
public class QcRecordItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 所属检验记录ID */
    @TableField("record_id")
    private Long recordId;

    /** 行号 */
    @TableField("line_no")
    private Integer lineNo;

    /** 检验项目名称 */
    @TableField("item_name")
    private String itemName;

    /** 标准值 */
    @TableField("standard_value")
    private String standardValue;

    /** 实测值（文本，便于记录描述性结果） */
    @TableField("measured_value")
    private String measuredValue;

    /** 上限 */
    @TableField("upper_limit")
    private BigDecimal upperLimit;

    /** 下限 */
    @TableField("lower_limit")
    private BigDecimal lowerLimit;

    /** 单项判定：合格 / 不合格 */
    @TableField("result")
    private String result;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /**
     * 【第 19 期】留证图片：逗号分隔 URL（一个检验项可多张，复用全站上传通道）。
     * 为空 = 未留证（用户口径：逐项留证、不强制）。
     */
    @TableField("images")
    private String images;

    // ===== 入库质检（2026-11-07）：一行 = 入库单一条物料明细 =====

    /** 物料ID */
    @TableField("material_id")
    private Long materialId;

    /** 物料编码 */
    @TableField("material_code")
    private String materialCode;

    /** 物料名称 */
    @TableField("material_name")
    private String materialName;

    /** 送检数量 */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 合格数量 */
    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    /** 不合格数量 */
    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 不合格处理方式：RELEASE=放行 / RETURN=自动退货 / SCRAP=进报废仓 */
    @TableField("handling_type")
    private String handlingType;

    /** 报废仓ID（处理方式=进报废仓时） */
    @TableField("scrap_warehouse_id")
    private Long scrapWarehouseId;

    /** 报废仓名称 */
    @TableField("scrap_warehouse_name")
    private String scrapWarehouseName;

    /** 模板自定义字段填写值（JSON 文本，键 = 检验项名称） */
    @TableField("ext_data")
    private String extData;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
