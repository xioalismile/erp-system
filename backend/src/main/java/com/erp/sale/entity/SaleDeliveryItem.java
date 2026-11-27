package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("sale_delivery_item")
public class SaleDeliveryItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "delivery_item_id", type = IdType.AUTO)
    private Long deliveryItemId;

    @TableField("delivery_id")
    private Long deliveryId;

    @TableField("line_no")
    private Integer lineNo;

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

    /** 单位名称(非数据库字段,仅用于前端展示) */
    @TableField(exist = false)
    private String unit;

    @TableField("quantity")
    private java.math.BigDecimal quantity;

    @TableField("unit_price")
    private java.math.BigDecimal unitPrice;

    /** 折扣率(%) */
    @TableField("discount_rate")
    private java.math.BigDecimal discountRate;

    /** 折扣额 */
    @TableField("discount_amount")
    private java.math.BigDecimal discountAmount;

    @TableField("tax_rate")
    private java.math.BigDecimal taxRate;

    @TableField("tax_type")
    private Integer taxType;

    @TableField("amount")
    private java.math.BigDecimal amount;

    @TableField("avg_cost")
    private BigDecimal avgCost;

    @TableField("tax_amount")
    private java.math.BigDecimal taxAmount;

    @TableField("net_amount")
    private java.math.BigDecimal netAmount;

    /** 含税价（按行级/表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private java.math.BigDecimal priceWithTax;

    /** 不含税价（按行级/表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private java.math.BigDecimal priceWithoutTax;

    /** 行级计税方式（价内税/价外税/不计税）：销售出库单为行级口径，传输字段不落库，保存时用于换算两列 */
    @TableField(exist = false)
    private String taxCalcMethod;

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    @TableField("batch_no")
    private String batchNo;

    /** 行级 BOM（2026-11-06）：按物料主 BOM 自动带出 */
    @TableField("bom_id")
    private Long bomId;

    @TableField("bom_code")
    private String bomCode;

    @TableField("bom_version")
    private String bomVersion;

    /** 序列号, 序列号管理物料必填 */
    @TableField("serial_no")
    private String serialNo;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("order_line_no")
    private Integer orderLineNo;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_item_id")
    private Long sourceItemId;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("source_type")
    private String sourceType;

    /** 已开票数量（被销售发票引用的累计数量） */
    @TableField("turned_invoice_quantity")
    private java.math.BigDecimal turnedInvoiceQuantity;

    @TableField("base_unit_price")
    private java.math.BigDecimal baseUnitPrice;

    @TableField("base_amount")
    private java.math.BigDecimal baseAmount;

    @TableField("base_tax_amount")
    private java.math.BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    private java.math.BigDecimal baseNetAmount;

    @TableField("remark")
    private String remark;

    // ===== 替换货标记（2026-11-17 替换货需求） =====
    /** 替换自的原货品主键（非替换行为 null） */
    @TableField("replaced_from_material_id")
    private Long replacedFromMaterialId;

    @TableField("replaced_from_material_code")
    private String replacedFromMaterialCode;

    @TableField("replaced_from_material_name")
    private String replacedFromMaterialName;

    /** 回写订单行成本前的快照（仅替换行，用于回退镜像恢复） */
    @TableField("order_cost_before")
    private java.math.BigDecimal orderCostBefore;

    /** 替换发货指示执行行：来源指示主键；仓库直接替代为空 */
    @TableField("replace_plan_id")
    private Long replacePlanId;

    // ===== 非持久化：来源订单行原货品 / 该行是否有待执行销售指示 =====
    @TableField(exist = false)
    private Long sourceMaterialId;

    @TableField(exist = false)
    private String sourceMaterialCode;

    @TableField(exist = false)
    private String sourceMaterialName;

    /** 出库单详情回显：该订单行是否有待执行的销售替换指示（前端据此隐藏仓库替代按钮） */
    @TableField(exist = false)
    private Boolean orderHasActivePlan;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private java.time.LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private java.time.LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
