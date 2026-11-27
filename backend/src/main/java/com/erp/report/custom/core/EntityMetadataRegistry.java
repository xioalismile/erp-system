package com.erp.report.custom.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.export.annotation.ExportField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义报表数据源元数据注册表（白名单）
 *
 * - 数据源 key = 实体 @TableName 表名，仅此处注册的表可作为数据源
 * - 字段元数据运行时反射实体类生成：列名取 @TableField/@TableId，
 *   中文标签优先取 @ExportField.label，否则查通用字段字典，最后回退字段名
 * - 明细→表头 关联自动推导：明细表存在与表头主键同名的列，或
 *   表头主键 Java 字段 snake 化后在明细表存在（如 inbound_id）
 * - 基础资料关联自动推导：主表/表头含 customer_id、material_id 等 FK 列时自动注册 join
 *
 * 安全约定：SqlBuilder 只接受本注册表输出的表名/列名/别名，任何用户输入
 * 不经本注册表校验不得拼接进 SQL。
 */
@Slf4j
@Component
public class EntityMetadataRegistry {

    /** 字段元数据（field=Java字段名/camelCase 结果key，column=数据库列名） */
    public static class FieldMeta {
        public final String field;
        public final String column;
        public final String label;
        public final String type; // string/date/datetime/amount/number/int/bool
        public final boolean defaultRequired;

        public FieldMeta(String field, String column, String label, String type) {
            this(field, column, label, type, false);
        }

        public FieldMeta(String field, String column, String label, String type, boolean defaultRequired) {
            this.field = field;
            this.column = column;
            this.label = label;
            this.type = type;
            this.defaultRequired = defaultRequired;
        }
    }

    /** 关联元数据（LEFT JOIN，alias 由注册表统一分配） */
    public static class JoinMeta {
        public final String key;          // 前端引用的 source key
        public final String name;         // 显示名
        public final String table;
        public final String alias;
        public final String onSql;        // 例如 t1.order_id = t2.order_id（由结构化字段派生）
        // 结构化 ON（跨单据关联克隆 join 树时重映射别名用）
        public final String leftAlias;    // ON 左侧表别名（t1 或本数据源内其它 join 的别名）
        public final String leftColumn;   // ON 左侧列名
        public final String rightAlias;   // 恒等于本 join 的 alias
        public final String rightColumn;  // ON 右侧列名（通常为目标表主键）
        public final Map<String, FieldMeta> fields = new LinkedHashMap<>();
        /** 关联分组：表头 / 基础资料 / 来源单据（前端下拉分组展示用） */
        public String group = "基础资料";
        /** 额外的 ON 条件（如来源单据类型限定），由注册表生成、不含用户输入 */
        public String extraOn;

        public JoinMeta(String key, String name, String table, String alias,
                        String leftAlias, String leftColumn, String rightColumn) {
            this.key = key;
            this.name = name;
            this.table = table;
            this.alias = alias;
            this.leftAlias = leftAlias;
            this.leftColumn = leftColumn;
            this.rightAlias = alias;
            this.rightColumn = rightColumn;
            this.onSql = leftAlias + "." + leftColumn + " = " + alias + "." + rightColumn;
        }
    }

    /** 数据源元数据 */
    public static class DataSourceMeta {
        public final String key;      // 表名
        public final String name;     // 中文名
        public final String module;   // 所属模块（数据权限用）
        public final String table;
        /** 主键列名（@TableId 对应列；自定义字段值表按 entity_id 关联时使用） */
        public String pkColumn;
        public final Map<String, FieldMeta> fields = new LinkedHashMap<>();
        public final Map<String, JoinMeta> joins = new LinkedHashMap<>(); // key -> join（含 join 自身字段）
        /** 明细表反向关联（表头→明细，一对多）：仅聚合挂载子查询内使用，不进平铺 join 树，杜绝行翻倍 */
        public final Map<String, JoinMeta> detailJoins = new LinkedHashMap<>();

        public DataSourceMeta(String key, String name, String module, String table) {
            this.key = key;
            this.name = name;
            this.module = module;
            this.table = table;
        }
    }

    // ==================== 基础资料 join 规则（FK列名 → 目标表/名称列/显示名） ====================
    private static final Map<String, String[]> BASE_JOIN_RULES = new LinkedHashMap<>();
    static {
        // {FK列名: joinKey, 目标表, 名称列, 显示名, 目标表主键列}
        // 第5项必须与目标表实体 @TableId 一致：employee=employee_id、department=dept_id、warehouse=stock_id
        BASE_JOIN_RULES.put("customer_id",     new String[]{"customer",     "customer",       "customer_name", "客户名称", "customer_id"});
        BASE_JOIN_RULES.put("supplier_id",     new String[]{"supplier",     "supplier",       "supplier_name", "供应商名称", "supplier_id"});
        BASE_JOIN_RULES.put("material_id",     new String[]{"material",     "material",       "material_name", "物料名称", "material_id"});
        BASE_JOIN_RULES.put("saler_id",        new String[]{"saler",        "employee",       "employee_name", "销售员", "employee_id"});
        BASE_JOIN_RULES.put("buyer_id",        new String[]{"buyer",        "employee",       "employee_name", "采购员", "employee_id"});
        BASE_JOIN_RULES.put("employee_id",     new String[]{"employee",     "employee",       "employee_name", "员工", "employee_id"});
        BASE_JOIN_RULES.put("department_id",   new String[]{"department",   "department",     "dept_name",     "部门", "dept_id"});
        BASE_JOIN_RULES.put("warehouse_id",    new String[]{"warehouse",    "warehouse",      "stock_name",    "仓库", "stock_id"});
        BASE_JOIN_RULES.put("out_warehouse_id",new String[]{"outWarehouse", "warehouse",      "stock_name",    "调出仓库", "stock_id"});
        BASE_JOIN_RULES.put("in_warehouse_id", new String[]{"inWarehouse",  "warehouse",      "stock_name",    "调入仓库", "stock_id"});
        BASE_JOIN_RULES.put("unit_id",         new String[]{"unit",         "unit_of_measure","unit_name",     "计量单位", "unit_id"});
    }

