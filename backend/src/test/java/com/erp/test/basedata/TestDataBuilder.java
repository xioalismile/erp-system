package com.erp.test.basedata;

import java.util.HashMap;
import java.util.Map;

/**
 * 14 个基础资料模块测试数据构造器
 * 统一管理各模块新增实体的 JSON 构造，便于测试用例复用
 * 编码使用 "TEST_" 前缀 + 时间戳避免冲突
 */
public class TestDataBuilder {

    private static final String TIMESTAMP = String.valueOf(System.currentTimeMillis() % 100000000L);  // 取后 8 位

    /** 生成唯一编码（保证测试隔离，总长度 ≤ 18 字符以适应 VARCHAR(20)） */
    public static String uniqueCode(String prefix) {
        return prefix + TIMESTAMP + (int)(Math.random() * 1000);
    }

    // ============ 1. 币种 Currency ============
    public static Map<String, Object> buildCurrency() {
        Map<String, Object> m = new HashMap<>();
        m.put("currencyCode", uniqueCode("CUR"));
        m.put("currencyName", "测试币种");
        m.put("currencySymbol", "T$");
        m.put("isBaseCurrency", (short) 0);
        m.put("exchangeRate", 7.25);
        m.put("status", "启用");
        return m;
    }

    // ============ 2. 计量单位 UnitOfMeasure ============
    public static Map<String, Object> buildUnit() {
        Map<String, Object> m = new HashMap<>();
        m.put("unitCode", uniqueCode("UOM"));
        m.put("unitName", "测试单位");
        m.put("unitGroup", "数量");
        m.put("isBaseUnit", (short) 1);
        m.put("conversionRate", 1.0);
        m.put("status", "启用");
        return m;
    }

    // ============ 3. 物料分类 MaterialCategory ============
    public static Map<String, Object> buildMaterialCategory() {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryCode", uniqueCode("MC"));
        m.put("categoryName", "测试分类");
        m.put("parentId", 0);
        m.put("categoryLevel", 1);
        m.put("sortOrder", 1);
        m.put("status", "启用");
        return m;
    }

    // ============ 4. 客户 Customer ============
    public static Map<String, Object> buildCustomer() {
        Map<String, Object> m = new HashMap<>();
        m.put("customerCode", uniqueCode("CUS"));
        m.put("customerName", "测试客户有限公司");
        m.put("shortName", "测试客户");
        m.put("customerType", "企业");
        m.put("customerLevel", "A");
        m.put("taxNo", "91110000TEST" + TIMESTAMP);
        m.put("contactPerson", "张三");
        m.put("contactPhone", "13800138000");
        m.put("email", "test@test.com");
        m.put("address", "测试地址123号");
        m.put("bankName", "测试银行");
        m.put("bankAccount", "622848" + TIMESTAMP + "01");
        m.put("creditLimit", 100000);
        m.put("status", "启用");
        return m;
    }

    // ============ 5. 供应商 Supplier ============
    public static Map<String, Object> buildSupplier() {
        Map<String, Object> m = new HashMap<>();
        m.put("supplierCode", uniqueCode("SUP"));
        m.put("supplierName", "测试供应商有限公司");
        m.put("shortName", "测试供应商");
        m.put("supplierType", "企业");
        m.put("supplierLevel", "B");
        m.put("taxNo", "91110000SUPP" + TIMESTAMP);
        m.put("contactPerson", "李四");
        m.put("contactPhone", "13900139000");
        m.put("email", "supplier@test.com");
        m.put("address", "供应商地址456号");
        m.put("bankName", "供应商银行");
        m.put("bankAccount", "622849" + TIMESTAMP + "01");
        m.put("deliveryDays", 7);
        m.put("status", "启用");
        return m;
    }

    // ============ 6. 仓库 Warehouse ============
    public static Map<String, Object> buildWarehouse() {
        Map<String, Object> m = new HashMap<>();
        m.put("stockCode", uniqueCode("WH"));
        m.put("stockName", "测试仓库");
        m.put("stockType", "普通仓");
        m.put("address", "仓库地址789号");
        m.put("isBinControl", (short) 0);
        m.put("isMrpAvailable", (short) 1);
        m.put("status", "启用");
        return m;
    }

