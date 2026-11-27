package com.erp.print.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.erp.masterdata.service.BomService;
import com.erp.purchase.service.*;
import com.erp.sale.service.*;
import com.erp.inventory.service.*;
import com.erp.production.service.WorkOrderService;
import com.erp.produce.assemble.service.ProduceAssembleService;
import com.erp.produce.assemble.service.ProduceDisassembleService;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.finance.service.OperationExpenseService;
import com.erp.finance.cost.calculation.service.CostCalculationService;
import com.erp.finance.wage.service.WageSettlementService;
import com.erp.finance.expense.purchase.service.PurchaseExpenseService;
import com.erp.finance.expense.sale.service.SaleExpenseService;
import com.erp.finance.prepayment.sale.service.SalePreReceiptService;
import com.erp.finance.prepayment.purchase.service.PurchasePrePaymentService;
import com.erp.production.service.ProductionReceiptService;
import com.erp.production.service.OperationReportService;
import com.erp.produce.mrp.service.MrpService;
import com.erp.sample.service.SampleOrderService;
import com.erp.freesample.service.FreeSampleOrderService;
import com.erp.price.sale.service.SalePriceStrategyService;
import com.erp.price.purchase.service.PurchasePriceStrategyService;
import com.erp.subcontract.order.service.SubcontractOrderService;
import com.erp.subcontract.pick.service.SubcontractPickService;
import com.erp.subcontract.inbound.service.SubcontractInboundService;
import com.erp.subcontract.returnn.service.SubcontractReturnService;
import com.erp.subcontract.supplement.service.SubcontractSupplementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 打印数据获取服务 - 按单据类型(billType)自动调用对应业务Service获取单据数据
 * 新增单据类型时只需在 fetchByType 中增加一行映射即可
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintDataFetcherService {

    /** 字段权限：打印数据统一剔除不可见字段（与列表/导出同一份规则） */
    private final com.erp.permission.service.FieldPermissionOutputFilter fieldPermissionOutputFilter;
    private final com.erp.permission.config.BillPermissionPathRegistry pageKeyResolver;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 销售模块
    private final SaleQuoteService saleQuoteService;
    private final SaleOrderService saleOrderService;
    private final SaleDeliveryService saleDeliveryService;
    private final SaleReturnService saleReturnService;
    private final SaleInvoiceService saleInvoiceService;
    // 销售换货单 / 售后维修单（2026-11-19 打印取数）：这两个 Service 与销售其它 Service 存在相互引用，
    // 用 @Lazy 注入，避免启动期循环依赖
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private SaleExchangeService saleExchangeService;
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private SaleRepairService saleRepairService;

    // 采购模块
    private final PurchaseRequestService purchaseRequestService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseInboundService purchaseInboundService;
    private final PurchaseReturnService purchaseReturnService;
    private final PurchaseInvoiceService purchaseInvoiceService;

    // 库存模块
    private final StockTransferService stockTransferService;
    private final StockCheckService stockCheckService;
    private final MaterialRequisitionService materialRequisitionService;
    private final OtherReceiptService otherReceiptService;
    private final OtherIssueService otherIssueService;

    // 生产模块
    private final BomService bomService;

    // 合同/生产/组装拆卸模块（打印支持）
    private final SaleContractService saleContractService;
    private final PurchaseContractService purchaseContractService;
    private final WorkOrderService workOrderService;
    private final ProduceAssembleService produceAssembleService;
    private final ProduceDisassembleService produceDisassembleService;

    // 委外模块
    private final SubcontractOrderService subcontractOrderService;
    private final SubcontractPickService subcontractPickService;
    private final SubcontractInboundService subcontractInboundService;
    private final SubcontractReturnService subcontractReturnService;
    private final SubcontractSupplementService subcontractSupplementService;

    // 生产模块（工单已有，补充生产入库/报工/MRP）
    private final ProductionReceiptService productionReceiptService;
    private final OperationReportService operationReportService;
    private final MrpService mrpService;

    // 财务模块
    private final PaymentReceiptMapper paymentReceiptMapper;
    private final PaymentVoucherMapper paymentVoucherMapper;
    private final SalePreReceiptService salePreReceiptService;
    private final PurchasePrePaymentService purchasePrePaymentService;
    private final OperationExpenseService operationExpenseService;
    private final CostCalculationService costCalculationService;
    private final WageSettlementService wageSettlementService;
    private final PurchaseExpenseService purchaseExpenseService;
    private final SaleExpenseService saleExpenseService;

    // 样品/价格策略模块
    private final SampleOrderService sampleOrderService;
    private final FreeSampleOrderService freeSampleOrderService;
    private final SalePriceStrategyService salePriceStrategyService;
    private final PurchasePriceStrategyService purchasePriceStrategyService;

    // 二维码打印：商品标签 / 工序卡 的取数（2026-09-27）
    private final com.erp.masterdata.mapper.MaterialMapper materialMapper;
    private final com.erp.basedata.mapper.UnitOfMeasureMapper unitOfMeasureMapper;
    private final com.erp.basedata.mapper.MaterialCategoryMapper materialCategoryMapper;
    private final com.erp.production.mapper.WorkOrderOperationMapper workOrderOperationMapper;
    /** 工作中心档案：生产工单打印「工序表」时按工序的工作中心ID回填名称 */
    private final com.erp.production.mapper.WorkCenterMapper workCenterMapper;

    // 打印数据补齐：附件信息 + 自定义字段取值（2026-11-23）
    private final PrintTemplateFieldCatalog fieldCatalog;
    private final com.erp.customfield.service.CustomFieldService customFieldService;

    // 员工信息补齐：按单据销售员取员工档案（2026-11-24）
    private final com.erp.basedata.mapper.EmployeeMapper employeeMapper;
    private final com.erp.basedata.mapper.DepartmentMapper departmentMapper;

    // 客户信息补齐：按单据客户取客户档案全字段（2026-11-24）
    private final com.erp.masterdata.mapper.CustomerMapper customerMapper;
    private final com.erp.basedata.mapper.PaymentTermMapper paymentTermMapper;
    private final com.erp.basedata.mapper.CurrencyMapper currencyMapper;
    private final com.erp.basedata.mapper.WarehouseMapper warehouseMapper;

    /**
     * 按单据类型获取单据数据, 转换为Map供模板渲染使用
     *
     * @param billType 单据类型 (如 SALE_ORDER, PURCHASE_ORDER)
     * @param billId   单据ID
     * @return 单据数据的Map表示 (包含主表字段 + items明细列表)
     */
    public Map<String, Object> fetchBillData(String billType, Long billId) {
        Object data = fetchByType(billType, billId);
        if (data == null) {
            return new HashMap<>();
        }
        // 使用Jackson将实体对象转为Map, 支持嵌套对象和List
        Map<String, Object> map = objectMapper.convertValue(data, Map.class);
        // 【2026-11-25】生产工单的明细在实体里叫 materials；打印字段/明细循环统一用 items，
        // 这里补一份别名，保证「明细表」「工序表混用物料列」都能取到物料行。
        if (("WORK_ORDER".equalsIgnoreCase(billType) || "PRODUCTION_ORDER".equalsIgnoreCase(billType))
                && !map.containsKey("items") && map.get("materials") instanceof List<?> mats) {
            map.put("items", mats);
        }
        enrichPrintData(billType, billId, map);
        // 字段权限：打印与列表/导出使用同一份不可见字段规则（按单据类型自动对应主表）
        applyFieldPermission(billType, map);
        return map;
    }

    /** 剔除当前用户不可见的字段（表头 + 明细行）；管理员/无配置不处理 */
    private void applyFieldPermission(String billType, Map<String, Object> map) {
        try {
            String pageKey = pageKeyResolver.fromBillType(billType);
            if (pageKey == null) return;
            Set<String> hidden = fieldPermissionOutputFilter.hiddenKeys(pageKey);
            if (hidden == null || hidden.isEmpty()) return;
            map.keySet().removeIf(k -> hidden.contains(k)
                    || hidden.contains(com.erp.permission.service.FieldVisiblePermissionService.snakeToCamel(k)));
            for (String listKey : new String[]{"items", "materials", "operations", "writeOffItems"}) {
                Object v = map.get(listKey);
                if (!(v instanceof List<?> list)) continue;
                for (Object row : list) {
                    if (row instanceof Map<?, ?> rowMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) rowMap;
                        m.keySet().removeIf(k -> hidden.contains(k)
                                || hidden.contains(com.erp.permission.service.FieldVisiblePermissionService.snakeToCamel(k)));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[打印取数] 字段权限过滤失败（按原数据打印）: billType={}, err={}", billType, e.getMessage());
        }
    }

    /**
     * 打印数据补齐（2026-11-23 用户口径「审批附件信息全都要」）：
     * ① 附件：attachmentUrls（逗号分隔URL）→ attachmentCount 附件数量 / attachmentNames 附件名称；
     * ② 自定义字段：按单据类型的自定义实体，把表头与明细行的自定义字段值并入数据
     *    （key = 自定义字段 fieldKey，与可用字段清单一致）。
     * 任何一步失败只记日志并按原数据继续打印，不阻断出纸。
     */
    private void enrichPrintData(String billType, Long billId, Map<String, Object> map) {
        try {
            enrichAttachments(map);
        } catch (Exception e) {
            log.warn("[打印取数] 附件信息计算失败: {}#{}", billType, billId, e);
        }
        try {
            enrichCustomFields(billType, billId, map);
        } catch (Exception e) {
            log.warn("[打印取数] 自定义字段取值失败: {}#{}", billType, billId, e);
        }
        try {
            // 【2026-11-25】生产工单「工序表」打印：回填工作中心/成品名称与工序自定义字段（含下拉/勾选显示格式）
            enrichWorkOrderOperations(billType, billId, map);
        } catch (Exception e) {
            log.warn("[打印取数] 工序清单取值失败: {}#{}", billType, billId, e);
        }
        try {
            enrichItemImages(map);
        } catch (Exception e) {
            log.warn("[打印取数] 物料图片取值失败: {}#{}", billType, billId, e);
        }
        try {
            enrichSalerEmployee(map);
        } catch (Exception e) {
            log.warn("[打印取数] 员工信息取值失败: {}#{}", billType, billId, e);
        }
        try {
            enrichCustomerInfo(map);
        } catch (Exception e) {
            log.warn("[打印取数] 客户信息取值失败: {}#{}", billType, billId, e);
        }
        try {
            enrichSalerEmployeeCustomFields(map);
        } catch (Exception e) {
            log.warn("[打印取数] 员工自定义字段取值失败: {}#{}", billType, billId, e);
        }
        try {
            enrichCustomerCustomFields(map);
        } catch (Exception e) {
            log.warn("[打印取数] 客户自定义字段取值失败: {}#{}", billType, billId, e);
        }
    }

    /**
     * 员工信息（2026-11-24 用户口径「销售员的联系电话打印模板里没有的选」）：
     * 按单据 salerId 查员工档案，回填 销售员电话/邮箱/部门/职位（与可用字段「员工信息」组一致）；
     * 无销售员或档案缺档时回填空串，不阻断出纸。
     */
    private void enrichSalerEmployee(Map<String, Object> map) {
        Object salerId = map.get("salerId");
        if (!(salerId instanceof Number n) || n.longValue() <= 0) return;
        com.erp.basedata.entity.Employee emp = employeeMapper.selectById(n.longValue());
        if (emp == null) return;
        map.put("salerPhone", emp.getMobilePhone() == null ? "" : emp.getMobilePhone());
        map.put("salerEmail", emp.getEmail() == null ? "" : emp.getEmail());
        map.put("salerPosition", emp.getPosition() == null ? "" : emp.getPosition());
        String deptName = "";
        if (emp.getDeptId() != null) {
            com.erp.basedata.entity.Department dept = departmentMapper.selectById(emp.getDeptId());
            if (dept != null && dept.getDeptName() != null) deptName = dept.getDeptName();
        }
        map.put("salerDeptName", deptName);
    }

    /**
     * 客户信息（2026-11-24 用户口径「客户信息里面全部字段都要显示」）：
     * 按单据 customerId 查客户档案，回填全部业务字段（cust 前缀，与可用字段「客户信息」组一致），
     * 引用型字段统一回填名称（付款条件/币种/默认业务员/默认发货仓），授信状态空值显示「未申请」。
     * 无客户或档案缺档时输出空，不阻断出纸。
     */
    private void enrichCustomerInfo(Map<String, Object> map) {
        Object cidObj = map.get("customerId");
        if (!(cidObj instanceof Number n) || n.longValue() <= 0) return;
        com.erp.masterdata.entity.Customer customer = customerMapper.selectById(n.longValue());
        if (customer == null) return;
        Map<?, ?> raw = objectMapper.convertValue(customer, Map.class);
        for (Map.Entry<String, String> e : fieldCatalog.customerFieldProps().entrySet()) {
            Object v = raw.get(e.getValue());
            map.put(e.getKey(), v == null ? "" : v);
        }
        map.put("custPaymentTermName", resolvePaymentTermName(customer.getPaymentTermId()));
        map.put("custCurrencyName", resolveCurrencyName(customer.getCurrencyId()));
        map.put("custSalerName", resolveEmployeeName(customer.getDefaultSalerId()));
        map.put("custDeliveryWarehouseName", resolveWarehouseName(customer.getDefaultDeliveryWarehouseId()));
        String creditStatus = customer.getCreditApprovalStatus();
        map.put("custCreditApprovalStatus", creditStatus == null || creditStatus.isBlank() ? "未申请" : creditStatus);
    }

    /** 客户自定义字段：按单据客户取客户档案启用的自定义字段值（key = custCf 前缀，与字段清单一致） */
    private void enrichCustomerCustomFields(Map<String, Object> map) {
        Object cidObj = map.get("customerId");
        if (!(cidObj instanceof Number n) || n.longValue() <= 0) return;
        Map<String, Object> values = customFieldService.getFieldValues("customer", n.longValue());
        if (values == null || values.isEmpty()) return;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String key = fieldCatalog.customerCustomFieldKey(e.getKey());
            if (!key.isBlank()) map.put(key, e.getValue() == null ? "" : e.getValue());
        }
    }

    /** 员工自定义字段：按单据销售员取员工档案启用的自定义字段值（key = salerCf 前缀，与字段清单一致） */
    private void enrichSalerEmployeeCustomFields(Map<String, Object> map) {
        Object salerId = map.get("salerId");
        if (!(salerId instanceof Number n) || n.longValue() <= 0) return;
        Map<String, Object> values = customFieldService.getFieldValues("employee", n.longValue());
        if (values == null || values.isEmpty()) return;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String key = fieldCatalog.employeeCustomFieldKey(e.getKey());
            if (!key.isBlank()) map.put(key, e.getValue() == null ? "" : e.getValue());
        }
    }

    private String resolvePaymentTermName(Long id) {
        if (id == null) return "";
        try {
            com.erp.basedata.entity.PaymentTerm t = paymentTermMapper.selectById(id);
            return t == null || t.getPaymentTermName() == null ? "" : t.getPaymentTermName();
        } catch (Exception e) {
            log.warn("[打印取数] 客户付款条件名称回填失败 id={}", id, e);
            return "";
        }
    }

    private String resolveCurrencyName(Long id) {
        if (id == null) return "";
        try {
            com.erp.basedata.entity.Currency c = currencyMapper.selectById(id);
            return c == null || c.getCurrencyName() == null ? "" : c.getCurrencyName();
        } catch (Exception e) {
            log.warn("[打印取数] 客户币种名称回填失败 id={}", id, e);
            return "";
        }
    }

    private String resolveEmployeeName(Long id) {
        if (id == null) return "";
        try {
            com.erp.basedata.entity.Employee emp = employeeMapper.selectById(id);
            return emp == null || emp.getEmployeeName() == null ? "" : emp.getEmployeeName();
        } catch (Exception e) {
            log.warn("[打印取数] 客户默认业务员名称回填失败 id={}", id, e);
            return "";
        }
    }

    private String resolveWarehouseName(Long id) {
        if (id == null) return "";
        try {
            com.erp.basedata.entity.Warehouse w = warehouseMapper.selectById(id);
            return w == null || w.getStockName() == null ? "" : w.getStockName();
        } catch (Exception e) {
            log.warn("[打印取数] 客户默认发货仓名称回填失败 id={}", id, e);
            return "";
        }
    }

    /** 明细物料图片：按明细 materialId 批量取物料档案主图（无主图时取多图第一张），字段名 materialImage */
    private void enrichItemImages(Map<String, Object> map) {
        Object itemsObj = map.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) return;
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        for (Object it : items) {
            if (!(it instanceof Map<?, ?> im)) continue;
            Object mid = ((Map<?, ?>) im).get("materialId");
            if (mid instanceof Number n) ids.add(n.longValue());
        }
        if (ids.isEmpty()) return;
        java.util.List<com.erp.masterdata.entity.Material> mats = materialMapper.selectBatchIds(ids);
        java.util.Map<Long, String> imgById = new java.util.HashMap<>();
        if (mats != null) {
            for (com.erp.masterdata.entity.Material m : mats) {
                String url = m.getMainImage();
                if (url == null || url.isBlank()) {
                    String urls = m.getImageUrls();
                    if (urls != null && !urls.isBlank()) url = urls.split("[,，]")[0].trim();
                }
                if (url != null && !url.isBlank() && m.getMaterialId() != null) imgById.put(m.getMaterialId(), url);
            }
        }
        for (Object it : items) {
            if (!(it instanceof Map<?, ?> im)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) im;
            Object mid = itemMap.get("materialId");
            String url = (mid instanceof Number n) ? imgById.get(n.longValue()) : null;
            itemMap.put("materialImage", url == null ? "" : url);
        }
    }

    /** 附件信息：attachmentUrls（逗号分隔URL字符串）→ 附件数量 + 附件名称（取最后一段文件名，空格分隔） */
    private void enrichAttachments(Map<String, Object> map) {
        Object raw = map.get("attachmentUrls");
        java.util.List<String> names = new java.util.ArrayList<>();
        if (raw != null && !String.valueOf(raw).isBlank()) {
            for (String part : String.valueOf(raw).split("[,，]")) {
                String url = part.trim();
                if (url.isEmpty()) continue;
                int slash = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
                String name = slash >= 0 ? url.substring(slash + 1) : url;
                int q = name.indexOf('?');
                if (q > 0) name = name.substring(0, q);
                if (!name.isBlank()) names.add(java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        map.put("attachmentCount", names.size());
        map.put("attachmentNames", String.join("、", names));
    }

    /** 自定义字段：表头值按单据ID取，明细值按明细主键批量取（key = fieldKey） */
    private void enrichCustomFields(String billType, Long billId, Map<String, Object> map) {
        String[] customTypes = fieldCatalog.customEntityTypesOf(billType);
        if (customTypes == null || billId == null) return;
        String entityType = customTypes[0];
        String itemEntity = customTypes[1];
        if (entityType != null && !entityType.isBlank()) {
            Map<String, Object> headerValues = customFieldService.getFieldValues(entityType, billId);
            if (headerValues != null && !headerValues.isEmpty()) {
                // 【2026-11-25】生产工序：下拉显示选项名称、勾选显示 ✔/空白、数字按精度输出
                if ("work_order_operation".equals(entityType)) {
                    map.putAll(formatCustomValues(entityType, headerValues));
                } else {
                    map.putAll(headerValues);
                }
            }
        }
        if (itemEntity == null || itemEntity.isBlank()) return;
        String itemPk = fieldCatalog.itemPkOf(billType);
        Object itemsObj = map.get("items");
        if (itemPk == null || !(itemsObj instanceof List<?> items) || items.isEmpty()) return;
        java.util.List<Long> itemIds = new java.util.ArrayList<>();
        for (Object it : items) {
            if (!(it instanceof Map<?, ?> im)) continue;
            Object pk = ((Map<?, ?>) im).get(itemPk);
            if (pk instanceof Number n) itemIds.add(n.longValue());
        }
        if (itemIds.isEmpty()) return;
        Map<Long, Map<String, Object>> valuesByItem = customFieldService.batchGetFieldValues(itemEntity, itemIds);
        if (valuesByItem == null || valuesByItem.isEmpty()) return;
        for (Object it : items) {
            if (!(it instanceof Map<?, ?> im)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) im;
            Object pk = itemMap.get(itemPk);
            if (pk instanceof Number n) {
                Map<String, Object> vals = valuesByItem.get(n.longValue());
                if (vals != null) itemMap.putAll(vals);
            }
        }
    }

    /**
     * 【2026-11-25】生产工单「工序表」打印取数：
     * ① 工作中心ID → 名称；② 成品行ID → 成品名称；
     * ③ 工序自定义字段值（批量）并按显示格式转换（下拉=选项名、勾选=✔/空白、数字=按精度）。
     * 任一环节失败只影响该字段为空，不阻断出纸。
     */
    private void enrichWorkOrderOperations(String billType, Long billId, Map<String, Object> map) {
        if (!"WORK_ORDER".equalsIgnoreCase(billType) && !"PRODUCTION_ORDER".equalsIgnoreCase(billType)) return;
        Object opsObj = map.get("operations");
        if (!(opsObj instanceof List<?> ops) || ops.isEmpty()) return;
        // 成品名称：工单详情的 products 已带 id + materialName
        Map<Long, String> productNames = new HashMap<>();
        Object productsObj = map.get("products");
        if (productsObj instanceof List<?> products) {
            for (Object p : products) {
                if (!(p instanceof Map<?, ?> pm)) continue;
                Object id = pm.get("id");
                if (id instanceof Number n) {
                    Object name = pm.get("materialName");
                    productNames.put(n.longValue(), name == null ? "" : String.valueOf(name));
                }
            }
        }
        List<Long> opIds = new ArrayList<>();
        Set<Long> workCenterIds = new LinkedHashSet<>();
        for (Object o : ops) {
            if (!(o instanceof Map<?, ?> om)) continue;
            Object id = om.get("id");
            if (id instanceof Number n) opIds.add(n.longValue());
            Object wc = om.get("workCenterId");
            if (wc instanceof Number wn) workCenterIds.add(wn.longValue());
        }
        Map<Long, String> wcNames = new HashMap<>();
        for (Long wcId : workCenterIds) {
            try {
                com.erp.production.entity.WorkCenter wc = workCenterMapper.selectById(wcId);
                if (wc != null && wc.getWorkCenterName() != null) wcNames.put(wcId, wc.getWorkCenterName());
            } catch (Exception ignore) {
                // 档案缺档 → 工作中心留空，不阻断打印
            }
        }
        Map<Long, Map<String, Object>> valuesByOp = opIds.isEmpty()
                ? Map.of()
                : customFieldService.batchGetFieldValues("work_order_operation", opIds);
        for (Object o : ops) {
            if (!(o instanceof Map<?, ?> rawOp)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> om = (Map<String, Object>) rawOp;
            Object wcIdObj = om.get("workCenterId");
            if (wcIdObj instanceof Number wn) {
                String name = wcNames.get(wn.longValue());
                if (name != null) om.put("workCenterName", name);
            }
            Object prodIdObj = om.get("orderProductId");
            if (prodIdObj instanceof Number pn) {
                String name = productNames.get(pn.longValue());
                if (name != null) om.put("productName", name);
            }
            Object idObj = om.get("id");
            if (idObj instanceof Number n) {
                Map<String, Object> rawValues = valuesByOp.get(n.longValue());
                if (rawValues != null && !rawValues.isEmpty()) {
                    java.util.Map<String, Object> formatted = formatCustomValues("work_order_operation", rawValues);
                    om.putAll(formatted);
                    // 【2026-11-26】工序字段按「字段名」出列：把镜像字段值再挂一份到 op_<字段名>，
                    // 不同工序的同名字段共用一列，缺的工序自然留白
                    for (com.erp.customfield.entity.CustomField def
                            : customFieldService.listEnabledByEntity("work_order_operation")) {
                        if (def.getFieldKey() == null || !def.getFieldKey().startsWith("rof_")) continue;
                        String label = def.getFieldLabel();
                        if (label == null || label.isBlank()) continue;
                        om.put("op_" + label, formatted.get(def.getFieldKey()));
                    }
                }
            }
        }
    }

    /** 自定义字段显示值转换：下拉→选项名称、勾选→✔/空白、数字/公式→按精度；其余原样 */
    private Map<String, Object> formatCustomValues(String entityType, Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) return out;
        List<com.erp.customfield.entity.CustomField> defs = customFieldService.listEnabledByEntity(entityType);
        for (com.erp.customfield.entity.CustomField def : defs) {
            String key = def.getFieldKey();
            if (key == null || key.isBlank()) continue;
            Object v = raw.get(key);
            String type = def.getFieldType() == null ? "" : def.getFieldType();
            switch (type) {
                case "switch" -> out.put(key, isTrueValue(v) ? "✔" : "");
                case "select" -> out.put(key, selectLabelOf(def, v));
                case "number", "formula" -> out.put(key, numberTextOf(v, def.getPrecisionVal()));
                default -> out.put(key, v == null ? "" : String.valueOf(v));
            }
        }
        return out;
    }

    private boolean isTrueValue(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() == 1;
        String s = String.valueOf(v).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "是".equals(s) || "✔".equals(s);
    }

    private String selectLabelOf(com.erp.customfield.entity.CustomField def, Object v) {
        if (v == null) return "";
        String val = String.valueOf(v);
        try {
            String options = def.getFieldOptions();
            if (options != null && !options.isBlank()) {
                var arr = objectMapper.readTree(options);
                for (var node : arr) {
                    if (node.has("value") && val.equals(node.get("value").asText())) {
                        return node.has("label") ? node.get("label").asText() : val;
                    }
                }
            }
        } catch (Exception ignore) {
            // 选项 JSON 异常 → 原样输出存储值
        }
        return val;
    }

    private String numberTextOf(Object v, Integer precision) {
        if (v == null || String.valueOf(v).isBlank()) return "";
        try {
            BigDecimal d = new BigDecimal(String.valueOf(v));
            int p = precision == null ? 2 : Math.max(0, Math.min(6, precision));
            return d.setScale(p, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    private Object fetchByType(String billType, Long billId) {
        if (billType == null) return null;

        switch (billType.toUpperCase()) {
            // ===== 销售模块 =====
            case "SALE_QUOTE":
            case "SALE_QUOTATION":
                return saleQuoteService.getDetail(billId);
            case "SALE_ORDER":
                return saleOrderService.getDetail(billId);
            case "SALE_DELIVERY":
                return saleDeliveryService.getDetail(billId);
            case "SALE_RETURN":
                return saleReturnService.getDetail(billId);
            case "SALE_EXCHANGE":
                return saleExchangeService.getDetail(billId);
            case "SALE_REPAIR":
                return saleRepairService.getDetail(billId);
            case "SALE_INVOICE":
                return saleInvoiceService.getDetail(billId);

            // ===== 采购模块 =====
            case "PURCHASE_REQUEST":
                return purchaseRequestService.getDetail(billId);
            case "PURCHASE_ORDER":
                return purchaseOrderService.getDetail(billId);
            case "PURCHASE_INBOUND":
                return purchaseInboundService.getDetail(billId);
            case "PURCHASE_RETURN":
                return purchaseReturnService.getDetail(billId);
            case "PURCHASE_INVOICE":
                return purchaseInvoiceService.getDetail(billId);

            // ===== 库存模块 =====
            case "STOCK_TRANSFER":
                return stockTransferService.getDetail(billId);
            case "STOCK_CHECK":
                return stockCheckService.getDetail(billId);
            case "MATERIAL_REQUISITION":
                return materialRequisitionService.getDetail(billId);
            case "OTHER_RECEIPT":
                return otherReceiptService.getDetail(billId);
            case "OTHER_ISSUE":
                return otherIssueService.getDetail(billId);

            // ===== 生产模块 =====
            case "BOM":
                return bomService.getDetail(billId);
            case "WORK_ORDER":
            case "PRODUCTION_ORDER":
                return workOrderService.getDetail(billId);

            // ===== 合同/组装拆卸模块 =====
            case "SALE_CONTRACT":
                return saleContractService.getDetail(billId);
            case "PURCHASE_CONTRACT":
                return purchaseContractService.getDetail(billId);
            case "PRODUCE_ASSEMBLE":
                return produceAssembleService.getDetail(billId);
            case "PRODUCE_DISASSEMBLE":
                return produceDisassembleService.getDetail(billId);

            // ===== 委外模块 =====
            case "SUBCONTRACT_ORDER":
                return subcontractOrderService.getDetail(billId);
            case "SUBCONTRACT_PICK":
                return subcontractPickService.getDetail(billId);
            case "SUBCONTRACT_INBOUND":
                return subcontractInboundService.getDetail(billId);
            case "SUBCONTRACT_RETURN":
                return subcontractReturnService.getDetail(billId);
            case "SUBCONTRACT_SUPPLEMENT":
                return subcontractSupplementService.getDetail(billId);

            // ===== 生产模块（补充） =====
            case "PRODUCTION_RECEIPT":
                return productionReceiptService.getDetail(billId);
            case "OPERATION_REPORT":
                return operationReportService.getDetail(billId);
            case "MRP_PLAN":
                return mrpService.getDetail(billId);

            // ===== 财务模块 =====
            case "PAYMENT_RECEIPT":
                return paymentReceiptMapper.selectById(billId);
            case "PAYMENT_VOUCHER":
                return paymentVoucherMapper.selectById(billId);
            case "SALE_PRE_RECEIPT":
                return salePreReceiptService.getDetail(billId);
            case "PURCHASE_PRE_PAYMENT":
                return purchasePrePaymentService.getDetail(billId);
            case "OPERATION_EXPENSE":
                return operationExpenseService.getById(billId);
            case "COST_CALCULATION":
                return costCalculationService.getDetail(billId);
            case "WAGE_SETTLEMENT":
                return wageSettlementService.getDetail(billId);
            case "PURCHASE_EXPENSE":
                return purchaseExpenseService.getDetail(billId);
            case "SALE_EXPENSE":
                return saleExpenseService.getDetail(billId);

            // ===== 样品/价格策略模块 =====
            case "SAMPLE":
            case "SAMPLE_ORDER":
                return sampleOrderService.getDetail(billId);
            case "FREE_SAMPLE":
                return freeSampleOrderService.getById(billId);
            case "SALE_PRICE_STRATEGY":
                return salePriceStrategyService.getById(billId);
            case "PURCHASE_PRICE_STRATEGY":
                return purchasePriceStrategyService.getById(billId);

            // ===== 二维码打印：商品标签 / 工序卡 =====
            case "MATERIAL":
            case "MATERIAL_LABEL":
                return fetchMaterial(billId);
            case "WORK_OPERATION":
            case "WORK_OPERATION_CARD":
                return workOrderOperationMapper.selectById(billId);

            default:
                log.warn("不支持的单据打印类型: {}", billType);
                return null;
        }
    }

    /** 商品标签打印数据：物料主档 + 单位/分类名称（冗余字段，供标签模板直接使用） */
    private Object fetchMaterial(Long materialId) {
        if (materialId == null) return null;
        com.erp.masterdata.entity.Material m = materialMapper.selectById(materialId);
        if (m == null) return null;
        try {
            if (m.getBaseUnitId() != null) {
                com.erp.basedata.entity.UnitOfMeasure u = unitOfMeasureMapper.selectById(m.getBaseUnitId());
                if (u != null) m.setBaseUnitName(u.getUnitName());
            }
            if (m.getCategoryId() != null) {
                com.erp.basedata.entity.MaterialCategory c = materialCategoryMapper.selectById(m.getCategoryId());
                if (c != null) m.setCategoryName(c.getCategoryName());
            }
        } catch (Exception e) {
            log.warn("商品标签取单位/分类名称失败 materialId={}", materialId, e);
        }
        return m;
    }

    /**
     * 获取单据类型的中文显示名
     */
    public String getBillTypeName(String billType) {
        if (billType == null) return "未知单据";
        switch (billType.toUpperCase()) {
            case "SALE_QUOTE": case "SALE_QUOTATION": return "销售报价单";
            case "SALE_ORDER": return "销售订单";
            case "SALE_DELIVERY": return "销售出库单";
            case "SALE_RETURN": return "销售退货单";
            case "SALE_EXCHANGE": return "销售换货单";
            case "SALE_REPAIR": return "售后维修单";
            case "SALE_INVOICE": return "销售发票";
            case "PURCHASE_REQUEST": return "采购申请单";
            case "PURCHASE_ORDER": return "采购订单";
            case "PURCHASE_INBOUND": return "采购入库单";
            case "PURCHASE_RETURN": return "采购退货单";
            case "PURCHASE_INVOICE": return "采购发票";
            case "STOCK_TRANSFER": return "库存调拨单";
            case "STOCK_CHECK": return "库存盘点单";
            case "MATERIAL_REQUISITION": return "生产领料单";
            case "OTHER_RECEIPT": return "其他入库单";
            case "OTHER_ISSUE": return "其他出库单";
            case "BOM": return "BOM";
            case "MATERIAL": case "MATERIAL_LABEL": return "商品标签";
            case "WORK_OPERATION": case "WORK_OPERATION_CARD": return "工序卡";
            case "WORK_ORDER": return "生产工单";
            case "SALE_CONTRACT": return "销售合同";
            case "PURCHASE_CONTRACT": return "采购合同";
            case "PRODUCE_ASSEMBLE": return "生产组装单";
            case "PRODUCE_DISASSEMBLE": return "生产拆卸单";
            case "SUBCONTRACT_ORDER": return "委外加工单";
            case "SUBCONTRACT_PICK": return "委外发料单";
            case "SUBCONTRACT_INBOUND": return "委外入库单";
            case "SUBCONTRACT_RETURN": return "委外退料单";
            case "SUBCONTRACT_SUPPLEMENT": return "委外补料单";
            case "PRODUCTION_ORDER": return "生产工单";
            case "PRODUCTION_RECEIPT": return "生产入库单";
            case "OPERATION_REPORT": return "报工单";
            case "MRP_PLAN": return "MRP计划";
            case "PAYMENT_RECEIPT": return "收款单";
            case "PAYMENT_VOUCHER": return "付款单";
            case "SALE_PRE_RECEIPT": return "销售预收款单";
            case "PURCHASE_PRE_PAYMENT": return "采购预付款单";
            case "OPERATION_EXPENSE": return "费用分摊";
            case "COST_CALCULATION": return "成本核算";
            case "WAGE_SETTLEMENT": return "工序工资核算单";
            case "PURCHASE_EXPENSE": return "采购费用单";
            case "SALE_EXPENSE": return "销售费用单";
            case "SAMPLE": return "样品订单";
            case "SAMPLE_ORDER": return "打样单";
            case "FREE_SAMPLE": return "免费样品订单";
            case "SALE_PRICE_STRATEGY": return "销售价格策略";
            case "PURCHASE_PRICE_STRATEGY": return "采购价格策略";
            default: return billType;
        }
    }
}