    /**
     * 上游「来源单据」关联规则（2026-11-06 新增）。
     * <p>单据之间的上下游关系记在来源单号 + 来源类型上（不是外键），这里显式登记，
     * 生成可选的「来源：XX」关联，供自定义看板做多级穿插查询。</p>
     * <p>每条 = 本表, 来源单号列, 来源类型列, 目标表, 目标单号列, 来源类型取值, 显示名</p>
     */
    private static final List<String[]> UPSTREAM_RULES = new ArrayList<>();
    static {
        // 销售链路
        upstream("sale_order", "source_bill_no", "source_bill_type", "sale_quote", "quote_no", "销售报价单", "销售报价单,SALE_QUOTE");
        upstream("sale_order", "source_bill_no", "source_bill_type", "sale_contract", "contract_no", "销售合同", "销售合同,SALE_CONTRACT");
        upstream("sale_delivery", "source_bill_no", "source_bill_type", "sale_order", "order_no", "销售订单", "销售订单,SALE_ORDER");
        upstream("sale_invoice", "source_bill_no", "source_bill_type", "sale_delivery", "delivery_no", "销售出库单", "销售出库单,SALE_DELIVERY");
        upstream("sale_invoice", "source_bill_no", "source_bill_type", "sale_order", "order_no", "销售订单", "销售订单,SALE_ORDER");
        upstream("sale_invoice", "source_bill_no", "source_bill_type", "sale_return", "return_no", "销售退货单", "销售退货单,SALE_RETURN");
        upstream("sale_return", "source_bill_no", "source_bill_type", "sale_delivery", "delivery_no", "销售出库单", "销售出库单,SALE_DELIVERY");
        upstream("sale_return", "source_bill_no", "source_bill_type", "sale_order", "order_no", "销售订单", "销售订单,SALE_ORDER");
        // 替换货（2026-11-17）：换货单来源于销售订单
        upstream("sale_exchange", "source_bill_no", "source_bill_type", "sale_order", "order_no", "销售订单", "销售订单,SALE_ORDER");
        // 售后维修（2026-11-18）：维修单来源于销售订单或销售出库单
        upstream("sale_repair", "source_bill_no", "source_bill_type", "sale_order", "order_no", "销售订单", "销售订单,SALE_ORDER");
        upstream("sale_repair", "source_bill_no", "source_bill_type", "sale_delivery", "delivery_no", "销售出库单", "销售出库单,SALE_DELIVERY");
        // 出库明细行级的来源（历史数据 source_type 有 sell / 销售订单 两种写法）
        upstream("sale_delivery_item", "source_bill_no", "source_type", "sale_order", "order_no", "销售订单", "销售订单,sell,SALE_ORDER");
        // 采购链路
        upstream("purchase_order", "source_bill_no", "source_bill_type", "purchase_request", "request_no", "采购申请单", "采购申请单,PURCHASE_REQUEST");
        upstream("purchase_order", "source_bill_no", "source_bill_type", "purchase_contract", "contract_no", "采购合同", "采购合同,PURCHASE_CONTRACT");
        upstream("purchase_receipt", "source_bill_no", "source_type", "purchase_order", "order_no", "采购订单", "采购订单,PURCHASE_ORDER");
        upstream("purchase_invoice", "source_bill_no", "source_bill_type", "purchase_receipt", "receipt_no", "采购入库单", "采购入库单,PURCHASE_INBOUND");
        upstream("purchase_invoice", "source_bill_no", "source_bill_type", "purchase_order", "order_no", "采购订单", "采购订单,PURCHASE_ORDER");
        upstream("purchase_return", "source_bill_no", "source_bill_type", "purchase_receipt", "receipt_no", "采购入库单", "采购入库单,PURCHASE_INBOUND");
        upstream("purchase_return", "source_bill_no", "source_bill_type", "purchase_order", "order_no", "采购订单", "采购订单,PURCHASE_ORDER");
        // 生产 / 领料链路
        upstream("production_order", "source_no", "source_type", "sale_order", "order_no", "销售订单", "销售订单,SALE_ORDER");
        upstream("production_receipt", "source_bill_no", "source_bill_type", "production_order", "order_no", "生产工单", "生产工单,PRODUCTION_ORDER");
        upstream("material_requisition", "source_bill_no", "source_bill_type", "production_order", "order_no", "生产工单", "生产工单,PRODUCTION_ORDER");
        // 财务收款（来源是销售订单）
        upstream("cash_receipt", "source_bill_no", "source_bill_type", "sale_order", "order_no", "销售订单", "销售订单,SALE_ORDER");
    }

    private static void upstream(String table, String srcNoCol, String srcTypeCol,
                                 String targetTable, String targetNoCol, String name, String typeValues) {
        UPSTREAM_RULES.add(new String[]{table, srcNoCol, srcTypeCol, targetTable, targetNoCol, name, typeValues});
    }

