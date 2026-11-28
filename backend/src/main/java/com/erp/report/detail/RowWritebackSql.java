package com.erp.report.detail;

/**
 * 行级回写列「实时聚合」取数出口（2026-10-14 修复，唯一来源）。
 *
 * <p>背景（用户实锤反馈）：明细视图行级回写列原先直接读来源明细上的落库回写列
 * （如 sale_order_item.delivered_quantity），但 delivered_quantity 全系统没有任何回写点
 * （历史遗留死列，只有演示数据塞过假值），导致：① 真实下推并生效后列表回写数据纹丝不动；
 * ② 演示数据给无下游的单据塞了假值，列表假报"有下推关系"、点进去下游为空。</p>
 *
 * <p>修复口径 = 与列表页「下推状态」汇总服务（BillPushStatusService，2026-10-10 用户确认口径）一致：
 * <b>数量从下游单据明细实时聚合，不读来源明细上的累计回写列</b>。本类只产出 SQL 常量片段，
 * 全部表名/列名为代码常量，不存在注入面（调用处 expr 均为注册表常量）。</p>
 *
 * <p>状态过滤与后端状态工具同口径的 SQL 版：
 * 剔除 已作废/作废/VOIDED 与 草稿族（草稿/未审核/驳回/已驳回/已取消/待审核/待审批/审批中/DRAFT/PENDING/空），
 * 其余（含未知态）按"已生效类"计入数量 —— 与 BillStatusUtils.toNumber 的兜底方向一致。</p>
 *
 * <p>行匹配两级：① 行级来源主键精确匹配（下推链路写入的 source_item_id）；
 * ② 兜底 = 无行级主键但表头来源单号匹配本单 + 物料编码相同（与来源/去向面板的兜底口径一致）。</p>
 */
public final class RowWritebackSql {

    private RowWritebackSql() {}

    /** 已生效类状态过滤（h = 下游单据表头别名） */
    private static String eff(String h) {
        return " (" + h + ".status IS NOT NULL AND " + h + ".status <> '' AND " + h
                + ".status NOT IN ('草稿','未审核','驳回','已驳回','已取消','待审核','待审批','审批中'"
                + ",'已作废','作废','DRAFT','PENDING','VOIDED')) ";
    }

    /** 行匹配条件（ik = 下游明细行级来源主键列, iv = 本单明细主键表达式, sn = 下游明细表头来源单号列, snv = 本单单号表达式, mc = 下游明细物料列, mcv = 本单物料表达式） */
    private static String match(String ik, String iv, String sn, String snv, String mc, String mcv) {
        return " ((" + ik + " = " + iv + ") OR (" + ik + " IS NULL AND " + sn + " = " + snv
                + " AND " + mc + " = " + mcv + ")) ";
    }

    /** 数量取整两位（与来源明细数量显示精度一致，避免 27.0000 这类长度漂移） */
    private static String num(String sub) {
        return " ROUND(COALESCE((" + sub + "), 0), 2) ";
    }

    /** 汇总片段（qty 列必须包 COALESCE(col,0) 由调用方传入） */
    private static String sum(String itemTable, String itemAlias, String headTable, String headAlias,
                              String headJoinCol, String headPk, String qtyExpr,
                              String matchExpr) {
        return "SELECT COALESCE(SUM(" + qtyExpr + "),0) FROM " + itemTable + " " + itemAlias
                + " JOIN " + headTable + " " + headAlias + " ON " + headAlias + "." + headJoinCol
                + " = " + itemAlias + "." + headPk
                + " WHERE " + matchExpr + " AND " + eff(headAlias);
    }

    // ==================== 销售订单行：下游 = 出库 / 发票 / 退货 / 生产 ====================

    /** 已发数量：销售出库单明细实时聚合（iv=订单明细主键表达式, snv=订单号表达式, mcv=订单物料表达式） */
    public static String saleDeliveryQty(String iv, String snv, String mcv) {
        return num(sum("sale_delivery_item", "di", "sale_delivery", "dh", "delivery_id", "delivery_id",
                "COALESCE(di.quantity,0)",
                match("di.source_item_id", iv, "di.source_bill_no", snv, "di.material_code", mcv)));
    }

