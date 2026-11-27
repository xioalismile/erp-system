package com.erp.export.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入/导出「单一登记表」（唯一事实来源）。
 *
 * <p><b>背景（为什么必须有这个类）</b>：改造前，一张单据要在 10 个地方各登记一次才算"支持导入导出"——
 * {@code ExcelServiceImpl.ENTITY_CLASS_MAP}、{@code getMapper()}、{@code SPECIAL_IMPORT_ENTITIES}、
 * 四个 {@code *_TEMPLATE_ENTITIES}、{@code EXPORT_ONLY_ENTITIES}、
 * {@code ExportController.importData()} 的 24 个 else-if、{@code exportTemplate()} 的 startsWith 前缀分派、
 * 前端 {@code exportUtil.js} 两张手写文件名 map。结果是"补了销售订单、漏了委外/生产"，
 * 且 {@code startsWith("operation")} 会把工序汇报单误判成财务单据。</p>
 *
 * <p><b>现在的规则（铁律）</b>：新增一张单据的导入/导出能力，<b>只允许改这一个文件</b>——
 * 在 {@code BILLS} 里加一行（code / 中文名 / 实体类 / Mapper / 模板分组 / 是否可导 / 是否可入）。
 * 其余消费方（导出表头、取数 Mapper、模板分派、导入分派、前端文件名）全部由本表派生。
 * 登记表与代码不一致时由 {@code BillRegistryTraversalTest} 遍历回归测试兜住。</p>
 */
public final class BillExcelRegistry {

    private BillExcelRegistry() {
    }

    /**
     * 模板分派分组：决定「下载模板」走哪条生成逻辑。
     *
     * <ul>
     *   <li>{@code BASE}      基础资料：列来自实体 {@code @ExportField} 反射（含自定义字段）</li>
     *   <li>{@code SALE}      销售单据：表头列 + 明细列（销售模块静态常量）</li>
     *   <li>{@code PURCHASE}  采购单据：表头列 + 明细列（采购模块静态常量）</li>
     *   <li>{@code INVENTORY} 库存单据：表头列 + 明细列（库存模块静态常量）</li>
     *   <li>{@code FINANCE}   财务单据：表头列 + 明细列（财务模块静态常量）</li>
     *   <li>{@code PRODUCTION}/{@code SUBCONTRACT}/{@code OTHER} 反射列 + 明细列（BILL_ITEM_SPECS 有登记时追加）</li>
     * </ul>
     */
    public enum Group {
        BASE, SALE, PURCHASE, INVENTORY, FINANCE, PRODUCTION, SUBCONTRACT, OTHER
    }

    /**
     * 数据权限：主档类「员工权限补充白名单」种类（与 DataScopeResolver 的四个白名单一一对应）。
     */
    public enum WhitelistKind { NONE, CUSTOMER, SUPPLIER, WAREHOUSE, MATERIAL_CATEGORY }

    /**
     * 「业务准入门槛」种类：列表页 Controller#applyExtraFilter 里那层"业务可见性"条件，
     * 导出必须逐字同口径（否则导出比列表宽 —— 历史 BUG：客户档案列表 0 条、导出却出 2 条客户）。
     */
    public enum ExtraGate {
        /** 无业务准入门槛（绝大多数实体） */
        NONE,
        /** 客户档案域准入：仅「正式客户」与历史存量（crm_status 为空）进入客户档案，与 basedata/CustomerController#applyExtraFilter 一致 */
        CUSTOMER_ARCHIVE
    }