    /** 通用字段中文字典（无 @ExportField 的字段兜底，key=Java字段名） */
    private static final Map<String, String> COMMON_LABELS = new HashMap<>();
    static {
        COMMON_LABELS.put("id", "ID");
        COMMON_LABELS.put("orderId", "订单ID"); COMMON_LABELS.put("orderNo", "订单号"); COMMON_LABELS.put("orderDate", "订单日期");
        COMMON_LABELS.put("quoteId", "报价ID"); COMMON_LABELS.put("quoteNo", "报价单号");
        COMMON_LABELS.put("contractId", "合同ID"); COMMON_LABELS.put("contractNo", "合同号");
        COMMON_LABELS.put("requestId", "申请ID"); COMMON_LABELS.put("requestNo", "申请单号");
        COMMON_LABELS.put("receiptId", "入库ID"); COMMON_LABELS.put("receiptNo", "入库单号");
        COMMON_LABELS.put("invoiceId", "发票ID"); COMMON_LABELS.put("invoiceNo", "发票号");
        COMMON_LABELS.put("deliveryId", "出库ID"); COMMON_LABELS.put("deliveryNo", "出库单号");
        COMMON_LABELS.put("returnId", "退货ID"); COMMON_LABELS.put("returnNo", "退货单号");
        COMMON_LABELS.put("transferId", "调拨ID"); COMMON_LABELS.put("transferNo", "调拨单号");
        COMMON_LABELS.put("checkId", "盘点ID"); COMMON_LABELS.put("checkNo", "盘点单号");
        COMMON_LABELS.put("issueId", "出库ID"); COMMON_LABELS.put("issueNo", "出库单号");
        COMMON_LABELS.put("requisitionId", "领料ID"); COMMON_LABELS.put("requisitionNo", "领料单号");
        COMMON_LABELS.put("voucherId", "凭证ID"); COMMON_LABELS.put("voucherNo", "凭证号");
        COMMON_LABELS.put("sampleId", "打样ID"); COMMON_LABELS.put("sampleNo", "打样单号");
        COMMON_LABELS.put("lineNo", "行号");
        COMMON_LABELS.put("materialId", "物料ID"); COMMON_LABELS.put("materialCode", "物料编码");
        COMMON_LABELS.put("materialName", "物料名称"); COMMON_LABELS.put("specification", "规格");
        COMMON_LABELS.put("unitId", "单位ID"); COMMON_LABELS.put("unit", "单位");
        COMMON_LABELS.put("quantity", "数量"); COMMON_LABELS.put("unitPrice", "单价");
        // 质检（qc_record / qc_record_item）：实体未加 @ExportField，这里补中文名，避免自定义看板字段按钮显示原始键名
        COMMON_LABELS.put("recordNo", "检验单号"); COMMON_LABELS.put("qcType", "质检类型");
        COMMON_LABELS.put("inspectQty", "检验数"); COMMON_LABELS.put("qualifiedQty", "合格数");
        COMMON_LABELS.put("defectQty", "不合格数"); COMMON_LABELS.put("inspectTime", "检验时间");
        COMMON_LABELS.put("inspectorName", "检验员"); COMMON_LABELS.put("inspectResult", "判定结果");
        COMMON_LABELS.put("handleType", "不合格处理方式");
        COMMON_LABELS.put("handleOperationName", "返工回退工序"); COMMON_LABELS.put("handleRemark", "处理备注");
        COMMON_LABELS.put("operationName", "工序名称"); COMMON_LABELS.put("operationRowId", "工序行ID");
        COMMON_LABELS.put("recordId", "检验记录ID"); COMMON_LABELS.put("templateId", "质检模板ID");
        COMMON_LABELS.put("inspectorId", "检验人ID"); COMMON_LABELS.put("itemName", "检验项目");
        COMMON_LABELS.put("standardValue", "标准值"); COMMON_LABELS.put("measuredValue", "实测值");
        COMMON_LABELS.put("upperLimit", "上限"); COMMON_LABELS.put("lowerLimit", "下限");
        COMMON_LABELS.put("images", "留证图片");
        COMMON_LABELS.put("batchId", "批次ID"); COMMON_LABELS.put("batchNo", "批次号");
        COMMON_LABELS.put("reviewerId", "复核人ID"); COMMON_LABELS.put("reviewerName", "复核人");
        // 判定结果字段在实体里叫 result（与通用 result 冲突，单独在下方按实体兜底）
        COMMON_LABELS.put("amount", "金额"); COMMON_LABELS.put("taxRate", "税率");
        COMMON_LABELS.put("taxAmount", "税额"); COMMON_LABELS.put("netAmount", "净额");
        COMMON_LABELS.put("discountRate", "折扣率(%)"); COMMON_LABELS.put("discountAmount", "折扣额");
        COMMON_LABELS.put("avgCost", "平均成本");
        COMMON_LABELS.put("deliveredQuantity", "已发货数量");
        COMMON_LABELS.put("warehouseId", "仓库ID"); COMMON_LABELS.put("warehouseName", "仓库名称");
        COMMON_LABELS.put("stockId", "库存ID");
        COMMON_LABELS.put("customerId", "客户ID"); COMMON_LABELS.put("customerCode", "客户编码");
        COMMON_LABELS.put("customerName", "客户名称"); COMMON_LABELS.put("customerShortName", "客户简称");
        COMMON_LABELS.put("supplierId", "供应商ID"); COMMON_LABELS.put("supplierName", "供应商名称");
        COMMON_LABELS.put("salerId", "销售员ID"); COMMON_LABELS.put("salerName", "销售员");
        COMMON_LABELS.put("buyerId", "采购员ID"); COMMON_LABELS.put("buyerName", "采购员");
        COMMON_LABELS.put("departmentId", "部门ID"); COMMON_LABELS.put("departmentName", "部门");
        COMMON_LABELS.put("approvalStatus", "审核状态"); COMMON_LABELS.put("status", "状态");
        COMMON_LABELS.put("closeStatus", "关闭状态");
        COMMON_LABELS.put("remark", "备注"); COMMON_LABELS.put("createdBy", "创建人");
        COMMON_LABELS.put("result", "结果");
        COMMON_LABELS.put("createdAt", "创建时间"); COMMON_LABELS.put("updatedAt", "更新时间");
        COMMON_LABELS.put("sourceBillType", "源单类型"); COMMON_LABELS.put("sourceBillNo", "源单号");
        COMMON_LABELS.put("sourceDocId", "源单ID"); COMMON_LABELS.put("sourceId", "源单ID");
        COMMON_LABELS.put("sourceItemId", "源单明细ID");
        // 销售换货单 / 售后维修单（实体未加 @ExportField，这里补中文名，避免字段权限页显示原始键名）
        COMMON_LABELS.put("exchangeId", "换货单ID"); COMMON_LABELS.put("exchangeNo", "换货单号");
        COMMON_LABELS.put("exchangeDate", "换货日期"); COMMON_LABELS.put("exchangeItemId", "换货明细ID");
        COMMON_LABELS.put("replacementMaterialId", "替换货品ID");
        COMMON_LABELS.put("originMaterialId", "原货品ID"); COMMON_LABELS.put("originMaterialCode", "原货品编码");
        COMMON_LABELS.put("originMaterialName", "原货品名称"); COMMON_LABELS.put("originSpecification", "原货品规格");
        COMMON_LABELS.put("originUnitId", "原货品单位ID"); COMMON_LABELS.put("originUnit", "原货品单位");
        COMMON_LABELS.put("originUnitPrice", "原货品单价");
        COMMON_LABELS.put("routeType", "换货路线"); COMMON_LABELS.put("linkedReturnId", "关联退货单ID");
        COMMON_LABELS.put("linkedReturnNo", "关联退货单号"); COMMON_LABELS.put("linkedReturnItemId", "关联退货明细ID");
        COMMON_LABELS.put("returnedQuantity", "已退回数量");
        COMMON_LABELS.put("substituteReturnedQuantity", "已退回替换货数量");
        COMMON_LABELS.put("pendingReturnQuantity", "待退回数量"); COMMON_LABELS.put("waivedQuantity", "放弃退回数量");
        COMMON_LABELS.put("waivedBy", "放弃退回操作人"); COMMON_LABELS.put("waivedAt", "放弃退回时间");
        COMMON_LABELS.put("waivedReason", "放弃退回原因");
        COMMON_LABELS.put("turnedDeliveryQuantity", "已下推出库数量");
        COMMON_LABELS.put("priceWithTax", "含税价"); COMMON_LABELS.put("priceWithoutTax", "不含税价");
        COMMON_LABELS.put("generatedDeliveryId", "已生成出库单ID"); COMMON_LABELS.put("generatedDeliveryNo", "已生成出库单号");
        COMMON_LABELS.put("repairId", "维修单ID"); COMMON_LABELS.put("repairNo", "维修单号");
        COMMON_LABELS.put("repairDate", "维修日期"); COMMON_LABELS.put("repairItemId", "维修明细ID");
        COMMON_LABELS.put("partId", "配件行ID");
        COMMON_LABELS.put("serviceType", "服务方式"); COMMON_LABELS.put("warrantyStatus", "保修状态");
        COMMON_LABELS.put("warrantyBasis", "保修依据"); COMMON_LABELS.put("warrantyMonths", "保修月数");
        COMMON_LABELS.put("partsWarehouseId", "备件仓ID"); COMMON_LABELS.put("faultDescription", "故障描述");
        COMMON_LABELS.put("repairPlan", "维修方案"); COMMON_LABELS.put("laborHours", "工时");
        COMMON_LABELS.put("receiveDate", "收件日期"); COMMON_LABELS.put("receivedQuantity", "收件数量");
        COMMON_LABELS.put("repairFee", "维修费"); COMMON_LABELS.put("partsFee", "配件费");
        COMMON_LABELS.put("totalFee", "费用合计"); COMMON_LABELS.put("repairResult", "维修结果");
        COMMON_LABELS.put("transferredBillType", "转换单据类型"); COMMON_LABELS.put("transferredBillNo", "转换单据号");
        COMMON_LABELS.put("transferredBillId", "转换单据ID");
        COMMON_LABELS.put("receivedInboundId", "收件入库单ID"); COMMON_LABELS.put("receivedInboundNo", "收件入库单号");
        COMMON_LABELS.put("returnedIssueId", "返还出库单ID"); COMMON_LABELS.put("returnedIssueNo", "返还出库单号");
        COMMON_LABELS.put("issuedQuantity", "已领用数量"); COMMON_LABELS.put("issueId", "领用出库单ID");
        COMMON_LABELS.put("issueNo", "领用出库单号");
        COMMON_LABELS.put("serialNo", "序列号");
    }

