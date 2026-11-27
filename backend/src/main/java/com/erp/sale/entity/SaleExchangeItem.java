package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 销售换货单明细：每行同时记录原货品（快照）与替换货品，以及回写订单原货行的来源链。
 */
@Data
@TableName("sale_exchange_item")
public class SaleExchangeItem {

    @TableId(value = "exchange_item_id", type = IdType.AUTO)
    private Long exchangeItemId;

    @TableField("exchange_id")
    private Long exchangeId;

    @TableField("line_no")
    private Integer lineNo;

    // ===== 原货品快照（只读） =====
    @TableField("origin_material_id")
    private Long originMaterialId;

    @TableField("origin_material_code")
    private String originMaterialCode;

    @TableField("origin_material_name")
    private String originMaterialName;

    @TableField("origin_specification")
    private String originSpecification;

    @TableField("origin_unit_id")
    private Long originUnitId;

    @TableField("origin_unit")
    private String originUnit;

    @TableField("origin_unit_price")
    private BigDecimal originUnitPrice;

    // ===== 替换货品 =====
    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit_id")
    private Long unitId;

    @TableField("unit")
    private String unit;

    @TableField("material_main_image")
    private String materialMainImage;

    // ===== 数量与金额 =====
    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("tax_rate")
    private BigDecimal taxRate = new BigDecimal("13");

    @TableField("tax_type")
    private Integer taxType;

    @TableField("discount_rate")
    private BigDecimal discountRate;

    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    // ===== 来源链（回写订单原货行的唯一锚点） =====
    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

    @TableField("source_item_id")
    private Long sourceItemId;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("source_delivery_no")
    private String sourceDeliveryNo;

    @TableField("source_delivery_id")
    private Long sourceDeliveryId;

    @TableField("source_delivery_item_id")
    private Long sourceDeliveryItemId;

    // ===== 路线与冲抵台账 =====
    /** A = 先补发后退货；B = 先退货后补发（系统自动判定，不可手改） */
    @TableField("route_type")
    private String routeType;

    @TableField("linked_return_id")
    private Long linkedReturnId;

    @TableField("linked_return_no")
    private String linkedReturnNo;

    @TableField("linked_return_item_id")
    private Long linkedReturnItemId;

    /** 已退回原货并冲抵的数量 */
    @TableField("returned_quantity")
    private BigDecimal returnedQuantity;

    /** 已退回替换货的数量（不冲抵待退回） */
    @TableField("substitute_returned_quantity")
    private BigDecimal substituteReturnedQuantity;

    /** 待退回数量（A 路线：已补发、客户尚未退回原货） */
    @TableField("pending_return_quantity")
    private BigDecimal pendingReturnQuantity;

    /** 人工结案时放弃的待退回数量 */
    @TableField("waived_quantity")
    private BigDecimal waivedQuantity;

    @TableField("waived_by")
    private String waivedBy;

    @TableField("waived_at")
    private LocalDateTime waivedAt;

    @TableField("waived_reason")
    private String waivedReason;

    /** 已下推出库数量（防重复生成） */
    @TableField("turned_delivery_quantity")
    private BigDecimal turnedDeliveryQuantity;

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

    /** 明细行自定义字段值（非数据库字段），随明细行 id 存取 */
    @TableField(exist = false)
    private Map<String, Object> customFieldValues;
}