    /**
     * 一张单据的导入导出能力声明。
     *
     * @param code                前端/接口使用的实体名（entityName），必须全局唯一
     * @param name                中文名（用于导出文件名与模板文件名）
     * @param entityClass         导出表头来源类（读取 {@code @ExportField}）
     * @param mapperClass         导出取数 Mapper（必须是 Spring 容器里的 BaseMapper bean）
     * @param group               模板分派分组
     * @param exportable          是否支持导出
     * @param importable          是否支持导入（false = 前端不显示导入入口，后端也拒绝）
     * @param scopeOwnerColumn    数据权限「归属列」（员工ID维度，如 saler_id/buyer_id/applicant_id/assigned_sales_id）；null=该表无归属列
     * @param scopeCreatedByColumn 数据权限「创建人列」（多数表 created_by；预收/预付单 create_by；费用支出单 applicant_name）；
     *                            <b>null = 该实体不套数据权限</b>（其列表页本身也未接数据权限，保持"导出与列表同口径、不新增差异"）
     * @param scopeWhitelist      数据权限白名单种类（主档类使用；其它传 NONE）
     * @param extraGate           业务准入门槛种类（列表页 applyExtraFilter 同口径；无则 NONE）
     */
    public record Bill(String code,
                       String name,
                       Class<?> entityClass,
                       Class<?> mapperClass,
                       Group group,
                       boolean exportable,
                       boolean importable,
                       String scopeOwnerColumn,
                       String scopeCreatedByColumn,
                       WhitelistKind scopeWhitelist,
                       ExtraGate extraGate) {

        /** 兼容只声明数据权限的绝大多数实体（业务准入门槛默认 NONE） */
        public Bill(String code, String name, Class<?> entityClass, Class<?> mapperClass, Group group,
                    boolean exportable, boolean importable, String scopeOwnerColumn,
                    String scopeCreatedByColumn, WhitelistKind scopeWhitelist) {
            this(code, name, entityClass, mapperClass, group, exportable, importable,
                    scopeOwnerColumn, scopeCreatedByColumn, scopeWhitelist, ExtraGate.NONE);
        }

        /** 是否需要在导出时套用数据权限（与列表同口径） */
        public boolean scopeEnabled() {
            return scopeCreatedByColumn != null || scopeOwnerColumn != null
                    || (scopeWhitelist != null && scopeWhitelist != WhitelistKind.NONE);
        }

        /** 是否需要在导出时套用业务准入门槛（与列表 applyExtraFilter 同口径） */
        public boolean extraGateEnabled() {
            return extraGate != null && extraGate != ExtraGate.NONE;
        }
    }

    private static final Map<String, Bill> BILLS;