    private final Map<String, DataSourceMeta> dataSources = new LinkedHashMap<>();
    private final Map<String, String> dataSourceNames = new LinkedHashMap<>();

    /** 注册表指纹：数据源数+字段总数，供前端元数据缓存失效判断 */
    private final String buildId;

    /** 自定义字段支持（字段注入：本类由无参构造器 self-register，避免改动构造签名） */
    @Autowired
    private ReportCustomFieldSupport customFieldSupport;

    /**
     * 元数据指纹 = 系统字段指纹 + 自定义字段指纹。
     * 自定义字段是运行时数据（可增删改），必须计入指纹，否则前端 sessionStorage 会一直用旧字段树。
     */
    public String getBuildId() {
        if (customFieldSupport == null) return buildId;
        return buildId + "-" + customFieldSupport.fingerprint();
    }

    public EntityMetadataRegistry() {
        // ==================== 销售模块 ====================
        register("sale", "销售报价单", com.erp.sale.entity.SaleQuote.class, com.erp.sale.entity.SaleQuote.class);
        register("sale", "销售报价明细", com.erp.sale.entity.SaleQuoteItem.class, com.erp.sale.entity.SaleQuote.class);
        register("sale", "销售合同", com.erp.sale.entity.SaleContract.class, com.erp.sale.entity.SaleContract.class);
        register("sale", "销售合同明细", com.erp.sale.entity.SaleContractItem.class, com.erp.sale.entity.SaleContract.class);
        register("sale", "销售订单", com.erp.sale.entity.SaleOrder.class, com.erp.sale.entity.SaleOrder.class);
        register("sale", "销售订单明细", com.erp.sale.entity.SaleOrderItem.class, com.erp.sale.entity.SaleOrder.class);
        register("sale", "销售出库单", com.erp.sale.entity.SaleDelivery.class, com.erp.sale.entity.SaleDelivery.class);
        register("sale", "销售出库明细", com.erp.sale.entity.SaleDeliveryItem.class, com.erp.sale.entity.SaleDelivery.class);
        register("sale", "销售发票", com.erp.sale.entity.SaleInvoice.class, com.erp.sale.entity.SaleInvoice.class);
        register("sale", "销售发票明细", com.erp.sale.entity.SaleInvoiceItem.class, com.erp.sale.entity.SaleInvoice.class);
        register("sale", "销售退货单", com.erp.sale.entity.SaleReturn.class, com.erp.sale.entity.SaleReturn.class);
        register("sale", "销售退货明细", com.erp.sale.entity.SaleReturnItem.class, com.erp.sale.entity.SaleReturn.class);
        // 替换货（2026-11-19 补注册）：销售换货单 + 明细
        register("sale", "销售换货单", com.erp.sale.entity.SaleExchange.class, com.erp.sale.entity.SaleExchange.class);
        register("sale", "销售换货单明细", com.erp.sale.entity.SaleExchangeItem.class, com.erp.sale.entity.SaleExchange.class);
        // 售后维修（2026-11-19 补注册）：维修单 + 客户产品明细 + 配件领用
        register("sale", "售后维修单", com.erp.sale.entity.SaleRepair.class, com.erp.sale.entity.SaleRepair.class);
        register("sale", "售后维修明细", com.erp.sale.entity.SaleRepairItem.class, com.erp.sale.entity.SaleRepair.class);
        register("sale", "售后维修配件", com.erp.sale.entity.SaleRepairPart.class, com.erp.sale.entity.SaleRepair.class);

        // ==================== 采购模块 ====================
        register("purchase", "采购申请单", com.erp.purchase.entity.PurchaseRequest.class, com.erp.purchase.entity.PurchaseRequest.class);
        register("purchase", "采购申请明细", com.erp.purchase.entity.PurchaseRequestItem.class, com.erp.purchase.entity.PurchaseRequest.class);
        register("purchase", "采购合同", com.erp.purchase.entity.PurchaseContract.class, com.erp.purchase.entity.PurchaseContract.class);
        register("purchase", "采购合同明细", com.erp.purchase.entity.PurchaseContractItem.class, com.erp.purchase.entity.PurchaseContract.class);
        register("purchase", "采购订单", com.erp.purchase.entity.PurchaseOrder.class, com.erp.purchase.entity.PurchaseOrder.class);
        register("purchase", "采购订单明细", com.erp.purchase.entity.PurchaseOrderItem.class, com.erp.purchase.entity.PurchaseOrder.class);
        register("purchase", "采购入库单", com.erp.purchase.entity.PurchaseInbound.class, com.erp.purchase.entity.PurchaseInbound.class);
        register("purchase", "采购入库明细", com.erp.purchase.entity.PurchaseInboundItem.class, com.erp.purchase.entity.PurchaseInbound.class);
        register("purchase", "采购发票", com.erp.purchase.entity.PurchaseInvoice.class, com.erp.purchase.entity.PurchaseInvoice.class);
        register("purchase", "采购发票明细", com.erp.purchase.entity.PurchaseInvoiceItem.class, com.erp.purchase.entity.PurchaseInvoice.class);
        register("purchase", "采购退货单", com.erp.purchase.entity.PurchaseReturn.class, com.erp.purchase.entity.PurchaseReturn.class);
        register("purchase", "采购退货明细", com.erp.purchase.entity.PurchaseReturnItem.class, com.erp.purchase.entity.PurchaseReturn.class);

        // ==================== 库存模块 ====================
        register("inventory", "即时库存", com.erp.inventory.entity.Inventory.class, null);
        register("inventory", "库存批次", com.erp.inventory.entity.InventoryBatch.class, null);
        register("inventory", "库存流水", com.erp.inventory.entity.InventoryTransaction.class, null);
        register("inventory", "调拨单", com.erp.inventory.entity.StockTransfer.class, com.erp.inventory.entity.StockTransfer.class);
        register("inventory", "调拨明细", com.erp.inventory.entity.StockTransferItem.class, com.erp.inventory.entity.StockTransfer.class);
        register("inventory", "盘点单", com.erp.inventory.entity.StockCheck.class, com.erp.inventory.entity.StockCheck.class);
        register("inventory", "盘点明细", com.erp.inventory.entity.StockCheckItem.class, com.erp.inventory.entity.StockCheck.class);
        register("inventory", "其他入库单", com.erp.inventory.entity.OtherReceipt.class, com.erp.inventory.entity.OtherReceipt.class);
        register("inventory", "其他入库明细", com.erp.inventory.entity.OtherReceiptItem.class, com.erp.inventory.entity.OtherReceipt.class);
        register("inventory", "其他出库单", com.erp.inventory.entity.OtherIssue.class, com.erp.inventory.entity.OtherIssue.class);
        register("inventory", "其他出库明细", com.erp.inventory.entity.OtherIssueItem.class, com.erp.inventory.entity.OtherIssue.class);
        register("inventory", "领料单", com.erp.inventory.entity.MaterialRequisition.class, com.erp.inventory.entity.MaterialRequisition.class);
        register("inventory", "领料明细", com.erp.inventory.entity.MaterialRequisitionItem.class, com.erp.inventory.entity.MaterialRequisition.class);

        // ==================== 生产模块 ====================
        register("production", "生产工单", com.erp.production.entity.WorkOrder.class, com.erp.production.entity.WorkOrder.class);
        register("production", "生产工单物料明细", com.erp.production.entity.WorkOrderMaterial.class, com.erp.production.entity.WorkOrder.class);
        register("production", "报工单", com.erp.production.entity.OperationReport.class, null);
        register("production", "生产入库单", com.erp.production.entity.ProductionReceipt.class, com.erp.production.entity.ProductionReceipt.class);
        register("production", "生产入库明细", com.erp.production.entity.ProductionReceiptItem.class, com.erp.production.entity.ProductionReceipt.class);

        // ==================== 委外模块 ====================
        register("subcontract", "委外订单", com.erp.subcontract.order.entity.SubcontractOrder.class, com.erp.subcontract.order.entity.SubcontractOrder.class);
        register("subcontract", "委外订单明细", com.erp.subcontract.order.entity.SubcontractOrderItem.class, com.erp.subcontract.order.entity.SubcontractOrder.class);
        register("subcontract", "委外领料单", com.erp.subcontract.pick.entity.SubcontractPick.class, com.erp.subcontract.pick.entity.SubcontractPick.class);
        register("subcontract", "委外领料明细", com.erp.subcontract.pick.entity.SubcontractPickItem.class, com.erp.subcontract.pick.entity.SubcontractPick.class);
        register("subcontract", "委外入库单", com.erp.subcontract.inbound.entity.SubcontractInbound.class, com.erp.subcontract.inbound.entity.SubcontractInbound.class);
        register("subcontract", "委外入库明细", com.erp.subcontract.inbound.entity.SubcontractInboundItem.class, com.erp.subcontract.inbound.entity.SubcontractInbound.class);
        register("subcontract", "委外退货单", com.erp.subcontract.returnn.entity.SubcontractReturn.class, com.erp.subcontract.returnn.entity.SubcontractReturn.class);
        register("subcontract", "委外退货明细", com.erp.subcontract.returnn.entity.SubcontractReturnItem.class, com.erp.subcontract.returnn.entity.SubcontractReturn.class);
        register("subcontract", "委外补料单", com.erp.subcontract.supplement.entity.SubcontractSupplement.class, com.erp.subcontract.supplement.entity.SubcontractSupplement.class);
        register("subcontract", "委外补料明细", com.erp.subcontract.supplement.entity.SubcontractSupplementItem.class, com.erp.subcontract.supplement.entity.SubcontractSupplement.class);

        // ==================== 财务模块 ====================
        register("finance", "收款单", com.erp.finance.entity.PaymentReceipt.class, null);
        register("finance", "付款单", com.erp.finance.entity.PaymentVoucher.class, null);
        register("finance", "销售收款单", com.erp.finance.prepayment.sale.entity.SalePreReceipt.class,
                com.erp.finance.prepayment.sale.entity.SalePreReceipt.class);
        register("finance", "资金转账单", com.erp.finance.entity.FundTransfer.class, com.erp.finance.entity.FundTransfer.class);
        register("finance", "资金转账明细", com.erp.finance.entity.FundTransferItem.class, com.erp.finance.entity.FundTransfer.class);
        register("finance", "运营费用", com.erp.finance.entity.OperationExpense.class, null);
        register("finance", "应收单", com.erp.finance.entity.AccountsReceivable.class, null);
        register("finance", "应付单", com.erp.finance.entity.AccountsPayable.class, null);
        register("finance", "固定资产", com.erp.finance.entity.FixedAsset.class, null);

        // ==================== 对账单（2026-11-22 立项三期补登：AI 自定义报表/聚合可查对账口径） ====================
        register("finance", "客户对账单", com.erp.finance.salebill.entity.SaleStatement.class, com.erp.finance.salebill.entity.SaleStatement.class);
        register("finance", "客户对账单明细", com.erp.finance.salebill.entity.SaleStatementItem.class, com.erp.finance.salebill.entity.SaleStatement.class);
        register("finance", "供应商对账单", com.erp.finance.purchasebill.entity.PurchaseStatement.class, com.erp.finance.purchasebill.entity.PurchaseStatement.class);
        register("finance", "供应商对账单明细", com.erp.finance.purchasebill.entity.PurchaseStatementItem.class, com.erp.finance.purchasebill.entity.PurchaseStatement.class);

        // ==================== CRM 模块 ====================
        register("crm", "销售线索", com.erp.crm.entity.Lead.class, null);
        register("crm", "商机", com.erp.crm.entity.Opportunity.class, null);
        register("crm", "联系人", com.erp.crm.entity.Contact.class, null);
        register("crm", "跟进记录", com.erp.crm.entity.FollowUp.class, null);
        register("crm", "客户投诉", com.erp.crm.entity.Complaint.class, null);

        // ==================== 质量模块（2026-11-05 SPC/自定义看板接入） ====================
        register("qc", "质检记录", com.erp.qc.entity.QcRecord.class, com.erp.qc.entity.QcRecord.class);
        register("qc", "检验明细", com.erp.qc.entity.QcRecordItem.class, com.erp.qc.entity.QcRecord.class);

        // ==================== 打样模块 ====================
        register("sample", "打样订单", com.erp.sample.entity.SampleOrder.class, com.erp.sample.entity.SampleOrder.class);
        register("sample", "打样订单明细", com.erp.sample.entity.SampleOrderItem.class, com.erp.sample.entity.SampleOrder.class);
        register("sample", "免费样品订单", com.erp.freesample.entity.FreeSampleOrder.class, com.erp.freesample.entity.FreeSampleOrder.class);
        register("sample", "免费样品明细", com.erp.freesample.entity.FreeSampleOrderItem.class, com.erp.freesample.entity.FreeSampleOrder.class);

        // ==================== 基础资料模块（字段权限元数据数据源） ====================
        // 注意：ds.key 取自实体 @TableName，必须与前端 BaseEdit 页面的 page-key 属性完全一致
        // 会计科目(Account)：按用户要求不接入字段权限，不在此注册
        register("basedata", "银行账户",         com.erp.basedata.entity.BankAccount.class,      null);
        register("basedata", "币种",             com.erp.basedata.entity.Currency.class,         null);
        register("basedata", "部门档案",         com.erp.basedata.entity.Department.class,       null);
        register("basedata", "员工档案",         com.erp.basedata.entity.Employee.class,         null);
        register("basedata", "货位档案",         com.erp.basedata.entity.Location.class,         null);
        register("basedata", "物料分类",         com.erp.basedata.entity.MaterialCategory.class, null);
        register("basedata", "付款条件",         com.erp.basedata.entity.PaymentTerm.class,      null);
        register("basedata", "价格等级",         com.erp.basedata.entity.PriceLevel.class,       null);
        register("basedata", "结算方式",         com.erp.basedata.entity.SettlementMode.class,   null);
        register("basedata", "计量单位",         com.erp.basedata.entity.UnitOfMeasure.class,    null);
        register("basedata", "仓库档案",         com.erp.basedata.entity.Warehouse.class,        null);
        register("basedata", "物料档案",         com.erp.masterdata.entity.Material.class,       null);
        register("basedata", "客户档案",         com.erp.masterdata.entity.Customer.class,       null);
        register("basedata", "供应商档案",       com.erp.masterdata.entity.Supplier.class,       null);

        // 来源单据（上游）关联：按来源单号 + 来源类型登记，供多级穿插查询
        registerUpstreamJoins();

        // 注册表指纹（数据源数+字段总数）：实体变更后自动变化，前端据此失效缓存
        int fieldCount = 0;
        for (DataSourceMeta ds : dataSources.values()) {
            fieldCount += ds.fields.size();
            for (JoinMeta jm : ds.joins.values()) fieldCount += jm.fields.size();
            for (JoinMeta jm : ds.detailJoins.values()) fieldCount += jm.fields.size();
        }
        this.buildId = dataSources.size() + "-" + fieldCount;
    }

