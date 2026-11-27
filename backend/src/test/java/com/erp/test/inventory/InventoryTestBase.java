package com.erp.test.inventory;

import com.erp.test.TestEnv;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 库存管理模块自动化测试基类
 *
 * 职责：
 *  1. 登录获取 JWT token（目标环境/账号从系统属性/环境变量读，见 TestEnv；登录后必须选租户）
 *  2. 封装带鉴权的 HTTP 请求（GET/POST/PUT/DELETE）
 *  3. 提供测试数据生成器（5 类库存单据的请求体与明细）
 *  4. 提供各单据的创建工厂方法，返回新建实体ID
 *  5. 提供即时库存查询辅助方法，用于校验库存增减
 *  6. 提供通用断言辅助
 *
 * 覆盖的 5 类业务单据：
 *  - 其他入库单 OtherReceipt     /api/inventory/other-receipts    前缀 OIN-yyyyMMdd-0001
 *  - 其他出库单 OtherIssue       /api/inventory/other-issues      前缀 OOUT-yyyyMMdd-0001
 *  - 生产领料单 MaterialRequisition /api/inventory/material-requisitions 前缀 MOPICK-yyyyMMdd-0001
 *  - 调拨单 StockTransfer        /api/inventory/stock-transfers   前缀 TR-yyyyMMdd-0001
 *  - 盘点单 StockCheck           /api/inventory/stock-checks      前缀 INV-yyyyMMdd-0001
 *
 * 设计原则（参照 ProductionTestBase）：
 *  - 用例之间数据隔离，每个用例独立创建单据
 *  - 单据编号由后端自动生成（请求体不传编号），保证格式与唯一性
 *  - 所有请求默认带 Authorization 头与 X-Operator 头
 *  - 状态流转端点统一使用 POST + 路径参数
 */
public abstract class InventoryTestBase {

    protected static String token;
    /** 目标环境地址（系统属性 erp.test.base-url / 环境变量 ERP_TEST_BASE_URL） */
    protected static final String BASE_URL = TestEnv.baseUrl();

    /** 预设基础数据 ID：@BeforeAll 时按目标租户库动态解析（独立租户库里未必存在写死的 1） */
    protected static Long WAREHOUSE_ID_1 = 1L;
    protected static Long WAREHOUSE_ID_2 = 2L;
    protected static Long MATERIAL_ID = 1L;
    protected static Long UNIT_ID = 1L;
    protected static Long CURRENCY_ID = 1L;
    protected static Long DEPARTMENT_ID = 1L;
    protected static Long EMPLOYEE_ID = 1L;
    protected static Long HANDLER_ID = 1L;
    protected static String MATERIAL_CODE = "MAT-001";
    protected static String MATERIAL_NAME = "测试物料";
    protected static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("100");

    /** 自增序号，避免同毫秒生成的批次号冲突 */
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    @org.junit.jupiter.api.BeforeAll
    static void loginAndGetToken() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.urlEncodingEnabled = true;

