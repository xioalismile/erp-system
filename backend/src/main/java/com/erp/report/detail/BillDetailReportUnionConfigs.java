package com.erp.report.detail;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 多张明细表合并成一张"全明细"的配置入口（2026-11-27 列表页明细模式适配）。
 *
 * <p>只用于「编辑页有两张明细」的单据；单明细单据仍走原有配置，行为完全不变。
 * 每个方法返回一份普通 {@link BillDetailReportConfig}，由报表 Service 交给原引擎执行。</p>
 */
public final class BillDetailReportUnionConfigs {

    private BillDetailReportUnionConfigs() {}

    /** 生产工单：成品明细 + 原料明细 */
    public static BillDetailReportConfig workOrder(JdbcTemplate jdbcTemplate) {
        return BillDetailReportUnion.build(jdbcTemplate, List.of(
                new BillDetailReportUnion.Source("成品", "product",
                        ProductionBillDetailReportConfigs.workOrderProductConfig()),
                new BillDetailReportUnion.Source("原料", "material",
                        ProductionBillDetailReportConfigs.workOrderMaterialConfig())
        ));
    }

    /** 生产组装单：成品明细 + 用料明细 */
    public static BillDetailReportConfig assemble(JdbcTemplate jdbcTemplate) {
        return BillDetailReportUnion.build(jdbcTemplate, List.of(
                new BillDetailReportUnion.Source("成品", "product",
                        ProductionBillDetailReportConfigs.assembleProductConfig()),
                new BillDetailReportUnion.Source("用料", "bom",
                        ProductionBillDetailReportConfigs.assembleBomConfig())
        ));
    }

    /** 生产拆卸单：成品明细 + 用料明细 */
    public static BillDetailReportConfig disassemble(JdbcTemplate jdbcTemplate) {
        return BillDetailReportUnion.build(jdbcTemplate, List.of(
                new BillDetailReportUnion.Source("成品", "product",
                        ProductionBillDetailReportConfigs.disassembleProductConfig()),
                new BillDetailReportUnion.Source("用料", "bom",
                        ProductionBillDetailReportConfigs.disassembleBomConfig())
        ));
    }

    /** 收款单：核销明细 + 收款明细 */
    public static BillDetailReportConfig receipt(JdbcTemplate jdbcTemplate) {
        return BillDetailReportUnion.build(jdbcTemplate, List.of(
                new BillDetailReportUnion.Source("核销", "writeoff",
                        FinanceBillDetailReportConfigs.receiptConfig()),
                new BillDetailReportUnion.Source("收款", "receipt",
                        FinanceBillDetailReportConfigs.receiptItemConfig())
        ));
    }

    /** 付款单：核销明细 + 付款明细 */
    public static BillDetailReportConfig voucher(JdbcTemplate jdbcTemplate) {
        return BillDetailReportUnion.build(jdbcTemplate, List.of(
                new BillDetailReportUnion.Source("核销", "writeoff",
                        FinanceBillDetailReportConfigs.voucherConfig()),
                new BillDetailReportUnion.Source("付款", "payment",
                        FinanceBillDetailReportConfigs.paymentItemConfig())
        ));
    }

    /** 费用分摊单：费用明细（表头 JSON 展开） + 分摊明细 */
    public static BillDetailReportConfig expense(JdbcTemplate jdbcTemplate) {
        return BillDetailReportUnion.build(jdbcTemplate, List.of(
                new BillDetailReportUnion.Source("费用", "expense",
                        FinanceBillDetailReportConfigs.expenseLinesConfig()),
                new BillDetailReportUnion.Source("分摊", "allocation",
                        FinanceBillDetailReportConfigs.operationExpenseConfig())
        ));
    }

    /** 售后维修单：维修明细 + 配件领用（2026-11-27 补：列表页此前没有明细模式） */
    public static BillDetailReportConfig repair(JdbcTemplate jdbcTemplate) {
        return BillDetailReportUnion.build(jdbcTemplate, List.of(
                new BillDetailReportUnion.Source("维修", "item",
                        SaleRepairBillDetailReportConfigs.repairItemConfig()),
                new BillDetailReportUnion.Source("配件", "part",
                        SaleRepairBillDetailReportConfigs.repairPartConfig())
        ));
    }
}