    // ==================== 对外查询 API ====================

    public DataSourceMeta getDataSource(String key) {
        return dataSources.get(key);
    }

    public boolean isRegistered(String key) {
        return dataSources.containsKey(key);
    }

    /** 设计器数据源列表（模块分组） */
    public List<Map<String, Object>> listDataSources() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DataSourceMeta ds : dataSources.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", ds.key);
            m.put("name", ds.name);
            m.put("module", ds.module);
            result.add(m);
        }
        return result;
    }

    /** 设计器完整元数据：主表字段 + 可用关联（含关联字段） */
    public Map<String, Object> describeDataSource(String key) {
        DataSourceMeta ds = dataSources.get(key);
        if (ds == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", ds.key);
        m.put("name", ds.name);
        m.put("module", ds.module);
        m.put("fields", fieldsToListWithCustom(ds.fields, ds.table, ds.pkColumn));
        List<Map<String, Object>> joins = new ArrayList<>();
        for (JoinMeta jm : ds.joins.values()) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("key", jm.key);
            j.put("name", jm.name);
            j.put("fields", fieldsToListWithCustom(jm.fields, jm.table, jm.rightColumn));
            joins.add(j);
        }
        m.put("joins", joins);
        // 明细表反向关联（聚合挂载专用：SUM(明细数量) 等）
        List<Map<String, Object>> detailJoins = new ArrayList<>();
        for (JoinMeta jm : ds.detailJoins.values()) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("key", jm.key);
            j.put("name", jm.name);
            j.put("fields", fieldsToList(jm.fields));
            detailJoins.add(j);
        }
        m.put("detailJoins", detailJoins);
        return m;
    }

    /** 校验并解析列引用：source=main 或 joinKey；返回所属表别名与列元数据，非法返回 null */
    public ResolvedColumn resolve(String dsKey, String source, String field) {
        DataSourceMeta ds = dataSources.get(dsKey);
        if (ds == null || source == null || field == null) return null;
        if ("main".equals(source)) {
            FieldMeta fm = ds.fields.get(field);
            return fm == null ? null : new ResolvedColumn("t1", fm);
        }
        JoinMeta jm = ds.joins.get(source);
        if (jm == null) return null;
        FieldMeta fm = jm.fields.get(field);
        return fm == null ? null : new ResolvedColumn(jm.alias, fm);
    }

    /** 在主表/表头中查找某列存在的别名（数据权限用），找不到返回 null */
    public String findColumnAlias(String dsKey, String column) {
        DataSourceMeta ds = dataSources.get(dsKey);
        if (ds == null) return null;
        for (FieldMeta fm : ds.fields.values()) {
            if (fm.column.equals(column)) return "t1";
        }
        for (JoinMeta jm : ds.joins.values()) {
            for (FieldMeta fm : jm.fields.values()) {
                if (fm.column.equals(column)) return jm.alias;
            }
        }
        return null;
    }

    public String getDataSourceName(String key) {
        return dataSourceNames.getOrDefault(key, key);
    }

    /**
     * 跨单据关联：返回双方数据源（main+joins 树）的完整字段树，
     * 前端左右各任选一个字段（类型一致即可，值匹配语义，不要求同名）。
     * 保存/运行时 SqlBuilder 会按类型兼容再次校验，字段元数据本身即白名单。
     */
    public Map<String, Object> joinables(String key, String targetKey) {
        DataSourceMeta a = dataSources.get(key);
        DataSourceMeta b = dataSources.get(targetKey);
        // 自关联放行：聚合挂载模式允许目标=主表自身（聚合本表明细，如订单数量=SUM(订单明细.quantity)）；
        // 平铺自关联由 SqlBuilder 构建期拒绝（纯行翻倍无意义）
        if (a == null || b == null) {
            throw new IllegalArgumentException("数据源不存在: " + key + " / " + targetKey);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", fieldTreeOf(a));
        result.put("right", fieldTreeOf(b));
        // 目标明细表树（聚合挂载选明细字段用；一对多不可做关联键，仅做聚合）
        result.put("rightDetailTree", fieldTreeOfDetails(b));
        return result;
    }

    /** 明细表字段树：[{source, name, fields:[...]}]（仅 detailJoins，不含主表/joins） */
    private List<Map<String, Object>> fieldTreeOfDetails(DataSourceMeta ds) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (JoinMeta jm : ds.detailJoins.values()) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("source", jm.key);
            j.put("name", jm.name);
            j.put("fields", fieldsToList(jm.fields));
            tree.add(j);
        }
        return tree;
    }

    /** 数据源字段树：[{source, name, fields:[{field,label,type}]}]，main 在前 */
    private List<Map<String, Object>> fieldTreeOf(DataSourceMeta ds) {
        List<Map<String, Object>> tree = new ArrayList<>();
        Map<String, Object> main = new LinkedHashMap<>();
        main.put("source", "main");
        main.put("name", ds.name + "·主表");
        main.put("fields", fieldsToList(ds.fields));
        tree.add(main);
        for (JoinMeta jm : ds.joins.values()) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("source", jm.key);
            j.put("name", jm.name);
            j.put("fields", fieldsToList(jm.fields));
            tree.add(j);
        }
        return tree;
    }

    /**
     * 克隆目标数据源的 join 树（表头+基础资料）供跨单据关联挂载：
     * 所有别名重映射为 seq 递增的新别名（目标主表 t1 → extMainAlias），
     * key 加 "extN." 前缀。返回克隆的 join 列表（不含目标主表本身，主表 join 由调用方生成）。
     */
    public List<JoinMeta> cloneJoinTree(DataSourceMeta target, String extPrefix, String extMainAlias, int[] seq) {
        List<JoinMeta> clones = new ArrayList<>();
        Map<String, String> oldToNew = new HashMap<>();
        oldToNew.put("t1", extMainAlias); // 目标主表别名重映射
        for (JoinMeta jm : target.joins.values()) {
            String newAlias = "t" + (seq[0]++);
            oldToNew.put(jm.alias, newAlias);
            JoinMeta clone = new JoinMeta(extPrefix + "." + jm.key, jm.name, jm.table, newAlias,
                    oldToNew.get(jm.leftAlias), jm.leftColumn, jm.rightColumn);
            clone.fields.putAll(jm.fields); // FieldMeta 不可变，可共享
            clones.add(clone);
        }
        return clones;
    }

    /** 解析结果：表别名 + 列元数据 + SQL 取值表达式 */
    public static class ResolvedColumn {
        public final String alias;
        public final FieldMeta meta;
        /**
         * SQL 取值表达式：系统字段 = {@code alias.column}；
         * 自定义字段 = 值表 JOIN 别名上的类型化取值表达式（含正则守卫 CAST）。
         * <b>所有拼 SQL 的地方都必须用本字段，不得再手拼 alias + "." + meta.column</b>。
         */
        public final String expr;

        public ResolvedColumn(String alias, FieldMeta meta) {
            this(alias, meta, alias + "." + meta.column);
        }

        public ResolvedColumn(String alias, FieldMeta meta, String expr) {
            this.alias = alias;
            this.meta = meta;
            this.expr = expr;
        }
    }

    // ==================== 注册与推导 ====================

    private void register(String module, String displayName, Class<?> mainClass, Class<?> headerClass) {
        String table = tableNameOf(mainClass);
        if (table == null) {
            log.warn("[自定义报表] 实体缺少 @TableName，跳过注册: {}", mainClass.getName());
            return;
        }
        DataSourceMeta ds = new DataSourceMeta(table, displayName, module, table);
        collectFields(mainClass, ds.fields);
        if (ds.fields.isEmpty()) {
            log.warn("[自定义报表] 实体无可映射字段，跳过注册: {}", mainClass.getName());
            return;
        }
        // 主键列：自定义字段值表（sys_custom_field_value.entity_id）按此列关联
        String[] mainPk = primaryKeyOf(mainClass);
        ds.pkColumn = mainPk == null ? null : mainPk[1];

        int aliasSeq = 2;
        // 1) 明细 → 表头关联（自动推导 FK）
        if (headerClass != null && headerClass != mainClass) {
            String headerTable = tableNameOf(headerClass);
            String[] pk = primaryKeyOf(headerClass);
            if (headerTable != null && pk != null) {
                String fkCol = deriveFkColumn(ds.fields, pk);
                if (fkCol != null) {
                    String alias = "t" + aliasSeq++;
                    JoinMeta jm = new JoinMeta(headerTable, displayNameOf(headerTable) + "表头",
                            headerTable, alias, "t1", fkCol, pk[1]);
                    jm.group = "表头";
                    collectFields(headerClass, jm.fields);
                    ds.joins.put(jm.key, jm);
                    // 反向关联（表头→明细，一对多）：登记到表头的 detailJoins，
                    // 仅供聚合挂载子查询使用，不进 joins 平铺树（避免行翻倍）
                    DataSourceMeta headerDs = dataSources.get(headerTable);
                    if (headerDs != null && !headerDs.detailJoins.containsKey(table)) {
                        JoinMeta rev = new JoinMeta(table, displayName,
                                table, "d" + (headerDs.detailJoins.size() + 1),
                                "t1", pk[1], fkCol);
                        collectFields(mainClass, rev.fields);
                        headerDs.detailJoins.put(rev.key, rev);
                    }
                } else {
                    log.warn("[自定义报表] 无法推导明细→表头外键，{} 不提供表头关联", table);
                }
            }
        }
        // 2) 基础资料关联（主表 + 表头的 FK 列自动注册）
        Map<String, FieldMeta> allFkCandidates = new LinkedHashMap<>(ds.fields);
        for (JoinMeta jm : ds.joins.values()) allFkCandidates.putAll(jm.fields);
        for (Map.Entry<String, FieldMeta> e : allFkCandidates.entrySet()) {
            FieldMeta fm = e.getValue();
            String[] rule = BASE_JOIN_RULES.get(fm.column);
            if (rule == null) continue;
            String joinKey = rule[0];
            if (ds.joins.containsKey(joinKey)) continue; // 同名 join 只注册一次
            String alias = "t" + (aliasSeq++);
            String ownerAlias = ownerAliasOf(ds, fm.column);
            JoinMeta jm = new JoinMeta(joinKey, rule[3], rule[1], alias,
                    ownerAlias, fm.column, rule[4]);
            // join 只暴露名称列一个字段，避免字段爆炸
            String nameField = camelOf(rule[2]);
            jm.fields.put(nameField, new FieldMeta(nameField, rule[2], rule[3], "string"));
            ds.joins.put(jm.key, jm);
        }

        dataSources.put(table, ds);
        dataSourceNames.put(table, displayName);
    }

    /**
     * 为已注册数据源补「来源单据」关联：ON 本表.来源单号 = 上游.单号，并按来源类型限定。
     * 别名统一用 u1/u2…，与表头/基础资料的 tN 不冲突。
     */
    private void registerUpstreamJoins() {
        for (String[] rule : UPSTREAM_RULES) {
            DataSourceMeta ds = dataSources.get(rule[0]);
            if (ds == null) continue;
            DataSourceMeta target = dataSources.get(rule[3]);
            if (target == null || target.fields.isEmpty()) continue;
            boolean hasNo = ds.fields.values().stream().anyMatch(f -> f.column.equals(rule[1]));
            if (!hasNo) continue;
            boolean hasType = rule[2] != null && !rule[2].isBlank()
                    && ds.fields.values().stream().anyMatch(f -> f.column.equals(rule[2]));
            String key = "src_" + rule[3];
            if (ds.joins.containsKey(key)) continue;
            String alias = "u" + (ds.joins.size() + 1);
            JoinMeta jm = new JoinMeta(key, "来源：" + rule[5], target.table, alias, "t1", rule[1], rule[4]);
            jm.group = "来源单据";
            if (hasType) {
                StringBuilder in = new StringBuilder();
                for (String v : rule[6].split(",")) {
                    if (v.isBlank()) continue;
                    if (in.length() > 0) in.append(",");
                    in.append("'").append(v.trim().replace("'", "''")).append("'");
                }
                if (in.length() > 0) {
                    jm.extraOn = " AND \"t1\".\"" + rule[2] + "\" IN (" + in + ")";
                }
            }
            jm.fields.putAll(target.fields); // FieldMeta 不可变，可共享
            ds.joins.put(key, jm);
        }
    }

    /** 找到某 FK 列所属的别名（t1 或表头 join） */
    private String ownerAliasOf(DataSourceMeta ds, String column) {
        for (FieldMeta fm : ds.fields.values()) {
            if (fm.column.equals(column)) return "t1";
        }
        for (JoinMeta jm : ds.joins.values()) {
            for (FieldMeta fm : jm.fields.values()) {
                if (fm.column.equals(column)) return jm.alias;
            }
        }
        return "t1";
    }

    /** 推导明细表中指向表头主键的外键列：先按主键列名匹配，再按主键Java字段snake化匹配 */
    private String deriveFkColumn(Map<String, FieldMeta> itemFields, String[] pk) {
        // pk[0]=Java字段名, pk[1]=列名
        for (FieldMeta fm : itemFields.values()) {
            if (fm.column.equals(pk[1])) return pk[1];
        }
        String alt = camelToSnake(pk[0]);
        for (FieldMeta fm : itemFields.values()) {
            if (fm.column.equals(alt)) return alt;
        }
        return null;
    }

    private void collectFields(Class<?> clazz, Map<String, FieldMeta> target) {
        List<FieldMeta> list = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            TableField tf = f.getAnnotation(TableField.class);
            if (tf != null && !tf.exist()) continue;
            TableId ti = f.getAnnotation(TableId.class);
            String column;
            if (ti != null && !ti.value().isEmpty()) {
                column = ti.value();
            } else if (tf != null && !tf.value().isEmpty()) {
                column = tf.value();
            } else if (ti != null || tf != null) {
                column = camelToSnake(f.getName());
            } else {
                continue; // 无注解字段不暴露（保守策略）
            }
            String label = null;
            ExportField ef = f.getAnnotation(ExportField.class);
            boolean required = false;
            if (ef != null) {
                label = ef.label();
                required = ef.required();
            }
            if (label == null || label.isEmpty()) label = COMMON_LABELS.get(f.getName());
            if (label == null) label = f.getName();
            list.add(new FieldMeta(f.getName(), column, label, typeOf(f.getType(), f.getName()), required));
        }
        for (FieldMeta fm : list) target.put(fm.field, fm);
    }

    /** 数值类统一为 number / amount；整数不单独成类（ERP 领域无意义），归入 number */
    private String typeOf(Class<?> t, String fieldName) {
        if (t == LocalDate.class) return "date";
        if (t == LocalDateTime.class) return "datetime";
        if (t == BigDecimal.class || t == Double.class || t == double.class || t == Float.class || t == float.class) {
            return isAmountField(fieldName) ? "amount" : "number";
        }
        if (t == Integer.class || t == Long.class || t == int.class || t == long.class) return "number";
        if (t == Boolean.class || t == boolean.class || t == Short.class || t == short.class) return "bool";
        return "string";
    }

    /** 金额类字段判定：Amount/Money/Balance/Cost/Fee/Price 结尾（不区分大小写）；数量/比率/行号等归 number */
    private boolean isAmountField(String fieldName) {
        if (fieldName == null) return false;
        String n = fieldName.toLowerCase();
        return n.endsWith("amount") || n.endsWith("money") || n.endsWith("balance")
                || n.endsWith("cost") || n.endsWith("fee") || n.endsWith("price");
    }

    private String tableNameOf(Class<?> clazz) {
        TableName tn = clazz.getAnnotation(TableName.class);
        return tn == null ? null : tn.value();
    }

    /** 返回 [Java主键字段名, 主键列名]，无 @TableId 时取 null */
    private String[] primaryKeyOf(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            TableId ti = f.getAnnotation(TableId.class);
            if (ti != null) {
                String col = ti.value().isEmpty() ? camelToSnake(f.getName()) : ti.value();
                return new String[]{f.getName(), col};
            }
        }
        return null;
    }

    private String displayNameOf(String headerTable) {
        // 委外等表头名兜底：取数据源中文名，否则用表名
        return dataSourceNames.getOrDefault(headerTable, headerTable);
    }

    private String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private String camelOf(String snake) {
        String[] parts = snake.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private List<Map<String, Object>> fieldsToList(Map<String, FieldMeta> fields) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FieldMeta fm : fields.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", fm.field);
            m.put("label", fm.label);
            m.put("type", fm.type);
            list.add(m);
        }
        return list;
    }

    /**
     * 系统字段 + 该表启用的自定义字段（仅描述输出层使用，不写入 DataSourceMeta.fields）。
     *
     * <p>为什么不合并进 fields：{@code DataSourceMeta.fields} 被 FieldMetaService（字段权限页）
     * 直接遍历并自行加载自定义字段，合并会导致字段权限页出现重复行。
     *
     * @param table    该字段树对应的业务表名
     * @param pkColumn 该表主键列；为 null（无法定位行ID）时不暴露自定义字段
     */
    private List<Map<String, Object>> fieldsToListWithCustom(Map<String, FieldMeta> fields,
                                                             String table, String pkColumn) {
        List<Map<String, Object>> list = fieldsToList(fields);
        if (customFieldSupport == null || table == null || pkColumn == null) return list;
        for (ReportCustomFieldSupport.CfField cf : customFieldSupport.fieldsOfTable(table)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", cf.ref);
            m.put("label", cf.label);
            m.put("type", cf.type);
            m.put("custom", true);
            m.put("entityType", cf.entityType);
            if (cf.options != null && !cf.options.isEmpty()) m.put("options", cf.options);
            list.add(m);
        }
        return list;
    }
}