    // ============ 7. 货位 Location ============
    public static Map<String, Object> buildLocation() {
        Map<String, Object> m = new HashMap<>();
        m.put("binCode", uniqueCode("LOC"));
        m.put("binName", "测试货位");
        m.put("stockId", 1);
        m.put("status", "启用");
        return m;
    }

    // ============ 8. 物料 Material ============
    public static Map<String, Object> buildMaterial() {
        Map<String, Object> m = new HashMap<>();
        m.put("materialCode", uniqueCode("MAT"));
        m.put("materialName", "测试物料");
        m.put("fullName", "测试物料全名规格型号");
        m.put("model", "TEST-MODEL-001");
        m.put("materialType", "自制件");
        m.put("categoryId", 1);
        m.put("baseUnitId", 1);
        m.put("isLotControl", (short) 0);
        m.put("isSnControl", (short) 0);
        m.put("isBinControl", (short) 0);
        m.put("isQcRequired", (short) 0);
        m.put("salePrice", 100.50);
        m.put("purchasePrice", 80.00);
        m.put("taxRate", 13);
        m.put("minStock", 10);
        m.put("maxStock", 1000);
        m.put("status", "启用");
        return m;
    }

    // ============ 9. 部门 Department ============
    public static Map<String, Object> buildDepartment() {
        Map<String, Object> m = new HashMap<>();
        m.put("deptCode", uniqueCode("DEPT"));
        m.put("deptName", "测试部门");
        m.put("parentDeptId", 0);
        m.put("deptType", "部门");
        m.put("status", "启用");
        return m;
    }

    // ============ 10. 员工 Employee ============
    public static Map<String, Object> buildEmployee() {
        Map<String, Object> m = new HashMap<>();
        m.put("employeeCode", uniqueCode("EMP"));
        m.put("employeeName", "测试员工");
        m.put("deptId", 1);
        m.put("position", "测试工程师");
        m.put("gender", "男");
        m.put("mobilePhone", "13700137000");
        m.put("email", "emp@test.com");
        m.put("entryDate", "2024-01-01");
        m.put("status", "启用");
        return m;
    }

    // ============ 11. 付款条件 PaymentTerm ============
    public static Map<String, Object> buildPaymentTerm() {
        Map<String, Object> m = new HashMap<>();
        m.put("paymentTermCode", uniqueCode("PT"));
        m.put("paymentTermName", "测试付款条件");
        m.put("paymentDays", 30);
        m.put("discountDays", 10);
        m.put("discountRate", 2);
        m.put("advanceRate", 0);
        m.put("status", "启用");
        return m;
    }

    // ============ 12. 价格等级 PriceLevel ============
    public static Map<String, Object> buildPriceLevel() {
        Map<String, Object> m = new HashMap<>();
        m.put("priceLevelCode", uniqueCode("PL"));
        m.put("priceLevelName", "测试价格等级");
        m.put("priceAdjustRate", 95);
        m.put("sortOrder", 99);
        m.put("status", "启用");
        return m;
    }

    // ============ 13. 结算方式 SettlementMode ============
    public static Map<String, Object> buildSettlementMode() {
        Map<String, Object> m = new HashMap<>();
        m.put("settleModeCode", uniqueCode("SM"));
        m.put("settleModeName", "测试结算方式");
        m.put("settleType", "现金");
        m.put("sortOrder", 99);
        m.put("status", "启用");
        return m;
    }

    // ============ 14. 会计科目 Account ============
    public static Map<String, Object> buildAccount() {
        Map<String, Object> m = new HashMap<>();
        m.put("accountCode", uniqueCode("ACC"));
        m.put("accountName", "测试科目");
        m.put("accountFullName", "测试科目全名");
        m.put("parentAccountId", 0);
        m.put("accountType", "资产");
        m.put("balanceDirection", "借");
        m.put("accountLevel", 1);
        m.put("isLeaf", (short) 1);
        m.put("isCashAccount", (short) 0);
        m.put("status", "启用");
        return m;
    }

