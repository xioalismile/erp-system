package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存调拨明细(对应 stock_transfer_item 表)
 */
@Data
@TableName("stock_transfer_item")
public class StockTransferItem {

    @TableId(value = "transfer_item_id", type = IdType.AUTO)
    private Long transferItemId;

    @TableField("transfer_id")
    private Long transferId;

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

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("from_stock_id")
    private Long fromStockId;

    @TableField("from_bin_id")
    private Long fromBinId;

    @TableField("to_stock_id")
    private Long toStockId;

    @TableField("to_bin_id")
    private Long toBinId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