        // 登录 → 选租户（三步闸门）；禁止平台超管 admin（其 token 无 tenantId，业务请求会落主库 erp_db）
        token = TestEnv.loginAndSelectTenant(TestEnv.tenantAdminUser(), TestEnv.tenantAdminPassword());

        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("登录失败，无法获取 token，请确认后端已启动 (" + BASE_URL + ")");
        }
        System.out.println("[InventoryTestBase] 登录成功, token=" + token.substring(0, Math.min(20, token.length())) + "...");

        loadReferenceIds();
    }

    // ===================== 基础资料动态解析 =====================

    /** 查询基础资料列表（兼容 data 直接是数组 / data.records 两种返回；失败返回空列表，不中断测试） */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listData(String path) {
        try {
            Response r = RestAssured.given()
                    .header("Authorization", "Bearer " + token)
                    .header("X-Operator", TestEnv.operatorName())
                    .when().get(path);
            if (r.statusCode() != 200) {
                warn(path + " 返回 HTTP " + r.statusCode() + "，相关基础资料 ID 沿用默认值");
                return List.of();
            }
            Object data = r.jsonPath().get("data");
            if (data instanceof List) return (List<Map<String, Object>>) data;
            Object records = r.jsonPath().get("data.records");
            if (records instanceof List) return (List<Map<String, Object>>) records;
            return List.of();
        } catch (Exception e) {
            warn(path + " 查询异常 " + e.getMessage() + "，相关基础资料 ID 沿用默认值");
            return List.of();
        }
    }

    /** 从记录中按候选字段名取 ID，取不到返回 null */
    private static Long idOf(Map<String, Object> rec, String... keys) {
        if (rec == null) return null;
        for (String k : keys) {
            Object v = rec.get(k);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v != null && !v.toString().isEmpty()) {
                try {
                    return Long.parseLong(v.toString());
                } catch (NumberFormatException ignore) {
                    // 非数字字段，继续尝试下一个候选键
                }
            }
        }
        return null;
    }

    private static void warn(String msg) {
        System.out.println("[InventoryTestBase] 警告: " + msg);
    }

    /**
     * 动态解析基础资料 ID（参照 ProductionFieldTestBase 的做法）。
     * 查不到记录时保留默认值并打印警告，不抛异常中断（避免本地/新租户无数据时整片报红）。
     */
    private static void loadReferenceIds() {
        List<Map<String, Object>> warehouses = listData("/api/basedata/warehouse/list");
        if (!warehouses.isEmpty()) {
            Long wh1 = idOf(warehouses.get(0), "stockId", "warehouseId", "id");
            if (wh1 != null) WAREHOUSE_ID_1 = wh1;
        } else {
            warn("仓库列表为空，WAREHOUSE_ID_1 沿用默认值 " + WAREHOUSE_ID_1);
        }
        if (warehouses.size() > 1) {
            Long wh2 = idOf(warehouses.get(1), "stockId", "warehouseId", "id");
            if (wh2 != null) WAREHOUSE_ID_2 = wh2;
        } else {
            warn("仓库不足 2 条，WAREHOUSE_ID_2 沿用默认值 " + WAREHOUSE_ID_2);
        }

        List<Map<String, Object>> materials = listData("/api/basedata/material/list");
        if (!materials.isEmpty()) {
            Long mid = idOf(materials.get(0), "materialId", "id");
            if (mid != null) MATERIAL_ID = mid;
            Object code = materials.get(0).get("materialCode");
            Object name = materials.get(0).get("materialName");
            if (code != null && !code.toString().isEmpty()) MATERIAL_CODE = code.toString();
            if (name != null && !name.toString().isEmpty()) MATERIAL_NAME = name.toString();
        } else {
            warn("物料列表为空，MATERIAL_ID/MATERIAL_CODE/MATERIAL_NAME 沿用默认值");
        }

        List<Map<String, Object>> units = listData("/api/basedata/unit/list");
        if (!units.isEmpty()) {
            Long unitId = idOf(units.get(0), "unitId", "id");
            if (unitId != null) UNIT_ID = unitId;
        } else {
            warn("单位列表为空，UNIT_ID 沿用默认值 " + UNIT_ID);
        }

        List<Map<String, Object>> currencies = listData("/api/basedata/currency/list");
        if (!currencies.isEmpty()) {
            Long currencyId = idOf(currencies.get(0), "currencyId", "id");
            if (currencyId != null) CURRENCY_ID = currencyId;
        } else {
            warn("币种列表为空，CURRENCY_ID 沿用默认值 " + CURRENCY_ID);
        }

        List<Map<String, Object>> depts = listData("/api/basedata/department/list");
        if (!depts.isEmpty()) {
            Long deptId = idOf(depts.get(0), "deptId", "departmentId", "id");
            if (deptId != null) DEPARTMENT_ID = deptId;
        } else {
            warn("部门列表为空，DEPARTMENT_ID 沿用默认值 " + DEPARTMENT_ID);
        }

        List<Map<String, Object>> employees = listData("/api/basedata/employee/list");
        if (!employees.isEmpty()) {
            Long empId = idOf(employees.get(0), "employeeId", "id");
            if (empId != null) {
                EMPLOYEE_ID = empId;
                HANDLER_ID = empId;
            }
        } else {
            warn("员工列表为空，EMPLOYEE_ID/HANDLER_ID 沿用默认值 " + EMPLOYEE_ID);
        }

        System.out.println("[InventoryTestBase] 基础资料: warehouse1=" + WAREHOUSE_ID_1 + " warehouse2=" + WAREHOUSE_ID_2
                + " material=" + MATERIAL_ID + "/" + MATERIAL_CODE + " unit=" + UNIT_ID
                + " currency=" + CURRENCY_ID + " department=" + DEPARTMENT_ID + " employee=" + EMPLOYEE_ID);
    }

    // ===================== 请求封装 =====================

    protected Response authGet(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .when()
                .get(path, pathParams);
    }

    protected Response authGetWithParams(String path, Map<String, ?> params) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .queryParams(params)
                .when()
                .get(path);
    }

    protected Response authPost(String path, Object body, Object... pathParams) {
        RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().post(path, pathParams);
    }

    protected Response authPut(String path, Object body, Object... pathParams) {
        RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().put(path, pathParams);
    }

    protected Response authDelete(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .when()
                .delete(path, pathParams);
    }

    /** 无 token 请求（用于权限测试） */
    protected Response noAuthGet(String path) {
        return RestAssured.given().when().get(path);
    }

    protected Response noAuthPost(String path, Object body) {
        RequestSpecification req = RestAssured.given().contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().post(path);
    }

    // ===================== 测试数据生成器 =====================

    protected static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis()) + SEQ.incrementAndGet();
    }

    /** 生成唯一批次号（避免不同用例库存记录相互覆盖） */
    protected static String uniqueBatchNo() {
        return "BATCH-T-" + uniqueSuffix();
    }

    // ---------- 明细构造 ----------

    /** 构造其他入库明细行（默认批次号） */
    protected Map<String, Object> buildOtherReceiptItem(BigDecimal qty) {
        return buildOtherReceiptItem(qty, uniqueBatchNo());
    }

    /** 构造其他入库明细行（指定批次号，用于库存影响测试时与出库批次对齐） */
    protected Map<String, Object> buildOtherReceiptItem(BigDecimal qty, String batchNo) {
        Map<String, Object> item = new HashMap<>();
        item.put("materialId", MATERIAL_ID);
        item.put("materialCode", MATERIAL_CODE);
        item.put("materialName", MATERIAL_NAME);
        item.put("specification", "测试规格");
        item.put("unitId", UNIT_ID);
        item.put("quantity", qty);
        item.put("unitPrice", DEFAULT_UNIT_PRICE);
        item.put("stockId", WAREHOUSE_ID_1);
        item.put("batchNo", batchNo);
        item.put("remark", "测试明细");
        return item;
    }

    /** 构造其他出库明细行（默认批次号） */
    protected Map<String, Object> buildOtherIssueItem(BigDecimal qty) {
        return buildOtherIssueItem(qty, uniqueBatchNo());
    }

    /** 构造其他出库明细行（指定批次号） */
    protected Map<String, Object> buildOtherIssueItem(BigDecimal qty, String batchNo) {
        Map<String, Object> item = new HashMap<>();
        item.put("materialId", MATERIAL_ID);
        item.put("materialCode", MATERIAL_CODE);
        item.put("materialName", MATERIAL_NAME);
        item.put("specification", "测试规格");
        item.put("unitId", UNIT_ID);
        item.put("quantity", qty);
        item.put("unitPrice", DEFAULT_UNIT_PRICE);
        item.put("stockId", WAREHOUSE_ID_1);
        item.put("batchNo", batchNo);
        item.put("remark", "测试明细");
        return item;
    }

    /** 构造生产领料明细行（默认批次号） */
    protected Map<String, Object> buildMaterialRequisitionItem(BigDecimal qty) {
        return buildMaterialRequisitionItem(qty, uniqueBatchNo());
    }

    /** 构造生产领料明细行（指定批次号） */
    protected Map<String, Object> buildMaterialRequisitionItem(BigDecimal qty, String batchNo) {
        Map<String, Object> item = new HashMap<>();
        item.put("materialId", MATERIAL_ID);
        item.put("materialCode", MATERIAL_CODE);
        item.put("materialName", MATERIAL_NAME);
        item.put("specification", "测试规格");
        item.put("unitId", UNIT_ID);
        item.put("quantity", qty);
        item.put("unitPrice", DEFAULT_UNIT_PRICE);
        item.put("stockId", WAREHOUSE_ID_1);
        item.put("batchNo", batchNo);
        item.put("remark", "测试明细");
        return item;
    }

    /** 构造调拨明细行（默认批次号） */
    protected Map<String, Object> buildStockTransferItem(BigDecimal qty, Long fromStockId, Long toStockId) {
        return buildStockTransferItem(qty, fromStockId, toStockId, uniqueBatchNo());
    }

    /** 构造调拨明细行（指定批次号） */
    protected Map<String, Object> buildStockTransferItem(BigDecimal qty, Long fromStockId, Long toStockId, String batchNo) {
        Map<String, Object> item = new HashMap<>();
        item.put("materialId", MATERIAL_ID);
        item.put("materialCode", MATERIAL_CODE);
        item.put("materialName", MATERIAL_NAME);
        item.put("specification", "测试规格");
        item.put("unitId", UNIT_ID);
        item.put("quantity", qty);
        item.put("unitPrice", DEFAULT_UNIT_PRICE);
        item.put("fromStockId", fromStockId);
        item.put("toStockId", toStockId);
        item.put("batchNo", batchNo);
        item.put("remark", "测试明细");
        return item;
    }

    /** 构造盘点明细行（默认批次号） */
    protected Map<String, Object> buildStockCheckItem(BigDecimal bookQty, BigDecimal checkQty) {
        return buildStockCheckItem(bookQty, checkQty, uniqueBatchNo());
    }

    /** 构造盘点明细行（指定批次号） */
    protected Map<String, Object> buildStockCheckItem(BigDecimal bookQty, BigDecimal checkQty, String batchNo) {
        Map<String, Object> item = new HashMap<>();
        item.put("materialId", MATERIAL_ID);
        item.put("materialCode", MATERIAL_CODE);
        item.put("materialName", MATERIAL_NAME);
        item.put("specification", "测试规格");
        item.put("unitId", UNIT_ID);
        item.put("stockId", WAREHOUSE_ID_1);
        item.put("batchNo", batchNo);
        item.put("bookQuantity", bookQty);
        item.put("checkQuantity", checkQty);
        item.put("unitPrice", DEFAULT_UNIT_PRICE);
        item.put("remark", "测试盘点明细");
        return item;
    }

    // ---------- 单据请求体构造 ----------

    /** 构造其他入库单创建请求体（含 2 条明细，用于行号校验） */
    protected Map<String, Object> buildOtherReceiptBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("receiptDate", LocalDate.now().toString());
        body.put("receiptType", "盘盈入库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-其他入库");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherReceiptItem(new BigDecimal("10")));
        items.add(buildOtherReceiptItem(new BigDecimal("5")));
        body.put("items", items);
        return body;
    }

    /** 构造其他入库单（单条明细，指定数量，用于库存影响测试） */
    protected Map<String, Object> buildOtherReceiptBodyWithQty(BigDecimal qty) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiptDate", LocalDate.now().toString());
        body.put("receiptType", "盘盈入库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-其他入库");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherReceiptItem(qty));
        body.put("items", items);
        return body;
    }

    /** 构造其他入库单（单条明细，指定数量与批次号，用于预备库存场景与出库批次对齐） */
    protected Map<String, Object> buildOtherReceiptBodyWithQtyAndBatch(BigDecimal qty, String batchNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiptDate", LocalDate.now().toString());
        body.put("receiptType", "盘盈入库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试-预备库存");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherReceiptItem(qty, batchNo));
        body.put("items", items);
        return body;
    }

    /** 构造其他出库单创建请求体（含 2 条明细） */
    protected Map<String, Object> buildOtherIssueBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("issueDate", LocalDate.now().toString());
        body.put("issueType", "盘亏出库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("departmentId", DEPARTMENT_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-其他出库");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherIssueItem(new BigDecimal("10")));
        items.add(buildOtherIssueItem(new BigDecimal("5")));
        body.put("items", items);
        return body;
    }

    /** 构造其他出库单（单条明细，指定数量） */
    protected Map<String, Object> buildOtherIssueBodyWithQty(BigDecimal qty) {
        Map<String, Object> body = new HashMap<>();
        body.put("issueDate", LocalDate.now().toString());
        body.put("issueType", "盘亏出库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("departmentId", DEPARTMENT_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-其他出库");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherIssueItem(qty));
        body.put("items", items);
        return body;
    }

    /** 构造生产领料单创建请求体（含 2 条明细） */
    protected Map<String, Object> buildMaterialRequisitionBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("requisitionDate", LocalDate.now().toString());
        body.put("workOrderNo", "MO-TEST-001");
        body.put("departmentId", DEPARTMENT_ID);
        body.put("employeeId", EMPLOYEE_ID);
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-生产领料");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildMaterialRequisitionItem(new BigDecimal("10")));
        items.add(buildMaterialRequisitionItem(new BigDecimal("5")));
        body.put("items", items);
        return body;
    }

    /** 构造调拨单创建请求体（含 2 条明细，仓库1 → 仓库2） */
    protected Map<String, Object> buildStockTransferBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("transferDate", LocalDate.now().toString());
        body.put("fromStockId", WAREHOUSE_ID_1);
        body.put("toStockId", WAREHOUSE_ID_2);
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("transferType", "仓库间调拨");
        body.put("remark", "自动化测试创建-调拨");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildStockTransferItem(new BigDecimal("10"), WAREHOUSE_ID_1, WAREHOUSE_ID_2));
        items.add(buildStockTransferItem(new BigDecimal("5"), WAREHOUSE_ID_1, WAREHOUSE_ID_2));
        body.put("items", items);
        return body;
    }

    /** 构造盘点单创建请求体（含 2 条明细，盘平） */
    protected Map<String, Object> buildStockCheckBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("checkDate", LocalDate.now().toString());
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("stockType", "全盘");
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-盘点");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildStockCheckItem(new BigDecimal("100"), new BigDecimal("100")));
        items.add(buildStockCheckItem(new BigDecimal("50"), new BigDecimal("50")));
        body.put("items", items);
        return body;
    }

    // ---------- 指定批次号的单据请求体（用于需要先预备库存的场景） ----------

    /** 构造其他出库单（2 条明细，指定批次号，与预备库存批次对齐） */
    protected Map<String, Object> buildOtherIssueBody(String batchNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("issueDate", LocalDate.now().toString());
        body.put("issueType", "盘亏出库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("departmentId", DEPARTMENT_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-其他出库");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherIssueItem(new BigDecimal("10"), batchNo));
        items.add(buildOtherIssueItem(new BigDecimal("5"), batchNo));
        body.put("items", items);
        return body;
    }

    /** 构造生产领料单（2 条明细，指定批次号） */
    protected Map<String, Object> buildMaterialRequisitionBody(String batchNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("requisitionDate", LocalDate.now().toString());
        body.put("workOrderNo", "MO-TEST-001");
        body.put("departmentId", DEPARTMENT_ID);
        body.put("employeeId", EMPLOYEE_ID);
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "自动化测试创建-生产领料");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildMaterialRequisitionItem(new BigDecimal("10"), batchNo));
        items.add(buildMaterialRequisitionItem(new BigDecimal("5"), batchNo));
        body.put("items", items);
        return body;
    }

    /** 构造调拨单（2 条明细，指定批次号，仓库1 → 仓库2） */
    protected Map<String, Object> buildStockTransferBody(String batchNo) {
        return buildStockTransferBody(batchNo, WAREHOUSE_ID_1, WAREHOUSE_ID_2);
    }

    /** 构造调拨单（2 条明细，指定批次号与调出/调入仓库） */
    protected Map<String, Object> buildStockTransferBody(String batchNo, Long fromStockId, Long toStockId) {
        Map<String, Object> body = new HashMap<>();
        body.put("transferDate", LocalDate.now().toString());
        body.put("fromStockId", fromStockId);
        body.put("toStockId", toStockId);
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("transferType", "仓库间调拨");
        body.put("remark", "自动化测试创建-调拨");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildStockTransferItem(new BigDecimal("10"), fromStockId, toStockId, batchNo));
        items.add(buildStockTransferItem(new BigDecimal("5"), fromStockId, toStockId, batchNo));
        body.put("items", items);
        return body;
    }

    // ===================== 业务辅助方法：创建并返回 ID =====================

    /** 创建其他入库单并返回 receiptId */
    protected Long createOtherReceiptAndGetId() {
        Response r = authPost("/api/inventory/other-receipts", buildOtherReceiptBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.receiptId")).longValue();
    }

    /** 创建其他出库单并返回 issueId */
    protected Long createOtherIssueAndGetId() {
        Response r = authPost("/api/inventory/other-issues", buildOtherIssueBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.issueId")).longValue();
    }

    /** 创建生产领料单并返回 requisitionId */
    protected Long createMaterialRequisitionAndGetId() {
        Response r = authPost("/api/inventory/material-requisitions", buildMaterialRequisitionBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.requisitionId")).longValue();
    }

    /** 创建调拨单并返回 transferId */
    protected Long createStockTransferAndGetId() {
        Response r = authPost("/api/inventory/stock-transfers", buildStockTransferBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.transferId")).longValue();
    }

    /** 创建盘点单并返回 checkId */
    protected Long createStockCheckAndGetId() {
        Response r = authPost("/api/inventory/stock-checks", buildStockCheckBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.checkId")).longValue();
    }

    /**
     * 预备库存：通过其他入库单创建+提交+审核，向仓库1注入指定批次号与数量的库存。
     * 用于出库/领料/调拨等扣减库存的单据在审核前先准备足量库存（batchNo 必须与扣减单据明细批次对齐）。
     * @return 新建并已生效的其他入库单 receiptId
     */
    protected Long prepareStock(String batchNo, BigDecimal qty) {
        Map<String, Object> body = buildOtherReceiptBodyWithQtyAndBatch(qty, batchNo);
        Response r = authPost("/api/inventory/other-receipts", body);
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        Long receiptId = ((Number) r.path("data.receiptId")).longValue();
        authPost("/api/inventory/other-receipts/{id}/submit", null, receiptId)
                .then().body("code", org.hamcrest.Matchers.equalTo(200));
        authPost("/api/inventory/other-receipts/{id}/approve", null, receiptId)
                .then().body("code", org.hamcrest.Matchers.equalTo(200));
        return receiptId;
    }

    // ===================== 即时库存查询辅助 =====================

    /**
     * 查询指定仓库+物料的即时库存数量总和
     * 接口: GET /api/inventory/list?stockId=&materialId=
     */
    protected BigDecimal getInventoryQuantity(Long stockId, Long materialId) {
        Response r = authGetWithParams("/api/inventory/list",
                Map.of("current", 1, "size", 200, "stockId", stockId, "materialId", materialId));
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        List<Map<String, Object>> records = r.path("data.records");
        BigDecimal total = BigDecimal.ZERO;
        if (records != null) {
            for (Map<String, Object> rec : records) {
                Object qty = rec.get("quantity");
                if (qty != null) {
                    total = total.add(new BigDecimal(qty.toString()));
                }
            }
        }
        return total;
    }

    // ===================== 通用断言辅助 =====================

    /** 断言操作成功（HTTP 200 + code=200） */
    protected void assertSuccess(Response r, String operation) {
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        System.out.println("  [PASS] " + operation);
    }

    /** 断言操作失败（code != 200） */
    protected void assertFailed(Response r, String operation) {
        int code = r.path("code");
        if (code != 200) {
            System.out.println("  [PASS] " + operation + " (预期失败, code=" + code + ")");
        } else {
            throw new AssertionError("预期操作应失败但成功了: " + operation);
        }
    }

    /** 断言状态值（优先取 data.status） */
    protected void assertStatus(Response r, String expectedStatus) {
        String actualStatus = r.path("data.status");
        if (actualStatus == null) {
            actualStatus = r.path("data.planStatus");
        }
        org.junit.jupiter.api.Assertions.assertEquals(expectedStatus, actualStatus,
                "状态不匹配，期望: " + expectedStatus + "，实际: " + actualStatus);
    }
}