    /** 根据模块名获取测试数据 */
    public static Map<String, Object> buildByModule(String module) {
        switch (module) {
            case "currency": return buildCurrency();
            case "unit": return buildUnit();
            case "material-category": return buildMaterialCategory();
            case "customer": return buildCustomer();
            case "supplier": return buildSupplier();
            case "warehouse": return buildWarehouse();
            case "location": return buildLocation();
            case "material": return buildMaterial();
            case "department": return buildDepartment();
            case "employee": return buildEmployee();
            case "payment-term": return buildPaymentTerm();
            case "price-level": return buildPriceLevel();
            case "settle": return buildSettlementMode();
            case "account": return buildAccount();
            default: throw new IllegalArgumentException("未知模块: " + module);
        }
    }

    /** 获取模块编码字段名 */
    public static String getCodeField(String module) {
        switch (module) {
            case "currency": return "currencyCode";
            case "unit": return "unitCode";
            case "material-category": return "categoryCode";
            case "customer": return "customerCode";
            case "supplier": return "supplierCode";
            case "warehouse": return "stockCode";
            case "location": return "binCode";
            case "material": return "materialCode";
            case "department": return "deptCode";
            case "employee": return "employeeCode";
            case "payment-term": return "paymentTermCode";
            case "price-level": return "priceLevelCode";
            case "settle": return "settleModeCode";
            case "account": return "accountCode";
            default: throw new IllegalArgumentException("未知模块: " + module);
        }
    }

    /** 获取模块主键字段名 */
    public static String getIdField(String module) {
        switch (module) {
            case "currency": return "currencyId";
            case "unit": return "unitId";
            case "material-category": return "categoryId";
            case "customer": return "customerId";
            case "supplier": return "supplierId";
            case "warehouse": return "stockId";
            case "location": return "binId";
            case "material": return "materialId";
            case "department": return "deptId";
            case "employee": return "employeeId";
            case "payment-term": return "paymentTermId";
            case "price-level": return "priceLevelId";
            case "settle": return "settleModeId";
            case "account": return "accountId";
            default: throw new IllegalArgumentException("未知模块: " + module);
        }
    }

    /** 获取模块名称字段名 */
    public static String getNameField(String module) {
        switch (module) {
            case "currency": return "currencyName";
            case "unit": return "unitName";
            case "material-category": return "categoryName";
            case "customer": return "customerName";
            case "supplier": return "supplierName";
            case "warehouse": return "stockName";
            case "location": return "binName";
            case "material": return "materialName";
            case "department": return "deptName";
            case "employee": return "employeeName";
            case "payment-term": return "paymentTermName";
            case "price-level": return "priceLevelName";
            case "settle": return "settleModeName";
            case "account": return "accountName";
            default: throw new IllegalArgumentException("未知模块: " + module);
        }
    }

    /** 获取模块中文名 */
    public static String getModuleCnName(String module) {
        switch (module) {
            case "currency": return "币种";
            case "unit": return "计量单位";
            case "material-category": return "物料分类";
            case "customer": return "客户";
            case "supplier": return "供应商";
            case "warehouse": return "仓库";
            case "location": return "货位";
            case "material": return "物料";
            case "department": return "部门";
            case "employee": return "员工";
            case "payment-term": return "付款条件";
            case "price-level": return "价格等级";
            case "settle": return "结算方式";
            case "account": return "会计科目";
            default: return module;
        }
    }

    /** 获取所有 14 个模块名列表 */
    public static String[] getAllModules() {
        return new String[]{
            "currency", "unit", "material-category", "customer", "supplier",
            "warehouse", "location", "material", "department", "employee",
            "payment-term", "price-level", "settle", "account"
        };
    }

    /** 获取树形结构模块列表 */
    public static String[] getTreeModules() {
        return new String[]{"material-category", "department", "account"};
    }
}