    static {
        Map<String, Bill> m = new LinkedHashMap<>();
        // ==================== 基础资料（反射列模板，通用导入） ====================
        // 以下 4 个主档的列表页已接「数据权限 + 员工权限补充白名单」，导出必须同口径（否则导出比列表宽）
        // 客户额外带「业务准入门槛」：客户档案列表只显示正式客户+历史存量（见 basedata/CustomerController#applyExtraFilter），
        // 导出必须一并套用，否则出现"客户档案列表 0 条、导出却有 2 条客户"（2026-09-17 121 报障）
        m.put("customer", new Bill("customer", "客户", com.erp.masterdata.entity.Customer.class,
                com.erp.masterdata.mapper.CustomerMapper.class, Group.BASE, true, true,
                null, "created_by", WhitelistKind.CUSTOMER, ExtraGate.CUSTOMER_ARCHIVE));
        putScoped(m, "supplier", "供应商", com.erp.masterdata.entity.Supplier.class, com.erp.masterdata.mapper.SupplierMapper.class, Group.BASE, null, "created_by", WhitelistKind.SUPPLIER);
        putScoped(m, "material", "物料", com.erp.masterdata.entity.Material.class, com.erp.masterdata.mapper.MaterialMapper.class, Group.BASE, null, "created_by", WhitelistKind.MATERIAL_CATEGORY);
        put(m, "materialCategory", "物料分类", com.erp.basedata.entity.MaterialCategory.class, com.erp.basedata.mapper.MaterialCategoryMapper.class, Group.BASE);
        put(m, "unit", "计量单位", com.erp.basedata.entity.UnitOfMeasure.class, com.erp.basedata.mapper.UnitOfMeasureMapper.class, Group.BASE);
        putScoped(m, "warehouse", "仓库", com.erp.basedata.entity.Warehouse.class, com.erp.basedata.mapper.WarehouseMapper.class, Group.BASE, null, "created_by", WhitelistKind.WAREHOUSE);
        put(m, "location", "货位", com.erp.basedata.entity.Location.class, com.erp.basedata.mapper.LocationMapper.class, Group.BASE);
        put(m, "department", "部门", com.erp.basedata.entity.Department.class, com.erp.basedata.mapper.DepartmentMapper.class, Group.BASE);
        put(m, "employee", "员工", com.erp.basedata.entity.Employee.class, com.erp.basedata.mapper.EmployeeMapper.class, Group.BASE);
        put(m, "account", "科目", com.erp.basedata.entity.Account.class, com.erp.basedata.mapper.AccountMapper.class, Group.BASE);
        put(m, "currency", "货币", com.erp.basedata.entity.Currency.class, com.erp.basedata.mapper.CurrencyMapper.class, Group.BASE);
        put(m, "paymentTerm", "付款条件", com.erp.basedata.entity.PaymentTerm.class, com.erp.basedata.mapper.PaymentTermMapper.class, Group.BASE);
        put(m, "priceLevel", "价格等级", com.erp.basedata.entity.PriceLevel.class, com.erp.basedata.mapper.PriceLevelMapper.class, Group.BASE);
        put(m, "settlementMode", "结算方式", com.erp.basedata.entity.SettlementMode.class, com.erp.basedata.mapper.SettlementModeMapper.class, Group.BASE);
        put(m, "bankAccount", "银行账户", com.erp.basedata.entity.BankAccount.class, com.erp.basedata.mapper.BankAccountMapper.class, Group.BASE);
        put(m, "bom", "BOM", com.erp.masterdata.entity.Bom.class, com.erp.masterdata.mapper.BomMapper.class, Group.BASE);
        // CRM（线索/商机列表按 assigned_sales_id 作为归属列做数据权限）
        putScoped(m, "lead", "线索", com.erp.crm.entity.Lead.class, com.erp.crm.mapper.LeadMapper.class, Group.BASE, "assigned_sales_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "opportunity", "商机", com.erp.crm.entity.Opportunity.class, com.erp.crm.mapper.OpportunityMapper.class, Group.BASE, "assigned_sales_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "complaint", "投诉", com.erp.crm.entity.Complaint.class, com.erp.crm.mapper.ComplaintMapper.class, Group.BASE, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "survey", "调研", com.erp.crm.entity.Survey.class, com.erp.crm.mapper.SurveyMapper.class, Group.BASE, null, "created_by", WhitelistKind.NONE);

        // ==================== 销售单据（6） ====================
        // 6 张销售单据的列表页口径均为 applyTo(wrapper, "saler_id")（created_by OR saler_id=当前员工）
        putScoped(m, "saleQuote", "销售报价单", com.erp.sale.entity.SaleQuote.class, com.erp.sale.mapper.SaleQuoteMapper.class, Group.SALE, "saler_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "saleOrder", "销售订单", com.erp.sale.entity.SaleOrder.class, com.erp.sale.mapper.SaleOrderMapper.class, Group.SALE, "saler_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "saleContract", "销售合同", com.erp.sale.entity.SaleContract.class, com.erp.sale.mapper.SaleContractMapper.class, Group.SALE, "saler_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "saleDelivery", "销售出库单", com.erp.sale.entity.SaleDelivery.class, com.erp.sale.mapper.SaleDeliveryMapper.class, Group.SALE, "saler_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "saleReturn", "销售退货单", com.erp.sale.entity.SaleReturn.class, com.erp.sale.mapper.SaleReturnMapper.class, Group.SALE, "saler_id", "created_by", WhitelistKind.NONE);
        // 替换货（2026-11-17）：销售换货单只导出不导入（必须由销售订单按钮生成）
        putNoImport(m, "saleExchange", "销售换货单", com.erp.sale.entity.SaleExchange.class, com.erp.sale.mapper.SaleExchangeMapper.class, Group.SALE);
        // 售后维修（2026-11-19 用户口径：导入/导出都走通用机制，不单独关闭导入）
        // 列表口径 applyTo(wrapper, "created_by")，导出/导入同口径
        putScoped(m, "saleRepair", "售后维修单", com.erp.sale.entity.SaleRepair.class, com.erp.sale.mapper.SaleRepairMapper.class, Group.SALE, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "saleInvoice", "销售发票", com.erp.sale.entity.SaleInvoice.class, com.erp.sale.mapper.SaleInvoiceMapper.class, Group.SALE, "saler_id", "created_by", WhitelistKind.NONE);

        // ==================== 采购单据（6） ====================
        // 采购申请单归属列 applicant_id；其余 4 张 buyer_id；采购发票表无 buyer_id（列表口径即 created_by）
        putScoped(m, "purchaseRequest", "采购申请单", com.erp.purchase.entity.PurchaseRequest.class, com.erp.purchase.mapper.PurchaseRequestMapper.class, Group.PURCHASE, "applicant_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "purchaseOrder", "采购订单", com.erp.purchase.entity.PurchaseOrder.class, com.erp.purchase.mapper.PurchaseOrderMapper.class, Group.PURCHASE, "buyer_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "purchaseContract", "采购合同", com.erp.purchase.entity.PurchaseContract.class, com.erp.purchase.mapper.PurchaseContractMapper.class, Group.PURCHASE, "buyer_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "purchaseInbound", "采购入库单", com.erp.purchase.entity.PurchaseInbound.class, com.erp.purchase.mapper.PurchaseInboundMapper.class, Group.PURCHASE, "buyer_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "purchaseReturn", "采购退货单", com.erp.purchase.entity.PurchaseReturn.class, com.erp.purchase.mapper.PurchaseReturnMapper.class, Group.PURCHASE, "buyer_id", "created_by", WhitelistKind.NONE);
        putScoped(m, "purchaseInvoice", "采购发票", com.erp.purchase.entity.PurchaseInvoice.class, com.erp.purchase.mapper.PurchaseInvoiceMapper.class, Group.PURCHASE, null, "created_by", WhitelistKind.NONE);

        // ==================== 库存单据（4 + 领料） ====================
        // 这 5 张的列表/卡片已按统一数据权限（角色 data_scope）过滤 created_by，导出必须同口径
        putScoped(m, "otherReceipt", "其他入库单", com.erp.inventory.entity.OtherReceipt.class, com.erp.inventory.mapper.OtherReceiptMapper.class, Group.INVENTORY, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "otherIssue", "其他出库单", com.erp.inventory.entity.OtherIssue.class, com.erp.inventory.mapper.OtherIssueMapper.class, Group.INVENTORY, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "stockTransfer", "库存调拨单", com.erp.inventory.entity.StockTransfer.class, com.erp.inventory.mapper.StockTransferMapper.class, Group.INVENTORY, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "stockCheck", "库存盘点单", com.erp.inventory.entity.StockCheck.class, com.erp.inventory.mapper.StockCheckMapper.class, Group.INVENTORY, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "materialRequisition", "生产领料单", com.erp.inventory.entity.MaterialRequisition.class, com.erp.inventory.mapper.MaterialRequisitionMapper.class, Group.INVENTORY, null, "created_by", WhitelistKind.NONE);

        // ==================== 财务单据（5） ====================
        putScoped(m, "paymentReceipt", "收款单", com.erp.finance.entity.PaymentReceipt.class, com.erp.finance.mapper.PaymentReceiptMapper.class, Group.FINANCE, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "paymentVoucher", "付款单", com.erp.finance.entity.PaymentVoucher.class, com.erp.finance.mapper.PaymentVoucherMapper.class, Group.FINANCE, null, "created_by", WhitelistKind.NONE);
        // 预收/预付款单的创建人列是 create_by（不是 created_by），列表口径即 applyTo(wrapper, null, "create_by")
        putScoped(m, "purchasePrePayment", "采购预付款单", com.erp.finance.prepayment.purchase.entity.PurchasePrePayment.class, com.erp.finance.prepayment.purchase.mapper.PurchasePrePaymentMapper.class, Group.FINANCE, null, "create_by", WhitelistKind.NONE);
        putScoped(m, "salePreReceipt", "销售预收款单", com.erp.finance.prepayment.sale.entity.SalePreReceipt.class, com.erp.finance.prepayment.sale.mapper.SalePreReceiptMapper.class, Group.FINANCE, null, "create_by", WhitelistKind.NONE);
        // 费用支出单的创建人列是 applicant_name（申请人用户名），列表口径即 applyTo(wrapper, null, "applicant_name")
        putScoped(m, "operationExpense", "费用分摊", com.erp.finance.entity.OperationExpense.class, com.erp.finance.mapper.OperationExpenseMapper.class, Group.FINANCE, null, "applicant_name", WhitelistKind.NONE);
        // 应收/应付：系统生成，只导出不导入；模板走实体 @ExportField 反射列（不属于财务单据模板族，勿改成 FINANCE）
        // 列表口径：应收 applyTo(wrapper, "saler_id")、应付 applyTo(wrapper, "buyer_id")，导出必须同口径
        putNoImportScoped(m, "accountsReceivable", "应收账款", com.erp.finance.entity.AccountsReceivable.class, com.erp.finance.mapper.AccountsReceivableMapper.class, Group.OTHER, "saler_id", "created_by", WhitelistKind.NONE);
        putNoImportScoped(m, "accountsPayable", "应付账款", com.erp.finance.entity.AccountsPayable.class, com.erp.finance.mapper.AccountsPayableMapper.class, Group.OTHER, "buyer_id", "created_by", WhitelistKind.NONE);

        // ==================== 生产单据 ====================
        // 列表/卡片已按统一数据权限（角色 data_scope）过滤：工单/入库/工序汇报 用 created_by；
        // MRP 计划表沿用本表现有列名 create_by
        putScoped(m, "workOrder", "生产工单", com.erp.production.entity.WorkOrder.class, com.erp.production.mapper.WorkOrderMapper.class, Group.PRODUCTION, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "productionReceipt", "生产入库单", com.erp.production.entity.ProductionReceipt.class, com.erp.production.mapper.ProductionReceiptMapper.class, Group.PRODUCTION, null, "created_by", WhitelistKind.NONE);
        putScoped(m, "operationReport", "工序汇报单", com.erp.production.entity.OperationReport.class, com.erp.production.mapper.OperationReportMapper.class, Group.PRODUCTION, null, "created_by", WhitelistKind.NONE);
        // 工作中心/工艺路线/工作站属「生产主档」（与基础资料同口径：列表不做数据权限过滤），导出保持一致不套权限
         put(m, "workCenter", "工作中心", com.erp.production.entity.WorkCenter.class, com.erp.production.mapper.WorkCenterMapper.class, Group.PRODUCTION);
         put(m, "routing", "工艺路线", com.erp.production.entity.Routing.class, com.erp.production.mapper.RoutingMapper.class, Group.PRODUCTION);
         put(m, "workstation", "工作站", com.erp.production.entity.Workstation.class, com.erp.production.mapper.WorkstationMapper.class, Group.PRODUCTION);
         putScoped(m, "mrpPlan", "MRP计划", com.erp.produce.mrp.entity.MrpPlan.class, com.erp.produce.mrp.mapper.MrpPlanMapper.class, Group.PRODUCTION, null, "create_by", WhitelistKind.NONE);

        // ==================== 委外单据（5） ====================
        // 这 5 张表的真实创建人列是 create_by（不是 created_by），列表/卡片/导出统一用它
        putScoped(m, "subcontract", "委外订单", com.erp.subcontract.order.entity.SubcontractOrder.class, com.erp.subcontract.order.mapper.SubcontractOrderMapper.class, Group.SUBCONTRACT, null, "create_by", WhitelistKind.NONE);
        putScoped(m, "subcontractPick", "委外发料单", com.erp.subcontract.pick.entity.SubcontractPick.class, com.erp.subcontract.pick.mapper.SubcontractPickMapper.class, Group.SUBCONTRACT, null, "create_by", WhitelistKind.NONE);
        putScoped(m, "subcontractInbound", "委外入库单", com.erp.subcontract.inbound.entity.SubcontractInbound.class, com.erp.subcontract.inbound.mapper.SubcontractInboundMapper.class, Group.SUBCONTRACT, null, "create_by", WhitelistKind.NONE);
        putScoped(m, "subcontractReturn", "委外退料单", com.erp.subcontract.returnn.entity.SubcontractReturn.class, com.erp.subcontract.returnn.mapper.SubcontractReturnMapper.class, Group.SUBCONTRACT, null, "create_by", WhitelistKind.NONE);
        putScoped(m, "subcontractSupplement", "委外补料单", com.erp.subcontract.supplement.entity.SubcontractSupplement.class, com.erp.subcontract.supplement.mapper.SubcontractSupplementMapper.class, Group.SUBCONTRACT, null, "create_by", WhitelistKind.NONE);

        // ==================== 组装单 / 拆卸单（2026-09-20 新增：只开导出，不开导入） ====================
        // 前端「导出」按钮原先调 /api/produce/{assemble|disassemble}/export，后端无该映射（必 404）；
        // 现改走通用导出 /api/export/data/{entityName}，故在此登记。
        // 两个实体已补 @ExportField 表头列（16 条）+ 明细规格（见 ExcelServiceImpl 的 ASSEMBLE_ITEM_COLUMNS / BILL_ITEM_SPECS）。
        // 2026-11-07：补主子表导入实现（ExcelServiceImpl#importProduceAssembles/importProduceDisassembles），开放导入。
        putScoped(m, "produceAssemble", "组装单", com.erp.produce.assemble.entity.ProduceAssemble.class, com.erp.produce.assemble.mapper.ProduceAssembleMapper.class, Group.PRODUCTION, null, "creator", WhitelistKind.NONE);
        putScoped(m, "produceDisassemble", "拆卸单", com.erp.produce.assemble.entity.ProduceDisassemble.class, com.erp.produce.assemble.mapper.ProduceDisassembleMapper.class, Group.PRODUCTION, null, "creator", WhitelistKind.NONE);

        // ==================== 后端已有、但导入导出不开放（exportable=false 显式标记） ====================
        // 分两类，别混淆：

        // 【A 类·待补导出列】有前端页面、已确认要补导入导出，但实体零个 @ExportField，
        // 通用反射导出只会得到空表头 → 需像库存现存量那样补一套专用导出列，补齐后把 exportable 改 true。
        putNoExportNoImport(m, "sampleOrder", "打样单", com.erp.sample.entity.SampleOrder.class, com.erp.sample.mapper.SampleOrderMapper.class, Group.OTHER);
        putNoExportNoImport(m, "freeSampleOrder", "免费样品单", com.erp.freesample.entity.FreeSampleOrder.class, com.erp.freesample.mapper.FreeSampleOrderMapper.class, Group.OTHER);

        // 【B 类·无前端入口，按设计不开放（用户 2026-09-15 拍板：保留后端、不补入口）】
        // 这些单据的金额被 DataBoardServiceImpl 的「净利润 = 毛利 − 销售费用 − 采购费用 − 税金」
        // 与看板费用口径引用，属于"统计口径的数据载体"；用户点不到也不需要点。
        // 在此登记只为留痕，防止日后被当成死代码误删（误删会让看板净利润缺项）。
        putNoExportNoImport(m, "purchaseExpense", "采购费用单", com.erp.finance.expense.purchase.entity.PurchaseExpense.class, com.erp.finance.expense.purchase.mapper.PurchaseExpenseMapper.class, Group.FINANCE);
        putNoExportNoImport(m, "saleExpense", "销售费用单", com.erp.finance.expense.sale.entity.SaleExpense.class, com.erp.finance.expense.sale.mapper.SaleExpenseMapper.class, Group.FINANCE);
        putNoExportNoImport(m, "purchaseStatement", "采购结算单", com.erp.finance.purchasebill.entity.PurchaseStatement.class, com.erp.finance.purchasebill.mapper.PurchaseStatementMapper.class, Group.FINANCE);
        putNoExportNoImport(m, "saleStatement", "销售结算单", com.erp.finance.salebill.entity.SaleStatement.class, com.erp.finance.salebill.mapper.SaleStatementMapper.class, Group.FINANCE);

        // ==================== 查询视图（不是单据：有导出、无导入、无新增） ====================
        // 说明：库存流水(StockFlowList)与批次台账(BatchList)是纯前端 CSV 导出，走不到后端，故不登记。
        //       库存现存量(InventoryList)调用后端 /api/export/data/inventory，必须登记。
        putNoImport(m, "inventory", "库存现存量", com.erp.inventory.entity.Inventory.class, com.erp.inventory.mapper.InventoryMapper.class, Group.OTHER);

        // ==================== 销售 / 采购价格策略（表头 + 明细结构） ====================
        // 这两张是「表头 + 明细」结构、导出/模板/导入共用一套扁平列（一行 = 一个对象的价格），
        // 故实体不加 @ExportField，走专用导出行（见 ExcelServiceImpl 的 exportPriceStrategyTemplate/Data）。
        // 用 put(...)：可导 + 可入 + 不套数据权限（与列表页同口径：价格策略列表页本身未接数据权限）。
        put(m, "salePriceStrategy", "销售价格策略", com.erp.price.sale.entity.SalePriceStrategy.class, com.erp.price.sale.mapper.SalePriceStrategyMapper.class, Group.OTHER);
        put(m, "purchasePriceStrategy", "采购价格策略", com.erp.price.purchase.entity.PurchasePriceStrategy.class, com.erp.price.purchase.mapper.PurchasePriceStrategyMapper.class, Group.OTHER);

        BILLS = Collections.unmodifiableMap(m);
    }

