package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.dto.InventoryQueryDTO;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.InventoryTransactionService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.inventory.service.VirtualStockService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    /** 库存列表可排序列映射：关联虚字段映射为子查询表达式，真实列直接用列名 */
    private static final Map<String, String> INVENTORY_SORTABLE = Map.ofEntries(
            Map.entry("material_code", "(SELECT m.material_code FROM material m WHERE m.material_id = inventory_current.material_id)"),
            Map.entry("material_name", "(SELECT m.material_name FROM material m WHERE m.material_id = inventory_current.material_id)"),
            Map.entry("model", "(SELECT m.model FROM material m WHERE m.material_id = inventory_current.material_id)"),
            Map.entry("stock_name", "(SELECT w.stock_name FROM warehouse w WHERE w.stock_id = inventory_current.stock_id)"),
            Map.entry("unit_name", "(SELECT u.unit_name FROM unit_of_measure u WHERE u.unit_id = (SELECT m.stock_unit_id FROM material m WHERE m.material_id = inventory_current.material_id))"),
            Map.entry("batch_no", "batch_no"),
            Map.entry("quantity", "quantity"),
            Map.entry("available_quantity", "available_quantity"),
            Map.entry("unit_cost", "unit_cost"),
            Map.entry("total_cost", "total_cost"));

    private final InventoryService inventoryService;
    private final InventoryTransactionService transactionService;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final MaterialCostService materialCostService;
    private final VirtualStockService virtualStockService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;
    /** 台账只存单据号文本，需按单号反查单据主键才能支持「单据号 → 来源单据」跳转 */
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.erp.common.bill.link.BillLinkRegistry billLinkRegistry;
    /** 导出：按"页面当前可见列"写出 Excel（列与页面一致是本次修复的硬要求） */
    private final com.erp.export.service.ExcelService excelService;
    /** 导出：物料自定义字段值（EAV 表），与列表页同一取数口径 */
    private final com.erp.customfield.service.CustomFieldService customFieldService;

    /**
     * 分页查询即时库存
     * 关联查询物料（编码/名称/规格/主图/单位/最小库存）和仓库（编码/名称）信息
     * 数据权限（2026-09-02 体系落地）：按可见仓库过滤——
     * 可见仓库 = 数据范围内的仓库（created_by 按角色 data_scope） ∪ 员工权限补充白名单仓库；
     * 数据范围=全部 → 不过滤；无任何可见仓库 → 返回空列表。
     */
    @GetMapping("/list")
    public Result<PageResult<Inventory>> list(InventoryQueryDTO q, jakarta.servlet.http.HttpServletRequest request) {
        if (q == null) q = new InventoryQueryDTO();
        int current = q.getCurrent() == null ? 1 : q.getCurrent();
        int size = q.getSize() == null ? 20 : q.getSize();
        // 仓库数据权限过滤
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(scopeCtx);
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            // 无任何可见仓库：直接返回空页，不再查询
            return Result.success(PageResult.of(List.of(), 0L, current, size));
        }
        QueryWrapper<Inventory> wrapper = buildInventoryWrapper(q, visibleStockIds);
        if (wrapper == null) {
            // 用户指定的仓库不在其可见范围内：与"无任何可见仓库"同口径 → 空页
            return Result.success(PageResult.of(List.of(), 0L, current, size));
        }
        // 条件构造已抽到 buildInventoryWrapper（列表 / 导出共用同一段代码，禁止各写一份）
        Page<Inventory> page = new Page<>(current, size);
        Page<Inventory> result = inventoryService.page(page, wrapper);

        // 批量填充物料、仓库、单位关联信息
        fillRelatedInfo(result.getRecords());

        return Result.success(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /**
     * 即时库存"列表 / 导出"共用的查询条件构造（**必须共用，禁止各写一份**）。
     *
     * <p>抽取原因（2026-09-22 用户反馈"报表里看到的和导出不一致"）：原先导出另走一条取数链路
     * （{@code /api/export/data/inventory}，不套任何筛选、固定 12 列）→ 必然漂移：页面勾的筛选条件、
     * 页面上的物料自定义字段列、页面已过滤的脏行，在导出里全都对不上。</p>
     *
     * @return null = 当前条件下必然无数据（用户指定的仓库不在其可见仓库范围内）
     */
    private QueryWrapper<Inventory> buildInventoryWrapper(InventoryQueryDTO q, List<Long> visibleStockIds) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        if (visibleStockIds != null) {
            if (q.getStockId() != null) {
                // 用户指定了仓库：必须在其可见仓库范围内
                if (!visibleStockIds.contains(q.getStockId())) return null;
            } else {
                wrapper.in("stock_id", visibleStockIds);
            }
        }
        if (q.getStockId() != null && visibleStockIds == null) wrapper.eq("stock_id", q.getStockId());
        if (q.getMaterialId() != null) wrapper.eq("material_id", q.getMaterialId());
        if (q.getBatchNo() != null && !q.getBatchNo().isBlank()) wrapper.eq("batch_no", q.getBatchNo());
        // 物料关键字（列表页通用口径）：编码或名称模糊匹配。
        // 两个 LIKE 组在子查询内部，整体作为一个条件 AND 到其它条件上——不会绕过仓库/自定义字段筛选
        if (q.getMaterialKeyword() != null && !q.getMaterialKeyword().isBlank()) {
            wrapper.apply("material_id IN (SELECT material_id FROM material WHERE material_code ILIKE {0} OR material_name ILIKE {0})",
                    "%" + q.getMaterialKeyword().trim() + "%");
        }
        // 兼容旧参数：单独传名称 / 编码时也真正生效
        if (q.getMaterialName() != null && !q.getMaterialName().isBlank()) {
            wrapper.apply("material_id IN (SELECT material_id FROM material WHERE material_name ILIKE {0})",
                    "%" + q.getMaterialName().trim() + "%");
        }
        if (q.getMaterialCode() != null && !q.getMaterialCode().isBlank()) {
            wrapper.apply("material_id IN (SELECT material_id FROM material WHERE material_code ILIKE {0})",
                    "%" + q.getMaterialCode().trim() + "%");
        }
        // 物料自定义字段搜索（设置中心勾了「可搜索」的字段；即时库存按 material_id 关联物料的自定义字段值）
        CustomFieldSearchHelper.apply(wrapper, "material", q.getCustomSearch(), "material_id");
        if (Boolean.TRUE.equals(q.getOnlyPositive())) wrapper.gt("quantity", 0);
        if (Boolean.TRUE.equals(q.getZeroOnly())) wrapper.eq("quantity", 0);
        // 数据看板穿透过滤（可选；全部参数化静态 SQL，不拼接用户输入）
        if (q.getMaterialType() != null && !q.getMaterialType().isBlank()) {
            wrapper.apply("material_id IN (SELECT material_id FROM material WHERE material_type = {0})", q.getMaterialType());
        }
        if (q.getLineId() != null) {
            // 产品线 = 一级分类及其子孙分类（递归 CTE）
            wrapper.apply("material_id IN (WITH RECURSIVE c AS (SELECT category_id FROM material_category WHERE category_id = {0} "
                    + "UNION ALL SELECT mc.category_id FROM material_category mc JOIN c ON mc.parent_id = c.category_id) "
                    + "SELECT m.material_id FROM material m JOIN c ON m.category_id = c.category_id)", q.getLineId());
        }
        if (q.getAgeOver() != null && q.getAgeOver() > 0) {
            wrapper.isNotNull("last_in_date");
            wrapper.apply("last_in_date < {0}", java.time.LocalDate.now().minusDays(q.getAgeOver()));
        }
        if (Boolean.TRUE.equals(q.getStaleOnly())) {
            // 呆滞 = 库龄超 180 天 且 从未出库
            wrapper.isNotNull("last_in_date");
            wrapper.apply("last_in_date < {0}", java.time.LocalDate.now().minusDays(180));
            wrapper.apply("NOT EXISTS (SELECT 1 FROM inventory_ledger l WHERE l.material_id = inventory_current.material_id AND l.out_quantity > 0)");
        }
        if (Boolean.TRUE.equals(q.getBelowMinStock())) {
            // 低于安全库存 = 现存量 < 物料安全库存
            wrapper.apply("quantity < (SELECT COALESCE(m.min_stock, 0) FROM material m WHERE m.material_id = inventory_current.material_id)");
        }
        // 2026-09-22 新增：物料已不存在的孤儿行（物料被删 / 测试数据残留）不是有效库存 ——
        // 列表与导出统一过滤，根治"导出里出现物料与仓库全空的空行"（实测本地 26 行里有 24 行物料已删）
        wrapper.apply("EXISTS (SELECT 1 FROM material m WHERE m.material_id = inventory_current.material_id)");
        // 排序：白名单映射（关联字段走子查询），无排序参数时默认主键倒序
        String sortCol = SortHelper.camelToSnake(q.getSortBy() == null ? "" : q.getSortBy().trim());
        boolean asc = "asc".equalsIgnoreCase(q.getSortOrder() == null ? "" : q.getSortOrder().trim());
        boolean desc = "desc".equalsIgnoreCase(q.getSortOrder() == null ? "" : q.getSortOrder().trim());
        String sortExpr = INVENTORY_SORTABLE.get(sortCol);
        if (sortExpr == null || (!asc && !desc)) {
            wrapper.orderByDesc("inventory_id");
        } else {
            wrapper.orderBy(true, asc, sortExpr);
        }
        return wrapper;
    }

    /** 导出全量时的安全上限（与既有导出链路同口径） */
    private static final int EXPORT_MAX_ROWS = 100000;

    /**
     * 即时库存导出（2026-09-22）：**与列表页同源同列**。
     * 条件 = 同一个 {@link #buildInventoryWrapper}；列 = 前端传来的"当前可见列"（含物料自定义字段），
     * 逐列按页面显示口径输出 → 做到"页面上看到什么，导出就带什么"。
     */
    @PostMapping("/export")
    public void export(@RequestBody(required = false) InventoryQueryDTO q,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (q == null) q = new InventoryQueryDTO();
        List<Map<String, Object>> fields = q.getFields() == null ? List.of() : q.getFields();
        // 未指定列：保持历史行为（固定 12 列全量现存量），兼容其它调用方
        if (fields.isEmpty()) {
            excelService.exportInventoryCurrent(response, false);
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            labels.add(f.get("label") == null ? "" : String.valueOf(f.get("label")));
        }

        List<Inventory> records = new ArrayList<>();
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(dataScopeResolver.resolve(request));
        if (visibleStockIds == null || !visibleStockIds.isEmpty()) {
            QueryWrapper<Inventory> wrapper = buildInventoryWrapper(q, visibleStockIds);
            if (wrapper != null) {
                records = inventoryService.page(new Page<>(1, EXPORT_MAX_ROWS), wrapper).getRecords();
            }
        }
        fillRelatedInfo(records);

        // 物料自定义字段值：与列表页同一个 EAV 口径（按 material_id 批量取）
        Map<Long, Map<String, Object>> customValues = new HashMap<>();
        List<Long> materialIds = new ArrayList<>();
        for (Inventory inv : records) {
            if (inv.getMaterialId() != null && !materialIds.contains(inv.getMaterialId())) {
                materialIds.add(inv.getMaterialId());
            }
        }
        if (!materialIds.isEmpty()) {
            try {
                customValues = customFieldService.batchGetFieldValues("material", materialIds);
            } catch (Exception e) {
                log.warn("[即时库存导出] 读取物料自定义字段值失败（降级为空，不阻断导出）: {}", e.getMessage());
            }
        }

        List<List<Object>> rows = new ArrayList<>();
        for (Inventory inv : records) {
            Map<String, Object> cf = customValues.getOrDefault(inv.getMaterialId(), Map.of());
            List<Object> row = new ArrayList<>();
            for (Map<String, Object> f : fields) {
                row.add(exportCell(inv, cf, String.valueOf(f.get("key"))));
            }
            rows.add(row);
        }
        excelService.writeSheetByLabels(response, "即时库存数据", "即时库存", labels, rows);
    }

    /** 导出取值：先按列表静态列取（与页面列定义一一对应），取不到再当作物料自定义字段取值 */
    private Object exportCell(Inventory inv, Map<String, Object> customValues, String key) {
        switch (key) {
            case "materialCode":    return nz(inv.getMaterialCode());
            case "materialName":    return nz(inv.getMaterialName());
            case "model":           return nz(inv.getModel());
            case "stockName":       return nz(inv.getStockName());
            case "stockCode":       return nz(inv.getStockCode());
            case "batchNo":         return nz(inv.getBatchNo());
            case "quantity":        return scale4(inv.getQuantity());
            case "availableQuantity": return scale4(inv.getAvailableQuantity());
            case "reservedQuantity":  return scale4(inv.getReservedQuantity());
            case "virtualAvailable":  return scale4(inv.getVirtualAvailable());
            case "unitName":        return nz(inv.getUnitName());
            case "unitCost":        return scale4(inv.getUnitCost());
            case "latestCalcCost":  return scale4(inv.getLatestCalcCost());
            // 页面安全库存为空显示 '-'；导出写空单元格（不写占位符，遵循"数据为空即空白"）
            case "safetyStock":     return inv.getSafetyStock() == null ? "" : scale4(inv.getSafetyStock());
            case "_status":         return inventoryStatusText(inv);
            default: {
                Object v = customValues.get(key);
                return v == null ? "" : v;
            }
        }
    }

    /** 与列表页 statusText 同一口径（页面上"状态"列显示什么，导出就是什么） */
    private static String inventoryStatusText(Inventory inv) {
        if (inv.getQuantity() == null) return "未知";
        if (inv.getSafetyStock() != null && inv.getQuantity().compareTo(inv.getSafetyStock()) < 0) return "不足";
        if (inv.getQuantity().compareTo(java.math.BigDecimal.ZERO) == 0) return "零库存";
        return "正常";
    }

    private static Object nz(String s) { return s == null ? "" : s; }

    /** 页面数字列统一显示 4 位小数 → 导出同口径（保留数值型，Excel 可直接求和） */
    private static java.math.BigDecimal scale4(java.math.BigDecimal v) {
        return (v == null ? java.math.BigDecimal.ZERO : v).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 即时库存顶部统计卡片（独立请求；与 /inventory/list 同数据权限口径）
     * 返回：materialCount 现存记录数、positiveCount 有库存、zeroCount 零库存、belowMinCount 低于安全库存
     * 说明：卡片一律取接口值，页面不再用"当前页行"自行统计（列表分页时那样会算错）
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(dataScopeResolver.resolve(request));
        Map<String, Object> data = new HashMap<>();
        // 无可见仓库：与列表返回空页口径一致，卡片全部计 0
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            data.put("materialCount", 0L);
            data.put("positiveCount", 0L);
            data.put("zeroCount", 0L);
            data.put("belowMinCount", 0L);
            return Result.success(data);
        }
        data.put("materialCount", inventoryService.count(inventoryScopeWrapper(visibleStockIds)));
        data.put("positiveCount", inventoryService.count(inventoryScopeWrapper(visibleStockIds).gt("quantity", 0)));
        data.put("zeroCount", inventoryService.count(inventoryScopeWrapper(visibleStockIds).eq("quantity", 0)));
        // 低于安全库存 = 现存量 < 物料安全库存（与列表 belowMinStock 条件同一表达式）
        data.put("belowMinCount", inventoryService.count(inventoryScopeWrapper(visibleStockIds)
                .apply("quantity < (SELECT COALESCE(m.min_stock, 0) FROM material m WHERE m.material_id = inventory_current.material_id)")));
        return Result.success(data);
    }

    /** 即时库存统计的基础条件：按可见仓库范围过滤（与列表同口径） */
    private QueryWrapper<Inventory> inventoryScopeWrapper(List<Long> visibleStockIds) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        if (visibleStockIds != null) {
            wrapper.in("stock_id", visibleStockIds);
        }
        // 与列表/导出同一口径：物料已不存在的孤儿行不计入卡片（否则"物料总数"会比列表条数多，用户会认为数字对不上）
        wrapper.apply("EXISTS (SELECT 1 FROM material m WHERE m.material_id = inventory_current.material_id)");
        return wrapper;
    }

    /**
     * 库存预警列表 (库存数量 < 物料最小库存)
     * 数据权限（2026-09-02）：按可见仓库过滤
     */
    @GetMapping("/warning")
    public Result<List<Inventory>> warning(jakarta.servlet.http.HttpServletRequest request) {
        List<Inventory> list = inventoryService.getWarningList();
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(
                dataScopeResolver.resolve(request));
        if (visibleStockIds != null) {
            list = list.stream()
                    .filter(i -> i.getStockId() != null && visibleStockIds.contains(i.getStockId()))
                    .toList();
        }
        fillRelatedInfo(list);
        return Result.success(list);
    }

    /**
     * 【2026-09-15 单据明细行库存展示】按物料批量查询库存汇总（跨仓库合计，不区分仓库）。
     *
     * 返回：库存量 SUM(quantity) / 可用量 SUM(available_quantity) / 预计可用量 = 可用量 - 订单占用。
     * 数据权限（与 /inventory/list 同口径）：按可见仓库过滤；无任何可见仓库 → 返回空列表。
     * 入参 materialIds 去重且最多 500 个（防超长 IN）；一次查询，供前端选料后批量回填。
     */
    @GetMapping("/material-stock-summary")
    public Result<List<com.erp.inventory.dto.MaterialStockSummaryDTO>> materialStockSummary(
            @RequestParam(required = false) List<Long> materialIds,
            jakarta.servlet.http.HttpServletRequest request) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<Long> ids = materialIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(500)
                .toList();
        if (ids.isEmpty()) {
            return Result.success(List.of());
        }
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(
                dataScopeResolver.resolve(request));
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            return Result.success(List.of());
        }
        Map<Long, com.erp.inventory.dto.MaterialStockSummaryDTO> summary =
                inventoryService.batchGetStockSummary(ids, visibleStockIds);
        // 订单占用（生效销售订单未发货数量合计，按物料全局统计）
        Map<Long, java.math.BigDecimal> reservedMap =
                virtualStockService.batchGetReservedQuantity(ids);
        List<com.erp.inventory.dto.MaterialStockSummaryDTO> list = new java.util.ArrayList<>(ids.size());
        for (Long id : ids) {
            com.erp.inventory.dto.MaterialStockSummaryDTO dto = summary.get(id);
            if (dto == null) {
                dto = new com.erp.inventory.dto.MaterialStockSummaryDTO();
                dto.setMaterialId(id);
            }
            dto.setExpectedAvailableQuantity(dto.getAvailableQuantity()
                    .subtract(reservedMap.getOrDefault(id, java.math.BigDecimal.ZERO)));
            list.add(dto);
        }
        return Result.success(list);
    }

    /**
     * 物料库存流水
     * 数据权限（2026-09-02）：按可见仓库过滤
     */
    @GetMapping("/transactions")
    public Result<List<InventoryTransaction>> transactions(
            @RequestParam Long materialId,
            @RequestParam(required = false) Long stockId,
            jakarta.servlet.http.HttpServletRequest request) {
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(
                dataScopeResolver.resolve(request));
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            return Result.success(List.of());
        }
        if (visibleStockIds != null && stockId != null && !visibleStockIds.contains(stockId)) {
            return Result.success(List.of());
        }
        return Result.success(transactionService.getMaterialTransactions(materialId, stockId));
    }

    /**
     * 单据库存流水
     */
    @GetMapping("/transactions/{transNo}")
    public Result<List<InventoryTransaction>> documentTransactions(@PathVariable String transNo) {
        return Result.success(transactionService.getDocumentTransactions(transNo));
    }

    /**
     * 库存流水分页查询（库存流水表页面专用）
     * 支持按日期范围、单据类型、物料、仓库、批次号、来源单据、关键字多条件筛选
     * 返回结果带物料编码/名称、仓库名称、单位名称等关联展示字段
     */
    @GetMapping("/flow/list")
    public Result<PageResult<InventoryTransaction>> flowList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String transType,
            @RequestParam(required = false) Long stockId,
            @RequestParam(required = false) Long materialId,
            // 列表页通用口径：物料编码/名称一个框模糊匹配（子查询内成组 OR）
            @RequestParam(required = false) String materialKeyword,
            // 列表页通用口径：自定义字段搜索（JSON {fieldKey: 值}），按物料的自定义字段过滤
            @RequestParam(required = false) String customSearch,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String sourceBillType,
            @RequestParam(required = false) String keyword,
            jakarta.servlet.http.HttpServletRequest request) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 仓库数据权限（2026-09-02）
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(
                dataScopeResolver.resolve(request));
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            return Result.success(PageResult.of(List.of(), 0L, p[0], p[1]));
        }
        QueryWrapper<InventoryTransaction> wrapper = new QueryWrapper<>();
        if (visibleStockIds != null) {
            if (stockId != null) {
                if (!visibleStockIds.contains(stockId)) {
                    return Result.success(PageResult.of(List.of(), 0L, p[0], p[1]));
                }
            } else {
                wrapper.in("stock_id", visibleStockIds);
            }
        }
        if (visibleStockIds == null && stockId != null) {
            wrapper.eq("stock_id", stockId);
        }
        // 日期区间（本页真实列 trans_date）；非法/空日期一律忽略，不阻断查询
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "trans_date", startDate, endDate);
        if (transType != null && !transType.isBlank()) {
            wrapper.eq("trans_type", transType);
        }
        if (stockId != null) {
            wrapper.eq("stock_id", stockId);
        }
        if (materialId != null) {
            wrapper.eq("material_id", materialId);
        }
        // 物料关键字（列表页通用口径）：编码或名称模糊匹配；子查询内成组，整体 AND 到其它条件
        // （2026-09-16 修复：此前前端传的 materialKeyword 后端没人接，物料筛选点了没反应）
        if (materialKeyword != null && !materialKeyword.isBlank()) {
            wrapper.apply("material_id IN (SELECT material_id FROM material WHERE material_code ILIKE {0} OR material_name ILIKE {0})",
                    "%" + materialKeyword.trim() + "%");
        }
        // 物料自定义字段搜索（设置中心勾了「可搜索」的字段）
        CustomFieldSearchHelper.apply(wrapper, "material", customSearch, "material_id");
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.like("batch_no", batchNo);
        }
        if (sourceBillType != null && !sourceBillType.isBlank()) {
            wrapper.eq("source_bill_type", sourceBillType);
        }
        // 关键字匹配：单据号 / 来源单据号 / 批次号 / 备注（统一走 applyKeywordGroup，强制成组）
        com.erp.common.query.ListQuerySupport.applyKeywordGroup(wrapper, keyword,
                "trans_no", "source_bill_no", "batch_no", "remark");
        wrapper.orderByDesc("trans_date", "ledger_id");
        Page<InventoryTransaction> pageResult = new Page<>(p[0], p[1]);
        Page<InventoryTransaction> result = transactionService.page(pageResult, wrapper);
        // 填充关联展示字段
        fillTransactionRelatedInfo(result.getRecords());
        // 填充「单据号 → 来源单据主键」跳转信息（用户在流水里点单据号可直接打开对应单据）
        fillBillJumpRefs(result.getRecords());
        return Result.success(PageResult.of(result.getRecords(), result.getTotal(), p[0], p[1]));
    }

    /**
     * 库存流水顶部统计卡片（与 /flow/list 同数据权限口径：按可见仓库过滤）
     * 返回：totalCount 流水总数 / inCount 入库笔数 / outCount 出库笔数 / monthInAmount 本月入库金额
     */
    @GetMapping("/flow/dashboard")
    public Result<Map<String, Object>> flowDashboard(jakarta.servlet.http.HttpServletRequest request) {
        List<Long> visibleStockIds = dataScopeResolver.visibleWarehouseIds(
                dataScopeResolver.resolve(request));
        Map<String, Object> data = new HashMap<>();
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            // 无任何可见仓库：全部为 0，不越权统计
            data.put("totalCount", 0L);
            data.put("inCount", 0L);
            data.put("outCount", 0L);
            data.put("monthInAmount", java.math.BigDecimal.ZERO);
            return Result.success(data);
        }
        QueryWrapper<InventoryTransaction> totalW = new QueryWrapper<>();
        if (visibleStockIds != null) totalW.in("stock_id", visibleStockIds);
        long totalCount = transactionService.count(totalW);

        QueryWrapper<InventoryTransaction> inW = new QueryWrapper<InventoryTransaction>().gt("in_quantity", 0);
        if (visibleStockIds != null) inW.in("stock_id", visibleStockIds);
        long inCount = transactionService.count(inW);

        QueryWrapper<InventoryTransaction> outW = new QueryWrapper<InventoryTransaction>().gt("out_quantity", 0);
        if (visibleStockIds != null) outW.in("stock_id", visibleStockIds);
        long outCount = transactionService.count(outW);

        // 本月入库金额：SUM(in_amount)（本月 1 号至当月最后一天，与卡片筛选区间一致，按 trans_date）
        QueryWrapper<InventoryTransaction> amtW = new QueryWrapper<>();
        amtW.select("COALESCE(SUM(COALESCE(in_amount,0)),0) AS month_in_amount");
        if (visibleStockIds != null) amtW.in("stock_id", visibleStockIds);
        com.erp.common.query.ListQuerySupport.applyDateRange(amtW, "trans_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        List<Map<String, Object>> amtRows = transactionService.listMaps(amtW);
        java.math.BigDecimal monthInAmount = (amtRows == null || amtRows.isEmpty()
                || amtRows.get(0).get("month_in_amount") == null)
                ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal(amtRows.get(0).get("month_in_amount").toString());

        data.put("totalCount", totalCount);
        data.put("inCount", inCount);
        data.put("outCount", outCount);
        data.put("monthInAmount", monthInAmount);
        return Result.success(data);
    }

    /**
     * 批量填充库存流水的关联展示字段：物料编码/名称/规格、仓库名称、单位名称
     */
    private void fillTransactionRelatedInfo(List<InventoryTransaction> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        java.util.Set<Long> materialIds = new java.util.HashSet<>();
        java.util.Set<Long> stockIds = new java.util.HashSet<>();
        java.util.Set<Long> unitIds = new java.util.HashSet<>();
        for (InventoryTransaction tx : records) {
            if (tx.getMaterialId() != null) materialIds.add(tx.getMaterialId());
            if (tx.getStockId() != null) stockIds.add(tx.getStockId());
        }

        Map<Long, Material> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<Material> materials = materialService.listByIds(materialIds);
            for (Material m : materials) {
                materialMap.put(m.getMaterialId(), m);
                if (m.getBaseUnitId() != null) unitIds.add(m.getBaseUnitId());
            }
        }

        Map<Long, Warehouse> warehouseMap = new HashMap<>();
        if (!stockIds.isEmpty()) {
            List<Warehouse> warehouses = warehouseService.listByIds(stockIds);
            for (Warehouse w : warehouses) {
                warehouseMap.put(w.getStockId(), w);
            }
        }

        Map<Long, UnitOfMeasure> unitMap = new HashMap<>();
        if (!unitIds.isEmpty()) {
            List<UnitOfMeasure> units = unitOfMeasureService.listByIds(unitIds);
            for (UnitOfMeasure u : units) {
                unitMap.put(u.getUnitId(), u);
            }
        }

        for (InventoryTransaction tx : records) {
            Material m = materialMap.get(tx.getMaterialId());
            if (m != null) {
                tx.setMaterialCode(m.getMaterialCode());
                tx.setMaterialName(m.getMaterialName());
                tx.setSpecification(m.getModel());
                UnitOfMeasure unit = unitMap.get(m.getBaseUnitId());
                if (unit != null) {
                    tx.setUnitName(unit.getUnitName());
                }
            }
            Warehouse w = warehouseMap.get(tx.getStockId());
            if (w != null) {
                tx.setStockName(w.getStockName());
                tx.setStockCode(w.getStockCode());
            }
        }
    }

    /**
     * 填充「单据号 → 来源单据主键」跳转信息（2026-09-20）
     * <p>台账表只存 trans_no 文本、不存单据主键（写入侧 38 处调用均未传 id），
     * 因此这里按 trans_type 归一到单据关联注册表的 docType，再用 trans_no 反查该单据主键。
     * 反查不到（单据已删/单号口径不符）时留空 → 前端不显示可点样式，不影响列表其它内容。</p>
     * <p>安全：表名/列名取自注册表代码常量，单号只作为绑定参数，不拼接进 SQL。</p>
     */
    private void fillBillJumpRefs(List<InventoryTransaction> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 同一页里同一张单据常有多行流水 → 同单号只查一次
        Map<String, Long> cache = new HashMap<>();
        for (InventoryTransaction tx : records) {
            String docType = jumpDocTypeOf(tx.getTransType());
            String no = tx.getTransNo();
            if (docType == null || no == null || no.isBlank()) {
                continue;
            }
            com.erp.common.bill.link.BillLinkRegistry.DocDef def = resolveJumpDocDef(docType);
            if (def == null || def.table == null || def.pkCol == null || def.noCol == null) {
                continue;
            }
            String cacheKey = docType + "|" + no;
            Long id = cache.get(cacheKey);
            if (id == null && !cache.containsKey(cacheKey)) {
                try {
                    id = jdbcTemplate.query(
                            "SELECT " + def.pkCol + " FROM " + def.table + " WHERE " + def.noCol + " = ? LIMIT 1",
                            rs -> rs.next() ? Long.valueOf(rs.getLong(1)) : null,
                            no);
                } catch (Exception e) {
                    // 反查失败不影响流水列表：该行单据号退化为纯文本
                    log.warn("库存流水单据跳转反查失败 docType={} no={}: {}", docType, no, e.getMessage());
                    id = null;
                }
                cache.put(cacheKey, id);
            }
            if (id != null) {
                tx.setBillDocType(docType);
                tx.setBillId(id);
            }
        }
    }

    /**
     * 取「反查主键」用的单据定义：注册表有该 docType 直接用；
     * 生产退料/补料（MATERIAL_RETURN / MATERIAL_SUPPLEMENT）与领料单共用 material_requisition 表，
     * 注册表只登记了 MATERIAL_REQUISITION，故这两类借用领料单的表/主键/单号列定义（仅用于反查主键，
     * 下发给前端的 docType 仍是各自编码，保证跳到「退料单/补料单」页面而不是领料单页面）。
     */
    private com.erp.common.bill.link.BillLinkRegistry.DocDef resolveJumpDocDef(String docType) {
        com.erp.common.bill.link.BillLinkRegistry.DocDef def = billLinkRegistry.doc(docType);
        if (def != null) {
            return def;
        }
        if ("MATERIAL_RETURN".equals(docType) || "MATERIAL_SUPPLEMENT".equals(docType)) {
            return billLinkRegistry.doc("MATERIAL_REQUISITION");
        }
        return null;
    }

    /**
     * 台账 trans_type → 单据关联注册表 docType（两套编码口径不同，需归一）
     * 用前缀匹配以覆盖各单据的 _REVERSE（红冲/反审核）等派生取值。
     */
    private static String jumpDocTypeOf(String transType) {
        if (transType == null || transType.isBlank()) {
            return null;
        }
        String s = transType.trim().toUpperCase();
        if (s.startsWith("PURCHASE_IN")) return "PURCHASE_INBOUND";
        if (s.startsWith("PURCHASE_RETURN")) return "PURCHASE_RETURN";
        if (s.startsWith("SALE_OUT")) return "SALE_DELIVERY";
        if (s.startsWith("SALE_RETURN")) return "SALE_RETURN";
        if (s.startsWith("OTHER_IN")) return "OTHER_RECEIPT";
        if (s.startsWith("OTHER_OUT")) return "OTHER_ISSUE";
        if (s.startsWith("STOCK_TRANSFER")) return "STOCK_TRANSFER";
        if (s.startsWith("STOCK_CHECK")) return "STOCK_CHECK";
        if (s.startsWith("PRODUCTION_RECEIPT")) return "PRODUCTION_RECEIPT";
        // 领料/退料/补料三类共用 material_requisition 表，但页面各不相同：
        // 必须按各自编码返回（原先一律返回 MATERIAL_REQUISITION → 点退料单号会打开「领料单」页且 id 对不上）
        if (s.startsWith("MATERIAL_RETURN")) return "MATERIAL_RETURN";
        if (s.startsWith("MATERIAL_SUPPLEMENT")) return "MATERIAL_SUPPLEMENT";
        if (s.startsWith("MATERIAL_")) return "MATERIAL_REQUISITION";
        if (s.startsWith("SUBCONTRACT_INBOUND")) return "SUBCONTRACT_RECEIPT";
        if (s.startsWith("SUBCONTRACT_PICK")) return "SUBCONTRACT_DELIVERY";
        if (s.startsWith("SUBCONTRACT_RETURN")) return "SUBCONTRACT_RETURN";
        if (s.startsWith("SUBCONTRACT_SUPPLEMENT")) return "SUBCONTRACT_SUPPLEMENT";
        // 组装/拆卸单也是库存内部加工单，台账前缀 ASSEMBLE_* / DISASSEMBLE_*（含 _CANCEL 回冲派生）
        if (s.startsWith("ASSEMBLE_")) return "PRODUCE_ASSEMBLE";
        if (s.startsWith("DISASSEMBLE_")) return "PRODUCE_DISASSEMBLE";
        if (s.startsWith("FREE_SAMPLE")) return "FREE_SAMPLE";
        return null;
    }

    /**
     * 批量填充库存记录的关联展示字段：物料编码/名称/规格/主图/单位/最小库存、仓库编码/名称
     */
    private void fillRelatedInfo(List<Inventory> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 收集所有需要查询的物料ID和仓库ID
        java.util.Set<Long> materialIds = new java.util.HashSet<>();
        java.util.Set<Long> stockIds = new java.util.HashSet<>();
        java.util.Set<Long> unitIds = new java.util.HashSet<>();
        for (Inventory inv : records) {
            if (inv.getMaterialId() != null) materialIds.add(inv.getMaterialId());
            if (inv.getStockId() != null) stockIds.add(inv.getStockId());
        }

        // 批量查询物料
        Map<Long, Material> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<Material> materials = materialService.listByIds(materialIds);
            for (Material m : materials) {
                materialMap.put(m.getMaterialId(), m);
                if (m.getBaseUnitId() != null) unitIds.add(m.getBaseUnitId());
            }
        }

        // 批量查询仓库
        Map<Long, Warehouse> warehouseMap = new HashMap<>();
        if (!stockIds.isEmpty()) {
            List<Warehouse> warehouses = warehouseService.listByIds(stockIds);
            for (Warehouse w : warehouses) {
                warehouseMap.put(w.getStockId(), w);
            }
        }

        // 批量查询单位
        Map<Long, UnitOfMeasure> unitMap = new HashMap<>();
        if (!unitIds.isEmpty()) {
            List<UnitOfMeasure> units = unitOfMeasureService.listByIds(unitIds);
            for (UnitOfMeasure u : units) {
                unitMap.put(u.getUnitId(), u);
            }
        }

        // 批量查询最新核算成本（期间核算口径，来自cost_calculation表"已确认"记录）
        Map<Long, java.math.BigDecimal> calcCostMap = materialCostService.batchGetLatestCalcCost(
                new java.util.ArrayList<>(materialIds));

        // 批量查询订单占用量（生效销售订单待发货数量，按物料全局统计）
        Map<Long, java.math.BigDecimal> reservedMap =
                virtualStockService.batchGetReservedQuantity(materialIds);

        // 填充关联字段
        for (Inventory inv : records) {
            Material m = materialMap.get(inv.getMaterialId());
            if (m != null) {
                inv.setMaterialCode(m.getMaterialCode());
                inv.setMaterialName(m.getMaterialName());
                inv.setModel(m.getModel());
                // 主图兜底：mainImage 为空时取 imageUrls 第一张（历史数据可能未写主图）
                String mainImg = m.getMainImage();
                if (mainImg == null || mainImg.isBlank()) {
                    String urls = m.getImageUrls();
                    if (urls != null && !urls.isBlank()) {
                        String first = urls.split(",")[0].trim();
                        if (!first.isEmpty()) mainImg = first;
                    }
                }
                inv.setMainImage(mainImg);
                inv.setSafetyStock(m.getMinStock());
                UnitOfMeasure unit = unitMap.get(m.getBaseUnitId());
                if (unit != null) {
                    inv.setUnitName(unit.getUnitName());
                }
            }
            Warehouse w = warehouseMap.get(inv.getStockId());
            if (w != null) {
                inv.setStockName(w.getStockName());
                inv.setStockCode(w.getStockCode());
            }
            // 填充最新核算成本（inventory_current.unit_cost 是库存移动平均成本，latestCalcCost 是期间核算成本）
            java.math.BigDecimal calcCost = calcCostMap.get(inv.getMaterialId());
            inv.setLatestCalcCost(calcCost != null ? calcCost : java.math.BigDecimal.ZERO);
            // 填充订单占用量与预计可用量（历史名"虚拟可用"；占用按物料全局统计；预计可用量 = 本行可用 - 全局占用，负数表示缺口）
            java.math.BigDecimal reserved = reservedMap.getOrDefault(inv.getMaterialId(), java.math.BigDecimal.ZERO);
            inv.setReservedQuantity(reserved);
            java.math.BigDecimal rowAvailable = inv.getAvailableQuantity() != null
                    ? inv.getAvailableQuantity() : java.math.BigDecimal.ZERO;
            inv.setVirtualAvailable(rowAvailable.subtract(reserved));
        }
    }
}
