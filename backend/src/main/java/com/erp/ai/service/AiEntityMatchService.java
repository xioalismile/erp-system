package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.basedata.entity.Department;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.DepartmentService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.production.entity.Routing;
import com.erp.production.entity.WorkCenter;
import com.erp.production.service.RoutingService;
import com.erp.production.service.WorkCenterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI「人话 → 系统对象」匹配服务（唯一来源，禁止各工具各写一份）。
 *
 * <p>动机（用户 2026-10-10 二期）：客户发来的 Word/图片工单、资料里写的是**编码或名称**
 * （"物料：A-1001"、"仓库：成品仓"），而系统建单必须写**主键 ID**。历史做法是各工具自己模糊搜，
 * 口径不一（有的只搜编码、有的只搜名称、命中多条时随便取第一条）→ 建出来的单挂错对象且不报错。
 *
 * <p>统一口径（命中即返回，不猜）：
 * <ol>
 *   <li>先按**编码**精确匹配（忽略大小写）</li>
 *   <li>再按**名称**精确匹配</li>
 *   <li>最后按编码/名称**模糊**匹配：唯一命中 → 采用；多条命中 → 进 candidates 让用户/模型确认</li>
 * </ol>
 * 任何一种都可能"没找到"，此时 matched=null，由调用方决定是提示用户还是（物料）自动新建。
 *
 * <p>物料匹配沿用既有保密口径：`is_confidential=1` 的物料不参与匹配（与通用查询工具同口径）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEntityMatchService {

    private final MaterialService materialService;
    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final WarehouseService warehouseService;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final WorkCenterService workCenterService;
    private final RoutingService routingService;
    private final UnitOfMeasureService unitOfMeasureService;

    /** 支持匹配的对象类型（对外口径，供工具说明与启动自检使用） */
    public static final Set<String> SUPPORTED_TYPES = new LinkedHashSet<>(List.of(
            "material", "customer", "supplier", "warehouse", "department",
            "workcenter", "employee", "routing", "unit"
    ));

    /** 模糊匹配最多返回的候选数（防上下文爆炸） */
    private static final int MAX_CANDIDATES = 10;

    /**
     * 匹配单个对象。
     *
     * @param type    对象类型，见 {@link #SUPPORTED_TYPES}
     * @param keyword 编码或名称（用户/文件里写的原文）
     * @return { success, type, keyword, matched{id,code,name,...} | null, candidates[], message }
     */
    public Map<String, Object> match(String type, String keyword) {
        String t = type == null ? "" : type.trim().toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", t);
        result.put("keyword", keyword);

        if (!SUPPORTED_TYPES.contains(t)) {
            result.put("success", false);
            result.put("matched", null);
            result.put("message", "不支持匹配的对象类型「" + type + "」；支持：" + String.join("/", SUPPORTED_TYPES));
            return result;
        }
        if (keyword == null || keyword.isBlank()) {
            result.put("success", false);
            result.put("matched", null);
            result.put("message", "未提供要匹配的编码或名称");
            return result;
        }
        String kw = keyword.trim();

        try {
            // 1) 编码精确 → 2) 名称精确 → 3) 模糊
            List<Map<String, Object>> hits = query(t, kw, "code", true);
            if (hits.size() == 1) return hit(result, hits.get(0), "编码精确匹配");
            if (hits.size() > 1) return ambiguous(result, hits, "编码命中多条，请确认用哪一个");

            hits = query(t, kw, "name", true);
            if (hits.size() == 1) return hit(result, hits.get(0), "名称精确匹配");
            if (hits.size() > 1) return ambiguous(result, hits, "名称命中多条，请确认用哪一个");

            hits = query(t, kw, "like", false);
            if (hits.size() == 1) return hit(result, hits.get(0), "模糊唯一命中");
            if (hits.size() > 1) return ambiguous(result, hits, "有多条相近记录，请确认用哪一个");

            result.put("success", true);
            result.put("matched", null);
            result.put("candidates", List.of());
            result.put("message", "系统里没有找到「" + kw + "」对应的" + labelOf(t));
            return result;
        } catch (Exception e) {
            log.warn("[AI匹配] 失败 type={} keyword={}", t, kw, e);
            result.put("success", false);
            result.put("matched", null);
            result.put("message", "匹配「" + kw + "」时出错：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return result;
        }
    }

    /**
     * 取某物料的主工艺路线（用户在界面上选的就是这条）。
     * 优先 is_primary=1 且启用；没有主路线时回退该物料下任一启用路线；都没有返回 null。
     */
    public Routing primaryRoutingOfMaterial(Long materialId) {
        if (materialId == null) return null;
        Routing primary = routingService.getOne(
                new QueryWrapper<Routing>().eq("material_id", materialId)
                        .eq("status", "启用").eq("is_primary", 1)
                        .orderByAsc("routing_id").last("LIMIT 1"));
        if (primary != null) return primary;
        return routingService.getOne(
                new QueryWrapper<Routing>().eq("material_id", materialId)
                        .eq("status", "启用")
                        .orderByAsc("routing_id").last("LIMIT 1"));
    }

    /**
     * 取一个可用的计量单位主键（自动建物料时"基本单位"是数据库 NOT NULL 列，必须有值）。
     * 优先按传入名称/编码匹配；匹配不到时取第一个启用单位兜底，保证能落库。
     */
    public Long resolveUnitId(String unitNameOrCode) {
        if (unitNameOrCode != null && !unitNameOrCode.isBlank()) {
            Map<String, Object> m = match("unit", unitNameOrCode);
            Object matched = m.get("matched");
            if (matched instanceof Map<?, ?> row && row.get("id") instanceof Number n) {
                return n.longValue();
            }
        }
        UnitOfMeasure first = unitOfMeasureService.getOne(
                new QueryWrapper<UnitOfMeasure>().eq("status", "启用")
                        .orderByAsc("unit_id").last("LIMIT 1"));
        if (first != null) return first.getUnitId();
        UnitOfMeasure any = unitOfMeasureService.getOne(
                new QueryWrapper<UnitOfMeasure>().orderByAsc("unit_id").last("LIMIT 1"));
        return any == null ? null : any.getUnitId();
    }

    /** 按类型 + 模式取候选行（统一产出 {id,code,name}，避免各工具各自拼表） */
    private List<Map<String, Object>> query(String type, String kw, String mode, boolean exact) {
        List<?> list;
        switch (type) {
            case "material" -> {
                QueryWrapper<Material> q = new QueryWrapper<>();
                // 保密物料不参与匹配（与通用查询工具同口径）
                q.and(w -> w.ne("is_confidential", 1).or().isNull("is_confidential"));
                applyKeyword(q, "material_code", "material_name", kw, mode, exact);
                list = materialService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "customer" -> {
                QueryWrapper<Customer> q = new QueryWrapper<>();
                applyKeyword(q, "customer_code", "customer_name", kw, mode, exact);
                list = customerService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "supplier" -> {
                QueryWrapper<Supplier> q = new QueryWrapper<>();
                applyKeyword(q, "supplier_code", "supplier_name", kw, mode, exact);
                list = supplierService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "warehouse" -> {
                QueryWrapper<Warehouse> q = new QueryWrapper<>();
                applyKeyword(q, "stock_code", "stock_name", kw, mode, exact);
                list = warehouseService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "department" -> {
                QueryWrapper<Department> q = new QueryWrapper<>();
                applyKeyword(q, "dept_code", "dept_name", kw, mode, exact);
                list = departmentService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "employee" -> {
                QueryWrapper<Employee> q = new QueryWrapper<>();
                applyKeyword(q, "employee_code", "employee_name", kw, mode, exact);
                list = employeeService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "workcenter" -> {
                QueryWrapper<WorkCenter> q = new QueryWrapper<>();
                applyKeyword(q, "work_center_code", "work_center_name", kw, mode, exact);
                list = workCenterService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "routing" -> {
                QueryWrapper<Routing> q = new QueryWrapper<>();
                applyKeyword(q, "routing_code", "routing_name", kw, mode, exact);
                list = routingService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            case "unit" -> {
                QueryWrapper<UnitOfMeasure> q = new QueryWrapper<>();
                applyKeyword(q, "unit_code", "unit_name", kw, mode, exact);
                list = unitOfMeasureService.list(q.last("LIMIT " + MAX_CANDIDATES));
            }
            default -> list = List.of();
        }
        return rowsOf(type, list);
    }

    /**
     * 套用关键词条件。
     * <p>精确模式只比**目标列**（mode=name 比名称列，否则比编码列），且忽略大小写；
     * 模糊模式比"编码 OR 名称"两列，**整组包 and(...)**（历史铁律：MP 平铺 or() 不补括号会把后续条件吃掉）。
     */
    private <T> void applyKeyword(QueryWrapper<T> q, String codeCol, String nameCol,
                                  String kw, String mode, boolean exact) {
        if (exact) {
            String col = "name".equals(mode) ? nameCol : codeCol;
            q.apply("LOWER(" + col + ") = {0}", kw.toLowerCase());
        } else {
            q.and(w -> w.like(codeCol, kw).or().like(nameCol, kw));
        }
    }

    private List<Map<String, Object>> rowsOf(String type, List<?> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (list == null) return rows;
        for (Object o : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            switch (type) {
                case "material" -> {
                    Material m = (Material) o;
                    row.put("id", m.getMaterialId());
                    row.put("code", m.getMaterialCode());
                    row.put("name", m.getMaterialName());
                    row.put("spec", m.getModel());
                    row.put("unitId", m.getBaseUnitId());
                }
                case "customer" -> {
                    Customer c = (Customer) o;
                    row.put("id", c.getCustomerId());
                    row.put("code", c.getCustomerCode());
                    row.put("name", c.getCustomerName());
                }
                case "supplier" -> {
                    Supplier s = (Supplier) o;
                    row.put("id", s.getSupplierId());
                    row.put("code", s.getSupplierCode());
                    row.put("name", s.getSupplierName());
                }
                case "warehouse" -> {
                    Warehouse w = (Warehouse) o;
                    row.put("id", w.getStockId());
                    row.put("code", w.getStockCode());
                    row.put("name", w.getStockName());
                }
                case "department" -> {
                    Department d = (Department) o;
                    row.put("id", d.getDeptId());
                    row.put("code", d.getDeptCode());
                    row.put("name", d.getDeptName());
                }
                case "employee" -> {
                    Employee e = (Employee) o;
                    row.put("id", e.getEmployeeId());
                    row.put("code", e.getEmployeeCode());
                    row.put("name", e.getEmployeeName());
                }
                case "workcenter" -> {
                    WorkCenter wc = (WorkCenter) o;
                    row.put("id", wc.getWorkCenterId());
                    row.put("code", wc.getWorkCenterCode());
                    row.put("name", wc.getWorkCenterName());
                }
                case "routing" -> {
                    Routing r = (Routing) o;
                    row.put("id", r.getRoutingId());
                    row.put("code", r.getRoutingCode());
                    row.put("name", r.getRoutingName());
                    row.put("materialId", r.getMaterialId());
                    row.put("materialCode", r.getMaterialCode());
                    row.put("materialName", r.getMaterialName());
                }
                case "unit" -> {
                    UnitOfMeasure u = (UnitOfMeasure) o;
                    row.put("id", u.getUnitId());
                    row.put("code", u.getUnitCode());
                    row.put("name", u.getUnitName());
                }
                default -> { }
            }
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> hit(Map<String, Object> result, Map<String, Object> row, String how) {
        result.put("success", true);
        result.put("matched", row);
        result.put("candidates", List.of());
        result.put("message", how + "：" + row.get("code") + " " + row.get("name"));
        return result;
    }

    private Map<String, Object> ambiguous(Map<String, Object> result, List<Map<String, Object>> rows, String msg) {
        result.put("success", true);
        result.put("matched", null);
        result.put("candidates", rows);
        result.put("message", msg + "（共 " + rows.size() + " 条）");
        return result;
    }

    private String labelOf(String type) {
        return switch (type) {
            case "material" -> "物料";
            case "customer" -> "客户";
            case "supplier" -> "供应商";
            case "warehouse" -> "仓库";
            case "department" -> "部门";
            case "employee" -> "员工";
            case "workcenter" -> "工作中心";
            case "routing" -> "工艺路线";
            case "unit" -> "计量单位";
            default -> "对象";
        };
    }
}
