package com.erp.finance.cost.calculation.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 成本报表DTO - 当期物料成本汇总
 * 参考金蝶K3成本管理模块设计
 */
@Data
public class CostReportItem {

    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;

    /** 期初数量 */
    private BigDecimal beginQuantity;
    /** 期初金额 */
    private BigDecimal beginAmount;

    /** 本期入库数量 */
    private BigDecimal inQuantity;
    /** 本期入库金额 */
    private BigDecimal inAmount;

    /** 本期出库数量 */
    private BigDecimal outQuantity;
    /** 本期出库金额 */
    private BigDecimal outAmount;

    /** 期末数量（期初+入库-出库） */
    private BigDecimal endQuantity;
    /** 期末金额（期初金额+入库金额-出库金额） */
    private BigDecimal endAmount;

    /** 单位成本（期末金额/期末数量） */
    private BigDecimal unitCost;

    /** 直接材料 */
    private BigDecimal directMaterialCost;
    /** 直接人工 */
    private BigDecimal directLaborCost;
    /** 制造费用 */
    private BigDecimal manufacturingOverhead;
}