    /** 支持导入的单据（导入按钮、AI 导入工具白名单都取这里）。默认不套数据权限（scopeCreatedByColumn=null）。 */
    private static void put(Map<String, Bill> m, String code, String name, Class<?> entityClass, Class<?> mapperClass, Group group) {
        m.put(code, new Bill(code, name, entityClass, mapperClass, group, true, true, null, null, WhitelistKind.NONE));
    }

    /**
     * 支持导入 + <b>导出需套数据权限</b>（与列表页同口径）的单据/主档。
     *
     * @param scopeOwnerColumn     归属列（员工ID维度）；null = 无归属列
     * @param scopeCreatedByColumn 创建人列；传 "created_by" 等（不可为 null，null 表示不套权限）
     * @param scopeWhitelist       白名单种类，非主档传 NONE
     */
    private static void putScoped(Map<String, Bill> m, String code, String name, Class<?> entityClass,
                                  Class<?> mapperClass, Group group, String scopeOwnerColumn,
                                  String scopeCreatedByColumn, WhitelistKind scopeWhitelist) {
        m.put(code, new Bill(code, name, entityClass, mapperClass, group, true, true,
                scopeOwnerColumn, scopeCreatedByColumn, scopeWhitelist));
    }

    /** 只导出不导入（系统生成单据 / 查询视图） */
    private static void putNoImport(Map<String, Bill> m, String code, String name, Class<?> entityClass, Class<?> mapperClass, Group group) {
        m.put(code, new Bill(code, name, entityClass, mapperClass, group, true, false, null, null, WhitelistKind.NONE));
    }