    /** 已开数量：销售发票明细实时聚合 */
    public static String saleInvoiceQty(String iv, String snv, String mcv) {
        return num(sum("sale_invoice_item", "sii", "sale_invoice", "sih", "invoice_id", "invoice_id",
                "COALESCE(sii.quantity,0)",
                match("sii.source_item_id", iv, "sii.source_bill_no", snv, "sii.material_code", mcv)));
    }

    /** 已退数量：销售退货单明细实时聚合 */
    public static String saleReturnQty(String iv, String snv, String mcv) {
        return num(sum("sale_return_item", "sri", "sale_return", "srh", "return_id", "return_id",
                "COALESCE(sri.quantity,0)",
                match("sri.source_item_id", iv, "sri.source_bill_no", snv, "sri.material_code", mcv)));
    }

    /**
     * 销售订单行的已开票数量（2026-11-28 修复）：
     * 发票既可能直接引用订单明细（source_bill_type=销售订单），也可能引用出库明细（source_bill_type=销售出库单）。
     * 正常「订单→出库→发票」链路是两跳，必须经 sale_delivery_item 回溯到订单明细，否则已开票恒为 0。
     */
    public static String saleOrderInvoiceQty(String iv, String snv, String mcv) {
        String matchExpr = " (("
                + "sii.source_item_id = " + iv + " AND sii.source_bill_type = '销售订单'"
                + ") OR (sii.source_bill_type IN ('销售出库单','销售出库') AND EXISTS ("
                + "SELECT 1 FROM sale_delivery_item sdi WHERE sdi.delivery_item_id = sii.source_item_id"
                + " AND sdi.source_item_id = " + iv + " AND sdi.source_bill_type = '销售订单'))"
                + " OR (sii.source_item_id IS NULL AND sii.source_bill_no = " + snv
                + " AND sii.material_code = " + mcv + ")) ";
        return num(sum("sale_invoice_item", "sii", "sale_invoice", "sih", "invoice_id", "invoice_id",
                "COALESCE(sii.quantity,0)", matchExpr));
    }

    /**
     * 销售订单行的已退货数量（2026-11-28 修复）：
     * 同发票口径，退货单可能直接引用订单明细，也可能引用出库明细，需经出库明细两跳回溯。
     */
    public static String saleOrderReturnQty(String iv, String snv, String mcv) {
        String matchExpr = " (("
                + "sri.source_item_id = " + iv + " AND sri.source_bill_type = '销售订单'"
                + ") OR (sri.source_bill_type IN ('销售出库单','销售出库') AND EXISTS ("
                + "SELECT 1 FROM sale_delivery_item sdi WHERE sdi.delivery_item_id = sri.source_item_id"
                + " AND sdi.source_item_id = " + iv + " AND sdi.source_bill_type = '销售订单'))"
                + " OR (sri.source_item_id IS NULL AND sri.source_bill_no = " + snv
                + " AND sri.material_code = " + mcv + ")) ";
        return num(sum("sale_return_item", "sri", "sale_return", "srh", "return_id", "return_id",
                "COALESCE(sri.quantity,0)", matchExpr));
    }

    /**
     * 已产数量：生产工单（表头按 来源单号+来源类型=销售订单 匹配，数量取「成品明细」行）。
     * 【2026-10-19 修复"已产恒0"】原实现查的是 production_order_item（工单原料/用料明细，BOM 子件），
     * 而订单行物料对应的是工单成品明细 work_order_product —— 查错表导致下推生效后"已产"永远是 0。
     * 行匹配两级：① 成品行来源主键（source_item_id = 订单明细主键，下推进入/选单带入时写入）；
     * ② 兜底 = 无行级主键但成品行物料编码相同（服务端直推接口的表头兜底成品行属此类）。
     */
    public static String saleProduceQty(String iv, String snv, String mcv) {
        return num("SELECT COALESCE(SUM(COALESCE(wop.quantity,0)),0)"
                + " FROM work_order_product wop JOIN production_order po ON po.order_id = wop.order_id"
                + " WHERE po.source_no = " + snv + " AND po.source_type = '销售订单'"
                + " AND ((wop.source_item_id = " + iv + ")"
                + " OR (wop.source_item_id IS NULL AND wop.material_code = " + mcv + "))"
                + " AND " + eff("po"));
    }

    // ==================== 销售出库单 / 退货单行：下游 = 发票 ====================

