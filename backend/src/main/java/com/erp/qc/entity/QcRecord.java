package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工序检验记录：一次检验一行（首检 / 抽检 / 全检），含判定结果与不合格处理方式。
 */
@Data
@TableName("qc_record")
public class QcRecord {

    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;

    /** 检验单号（自动生成） */
    @TableField("record_no")
    private String recordNo;

    /** 工单ID */
    @TableField("order_id")
    private Long orderId;

    /** 工单号 */
    @TableField("order_no")
    private String orderNo;

    /** 关联工单工序行ID（work_order_operation.id） */
    @TableField("operation_row_id")
    private Long operationRowId;

    /** 工序名称 */
    @TableField("operation_name")
    private String operationName;

    /** 使用的质检模板ID */
    @TableField("template_id")
    private Long templateId;

    /** 质检类型：首检 / 抽检 / 全检 */
    @TableField("qc_type")
    private String qcType;

    /** 检验数量（首检=报工数量；抽检=抽样数；全检=报工数量） */
    @TableField("inspect_qty")
    private BigDecimal inspectQty;

    /** 判定合格数（= 写入 work_order_operation.approve_qty，供下道卡量） */
    @TableField("qualified_qty")
    private BigDecimal qualifiedQty;

    /** 判定不合格数（进不合格处理） */
    @TableField("defect_qty")
    private BigDecimal defectQty;

    /** 判定结果：合格 / 不合格 */
    @TableField("result")
    private String result;

    /** 不合格处理方式：补料 / 返工回退 / 报废（合格时为空） */
    @TableField("handle_type")
    private String handleType;

    /** 返工回退目标工序名称（仅返工回退时填写） */
    @TableField("handle_operation_name")
    private String handleOperationName;

    /** 不合格处理备注 */
    @TableField("handle_remark")
    private String handleRemark;

    /** 检验人ID */
    @TableField("inspector_id")
    private Long inspectorId;

    /** 检验人姓名 */
    @TableField("inspector_name")
    private String inspectorName;

    /** 检验时间 */
    @TableField("inspect_time")
    private LocalDateTime inspectTime;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 检验环节：来料检/过程检/成品检（化妆品批记录扩展，默认过程检=旧行为） */
    @TableField("qc_stage")
    private String qcStage;

    /** 批次档案ID（batch_info.id），批记录链路用 */
    @TableField("batch_id")
    private Long batchId;

    /** 批号（冗余展示用，与 batch_id 同源） */
    @TableField("batch_no")
    private String batchNo;

    /** 复核人ID（电子签名后落值） */
    @TableField("reviewer_id")
    private Long reviewerId;

    /** 复核人（电子签名后落值） */
    @TableField("reviewer_name")
    private String reviewerName;

    /** 来源单据类型（入库质检用：PURCHASE_INBOUND / PRODUCTION_RECEIPT / SUBCONTRACT_INBOUND） */
    @TableField("source_bill_type")
    private String sourceBillType;

    /** 来源单据主键（入库单ID） */
    @TableField("source_bill_id")
    private Long sourceBillId;

    /** 默认报废仓ID（按行未指定时兜底） */
    @TableField("scrap_warehouse_id")
    private Long scrapWarehouseId;

    /** 默认报废仓名称 */
    @TableField("scrap_warehouse_name")
    private String scrapWarehouseName;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 实测明细（非库字段：查询记录详情时一并携带） */
    @TableField(exist = false)
    private List<QcRecordItem> items;

    /** 手写签名图（非库字段：提交检验时随 payload 传入，只用于落 signature_record） */
    @TableField(exist = false)
    private String signImage;
}
