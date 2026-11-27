package com.erp.ai.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.ai.bill.BillActionRegistry;
import com.erp.ai.dto.EntityCrudConfig;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.service.CurrencyService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialCodeRuleService;
import com.erp.masterdata.service.MaterialService;
import com.erp.permission.config.BillPermissionPathRegistry;
import com.erp.permission.service.FieldPermissionOutputFilter;
import com.erp.serialno.service.SysSerialNoConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.erp.common.util.SortHelper;

@Slf4j
@Service("aiCrudAgentService")
@RequiredArgsConstructor
public class AiCrudAgentService {

    private final ApplicationContext applicationContext;
    private final JdbcTemplate jdbcTemplate;
    private final MaterialCodeRuleService materialCodeRuleService;
    private final SysSerialNoConfigService serialNoService;
    private final BillActionRegistry billActionRegistry;
    /** 字段权限：页面键按实体 @TableName 自动推导；小助手线程无请求上下文时按用户显式解析 */
    private final BillPermissionPathRegistry billPermissionPathRegistry;
    private final FieldPermissionOutputFilter fieldPermissionOutputFilter;

    private final Map<String, EntityCrudConfig> configMap = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        registerBasedata();
        registerCrm();
        registerSale();
        registerPurchase();
        registerInventory();
        registerProduction();
        registerFinance();
        registerSystem();
        registerMasterdata();
        registerExtendedObjects();
        registerExtendedObjects2();
        validateConfigFields();
        validateColumnsAgainstDb();
        // 表达层机制重构（2026-11-22 立项一期）：展示列/筛选字段/中文名映射从实体注解与
        // 列表筛选登记自动生成，手工配置降级为覆盖层——新字段/新功能自动进 AI，不再人工补齐
        AiQuerySchemaEnhancer.Summary s = AiQuerySchemaEnhancer.enhanceAll(configMap);
        log.info("[AI查询自动注册] 增强 {} 个配置：展示列扩充 {} 处、筛选字段扩充 {} 处、仅默认筛选 {} 个；无中文名标签字段 {} 处{}",
                s.enhanced(), s.displayExtended(), s.filterExtended(), s.defaultFilterOnly(), s.unlabeledFields(),
                s.unlabeledFields() > 0 ? "（示例：" + String.join("、", s.unlabeledSamples()) + "）" : "");
        if (s.unlabeledFields() > 0) {
            log.warn("[AI查询自动注册] 存在无中文名标签的实体业务字段，请按规范补 @ExportField 标签（见上方示例）");
        }
        reconcileBillCoverage();
        log.info("[AI CRUD代理] 已注册 {} 个实体的CRUD能力", configMap.size());
    }

    // ===== 启动对账门禁（2026-11-22 立项一期）：单据动作登记 vs AI 查询配置，漏登记当场点名 =====
    // 目标（用户 2026-11-22）：以后新增单据只要按规范走了动作登记，缺 AI 查询就由本门禁在启动/发布时点名，
    // 不再依赖用户报障后人工补齐。映射表 = 动作登记 billType → 期望的 AI 查询意图；null = 尚未注册（二期清单）。
    /** 单据类型 → AI 查询意图（与单据动作登记表一一对应；工序汇报单已下线，有意不注册） */
    private static final Map<String, String> BILL_TYPE_TO_AI_INTENT = buildBillTypeToIntent();

    private static Map<String, String> buildBillTypeToIntent() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SALE_QUOTE", "QUERY_SALE_QUOTE");
        m.put("SALE_ORDER", "QUERY_SALE_ORDER");
        m.put("SALE_CONTRACT", "QUERY_SALE_CONTRACT");
        m.put("SALE_DELIVERY", "QUERY_SALE_DELIVERY");
        m.put("SALE_RETURN", "QUERY_SALE_RETURN");
        m.put("SALE_INVOICE", "QUERY_SALE_INVOICE");
        m.put("SALE_EXCHANGE", "QUERY_SALE_EXCHANGE");   // 2026-11-22 二期补登
        m.put("SALE_REPAIR", "QUERY_SALE_REPAIR");       // 2026-11-22 二期补登
        m.put("PURCHASE_REQUEST", "QUERY_PURCHASE_REQUEST");
        m.put("PURCHASE_ORDER", "QUERY_PURCHASE_ORDER");
        m.put("PURCHASE_CONTRACT", "QUERY_PURCHASE_CONTRACT");
        m.put("PURCHASE_INBOUND", "QUERY_PURCHASE_INBOUND");
        m.put("PURCHASE_RETURN", "QUERY_PURCHASE_RETURN");
        m.put("PURCHASE_INVOICE", "QUERY_PURCHASE_INVOICE");
        m.put("PRODUCTION_ORDER", "QUERY_PRODUCTION_WORKORDER");
        m.put("PRODUCTION_RECEIPT", "QUERY_PRODUCTION_RECEIPT");
        m.put("PRODUCE_ASSEMBLE", "QUERY_PRODUCTION_ASSEMBLE");
        m.put("PRODUCE_DISASSEMBLE", "QUERY_PRODUCTION_DISASSEMBLE");
        m.put("PRODUCTION_RETURN", "QUERY_PRODUCTION_RETURN");       // 2026-11-22 二期补登（领料单族固定过滤）
        m.put("PRODUCTION_SUPPLEMENT", "QUERY_PRODUCTION_SUPPLEMENT"); // 2026-11-22 二期补登（领料单族固定过滤）
        m.put("MATERIAL_REQUISITION", "QUERY_INVENTORY_REQUISITION");
        m.put("STOCK_TRANSFER", "QUERY_INVENTORY_TRANSFER");
        m.put("STOCK_CHECK", "QUERY_INVENTORY_CHECK");
        m.put("OTHER_RECEIPT", "QUERY_INVENTORY_OTHERRECEIPT");
        m.put("OTHER_ISSUE", "QUERY_INVENTORY_OTHERISSUE");
        m.put("SUBCONTRACT_ORDER", "QUERY_SUBCONTRACT_ORDER");
        m.put("SUBCONTRACT_DELIVERY", "QUERY_SUBCONTRACT_PICK");
        m.put("SUBCONTRACT_RECEIPT", "QUERY_SUBCONTRACT_INBOUND");
        m.put("SUBCONTRACT_RETURN", "QUERY_SUBCONTRACT_RETURN");
        m.put("SUBCONTRACT_SUPPLEMENT", "QUERY_SUBCONTRACT_SUPPLEMENT");
        m.put("SAMPLE_ORDER", "QUERY_SAMPLE_ORDER");
        m.put("FREE_SAMPLE", "QUERY_SAMPLE_FREESAMPLE");
        m.put("PURCHASE_EXPENSE", "QUERY_FINANCE_PURCHASEEXPENSE");
        m.put("PURCHASE_PRE_PAYMENT", "QUERY_FINANCE_PURCHASEPREPAYMENT");
        m.put("PAYMENT_RECEIPT", "QUERY_FINANCE_RECEIPT");
        m.put("PAYMENT_VOUCHER", "QUERY_FINANCE_VOUCHER");
        m.put("BOM", "QUERY_PRODUCTION_BOM");
        m.put("SALE_PRE_RECEIPT", "QUERY_FINANCE_SALEPRERECEIPT");
        m.put("OPERATION_EXPENSE", "QUERY_FINANCE_EXPENSE");
        m.put("COST_CALCULATION", "QUERY_FINANCE_COSTCALCULATION");
        m.put("WAGE_SETTLEMENT", "QUERY_FINANCE_WAGESETTLEMENT");
        m.put("SALE_EXPENSE", "QUERY_FINANCE_SALEEXPENSE");
        m.put("SALE_STATEMENT", "QUERY_FINANCE_SALESTATEMENT");
        m.put("PURCHASE_STATEMENT", "QUERY_FINANCE_PURCHASESTATEMENT");
        m.put("OPERATION_REPORT", null);         // 工序汇报单/报工单已下线（2026-10-25），有意不注册
        m.put("MRP_PLAN", "QUERY_PRODUCTION_MRPPLAN");
        return Collections.unmodifiableMap(m);
    }

    /** 对账门禁：缺 AI 查询的单据启动时 WARN 点名（含已下线豁免） */
    private void reconcileBillCoverage() {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : BILL_TYPE_TO_AI_INTENT.entrySet()) {
            if (e.getValue() == null) {
                // null = 有意不注册（唯一豁免：已下线的工序汇报单）
                if (!"OPERATION_REPORT".equals(e.getKey())) {
                    missing.add(e.getKey());
                }
                continue;
            }
            if (!configMap.containsKey(e.getValue())) {
                missing.add(billActionRegistry.label(e.getKey()) + "（" + e.getKey() + "）");
            }
        }
        if (!missing.isEmpty()) {
            log.warn("[AI对账门禁] 以下单据缺 AI 查询配置，请按立项补齐：{}", String.join("、", missing));
        }
        log.info("[AI对账门禁] 单据动作 {} 种，AI 查询覆盖缺口 {} 处", billActionRegistry.allBillTypes().size(), missing.size());
    }

    private void registerBasedata() {
        configMap.put("QUERY_BASEDATA_DEPARTMENT", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_DEPARTMENT")
                .entityClass(com.erp.basedata.entity.Department.class)
                .serviceClass(com.erp.basedata.service.DepartmentService.class)
                .serviceBeanName("departmentServiceImpl")
                .primaryKeyField("deptId")
                .primaryKeyColumn("dept_id")
                .searchFields(List.of("deptName", "deptCode"))
                .displayFields(List.of("deptCode", "deptName", "parentDeptId", "status"))
                .nameField("deptName")
                .codeField("deptCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_EMPLOYEE", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_EMPLOYEE")
                .entityClass(com.erp.basedata.entity.Employee.class)
                .serviceClass(com.erp.basedata.service.EmployeeService.class)
                .serviceBeanName("employeeServiceImpl")
                .primaryKeyField("employeeId")
                .primaryKeyColumn("employee_id")
                .searchFields(List.of("employeeName", "employeeCode", "mobilePhone"))
                .displayFields(List.of("employeeCode", "employeeName", "position", "mobilePhone", "status"))
                .nameField("employeeName")
                .codeField("employeeCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_CURRENCY", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_CURRENCY")
                .entityClass(com.erp.basedata.entity.Currency.class)
                .serviceClass(com.erp.basedata.service.CurrencyService.class)
                .serviceBeanName("currencyServiceImpl")
                .primaryKeyField("currencyId")
                .primaryKeyColumn("currency_id")
                .searchFields(List.of("currencyName", "currencyCode"))
                .displayFields(List.of("currencyCode", "currencyName", "exchangeRate", "status"))
                .nameField("currencyName")
                .codeField("currencyCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_UNIT", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_UNIT")
                .entityClass(com.erp.basedata.entity.UnitOfMeasure.class)
                .serviceClass(com.erp.basedata.service.UnitOfMeasureService.class)
                .serviceBeanName("unitOfMeasureServiceImpl")
                .primaryKeyField("unitId")
                .primaryKeyColumn("unit_id")
                .searchFields(List.of("unitName", "unitCode"))
                .displayFields(List.of("unitCode", "unitName", "status"))
                .nameField("unitName")
                .codeField("unitCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_ACCOUNT", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_ACCOUNT")
                .entityClass(com.erp.basedata.entity.Account.class)
                .serviceClass(com.erp.basedata.service.AccountService.class)
                .serviceBeanName("accountServiceImpl")
                .primaryKeyField("accountId")
                .primaryKeyColumn("account_id")
                .searchFields(List.of("accountName", "accountCode"))
                .displayFields(List.of("accountCode", "accountName", "accountType", "status"))
                .nameField("accountName")
                .codeField("accountCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_PAYMENTTERM", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_PAYMENTTERM")
                .entityClass(com.erp.basedata.entity.PaymentTerm.class)
                .serviceClass(com.erp.basedata.service.PaymentTermService.class)
                .serviceBeanName("paymentTermServiceImpl")
                .primaryKeyField("paymentTermId")
                .primaryKeyColumn("payment_term_id")
                .searchFields(List.of("paymentTermName", "paymentTermCode"))
                .displayFields(List.of("paymentTermCode", "paymentTermName", "paymentDays", "status"))
                .nameField("paymentTermName")
                .codeField("paymentTermCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_PRICELEVEL", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_PRICELEVEL")
                .entityClass(com.erp.basedata.entity.PriceLevel.class)
                .serviceClass(com.erp.basedata.service.PriceLevelService.class)
                .serviceBeanName("priceLevelServiceImpl")
                .primaryKeyField("priceLevelId")
                .primaryKeyColumn("price_level_id")
                .searchFields(List.of("priceLevelName", "priceLevelCode"))
                .displayFields(List.of("priceLevelCode", "priceLevelName", "status"))
                .nameField("priceLevelName")
                .codeField("priceLevelCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_SETTLE", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_SETTLE")
                .entityClass(com.erp.basedata.entity.SettlementMode.class)
                .serviceClass(com.erp.basedata.service.SettlementModeService.class)
                .serviceBeanName("settlementModeServiceImpl")
                .primaryKeyField("settleModeId")
                .primaryKeyColumn("settle_mode_id")
                .searchFields(List.of("settleModeName", "settleModeCode"))
                .displayFields(List.of("settleModeCode", "settleModeName", "status"))
                .nameField("settleModeName")
                .codeField("settleModeCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_MATERIALCATEGORY", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_MATERIALCATEGORY")
                .entityClass(com.erp.basedata.entity.MaterialCategory.class)
                .serviceClass(com.erp.basedata.service.MaterialCategoryService.class)
                .serviceBeanName("materialCategoryServiceImpl")
                .primaryKeyField("categoryId")
                .primaryKeyColumn("category_id")
                .searchFields(List.of("categoryName", "categoryCode"))
                .displayFields(List.of("categoryCode", "categoryName", "status"))
                .nameField("categoryName")
                .codeField("categoryCode")
                .isMasterData(true)
                .build());
    }

    private void registerMasterdata() {
        configMap.put("QUERY_BASEDATA_CUSTOMER", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_CUSTOMER")
                .entityClass(com.erp.masterdata.entity.Customer.class)
                .serviceClass(com.erp.masterdata.service.CustomerService.class)
                .serviceBeanName("customerService")
                .primaryKeyField("customerId")
                .primaryKeyColumn("customer_id")
                .searchFields(List.of("customerName", "customerCode", "contactPerson", "contactPhone"))
                .displayFields(List.of("customerCode", "customerName", "contactPerson", "contactPhone", "customerLevel"))
                .nameField("customerName")
                .codeField("customerCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_SUPPLIER", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_SUPPLIER")
                .entityClass(com.erp.masterdata.entity.Supplier.class)
                .serviceClass(com.erp.masterdata.service.SupplierService.class)
                .serviceBeanName("supplierService")
                .primaryKeyField("supplierId")
                .primaryKeyColumn("supplier_id")
                .searchFields(List.of("supplierName", "supplierCode", "contactPerson"))
                .displayFields(List.of("supplierCode", "supplierName", "contactPerson", "contactPhone"))
                .nameField("supplierName")
                .codeField("supplierCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_MATERIAL", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_MATERIAL")
                .entityClass(com.erp.masterdata.entity.Material.class)
                .serviceClass(com.erp.masterdata.service.MaterialService.class)
                .serviceBeanName("materialService")
                .primaryKeyField("materialId")
                .primaryKeyColumn("material_id")
                .searchFields(List.of("materialName", "materialCode", "model"))
                // 分类与基本单位必须一起返回：小楷新建 BOM/物料前要按"系统现状"给建议，
                // 只返回编码/名称/型号会让它看不到分类，进而猜分类编码或向用户索要（2026-10-28 用户实测）。
                .displayFields(List.of("materialCode", "materialName", "model", "materialType",
                        "categoryCode", "categoryName", "baseUnitName", "salePrice", "purchasePrice"))
                .nameField("materialName")
                .codeField("materialCode")
                .isMasterData(true)
                .aiFilterColumn("is_confidential")
                .aiFilterValue(1)
                .aiFilterOperator("ne")
                .build());
    }

    /**
     * 批次1：查询对象全量登记（基础资料 / 生产 / 库存 / 委外 / 打样 / CRM / 财务 / 获客 / 系统）。
     * 全部字段名均按实体类真实属性登记；未注册的对象见交付报告（部分对象无 MyBatis-Plus IService）。
     */
    private void registerExtendedObjects() {
        // ===== 基础资料 =====
        configMap.put("QUERY_BASEDATA_BANKACCOUNT", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_BANKACCOUNT")
                .entityClass(com.erp.basedata.entity.BankAccount.class)
                .serviceClass(com.erp.basedata.service.BankAccountService.class)
                .serviceBeanName("bankAccountServiceImpl")
                .primaryKeyField("accountId")
                .primaryKeyColumn("account_id")
                .searchFields(List.of("accountNo", "accountName", "bankName"))
                .displayFields(List.of("accountNo", "accountName", "bankName", "accountType", "currentBalance", "isDefault", "status"))
                .nameField("accountName")
                .codeField("accountNo")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_BASEDATA_MATERIALCODERULE", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_MATERIALCODERULE")
                .entityClass(com.erp.masterdata.entity.MaterialCodeRule.class)
                .serviceClass(com.erp.masterdata.service.MaterialCodeRuleService.class)
                .serviceBeanName("materialCodeRuleServiceImpl")
                .primaryKeyField("ruleId")
                .primaryKeyColumn("rule_id")
                .searchFields(List.of("prefix", "categoryName"))
                .displayFields(List.of("prefix", "categoryName", "seqLength", "includeCategoryCode", "includeDate", "isEnabled"))
                .nameField("prefix")
                .codeField("prefix")
                .isMasterData(true)
                .build());

        // ===== 生产 =====
        configMap.put("QUERY_PRODUCTION_ASSEMBLE", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_ASSEMBLE")
                .entityClass(com.erp.produce.assemble.entity.ProduceAssemble.class)
                .serviceClass(com.erp.produce.assemble.service.ProduceAssembleService.class)
                .serviceBeanName("produceAssembleServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("serialNo"))
                .displayFields(List.of("serialNo", "dept", "whName", "amount", "approvalStatus", "creator", "salesmanStr", "dateline"))
                .nameField("serialNo")
                .codeField("serialNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "serialNo", "closeStatus", "whId", "amount"))
                .build());

        configMap.put("QUERY_PRODUCTION_DISASSEMBLE", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_DISASSEMBLE")
                .entityClass(com.erp.produce.assemble.entity.ProduceDisassemble.class)
                .serviceClass(com.erp.produce.assemble.service.ProduceDisassembleService.class)
                .serviceBeanName("produceDisassembleServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("serialNo"))
                .displayFields(List.of("serialNo", "dept", "whName", "amount", "approvalStatus", "creator", "salesmanStr", "dateline"))
                .nameField("serialNo")
                .codeField("serialNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "serialNo", "closeStatus", "whId", "amount"))
                .build());

        // 【2026-11-22 死配置清理】QUERY_PRODUCTION_OPERATIONREPORT 已移除：工序汇报单/报工单页面
        // 于 2026-10-25 下线，AI 查询配置残留会让小楷继续应答一个已不存在的单据（对账门禁豁免项）。

        configMap.put("QUERY_PRODUCTION_MRPPLAN", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_MRPPLAN")
                .entityClass(com.erp.produce.mrp.entity.MrpPlan.class)
                .serviceClass(com.erp.produce.mrp.service.MrpService.class)
                .serviceBeanName("mrpServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("planNo", "planName", "demandSource"))
                .displayFields(List.of("planNo", "planName", "planDate", "demandSource", "planStatus", "totalNetDemand", "totalPurchaseSuggestion", "totalProduceSuggestion"))
                .nameField("planName")
                .codeField("planNo")
                .isMasterData(false)
                .filterFields(List.of("planStatus", "planNo", "planDate", "closeStatus", "demandSource"))
                .build());

        // ===== 库存 =====
        configMap.put("QUERY_INVENTORY_BATCH", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_BATCH")
                .entityClass(com.erp.inventory.entity.InventoryBatch.class)
                .serviceClass(com.erp.inventory.service.InventoryBatchService.class)
                .serviceBeanName("inventoryBatchServiceImpl")
                .primaryKeyField("batchId")
                .primaryKeyColumn("batch_id")
                .searchFields(List.of("batchNo", "manufacturingNo"))
                .displayFields(List.of("batchNo", "materialName", "supplierName", "quantity", "availableQuantity", "expiryDate", "status"))
                .nameField("batchNo")
                .codeField("batchNo")
                .isMasterData(false)
                .filterFields(List.of("status", "batchNo", "expiryDate", "productionDate", "incomingDate", "materialId", "supplierId"))
                .build());

        configMap.put("QUERY_INVENTORY_LEDGER", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_LEDGER")
                .entityClass(com.erp.inventory.entity.InventoryTransaction.class)
                .serviceClass(com.erp.inventory.service.InventoryTransactionService.class)
                .serviceBeanName("inventoryTransactionServiceImpl")
                .primaryKeyField("ledgerId")
                .primaryKeyColumn("ledger_id")
                .searchFields(List.of("transNo", "batchNo", "materialCode", "materialName", "sourceBillNo"))
                .displayFields(List.of("transDate", "transType", "transNo", "materialCode", "materialName", "inQuantity", "outQuantity", "balanceQuantity"))
                .nameField("transNo")
                .codeField("transNo")
                .isMasterData(false)
                .filterFields(List.of("transType", "transNo", "transDate", "materialId", "stockId", "sourceBillNo", "batchNo"))
                .build());

        configMap.put("QUERY_INVENTORY_OTHERRECEIPT", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_OTHERRECEIPT")
                .entityClass(com.erp.inventory.entity.OtherReceipt.class)
                .serviceClass(com.erp.inventory.service.OtherReceiptService.class)
                .serviceBeanName("otherReceiptServiceImpl")
                .primaryKeyField("receiptId")
                .primaryKeyColumn("receipt_id")
                .searchFields(List.of("receiptNo"))
                .displayFields(List.of("receiptNo", "receiptDate", "receiptType", "totalAmount", "status", "approvalStatus"))
                .nameField("receiptNo")
                .codeField("receiptNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "receiptNo", "receiptDate", "closeStatus", "warehouseId", "handlerId", "totalAmount"))
                .build());

        configMap.put("QUERY_INVENTORY_OTHERISSUE", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_OTHERISSUE")
                .entityClass(com.erp.inventory.entity.OtherIssue.class)
                .serviceClass(com.erp.inventory.service.OtherIssueService.class)
                .serviceBeanName("otherIssueServiceImpl")
                .primaryKeyField("issueId")
                .primaryKeyColumn("issue_id")
                .searchFields(List.of("issueNo"))
                .displayFields(List.of("issueNo", "issueDate", "issueType", "totalAmount", "status", "approvalStatus"))
                .nameField("issueNo")
                .codeField("issueNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "issueNo", "issueDate", "closeStatus", "warehouseId", "handlerId", "departmentId"))
                .build());

        // ===== 委外 =====
        configMap.put("QUERY_SUBCONTRACT_ORDER", EntityCrudConfig.builder()
                .intentPrefix("SUBCONTRACT_ORDER")
                .entityClass(com.erp.subcontract.order.entity.SubcontractOrder.class)
                .serviceClass(com.erp.subcontract.order.service.SubcontractOrderService.class)
                .serviceBeanName("subcontractOrderServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("orderNo", "supplierName", "materialName"))
                .displayFields(List.of("orderNo", "supplierName", "materialName", "totalQuantity", "finishedQuantity", "unitPrice", "totalAmount", "approvalStatus"))
                .nameField("orderNo")
                .codeField("orderNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "orderNo", "orderDate", "closeStatus", "supplierId", "materialId", "totalAmount"))
                .build());

        configMap.put("QUERY_SUBCONTRACT_PICK", EntityCrudConfig.builder()
                .intentPrefix("SUBCONTRACT_PICK")
                .entityClass(com.erp.subcontract.pick.entity.SubcontractPick.class)
                .serviceClass(com.erp.subcontract.pick.service.SubcontractPickService.class)
                .serviceBeanName("subcontractPickServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("pickNo", "orderNo", "supplierName"))
                .displayFields(List.of("pickNo", "pickDate", "orderNo", "supplierName", "totalQuantity", "status", "approvalStatus"))
                .nameField("pickNo")
                .codeField("pickNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "pickNo", "pickDate", "orderId", "supplierId"))
                .build());

        configMap.put("QUERY_SUBCONTRACT_INBOUND", EntityCrudConfig.builder()
                .intentPrefix("SUBCONTRACT_INBOUND")
                .entityClass(com.erp.subcontract.inbound.entity.SubcontractInbound.class)
                .serviceClass(com.erp.subcontract.inbound.service.SubcontractInboundService.class)
                .serviceBeanName("subcontractInboundServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("inboundNo", "orderNo", "supplierName"))
                .displayFields(List.of("inboundNo", "inboundDate", "orderNo", "supplierName", "totalQuantity", "processingFee", "status", "approvalStatus"))
                .nameField("inboundNo")
                .codeField("inboundNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "inboundNo", "inboundDate", "orderId", "supplierId"))
                .build());

        configMap.put("QUERY_SUBCONTRACT_RETURN", EntityCrudConfig.builder()
                .intentPrefix("SUBCONTRACT_RETURN")
                .entityClass(com.erp.subcontract.returnn.entity.SubcontractReturn.class)
                .serviceClass(com.erp.subcontract.returnn.service.SubcontractReturnService.class)
                .serviceBeanName("subcontractReturnServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("returnNo", "orderNo", "supplierName"))
                .displayFields(List.of("returnNo", "returnDate", "orderNo", "supplierName", "totalQuantity", "status", "approvalStatus"))
                .nameField("returnNo")
                .codeField("returnNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "returnNo", "returnDate", "orderId", "supplierId"))
                .build());

        configMap.put("QUERY_SUBCONTRACT_SUPPLEMENT", EntityCrudConfig.builder()
                .intentPrefix("SUBCONTRACT_SUPPLEMENT")
                .entityClass(com.erp.subcontract.supplement.entity.SubcontractSupplement.class)
                .serviceClass(com.erp.subcontract.supplement.service.SubcontractSupplementService.class)
                .serviceBeanName("subcontractSupplementServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("supplementNo", "orderNo", "supplierName"))
                .displayFields(List.of("supplementNo", "supplementDate", "orderNo", "supplierName", "totalQuantity", "reason", "status", "approvalStatus"))
                .nameField("supplementNo")
                .codeField("supplementNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "supplementNo", "supplementDate", "orderId", "supplierId"))
                .build());

        // ===== 打样 =====
        configMap.put("QUERY_SAMPLE_ORDER", EntityCrudConfig.builder()
                .intentPrefix("SAMPLE_ORDER")
                .entityClass(com.erp.sample.entity.SampleOrder.class)
                .serviceClass(com.erp.sample.service.SampleOrderService.class)
                .serviceBeanName("sampleOrderServiceImpl")
                .primaryKeyField("sampleId")
                .primaryKeyColumn("sample_id")
                .searchFields(List.of("sampleNo", "sampleName", "customerName", "materialName"))
                .displayFields(List.of("sampleNo", "sampleName", "customerName", "sampleType", "sampleStage", "sampleQty", "status", "approvalStatus"))
                .nameField("sampleName")
                .codeField("sampleNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "sampleNo", "demandDate", "planFinishDate", "closeStatus", "customerId", "materialId"))
                .build());

        configMap.put("QUERY_SAMPLE_FREESAMPLE", EntityCrudConfig.builder()
                .intentPrefix("SAMPLE_FREESAMPLE")
                .entityClass(com.erp.freesample.entity.FreeSampleOrder.class)
                .serviceClass(com.erp.freesample.service.FreeSampleOrderService.class)
                .serviceBeanName("freeSampleOrderServiceImpl")
                .primaryKeyField("orderId")
                .primaryKeyColumn("order_id")
                .searchFields(List.of("orderNo", "customerName", "expressNo"))
                .displayFields(List.of("orderNo", "customerName", "orderDate", "salesName", "warehouseName", "totalQuantity", "status", "approvalStatus"))
                .nameField("orderNo")
                .codeField("orderNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "orderNo", "orderDate", "closeStatus", "customerId", "warehouseId"))
                .build());

        // ===== CRM =====
        configMap.put("QUERY_CRM_CONTACT", EntityCrudConfig.builder()
                .intentPrefix("CRM_CONTACT")
                .entityClass(com.erp.crm.entity.Contact.class)
                .serviceClass(com.erp.crm.service.ContactService.class)
                .serviceBeanName("contactServiceImpl")
                .primaryKeyField("contactId")
                .primaryKeyColumn("contact_id")
                .searchFields(List.of("contactNo", "contactName", "customerName", "mobile", "phone"))
                .displayFields(List.of("contactNo", "contactName", "customerName", "position", "mobile", "email", "isPrimary", "remark"))
                .nameField("contactName")
                .codeField("contactNo")
                .isMasterData(false)
                .filterFields(List.of("customerId", "isPrimary", "contactNo"))
                .build());

        configMap.put("QUERY_CRM_SALESTARGETCOMPANY", EntityCrudConfig.builder()
                .intentPrefix("CRM_SALESTARGETCOMPANY")
                .entityClass(com.erp.crm.entity.SalesTargetCompany.class)
                .serviceClass(com.erp.crm.service.SalesTargetService.class)
                .serviceBeanName("salesTargetServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("description"))
                .displayFields(List.of("targetYear", "annualAmount", "growthRate", "autoGenerateNextYear", "status", "description"))
                .nameField("description")
                .codeField("description")
                .isMasterData(true)
                .build());

        // ===== 财务 =====
        configMap.put("QUERY_FINANCE_COSTCALCULATION", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_COSTCALCULATION")
                .entityClass(com.erp.finance.cost.calculation.entity.CostCalculation.class)
                .serviceClass(com.erp.finance.cost.calculation.service.CostCalculationService.class)
                .serviceBeanName("costCalculationServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("calcNo", "calcName", "materialName", "materialCode"))
                .displayFields(List.of("calcNo", "calcName", "calcDate", "materialName", "quantity", "totalCost", "unitCost", "status"))
                .nameField("calcName")
                .codeField("calcNo")
                .isMasterData(false)
                .filterFields(List.of("status", "calcNo", "calcDate", "closeStatus", "materialId", "totalCost", "calcMethod"))
                .build());

        configMap.put("QUERY_FINANCE_COSTCONFIG", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_COSTCONFIG")
                .entityClass(com.erp.finance.entity.CostAccountingConfig.class)
                .serviceClass(com.erp.finance.service.CostAccountingConfigService.class)
                .serviceBeanName("costAccountingConfigServiceImpl")
                .primaryKeyField("configId")
                .primaryKeyColumn("config_id")
                .searchFields(List.of("configName", "defaultMethod"))
                .displayFields(List.of("configName", "defaultMethod", "isEnabled", "remark"))
                .nameField("configName")
                .codeField("configName")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_FINANCE_WAGESETTLEMENT", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_WAGESETTLEMENT")
                .entityClass(com.erp.finance.wage.entity.WageSettlement.class)
                .serviceClass(com.erp.finance.wage.service.WageSettlementService.class)
                .serviceBeanName("wageSettlementServiceImpl")
                .primaryKeyField("settlementId")
                .primaryKeyColumn("settlement_id")
                .searchFields(List.of("settlementNo", "employeeName", "deptName", "period"))
                .displayFields(List.of("settlementNo", "period", "deptName", "employeeName", "qualifiedQuantity", "totalAmount", "status", "approvalStatus"))
                .nameField("settlementNo")
                .codeField("settlementNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "settlementNo", "period", "deptId", "employeeId"))
                .build());

        configMap.put("QUERY_FINANCE_SALEPRERECEIPT", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_SALEPRERECEIPT")
                .entityClass(com.erp.finance.prepayment.sale.entity.SalePreReceipt.class)
                .serviceClass(com.erp.finance.prepayment.sale.service.SalePreReceiptService.class)
                .serviceBeanName("salePreReceiptServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("receiptNo", "customerName", "orderNo"))
                .displayFields(List.of("receiptNo", "customerName", "receiptDate", "amount", "usedAmount", "remainAmount", "status", "paymentMethod"))
                .nameField("receiptNo")
                .codeField("receiptNo")
                .isMasterData(false)
                .filterFields(List.of("status", "receiptNo", "receiptDate", "closeStatus", "customerId", "cashierId"))
                .build());

        configMap.put("QUERY_FINANCE_PURCHASEPREPAYMENT", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_PURCHASEPREPAYMENT")
                .entityClass(com.erp.finance.prepayment.purchase.entity.PurchasePrePayment.class)
                .serviceClass(com.erp.finance.prepayment.purchase.service.PurchasePrePaymentService.class)
                .serviceBeanName("purchasePrePaymentServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("paymentNo", "supplierName", "orderNo"))
                .displayFields(List.of("paymentNo", "supplierName", "paymentDate", "amount", "usedAmount", "remainAmount", "status", "approvalStatus"))
                .nameField("paymentNo")
                .codeField("paymentNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "paymentNo", "paymentDate", "closeStatus", "supplierId", "cashierId"))
                .build());

        configMap.put("QUERY_FINANCE_SALEEXPENSE", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_SALEEXPENSE")
                .entityClass(com.erp.finance.expense.sale.entity.SaleExpense.class)
                .serviceClass(com.erp.finance.expense.sale.service.SaleExpenseService.class)
                .serviceBeanName("saleExpenseServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("expenseNo", "customerName", "expenseTypeName"))
                .displayFields(List.of("expenseNo", "customerName", "expenseDate", "expenseTypeName", "totalAmount", "status", "remark"))
                .nameField("expenseNo")
                .codeField("expenseNo")
                .isMasterData(false)
                .filterFields(List.of("status", "expenseNo", "expenseDate", "customerId", "expenseTypeId", "totalAmount"))
                .build());

        configMap.put("QUERY_FINANCE_PURCHASEEXPENSE", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_PURCHASEEXPENSE")
                .entityClass(com.erp.finance.expense.purchase.entity.PurchaseExpense.class)
                .serviceClass(com.erp.finance.expense.purchase.service.PurchaseExpenseService.class)
                .serviceBeanName("purchaseExpenseServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("expenseNo", "supplierName", "expenseTypeName"))
                .displayFields(List.of("expenseNo", "supplierName", "expenseDate", "expenseTypeName", "totalAmount", "status", "approvalStatus"))
                .nameField("expenseNo")
                .codeField("expenseNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "expenseNo", "expenseDate", "supplierId", "expenseTypeId", "totalAmount"))
                .build());

        configMap.put("QUERY_FINANCE_SALESTATEMENT", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_SALESTATEMENT")
                .entityClass(com.erp.finance.salebill.entity.SaleStatement.class)
                .serviceClass(com.erp.finance.salebill.service.SaleStatementService.class)
                .serviceBeanName("saleStatementServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("statementNo", "customerName"))
                .displayFields(List.of("statementNo", "customerName", "startDate", "endDate", "totalAmount", "receivedAmount", "unpaidAmount", "status"))
                .nameField("statementNo")
                .codeField("statementNo")
                .isMasterData(false)
                .filterFields(List.of("status", "statementNo", "startDate", "endDate", "customerId", "totalAmount"))
                .build());

        configMap.put("QUERY_FINANCE_PURCHASESTATEMENT", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_PURCHASESTATEMENT")
                .entityClass(com.erp.finance.purchasebill.entity.PurchaseStatement.class)
                .serviceClass(com.erp.finance.purchasebill.service.PurchaseStatementService.class)
                .serviceBeanName("purchaseStatementServiceImpl")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("statementNo", "supplierName"))
                .displayFields(List.of("statementNo", "supplierName", "startDate", "endDate", "totalAmount", "paidAmount", "unpaidAmount", "status"))
                .nameField("statementNo")
                .codeField("statementNo")
                .isMasterData(false)
                .filterFields(List.of("status", "statementNo", "startDate", "endDate", "supplierId", "totalAmount"))
                .build());

        // ===== 获客 =====
        configMap.put("QUERY_ACQUISITION_TASK", EntityCrudConfig.builder()
                .intentPrefix("ACQUISITION_TASK")
                .entityClass(com.erp.acquisition.entity.AcquisitionTask.class)
                .serviceClass(com.erp.acquisition.service.AcquisitionTaskService.class)
                .serviceBeanName("acquisitionTaskServiceImpl")
                .primaryKeyField("taskId")
                .primaryKeyColumn("task_id")
                .searchFields(List.of("taskNo", "taskName", "platform", "industry", "area"))
                .displayFields(List.of("taskNo", "taskName", "platform", "industry", "area", "status", "capturedCount", "importedCount"))
                .nameField("taskName")
                .codeField("taskNo")
                .isMasterData(false)
                .filterFields(List.of("status", "taskNo", "platform", "industry", "area"))
                .build());

        configMap.put("QUERY_ACQUISITION_RESULT", EntityCrudConfig.builder()
                .intentPrefix("ACQUISITION_RESULT")
                .entityClass(com.erp.acquisition.entity.AcquisitionResult.class)
                .serviceClass(com.erp.acquisition.service.AcquisitionResultService.class)
                .serviceBeanName("acquisitionResultServiceImpl")
                .primaryKeyField("resultId")
                .primaryKeyColumn("result_id")
                .searchFields(List.of("companyName", "creditCode", "legalPerson", "contactPhone"))
                .displayFields(List.of("companyName", "creditCode", "legalPerson", "contactPhone", "area", "industry", "companyStatus", "reviewStatus"))
                .nameField("companyName")
                .codeField("creditCode")
                .isMasterData(false)
                .filterFields(List.of("taskId", "reviewStatus", "companyName", "area", "industry"))
                .build());

        // ===== 系统 =====
        configMap.put("QUERY_SYSTEM_APPROVALTASK", EntityCrudConfig.builder()
                .intentPrefix("SYSTEM_APPROVALTASK")
                .entityClass(com.erp.approval.entity.ApprovalTask.class)
                .serviceClass(com.erp.approval.service.ApprovalRuntimeService.class)
                .serviceBeanName("approvalRuntimeService")
                .primaryKeyField("taskId")
                .primaryKeyColumn("task_id")
                .searchFields(List.of("billNo", "billType", "currentApprover"))
                .displayFields(List.of("billType", "billNo", "taskStatus", "currentApprover", "submitBy", "submitTime", "finishTime", "nodeOrder"))
                .nameField("billNo")
                .codeField("billNo")
                .isMasterData(false)
                .filterFields(List.of("taskStatus", "billType", "billNo", "currentApprover", "submitBy", "flowId", "billId"))
                .build());
    }

    /**
     * 批次1补登：为缺少 MyBatis-Plus IService 的原服务补建 AI 查询专用薄服务（见 com.erp.ai.query 包）后，
     * 登记质检 / 财务收付款 / 消息通知等查询对象。全部字段名均按实体类真实属性登记。
     * （租户 / 部署版本属平台级数据，不在本通用查询登记，改由 platform_data_query 工具走平台通道查询）
     */
    private void registerExtendedObjects2() {
        // ===== 质检 =====
        configMap.put("QUERY_BASEDATA_QCTEMPLATE", EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_QCTEMPLATE")
                .entityClass(com.erp.qc.entity.QcTemplate.class)
                .serviceClass(com.erp.ai.query.QcTemplateAiQueryService.class)
                .serviceBeanName("qcTemplateAiQueryService")
                .primaryKeyField("templateId")
                .primaryKeyColumn("template_id")
                .searchFields(List.of("templateCode", "templateName", "operationName", "qcType"))
                .displayFields(List.of("templateCode", "templateName", "qcType", "operationName", "sampleRatio", "allowedDefectRatio", "isEnabled", "remark"))
                .nameField("templateName")
                .codeField("templateCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_PRODUCTION_QCRECORD", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_QCRECORD")
                .entityClass(com.erp.qc.entity.QcRecord.class)
                .serviceClass(com.erp.ai.query.QcRecordAiQueryService.class)
                .serviceBeanName("qcRecordAiQueryService")
                .primaryKeyField("recordId")
                .primaryKeyColumn("record_id")
                .searchFields(List.of("recordNo", "orderNo", "operationName", "inspectorName", "result"))
                .displayFields(List.of("recordNo", "orderNo", "operationName", "qcType", "inspectQty", "qualifiedQty", "defectQty", "result"))
                .nameField("recordNo")
                .codeField("recordNo")
                .isMasterData(false)
                .filterFields(List.of("result", "qcType", "recordNo", "inspectTime", "orderId", "templateId", "inspectorId", "handleType"))
                .build());

        // ===== CRM / 客户 =====
        configMap.put("QUERY_CRM_SALESTARGETEMPLOYEE", EntityCrudConfig.builder()
                .intentPrefix("CRM_SALESTARGETEMPLOYEE")
                .entityClass(com.erp.crm.entity.SalesTargetEmployee.class)
                .serviceClass(com.erp.ai.query.SalesTargetEmployeeAiQueryService.class)
                .serviceBeanName("salesTargetEmployeeAiQueryService")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("employeeName", "deptName", "allocationStatus"))
                .displayFields(List.of("employeeName", "deptName", "targetYear", "annualTargetAmount", "actualAmount", "completionRate", "allocationStatus"))
                .nameField("employeeName")
                .codeField("employeeName")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_CRM_WARNINGCONFIG", EntityCrudConfig.builder()
                .intentPrefix("CRM_WARNINGCONFIG")
                .entityClass(com.erp.masterdata.entity.CustomerWarningConfig.class)
                .serviceClass(com.erp.ai.query.CustomerWarningConfigAiQueryService.class)
                .serviceBeanName("customerWarningConfigAiQueryService")
                .primaryKeyField("warningId")
                .primaryKeyColumn("warning_id")
                .searchFields(List.of("warningType", "receiverRole"))
                .displayFields(List.of("warningType", "thresholdDays", "isEnabled", "receiverRole", "remark"))
                .nameField("warningType")
                .codeField("warningType")
                .isMasterData(true)
                .build());

        // ===== 财务 =====
        configMap.put("QUERY_FINANCE_RECEIPT", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_RECEIPT")
                .entityClass(com.erp.finance.entity.PaymentReceipt.class)
                .serviceClass(com.erp.ai.query.PaymentReceiptAiQueryService.class)
                .serviceBeanName("paymentReceiptAiQueryService")
                .primaryKeyField("receiptId")
                .primaryKeyColumn("receipt_id")
                .searchFields(List.of("receiptNo", "customerName", "documentNo"))
                .displayFields(List.of("receiptNo", "receiptDate", "customerName", "amount", "receivedAmount", "status", "approvalStatus", "closeStatus"))
                .nameField("receiptNo")
                .codeField("receiptNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "receiptNo", "receiptDate", "closeStatus", "customerId", "amount"))
                .build());

        configMap.put("QUERY_FINANCE_VOUCHER", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_VOUCHER")
                .entityClass(com.erp.finance.entity.PaymentVoucher.class)
                .serviceClass(com.erp.ai.query.PaymentVoucherAiQueryService.class)
                .serviceBeanName("paymentVoucherAiQueryService")
                .primaryKeyField("paymentId")
                .primaryKeyColumn("payment_id")
                .searchFields(List.of("paymentNo", "supplierName", "documentNo"))
                .displayFields(List.of("paymentNo", "paymentDate", "supplierName", "amount", "paidAmount", "status", "approvalStatus", "closeStatus"))
                .nameField("paymentNo")
                .codeField("paymentNo")
                .isMasterData(false)
                .filterFields(List.of("status", "approvalStatus", "paymentNo", "paymentDate", "closeStatus", "supplierId", "amount"))
                .build());

        // ===== 系统 =====
        configMap.put("QUERY_SYSTEM_NOTIFICATION", EntityCrudConfig.builder()
                .intentPrefix("SYSTEM_NOTIFICATION")
                .entityClass(com.erp.notification.entity.SysNotification.class)
                .serviceClass(com.erp.ai.query.SysNotificationAiQueryService.class)
                .serviceBeanName("sysNotificationAiQueryService")
                .primaryKeyField("id")
                .primaryKeyColumn("id")
                .searchFields(List.of("title", "content", "receiverName", "sourceNo"))
                .displayFields(List.of("title", "type", "receiverName", "urgency", "isRead", "readAt", "createdAt"))
                .nameField("title")
                .codeField("title")
                .isMasterData(false)
                .filterFields(List.of("type", "isRead", "urgency", "receiverId", "sourceType", "sourceNo"))
                .build());

        // 【2026-09-30】原来的 QUERY_SYSTEM_TENANT / QUERY_SYSTEM_DEPLOYVERSION 已移除：
        // 租户（sys_tenant）在平台库、部署版本在部署控制台库，均不在当前租户库，走本通用查询
        // 会"查了报错/查了空"（假能力，开机自检也会报 sys_tenant 在当前库不存在）。
        // 这两类平台级数据改由专用只读工具 platform_data_query 走平台通道查询（仅平台管理员）。
    }

    /**
     * 启动自检：用反射断言每个查询配置里引用的字段名在该实体类上真实存在。
     * 背景：字段名写错时查询会静默为空（前端出现空列），且编译期不报错 —— 必须开机即暴露。
     * 口径：校验失败只打印错误日志、不抛异常、不阻断启动（避免影响线上）。
     */
    /**
     * 由「外键名称补全器」在查询结果里动态填充的名称键（实体上并不存在这些字段）。
     * 见 fillForeignDisplayNames 里逐条 fillNameFrom 的目标键 —— 自检时必须放行，
     * 否则会把"本应由补全器填名称"的正确配置误判成错字段（2026-09-28 实跑踩到）。
     */
    private static final Set<String> FOREIGN_NAME_KEYS = Set.of(
            "customerName", "supplierName", "materialName", "stockName",
            "fromStockName", "toStockName", "warehouseName", "employeeName");

    private void validateConfigFields() {
        int total = 0;
        int bad = 0;
        for (Map.Entry<String, EntityCrudConfig> entry : configMap.entrySet()) {
            EntityCrudConfig cfg = entry.getValue();
            total++;
            List<String> fields = new ArrayList<>();
            if (cfg.getPrimaryKeyField() != null) fields.add(cfg.getPrimaryKeyField());
            if (cfg.getNameField() != null) fields.add(cfg.getNameField());
            if (cfg.getCodeField() != null) fields.add(cfg.getCodeField());
            if (cfg.getSearchFields() != null) fields.addAll(cfg.getSearchFields());
            if (cfg.getDisplayFields() != null) fields.addAll(cfg.getDisplayFields());
            if (cfg.getFilterFields() != null) fields.addAll(cfg.getFilterFields());
            for (String field : fields) {
                if (field == null || field.isBlank()) continue;
                if (FOREIGN_NAME_KEYS.contains(field)) continue;
                if (findField(cfg.getEntityClass(), field) == null) {
                    bad++;
                    log.error("[AI CRUD自检] 配置 {} 的字段 {} 在实体上不存在", entry.getKey(), field);
                }
            }
        }
        log.info("[AI CRUD自检] 配置 {} 个，字段校验不通过 {} 处", total, bad);
    }

    /**
     * 启动自检（对库）：查询/筛选类字段最终会按「属性名转下划线」拼成 SQL 列名（**不认实体上的列名映射**），
     * 所以只有"属性名蛇形化 == 真实列名"才安全；不一致的字段会在运行期报 SQL 错或静默返回空列。
     * 2026-09-28 实锤：采购入库单的「入库日期/部门/仓库」分别映射到 receipt_date/department_id/warehouse_id，
     * 按属性名拼出来就是错的列名 —— 故必须逐条对库核验。
     * 口径：只打印、不抛异常、不阻断启动（避免影响线上）。
     */
    private void validateColumnsAgainstDb() {
        int checked = 0;
        int bad = 0;
        for (Map.Entry<String, EntityCrudConfig> entry : configMap.entrySet()) {
            EntityCrudConfig cfg = entry.getValue();
            String table = tableNameOf(cfg.getEntityClass());
            if (table == null) continue;
            Set<String> cols;
            try {
                cols = new HashSet<>(jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                        String.class, table));
            } catch (Exception e) {
                log.warn("[AI CRUD对库自检] 读取表结构失败 table={}: {}", table, e.getMessage());
                continue;
            }
            if (cols.isEmpty()) {
                log.warn("[AI CRUD对库自检] 表 {} 在当前库中不存在，跳过（可能是平台级表）", table);
                continue;
            }
            List<String> fields = new ArrayList<>();
            if (cfg.getNameField() != null) fields.add(cfg.getNameField());
            if (cfg.getCodeField() != null) fields.add(cfg.getCodeField());
            if (cfg.getSearchFields() != null) fields.addAll(cfg.getSearchFields());
            if (cfg.getDisplayFields() != null) fields.addAll(cfg.getDisplayFields());
            if (cfg.getFilterFields() != null) fields.addAll(cfg.getFilterFields());
            for (String field : fields) {
                if (field == null || field.isBlank()) continue;
                if (FOREIGN_NAME_KEYS.contains(field)) continue;
                checked++;
                String col = columnOf(cfg.getEntityClass(), field);
                if (!cols.contains(col)) {
                    bad++;
                    log.error("[AI CRUD对库自检] 配置 {} 的字段 {} → 列 {} 在表 {} 中不存在",
                            entry.getKey(), field, col, table);
                }
            }
        }
        log.info("[AI CRUD对库自检] 校验列 {} 处，不存在 {} 处", checked, bad);
    }

    /** 取实体对应的表名（无 @TableName 或值为空时返回 null） */
    private static String tableNameOf(Class<?> clazz) {
        if (clazz == null) return null;
        com.baomidou.mybatisplus.annotation.TableName tn =
                clazz.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
        if (tn == null || tn.value() == null || tn.value().isBlank()) return null;
        return tn.value();
    }

    private void registerCrm() {
        configMap.put("QUERY_CRM_LEAD", EntityCrudConfig.builder()
                .intentPrefix("CRM_LEAD")
                .entityClass(com.erp.crm.entity.Lead.class)
                .serviceClass(com.erp.crm.service.LeadService.class)
                .serviceBeanName("leadServiceImpl")
                .primaryKeyField("leadId")
                .primaryKeyColumn("lead_id")
                .searchFields(List.of("leadName", "companyName", "contactPhone"))
                .displayFields(List.of("leadNo", "leadName", "companyName", "contactPhone", "leadStatus"))
                .nameField("leadName")
                .codeField("leadNo")
                .isMasterData(false)
                .build());

        configMap.put("QUERY_CRM_OPPORTUNITY", EntityCrudConfig.builder()
                .intentPrefix("CRM_OPPORTUNITY")
                .entityClass(com.erp.crm.entity.Opportunity.class)
                .serviceClass(com.erp.crm.service.OpportunityService.class)
                .serviceBeanName("opportunityServiceImpl")
                .primaryKeyField("opportunityId")
                .primaryKeyColumn("opportunity_id")
                .searchFields(List.of("opportunityName", "customerName"))
                .displayFields(List.of("opportunityNo", "opportunityName", "customerName", "salesStage", "probability"))
                .nameField("opportunityName")
                .codeField("opportunityNo")
                .isMasterData(false)
                .build());

        configMap.put("QUERY_CRM_FOLLOWUP", EntityCrudConfig.builder()
                .intentPrefix("CRM_FOLLOWUP")
                .entityClass(com.erp.crm.entity.FollowUp.class)
                .serviceClass(com.erp.crm.service.FollowUpService.class)
                .serviceBeanName("followUpServiceImpl")
                .primaryKeyField("followUpId")
                .primaryKeyColumn("follow_up_id")
                .searchFields(List.of("summary", "content"))
                .displayFields(List.of("followUpNo", "summary", "followUpType", "followUpDate"))
                .nameField("summary")
                .codeField("followUpNo")
                .isMasterData(false)
                .build());

        configMap.put("QUERY_CRM_COMPLAINT", EntityCrudConfig.builder()
                .intentPrefix("CRM_COMPLAINT")
                .entityClass(com.erp.crm.entity.Complaint.class)
                .serviceClass(com.erp.crm.service.ComplaintService.class)
                .serviceBeanName("complaintServiceImpl")
                .primaryKeyField("complaintId")
                .primaryKeyColumn("complaint_id")
                .searchFields(List.of("complaintContent", "customerName"))
                .displayFields(List.of("complaintNo", "complaintContent", "customerName", "handleStatus"))
                .nameField("complaintContent")
                .codeField("complaintNo")
                .isMasterData(false)
                .build());

        configMap.put("QUERY_CRM_SURVEY", EntityCrudConfig.builder()
                .intentPrefix("CRM_SURVEY")
                .entityClass(com.erp.crm.entity.Survey.class)
                .serviceClass(com.erp.crm.service.SurveyService.class)
                .serviceBeanName("surveyServiceImpl")
                .primaryKeyField("surveyId")
                .primaryKeyColumn("survey_id")
                .searchFields(List.of("surveyName", "customerName"))
                .displayFields(List.of("surveyNo", "surveyName", "customerName", "status"))
                .nameField("surveyName")
                .codeField("surveyNo")
                .isMasterData(false)
                .build());
    }

    private void registerSale() {
        configMap.put("QUERY_SALE_QUOTE", EntityCrudConfig.builder()
                .intentPrefix("SALE_QUOTE")
                .entityClass(com.erp.sale.entity.SaleQuote.class)
                .serviceClass(com.erp.sale.service.SaleQuoteService.class)
                .serviceBeanName("saleQuoteService")
                .primaryKeyField("quoteId")
                .primaryKeyColumn("quote_id")
                .searchFields(List.of("quoteNo"))
                .displayFields(List.of("quoteNo", "customerName", "totalAmount", "status"))
                .nameField("quoteNo")
                .codeField("quoteNo")
                .isMasterData(false)
                // 条件筛选白名单（对应销售报价单列表页的搜索条件；2026-09-18 新增）
                .filterFields(List.of("status", "quoteNo", "customerId", "quoteDate", "deliveryDate", "totalAmount", "closeStatus"))
                .build());

        configMap.put("QUERY_SALE_ORDER", EntityCrudConfig.builder()
                .intentPrefix("SALE_ORDER")
                .entityClass(com.erp.sale.entity.SaleOrder.class)
                .serviceClass(com.erp.sale.service.SaleOrderService.class)
                .serviceBeanName("saleOrderService")
                .primaryKeyField("orderId")
                .primaryKeyColumn("order_id")
                .searchFields(List.of("orderNo", "customerName"))
                .displayFields(List.of("orderNo", "customerName", "totalAmount", "status", "orderDate"))
                .nameField("orderNo")
                .codeField("orderNo")
                .isMasterData(false)
                // 条件筛选白名单（对应销售订单列表页的搜索条件；2026-09-18 新增）
                .filterFields(List.of("status", "orderNo", "customerId", "orderDate", "deliveryDate", "totalAmount", "closeStatus"))
                .build());

        configMap.put("QUERY_SALE_DELIVERY", EntityCrudConfig.builder()
                .intentPrefix("SALE_DELIVERY")
                .entityClass(com.erp.sale.entity.SaleDelivery.class)
                .serviceClass(com.erp.sale.service.SaleDeliveryService.class)
                .serviceBeanName("saleDeliveryService")
                .primaryKeyField("deliveryId")
                .primaryKeyColumn("delivery_id")
                .searchFields(List.of("deliveryNo"))
                .displayFields(List.of("deliveryNo", "customerName", "totalAmount", "status", "deliveryDate"))
                .nameField("deliveryNo")
                .codeField("deliveryNo")
                .isMasterData(false)
                // 条件筛选白名单（对应销售出库单列表页的搜索条件；2026-09-18 新增）
                .filterFields(List.of("status", "deliveryNo", "customerId", "deliveryDate", "totalAmount", "closeStatus"))
                .build());

        configMap.put("QUERY_SALE_RETURN", EntityCrudConfig.builder()
                .intentPrefix("SALE_RETURN")
                .entityClass(com.erp.sale.entity.SaleReturn.class)
                .serviceClass(com.erp.sale.service.SaleReturnService.class)
                .serviceBeanName("saleReturnService")
                .primaryKeyField("returnId")
                .primaryKeyColumn("return_id")
                .searchFields(List.of("returnNo"))
                .displayFields(List.of("returnNo", "customerName", "totalAmount", "status"))
                .nameField("returnNo")
                .codeField("returnNo")
                .isMasterData(false)
                // 条件筛选白名单（对应销售退货单列表页的搜索条件；2026-09-18 新增）
                .filterFields(List.of("status", "returnNo", "customerId", "returnDate", "totalAmount", "closeStatus"))
                .build());

        configMap.put("QUERY_SALE_INVOICE", EntityCrudConfig.builder()
                .intentPrefix("SALE_INVOICE")
                .entityClass(com.erp.sale.entity.SaleInvoice.class)
                .serviceClass(com.erp.sale.service.SaleInvoiceService.class)
                .serviceBeanName("saleInvoiceService")
                .primaryKeyField("invoiceId")
                .primaryKeyColumn("invoice_id")
                .searchFields(List.of("invoiceNo"))
                .displayFields(List.of("invoiceNo", "customerName", "totalAmount", "status"))
                .nameField("invoiceNo")
                .codeField("invoiceNo")
                .isMasterData(false)
                // 条件筛选白名单（对应销售发票列表页的搜索条件；2026-09-18 新增）
                .filterFields(List.of("status", "invoiceNo", "customerId", "invoiceDate", "totalAmount", "closeStatus"))
                .build());

        configMap.put("QUERY_SALE_CONTRACT", EntityCrudConfig.builder()
                .intentPrefix("SALE_CONTRACT")
                .entityClass(com.erp.sale.entity.SaleContract.class)
                .serviceClass(com.erp.sale.service.SaleContractService.class)
                .serviceBeanName("saleContractService")
                .primaryKeyField("contractId")
                .primaryKeyColumn("contract_id")
                .searchFields(List.of("contractNo", "customerShortName"))
                .displayFields(List.of("contractNo", "customerName", "totalAmount", "status"))
                .nameField("contractNo")
                .codeField("contractNo")
                .isMasterData(false)
                // 条件筛选白名单（对应销售合同列表页的搜索条件；2026-09-18 新增）
                .filterFields(List.of("status", "contractNo", "customerId", "contractDate", "deliveryDate", "totalAmount", "closeStatus"))
                .build());

        // ===== 2026-11-22 二期补登：销售换货单 / 售后维修单（统一状态机单据，基类即 IService，AI 查询链路通用） =====
        configMap.put("QUERY_SALE_EXCHANGE", EntityCrudConfig.builder()
                .intentPrefix("SALE_EXCHANGE")
                .entityClass(com.erp.sale.entity.SaleExchange.class)
                .serviceClass(com.erp.sale.service.SaleExchangeService.class)
                .serviceBeanName("saleExchangeServiceImpl")
                .primaryKeyField("exchangeId")
                .primaryKeyColumn("exchange_id")
                .searchFields(List.of("exchangeNo"))
                .displayFields(List.of("exchangeNo", "customerName", "totalQuantity", "netAmount", "status", "exchangeDate"))
                .nameField("exchangeNo")
                .codeField("exchangeNo")
                .isMasterData(false)
                // 条件筛选白名单（对应销售换货单列表页搜索条件）
                .filterFields(List.of("status", "approvalStatus", "exchangeNo", "exchangeDate", "closeStatus", "customerId", "netAmount"))
                .build());

        configMap.put("QUERY_SALE_REPAIR", EntityCrudConfig.builder()
                .intentPrefix("SALE_REPAIR")
                .entityClass(com.erp.sale.entity.SaleRepair.class)
                .serviceClass(com.erp.sale.service.SaleRepairService.class)
                .serviceBeanName("saleRepairServiceImpl")
                .primaryKeyField("repairId")
                .primaryKeyColumn("repair_id")
                .searchFields(List.of("repairNo"))
                .displayFields(List.of("repairNo", "customerName", "repairFee", "status", "repairDate"))
                .nameField("repairNo")
                .codeField("repairNo")
                .isMasterData(false)
                // 条件筛选白名单（对应售后维修单列表页搜索条件）
                .filterFields(List.of("status", "approvalStatus", "repairNo", "repairDate", "closeStatus", "repairFee"))
                .build());
    }

    private void registerPurchase() {
        configMap.put("QUERY_PURCHASE_REQUEST", EntityCrudConfig.builder()
                .intentPrefix("PURCHASE_REQUEST")
                .entityClass(com.erp.purchase.entity.PurchaseRequest.class)
                .serviceClass(com.erp.purchase.service.PurchaseRequestService.class)
                .serviceBeanName("purchaseRequestService")
                .primaryKeyField("requestId")
                .primaryKeyColumn("request_id")
                .searchFields(List.of("requestNo", "applierName"))
                .displayFields(List.of("requestNo", "totalQty", "status", "requestDate"))
                .nameField("requestNo")
                .codeField("requestNo")
                .isMasterData(false)
                // 条件筛选白名单（对应采购申请单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "requestNo", "requestDate", "demandDate", "closeStatus", "departmentId", "applicantId", "applyType", "totalQty"))
                .build());

        configMap.put("QUERY_PURCHASE_ORDER", EntityCrudConfig.builder()
                .intentPrefix("PURCHASE_ORDER")
                .entityClass(com.erp.purchase.entity.PurchaseOrder.class)
                .serviceClass(com.erp.purchase.service.PurchaseOrderService.class)
                .serviceBeanName("purchaseOrderService")
                .primaryKeyField("orderId")
                .primaryKeyColumn("order_id")
                .searchFields(List.of("orderNo", "supplierName"))
                .displayFields(List.of("orderNo", "supplierName", "totalAmount", "status", "orderDate"))
                .nameField("orderNo")
                .codeField("orderNo")
                .isMasterData(false)
                // 条件筛选白名单（对应采购订单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "orderNo", "orderDate", "deliveryDate", "closeStatus", "supplierId", "departmentId", "buyerId", "totalAmount"))
                .build());

        configMap.put("QUERY_PURCHASE_INBOUND", EntityCrudConfig.builder()
                .intentPrefix("PURCHASE_INBOUND")
                .entityClass(com.erp.purchase.entity.PurchaseInbound.class)
                .serviceClass(com.erp.purchase.service.PurchaseInboundService.class)
                .serviceBeanName("purchaseInboundService")
                .primaryKeyField("receiptId")
                .primaryKeyColumn("receipt_id")
                .searchFields(List.of("receiptNo", "supplierName"))
                .displayFields(List.of("receiptNo", "supplierName", "totalAmount", "status"))
                .nameField("receiptNo")
                .codeField("receiptNo")
                .isMasterData(false)
                // 条件筛选白名单（对应采购入库单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "receiptNo", "closeStatus", "supplierId", "buyerId", "totalAmount"))
                .build());

        configMap.put("QUERY_PURCHASE_RETURN", EntityCrudConfig.builder()
                .intentPrefix("PURCHASE_RETURN")
                .entityClass(com.erp.purchase.entity.PurchaseReturn.class)
                .serviceClass(com.erp.purchase.service.PurchaseReturnService.class)
                .serviceBeanName("purchaseReturnService")
                .primaryKeyField("returnId")
                .primaryKeyColumn("return_id")
                .searchFields(List.of("returnNo", "supplierName"))
                .displayFields(List.of("returnNo", "supplierName", "totalAmount", "status"))
                .nameField("returnNo")
                .codeField("returnNo")
                .isMasterData(false)
                // 条件筛选白名单（对应采购退货单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "returnNo", "returnDate", "closeStatus", "supplierId", "buyerId", "returnType", "totalAmount"))
                .build());

        configMap.put("QUERY_PURCHASE_INVOICE", EntityCrudConfig.builder()
                .intentPrefix("PURCHASE_INVOICE")
                .entityClass(com.erp.purchase.entity.PurchaseInvoice.class)
                .serviceClass(com.erp.purchase.service.PurchaseInvoiceService.class)
                .serviceBeanName("purchaseInvoiceService")
                .primaryKeyField("invoiceId")
                .primaryKeyColumn("invoice_id")
                .searchFields(List.of("invoiceNo", "supplierName"))
                .displayFields(List.of("invoiceNo", "supplierName", "amount", "status"))
                .nameField("invoiceNo")
                .codeField("invoiceNo")
                .isMasterData(false)
                // 条件筛选白名单（对应采购发票列表页搜索条件；2026-09-28 新增）
                // 注：未登记金额字段 —— 实体金额属性名为 amount，其映射列为 total_amount，
                //     而筛选拼列走属性名蛇形化(amount→amount)，与真实列不一致会报错，故不登记。
                .filterFields(List.of("status", "invoiceNo", "invoiceDate", "supplierId", "invoiceType", "closeStatus", "isRedInvoice", "isMatch", "taxAmount"))
                .build());

        configMap.put("QUERY_PURCHASE_CONTRACT", EntityCrudConfig.builder()
                .intentPrefix("PURCHASE_CONTRACT")
                .entityClass(com.erp.purchase.entity.PurchaseContract.class)
                .serviceClass(com.erp.purchase.service.PurchaseContractService.class)
                .serviceBeanName("purchaseContractService")
                .primaryKeyField("contractId")
                .primaryKeyColumn("contract_id")
                .searchFields(List.of("contractNo", "supplierShortName"))
                .displayFields(List.of("contractNo", "supplierName", "totalAmount", "status"))
                .nameField("contractNo")
                .codeField("contractNo")
                .isMasterData(false)
                // 条件筛选白名单（对应采购合同列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "contractNo", "contractDate", "deliveryDate", "closeStatus", "supplierId", "departmentId", "buyerId", "totalAmount"))
                .build());
    }

    private void registerInventory() {
        configMap.put("QUERY_INVENTORY_STOCK", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_STOCK")
                .entityClass(com.erp.inventory.entity.Inventory.class)
                .serviceClass(com.erp.inventory.service.InventoryService.class)
                .serviceBeanName("inventoryService")
                .primaryKeyField("inventoryId")
                .primaryKeyColumn("inventory_id")
                // 2026-09-29 修复：原 searchFields 为空 → AI 传的任何关键词都不生效 → 查库存必然返回全量列表
                // 2026-09-29 修正：规格列在实体上的属性名是 model（真实列 spec），此处必须写属性名，
                //            写列名会让开机自检报"字段在实体上不存在"（列名解析会走 @TableField 映射）
                .searchFields(List.of("materialCode", "materialName", "model", "batchNo"))
                .displayFields(List.of("materialCode", "materialName", "stockName", "quantity"))
                // 库存不是时序数据：按物料编码排序（原"最新在前"在 created_at 多为空时顺序看着乱）
                .orderByColumns(List.of("material_code"))
                .nameField("inventoryId")
                .codeField("inventoryId")
                .isMasterData(false)
                .build());

        configMap.put("QUERY_INVENTORY_WAREHOUSE", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_WAREHOUSE")
                .entityClass(com.erp.basedata.entity.Warehouse.class)
                .serviceClass(com.erp.inventory.service.WarehouseService.class)
                .serviceBeanName("warehouseServiceImpl")
                .primaryKeyField("stockId")
                .primaryKeyColumn("stock_id")
                .searchFields(List.of("stockName", "stockCode"))
                .displayFields(List.of("stockCode", "stockName", "status"))
                .nameField("stockName")
                .codeField("stockCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_INVENTORY_LOCATION", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_LOCATION")
                .entityClass(com.erp.inventory.entity.Location.class)
                .serviceClass(com.erp.inventory.service.LocationService.class)
                .serviceBeanName("locationServiceImpl")
                .primaryKeyField("binId")
                .primaryKeyColumn("bin_id")
                .searchFields(List.of("binName", "binCode"))
                .displayFields(List.of("binCode", "binName", "status"))
                .nameField("binName")
                .codeField("binCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_INVENTORY_TRANSFER", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_TRANSFER")
                .entityClass(com.erp.inventory.entity.StockTransfer.class)
                .serviceClass(com.erp.inventory.service.StockTransferService.class)
                .serviceBeanName("stockTransferService")
                .primaryKeyField("transferId")
                .primaryKeyColumn("transfer_id")
                .searchFields(List.of("transferNo"))
                .displayFields(List.of("transferNo", "fromStockName", "toStockName", "status"))
                .nameField("transferNo")
                .codeField("transferNo")
                .isMasterData(false)
                // 条件筛选白名单（对应库存调拨单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "transferNo", "transferDate", "closeStatus", "fromStockId", "toStockId", "handlerId", "transferType", "totalAmount"))
                .build());

        configMap.put("QUERY_INVENTORY_CHECK", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_CHECK")
                .entityClass(com.erp.inventory.entity.StockCheck.class)
                .serviceClass(com.erp.inventory.service.StockCheckService.class)
                .serviceBeanName("stockCheckService")
                .primaryKeyField("checkId")
                .primaryKeyColumn("check_id")
                .searchFields(List.of("checkNo"))
                .displayFields(List.of("checkNo", "warehouseName", "status"))
                .nameField("checkNo")
                .codeField("checkNo")
                .isMasterData(false)
                // 条件筛选白名单（对应库存盘点单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "checkNo", "checkDate", "closeStatus", "warehouseId", "handlerId", "stockType"))
                .build());

        configMap.put("QUERY_INVENTORY_REQUISITION", EntityCrudConfig.builder()
                .intentPrefix("INVENTORY_REQUISITION")
                .entityClass(com.erp.inventory.entity.MaterialRequisition.class)
                .serviceClass(com.erp.inventory.service.MaterialRequisitionService.class)
                .serviceBeanName("materialRequisitionService")
                .primaryKeyField("requisitionId")
                .primaryKeyColumn("requisition_id")
                .searchFields(List.of("requisitionNo"))
                .displayFields(List.of("requisitionNo", "employeeName", "status"))
                .nameField("requisitionNo")
                .codeField("requisitionNo")
                .isMasterData(false)
                // 条件筛选白名单（对应生产领料单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "requisitionNo", "billType", "requisitionDate", "closeStatus", "departmentId", "employeeId", "warehouseId"))
                .build());
    }

    private void registerProduction() {
        // ===== 2026-11-22 二期补登：生产退料单 / 生产补料单（领料单族变体，同一实体按「单据类型」固定过滤） =====
        // 库里 bill_type 存英文码：PICK=领料 / RETURN=退料 / SUPPLEMENT=补料（2026-11-22 连库实锤）
        configMap.put("QUERY_PRODUCTION_RETURN", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_RETURN")
                .entityClass(com.erp.inventory.entity.MaterialRequisition.class)
                .serviceClass(com.erp.inventory.service.MaterialRequisitionService.class)
                .serviceBeanName("materialRequisitionService")
                .primaryKeyField("requisitionId")
                .primaryKeyColumn("requisition_id")
                .searchFields(List.of("requisitionNo"))
                .displayFields(List.of("requisitionNo", "status", "requisitionDate", "totalAmount"))
                .nameField("requisitionNo")
                .codeField("requisitionNo")
                .isMasterData(false)
                // 固定过滤：只看退料（bill_type=RETURN），生产退料单页面的口径
                .aiFilterColumn("bill_type")
                .aiFilterValue("RETURN")
                .aiFilterOperator("eq")
                .filterFields(List.of("status", "approvalStatus", "requisitionNo", "requisitionDate", "closeStatus", "departmentId", "employeeId", "warehouseId"))
                .build());

        configMap.put("QUERY_PRODUCTION_SUPPLEMENT", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_SUPPLEMENT")
                .entityClass(com.erp.inventory.entity.MaterialRequisition.class)
                .serviceClass(com.erp.inventory.service.MaterialRequisitionService.class)
                .serviceBeanName("materialRequisitionService")
                .primaryKeyField("requisitionId")
                .primaryKeyColumn("requisition_id")
                .searchFields(List.of("requisitionNo"))
                .displayFields(List.of("requisitionNo", "status", "requisitionDate", "totalAmount"))
                .nameField("requisitionNo")
                .codeField("requisitionNo")
                .isMasterData(false)
                // 固定过滤：只看补料（bill_type=SUPPLEMENT），生产补料单页面的口径
                .aiFilterColumn("bill_type")
                .aiFilterValue("SUPPLEMENT")
                .aiFilterOperator("eq")
                .filterFields(List.of("status", "approvalStatus", "requisitionNo", "requisitionDate", "closeStatus", "departmentId", "employeeId", "warehouseId"))
                .build());

        configMap.put("QUERY_PRODUCTION_WORKORDER", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_WORKORDER")
                .entityClass(com.erp.production.entity.WorkOrder.class)
                .serviceClass(com.erp.production.service.WorkOrderService.class)
                .serviceBeanName("workOrderService")
                .primaryKeyField("orderId")
                .primaryKeyColumn("order_id")
                .searchFields(List.of("orderNo", "materialName"))
                .displayFields(List.of("orderNo", "materialName", "quantity", "status"))
                .nameField("orderNo")
                .codeField("orderNo")
                .isMasterData(false)
                // 条件筛选白名单（对应生产工单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "orderNo", "orderDate", "closeStatus", "materialId", "warehouseId", "departmentId", "workCenterId", "planStartDate", "planEndDate"))
                .build());

        configMap.put("QUERY_PRODUCTION_BOM", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_BOM")
                .entityClass(com.erp.masterdata.entity.Bom.class)
                .serviceClass(com.erp.masterdata.service.BomService.class)
                .serviceBeanName("bomService")
                .primaryKeyField("bomId")
                .primaryKeyColumn("bom_id")
                .searchFields(List.of("bomCode", "bomName", "materialName", "materialCode"))
                .displayFields(List.of("bomCode", "bomName", "materialName", "status"))
                // BOM 名称（bom_name）历史上创建时不赋值，按名称回查会永远查不到；
                // 面向用户的主名称统一取母件名称（materialName），与 BOM 管理列表显示口径一致。
                .nameField("materialName")
                .codeField("bomCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_PRODUCTION_ROUTING", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_ROUTING")
                .entityClass(com.erp.production.entity.Routing.class)
                .serviceClass(com.erp.production.service.RoutingService.class)
                .serviceBeanName("routingService")
                .primaryKeyField("routingId")
                .primaryKeyColumn("routing_id")
                .searchFields(List.of("routingCode", "routingName"))
                .displayFields(List.of("routingCode", "routingName", "status"))
                .nameField("routingName")
                .codeField("routingCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_PRODUCTION_RECEIPT", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_RECEIPT")
                .entityClass(com.erp.production.entity.ProductionReceipt.class)
                .serviceClass(com.erp.production.service.ProductionReceiptService.class)
                .serviceBeanName("productionReceiptService")
                .primaryKeyField("receiptId")
                .primaryKeyColumn("receipt_id")
                .searchFields(List.of("receiptNo"))
                .displayFields(List.of("receiptNo", "productionOrderNo", "totalQuantity", "status"))
                .nameField("receiptNo")
                .codeField("receiptNo")
                .isMasterData(false)
                // 条件筛选白名单（对应生产入库单列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "receiptNo", "receiptDate", "closeStatus", "warehouseId", "productionOrderId", "departmentId"))
                .build());

        configMap.put("QUERY_PRODUCTION_WORKCENTER", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_WORKCENTER")
                .entityClass(com.erp.production.entity.WorkCenter.class)
                .serviceClass(com.erp.production.service.WorkCenterService.class)
                .serviceBeanName("workCenterService")
                .primaryKeyField("workCenterId")
                .primaryKeyColumn("work_center_id")
                .searchFields(List.of("workCenterName", "workCenterCode"))
                .displayFields(List.of("workCenterCode", "workCenterName", "status"))
                .nameField("workCenterName")
                .codeField("workCenterCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_PRODUCTION_WORKSTATION", EntityCrudConfig.builder()
                .intentPrefix("PRODUCTION_WORKSTATION")
                .entityClass(com.erp.production.entity.Workstation.class)
                .serviceClass(com.erp.production.service.WorkstationService.class)
                .serviceBeanName("workstationService")
                .primaryKeyField("workstationId")
                .primaryKeyColumn("workstation_id")
                .searchFields(List.of("workstationName", "workstationCode"))
                .displayFields(List.of("workstationCode", "workstationName", "status"))
                .nameField("workstationName")
                .codeField("workstationCode")
                .isMasterData(true)
                .build());
    }

    private void registerFinance() {
        configMap.put("QUERY_FINANCE_RECEIVABLE", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_RECEIVABLE")
                .entityClass(com.erp.finance.entity.AccountsReceivable.class)
                .serviceClass(com.erp.finance.service.FinanceService.class)
                .serviceBeanName("financeService")
                .primaryKeyField("arId")
                .primaryKeyColumn("ar_id")
                .searchFields(List.of("customerName"))
                .displayFields(List.of("arNo", "customerName", "totalAmount", "status"))
                .nameField("arNo")
                .codeField("arNo")
                .isMasterData(false)
                .build());

        configMap.put("QUERY_FINANCE_PAYABLE", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_PAYABLE")
                .entityClass(com.erp.finance.entity.AccountsPayable.class)
                .serviceClass(com.erp.finance.service.FinanceService.class)
                .serviceBeanName("financeService")
                .primaryKeyField("apId")
                .primaryKeyColumn("ap_id")
                .searchFields(List.of("supplierName"))
                .displayFields(List.of("apNo", "supplierName", "totalAmount", "status"))
                .nameField("apNo")
                .codeField("apNo")
                .isMasterData(false)
                .build());

        configMap.put("QUERY_FINANCE_EXPENSE", EntityCrudConfig.builder()
                .intentPrefix("FINANCE_EXPENSE")
                .entityClass(com.erp.finance.entity.OperationExpense.class)
                .serviceClass(com.erp.finance.service.OperationExpenseService.class)
                .serviceBeanName("operationExpenseService")
                .primaryKeyField("expenseId")
                .primaryKeyColumn("expense_id")
                .searchFields(List.of("expenseNo", "expenseType"))
                .displayFields(List.of("expenseNo", "expenseType", "amount", "status"))
                .nameField("expenseNo")
                .codeField("expenseNo")
                .isMasterData(false)
                // 条件筛选白名单（对应费用分摊列表页搜索条件；2026-09-28 新增）
                .filterFields(List.of("status", "approvalStatus", "expenseNo", "expenseType", "expenseDate", "closeStatus", "departmentId", "applicantId", "amount"))
                .build());
    }

    private void registerSystem() {
        configMap.put("QUERY_SYSTEM_USER", EntityCrudConfig.builder()
                .intentPrefix("SYSTEM_USER")
                .entityClass(com.erp.system.entity.SysUser.class)
                .serviceClass(com.erp.system.service.SysUserService.class)
                .serviceBeanName("sysUserService")
                .primaryKeyField("userId")
                .primaryKeyColumn("user_id")
                .searchFields(List.of("username", "realName"))
                .displayFields(List.of("username", "realName", "status"))
                .nameField("realName")
                .codeField("username")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_SYSTEM_ROLE", EntityCrudConfig.builder()
                .intentPrefix("SYSTEM_ROLE")
                .entityClass(com.erp.system.entity.SysRole.class)
                .serviceClass(com.erp.system.service.SysRoleService.class)
                .serviceBeanName("sysRoleService")
                .primaryKeyField("roleId")
                .primaryKeyColumn("role_id")
                .searchFields(List.of("roleName", "roleCode"))
                .displayFields(List.of("roleCode", "roleName", "status"))
                .nameField("roleName")
                .codeField("roleCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_SYSTEM_MENU", EntityCrudConfig.builder()
                .intentPrefix("SYSTEM_MENU")
                .entityClass(com.erp.system.entity.SysMenu.class)
                .serviceClass(com.erp.system.service.SysMenuService.class)
                .serviceBeanName("sysMenuService")
                .primaryKeyField("menuId")
                .primaryKeyColumn("menu_id")
                .searchFields(List.of("menuName", "menuCode"))
                .displayFields(List.of("menuCode", "menuName", "status"))
                .nameField("menuName")
                .codeField("menuCode")
                .isMasterData(true)
                .build());

        configMap.put("QUERY_SYSTEM_DICT", EntityCrudConfig.builder()
                .intentPrefix("SYSTEM_DICT")
                .entityClass(com.erp.system.entity.SysDictType.class)
                .serviceClass(com.erp.system.service.SysDictTypeService.class)
                .serviceBeanName("sysDictTypeService")
                .primaryKeyField("dictTypeId")
                .primaryKeyColumn("dict_type_id")
                .searchFields(List.of("dictTypeName", "dictTypeCode"))
                .displayFields(List.of("dictTypeCode", "dictTypeName", "status"))
                .nameField("dictTypeName")
                .codeField("dictTypeCode")
                .isMasterData(true)
                .build());
    }

    public boolean supports(String intent) {
        String baseIntent = normalizeIntent(intent);
        return configMap.containsKey(baseIntent);
    }

    private String normalizeIntent(String intent) {
        if (intent == null) return null;
        if (intent.startsWith("QUERY_") || intent.startsWith("CREATE_") ||
            intent.startsWith("UPDATE_") || intent.startsWith("DELETE_")) {
            return intent;
        }
        return "QUERY_" + intent;
    }

    private EntityCrudConfig getConfig(String intent) {
        String baseIntent = intent.startsWith("QUERY_") ? intent :
                           intent.startsWith("CREATE_") ? intent.replace("CREATE_", "QUERY_") :
                           intent.startsWith("UPDATE_") ? intent.replace("UPDATE_", "QUERY_") :
                           intent.startsWith("DELETE_") ? intent.replace("DELETE_", "QUERY_") : intent;
        return configMap.get(baseIntent);
    }

    @SuppressWarnings("rawtypes")
    private IService getService(EntityCrudConfig config) {
        try {
            return (IService) applicationContext.getBean(config.getServiceClass());
        } catch (Exception e) {
            try {
                return (IService) applicationContext.getBean(config.getServiceBeanName());
            } catch (Exception e2) {
                throw new RuntimeException("未找到 Service Bean: " + config.getServiceClass().getName() 
                        + " / " + config.getServiceBeanName(), e2);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Map<String, Object> query(String intent, Map<String, Object> params) {
        return query(intent, params, null);
    }

    /**
     * 通用查询（带数据权限上下文）。
     * 数据权限规则（与系统界面行为对齐）：
     * - 管理员/主管：看全部
     * - 销售员：销售订单按 saler_id 过滤（同销售订单列表页）
     * - 普通员工：销售/CRM模块业务单据按 created_by 过滤
     * - 基础资料/系统数据：共享数据，不过滤
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Map<String, Object> query(String intent, Map<String, Object> params, com.erp.ai.dto.AiDataScope scope) {
        EntityCrudConfig config = getConfig(intent);
        if (config == null) {
            return Map.of("success", false, "message", "不支持的意图: " + intent);
        }

        try {
            IService service = getService(config);

            // 支持分页参数
            int page = 1;
            int size = 100; // 默认返回100条，而非20条
            if (params.get("page") != null) {
                try { page = Integer.parseInt(params.get("page").toString()); } catch (Exception ignored) {}
            }
            if (params.get("size") != null) {
                try { size = Integer.parseInt(params.get("size").toString()); } catch (Exception ignored) {}
            }
            // 限制最大返回500条，防止性能问题
            if (size > 500) size = 500;
            int offset = (page - 1) * size;

            String keyword = (String) params.get("keyword");
            if (keyword == null) keyword = (String) params.get("name");
            if (keyword == null) keyword = (String) params.get("code");
            final String finalKeyword = keyword;

            // 条件筛选（filters=[{field,op,value}]）：字段走该实体登记的 filterFields 白名单，op 走白名单
            // 例：[{field:status,op:eq,value:草稿}]、[{field:orderDate,op:between,value:["2026-07-01","2026-07-31"]}]
            final List<Map<String, Object>> filters = parseFilters(params.get("filters"));

            // 构建查询条件（用于count和list共用条件）
            java.util.function.Consumer<QueryWrapper<Object>> applyConditions = w -> {
                // 数据权限过滤（服务端强制，不信任AI传入的任何过滤参数）
                applyDataScope(w, intent, scope);

                if (config.getAiFilterColumn() != null && !config.getAiFilterColumn().isBlank()
                        && config.getAiFilterValue() != null && config.getAiFilterOperator() != null) {
                    String op = config.getAiFilterOperator().toLowerCase();
                    switch (op) {
                        case "eq" -> w.eq(config.getAiFilterColumn(), config.getAiFilterValue());
                        case "ne" -> w.ne(config.getAiFilterColumn(), config.getAiFilterValue());
                        case "gt" -> w.gt(config.getAiFilterColumn(), config.getAiFilterValue());
                        case "ge" -> w.ge(config.getAiFilterColumn(), config.getAiFilterValue());
                        case "lt" -> w.lt(config.getAiFilterColumn(), config.getAiFilterValue());
                        case "le" -> w.le(config.getAiFilterColumn(), config.getAiFilterValue());
                        case "isnull" -> w.isNull(config.getAiFilterColumn());
                        case "isnotnull" -> w.isNotNull(config.getAiFilterColumn());
                        default -> {}
                    }
                }

                if (finalKeyword != null && !finalKeyword.isBlank() && config.getSearchFields() != null && !config.getSearchFields().isEmpty()) {
                    // 关键词多字段 OR 必须整组包在 and(...) 内：MyBatis-Plus 平铺 or() 不给分组补括号，
                    // 会让"命中关键词"的行绕过其后的参数条件与条件筛选（项目铁律，2026-09-29 一并收口）
                    w.and(kwGroup -> {
                        boolean first = true;
                        for (String field : config.getSearchFields()) {
                            String column = columnOf(config.getEntityClass(), field);
                            if (first) {
                                kwGroup.like(column, finalKeyword);
                                first = false;
                            } else {
                                kwGroup.or().like(column, finalKeyword);
                            }
                        }
                        // 库存表的物料编码/名称是冗余列，可能过期（实测 inventory_current.material_code 为空、
                        // 但 material_id 有效 → "按编码查库存"查不到）。故库存查询再按物料档案解析一次主键（2026-09-29）
                        if (config.getEntityClass() == com.erp.inventory.entity.Inventory.class) {
                            List<Long> materialIds = resolveMaterialIdsByKeyword(finalKeyword);
                            if (!materialIds.isEmpty()) {
                                kwGroup.or().in("material_id", materialIds);
                            }
                        }
                    });
                }

                params.forEach((key, value) -> {
                    if (!"keyword".equals(key) && !"name".equals(key) && !"code".equals(key)
                        && !"page".equals(key) && !"size".equals(key) && !"filters".equals(key) && value != null) {
                        String actualField = resolveFieldName(config.getEntityClass(), key);
                        if (actualField != null) {
                            String column = columnOf(config.getEntityClass(), actualField);
                            Object converted = convertFieldValue(config.getEntityClass(), actualField, value);
                            if (converted != null) {
                                w.eq(column, converted);
                            }
                        }
                    }
                });

                // 条件筛选：字段必须在登记的白名单内 + 实体上真实存在；操作符走白名单（杜绝乱传列名/静默忽略）
                for (Map<String, Object> f : filters) {
                    String field = String.valueOf(f.get("field"));
                    String op = f.get("op") == null ? "eq" : String.valueOf(f.get("op")).toLowerCase();
                    Object val = f.get("value");
                    if (config.getFilterFields() == null || config.getFilterFields().isEmpty()
                            || !config.getFilterFields().contains(field)) {
                        throw new IllegalArgumentException("不支持按「" + field + "」筛选；该单据可用条件："
                                + (config.getFilterFields() == null || config.getFilterFields().isEmpty() ? "无" : config.getFilterFields()));
                    }
                    if (!FILTER_OPS.contains(op)) {
                        throw new IllegalArgumentException("不支持的筛选方式「" + op + "」；可用：" + FILTER_OPS);
                    }
                    String actualField = resolveFieldName(config.getEntityClass(), field);
                    if (actualField == null) {
                        throw new IllegalArgumentException("字段不存在：" + field);
                    }
                    String column = columnOf(config.getEntityClass(), actualField);
                    if ("between".equals(op)) {
                        List<?> pair = toPair(val);
                        if (pair.size() != 2) {
                            throw new IllegalArgumentException("between 需要两个值，如 [\"2026-07-01\",\"2026-07-31\"]");
                        }
                        w.between(column,
                                convertFieldValue(config.getEntityClass(), actualField, pair.get(0)),
                                convertFieldValue(config.getEntityClass(), actualField, pair.get(1)));
                    } else if ("like".equals(op)) {
                        w.like(column, val);
                    } else {
                        Object cv = convertFieldValue(config.getEntityClass(), actualField, val);
                        switch (op) {
                            case "eq" -> w.eq(column, cv);
                            case "ne" -> w.ne(column, cv);
                            case "gt" -> w.gt(column, cv);
                            case "ge" -> w.ge(column, cv);
                            case "lt" -> w.lt(column, cv);
                            case "le" -> w.le(column, cv);
                            default -> { }
                        }
                    }
                }
            };

            // 先查总数（不带ORDER BY和LIMIT）
            QueryWrapper<Object> countWrapper = new QueryWrapper<>();
            applyConditions.accept(countWrapper);
            long totalCount = service.count(countWrapper);

            // 分页查询（带ORDER BY和LIMIT）
            QueryWrapper<Object> listWrapper = new QueryWrapper<>();
            applyConditions.accept(listWrapper);
            // 登记了"业务默认排序"的实体用它（如库存按物料编码，2026-09-29 新增），否则沿用全系统"最新在前"
            if (config.getOrderByColumns() != null && !config.getOrderByColumns().isEmpty()) {
                for (String col : config.getOrderByColumns()) {
                    listWrapper.orderByAsc(col);
                }
            } else {
                SortHelper.orderByNewest(listWrapper);
            }
            listWrapper.last("LIMIT " + size + " OFFSET " + offset);
            List list = service.list(listWrapper);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Object entity : list) {
                Map<String, Object> item = new LinkedHashMap<>();
                // 始终包含主键字段，便于前端点击跳转详情
                Object pkValue = ReflectUtil.getFieldValue(entity, config.getPrimaryKeyField());
                if (pkValue != null) {
                    item.put(config.getPrimaryKeyField(), pkValue);
                }
                if (config.getDisplayFields() != null) {
                    for (String field : config.getDisplayFields()) {
                        Object val = ReflectUtil.getFieldValue(entity, field);
                        item.put(field, val != null ? val : "");
                    }
                } else {
                    item.putAll(BeanUtil.beanToMap(entity));
                }
                result.add(item);
            }

            // 与界面口径对齐：按实体外键 ID 动态补全显示名（界面列表与详情都这么做，
            // 见 SaleOrderServiceImpl.fillDisplayFields()；此处不补就会出现"界面有客户名、小助手显示空"）
            // 【铁律】外键 ID 必须从【实体】上取 —— 结果 Map 里只有 displayFields 列出的键，
            //         通常并不包含 customerId/supplierId（首版修复就是栽在这里，取不到 ID 直接 return）
            fillForeignDisplayNames(result, list);

            // 派生列（计算列，2026-11-22 立项一期）：超期天数/未收款金额等"算给你看"的口径，
            // 按意图登记自动追加；计算失败只跳过该列，不影响查询结果
            AiDerivedColumns.apply(intent, list, result);

            // 字段权限：页面键由实体 @TableName 自动推导（与列表/导出/打印同一份规则）。
            // 小助手跑在线程池，按 scope 显式解析；否则回退请求上下文。
            String pageKey = billPermissionPathRegistry.fromEntityClass(config.getEntityClass());
            Set<String> hiddenKeys = (scope != null && scope.getUserId() != null)
                    ? fieldPermissionOutputFilter.hiddenKeysForUser(scope.getUserId(), scope.isAdmin(), pageKey)
                    : fieldPermissionOutputFilter.hiddenKeys(pageKey);
            fieldPermissionOutputFilter.filterRecords(hiddenKeys, result);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("data", result);
            resp.put("total", totalCount);
            resp.put("returned", list.size());
            resp.put("page", page);
            resp.put("size", size);
            resp.put("hasMore", offset + list.size() < totalCount);
            resp.put("entityName", config.getEntityClass().getSimpleName());
            resp.put("primaryKeyField", config.getPrimaryKeyField());
            resp.put("codeField", config.getCodeField() != null ? config.getCodeField() : "");
            // 字段中文名映射（自动注册机制生成）：前端列头优先取此映射，兜底走本地字典
            resp.put("labels", config.getLabels() != null
                    ? fieldPermissionOutputFilter.filterLabels(hiddenKeys, config.getLabels()) : Map.of());
            return resp;
        } catch (Exception e) {
            log.error("[AI CRUD查询] 失败 intent={}", intent, e);
            return Map.of("success", false, "message", "查询失败: " + e.getMessage());
        }
    }

    /**
     * 补全外键显示名（与界面口径一致）。
     *
     * <p>背景（2026-09-14 两轮修复）：
     * <ol>
     *   <li>界面的列表与详情都会按外键<b>动态补全</b>显示名
     *       （如 {@code SaleOrderServiceImpl.fillDisplayFields()} 按 customerId 取 customerName），
     *       而本类通用查询是"直接反射实体字段"。而销售 6 单与采购合同的
     *       {@code customerName}/{@code supplierName} 是 {@code @TableField(exist = false)}
     *       的<b>非数据库字段</b>，反射取值恒为 null → 出现「界面有、小助手空」的口径不一致。</li>
     *   <li><b>首版修复无效的原因（务必记住）</b>：原实现从<b>结果 Map</b> 里取 {@code customerId}，
     *       但 {@code displayFields} 里根本没有这一列（主键也是 orderId），于是取不到 ID 直接 return。
     *       现改为<b>从实体对象反射取外键 ID</b>。</li>
     * </ol>
     *
     * <p>规则：仅当"名称列为空 且 实体上该外键 ID 不为空"时才查库回填；IN 批量查询避免 N+1；
     * 查库失败只告警，<b>绝不影响查询结果本身</b>。
     *
     * @param rows     结果行（与 entities 一一对应、同序）
     * @param entities 原始实体列表（外键 ID 从这里取）
     */
    private void fillForeignDisplayNames(List<Map<String, Object>> rows, List<?> entities) {
        if (rows == null || rows.isEmpty() || entities == null || entities.size() != rows.size()) return;
        // 客户 / 供应商（销售 6 单、采购合同、CRM、应收应付）
        fillNameFrom(rows, entities, "customerId", "customerName", "customer", "customer_id", "customer_name");
        fillNameFrom(rows, entities, "supplierId", "supplierName", "supplier", "supplier_id", "supplier_name");
        // 物料（库存台账 / 库存调拨）
        fillNameFrom(rows, entities, "materialId", "materialName", "material", "material_id", "material_name");
        // 仓库：stockId(库存台账) / fromStockId+toStockId(调拨) / warehouseId(盘点) 都指向 warehouse.stock_id
        fillNameFrom(rows, entities, "stockId", "stockName", "warehouse", "stock_id", "stock_name");
        fillNameFrom(rows, entities, "fromStockId", "fromStockName", "warehouse", "stock_id", "stock_name");
        fillNameFrom(rows, entities, "toStockId", "toStockName", "warehouse", "stock_id", "stock_name");
        fillNameFrom(rows, entities, "warehouseId", "warehouseName", "warehouse", "stock_id", "stock_name");
        // 员工（领料人）
        fillNameFrom(rows, entities, "employeeId", "employeeName", "employee", "employee_id", "employee_name");
        // 物料分类（编码 + 名称一次补齐）：categoryCode/categoryName 是 Material 的非持久化字段，
        // 只靠 displayFields 反射恒为空；小楷建物料/BOM 前必须看到系统里真实的分类编码与名称。
        fillMaterialCategoryFields(rows, entities);
        // 物料基本单位名称（baseUnitId → unit_of_measure.unit_name）
        fillNameFrom(rows, entities, "baseUnitId", "baseUnitName",
                "unit_of_measure", "unit_id", "unit_name");
    }

    /**
     * 物料分类编码 + 名称批量回填（同一个 categoryId 只查一次，IN 批量查询）。
     * <p>只在该结果里确实包含 categoryCode/categoryName 列时生效，不影响其它实体查询。
     */
    private void fillMaterialCategoryFields(List<Map<String, Object>> rows, List<?> entities) {
        if (rows == null || rows.isEmpty() || entities == null || entities.size() != rows.size()) return;
        boolean needCode = rows.get(0).containsKey("categoryCode");
        boolean needName = rows.get(0).containsKey("categoryName");
        if (!needCode && !needName) return;
        try {
            Set<Long> ids = new LinkedHashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                boolean codeMissing = needCode && isBlank(rows.get(i).get("categoryCode"));
                boolean nameMissing = needName && isBlank(rows.get(i).get("categoryName"));
                if (!codeMissing && !nameMissing) continue;
                Object iv = ReflectUtil.getFieldValue(entities.get(i), "categoryId");
                if (iv == null) continue;
                try { ids.add(Long.parseLong(String.valueOf(iv).trim())); } catch (Exception ignore) { }
            }
            if (ids.isEmpty()) return;

            String placeholders = ids.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
            Map<Long, String[]> map = new HashMap<>();
            jdbcTemplate.query("SELECT category_id, category_code, category_name FROM material_category"
                            + " WHERE category_id IN (" + placeholders + ")",
                    rs -> { map.put(rs.getLong(1), new String[]{rs.getString(2), rs.getString(3)}); },
                    ids.toArray());

            for (int i = 0; i < rows.size(); i++) {
                Object iv = ReflectUtil.getFieldValue(entities.get(i), "categoryId");
                if (iv == null) continue;
                try {
                    String[] pair = map.get(Long.parseLong(String.valueOf(iv).trim()));
                    if (pair == null) continue;
                    if (needCode && isBlank(rows.get(i).get("categoryCode"))) {
                        rows.get(i).put("categoryCode", pair[0] == null ? "" : pair[0]);
                    }
                    if (needName && isBlank(rows.get(i).get("categoryName"))) {
                        rows.get(i).put("categoryName", pair[1] == null ? "" : pair[1]);
                    }
                } catch (Exception ignore) { }
            }
        } catch (Exception e) {
            log.warn("[AI CRUD查询] 补全物料分类编码/名称失败（不影响结果）: {}", e.getMessage());
        }
    }

    private boolean isBlank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    /**
     * 取单个实体的往来单位显示名（客户名 / 供应商名），供 AI 确认卡片、语音播报等
     * "对人话标识"场景复用（如「【销售订单】SO-20260914-0005（深圳远大智能科技有限公司）」）。
     *
     * <p>为什么要复用本类：单据实体上的 customerName/supplierName 是 {@code @TableField(exist = false)}
     * 的展示字段，反射恒为 null，必须按外键回查主档；而"哪个外键对应哪张主档表、哪个名称列"
     * 的映射本类已经维护（{@link #fillForeignDisplayNames}，单一适配点），
     * 在别处再写一套 SELECT 会形成两份口径 —— 历史踩坑（2026-09-14 客户名显示修复）就栽在这里。
     *
     * @return 客户名/供应商名；都取不到返回 null（调用方自行降级）
     */
    public String foreignDisplayName(Object entity) {
        if (entity == null) return null;
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerName", "");
            row.put("supplierName", "");
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row);
            List<Object> entities = new ArrayList<>();
            entities.add(entity);
            fillForeignDisplayNames(rows, entities);
            for (String key : new String[]{"customerName", "supplierName"}) {
                Object v = row.get(key);
                if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
            }
        } catch (Exception e) {
            log.warn("[AI CRUD查询] 取往来单位名失败（不影响主流程）: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 按<b>实体外键字段</b>回填显示名。
     *
     * @param rows     结果行
     * @param entities 实体列表（与 rows 同序）
     * @param idField  实体上的外键字段名（如 {@code customerId}）
     * @param nameKey  结果 Map 里的显示名键（如 {@code customerName}）
     * @param table    外键所在主档表（代码常量，非用户输入）
     * @param idCol    主档表主键列
     * @param nameCol  主档表名称列
     */
    private void fillNameFrom(List<Map<String, Object>> rows, List<?> entities,
                              String idField, String nameKey,
                              String table, String idCol, String nameCol) {
        try {
            // 该查询结果里若根本没返回这个名称列（displayFields 未包含），无需补全
            if (!rows.get(0).containsKey(nameKey)) return;

            Set<Long> ids = new LinkedHashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                Object nv = rows.get(i).get(nameKey);
                if (nv != null && !String.valueOf(nv).isBlank()) continue;
                Object iv = ReflectUtil.getFieldValue(entities.get(i), idField);
                if (iv == null) continue;
                try { ids.add(Long.parseLong(String.valueOf(iv).trim())); } catch (Exception ignore) { }
            }
            if (ids.isEmpty()) return;

            String placeholders = ids.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
            Map<Long, String> nameMap = new HashMap<>();
            jdbcTemplate.query("SELECT " + idCol + ", " + nameCol + " FROM " + table
                            + " WHERE " + idCol + " IN (" + placeholders + ")",
                    rs -> { nameMap.put(rs.getLong(1), rs.getString(2)); },
                    ids.toArray());
            if (nameMap.isEmpty()) return;

            int filled = 0;
            for (int i = 0; i < rows.size(); i++) {
                Object nv = rows.get(i).get(nameKey);
                if (nv != null && !String.valueOf(nv).isBlank()) continue;
                Object iv = ReflectUtil.getFieldValue(entities.get(i), idField);
                if (iv == null) continue;
                try {
                    String n = nameMap.get(Long.parseLong(String.valueOf(iv).trim()));
                    if (n != null && !n.isBlank()) { rows.get(i).put(nameKey, n); filled++; }
                } catch (Exception ignore) { }
            }
            if (filled > 0) log.debug("[AI CRUD查询] 已补全 {} 共 {} 行", nameKey, filled);
        } catch (Exception e) {
            log.warn("[AI CRUD查询] 补全 {} 失败（不影响结果）: {}", nameKey, e.getMessage());
        }
    }

    /**
     * 数据权限过滤（服务端强制）。
     * - scope为null（旧链路）或管理员/主管：不过滤
     * - 销售员查销售订单：按 saler_id 过滤（与销售订单列表页一致）
     * - 普通员工查销售/CRM业务单据：按 created_by 过滤
     * - 基础资料/系统数据：共享数据，不过滤
     */
    private void applyDataScope(QueryWrapper<Object> w, String intent, com.erp.ai.dto.AiDataScope scope) {
        if (scope == null || scope.isAdmin()) {
            return;
        }
        String upper = intent == null ? "" : intent.toUpperCase();
        if ("QUERY_SALE_ORDER".equals(upper) && scope.isSalesOnly() && scope.getEmployeeId() != null) {
            w.eq("saler_id", scope.getEmployeeId());
            return;
        }
        if (scope.isRegularEmployee()
                && (upper.startsWith("QUERY_SALE_") || upper.startsWith("QUERY_CRM_"))) {
            String username = scope.getUsername();
            if (username != null && !username.isBlank()) {
                w.eq("created_by", username);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Map<String, Object> create(String intent, Map<String, Object> params, Long userId) {
        EntityCrudConfig config = getConfig(intent);
        if (config == null) {
            return Map.of("success", false, "message", "不支持的意图: " + intent);
        }

        try {
            IService service = getService(config);
            Object entity = config.getEntityClass().getDeclaredConstructor().newInstance();

            Map<String, Object> entityParams = (Map<String, Object>) params.getOrDefault("data", params);
            // 明细两种写法都接受：data.items（旧写法）/ 最外层 items（带明细工具的参数声明）
            Object rawItems = entityParams.get("items");
            if (rawItems == null && params != entityParams) rawItems = params.get("items");
            // 工艺路线这类主档的明细字段叫 operations（工序行），与单据的 items 同口径处理
            Field itemsField = findField(config.getEntityClass(), "items");
            Field listField = itemsField;
            if (listField == null) {
                listField = findField(config.getEntityClass(), "operations");
                if (listField != null && rawItems == null) {
                    rawItems = entityParams.get("operations");
                    if (rawItems == null && params != entityParams) rawItems = params.get("operations");
                }
            }

            // 如实回传事实：模型传了但实体上没有的字段名，收集后写进创建结果，
            // 让模型看到"哪些没生效"并自行决定按正确字段名重发（不再静默丢弃，2026-11-19）
            List<String> unknownFields = new ArrayList<>();
            for (Map.Entry<String, Object> entry : entityParams.entrySet()) {
                String fieldName = entry.getKey();
                if ("items".equals(fieldName) || "operations".equals(fieldName)) continue;
                Object value = entry.getValue();
                try {
                    String resolved = resolveCreateFieldName(config.getEntityClass(), fieldName);
                    Field field = resolved == null ? null : findField(config.getEntityClass(), resolved);
                    if (field == null) {
                        log.warn("[AI CRUD创建] 字段无法落到该实体，已忽略 intent={} field={}", intent, fieldName);
                        unknownFields.add(value == null ? fieldName : fieldName + "=" + strOf(value));
                        continue;
                    }
                    field.setAccessible(true);
                    field.set(entity, convertValue(field.getType(), value));
                } catch (Exception ignored) {}
            }

            // 外键名称 → 主键：模型/用户常只给「客户=楷煜」「分类=五金件」「单位=个」这类名称。
            // 这里统一按"字段名去掉 Id + Name"去找同名字段并回查主档（客户/供应商/物料/分类/单位/仓库/员工/工作中心/工艺路线等），
            // 否则这些名称会被当成非库字段丢弃，建出来的单据/资料外键全空（2026-10-21 用户实测）。
            String fkError = resolveForeignKeys(entity, config.getEntityClass(), entityParams);
            if (fkError != null) {
                return Map.of("success", false, "message", fkError);
            }

            // 物料分类编码兜底：设置中心把「物料分类」自动编号关掉时，save() 会直接抛
            // "物料分类已关闭自动编号，请手动输入编码"。小楷按规则不允许自己编编码，
            // 于是整条"分类→物料→BOM"链条全崩（2026-10-28 用户实测）。
            // 口径：编码由**系统**生成（FL+3位序号，按库内实际占用避重），不是模型编的；
            // 用户在界面上手动建分类时行为完全不变。
            if ("QUERY_BASEDATA_MATERIALCATEGORY".equals(intent)) {
                Object codeVal = ReflectUtil.getFieldValue(entity, "categoryCode");
                if (codeVal == null || String.valueOf(codeVal).isBlank()) {
                    Field codeField = findField(config.getEntityClass(), "categoryCode");
                    String generated = generateMaterialCategoryCode();
                    if (codeField != null && generated != null) {
                        setFieldValue(entity, codeField, generated);
                        log.info("[AI CRUD创建] 物料分类自动编号已关闭，系统补编码 {} name={}",
                                generated, entityParams.get("categoryName"));
                    }
                }
            }
            // 币种：模型常给「人民币/美元」这类名称，必须解析成币种ID再落库；
            // 解析不到再补本位币 —— 否则银行账户等 currency_id 非空的表会直接抛数据库约束错误
            // （2026-10-21 实测：create_basedata_bankaccount 传 currencyName=人民币 → currency_id 为空 → 创建失败）
            String currencyError = fillCurrency(entity, config.getEntityClass(), entityParams);
            if (currencyError != null) {
                return Map.of("success", false, "message", currencyError);
            }

            List<Object> items = null;
            List<String> unknownItemFields = new ArrayList<>();
            if (listField != null) {
                String label = entityLabel(config);
                if (!(rawItems instanceof List<?> rawList) || rawList.isEmpty()) {
                    // 关键：没有明细就**不许保存空单、更不许回报成功**
                    return Map.of("success", false, "message",
                            "创建「" + label + "」至少要有一行明细（单据的物料行 / 工艺路线的工序行）。这次没有拿到明细，"
                                    + "所以没有保存空单；请把明细内容告诉我，我补上后再发起。");
                }
                Class<?> itemClass = listItemClass(listField);
                if (itemClass == null) {
                    return Map.of("success", false, "message",
                            "「" + label + "」的明细类型无法识别，暂不支持由小楷直接创建，请到对应菜单页面手工新建。");
                }
                items = new ArrayList<>();
                for (int i = 0; i < rawList.size(); i++) {
                    Object raw = rawList.get(i);
                    if (!(raw instanceof Map<?, ?>)) continue;
                    try {
                        items.add(buildItem(itemClass, (Map<String, Object>) raw, i + 1, config.getEntityClass(),
                                unknownItemFields));
                    } catch (IllegalArgumentException e) {
                        return Map.of("success", false, "message", e.getMessage());
                    }
                }
                if (items.isEmpty()) {
                    return Map.of("success", false, "message",
                            "「" + label + "」的明细里没有有效内容，未保存；请补充后重试。");
                }
                listField.setAccessible(true);
                listField.set(entity, items);
            }

            try {
                Field createdBy = findField(config.getEntityClass(), "createdBy");
                if (createdBy != null) {
                    createdBy.setAccessible(true);
                    createdBy.set(entity, userId != null ? userId.toString() : "AI");
                }
            } catch (Exception ignored) {}

            // 物料编码前置取号：分类编码规则优先；规则未配置/取号失败时回退通用自动流水。
            // 必须在进入保存事务之前完成 —— 此处不在任何事务内，try/catch 安全；
            // 事务内取号失败的异常会被外层吞掉，导致提交时报 UnexpectedRollbackException 英文噪声（2026-11-19 修复）。
            boolean genericCodeUsed = false;
            if (entity instanceof Material material
                    && (material.getMaterialCode() == null || material.getMaterialCode().isBlank())) {
                // 总开关必须最先校验：设置中心关闭"物料自动编号"后，规则/通用流水都不许再取号，
                // 否则开关成了摆设（2026-11-19 状态C实测：规则路径绕过开关照样编号）
                if (!serialNoService.isAutoGenerate("MD_MATERIAL")) {
                    return Map.of("success", false, "message", "物料已关闭自动编号，请手动输入物料编码");
                }
                String generated = null;
                try {
                    generated = materialCodeRuleService.generateCode(material.getCategoryId());
                } catch (Exception e) {
                    log.info("[AI CRUD创建] 物料分类编码规则取号失败，回退通用自动流水: {}", e.getMessage());
                }
                if (generated == null || generated.isBlank()) {
                    // 三参重载：以业务表实况补最小空洞（与物料保存服务的守卫口径一致，参数照抄原回退调用）
                    MaterialService materialService = applicationContext.getBean(MaterialService.class);
                    generated = serialNoService.generateSerialNo("MD_MATERIAL", prefix -> {
                        if (prefix == null || prefix.isBlank()) return null;
                        Material last = materialService.getOne(new QueryWrapper<Material>().likeRight("material_code", prefix)
                                .orderByDesc("material_code").last("LIMIT 1"));
                        if (last == null || last.getMaterialCode() == null) return null;
                        try {
                            return prefix + (Long.parseLong(last.getMaterialCode().substring(prefix.length())) + 1);
                        } catch (Exception e) {
                            return null;
                        }
                    }, materialService::listCodesByPrefix);
                    genericCodeUsed = true;
                }
                material.setMaterialCode(generated);
            }

            // 带明细的单据必须走 Service 的「表头 + 明细」保存入口：
            // 只 service.save(表头) 不会写明细、不会生成单号、也不会重算金额（正是"空单"的根因）
            Method saveWithItems = items != null ? findSaveWithItems(service, config.getEntityClass()) : null;
            if (items != null && saveWithItems == null) {
                return Map.of("success", false, "message",
                        "「" + entityLabel(config) + "」暂不支持由小楷直接创建（系统没有明细保存入口），请到对应菜单页面手工新建。");
            }
            if (saveWithItems != null) {
                if (saveWithItems.getParameterCount() == 2) saveWithItems.invoke(service, entity, items);
                else saveWithItems.invoke(service, entity);
            } else {
                service.save(entity);
            }

            Object id = ReflectUtil.getFieldValue(entity, config.getPrimaryKeyField());
            Object code = config.getCodeField() != null ? ReflectUtil.getFieldValue(entity, config.getCodeField()) : id;
            Object name = config.getNameField() != null ? ReflectUtil.getFieldValue(entity, config.getNameField()) : "";

            String message = "创建成功";
            if (code != null && !String.valueOf(code).isBlank()) message += "：单号 " + code;
            if (items != null) message += "，共 " + items.size() + " 行明细";
            if (genericCodeUsed) message += "；该公司未配置物料编码规则，编码按通用自动流水生成";
            // 如实回传：模型传了但档案上没有的字段名，明确告诉它"哪些没生效"，
            // 由模型决定是否按正确字段名补改 —— 不许静默丢弃造成"看起来设上了"的假成功（2026-11-19）
            List<String> allUnknown = new ArrayList<>(unknownFields);
            allUnknown.addAll(unknownItemFields);
            if (!allUnknown.isEmpty()) {
                message += "；以下字段未能写入（档案没有这些字段名）：" + String.join("、", allUnknown)
                        + "；请核对该资料的实际字段名后，再决定是否补改。";
            }

            log.info("[AI CRUD创建] 成功 intent={} id={} name={} items={}", intent, id, name, items == null ? 0 : items.size());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id != null ? id : "");
            result.put("code", code != null ? code : "");
            result.put("name", name != null ? name : "");
            result.put("entityName", config.getEntityClass().getSimpleName());
            if (items != null) result.put("itemCount", items.size());
            result.put("message", message);
            return result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("[AI CRUD创建] 失败 intent={}", intent, cause);
            return Map.of("success", false, "message", "创建失败: " + cause.getMessage());
        } catch (Exception e) {
            log.error("[AI CRUD创建] 失败 intent={}", intent, e);
            return Map.of("success", false, "message", "创建失败: " + e.getMessage());
        }
    }

    /**
     * 物料分类编码兜底生成（仅小楷创建路径使用，界面手工新建行为不变）。
     * 规则：FL + 3 位序号，从 001 起挑第一个库里没被占用的；用完 999 个后回退时间戳后缀。
     */
    private String generateMaterialCategoryCode() {
        for (int i = 1; i <= 999; i++) {
            String code = "FL" + String.format("%03d", i);
            try {
                Long cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM material_category WHERE category_code = ?", Long.class, code);
                if (cnt == null || cnt == 0) return code;
            } catch (Exception e) {
                log.warn("[AI CRUD创建] 检查物料分类编码占用失败: {}", e.getMessage());
                return code;
            }
        }
        return "FL" + (System.currentTimeMillis() % 100000);
    }

    /**
     * 创建时的字段名解析：先精确匹配，再查别名表。
     * **不做包含式模糊匹配** —— "price" 会被错配到 priceLevelId，把单价写成价格等级。
     */
    private String resolveCreateFieldName(Class<?> entityClass, String key) {
        if (key == null || key.isBlank()) return null;
        if (findField(entityClass, key) != null) return key;
        for (Map.Entry<String, List<String>> e : FIELD_ALIASES.entrySet()) {
            if (!e.getKey().equalsIgnoreCase(key)) continue;
            for (String alias : e.getValue()) {
                if (findField(entityClass, alias) != null) return alias;
            }
        }
        return null;
    }

    /**
     * 外键名称 → 主键（跨表通用）。
     *
     * <p>用户/模型常只给名称：客户=楷煜、物料分类=五金件、单位=个、仓库=成品仓、工作中心=CNC。
     * 这里按「外键字段名去掉 Id + Name」去 data 里找名称字段，再回目标主档按编码/名称查主键。
     * 目标主档 = AI 实体登记表里 primaryKeyField 等于该外键字段的实体（categoryId → 物料分类、unitId → 计量单位…）。
     *
     * <p>返回 null = 全部解析完成；非 null = 给用户看的失败原因（查不到 / 匹配到多条）。
     */
    private String resolveForeignKeys(Object entity, Class<?> entityClass, Map<String, Object> data) {
        return resolveForeignKeys(entity, entityClass, data, null);
    }

    /**
     * 同 {@link #resolveForeignKeys(Object, Class, Map)}，但跳过 excludedClass 对应主档。
     *
     * <p>明细行必须跳过「表头自己」：BOM 明细里的 bomId、单据明细里的 xxxId 由保存入口赋值，
     * 它不在明细 data 里；若不跳过，会拿明细的物料名称（如「面霜膏体」）去 BOM 表里找，
     * 报「未找到物料清单BOM「面霜膏体」」，整张 BOM 建不出来（2026-10-28 实测）。
     */
    private String resolveForeignKeys(Object entity, Class<?> entityClass, Map<String, Object> data,
                                      Class<?> excludedClass) {
        if (data == null || data.isEmpty() || entityClass == null) return null;

        // 1) 精确同名字段：categoryId / materialId / customerId / warehouseId / workCenterId / routingId …
        for (EntityCrudConfig target : configMap.values()) {
            String pk = target.getPrimaryKeyField();
            if (pk == null || !pk.endsWith("Id")) continue;
            if (target.getEntityClass() == entityClass) continue;   // 不让自己引用自己（新建客户时的 customerId）
            if (excludedClass != null && target.getEntityClass() == excludedClass) continue; // 明细不解析表头主档（bomId 等）
            Field idField = findField(entityClass, pk);
            if (idField == null || getFieldValue(entity, idField) != null) continue;
            String error = resolveOneFk(entity, entityClass, idField, target, data, null);
            if (error != null) return error;
        }

        // 2) 带前缀的单位字段：baseUnitId / purchaseUnitId / saleUnitId / stockUnitId / productionUnitId
        EntityCrudConfig unitConfig = configMap.get("QUERY_BASEDATA_UNIT");
        if (unitConfig != null) {
            String[] unitFields = {"baseUnitId", "purchaseUnitId", "saleUnitId", "stockUnitId", "productionUnitId"};
            for (String fn : unitFields) {
                Field f = findField(entityClass, fn);
                if (f == null || getFieldValue(entity, f) != null) continue;
                String error = resolveOneFk(entity, entityClass, f, unitConfig, data, fn.substring(0, fn.length() - 2));
                if (error != null) return error;
            }
        }
        return null;
    }

    /**
     * 解析单个外键。namePrefix 为空时按字段名推导（categoryId → category）。
     * 依次尝试：编码精确 → 名称精确 → 名称模糊；匹配到多条直接报错，不猜。
     */
    private String resolveOneFk(Object entity, Class<?> entityClass, Field idField,
                                EntityCrudConfig target, Map<String, Object> data, String namePrefix) {
        String pk = target.getPrimaryKeyField();
        String base = namePrefix != null ? namePrefix
                : (pk.endsWith("Id") ? pk.substring(0, pk.length() - 2) : pk);
        String codeField = target.getCodeField();
        String nameField = target.getNameField();

        List<String> codes = new ArrayList<>();
        addIfNotBlank(codes, strOf(data.get(pk + "Code")));      // 例如 categoryIdCode（极少用）
        addIfNotBlank(codes, strOf(data.get(base + "Code")));    // materialCode / categoryCode / unitCode
        addIfNotBlank(codes, strOf(data.get(base + "No")));      // xxxNo
        if (codeField != null) addIfNotBlank(codes, strOf(data.get(codeField)));

        List<String> names = new ArrayList<>();
        addIfNotBlank(names, strOf(data.get(base + "Name")));    // categoryName / materialName / unitName
        addIfNotBlank(names, strOf(data.get(base)));             // customer / category / unit
        if (nameField != null) addIfNotBlank(names, strOf(data.get(nameField)));
        if ("warehouseId".equals(pk)) {
            addIfNotBlank(names, strOf(data.get("stockName")));
            addIfNotBlank(names, strOf(data.get("warehouseName")));
        }

        if (codes.isEmpty() && names.isEmpty()) return null;     // 没给名称/编码 → 交给后续必填校验

        Object matched = null;
        try {
            for (String v : codes) {
                matched = queryTargetOne(target, codeField, v, false);
                if (matched != null) break;
            }
            if (matched == null) {
                for (String v : names) {
                    matched = queryTargetOne(target, nameField, v, false);
                    if (matched != null) break;
                }
            }
            if (matched == null) {
                for (String v : names) {
                    matched = queryTargetOne(target, nameField, v, true);
                    if (matched != null) break;
                }
            }
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        if (matched == null) {
            String hint = names.isEmpty() ? codes.get(0) : names.get(0);
            return "未找到" + entityLabel(target) + "「" + hint + "」，请先在系统里维护，或告诉我准确的编码。";
        }

        Object targetId = ReflectUtil.getFieldValue(matched, pk);
        if (targetId == null) return null;
        setFieldValue(entity, idField, targetId);

        // 回填名称字段（categoryName / unitName / warehouseName / customerName …），让详情页不是空名
        Object nameVal = nameField == null ? null : ReflectUtil.getFieldValue(matched, nameField);
        if (nameVal != null) {
            List<String> displayFields = new ArrayList<>();
            displayFields.add(base + "Name");
            if (namePrefix == null) displayFields.add(pk.substring(0, pk.length() - 2) + "Name");
            if ("baseUnitId".equals(pk)) displayFields.add("unitName");
            if ("warehouseId".equals(pk)) displayFields.add("stockName");
            for (String df : displayFields) {
                Field f = findField(entityClass, df);
                if (f != null && f.getType() == String.class && strOf(getFieldValue(entity, f)).isEmpty()) {
                    setFieldValue(entity, f, String.valueOf(nameVal));
                }
            }
        }
        return null;
    }

    private void addIfNotBlank(List<String> list, String v) {
        if (v != null && !v.isBlank() && !list.contains(v)) list.add(v);
    }

    /**
     * 在目标主档里按某一列精确/模糊查一条；查不到返回 null。
     *
     * <p>命中多条时分两种情况（不猜）：</p>
     * <ul>
     *   <li>精确命中多条 = 同名/同编码的历史重复主档，本质是同一个业务对象 → 取最早建档的一条；</li>
     *   <li>模糊命中多条 = 多个不同对象（如「华为」同时命中「华为技术」「华为终端」）→ 抛错并列出候选，
     *       绝不能替用户挑一个，否则单据会静默挂到错误的客户/供应商/仓库上。</li>
     * </ul>
     */
    private Object queryTargetOne(EntityCrudConfig target, String field, String value, boolean like) {
        if (field == null || value == null || value.isBlank()) return null;
        String column = columnOf(target.getEntityClass(), field);
        QueryWrapper<Object> w = new QueryWrapper<>();
        if (like) w.like(column, value);
        else w.eq(column, value);
        // 保密物料等数据过滤与查询工具同口径
        if ("is_confidential".equals(target.getAiFilterColumn())) {
            w.and(q -> q.isNull("is_confidential").or().ne("is_confidential", 1));
        }
        String pkColumn = columnOf(target.getEntityClass(), target.getPrimaryKeyField());
        if (pkColumn != null) w.orderByAsc(pkColumn);
        w.last("LIMIT 5");
        List<Object> list = getService(target).list(w);
        if (list.size() > 1) {
            if (like) {
                // 模糊命中多条 = 多个不同业务对象，不能替用户挑一个：列出候选让他给出准确编码/全名。
                List<String> candidates = new ArrayList<>();
                for (Object o : list) {
                    Object code = target.getCodeField() == null ? null : ReflectUtil.getFieldValue(o, target.getCodeField());
                    Object name = target.getNameField() == null ? null : ReflectUtil.getFieldValue(o, target.getNameField());
                    String label = strOf(code).isEmpty() ? strOf(name)
                            : (strOf(name).isEmpty() ? strOf(code) : strOf(code) + " " + strOf(name));
                    if (!label.isEmpty() && !candidates.contains(label)) candidates.add(label);
                }
                throw new IllegalArgumentException("「" + value + "」在" + entityLabel(target) + "里匹配到多条（"
                        + String.join("、", candidates) + "），请给出准确的编码或全名。");
            }
            // 精确命中多条：主档里同名/同编码的重复记录（历史导入、重复初始化遗留）不是"多条可选"，而是同一条业务对象：
            // 按主键升序取最早建档的那条即可，绝不把"请给准确的编码"甩回给用户或模型。
            // 2026-10-28 实测：计量单位「千克」有 KG×4 + UNIT07 共 5 条，「个」同理，
            // 旧逻辑直接报多条匹配，导致批量建物料 9 条全失败，并连坐三级 BOM 建不出来。
            log.info("[AI CRUD外键] {}「{}」匹配到 {} 条重复主档，按最早建档的一条处理",
                    entityLabel(target), value, list.size());
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 币种回填：名称/编码 → 币种ID；再退回本位币；实在没有则返回给用户的提示。
     *
     * <p>历史问题（2026-10-21 实测）：模型给的是「人民币」这种名称（currencyName），
     * 直接丢弃后 currency_id 为空，银行账户等非空约束表直接报数据库错误，用户只看到"创建失败"。
     */
    private String fillCurrency(Object entity, Class<?> entityClass, Map<String, Object> data) {
        Field field = findField(entityClass, "currencyId");
        if (field == null) return null;
        try {
            field.setAccessible(true);
            if (field.get(entity) != null) return null;
            CurrencyService currencyService = applicationContext.getBean(CurrencyService.class);

            Object raw = data.get("currencyName");
            if (raw == null) raw = data.get("currencyCode");
            if (raw == null) raw = data.get("currency");
            String name = strOf(raw);
            if (!name.isEmpty()) {
                Currency matched = currencyService.getOne(new QueryWrapper<Currency>()
                        .and(w -> w.eq("currency_name", name).or().eq("currency_code", name))
                        .last("LIMIT 1"));
                if (matched != null) {
                    field.set(entity, matched.getCurrencyId());
                    return null;
                }
            }

            Currency base = currencyService.getOne(new QueryWrapper<Currency>()
                    .eq("is_base_currency", 1).last("LIMIT 1"));
            if (base == null) {
                base = currencyService.getOne(new QueryWrapper<Currency>()
                        .orderByAsc("currency_id").last("LIMIT 1"));
            }
            if (base != null) {
                field.set(entity, base.getCurrencyId());
                return null;
            }
            return "系统里还没有币种，请先在基础资料 → 币种里维护币种后再创建";
        } catch (Exception e) {
            log.warn("[AI CRUD创建] 币种解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 取 items 字段的元素类型（如 List&lt;SaleDeliveryItem&gt; → SaleDeliveryItem） */
    private Class<?> listItemClass(Field itemsField) {
        Type type = itemsField.getGenericType();
        if (type instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> clazz) return clazz;
        }
        return null;
    }

    /** 把一行明细（Map）转成该单据的明细实体；物料只给名称/编码也能解析成主键。
     *  unknownSink 非空时收集"传了但明细实体上没有的字段"（如实回传给模型，不静默丢弃）。 */
    private Object buildItem(Class<?> itemClass, Map<String, Object> raw, int lineNo, Class<?> parentClass,
                             List<String> unknownSink) {
        Object item;
        try {
            item = itemClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("第 " + lineNo + " 行明细的类型无法创建，未保存。");
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String resolved = resolveCreateFieldName(itemClass, e.getKey());
            if (resolved == null) {
                if (unknownSink != null) {
                    unknownSink.add("明细第" + lineNo + "行 " + e.getKey()
                            + (e.getValue() == null ? "" : "=" + strOf(e.getValue())));
                }
                continue;
            }
            Field f = findField(itemClass, resolved);
            if (f == null) continue;
            try {
                f.setAccessible(true);
                f.set(item, convertValue(f.getType(), e.getValue()));
            } catch (Exception ignored) {}
        }

        Field materialIdField = findField(itemClass, "materialId");
        // 只有"物料行"才要求必须有物料；工艺路线的工序行没有 materialId 字段，不能按物料校验
        if (materialIdField != null) {
            Object materialId = getFieldValue(item, materialIdField);
            String code = strOf(raw.get("materialCode"));
            String name = strOf(raw.get("materialName"));
            if (materialId == null && (!code.isEmpty() || !name.isEmpty())) {
                Material material = findMaterial(code, name);
                if (material == null) {
                    throw new IllegalArgumentException("第 " + lineNo + " 行明细没找到物料「"
                            + (name.isEmpty() ? code : name) + "」，未保存；请确认物料编码或名称。");
                }
                setFieldValue(item, materialIdField, material.getMaterialId());
                fillItemMaterial(itemClass, item, material);
            } else if (materialId == null) {
                throw new IllegalArgumentException("第 " + lineNo + " 行明细缺少物料，未保存；请补充物料编码或名称。");
            }
        }

        // 明细行自身的外键（如仓库名称→仓库ID、工作中心名称→工作中心ID）同样按名称解析
        String fkError = resolveForeignKeys(item, itemClass, raw, parentClass);
        if (fkError != null) {
            throw new IllegalArgumentException("第 " + lineNo + " 行明细：" + fkError);
        }

        Field qtyField = findField(itemClass, "quantity");
        if (qtyField != null) {
            BigDecimal qty = toDecimal(getFieldValue(item, qtyField));
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("第 " + lineNo + " 行明细缺少数量（必须大于 0），未保存。");
            }
        }

        Field lineNoField = findField(itemClass, "lineNo");
        if (lineNoField != null && getFieldValue(item, lineNoField) == null) {
            setFieldValue(item, lineNoField, lineNo);
        }
        return item;
    }

    /** 物料档案上的编码/名称/规格/单位/税率回填到明细（用户只报名称也不会出现半空行） */
    private void fillItemMaterial(Class<?> itemClass, Object item, Material material) {
        setIfNullOrBlank(itemClass, item, "materialCode", material.getMaterialCode());
        setIfNullOrBlank(itemClass, item, "materialName", material.getMaterialName());
        setIfNullOrBlank(itemClass, item, "specification", material.getModel());
        Field unitField = findField(itemClass, "unitId");
        if (unitField != null && getFieldValue(item, unitField) == null) {
            setFieldValue(item, unitField, material.getBaseUnitId());
        }
        Field taxRate = findField(itemClass, "taxRate");
        if (taxRate != null && getFieldValue(item, taxRate) == null) {
            setFieldValue(item, taxRate, material.getTaxRate() != null ? material.getTaxRate() : new BigDecimal("13"));
        }
    }

    private Material findMaterial(String code, String name) {
        try {
            MaterialService materialService = applicationContext.getBean(MaterialService.class);
            if (!code.isEmpty()) {
                Material m = materialService.getOne(new QueryWrapper<Material>()
                        .eq("material_code", code)
                        .and(w -> w.isNull("is_confidential").or().ne("is_confidential", 1))
                        .last("LIMIT 1"));
                if (m != null) return m;
            }
            if (!name.isEmpty()) {
                return materialService.getOne(new QueryWrapper<Material>()
                        .like("material_name", name)
                        .and(w -> w.isNull("is_confidential").or().ne("is_confidential", 1))
                        .last("LIMIT 1"));
            }
        } catch (Exception e) {
            log.warn("[AI CRUD创建] 物料查询失败: {}", e.getMessage());
        }
        return null;
    }

    /** 找到 Service 的「表头 + 明细」保存方法：saveXxxWithItems / saveXxxWithOperations(表头, List) 或 saveWithItems(表头) */
    private Method findSaveWithItems(IService service, Class<?> entityClass) {
        Method oneArg = null;
        for (Method m : service.getClass().getMethods()) {
            String n = m.getName();
            if (!n.startsWith("save")) continue;
            if (!n.endsWith("WithItems") && !n.endsWith("WithOperations")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0].isAssignableFrom(entityClass) && List.class.isAssignableFrom(p[1])) return m;
            if (p.length == 1 && p[0].isAssignableFrom(entityClass)) oneArg = m;
        }
        return oneArg;
    }

    private Object getFieldValue(Object target, Field field) {
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private void setFieldValue(Object target, Field field, Object value) {
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {}
    }

    private void setIfNullOrBlank(Class<?> clazz, Object target, String name, Object value) {
        Field f = findField(clazz, name);
        if (f == null || value == null) return;
        Object current = getFieldValue(target, f);
        if (current != null && !String.valueOf(current).isBlank()) return;
        setFieldValue(target, f, convertValue(f.getType(), value));
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private String strOf(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** 面向用户的单据中文名（复用工具注册表的同一份映射；取不到时用"该单据"，绝不回退成类名） */
    private String entityLabel(EntityCrudConfig config) {
        try {
            String label = applicationContext.getBean(com.erp.ai.tool.ToolRegistry.class)
                    .entityLabelOfTool("query_" + config.getIntentPrefix().toLowerCase(Locale.ROOT));
            if (label != null && !label.isBlank()) return label;
        } catch (Exception ignored) {}
        return "该单据";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Map<String, Object> update(String intent, Map<String, Object> params, Long userId) {
        EntityCrudConfig config = getConfig(intent);
        if (config == null) {
            return Map.of("success", false, "message", "不支持的意图: " + intent);
        }

        try {
            IService service = getService(config);
            Object idValue = params.get(config.getPrimaryKeyField());
            if (idValue == null) idValue = params.get("id");

            if (idValue == null) {
                return Map.of("success", false, "message", "缺少ID参数");
            }

            Object entity = service.getById(((Number) idValue).longValue());
            if (entity == null) {
                return Map.of("success", false, "message", "记录不存在");
            }

            Map<String, Object> entityParams = (Map<String, Object>) params.getOrDefault("data", params);
            for (Map.Entry<String, Object> entry : entityParams.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                if (config.getPrimaryKeyField().equals(fieldName) || "id".equals(fieldName)) continue;
                try {
                    Field field = findField(config.getEntityClass(), fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        Object converted = convertValue(field.getType(), value);
                        field.set(entity, converted);
                    }
                } catch (Exception ignored) {}
            }

            try {
                Field updatedBy = findField(config.getEntityClass(), "updatedBy");
                if (updatedBy != null) {
                    updatedBy.setAccessible(true);
                    updatedBy.set(entity, userId != null ? userId.toString() : "AI");
                }
            } catch (Exception ignored) {}

            service.updateById(entity);

            log.info("[AI CRUD更新] 成功 intent={} id={}", intent, idValue);

            return Map.of(
                    "success", true,
                    "id", idValue,
                    "message", "更新成功"
            );
        } catch (Exception e) {
            log.error("[AI CRUD更新] 失败 intent={}", intent, e);
            return Map.of("success", false, "message", "更新失败: " + e.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Map<String, Object> delete(String intent, Map<String, Object> params, Long userId) {
        EntityCrudConfig config = getConfig(intent);
        if (config == null) {
            return Map.of("success", false, "message", "不支持的意图: " + intent);
        }

        try {
            IService service = getService(config);
            Object idValue = params.get(config.getPrimaryKeyField());
            if (idValue == null) idValue = params.get("id");

            if (idValue == null) {
                String name = (String) params.get(config.getNameField());
                String code = (String) params.get(config.getCodeField());
                if (name != null || code != null) {
                    QueryWrapper<Object> wrapper = new QueryWrapper<>();
                    if (name != null) wrapper.eq(columnOf(config.getEntityClass(), config.getNameField()), name);
                    if (code != null) wrapper.eq(columnOf(config.getEntityClass(), config.getCodeField()), code);
                    wrapper.last("LIMIT 1");
                    Object entity = service.getOne(wrapper);
                    if (entity != null) {
                        idValue = ReflectUtil.getFieldValue(entity, config.getPrimaryKeyField());
                    }
                }
            }

            if (idValue == null) {
                return Map.of("success", false, "message", "缺少ID参数且未找到匹配记录");
            }

            boolean result = service.removeById(((Number) idValue).longValue());

            log.info("[AI CRUD删除] 成功 intent={} id={} result={}", intent, idValue, result);

            return Map.of(
                    "success", result,
                    "id", idValue,
                    "message", result ? "删除成功" : "删除失败"
            );
        } catch (Exception e) {
            log.error("[AI CRUD删除] 失败 intent={}", intent, e);
            return Map.of("success", false, "message", "删除失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listAllSupportedEntities() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, EntityCrudConfig> entry : configMap.entrySet()) {
            EntityCrudConfig cfg = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("intent", entry.getKey());
            item.put("entityName", cfg.getEntityClass().getSimpleName());
            item.put("module", cfg.getIntentPrefix().split("_")[0]);
            item.put("searchFields", cfg.getSearchFields());
            item.put("displayFields", cfg.getDisplayFields());
            // 能力清单补「可筛选字段」（2026-11-22 立项一期）：让小楷知道自己能按什么条件筛——
            // 本次"没有交货日期字段暴露给我直接筛选"的直接根因就是能力清单没输出筛选白名单
            item.put("filterFields", cfg.getFilterFields());
            item.put("isMasterData", cfg.isMasterData());
            item.put("primaryKeyField", cfg.getPrimaryKeyField());
            item.put("nameField", cfg.getNameField());
            item.put("codeField", cfg.getCodeField());
            result.add(item);
        }
        return result;
    }

    public Map<String, EntityCrudConfig> getAllConfigs() {
        return Collections.unmodifiableMap(configMap);
    }

    /**
     * 全部可创建实体清单（供「批量创建（可跨表）」工具做枚举）：
     * key = 查询意图前缀小写（如 basedata_material / sale_order），value = 面向用户的中文名。
     */
    public Map<String, String> listEntityOptions() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, EntityCrudConfig> e : configMap.entrySet()) {
            String key = e.getKey().startsWith("QUERY_")
                    ? e.getKey().substring("QUERY_".length()).toLowerCase(Locale.ROOT)
                    : e.getKey().toLowerCase(Locale.ROOT);
            out.put(key, entityLabel(e.getValue()));
        }
        return out;
    }

    /**
     * 把批量工具传来的实体键解析成查询意图。
     * 同时接受全名（basedata_material / sale_order）与简称（material / customer）；
     * 简称命中多条时判定为歧义，返回 null（由调用方提示用户写全名），绝不乱猜。
     */
    public String resolveIntent(String entityKey) {
        if (entityKey == null || entityKey.isBlank()) return null;
        String key = entityKey.trim().toUpperCase(Locale.ROOT);
        if (key.startsWith("QUERY_")) key = key.substring("QUERY_".length());
        EntityCrudConfig exact = configMap.get("QUERY_" + key);
        if (exact != null) return "QUERY_" + key;

        String matched = null;
        for (String intent : configMap.keySet()) {
            if (!intent.endsWith("_" + key)) continue;
            if (matched != null) {
                log.warn("[AI批量创建] 实体简称「{}」命中多条：{} / {}，拒绝猜测", entityKey, matched, intent);
                return null;
            }
            matched = intent;
        }
        return matched;
    }

    /** 由意图取面向用户的实体中文名（取不到时返回"该实体"） */
    public String intentLabel(String intent) {
        EntityCrudConfig cfg = intent == null ? null : configMap.get(intent);
        return cfg == null ? "该实体" : entityLabel(cfg);
    }

    private String camelToUnderscore(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * 属性名 → 真实数据库列名。
     * 【铁律】必须认实体上的 {@code @TableField("列名")} 映射：camelToUnderscore 只会把属性名驼峰转下划线，
     * 一旦属性名与真实列名不一致（如 PurchaseInvoice.amount 实际映射列 total_amount、
     * PurchaseInbound.stockId 实际映射列 warehouse_id），拼出来的列名就是错的 —— 查询会静默返回空列、
     * 按它筛选会直接 SQL 报错。故凡是要拼 SQL 列名的地方一律走本方法。
     * 注意：{@code @TableField(exist=false)} 的字段不是库列（如外键补全器填充的名称键），此时仍回退到
     * 驼峰转下划线，交由开机自检去报错，不在本方法内静默放行。
     */
    private String columnOf(Class<?> entityClass, String field) {
        Field f = findField(entityClass, field);
        if (f != null) {
            com.baomidou.mybatisplus.annotation.TableField tf =
                    f.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
            if (tf != null && tf.exist() && tf.value() != null && !tf.value().isBlank()) {
                return tf.value();
            }
        }
        return camelToUnderscore(field);
    }

    /**
     * 按关键词（物料编码 / 名称 / 全名，模糊）解析物料主键。
     * <p>
     * 用途：库存表（inventory_current）的 material_code / material_name 是**冗余列、可能过期**
     * （实测 material_code 为空但 material_id 有效），只按冗余列搜会漏 → "按编码查库存查不到"。
     * 关键词同时按物料档案解析一次主键，命中并集即可覆盖（2026-09-29）。
     */
    private List<Long> resolveMaterialIdsByKeyword(String keyword) {
        try {
            String like = "%" + keyword + "%";
            return jdbcTemplate.queryForList(
                    "SELECT material_id FROM material "
                            + "WHERE (material_code LIKE ? OR material_name LIKE ? OR full_name LIKE ?) "
                            + "AND (is_confidential IS NULL OR is_confidential <> 1) LIMIT 200",
                    Long.class, like, like, like);
        } catch (Exception e) {
            log.warn("[AI查询] 关键词解析物料主键失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static final Map<String, List<String>> FIELD_ALIASES = Map.of(
            "date", List.of("orderDate", "deliveryDate", "createDate", "purchaseDate", "inboundDate", "outboundDate",
                    "workDate", "voucherDate", "expenseDate", "bizDate", "documentDate", "transactionDate"),
            "startDate", List.of("startDate", "beginDate", "fromDate"),
            "endDate", List.of("endDate", "toDate", "finishDate"),
            "amount", List.of("totalAmount", "orderAmount", "amount", "payAmount", "receivableAmount", "payableAmount"),
            "quantity", List.of("quantity", "totalQuantity", "orderQuantity", "inboundQuantity", "outboundQuantity"),
            "name", List.of("customerName", "supplierName", "materialName", "employeeName", "deptName", "warehouseName")
    );

    private String resolveFieldName(Class<?> entityClass, String paramKey) {
        if (paramKey == null || paramKey.isBlank()) return null;

        if (findField(entityClass, paramKey) != null) {
            return paramKey;
        }

        String lowerKey = paramKey.toLowerCase();
        for (Map.Entry<String, List<String>> entry : FIELD_ALIASES.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(lowerKey)) {
                for (String alias : entry.getValue()) {
                    if (findField(entityClass, alias) != null) {
                        log.debug("[字段匹配] {} -> {}", paramKey, alias);
                        return alias;
                    }
                }
            }
        }

        Field[] fields = ReflectUtil.getFields(entityClass);
        for (Field field : fields) {
            if (field.getName().toLowerCase().contains(lowerKey)
                    || lowerKey.contains(field.getName().toLowerCase())) {
                log.debug("[字段模糊匹配] {} -> {}", paramKey, field.getName());
                return field.getName();
            }
        }

        log.warn("[字段未匹配] 实体 {} 中找不到字段: {}", entityClass.getSimpleName(), paramKey);
        return null;
    }

    /** 支持的条件操作符（白名单） */
    private static final java.util.Set<String> FILTER_OPS =
            java.util.Set.of("eq", "ne", "gt", "ge", "lt", "le", "like", "between");

    /** 解析 filters 参数：数组 [{field,op,value}]；结构不对直接报错（不静默忽略条件，否则用户看到"条件没生效"却无提示） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFilters(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("filters 必须是数组，如 [{\"field\":\"status\",\"op\":\"eq\",\"value\":\"草稿\"}]");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException("filters 的每一项必须是对象 {field,op,value}");
            }
            Map<String, Object> m = (Map<String, Object>) o;
            if (m.get("field") == null || String.valueOf(m.get("field")).isBlank()) {
                throw new IllegalArgumentException("filters 每项必须有 field");
            }
            if (m.get("value") == null) {
                throw new IllegalArgumentException("筛选「" + m.get("field") + "」缺少 value");
            }
            out.add(m);
        }
        return out;
    }

    /** between 取值：支持 [a,b] 或 "a~b" */
    private List<?> toPair(Object v) {
        if (v instanceof List<?> l) return l;
        String s = String.valueOf(v);
        int i = s.indexOf('~');
        if (i > 0) return List.of(s.substring(0, i).trim(), s.substring(i + 1).trim());
        return List.of(s);
    }

    private Object convertFieldValue(Class<?> entityClass, String fieldName, Object value) {
        Field field = findField(entityClass, fieldName);
        if (field == null) return value;
        try {
            return convertValue(field.getType(), value);
        } catch (Exception e) {
            log.warn("[字段值转换失败] {} -> {}", fieldName, e.getMessage());
            return value;
        }
    }

    private Object convertValue(Class<?> targetType, Object value) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        String strValue = value.toString();

        if (targetType == String.class) return strValue;
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(strValue);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(strValue);
        }
        if (targetType == Double.class || targetType == double.class) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(strValue);
        }
        if (targetType == java.math.BigDecimal.class) {
            return new java.math.BigDecimal(strValue);
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(strValue);
        }
        if (targetType == LocalDateTime.class) {
            return LocalDateTime.parse(strValue);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(strValue);
        }

        return value;
    }
}