    /** 出库/退货明细的已开票数量（iv=出库或退货明细主键表达式, snv=本单单号表达式, mcv=本单物料表达式） */
    public static String deliveryInvoiceQty(String iv, String snv, String mcv) {
        return saleInvoiceQty(iv, snv, mcv);
    }

    // ==================== 销售报价单行：下游 = 销售订单 ====================

    /** 已下单数量：销售订单明细实时聚合 */
    public static String saleOrderQty(String iv, String snv, String mcv) {
        return num(sum("sale_order_item", "soi", "sale_order", "soh", "order_id", "order_id",
                "COALESCE(soi.quantity,0)",
                match("soi.source_item_id", iv, "soi.source_bill_no", snv, "soi.material_code", mcv)));
    }

    // ==================== 采购订单行：下游 = 入库 / 发票 ====================

    /** 已入数量：采购入库单明细实时聚合 */
    public static String purchaseInboundQty(String iv, String snv, String mcv) {
        return num(sum("purchase_receipt_item", "pri", "purchase_receipt", "prh", "receipt_id", "receipt_id",
                "COALESCE(pri.quantity,0)",
                match("pri.source_item_id", iv, "pri.source_bill_no", snv, "pri.material_code", mcv)));
    }

    /** 已开数量：采购发票明细实时聚合（发票行级引用为 source_row_no=行号；表头来源单号=订单号） */
    public static String purchaseInvoiceQty(String lineNoExpr, String snv, String mcv) {
        return num("SELECT COALESCE(SUM(COALESCE(pii.quantity,0)),0)"
                + " FROM purchase_invoice_item pii JOIN purchase_invoice pih ON pih.invoice_id = pii.invoice_id"
                + " WHERE pih.source_bill_no = " + snv
                + " AND ((pii.source_row_no = " + lineNoExpr + ") OR (pii.source_row_no IS NULL AND pii.material_code = " + mcv + "))"
                + " AND " + eff("pih"));
    }

    // ==================== 2026-10-14 第二批：把"普通列读落库累计列"的全部同病列改实时聚合 ====================

    /** 已转退货数量：采购退货单明细实时聚合（采购订单行） */
    public static String purchaseReturnQty(String iv, String snv, String mcv) {
        return num(sum("purchase_return_item", "pui", "purchase_return", "puh", "return_id", "return_id",
                "COALESCE(pui.quantity,0)",
                match("pui.source_item_id", iv, "pui.source_bill_no", snv, "pui.material_code", mcv)));
    }

    /** 合同→订单→入库 两跳：采购合同的已入库数量（入库明细行级来源主键 = 订单明细主键） */
    public static String purchaseContractInboundQty(String contractItemPk, String mcv) {
        String orderNos = "(SELECT po.order_no FROM purchase_order_item poi"
                + " JOIN purchase_order po ON po.order_id = poi.order_id"
                + " WHERE poi.source_item_id = " + contractItemPk + ")";
        return num("SELECT COALESCE(SUM(COALESCE(pri.quantity,0)),0)"
                + " FROM purchase_receipt_item pri JOIN purchase_receipt prh ON prh.receipt_id = pri.receipt_id"
                + " WHERE " + eff("prh") + " AND ("
                + "  (pri.source_item_id IN (SELECT poi.order_item_id FROM purchase_order_item poi"
                + "     WHERE poi.source_item_id = " + contractItemPk + "))"
                + "  OR (pri.source_item_id IS NULL AND pri.material_code = " + mcv
                + "     AND prh.source_bill_no IN " + orderNos + "))");
    }

    /** 合同→订单→发票 两跳：采购合同的已开票数量（发票行级引用 = 订单号+行号） */
    public static String purchaseContractInvoiceQty(String contractItemPk, String mcv) {
        String orderNos = "(SELECT po.order_no FROM purchase_order_item poi"
                + " JOIN purchase_order po ON po.order_id = poi.order_id"
                + " WHERE poi.source_item_id = " + contractItemPk + ")";
        return num("SELECT COALESCE(SUM(COALESCE(pii.quantity,0)),0)"
                + " FROM purchase_invoice_item pii JOIN purchase_invoice pih ON pih.invoice_id = pii.invoice_id"
                + " WHERE " + eff("pih") + " AND ("
                + "  EXISTS (SELECT 1 FROM purchase_order_item poi JOIN purchase_order po ON po.order_id = poi.order_id"
                + "    WHERE poi.source_item_id = " + contractItemPk
                + "    AND po.order_no = pih.source_bill_no AND poi.line_no = pii.source_row_no)"
                + "  OR (pii.material_code = " + mcv + " AND pih.source_bill_no IN " + orderNos + "))");
    }

