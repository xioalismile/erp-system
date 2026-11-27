package com.erp.produce.assemble.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.common.bill.BillItemUnitFields;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 生产拆卸单明细表-成品出库
 *
 * <p>2026-11-10 接入多单位换算：继承 {@link BillItemUnitFields}，
 * 补 conversion_rate / base_quantity / float_unit_id / float_quantity 四个折算字段（界面不新增隐藏列）。</p>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@TableName("produce_disassemble_detail")
public class ProduceDisassembleDetail extends BillItemUnitFields {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("disassemble_id")
    private Long disassembleId;

    @TableField("row_id")
    private String rowId;

    @TableField("product_id")
    private Long productId;

    @TableField("product_code")
    private String productCode;

    @TableField("product_name")
    private String productName;

    @TableField("specification")
    private String specification;

    @TableField("unit")
    private String unit;

    @TableField("unit_id")
    private Long unitId;

    @TableField("basic_unit")
    private String basicUnit;

    @TableField("basic_unit_id")
    private Long basicUnitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("basic_quantity")
    private BigDecimal basicQuantity;

    @TableField("wh_id")
    private Long whId;

    @TableField("wh_name")
    private String whName;

    @TableField("storage_id")
    private Long storageId;

    @TableField("storage_name")
    private String storageName;

    @TableField("price")
    private BigDecimal price;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由「单价」换算，2026-10-09 计税口径铺开） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由「单价」换算，2026-10-09 计税口径铺开） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_item_id")
    private Long sourceItemId;

    @TableField("source_serial_no")
    private String sourceSerialNo;

    @TableField("notes")
    private String notes;

    @TableField("sort_order")
    private Integer sortOrder;

    /** 旧的主单位数量(用于撤销时恢复) */
    @TableField(exist = false)
    private BigDecimal oldBasicQuantity;
}