    /** 只导出不导入 + 导出需套数据权限（应收/应付/库存现存量等） */
    private static void putNoImportScoped(Map<String, Bill> m, String code, String name, Class<?> entityClass,
                                          Class<?> mapperClass, Group group, String scopeOwnerColumn,
                                          String scopeCreatedByColumn, WhitelistKind scopeWhitelist) {
        m.put(code, new Bill(code, name, entityClass, mapperClass, group, true, false,
                scopeOwnerColumn, scopeCreatedByColumn, scopeWhitelist));
    }

    /**
     * 台账已有、导入导出都还没开放（项目里"待补"的显式标记）。
     * 用 exportable=false 而不是"不登记"，这样交叉校验能继续发现"漏登记"，
     * 同时前端不会出现导出/导入入口。补齐列后把 exportable 改 true 即可。
     */
    private static void putNoExportNoImport(Map<String, Bill> m, String code, String name, Class<?> entityClass, Class<?> mapperClass, Group group) {
        m.put(code, new Bill(code, name, entityClass, mapperClass, group, false, false, null, null, WhitelistKind.NONE));
    }

    public static Bill get(String code) {
        return code == null ? null : BILLS.get(code);
    }

    public static boolean exists(String code) {
        return get(code) != null;
    }

    public static List<Bill> all() {
        return new ArrayList<>(BILLS.values());
    }

    public static Set<String> allCodes() {
        return BILLS.keySet();
    }

    /** 可导入实体名集合（原 EXPORT_ONLY_ENTITIES 的排除逻辑已内化为 importable=false） */
    public static Set<String> importableCodes() {
        Set<String> s = new java.util.LinkedHashSet<>();
        for (Bill b : BILLS.values()) {
            if (b.importable()) {
                s.add(b.code());
            }
        }
        return s;
    }

    /** 取分组；未登记的实体返回 null（调用方必须显式报错，不允许静默回退到通用分支） */
    public static Group groupOf(String code) {
        Bill b = get(code);
        return b == null ? null : b.group();
    }

    public static String nameOf(String code) {
        Bill b = get(code);
        return b == null ? null : b.name();
    }
}
