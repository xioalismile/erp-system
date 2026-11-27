package com.erp.qc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 入库单质检保存请求（2026-11-07）。
 *
 * <p>一张入库单一条质检记录，items 按入库明细行一一对应；
 * 每行「合格数量 + 不合格数量 = 送检数量」，有不合格数量必须选择处理方式。</p>
 */
@Data
public class InboundQcSaveRequest {

    /** 单据类型：PURCHASE_INBOUND / PRODUCTION_RECEIPT / SUBCONTRACT_INBOUND */
    private String billType;

    /** 入库单主键 */
    private Long billId;

    /** 使用的单据级质检模板ID */
    private Long templateId;

    /** 质检人（为空取当前登录人） */
    private String inspectorName;

    /** 整单质检备注 */
    private String remark;

    private List<Item> items;

    @Data
    public static class Item {
        /** 入库单明细行主键 */
        private Long sourceItemId;
        private Integer lineNo;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String specification;
        private Long unitId;
        /** 送检数量 */
        private BigDecimal quantity;
        /** 合格数量 */
        private BigDecimal qualifiedQuantity;
        /** 不合格数量 */
        private BigDecimal rejectedQuantity;
        /** 不合格处理方式：RELEASE / RETURN / SCRAP */
        private String handlingType;
        /** 报废仓ID（处理方式=SCRAP 时可空，为空用默认报废仓） */
        private Long scrapWarehouseId;
        private String scrapWarehouseName;
        /** 不合格原因 */
        private String rejectReason;
        /** 行备注 */
        private String remark;
        /** 模板自定义字段填写值（JSON 文本，键 = 检验项名称） */
        private String extData;
    }
}