    /**
     * 生产工单原料行的 领/退/补 实时聚合（billTypes 如 "'PICK','SUPPLEMENT'"）。
     * 领料/退料/补料共用 material_requisition 表（bill_type 区分），行级 source_line_no = 工单原料行行号，
     * 表头 source_bill_type = '生产工单'（与下推链路写入值一致，WorkOrderServiceImpl.pushToMaterialRequisition）。
     */
    public static String workOrderReqQty(String billTypes, String lineNoExpr, String snv, String mcv) {
        return num(reqSub(billTypes, lineNoExpr, snv, mcv));
    }

    /** 领/退/补聚合子查询（不带取整包装，供净耗用相减复用） */
    private static String reqSub(String billTypes, String lineNoExpr, String snv, String mcv) {
        return "SELECT COALESCE(SUM(COALESCE(ri.quantity,0)),0)"
                + " FROM material_requisition_item ri"
                + " JOIN material_requisition rh ON rh.requisition_id = ri.requisition_id"
                + " WHERE rh.source_bill_no = " + snv + " AND rh.source_bill_type = '生产工单'"
                + " AND rh.bill_type IN (" + billTypes + ") AND " + eff("rh")
                + " AND ((ri.source_line_no = " + lineNoExpr + ")"
                + " OR (ri.source_line_no IS NULL AND ri.material_code = " + mcv + "))";
    }

    /**
     * 已耗用数量 = 已发数量 − 已退数量（净耗用，2026-10-14 用户拍板口径）。
     * 已发 = 领料(PICK)+补料(SUPPLEMENT) 生效数量，已退 = 退料(RETURN) 生效数量；负数按 0 封底。
     */
    public static String workOrderConsumedQty(String lineNoExpr, String snv, String mcv) {
        return num("GREATEST((" + reqSub("'PICK','SUPPLEMENT'", lineNoExpr, snv, mcv) + ")"
                + " - (" + reqSub("'RETURN'", lineNoExpr, snv, mcv) + "), 0)");
    }

    /** 生产工单表头级领料合计（表头「已领料数量」= 该工单全部生效领料单(PICK)数量合计） */
    public static String workOrderPickQtyDoc(String snv) {
        return num("SELECT COALESCE(SUM(COALESCE(ri.quantity,0)),0)"
                + " FROM material_requisition_item ri"
                + " JOIN material_requisition rh ON rh.requisition_id = ri.requisition_id"
                + " WHERE rh.source_bill_no = " + snv + " AND rh.source_bill_type = '生产工单'"
                + " AND rh.bill_type = 'PICK' AND " + eff("rh"));
    }

    // ==================== 2026-10-14 第四批：工单表头合格/报废接报工链路实时聚合（用户拍板） ====================

    /**
     * 工单表头「合格数量（整单）」= 该工单全部**生效工序汇报单**的合格数合计。
     * 关联用 production_order_id（汇报单落了工单主键，比单号匹配更精确）；草稿/已作废汇报不计。
     */
    public static String opReportQualifiedQty(String orderIdExpr) {
        return num("SELECT COALESCE(SUM(COALESCE(rh.qualified_quantity,0)),0)"
                + " FROM operation_report rh WHERE rh.production_order_id = " + orderIdExpr
                + " AND " + eff("rh"));
    }

    /**
     * 工单表头「报废数量（整单）」= 该工单全部工序行的报废数合计（非"已取消"工序）。
     * 报废数的真实落点在工序行 work_order_operation.scrap_quantity（判废确认/报废计入都累加在此），
     * 工序汇报单本身没有报废列 —— 这是报工链路里报废的唯一权威来源。
     */
    public static String operationScrapQty(String orderIdExpr) {
        return num("SELECT COALESCE(SUM(COALESCE(wo.scrap_quantity,0)),0)"
                + " FROM work_order_operation wo WHERE wo.order_id = " + orderIdExpr
                + " AND (wo.operation_status IS NULL OR wo.operation_status NOT IN ('已取消','已作废','作废'))");
    }
}
