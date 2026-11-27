package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单用料明细 - 对齐 PG production_order_item 表
 */
@Data
@TableName("production_order_item")
public class WorkOrderMaterial extends com.erp.common.bill.BillItemUnitFields {

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

    @TableField("bom_quantity")
    private BigDecimal bomQuantity;

    @TableField("required_quantity")
    private BigDecimal requiredQuantity;

    @TableField("issued_quantity")
    private BigDecimal issuedQuantity;

    @TableField("returned_quantity")
    private BigDecimal returnedQuantity;

    @TableField("consumed_quantity")
    private BigDecimal consumedQuantity;

    @TableField("scrap_quantity")
    private BigDecimal scrapQuantity;

    /** 供应类型：领料/倒冲/虚拟件/委外 */
    @TableField("supply_type")
    private String supplyType;

    /** 工序号 */
    @TableField("process_no")
    private String processNo;

    @TableField("unit_cost")
    private BigDecimal unitCost;

    @TableField("total_cost")
    private BigDecimal totalCost;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由「单价/单位成本」换算，2026-10-09 计税口径铺开） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由「单价/单位成本」换算，2026-10-09 计税口径铺开） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    /**
     * 【2026-11-10】归属成品行主键（work_order_product.id）。
     * 多成品行各自一套用料时用它记录归属：此前把归属塞进主键 order_item_id，
     * 成品表与原料表主键序列独立、值域重合会撞主键（保存报「数据重复」），故改为独立非主键列。
     */
    @TableField("order_product_id")
    private Long orderProductId;

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

    /**
     * 【2026-11-07】下推占用数量（非库字段）：该用料行已下推的生产领料数量，
     * 口径 = 下游草稿 + 待审核 + 已生效 − 已作废。用于选单弹窗「已下推/剩余」，
     * 与「已领料（issued_quantity，仅生效后累加）」分开，不混用。
     */
    @TableField(exist = false)
    private BigDecimal pushedQuantity;
}
