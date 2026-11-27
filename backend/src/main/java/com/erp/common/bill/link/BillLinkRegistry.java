package com.erp.common.bill.link;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据关联注册表（配置驱动，非数据库表）。
 *
 * <p>登记「单据类型 → 表/列」以及「源单 → 去向单」的定位方式，供 {@link BillLinkService} 做通用查询。
 * 新增单据类型只需在本类追加配置，后端接口与前端组件都不用改。</p>
 *
 * <p>安全约定：本类中的表名/列名/SQL 全部为代码常量，接口入参只作为绑定参数传入，绝不拼接进 SQL。
 * 所有列名已于 2026-09-11 用 information_schema 逐列核对。</p>
 *
 * <p>2026-09-11 扩展：覆盖销售/采购/库存/生产/委外/财务/样品/MRP 全部关联单据；
 * 去向单据支持物料级明细（{@link TargetDef#detailSql}）。</p>
 */
@Component
public class BillLinkRegistry {

    /** 单据表结构定义 */
    public static class DocDef {
        public final String docType;
        /** 中文名，与各单据表 source_bill_type 的取值口径一致（用于把"来源类型"反查成单据类型） */
        public final String name;
        public final String table;
        public final String pkCol;
        public final String noCol;
        public final String dateCol;
        public final String statusCol;
        /** 表头金额列，可为 null */
        public final String amountCol;
        /** 表头"来源单号"列，可为 null */
        public final String srcNoCol;
        /** 表头"来源单据类型"列，可为 null */
        public final String srcTypeCol;
        /** 表头"来源单据主键"列（预收款/预付款这类只存 id 的单据），可为 null */
        public String srcIdCol;
        // ---- 明细 ----
        public final String itemTable;
        public final String itemPkCol;
        public final String itemFkCol;
        /** 行号列，可为 null（销售合同明细用 sort_no；委外明细无行号） */
        public final String lineCol;
        public final String qtyCol;
        public final String priceCol;
        /** 明细物料列（默认 material_code/material_name/specification，特殊表可覆盖，可为 null） */
        public String itemMatCodeCol = "material_code";
        public String itemMatNameCol = "material_name";
        public String itemSpecCol = "specification";
        /** 本单据明细上的"行级来源列"（记录来源明细行）：source_item_id / source_line_no / source_row_no 口径不一，可为 null。 */
        public String srcItemCol;
        // ---------------- 行级来源信息（2026-10-28：来源/去向一律以明细行为准，表头只做历史兜底） ----------------
        /** 行级来源信息所在表：默认=itemTable；收款单/付款单=核销明细 bill_writeoff_detail；null=无行级来源 */
        public String srcRowsTable;
        /** 行级来源信息表指向本单据的外键列 */
        public String srcRowsFkCol;
        /** 行级来源单号列（默认 source_bill_no；核销明细为 source_no） */
        public String srcRowsNoCol;
        /** 行级来源单据类型列（默认 source_type / source_bill_type；核销明细为 source_type） */
        public String srcRowsTypeCol;
        /** 行级来源信息表的固定条件（如 bill_type='收款单'），可为 null */
        public String srcRowsFilter;
        /**
         * 本单据明细上的"已下推数量"列：key=下游单据类型编码（如 SALE_DELIVERY），value=列名。
         */
        public final Map<String, String> turnedByTarget = new LinkedHashMap<>();

        DocDef(String docType, String name, String table, String pkCol, String noCol, String dateCol,
               String statusCol, String amountCol, String srcNoCol, String srcTypeCol,
               String itemTable, String itemPkCol, String itemFkCol, String lineCol,
               String qtyCol, String priceCol) {
            this.docType = docType;
            this.name = name;
            this.table = table;
            this.pkCol = pkCol;
            this.noCol = noCol;
            this.dateCol = dateCol;
            this.statusCol = statusCol;
            this.amountCol = amountCol;
            this.srcNoCol = srcNoCol;
            this.srcTypeCol = srcTypeCol;
            this.itemTable = itemTable;
            this.itemPkCol = itemPkCol;
            this.itemFkCol = itemFkCol;
            this.lineCol = lineCol;
            this.qtyCol = qtyCol;
            this.priceCol = priceCol;
            // 行级来源信息默认就在明细子表里（列名由 BillLinkService 按真实表结构自动识别）
            this.srcRowsTable = itemTable;
            this.srcRowsFkCol = itemFkCol;
        }

        DocDef srcItem(String col) { this.srcItemCol = col; return this; }

        /**
         * 指定行级来源信息所在表/列（默认取明细子表 + source_bill_no/source_type）。
         * 收款单/付款单这类"来源记在核销明细"的单据用它覆盖。
         */
        DocDef srcRows(String table, String fkCol, String noCol, String typeCol, String filter) {
            this.srcRowsTable = table;
            this.srcRowsFkCol = fkCol;
            this.srcRowsNoCol = noCol;
            this.srcRowsTypeCol = typeCol;
            this.srcRowsFilter = filter;
            return this;
        }

        /** 表头"来源单据主键"列（预收款/预付款这类只存 id 的单据） */
        DocDef srcId(String col) { this.srcIdCol = col; return this; }

        DocDef itemCols(String code, String nameCol, String spec) {
            this.itemMatCodeCol = code;
            this.itemMatNameCol = nameCol;
            this.itemSpecCol = spec;
            return this;
        }

        DocDef turned(String targetDocType, String col) {
            this.turnedByTarget.put(targetDocType, col);
            return this;
        }
    }

    /** 去向单据定义：表头 SQL（结果列别名固定 id/no/dt/st/amt）+ 可选物料明细 SQL（单个绑定参数=下游单据主键） */
    public static class TargetDef {
        public final String docType;
        public final String name;
        /** 查询结果列别名固定为：id / no / dt / st / amt */
        public final String sql;
        /** 明细 SQL，可为 null（该去向无物料级明细）；结果列别名固定 lineNo/materialCode/materialName/spec/qty/price/amount */
        public final String detailSql;

        TargetDef(String docType, String name, String sql, String detailSql) {
            this.docType = docType;
            this.name = name;
            this.sql = sql;
            this.detailSql = detailSql;
        }
    }

    private final Map<String, DocDef> docByType = new LinkedHashMap<>();
    /** 表名 → 单据定义（看板下钻按数据源表反查单据类型用） */
    private final Map<String, DocDef> docByTable = new HashMap<>();
    /** 中文单据名 / 单号类型编码（大小写不敏感） → 单据类型编码 */
    private final Map<String, String> typeByName = new HashMap<>();
    /** 源单据类型编码 → 其去向单据定义 */
    private final Map<String, List<TargetDef>> targetsBySource = new HashMap<>();

    public BillLinkRegistry() {
        registerDocs();
        registerTargets();
    }

    // ==================================================================================
    // 一、单据表结构（表/主键/单号/日期/状态/金额/来源列 + 明细表）
    // ==================================================================================

    private void registerDocs() {
        // ---------------- 销售 ----------------
        register(doc("SALE_QUOTE", "销售报价单", "sale_quote", "quote_id", "quote_no", "quote_date", "status", "net_amount",
                "source_bill_no", null,
                "sale_quote_item", "quote_item_id", "quote_id", "line_no", "quantity", "unit_price")
                .turned("SALE_ORDER", "turned_order_quantity"));

        register(doc("SALE_CONTRACT", "销售合同", "sale_contract", "contract_id", "contract_no", "contract_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "sale_contract_item", "item_id", "contract_id", "sort_no", "quantity", "unit_price"));

        register(doc("SALE_ORDER", "销售订单", "sale_order", "order_id", "order_no", "order_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "sale_order_item", "order_item_id", "order_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id")
                .turned("SALE_DELIVERY", "turned_output_quantity")
                .turned("SALE_RETURN", "turned_return_quantity")
                .turned("SALE_INVOICE", "turned_invoice_quantity")
                .turned("PRODUCTION_ORDER", "turned_produce_quantity")
                .turned("SUBCONTRACT_ORDER", "turned_subcontract_quantity"));

        register(doc("SALE_DELIVERY", "销售出库单", "sale_delivery", "delivery_id", "delivery_no", "delivery_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "sale_delivery_item", "delivery_item_id", "delivery_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id")
                .turned("SALE_INVOICE", "turned_invoice_quantity"));

        register(doc("SALE_INVOICE", "销售发票", "sale_invoice", "invoice_id", "invoice_no", "invoice_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "sale_invoice_item", "invoice_item_id", "invoice_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id"));

        register(doc("SALE_RETURN", "销售退货单", "sale_return", "return_id", "return_no", "return_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "sale_return_item", "return_item_id", "return_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id")
                .turned("SALE_INVOICE", "turned_invoice_quantity"));

        // 销售换货单（2026-11-17 替换货）：来源=销售订单；去向=自动生成的销售出库单
        register(doc("SALE_EXCHANGE", "销售换货单", "sale_exchange", "exchange_id", "exchange_no", "exchange_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "sale_exchange_item", "exchange_item_id", "exchange_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id"));

        // 售后维修单（2026-11-18）：来源=销售订单/销售出库单；去向=转换新的换货单或转退货单
        register(doc("SALE_REPAIR", "售后维修单", "sale_repair", "repair_id", "repair_no", "repair_date", "status", "total_fee",
                "source_bill_no", "source_bill_type",
                "sale_repair_item", "repair_item_id", "repair_id", "line_no", "quantity", null));
        register(doc("PURCHASE_REQUEST", "采购申请单", "purchase_request", "request_id", "request_no", "request_date", "status", null,
                "source_bill_no", "source_type",
                "purchase_request_item", "request_item_id", "request_id", "line_no", "quantity", null)
                .srcItem("source_row_no"));

        register(doc("PURCHASE_ORDER", "采购订单", "purchase_order", "order_id", "order_no", "order_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "purchase_order_item", "order_item_id", "order_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id"));

        register(doc("PURCHASE_CONTRACT", "采购合同", "purchase_contract", "contract_id", "contract_no", "contract_date", "status", "net_amount",
                null, null,
                "purchase_contract_item", "contract_item_id", "contract_id", "line_no", "quantity", "unit_price")
                .srcItem("source_line_no"));

        register(doc("PURCHASE_INBOUND", "采购入库单", "purchase_receipt", "receipt_id", "receipt_no", "receipt_date", "status", "total_amount",
                "source_bill_no", "source_type",
                "purchase_receipt_item", "receipt_item_id", "receipt_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id"));

        register(doc("PURCHASE_RETURN", "采购退货单", "purchase_return", "return_id", "return_no", "return_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                "purchase_return_item", "return_item_id", "return_id", "line_no", "quantity", "unit_price")
                .srcItem("source_item_id"));

        register(doc("PURCHASE_INVOICE", "采购发票", "purchase_invoice", "invoice_id", "invoice_no", "invoice_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "purchase_invoice_item", "invoice_item_id", "invoice_id", "line_no", "quantity", "unit_price")
                .srcItem("source_row_no"));

        // ---------------- 库存 ----------------
        register(doc("OTHER_ISSUE", "其他出库单", "other_issue", "issue_id", "issue_no", "issue_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                "other_issue_item", "issue_item_id", "issue_id", "line_no", "quantity", "unit_price"));

        register(doc("OTHER_RECEIPT", "其他入库单", "other_receipt", "receipt_id", "receipt_no", "receipt_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                "other_receipt_item", "receipt_item_id", "receipt_id", "line_no", "quantity", "unit_price"));

        register(doc("MATERIAL_REQUISITION", "生产领料单", "material_requisition", "requisition_id", "requisition_no", "requisition_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                "material_requisition_item", "requisition_item_id", "requisition_id", "line_no", "quantity", "unit_price")
                .srcItem("source_line_no"));

        // 生产退料单 / 生产补料单：与领料单共用 material_requisition 表（bill_type 区分），
        // 来源单号优先取表头 source_bill_no，没有则退回工单号 work_order_no（退料/补料业务上就是从工单来的）。
        // 注意：docByTable 用 putIfAbsent，领料单已先注册，看板下钻反查不受影响；全局搜索按表去重（见 GlobalSearchService）。
        register(doc("MATERIAL_RETURN", "生产退料单", "material_requisition", "requisition_id", "requisition_no", "requisition_date", "status", "total_amount",
                "coalesce(nullif(source_bill_no, ''), work_order_no)", "source_bill_type",
                "material_requisition_item", "requisition_item_id", "requisition_id", "line_no", "quantity", "unit_price")
                .srcItem("source_line_no"));

        register(doc("MATERIAL_SUPPLEMENT", "生产补料单", "material_requisition", "requisition_id", "requisition_no", "requisition_date", "status", "total_amount",
                "coalesce(nullif(source_bill_no, ''), work_order_no)", "source_bill_type",
                "material_requisition_item", "requisition_item_id", "requisition_id", "line_no", "quantity", "unit_price")
                .srcItem("source_line_no"));

        register(doc("STOCK_TRANSFER", "库存调拨单", "stock_transfer", "transfer_id", "transfer_no", "transfer_date", "status", "total_amount",
                null, null,
                "stock_transfer_item", "transfer_item_id", "transfer_id", "line_no", "quantity", "unit_price"));

        register(doc("STOCK_CHECK", "库存盘点单", "stock_check", "check_id", "check_no", "check_date", "status", "check_amount",
                null, null,
                "stock_check_item", "check_item_id", "check_id", "line_no", "check_quantity", "unit_price"));

        // ---------------- 生产 ----------------
        register(doc("PRODUCTION_ORDER", "生产订单", "production_order", "order_id", "order_no", "order_date", "status", null,
                "source_no", "source_type",
                "production_order_item", "order_item_id", "order_id", "line_no", "required_quantity", "unit_cost"));

        register(doc("PRODUCTION_RECEIPT", "生产入库单", "production_receipt", "receipt_id", "receipt_no", "receipt_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                "production_receipt_item", "receipt_item_id", "receipt_id", "line_no", "quantity", "unit_price"));

        // 生产组装单 / 生产拆卸单：库存内部加工单，无来源单据、也不下推生成其他单据。
        // 表头虽有 source_type / source_id 列，但 Service 从未对其赋值（两列仅出现在列名白名单里，恒为空用）；
        // 故只登记 DocDef（让 isRegistered=true、BillLinkPanel 不再抛「未登记」异常），不配来源列、也不配 target。
        register(doc("PRODUCE_ASSEMBLE", "生产组装单", "produce_assemble", "id", "serial_no", "dateline", "status", "amount",
                null, null,
                null, null, null, null, null, null));

        register(doc("PRODUCE_DISASSEMBLE", "生产拆卸单", "produce_disassemble", "id", "serial_no", "dateline", "status", "amount",
                null, null,
                null, null, null, null, null, null));

        // 工序汇报单：有状态流转但无明细表（表头级）
        register(doc("OPERATION_REPORT", "工序汇报单", "operation_report", "report_id", "report_no", "report_date", "status", null,
                "source_bill_no", "source_bill_type",
                null, null, null, null, null, null));

        // ---------------- 委外 ----------------
        register(doc("SUBCONTRACT_ORDER", "委外订单", "subcontract_order", "id", "order_no", "order_date", "status", "net_amount",
                "source_bill_no", "source_bill_type",
                "subcontract_order_item", "id", "order_id", null, "quantity", "unit_price"));

        register(doc("SUBCONTRACT_DELIVERY", "委外发料单", "subcontract_pick", "id", "pick_no", "pick_date", "status", null,
                null, null,
                "subcontract_pick_item", "id", "pick_id", null, "quantity", null));

        register(doc("SUBCONTRACT_RECEIPT", "委外入库单", "subcontract_inbound", "id", "inbound_no", "inbound_date", "status", null,
                null, null,
                "subcontract_inbound_item", "id", "inbound_id", null, "quantity", "processing_fee"));

        register(doc("SUBCONTRACT_RETURN", "委外退货单", "subcontract_return", "id", "return_no", "return_date", "status", null,
                null, null,
                "subcontract_return_item", "id", "return_id", null, "quantity", null));

        register(doc("SUBCONTRACT_SUPPLEMENT", "委外补料单", "subcontract_supplement", "id", "supplement_no", "supplement_date", "status", null,
                null, null,
                "subcontract_supplement_item", "id", "supplement_id", null, "quantity", null));

        // ---------------- 财务 ----------------
        register(doc("PAYMENT_RECEIPT", "收款单", "cash_receipt", "receipt_id", "receipt_no", "receipt_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                null, null, null, null, null, null)
                // 收款单的来源信息记在核销明细（一行一个来源：销售订单/出库单/发票/应收单/预收款单）
                .srcRows("bill_writeoff_detail", "bill_id", "source_no", "source_type", "bill_type = '收款单'"));

        register(doc("PAYMENT_VOUCHER", "付款单", "cash_payment", "payment_id", "payment_no", "payment_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                null, null, null, null, null, null)
                // 付款单的来源信息同样记在核销明细（应付单/采购订单/入库单/发票/预付款单）
                .srcRows("bill_writeoff_detail", "bill_id", "source_no", "source_type", "bill_type = '付款单'"));

        register(doc("PURCHASE_PRE_PAYMENT", "预付款单", "purchase_pre_payment", "id", "payment_no", "payment_date", "status", "amount",
                null, "source_type",
                null, null, null, null, null, null)
                .srcId("source_bill_id"));

        register(doc("SALE_PRE_RECEIPT", "销售预收款单", "sale_pre_receipt", "id", "receipt_no", "receipt_date", "status", "amount",
                null, "source_type",
                null, null, null, null, null, null)
                .srcId("source_bill_id"));

        register(doc("ACCOUNT_RECEIVABLE", "应收单", "account_receivable", "ar_id", "ar_no", "ar_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                null, null, null, null, null, null));

        register(doc("ACCOUNT_PAYABLE", "应付单", "account_payable", "ap_id", "ap_no", "ap_date", "status", "total_amount",
                "source_bill_no", "source_bill_type",
                null, null, null, null, null, null));

        register(doc("PURCHASE_EXPENSE", "采购费用单", "purchase_expense", "id", "expense_no", "expense_date", "status", "total_amount",
                "related_doc_no", null,
                null, null, null, null, null, null));

        // 2026-09-15 补登记（原先漏在台账外）：费用分摊单（= 运营费用/制造费用分摊）。
        // 分摊明细 operation_expense_allocation 是"按来源单据分摊的金额行"，没有物料列，
        // 故不挂 itemTable（BillLinkService 对 itemTable=null 已做守卫，不会加载明细）。
        register(doc("OPERATION_EXPENSE", "费用分摊", "operation_expense", "expense_id", "expense_no", "expense_date", "status", "amount",
                null, null,
                null, null, null, null, null, null));

        // ---------------- 样品 ----------------
        register(doc("SAMPLE_ORDER", "打样单", "sample_order", "sample_id", "sample_no", "demand_date", "status", "cost_amount",
                null, null,
                "sample_order_item", "item_id", "sample_id", "line_no", "base_qty", "unit_price")
                .itemCols("child_material_code", "child_material_name", "specification"));

        register(doc("FREE_SAMPLE", "免费样品单", "free_sample_order", "order_id", "order_no", "order_date", "status", "total_amount",
                null, null,
                "free_sample_order_item", "item_id", "order_id", "line_no", "quantity", null)
                .itemCols("material_code", "material_name", "model"));

        // ---------------- MRP ----------------
        register(doc("MRP_PLAN", "MRP计划", "mrp_plan", "id", "plan_no", "plan_date", "plan_status", null,
                null, null,
                "mrp_plan_item", "id", "plan_id", null, "plan_quantity", null));

        // 来源类型别名（历史/特殊取值，统一指向同一单据类型）
        alias("MRP", "MRP_PLAN");
        alias("调拨单", "STOCK_TRANSFER");
        alias("盘点单", "STOCK_CHECK");
        alias("生产工单", "PRODUCTION_ORDER");
        alias("委外领料单", "SUBCONTRACT_DELIVERY");
        alias("委外加工单", "SUBCONTRACT_ORDER");
        alias("免费样品", "FREE_SAMPLE");
        alias("打样单", "SAMPLE_ORDER");
        // 2026-09-15 补：下列别名 = 前端页面标题（用户日常看到的名字）。
        // 台账主名是 source_bill_type 的数据口径（库里有历史中文值，不可改），
        // 这里只为「按页面名反查单据类型」补口径，使页面名与数据名都能命中。
        alias("库存调拨单", "STOCK_TRANSFER");
        alias("库存盘点单", "STOCK_CHECK");
        alias("委外发料单", "SUBCONTRACT_DELIVERY");
        alias("委外退料单", "SUBCONTRACT_RETURN");
        alias("采购预付款单", "PURCHASE_PRE_PAYMENT");
        // 核销明细里的历史叫法（收款/付款核销行的 source_type 取这两种短名）
        alias("预收款单", "SALE_PRE_RECEIPT");
        alias("预付款单", "PURCHASE_PRE_PAYMENT");
        alias("应收账款", "ACCOUNT_RECEIVABLE");
        alias("应付账款", "ACCOUNT_PAYABLE");
        // 费用分摊单的历史/页面叫法（同一张单据曾有四套名）：全部指向 OPERATION_EXPENSE
        alias("费用支出单", "OPERATION_EXPENSE");
        alias("运营费用", "OPERATION_EXPENSE");
        alias("制造费用", "OPERATION_EXPENSE");
        alias("制造费用分摊单", "OPERATION_EXPENSE");
        // 组装单/拆卸单的页面标题叫法（BillExcelRegistry 与前端页面标题对齐），指向台账主名
        alias("组装单", "PRODUCE_ASSEMBLE");
        alias("拆卸单", "PRODUCE_DISASSEMBLE");
        // 2026-11-06 批次溯源：库存流水里 source_bill_type 存在"Java 类名"历史取值
        // （如 OtherReceipt / ProductionReceipt），补别名后单号才能解析出单据主键并跳转
        alias("OtherReceipt", "OTHER_RECEIPT");
        alias("OtherIssue", "OTHER_ISSUE");
        alias("ProductionReceipt", "PRODUCTION_RECEIPT");
        alias("ProductionOrder", "PRODUCTION_ORDER");
        alias("WorkOrder", "PRODUCTION_ORDER");
        alias("PurchaseInbound", "PURCHASE_INBOUND");
        alias("PurchaseReceipt", "PURCHASE_INBOUND");
        alias("PurchaseReturn", "PURCHASE_RETURN");
        alias("PurchaseOrder", "PURCHASE_ORDER");
        alias("PurchaseInvoice", "PURCHASE_INVOICE");
        alias("SaleDelivery", "SALE_DELIVERY");
        alias("SaleOrder", "SALE_ORDER");
        alias("SaleReturn", "SALE_RETURN");
        alias("SaleInvoice", "SALE_INVOICE");
        alias("MaterialRequisition", "MATERIAL_REQUISITION");
        alias("StockTransfer", "STOCK_TRANSFER");
        alias("StockCheck", "STOCK_CHECK");
    }

    // ==================================================================================
    // 二、来源单 → 去向单（一跳直接下游）
    // ==================================================================================

    private void registerTargets() {
        // ---------------- 销售链路 ----------------
        target("SALE_QUOTE", "SALE_ORDER", "销售订单",
                "select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt"
                        + " from sale_order where source_bill_no = ?",
                detail("sale_order_item", "order_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("SALE_QUOTE", "SALE_CONTRACT", "销售合同",
                "select contract_id as id, contract_no as no, contract_date as dt, status as st, net_amount as amt"
                        + " from sale_contract where source_bill_no = ?",
                detail("sale_contract_item", "contract_id", "sort_no", "quantity", "unit_price", "amount"));

        target("SALE_CONTRACT", "SALE_ORDER", "销售订单",
                "select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt"
                        + " from sale_order where source_bill_no = ?",
                detail("sale_order_item", "order_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        // 销售合同 → 采购合同（2026-11-08 补登记）：前端下推关系早已存在，但注册表漏登记 →
        // 下推按钮无法按"已生成采购合同"隐藏、去向页签也看不到采购合同。
        // 采购合同表头没有来源列，按明细行来源单号反查。
        target("SALE_CONTRACT", "PURCHASE_CONTRACT", "采购合同",
                "select distinct pc.contract_id as id, pc.contract_no as no, pc.contract_date as dt,"
                        + " pc.status as st, pc.net_amount as amt"
                        + " from purchase_contract pc join purchase_contract_item pci on pci.contract_id = pc.contract_id"
                        + " where pci.source_bill_no = ?",
                detail("purchase_contract_item", "contract_id", "line_no", "quantity", "unit_price", "amount"));

        // 销售订单 → 销售合同（2026-11-08 自动下推单据）：设置中心可开启"订单生效自动生成合同"，
        // 自动生成的合同 source_bill_no = 订单号；登记后订单详情「去向单据」页签可直接看到该合同。
        target("SALE_ORDER", "SALE_CONTRACT", "销售合同",
                "select contract_id as id, contract_no as no, contract_date as dt, status as st, net_amount as amt"
                        + " from sale_contract where source_bill_no = ?",
                detail("sale_contract_item", "contract_id", "sort_no", "quantity", "unit_price", "amount"));

        target("SALE_ORDER", "SALE_DELIVERY", "销售出库单",
                "select delivery_id as id, delivery_no as no, delivery_date as dt, status as st, net_amount as amt,"
                        + " source_bill_type as srcType"
                        + " from sale_delivery where source_bill_no = ?",
                detail("sale_delivery_item", "delivery_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        // 销售订单 → 销售换货单（2026-11-17 替换货）：订单「去向单据」可看到换货单并点击进入
        // 2026-11-18 修正：sale_exchange.source_id 是订单主键（bigint），而通用去向查询按"来源单号"绑定参数，
        // 原 where source_id = ? 会把订单号当 bigint 比较直接报错 → 订单去向里永远看不到换货单。
        target("SALE_ORDER", "SALE_EXCHANGE", "销售换货单",
                "select exchange_id as id, exchange_no as no, exchange_date as dt, status as st, net_amount as amt"
                        + " from sale_exchange where source_bill_no = ?",
                detail("sale_exchange_item", "exchange_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        // 销售换货单 → 销售出库单（生效后自动生成）：换货单「去向单据」可看到生成的出库单
        target("SALE_EXCHANGE", "SALE_DELIVERY", "销售出库单",
                "select delivery_id as id, delivery_no as no, delivery_date as dt, status as st, net_amount as amt"
                        + " from sale_delivery where source_bill_no = ?",
                detail("sale_delivery_item", "delivery_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        // 销售订单 / 销售出库单 → 售后维修单（2026-11-18）：来源单据「去向单据」可看到报修并点击进入
        target("SALE_ORDER", "SALE_REPAIR", "售后维修单",
                "select repair_id as id, repair_no as no, repair_date as dt, status as st, total_fee as amt"
                        + " from sale_repair where source_bill_no = ?",
                detail("sale_repair_item", "repair_id", "line_no", "quantity", null, null));
        target("SALE_DELIVERY", "SALE_REPAIR", "售后维修单",
                "select repair_id as id, repair_no as no, repair_date as dt, status as st, total_fee as amt"
                        + " from sale_repair where source_bill_no = ?",
                detail("sale_repair_item", "repair_id", "line_no", "quantity", null, null));

        // 售后维修单 → 销售换货单 / 销售退货单（2026-11-19 补全去向）：
        // 「转换新 / 转退货」后维修单表头记录下游单号（transferred_bill_no）与类型（transferred_bill_type），
        // 通用去向查询的绑定参数是维修单号，故按 维修单号 → 转入单号 子查询反查下游单据。
        target("SALE_REPAIR", "SALE_EXCHANGE", "销售换货单",
                "select exchange_id as id, exchange_no as no, exchange_date as dt, status as st, net_amount as amt"
                        + " from sale_exchange where exchange_no = (select transferred_bill_no from sale_repair"
                        + " where repair_no = ? and transferred_bill_type = '销售换货单')",
                detail("sale_exchange_item", "exchange_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));
        target("SALE_REPAIR", "SALE_RETURN", "销售退货单",
                "select return_id as id, return_no as no, return_date as dt, status as st, net_amount as amt"
                        + " from sale_return where return_no = (select transferred_bill_no from sale_repair"
                        + " where repair_no = ? and transferred_bill_type = '销售退货单')",
                detail("sale_return_item", "return_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        // 销售订单 → 销售发票（2026-10-10 补登记）：前端下推关系早已存在（pushRelations SaleOrder→SaleInvoice，
        // 下推时明细写 source_id=订单主键），但此处漏登记 → 列表「下推详情」弹窗看不到订单直推的发票。
        target("SALE_ORDER", "SALE_INVOICE", "销售发票",
                "select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt"
                        + " from sale_invoice where source_bill_no = ?",
                detail("sale_invoice_item", "invoice_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("SALE_ORDER", "PURCHASE_ORDER", "采购订单",
                "select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt"
                        + " from purchase_order where source_bill_no = ?",
                detail("purchase_order_item", "order_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("SALE_ORDER", "ACCOUNT_RECEIVABLE", "应收单",
                "select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt"
                        + " from account_receivable where source_bill_no = ?");

        target("SALE_ORDER", "PRODUCTION_ORDER", "生产订单",
                "select order_id as id, order_no as no, order_date as dt, status as st, null as amt"
                        + " from production_order where source_no = ? and source_type = '销售订单'",
                detail("production_order_item", "order_id", "line_no", "required_quantity", "unit_cost", "total_cost"));

        target("SALE_ORDER", "MRP_PLAN", "MRP计划",
                "select p.id as id, p.plan_no as no, p.plan_date as dt, p.plan_status as st, null as amt"
                        + " from mrp_plan p where p.id in (select i.plan_id from mrp_plan_item i where i.sale_order_no = ?)",
                detail("mrp_plan_item", "plan_id", null, "plan_quantity", null, null, "sale_order_item_id"));

        target("SALE_DELIVERY", "SALE_INVOICE", "销售发票",
                "select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt"
                        + " from sale_invoice where source_bill_no = ?",
                detail("sale_invoice_item", "invoice_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("SALE_DELIVERY", "ACCOUNT_RECEIVABLE", "应收单",
                "select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt"
                        + " from account_receivable where source_bill_no = ?");

        target("SALE_INVOICE", "ACCOUNT_RECEIVABLE", "应收单",
                "select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt"
                        + " from account_receivable where source_bill_no = ?");

        // 收款单可直接以"销售发票"为来源（应收自动生成关闭 finance.ar.source_bill_type=none 时就是这条路径），
        // 也可经"应收单"间接关联；两条都要注册，否则发票的「去向单据」会看不到收款单。
        target("SALE_INVOICE", "PAYMENT_RECEIPT", "收款单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from cash_receipt where source_bill_no = ?");

        target("SALE_RETURN", "SALE_INVOICE", "销售发票（红字）",
                "select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt"
                        + " from sale_invoice where source_bill_no = ?",
                detail("sale_invoice_item", "invoice_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("ACCOUNT_RECEIVABLE", "PAYMENT_RECEIPT", "收款单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from cash_receipt where source_bill_no = ?");

        // 2026-10-28：手动选来源单的收款只写核销明细（表头来源单号为空），
        // 这四条登记保证"销售订单/出库单"的「去向单据」也能看到收款单（行级匹配由服务层统一补齐）。
        target("SALE_ORDER", "PAYMENT_RECEIPT", "收款单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from cash_receipt where source_bill_no = ?");
        target("SALE_DELIVERY", "PAYMENT_RECEIPT", "收款单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from cash_receipt where source_bill_no = ?");

        // ---------------- 采购链路 ----------------
        target("PURCHASE_REQUEST", "PURCHASE_ORDER", "采购订单",
                "select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt"
                        + " from purchase_order where source_bill_no = ?",
                detail("purchase_order_item", "order_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("PURCHASE_CONTRACT", "PURCHASE_ORDER", "采购订单",
                "select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt"
                        + " from purchase_order where source_bill_no = ?",
                detail("purchase_order_item", "order_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("PURCHASE_ORDER", "PURCHASE_INBOUND", "采购入库单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from purchase_receipt where source_bill_no = ?",
                detail("purchase_receipt_item", "receipt_id", "line_no", "quantity", "unit_price", "amount", "source_item_id"));

        target("PURCHASE_ORDER", "ACCOUNT_PAYABLE", "应付单",
                "select ap_id as id, ap_no as no, ap_date as dt, status as st, total_amount as amt"
                        + " from account_payable where source_bill_no = ?");

        target("PURCHASE_INBOUND", "PURCHASE_INVOICE", "采购发票",
                "select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt"
                        + " from purchase_invoice where source_bill_no = ?",
                detail("purchase_invoice_item", "invoice_id", "line_no", "quantity", "unit_price", "amount", "source_row_no"));

        target("ACCOUNT_PAYABLE", "PAYMENT_VOUCHER", "付款单",
                "select payment_id as id, payment_no as no, payment_date as dt, status as st, total_amount as amt"
                        + " from cash_payment where source_bill_no = ?");

        // 2026-10-28：手动选来源单的付款只写核销明细，采购侧三条去向登记补齐（行级匹配由服务层统一处理）
        target("PURCHASE_ORDER", "PAYMENT_VOUCHER", "付款单",
                "select payment_id as id, payment_no as no, payment_date as dt, status as st, total_amount as amt"
                        + " from cash_payment where source_bill_no = ?");
        target("PURCHASE_INBOUND", "PAYMENT_VOUCHER", "付款单",
                "select payment_id as id, payment_no as no, payment_date as dt, status as st, total_amount as amt"
                        + " from cash_payment where source_bill_no = ?");
        target("PURCHASE_INVOICE", "PAYMENT_VOUCHER", "付款单",
                "select payment_id as id, payment_no as no, payment_date as dt, status as st, total_amount as amt"
                        + " from cash_payment where source_bill_no = ?");

        // ---------------- 生产链路 ----------------
        target("PRODUCTION_ORDER", "MATERIAL_REQUISITION", "生产领料单",
                "select requisition_id as id, requisition_no as no, requisition_date as dt, status as st, total_amount as amt"
                        + " from material_requisition where source_bill_no = ?",
                detail("material_requisition_item", "requisition_id", "line_no", "quantity", "unit_price", "amount", "source_line_no"));

        target("PRODUCTION_ORDER", "OPERATION_REPORT", "工序汇报单",
                "select report_id as id, report_no as no, report_date as dt, status as st, null as amt"
                        + " from operation_report where source_bill_no = ?");

        target("PRODUCTION_ORDER", "PRODUCTION_RECEIPT", "生产入库单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from production_receipt where source_bill_no = ?",
                detail("production_receipt_item", "receipt_id", "line_no", "quantity", "unit_price", "amount"));

        target("PRODUCTION_ORDER", "SUBCONTRACT_ORDER", "委外订单",
                "select id as id, order_no as no, order_date as dt, status as st, net_amount as amt"
                        + " from subcontract_order where source_bill_no = ?",
                detail("subcontract_order_item", "order_id", null, "quantity", "unit_price", "amount"));

        target("OPERATION_REPORT", "PRODUCTION_RECEIPT", "生产入库单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from production_receipt where source_bill_no = ?",
                detail("production_receipt_item", "receipt_id", "line_no", "quantity", "unit_price", "amount"));

        // ---------------- MRP 下推 ----------------
        target("MRP_PLAN", "PURCHASE_REQUEST", "采购申请单",
                "select request_id as id, request_no as no, request_date as dt, status as st, null as amt"
                        + " from purchase_request where source_bill_no = ?",
                detail("purchase_request_item", "request_id", "line_no", "quantity", null, null, "source_row_no"));

        target("MRP_PLAN", "PRODUCTION_ORDER", "生产订单",
                "select order_id as id, order_no as no, order_date as dt, status as st, null as amt"
                        + " from production_order where source_no = ?",
                detail("production_order_item", "order_id", "line_no", "required_quantity", "unit_cost", "total_cost"));

        target("MRP_PLAN", "SUBCONTRACT_ORDER", "委外订单",
                "select id as id, order_no as no, order_date as dt, status as st, net_amount as amt"
                        + " from subcontract_order where source_bill_no = ?",
                detail("subcontract_order_item", "order_id", null, "quantity", "unit_price", "amount"));

        // ---------------- 委外链路（发料/入库靠 order_id 关联） ----------------
        target("SUBCONTRACT_ORDER", "SUBCONTRACT_DELIVERY", "委外发料单",
                "select id as id, pick_no as no, pick_date as dt, status as st, null as amt"
                        + " from subcontract_pick where order_id in (select id from subcontract_order where order_no = ?)",
                detail("subcontract_pick_item", "pick_id", null, "quantity", null, null, "order_item_id"));

        target("SUBCONTRACT_ORDER", "SUBCONTRACT_RECEIPT", "委外入库单",
                "select id as id, inbound_no as no, inbound_date as dt, status as st, null as amt"
                        + " from subcontract_inbound where order_id in (select id from subcontract_order where order_no = ?)",
                detail("subcontract_inbound_item", "inbound_id", null, "quantity", null, "processing_fee", "order_item_id"));

        target("SUBCONTRACT_INBOUND", "ACCOUNT_PAYABLE", "应付单",
                "select ap_id as id, ap_no as no, ap_date as dt, status as st, total_amount as amt"
                        + " from account_payable where source_bill_no = ?");

        // ---------------- 打样链路 ----------------
        target("SAMPLE_ORDER", "MATERIAL_REQUISITION", "生产领料单",
                "select requisition_id as id, requisition_no as no, requisition_date as dt, status as st, total_amount as amt"
                        + " from material_requisition where source_bill_no = ?",
                detail("material_requisition_item", "requisition_id", "line_no", "quantity", "unit_price", "amount", "source_line_no"));

        target("SAMPLE_ORDER", "PRODUCTION_RECEIPT", "生产入库单",
                "select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt"
                        + " from production_receipt where source_bill_no = ?",
                detail("production_receipt_item", "receipt_id", "line_no", "quantity", "unit_price", "amount"));
    }

    // ==================================================================================
    // 三、注册/工厂/查询辅助
    // ==================================================================================

    private void register(DocDef def) {
        docByType.put(def.docType, def);
        if (def.table != null) docByTable.putIfAbsent(def.table, def);
        if (def.name != null) typeByName.put(def.name, def.docType);
        typeByName.put(def.docType.toLowerCase(), def.docType);
    }

    /** 单据类型 → 主表名（字段权限/打印等按主表自动对应 page_key；未登记返回 null） */
    public String tableOf(String docType) {
        if (docType == null) return null;
        DocDef def = docByType.get(docType.trim().toUpperCase());
        return def == null ? null : def.table;
    }

    /** 追加来源类型别名（历史取值/同义词） */
    private void alias(String name, String docType) {
        typeByName.put(name, docType);
    }

    /** DocDef 构造工厂（字段较多，集中在此便于阅读） */
    private static DocDef doc(String docType, String name, String table, String pkCol, String noCol,
                              String dateCol, String statusCol, String amountCol,
                              String srcNoCol, String srcTypeCol,
                              String itemTable, String itemPkCol, String itemFkCol, String lineCol,
                              String qtyCol, String priceCol) {
        return new DocDef(docType, name, table, pkCol, noCol, dateCol, statusCol, amountCol,
                srcNoCol, srcTypeCol, itemTable, itemPkCol, itemFkCol, lineCol, qtyCol, priceCol);
    }

    /** 去向明细 SQL（标准物料行：行号 + 物料 + 规格 + 数量 + 单价 + 金额；列名传 null 表示该表无此列） */
    private static String detail(String itemTable, String fkCol, String lineCol,
                                 String qtyCol, String priceCol, String amountCol) {
        return detail(itemTable, fkCol, lineCol, qtyCol, priceCol, amountCol, (String) null);
    }

    /**
     * 去向明细 SQL（带"下游明细行 → 本单行"的引用列）。
     *
     * @param srcRefCol 下游明细里指回本单明细的列：source_item_id（按明细ID）/ source_line_no / source_row_no（按行号）/ order_item_id 等；null=无
     */
    private static String detail(String itemTable, String fkCol, String lineCol,
                                 String qtyCol, String priceCol, String amountCol, String srcRefCol) {
        return detail(itemTable, fkCol, lineCol, qtyCol, priceCol, amountCol,
                "material_code", "material_name", "specification", srcRefCol);
    }

    private static String detail(String itemTable, String fkCol, String lineCol,
                                 String qtyCol, String priceCol, String amountCol,
                                 String codeCol, String nameCol, String specCol) {
        return detail(itemTable, fkCol, lineCol, qtyCol, priceCol, amountCol, codeCol, nameCol, specCol, null);
    }

    private static String detail(String itemTable, String fkCol, String lineCol,
                                 String qtyCol, String priceCol, String amountCol,
                                 String codeCol, String nameCol, String specCol, String srcRefCol) {
        StringBuilder sb = new StringBuilder("select ");
        sb.append(nvl(lineCol)).append(" as lineNo, ")
          .append(nvl(codeCol)).append(" as materialCode, ")
          .append(nvl(nameCol)).append(" as materialName, ")
          .append(nvl(specCol)).append(" as spec, ")
          .append(nvl(qtyCol)).append(" as qty, ")
          .append(nvl(priceCol)).append(" as price, ")
          .append(nvl(amountCol)).append(" as amount, ")
          .append(nvl(srcRefCol)).append(" as srcRef")
          .append(" from ").append(itemTable)
          .append(" where ").append(fkCol).append(" = ?");
        if (lineCol != null) sb.append(" order by ").append(lineCol);
        return sb.toString();
    }

    private static String nvl(String col) {
        return col == null ? "null" : col;
    }

    private void target(String sourceDocType, String targetDocType, String targetName, String sql) {
        target(sourceDocType, targetDocType, targetName, sql, null);
    }

    private void target(String sourceDocType, String targetDocType, String targetName, String sql, String detailSql) {
        targetsBySource.computeIfAbsent(sourceDocType, k -> new ArrayList<>())
                .add(new TargetDef(targetDocType, targetName, sql, detailSql));
    }

    /** 按单据类型取定义；未登记返回 null */
    public DocDef doc(String docType) {
        return docType == null ? null : docByType.get(docType);
    }

    /** 按业务表名取单据定义（看板下钻：数据源表 → 单据类型/单号列）；未登记返回 null */
    public DocDef docByTable(String table) {
        return table == null ? null : docByTable.get(table);
    }

    /**
     * 全部已登记单据类型（只读）。
     * 供「导入导出登记表 BillExcelRegistry × 单据台账」交叉校验使用：
     * 台账里每张单据都必须在导入导出登记表里有对应实体，否则就是"新增了单据却漏配导入导出"。
     */
    public java.util.Set<String> allDocTypes() {
        return java.util.Collections.unmodifiableSet(docByType.keySet());
    }

    /** 按中文单据名 / 编号（大小写不敏感）反查单据类型；未登记返回 null */
    public String docTypeByName(String name) {
        if (name == null) return null;
        String key = name.trim();
        String hit = typeByName.get(key);
        if (hit != null) return hit;
        return typeByName.get(key.toLowerCase());
    }

    /** 取某单据类型的去向单据定义列表（无则空列表） */
    public List<TargetDef> targets(String docType) {
        List<TargetDef> list = targetsBySource.get(docType);
        return list == null ? List.of() : list;
    }

    /** 该单据类型是否已登记（前端据此决定是否渲染来源/去向页签） */
    public boolean isRegistered(String docType) {
        return docType != null && docByType.containsKey(docType);
    }
}
