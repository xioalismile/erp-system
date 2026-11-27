package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运营费用分摊明细实体
 * 对应表: operation_expense_allocation
 *
 * 来源单类型: SALE_ORDER/PURCHASE_ORDER/PRODUCTION_RECEIPT/PURCHASE_INBOUND/SALE_RETURN
 * 审核费用分摊单后, 关联入库类单据(生产入库/采购入库/销售退货)的分摊明细
 * 会自动汇总到对应物料的成本核算单
 */
@Data
@TableName("operation_expense_allocation")
public class OperationExpenseAllocation {

    /** 分摊明细ID */
    @TableId(value = "allocation_id", type = IdType.AUTO)
    @ExportField(label = "分摊明细ID", order = 1, hidden = true)
    private Long allocationId;

    /** 运营费用单ID */
    @TableField("expense_id")
    @ExportField(label = "费用单ID", order = 2, hidden = true)
    private Long expenseId;

    /** 费用类型(每行可不同): 人工费用/制造费用/水电费/办公费/出差/宴请/快递/其他 */
    @TableField("expense_type")
    @ExportField(label = "分摊费用类型", order = 3)
    private String expenseType;

    /** 分摊基数(数量/金额等, 按分摊方式自动填写, 可手动调整) */
    @TableField("allocation_base")
    @ExportField(label = "分摊基数", order = 4, numberFormat = "0.00")
    private BigDecimal allocationBase;

    /** 分摊比例(%) */
    @TableField("allocation_ratio")
    @ExportField(label = "分摊比例(%)", order = 5, numberFormat = "0.00")
    private BigDecimal allocationRatio;

    /** 来源单类型: SALE_ORDER/PURCHASE_ORDER/PRODUCTION_RECEIPT/PURCHASE_INBOUND/SALE_RETURN */
    @TableField("order_type")
    @ExportField(label = "来源单类型", order = 6)
    private String orderType;

    /** 来源单ID */
    @TableField("order_id")
    @ExportField(label = "来源单ID", order = 7, hidden = true)
    private Long orderId;

    /** 来源单编号 */
    @TableField("order_no")
    @ExportField(label = "来源单编号", order = 8)
    private String orderNo;

    /** 分摊金额 */
    @TableField("amount")
    @ExportField(label = "分摊金额", order = 9, numberFormat = "0.00", required = true)
    private BigDecimal amount;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "明细备注", order = 10)
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
