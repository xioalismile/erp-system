package com.erp.production.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OperationProgressVO {

    private Long operationId;

    private Integer lineNo;

    /** 所属成品明细行ID（第10期：多商品工单里，这道工序属于哪个成品；为空=老数据/整单共用） */
    private Long orderProductId;

    /** 所属成品名称（第10期：进度流程按成品分组展示用） */
    private String productName;

    private String operationCode;

    private String operationName;

    private String workCenterName;

    private BigDecimal setupHours;

    private BigDecimal processHours;

    private BigDecimal totalHours;

    private BigDecimal planQty;

    private BigDecimal reportedQty;

    private BigDecimal qualifiedQty;

    private BigDecimal rejectedQty;

    private BigDecimal actualWorkHours;

    private String status;

    private Short isCritical;

    private Short isQcRequired;

    /** 【第 19 期】工单工序行ID（work_order_operation.id）：父子归组 + 打开工序操作面板用 */
    private Long operationRowId;

    /** 【第 19 期】父工序行ID（为空 = 顶层工序） */
    private Long parentRowId;

    /** 【第 19 期】直接子工序数（父工序显示「总·N」角标用） */
    private Integer childCount;

    /** 所属生产工单的附件（只读带入工序报工面板；图片可预览、文件可下载） */
    private String billAttachments;
}
