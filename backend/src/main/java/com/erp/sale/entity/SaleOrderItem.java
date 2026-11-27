package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sale_order_item")
public class SaleOrderItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "order_item_id", type = IdType.AUTO)
    private Long orderItemId;

    @TableField("order_id")
    private Long orderId;

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
    private BigDecimal quantity;

    @TableField("delivered_quantity")
    private BigDecimal deliveredQuantity;

    /** 行级已收金额（明细收款/发货收款回写分摊；2026-10-12 销售订单收款情况） */
    @TableField("received_amount")
    private BigDecimal receivedAmount;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 折扣率(%) */
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 折扣额 */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("tax_type")
    private Integer taxType;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("avg_cost")
    private BigDecimal avgCost;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("delivery_date")
    private LocalDate deliveryDate;

    @TableField("delivery_days")
    private Integer deliveryDays;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField(exist = false)
    private String warehouseName;

    @TableField("bin_id")
    private Long binId;

    @TableField(exist = false)
    private String binName;

    @TableField("batch_no")
    private String batchNo;

    /** 行级 BOM（2026-11-06）：按物料主 BOM 自动带出，销售不可改 */
    @TableField("bom_id")
    private Long bomId;

    @TableField("bom_code")
    private String bomCode;

    @TableField("bom_version")
    private String bomVersion;

    @TableField("delivered_line_no")
    private Integer deliveredLineNo;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_item_id")
    private Long sourceItemId;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("source_type")
    private String sourceType;

    @TableField("detail_close_status")
    private Integer detailCloseStatus;

    @TableField("turned_output_quantity")
    private BigDecimal turnedOutputQuantity;

    @TableField("turned_return_quantity")
    private BigDecimal turnedReturnQuantity;

    @TableField("turned_invoice_quantity")
    private BigDecimal turnedInvoiceQuantity;

    /** 行级已开票金额（已生效发票金额累计；发票生效 +net_amount、重置草稿 -net_amount；完结口径「销售发票」模式判定用） */
    @TableField("turned_invoice_amount")
    private BigDecimal turnedInvoiceAmount;

    @TableField("turned_produce_quantity")
    private BigDecimal turnedProduceQuantity;

    @TableField("turned_subcontract_quantity")
    private BigDecimal turnedSubcontractQuantity;

    @TableField("base_unit_price")
    private BigDecimal baseUnitPrice;

    @TableField("base_amount")
    private BigDecimal baseAmount;

    @TableField("base_tax_amount")
    private BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    private BigDecimal baseNetAmount;

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

    /** 替换发货信息（非数据库字段）："C×10（换货单 SEXC-xxx）" 多行拼接，供订单明细只读展示 */
    @TableField(exist = false)
    private String replaceDeliveryInfo;

    /** 本行待回库数量（非数据库字段，P > 0 时提示） */
    @TableField(exist = false)
    private java.math.BigDecimal pendingReturnQuantity;

    /** 替换发货指示（非数据库字段）：销售在订单行上预先指定替代品/数量 */
    @TableField(exist = false)
    private List<SaleOrderSubstitutePlan> substitutePlans;
}
