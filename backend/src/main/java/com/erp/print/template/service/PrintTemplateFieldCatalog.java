package com.erp.print.template.service;

import com.erp.common.bill.link.BillLinkRegistry;
import com.erp.customfield.service.CustomFieldService;
import com.erp.export.annotation.ExportField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 打印模板「可用字段」唯一来源（后端）。
 *
 * <p>背景（2026-10-10，用户决策）：字段词汇原先只写在前端打印模板页面里，导致
 * ① AI 不知道某单据类型有哪些可用字段 → 即使读到模板截图也建不出"能取到数"的模板；
 * ② 前后端各写一份必然漂移。现统一以 {@code classpath:print/template-fields.json} 为唯一来源，
 * 前端页面与 AI 工具都从这里读，不再各存一份。
 *
 * <p>数据来源：由前端页面既有字段表一次性抽取而来（34 个单据类型 / 702 个字段），
 * 结构 = {@code commonItemFields[] + docTypeFieldsMap{docType:{header[],items[],footer[]}}}，
 * 每个字段 = {@code {key, label}}；模板内容里的占位符用 {@code {{key}}}。
 */
@Slf4j
@Service
public class PrintTemplateFieldCatalog {

    private static final String RESOURCE = "print/template-fields.json";

    /**
     * 台账里有、但不进入打印模板下拉的单据类型（原因逐条注明）。
     * 除这批之外，{@link BillLinkRegistry} 新登记的单据类型都会自动加入打印模板（兜底字段），
     * 保证「后续有单据自动加入」，不用再改前端或本类。
     */
    private static final Map<String, String> NON_PRINT_DOC_TYPES = Map.of(
            "PRODUCTION_ORDER", "生产订单与生产工单是同一张单，打印统一用 WORK_ORDER",
            "MATERIAL_RETURN", "生产退料单沿用生产领料单打印模板（发布闸门口径）",
            "MATERIAL_SUPPLEMENT", "生产补料单沿用生产领料单打印模板（发布闸门口径）",
            "SUBCONTRACT_DELIVERY", "委外发料的台账别名，打印统一用 SUBCONTRACT_PICK",
            "SUBCONTRACT_RECEIPT", "委外入库的台账别名，打印统一用 SUBCONTRACT_INBOUND",
            "SAMPLE_ORDER", "打样单的台账别名，打印统一用 SAMPLE",
            "ACCOUNT_RECEIVABLE", "收款单体系不生成应收单，且无打印入口",
            "ACCOUNT_PAYABLE", "付款单体系不生成应付单，且无打印入口"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    private final BillLinkRegistry billLinkRegistry;
    private final CustomFieldService customFieldService;

    public PrintTemplateFieldCatalog(BillLinkRegistry billLinkRegistry, CustomFieldService customFieldService) {
        this.billLinkRegistry = billLinkRegistry;
        this.customFieldService = customFieldService;
    }

    // ==================== 全量字段（2026-11-23 用户口径「表头全部字段、明细全部字段全都要」） ====================

    /**
     * 单据类型 → 单据实体（表头实体 / 明细实体，全限定类名）。
     * 用于启动时反射实体上的中文标注，把字段表里没登记的字段全部补进「可用字段」。
     * 未登记的明细实体 = 该单据无独立明细表或沿用兜底字段。
     */
    private static final Map<String, String[]> DOC_ENTITY_CLASSES = new LinkedHashMap<>();
    static {
        DOC_ENTITY_CLASSES.put("SALE_QUOTE", new String[]{"com.erp.sale.entity.SaleQuote", "com.erp.sale.entity.SaleQuoteItem"});
        DOC_ENTITY_CLASSES.put("SALE_ORDER", new String[]{"com.erp.sale.entity.SaleOrder", "com.erp.sale.entity.SaleOrderItem"});
        DOC_ENTITY_CLASSES.put("SALE_DELIVERY", new String[]{"com.erp.sale.entity.SaleDelivery", "com.erp.sale.entity.SaleDeliveryItem"});
        DOC_ENTITY_CLASSES.put("SALE_RETURN", new String[]{"com.erp.sale.entity.SaleReturn", "com.erp.sale.entity.SaleReturnItem"});
        DOC_ENTITY_CLASSES.put("SALE_EXCHANGE", new String[]{"com.erp.sale.entity.SaleExchange", "com.erp.sale.entity.SaleExchangeItem"});
        DOC_ENTITY_CLASSES.put("SALE_REPAIR", new String[]{"com.erp.sale.entity.SaleRepair", "com.erp.sale.entity.SaleRepairItem"});
        DOC_ENTITY_CLASSES.put("SALE_INVOICE", new String[]{"com.erp.sale.entity.SaleInvoice", "com.erp.sale.entity.SaleInvoiceItem"});
        DOC_ENTITY_CLASSES.put("SALE_CONTRACT", new String[]{"com.erp.sale.entity.SaleContract", "com.erp.sale.entity.SaleContractItem"});
        DOC_ENTITY_CLASSES.put("PURCHASE_REQUEST", new String[]{"com.erp.purchase.entity.PurchaseRequest", "com.erp.purchase.entity.PurchaseRequestItem"});
        DOC_ENTITY_CLASSES.put("PURCHASE_ORDER", new String[]{"com.erp.purchase.entity.PurchaseOrder", "com.erp.purchase.entity.PurchaseOrderItem"});
        DOC_ENTITY_CLASSES.put("PURCHASE_INBOUND", new String[]{"com.erp.purchase.entity.PurchaseInbound", "com.erp.purchase.entity.PurchaseInboundItem"});
        DOC_ENTITY_CLASSES.put("PURCHASE_RETURN", new String[]{"com.erp.purchase.entity.PurchaseReturn", "com.erp.purchase.entity.PurchaseReturnItem"});
        DOC_ENTITY_CLASSES.put("PURCHASE_INVOICE", new String[]{"com.erp.purchase.entity.PurchaseInvoice", "com.erp.purchase.entity.PurchaseInvoiceItem"});
        DOC_ENTITY_CLASSES.put("PURCHASE_CONTRACT", new String[]{"com.erp.purchase.entity.PurchaseContract", "com.erp.purchase.entity.PurchaseContractItem"});
        DOC_ENTITY_CLASSES.put("STOCK_TRANSFER", new String[]{"com.erp.inventory.entity.StockTransfer", "com.erp.inventory.entity.StockTransferItem"});
        DOC_ENTITY_CLASSES.put("STOCK_CHECK", new String[]{"com.erp.inventory.entity.StockCheck", "com.erp.inventory.entity.StockCheckItem"});
        DOC_ENTITY_CLASSES.put("MATERIAL_REQUISITION", new String[]{"com.erp.inventory.entity.MaterialRequisition", "com.erp.inventory.entity.MaterialRequisitionItem"});
        DOC_ENTITY_CLASSES.put("OTHER_RECEIPT", new String[]{"com.erp.inventory.entity.OtherReceipt", "com.erp.inventory.entity.OtherReceiptItem"});
        DOC_ENTITY_CLASSES.put("OTHER_ISSUE", new String[]{"com.erp.inventory.entity.OtherIssue", "com.erp.inventory.entity.OtherIssueItem"});
        DOC_ENTITY_CLASSES.put("BOM", new String[]{"com.erp.masterdata.entity.Bom", "com.erp.masterdata.entity.BomItem"});
        DOC_ENTITY_CLASSES.put("WORK_ORDER", new String[]{"com.erp.production.entity.WorkOrder", "com.erp.production.entity.WorkOrderMaterial"});
        DOC_ENTITY_CLASSES.put("PRODUCTION_RECEIPT", new String[]{"com.erp.production.entity.ProductionReceipt", "com.erp.production.entity.ProductionReceiptItem"});
        DOC_ENTITY_CLASSES.put("OPERATION_REPORT", new String[]{"com.erp.production.entity.OperationReport", null});
        DOC_ENTITY_CLASSES.put("PRODUCE_ASSEMBLE", new String[]{"com.erp.produce.assemble.entity.ProduceAssemble", "com.erp.produce.assemble.entity.ProduceAssembleDetail"});
        DOC_ENTITY_CLASSES.put("PRODUCE_DISASSEMBLE", new String[]{"com.erp.produce.assemble.entity.ProduceDisassemble", "com.erp.produce.assemble.entity.ProduceDisassembleDetail"});
        DOC_ENTITY_CLASSES.put("SUBCONTRACT_ORDER", new String[]{"com.erp.subcontract.order.entity.SubcontractOrder", "com.erp.subcontract.order.entity.SubcontractOrderItem"});
        DOC_ENTITY_CLASSES.put("SUBCONTRACT_PICK", new String[]{"com.erp.subcontract.pick.entity.SubcontractPick", "com.erp.subcontract.pick.entity.SubcontractPickItem"});
        DOC_ENTITY_CLASSES.put("SUBCONTRACT_INBOUND", new String[]{"com.erp.subcontract.inbound.entity.SubcontractInbound", "com.erp.subcontract.inbound.entity.SubcontractInboundItem"});
        DOC_ENTITY_CLASSES.put("SUBCONTRACT_RETURN", new String[]{"com.erp.subcontract.returnn.entity.SubcontractReturn", "com.erp.subcontract.returnn.entity.SubcontractReturnItem"});
        DOC_ENTITY_CLASSES.put("SUBCONTRACT_SUPPLEMENT", new String[]{"com.erp.subcontract.supplement.entity.SubcontractSupplement", "com.erp.subcontract.supplement.entity.SubcontractSupplementItem"});
        DOC_ENTITY_CLASSES.put("SAMPLE", new String[]{"com.erp.sample.entity.SampleOrder", "com.erp.sample.entity.SampleOrderItem"});
        DOC_ENTITY_CLASSES.put("FREE_SAMPLE", new String[]{"com.erp.freesample.entity.FreeSampleOrder", "com.erp.freesample.entity.FreeSampleOrderItem"});
        DOC_ENTITY_CLASSES.put("SALE_PRE_RECEIPT", new String[]{"com.erp.finance.prepayment.sale.entity.SalePreReceipt", null});
        DOC_ENTITY_CLASSES.put("PURCHASE_PRE_PAYMENT", new String[]{"com.erp.finance.prepayment.purchase.entity.PurchasePrePayment", null});
    }

    /**
     * 单据类型 → 自定义字段实体（表头实体 / 明细实体，sys_custom_field.entity_type）。
     * 与自定义字段服务的实体登记表同源；未登记 = 该单据未接入自定义字段。
     */
    private static final Map<String, String[]> DOC_CUSTOM_ENTITY_TYPES = new LinkedHashMap<>();
    static {
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_QUOTE", new String[]{"sale_quote", "sale_quote_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_ORDER", new String[]{"sale_order", "sale_order_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_DELIVERY", new String[]{"sale_delivery", "sale_delivery_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_RETURN", new String[]{"sale_return", "sale_return_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_EXCHANGE", new String[]{"sale_exchange", "sale_exchange_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_REPAIR", new String[]{"sale_repair", "sale_repair_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_INVOICE", new String[]{"sale_invoice", "sale_invoice_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_CONTRACT", new String[]{"sale_contract", "sale_contract_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("PURCHASE_REQUEST", new String[]{"purchase_request", "purchase_request_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("PURCHASE_ORDER", new String[]{"purchase_order", "purchase_order_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("PURCHASE_INBOUND", new String[]{"purchase_inbound", "purchase_inbound_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("PURCHASE_RETURN", new String[]{"purchase_return", "purchase_return_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("PURCHASE_INVOICE", new String[]{"purchase_invoice", "purchase_invoice_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("PURCHASE_CONTRACT", new String[]{"purchase_contract", "purchase_contract_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("STOCK_TRANSFER", new String[]{"stock_transfer", "stock_transfer_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("STOCK_CHECK", new String[]{"stock_check", "stock_check_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("MATERIAL_REQUISITION", new String[]{"material_requisition", "material_requisition_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("OTHER_RECEIPT", new String[]{"other_receipt", "other_receipt_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("OTHER_ISSUE", new String[]{"other_issue", "other_issue_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("BOM", new String[]{"bom", "bom_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("WORK_ORDER", new String[]{"production_order", null});
        DOC_CUSTOM_ENTITY_TYPES.put("PRODUCE_ASSEMBLE", new String[]{"produce_assemble", null});
        DOC_CUSTOM_ENTITY_TYPES.put("PRODUCE_DISASSEMBLE", new String[]{"produce_disassemble", null});
        DOC_CUSTOM_ENTITY_TYPES.put("SAMPLE", new String[]{"sample_order", "sample_order_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("FREE_SAMPLE", new String[]{"free_sample_order", "free_sample_order_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SALE_PRE_RECEIPT", new String[]{"sale_pre_receipt", null});
        DOC_CUSTOM_ENTITY_TYPES.put("PURCHASE_PRE_PAYMENT", new String[]{"purchase_pre_payment", null});
        // 2026-11-24 用户口径「员工、客户、单据表头和明细的自定义字段都要自动带出，全部单据都要支持」
        DOC_CUSTOM_ENTITY_TYPES.put("PRODUCTION_RECEIPT", new String[]{"production_receipt", "production_receipt_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("OPERATION_REPORT", new String[]{"operation_report", null});
        // 【2026-11-25】工序卡打印：带出该工序自身的自定义字段（实体=工单工序实例）
        DOC_CUSTOM_ENTITY_TYPES.put("WORK_OPERATION", new String[]{"work_order_operation", null});
        DOC_CUSTOM_ENTITY_TYPES.put("SUBCONTRACT_ORDER", new String[]{"subcontract_order", "subcontract_order_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SUBCONTRACT_PICK", new String[]{"subcontract_pick", "subcontract_pick_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SUBCONTRACT_INBOUND", new String[]{"subcontract_inbound", "subcontract_inbound_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SUBCONTRACT_RETURN", new String[]{"subcontract_return", "subcontract_return_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("SUBCONTRACT_SUPPLEMENT", new String[]{"subcontract_supplement", "subcontract_supplement_item"});
        DOC_CUSTOM_ENTITY_TYPES.put("PAYMENT_RECEIPT", new String[]{"cash_receipt", null});
        DOC_CUSTOM_ENTITY_TYPES.put("PAYMENT_VOUCHER", new String[]{"cash_payment", null});
    }

    /** 明细实体主键（自定义字段值按明细行存取）：docType → 主键属性名 */
    private final Map<String, String> itemPkNames = new HashMap<>();

    /** 已解析的实体类缓存（解析失败不重复尝试） */
    private final Map<String, Class<?>> resolvedClasses = new HashMap<>();

    private Class<?> resolveClass(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) return null;
        return resolvedClasses.computeIfAbsent(fqcn, cn -> {
            try {
                return Class.forName(cn);
            } catch (Throwable e) {
                log.warn("[打印模板字段] 实体类解析失败，该类型保持既有字段清单: {}", cn);
                return null;
            }
        });
    }

    /**
     * 某单据类型的自定义字段实体：[表头实体, 明细实体]（可能为 null），未接入返回 null。
     * 先查显式登记（含 WORK_ORDER→production_order 这类别名）；未登记的按单据类型自动推导
     * （如 NEW_BILL → new_bill / new_bill_item），推导结果必须已在自定义字段服务登记，
     * 保证「后续新单据接入自定义字段后，打印模板无需再改」。
     */
    public String[] customEntityTypesOf(String docType) {
        if (docType == null || docType.isBlank()) return null;
        String type = docType.trim().toUpperCase();
        String[] explicit = DOC_CUSTOM_ENTITY_TYPES.get(type);
        if (explicit != null) return explicit;
        String header = type.toLowerCase(java.util.Locale.ROOT);
        if (customFieldService.resolveTableName(header) == null) return null;
        String item = header + "_item";
        return new String[]{header, customFieldService.resolveTableName(item) != null ? item : null};
    }

    /** 某单据类型明细实体的主键属性名（自定义字段值按明细行存取用）；无明细返回 null */
    public String itemPkOf(String docType) {
        if (docType == null || docType.isBlank()) return null;
        return itemPkNames.get(docType.trim().toUpperCase());
    }

    /** 工序表标准字段（与工单工序实例打印数据 key 一致） */
    private static final List<Map<String, Object>> OPERATION_BASE_FIELDS = List.of(
            fieldEntry("operationName", "工序名称"),
            fieldEntry("operationCode", "工序编码"),
            fieldEntry("operationType", "工序类型"),
            fieldEntry("lineNo", "工序号"),
            fieldEntry("workCenterName", "工作中心"),
            fieldEntry("assigneeName", "负责人"),
            fieldEntry("planQuantity", "计划数量"),
            fieldEntry("reportedQuantity", "已报工数量"),
            fieldEntry("rejectedQuantity", "报废数量"),
            fieldEntry("operationStatus", "工序状态"),
            fieldEntry("planStartDate", "计划开始"),
            fieldEntry("planEndDate", "计划结束"),
            fieldEntry("actualStartDate", "实际开始"),
            fieldEntry("actualEndDate", "实际结束"),
            fieldEntry("productName", "成品名称"),
            fieldEntry("remark", "备注"));

    /**
     * 【2026-11-25】生产工单「工序表」可用字段：
     * 标准工序字段 + 当前租户在「自定义字段配置 → 生产工序」定义的字段（key/label 与取数一致）。
     * 其他单据类型无工序数据源，返回空列表（打印设计器不显示工序区）。
     */
    public List<Map<String, Object>> operationFields(String docType) {
        if (docType == null || docType.isBlank()) return List.of();
        String type = docType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"WORK_ORDER".equals(type) && !"PRODUCTION_ORDER".equals(type)) return List.of();
        List<Map<String, Object>> list = new ArrayList<>(OPERATION_BASE_FIELDS);
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> f : list) keys.add(String.valueOf(f.get("key")));
        for (com.erp.customfield.entity.CustomField def : customFieldService.listEnabledByEntity("work_order_operation")) {
            if (def.getFieldKey() == null || def.getFieldKey().isBlank()) continue;
            // 【2026-11-26】工艺路线工序字段（镜像 key 前缀 rof_）：打印列按「字段名」对齐，
            // 不同工序的同名字段共用一个列，缺的工序留白（用户口径：列数取字段最多的工序）。
            boolean routingScoped = def.getFieldKey().startsWith("rof_");
            String key = routingScoped ? ("op_" + (def.getFieldLabel() == null ? "" : def.getFieldLabel())) : def.getFieldKey();
            String label = def.getFieldLabel();
            if (keys.contains(key)) continue;
            // 标准字段同名时不重复出列
            boolean dupLabel = list.stream().anyMatch(f -> label != null && label.equals(String.valueOf(f.get("label")))
                    && !routingScoped);
            if (dupLabel) continue;
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("key", key);
            one.put("label", label);
            // 设计器/预览按类型渲染样例值（勾选=✔、数字=数值），不影响真实取数
            one.put("fieldType", def.getFieldType());
            list.add(one);
            keys.add(key);
        }
        return list;
    }

    /** 通用明细字段（供没有单独定义明细的模板复用） */
    private List<Map<String, Object>> commonItemFields = List.of();

    /**
     * 员工信息字段（2026-11-24 用户口径「销售员的联系电话打印模板里没有的选」）：
     * 取单据「销售员」在员工档案里的信息，由打印取数服务按单据 salerId 回填；
     * 单据没有销售员或档案缺档时打印为空，不影响出纸。
     */
    private List<Map<String, Object>> employeeFields = List.of(
            fieldEntry("salerPhone", "销售员电话"),
            fieldEntry("salerEmail", "销售员邮箱"),
            fieldEntry("salerDeptName", "销售员部门"),
            fieldEntry("salerPosition", "销售员职位"));

    private static Map<String, Object> fieldEntry(String key, String label) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("key", key);
        one.put("label", label);
        return one;
    }

    /** 客户档案实体（客户信息组字段按它反射） */
    private static final String CUSTOMER_ENTITY = "com.erp.masterdata.entity.Customer";

    /**
     * 客户信息字段（2026-11-24 用户口径「客户信息里面全部字段都要显示在这里」）：
     * key 统一 cust 前缀，避免与单据表头的同名字段（备注 / 状态 / 税率等）触发取值串号；
     * 打印时由取数服务按单据 customerId 回填，无客户或档案缺档时输出空，不阻断出纸。
     */
    private List<Map<String, Object>> customerFields = List.of();

    /** 客户信息字段 key → 客户档案属性名（打印取数按此回填） */
    private final Map<String, String> customerFieldProps = new LinkedHashMap<>();

    /** 客户信息组里按「客户档案页面顺序」优先输出的字段；其余业务字段按标注顺序补在后面 */
    private static final List<String> CUSTOMER_FORM_PROP_ORDER = List.of(
            "customerCode", "customerName", "shortName", "customerType", "customerLevel",
            "contactPerson", "contactPhone", "email", "address",
            "taxNo", "bankName", "bankAccount", "creditLimit", "taxRate", "paymentCategory",
            "creditDays", "status", "remark");

    /** 客户档案属性 → 打印字段 key：customerName → custName、contactPhone → custContactPhone */
    public String customerFieldKey(String prop) {
        if (prop == null || prop.isBlank()) return "";
        String base = prop.startsWith("customer") && prop.length() > "customer".length()
                ? prop.substring("customer".length()) : prop;
        return prefixedKey("cust", base);
    }

    /** 客户自定义字段 → 打印字段 key：custom_1 → custCfCustom_1（与客户档案固定字段完全隔离） */
    public String customerCustomFieldKey(String fieldKey) {
        return prefixedKey("custCf", fieldKey);
    }

    /** 员工自定义字段 → 打印字段 key：custom_1 → salerCfCustom_1（与员工信息固定四项完全隔离） */
    public String employeeCustomFieldKey(String fieldKey) {
        return prefixedKey("salerCf", fieldKey);
    }

    private String prefixedKey(String prefix, String name) {
        if (name == null || name.isBlank()) return "";
        String n = name.trim();
        return prefix + Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** 客户端只读：客户信息基础字段（全量业务字段 + 名称型字段） */
    public List<Map<String, Object>> customerFields() {
        return customerFields;
    }

    /** 客户端只读：员工信息基础字段 */
    public List<Map<String, Object>> employeeFields() {
        return employeeFields;
    }

    /** 客户信息字段 key → 客户档案属性名（打印取数用） */
    public Map<String, String> customerFieldProps() {
        return customerFieldProps;
    }

    /** 单据类型 → {header, items, footer} */
    private Map<String, Map<String, Object>> docTypeFields = new LinkedHashMap<>();

    /** 单据类型下拉清单（顺序 = 页面显示顺序）：{value,label} */
    private List<Map<String, Object>> docTypeList = new ArrayList<>();

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, Object> root = mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
            commonItemFields = toFieldList(root.get("commonItemFields"));
            docTypeList = toDocTypeList(root.get("docTypes"));
            Object dt = root.get("docTypeFieldsMap");
            if (dt instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    String docType = String.valueOf(e.getKey());
                    if (!(e.getValue() instanceof Map<?, ?> fields)) continue;
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("header", toFieldList(fields.get("header")));
                    entry.put("items", toFieldList(fields.get("items")));
                    entry.put("footer", toFieldList(fields.get("footer")));
                    docTypeFields.put(docType, entry);
                }
            }
            int autoAdded = mergeRegisteredBills();
            int enriched = enrichFromEntities();
            customerFields = buildCustomerFields();
            log.info("[打印模板字段] 已加载 {} 个单据类型（其中台账自动加入 {} 个）、{} 个通用明细字段（唯一来源：{}）；实体反射补全 {} 个字段；客户信息字段 {} 个",
                    docTypeFields.size(), autoAdded, commonItemFields.size(), RESOURCE, enriched, customerFields.size());
        } catch (Exception e) {
            log.error("[打印模板字段] 加载失败 {}：打印模板可用字段将为空（不做静默降级）", RESOURCE, e);
        }
    }

    /**
     * 实体反射补全（2026-11-23 用户口径「表头全部字段、明细全部字段全都要」）：
     * 逐个单据类型反射实体上的中文标注，把字段表里没登记的字段全部补进「可用字段」。
     * 口径：只补带中文标注的业务字段；标注为「ID」结尾的引用列（客户ID/币种ID等）与「附件」原样
     * 地址列不进打印字段清单（打印出来是无意义的数字/URL），与导入模板既有口径一致；
     * 附件信息由取数服务统一计算成「附件数量/附件名称」两个字段补进表头组。
     *
     * @return 本次补全的字段总数
     */
    private int enrichFromEntities() {
        int added = 0;
        for (Map.Entry<String, String[]> e : DOC_ENTITY_CLASSES.entrySet()) {
            String docType = e.getKey();
            Class<?> headerCls = resolveClass(e.getValue()[0]);
            Class<?> itemCls = resolveClass(e.getValue()[1]);
            if (itemCls != null) {
                Field pk = findTableIdField(itemCls);
                if (pk != null) itemPkNames.put(docType, pk.getName());
            }
            Map<String, Object> entry = docTypeFields.get(docType);
            if (entry == null) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> header = (List<Map<String, Object>>) entry.get("header");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) entry.get("items");
            if (headerCls != null) added += mergeAnnotatedFields(headerCls, header);
            if (itemCls != null) added += mergeItemFields(itemCls, items);
            // 物料图片：明细含物料字段的单据通用计算字段（取物料档案主图，打印时按图片输出）；放最前便于查找
            if (isMaterialItemList(items) && !keysOf(items).contains("materialImage")) {
                items.add(0, field("materialImage", "物料图片"));
                added++;
            }
            // 附件信息（全单据通用，取数服务统一计算 attachmentCount / attachmentNames）
            Set<String> headerKeys = keysOf(header);
            if (!headerKeys.contains("attachmentCount")) {
                Map<String, Object> cnt = new LinkedHashMap<>();
                cnt.put("key", "attachmentCount");
                cnt.put("label", "附件数量");
                header.add(0, cnt);
                added++;
            }
            if (!headerKeys.contains("attachmentNames")) {
                Map<String, Object> names = new LinkedHashMap<>();
                names.put("key", "attachmentNames");
                names.put("label", "附件名称");
                header.add(1, names);
                added++;
            }
        }
        // 台账自动加入、没有在 DOC_ENTITY_CLASSES 登记的单据类型：同样补附件两个字段，保证「附件」处处可见
        for (Map.Entry<String, Map<String, Object>> e : docTypeFields.entrySet()) {
            if (DOC_ENTITY_CLASSES.containsKey(e.getKey())) continue;
            Object hObj = e.getValue().get("header");
            if (!(hObj instanceof List<?>)) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> header = (List<Map<String, Object>>) hObj;
            Set<String> keys = keysOf(header);
            if (!keys.contains("attachmentNames")) {
                header.add(0, field("attachmentCount", "附件数量"));
                header.add(1, field("attachmentNames", "附件名称"));
                added += 2;
            }
            Object iObj = e.getValue().get("items");
            if (iObj instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) iObj;
                if (isMaterialItemList(items) && !keysOf(items).contains("materialImage")) {
                    items.add(0, field("materialImage", "物料图片"));
                    added++;
                }
            }
        }
        return added;
    }

    /** 明细字段清单里出现物料编码/名称/ID 之一 = 物料明细，才补「物料图片」 */
    private boolean isMaterialItemList(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return false;
        for (Map<String, Object> f : items) {
            String k = String.valueOf(f.get("key"));
            if ("materialId".equals(k) || "materialCode".equals(k) || "materialName".equals(k) || "productCode".equals(k)) return true;
        }
        return false;
    }

    private Map<String, Object> field(String key, String label) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("key", key);
        one.put("label", label);
        return one;
    }

    /**
     * 明细实体补全（2026-11-23 用户口径「明细全部字段」）：
     * 明细实体大多没有中文标注，中文名取「导入模板共享字典」+ 打印专用补充表；
     * ID / 审计 / 来源内部标识等系统列不进清单。按实体声明顺序追加，未登记中文名的跳过。
     */
    private int mergeItemFields(Class<?> cls, List<Map<String, Object>> target) {
        Set<String> existing = keysOf(target);
        Map<String, String> dict = com.erp.export.service.ExcelServiceImpl.itemColumnLabels();
        int added = 0;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (Collection.class.isAssignableFrom(f.getType()) || Map.class.isAssignableFrom(f.getType())) continue;
                String key = f.getName();
                if (existing.contains(key)) continue;
                String column = camelToSnake(key);
                if (column.endsWith("_id") || ITEM_PRINT_EXCLUDE.contains(column)) continue;
                String label = null;
                ExportField a = f.getAnnotation(ExportField.class);
                if (a != null && !a.hidden() && a.label() != null && !a.label().isBlank()) {
                    label = a.label();
                }
                if (label == null) label = dict.get(column);
                if (label == null) label = PRINT_ITEM_EXTRA_LABELS.get(column);
                if (label == null || label.isBlank() || label.endsWith("ID")) continue;
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("key", key);
                one.put("label", label);
                target.add(one);
                existing.add(key);
                added++;
            }
        }
        return added;
    }

    /** 打印明细专用补充中文名（导入模板共享字典里没有、但打印常用的列） */
    private static final Map<String, String> PRINT_ITEM_EXTRA_LABELS = new LinkedHashMap<>();
    static {
        PRINT_ITEM_EXTRA_LABELS.put("material_code", "物料编码");
        PRINT_ITEM_EXTRA_LABELS.put("material_name", "物料名称");
        PRINT_ITEM_EXTRA_LABELS.put("line_no", "行号");
        PRINT_ITEM_EXTRA_LABELS.put("unit", "单位");
        PRINT_ITEM_EXTRA_LABELS.put("warehouse_name", "仓库");
        PRINT_ITEM_EXTRA_LABELS.put("bin_name", "货位");
        PRINT_ITEM_EXTRA_LABELS.put("source_bill_no", "来源单号");
        PRINT_ITEM_EXTRA_LABELS.put("source_line_no", "来源行号");
        PRINT_ITEM_EXTRA_LABELS.put("delivery_line_no", "出库行号");
        PRINT_ITEM_EXTRA_LABELS.put("bom_code", "BOM编码");
        PRINT_ITEM_EXTRA_LABELS.put("bom_version", "BOM版本");
        PRINT_ITEM_EXTRA_LABELS.put("pending_return_quantity", "待退数量");
        PRINT_ITEM_EXTRA_LABELS.put("replace_delivery_info", "替换出库信息");
        PRINT_ITEM_EXTRA_LABELS.put("detail_close_status", "明细关闭状态");
        PRINT_ITEM_EXTRA_LABELS.put("delivered_quantity", "已出库数量");
        PRINT_ITEM_EXTRA_LABELS.put("invoiced_quantity", "已开票数量");
        PRINT_ITEM_EXTRA_LABELS.put("returned_quantity", "已退数量");
        PRINT_ITEM_EXTRA_LABELS.put("received_amount", "已收金额");
    }

    /** 打印明细排除的系统列（主键/外键/审计/来源内部标识；外键另按 _id 结尾统一排除） */
    private static final Set<String> ITEM_PRINT_EXCLUDE = Set.of(
            "id", "created_at", "updated_at", "created_by", "updated_by", "ext_data",
            "source_type", "source_bill_type", "source_row_no", "source_serial_no",
            "sort_order", "warranty_status"
    );

    /** 驼峰属性名 → 数据库列名（camelCase → snake_case） */
    private String camelToSnake(String name) {
        if (name == null || name.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 反射实体字段，把带中文标注、且清单里还没有的字段按标注顺序追加进列表 */
    private int mergeAnnotatedFields(Class<?> cls, List<Map<String, Object>> target) {
        Set<String> existing = keysOf(target);
        List<Field> annotated = annotatedFields(cls);
        int added = 0;
        for (Field f : annotated) {
            ExportField a = f.getAnnotation(ExportField.class);
            String label = a.label();
            String key = f.getName();
            if (label.endsWith("ID") || "附件".equals(label)) continue;
            if (existing.contains(key)) continue;
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("key", key);
            one.put("label", label);
            target.add(one);
            existing.add(key);
            added++;
        }
        return added;
    }

    /** 实体上带中文导出标注、非隐藏、非静态/集合的业务字段，按标注顺序排序 */
    private List<Field> annotatedFields(Class<?> cls) {
        List<Field> annotated = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (Collection.class.isAssignableFrom(f.getType()) || Map.class.isAssignableFrom(f.getType())) continue;
                ExportField a = f.getAnnotation(ExportField.class);
                if (a == null || a.hidden()) continue;
                annotated.add(f);
            }
        }
        annotated.sort(Comparator.comparingInt(f -> {
            ExportField a = f.getAnnotation(ExportField.class);
            return a != null ? a.order() : 0;
        }));
        return annotated;
    }

    /**
     * 客户信息组字段：客户档案全量业务字段（反射中文标注）+ 名称型的引用字段。
     * 顺序按客户档案页面（基本信息 → 联系方式 → 财务信息 → 关联与状态），CRM 统计字段按标注顺序补在后面。
     */
    private List<Map<String, Object>> buildCustomerFields() {
        List<Map<String, Object>> list = new ArrayList<>();
        customerFieldProps.clear();
        Class<?> cls = resolveClass(CUSTOMER_ENTITY);
        if (cls == null) {
            log.warn("[打印模板字段] 客户档案实体解析失败，客户信息组为空");
            return list;
        }
        List<Field> annotated = annotatedFields(cls);
        Map<String, Field> byProp = new LinkedHashMap<>();
        for (Field f : annotated) byProp.put(f.getName(), f);
        Set<String> usedKeys = new LinkedHashSet<>();
        Set<String> usedProps = new LinkedHashSet<>();
        for (String prop : CUSTOMER_FORM_PROP_ORDER) {
            Field f = byProp.get(prop);
            if (f == null) continue;
            addCustomerField(list, f.getName(), f.getAnnotation(ExportField.class).label(), usedKeys, usedProps);
            // 名称型引用字段：客户档案页上的下拉（ID 列打印出来是数字，统一换成名称）
            if ("paymentCategory".equals(prop)) {
                addCustomerExtra(list, "custPaymentTermName", "付款条件", usedKeys);
            } else if ("creditDays".equals(prop)) {
                addCustomerExtra(list, "custCurrencyName", "币种", usedKeys);
                addCustomerExtra(list, "custSalerName", "默认业务员", usedKeys);
                addCustomerExtra(list, "custDeliveryWarehouseName", "默认发货仓", usedKeys);
            } else if ("status".equals(prop)) {
                addCustomerExtra(list, "custCreditApprovalStatus", "授信状态", usedKeys);
            }
        }
        // 其余业务字段（客户管理 / CRM 统计口径）按标注顺序补齐
        for (Field f : annotated) {
            if (usedProps.contains(f.getName())) continue;
            addCustomerField(list, f.getName(), f.getAnnotation(ExportField.class).label(), usedKeys, usedProps);
        }
        return list;
    }

    private void addCustomerField(List<Map<String, Object>> list, String prop, String label,
                                  Set<String> usedKeys, Set<String> usedProps) {
        if (label == null || label.endsWith("ID") || "附件".equals(label)) return;
        String key = customerFieldKey(prop);
        if (key.isEmpty() || usedKeys.contains(key)) return;
        list.add(fieldEntry(key, label));
        customerFieldProps.put(key, prop);
        usedKeys.add(key);
        usedProps.add(prop);
    }

    private void addCustomerExtra(List<Map<String, Object>> list, String key, String label, Set<String> usedKeys) {
        if (usedKeys.contains(key)) return;
        list.add(fieldEntry(key, label));
        usedKeys.add(key);
    }

    private Field findTableIdField(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getAnnotation(com.baomidou.mybatisplus.annotation.TableId.class) != null) return f;
            }
        }
        return null;
    }

    private Set<String> keysOf(List<Map<String, Object>> list) {
        Set<String> keys = new LinkedHashSet<>();
        if (list != null) {
            for (Map<String, Object> f : list) keys.add(String.valueOf(f.get("key")));
        }
        return keys;
    }

    /**
     * 把单据台账（BillLinkRegistry）里已登记、但字段表还没写的单据类型自动补进打印模板。
     *
     * <p>目的（用户 2026-11-22 口径「后续有单据自动加入」）：以后新增单据只要按惯例登记台账，
     * 无需再改前端下拉或本配置文件，打印模板里就能选到它；字段先给兜底（单号/日期/状态/金额 +
     * 通用明细字段），需要更细的字段时再在 template-fields.json 里补专用清单即可覆盖。</p>
     *
     * @return 本次自动加入的类型数量
     */
    private int mergeRegisteredBills() {
        int autoAdded = 0;
        for (String type : billLinkRegistry.allDocTypes()) {
            if (docTypeFields.containsKey(type) || NON_PRINT_DOC_TYPES.containsKey(type)) continue;
            BillLinkRegistry.DocDef def = billLinkRegistry.doc(type);
            if (def == null) continue;
            docTypeFields.put(type, autoFields(def));
            appendDocTypeOption(type, def.name == null || def.name.isBlank() ? type : def.name);
            autoAdded++;
        }
        // 字段表里有、下拉清单漏登记的类型（历史配置遗漏）也补进下拉，避免"能配字段却选不到"
        for (String type : docTypeFields.keySet()) {
            if (!containsDocType(type)) {
                BillLinkRegistry.DocDef def = billLinkRegistry.doc(type);
                appendDocTypeOption(type, def != null && def.name != null && !def.name.isBlank() ? def.name : type);
            }
        }
        return autoAdded;
    }

    /** 台账自动兜底字段：单号 / 单据日期 / 状态 / 金额 + 通用明细字段 */
    private Map<String, Object> autoFields(BillLinkRegistry.DocDef def) {
        List<Map<String, Object>> header = new ArrayList<>();
        addHeaderField(header, def.noCol, "单据编号");
        addHeaderField(header, def.dateCol, "单据日期");
        addHeaderField(header, def.statusCol, "状态");
        addHeaderField(header, def.amountCol, "金额");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("header", header);
        entry.put("items", commonItemFields);
        entry.put("footer", List.of());
        return entry;
    }

    private void addHeaderField(List<Map<String, Object>> list, String column, String label) {
        String key = toCamelKey(column);
        if (key.isEmpty()) return;
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("key", key);
        one.put("label", label);
        list.add(one);
    }

    /** 列名 → 单据数据字段名（camelCase）；SQL 表达式/空列返回空串（不猜字段名） */
    private String toCamelKey(String column) {
        if (column == null) return "";
        String s = column.trim();
        if (s.isEmpty() || s.contains(" ") || s.contains("(") || s.contains(")") || s.contains("'") || s.contains(".")) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    private void appendDocTypeOption(String value, String label) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("value", value);
        one.put("label", label);
        docTypeList.add(one);
    }

    private boolean containsDocType(String type) {
        for (Map<String, Object> o : docTypeList) {
            if (type.equals(o.get("value"))) return true;
        }
        return false;
    }

    /** 所有已登记的单据类型 */
    public Set<String> docTypes() {
        return new LinkedHashSet<>(docTypeFields.keySet());
    }

    /** 单据类型下拉清单（{value,label}，顺序 = 页面显示顺序） */
    public List<Map<String, Object>> docTypeOptions() {
        return docTypeList;
    }

    /** 某单据类型的可用字段；未登记返回 null（调用方自行决定提示文案） */
    public Map<String, Object> forDocType(String docType) {
        if (docType == null || docType.isBlank()) return null;
        return docTypeFields.get(docType.trim().toUpperCase());
    }

    /** 通用明细字段 */
    public List<Map<String, Object>> commonItemFields() {
        return commonItemFields;
    }

    /** 全量（给前端页面一次性读取；结构与原前端 local 常量完全一致，前端无需改结构） */
    public Map<String, Object> all() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("commonItemFields", commonItemFields);
        all.put("employeeFields", employeeFields);
        all.put("customerFields", customerFields);
        all.put("docTypeFieldsMap", docTypeFields);
        all.put("docTypes", docTypeList);
        return all;
    }

    private List<Map<String, Object>> toDocTypeList(Object o) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (!(item instanceof Map<?, ?> mm)) continue;
                Object v = mm.get("value");
                Object label = mm.get("label");
                if (v == null || String.valueOf(v).isBlank()) continue;
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("value", String.valueOf(v));
                one.put("label", label == null ? String.valueOf(v) : String.valueOf(label));
                list.add(one);
            }
        }
        return list;
    }

    private List<Map<String, Object>> toFieldList(Object o) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> mm) {
                    Map<String, Object> one = new LinkedHashMap<>();
                    Object k = mm.get("key");
                    Object label = mm.get("label");
                    one.put("key", k == null ? "" : String.valueOf(k));
                    one.put("label", label == null ? "" : String.valueOf(label));
                    list.add(one);
                }
            }
        }
        return list;
    }
}
