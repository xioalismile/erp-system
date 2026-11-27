package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.production.entity.WorkOrder;
import com.erp.production.mapper.WorkOrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存报表中心
 * 汇总端点：turnover / safety-warning / batch-aging / shortage
 * 明细端点：turnover-details / safety-warning-details / batch-aging-details / shortage-details
 *
 * 时间筛选用途：
 *   - turnover：出库统计区间（以 endDate 为参考日计算 daysSinceLastOut / 呆滞判定）
 *   - batch-aging：入库日期区间（过滤 lastInDate）
 *   - shortage：工单日期区间（过滤 workOrder.orderDate）
 *   - safety-warning：当前快照，时间参数暂不参与过滤（预留扩展）
 *
 * 数据权限：统一走 DataScopeResolver，按「当前用户可见仓库」（warehouse.created_by ∪ 员工白名单仓库）
 *   过滤库存表 stock_id；无可见仓库时报表返回空结果。工单/物料等无仓库列的表沿用原有口径。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/inventory")
@RequiredArgsConstructor
public class InventoryReportController {

    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;
    private final WorkOrderMapper workOrderMapper;
    private final DataScopeResolver dataScopeResolver;

    // ==================== 汇总报表 ====================

    /** 1. 库存周转率：周转天数、呆滞料识别 */
    @GetMapping("/turnover")
    public Result<Map<String, Object>> turnover(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Long> stockIds = visibleStockIds(req);
        LocalDate reference = endDate != null ? endDate : LocalDate.now();
        List<Inventory> inventories = inventoryMapper.selectList(inventoryScopeQuery(stockIds));
        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Material> materialMap = materials.stream()
                .collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));

        BigDecimal totalInventoryValue = inventories.stream()
                .map(i -> i.getTotalCost() == null ? BigDecimal.ZERO : i.getTotalCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 按物料汇总
        List<Map<String, Object>> items = inventories.stream()
                .filter(i -> i.getMaterialId() != null)
                .collect(Collectors.groupingBy(Inventory::getMaterialId))
                .entrySet().stream().map(e -> {
                    BigDecimal qty = e.getValue().stream()
                            .map(i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal value = e.getValue().stream()
                            .map(i -> i.getTotalCost() == null ? BigDecimal.ZERO : i.getTotalCost())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    LocalDate lastOut = e.getValue().stream()
                            .map(Inventory::getLastOutDate)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);

                    Material m = materialMap.get(e.getKey());
                    // 呆滞判定：90天以上无出库（以 endDate 为参考日）
                    boolean isSlow = lastOut == null || lastOut.isBefore(reference.minusDays(90));

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("materialId", e.getKey());
                    item.put("materialCode", m != null ? m.getMaterialCode() : "");
                    item.put("materialName", m != null ? m.getMaterialName() : "");
                    item.put("quantity", qty);
                    item.put("inventoryValue", value);
                    item.put("lastOutDate", lastOut);
                    item.put("daysSinceLastOut", lastOut == null ? null
                            : ChronoUnit.DAYS.between(lastOut, reference));
                    item.put("isSlow", isSlow);
                    return item;
                })
                .sorted((a, b) -> {
                    BigDecimal aV = (BigDecimal) a.get("inventoryValue");
                    BigDecimal bV = (BigDecimal) b.get("inventoryValue");
                    return bV.compareTo(aV);
                })
                .collect(Collectors.toList());

        long slowCount = items.stream().filter(i -> Boolean.TRUE.equals(i.get("isSlow"))).count();
        BigDecimal slowValue = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("isSlow")))
                .map(i -> (BigDecimal) i.get("inventoryValue"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalInventoryValue", totalInventoryValue);
        result.put("totalMaterialCount", items.size());
        result.put("slowMovingCount", slowCount);
        result.put("slowMovingValue", slowValue);
        result.put("items", items);
        return Result.success(result);
    }

    /** 2. 安全库存预警：低于安全库存的物料清单（当前快照，时间参数预留） */
    @GetMapping("/safety-warning")
    public Result<Map<String, Object>> safetyWarning(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Long> stockIds = visibleStockIds(req);
        if (stockIds != null && stockIds.isEmpty()) {
            // 无可见仓库 → 空结果（结构与正常返回一致）
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("belowMinCount", 0);
            empty.put("belowMin", Collections.emptyList());
            empty.put("aboveMaxCount", 0);
            empty.put("aboveMax", Collections.emptyList());
            return Result.success(empty);
        }
        // 安全库存预警为当前快照，startDate/endDate 暂不参与过滤（预留扩展）
        List<Inventory> inventories = inventoryMapper.selectList(inventoryScopeQuery(stockIds));
        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<>());

        Map<Long, BigDecimal> stockByMaterial = inventories.stream()
                .filter(i -> i.getMaterialId() != null)
                .collect(Collectors.groupingBy(Inventory::getMaterialId,
                        Collectors.reducing(BigDecimal.ZERO,
                                i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity(),
                                BigDecimal::add)));

        List<Map<String, Object>> belowMin = new ArrayList<>();
        List<Map<String, Object>> aboveMax = new ArrayList<>();
        for (Material m : materials) {
            BigDecimal stock = stockByMaterial.getOrDefault(m.getMaterialId(), BigDecimal.ZERO);
            if (m.getMinStock() != null && m.getMinStock().compareTo(BigDecimal.ZERO) > 0
                    && stock.compareTo(m.getMinStock()) < 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("materialId", m.getMaterialId());
                item.put("materialCode", m.getMaterialCode());
                item.put("materialName", m.getMaterialName());
                item.put("currentStock", stock);
                item.put("minStock", m.getMinStock());
                item.put("shortage", m.getMinStock().subtract(stock));
                item.put("warningLevel", stock.compareTo(BigDecimal.ZERO) == 0 ? "缺货" :
                        stock.compareTo(m.getMinStock().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP)) < 0 ? "严重" : "预警");
                belowMin.add(item);
            }
            if (m.getMaxStock() != null && m.getMaxStock().compareTo(BigDecimal.ZERO) > 0
                    && stock.compareTo(m.getMaxStock()) > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("materialId", m.getMaterialId());
                item.put("materialCode", m.getMaterialCode());
                item.put("materialName", m.getMaterialName());
                item.put("currentStock", stock);
                item.put("maxStock", m.getMaxStock());
                item.put("excess", stock.subtract(m.getMaxStock()));
                aboveMax.add(item);
            }
        }

        belowMin.sort((a, b) -> ((BigDecimal) b.get("shortage")).compareTo((BigDecimal) a.get("shortage")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("belowMinCount", belowMin.size());
        result.put("belowMin", belowMin);
        result.put("aboveMaxCount", aboveMax.size());
        result.put("aboveMax", aboveMax);
        return Result.success(result);
    }

    /** 3. 批次库龄分析：批次入库时长、临期预警（时间过滤入库日期区间） */
    @GetMapping("/batch-aging")
    public Result<Map<String, Object>> batchAging(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Long> stockIds = visibleStockIds(req);
        QueryWrapper<Inventory> qw = inventoryScopeQuery(stockIds).isNotNull("batch_no");
        if (startDate != null) qw.ge("last_in_date", startDate);
        if (endDate != null) qw.le("last_in_date", endDate);
        List<Inventory> inventories = inventoryMapper.selectList(qw);

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> items = inventories.stream().filter(i -> i.getBatchNo() != null && !i.getBatchNo().isEmpty()).map(i -> {
            long age = i.getLastInDate() == null ? 0
                    : ChronoUnit.DAYS.between(i.getLastInDate(), today);
            long shelfLife = i.getExpiryDate() == null ? -1
                    : ChronoUnit.DAYS.between(today, i.getExpiryDate());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inventoryId", i.getInventoryId());
            m.put("batchNo", i.getBatchNo());
            m.put("materialId", i.getMaterialId());
            m.put("quantity", i.getQuantity());
            m.put("lastInDate", i.getLastInDate());
            m.put("productionDate", i.getProductionDate());
            m.put("expiryDate", i.getExpiryDate());
            m.put("ageDays", age);
            m.put("shelfLifeDays", shelfLife);
            m.put("warningLevel", shelfLife < 0 ? "已过期" : shelfLife < 30 ? "临期" : shelfLife < 90 ? "关注" : "正常");
            return m;
        }).sorted((a, b) -> Long.compare((Long) a.get("ageDays"), (Long) b.get("ageDays"))).collect(Collectors.toList());

        long expiredCount = items.stream().filter(i -> "已过期".equals(i.get("warningLevel"))).count();
        long nearExpiryCount = items.stream().filter(i -> "临期".equals(i.get("warningLevel"))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBatches", items.size());
        result.put("expiredCount", expiredCount);
        result.put("nearExpiryCount", nearExpiryCount);
        result.put("items", items);
        return Result.success(result);
    }

    /** 4. 缺料分析：基于生产工单的缺料清单（时间过滤工单日期区间） */
    @GetMapping("/shortage")
    public Result<Map<String, Object>> shortage(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Long> stockIds = visibleStockIds(req);
        if (stockIds != null && stockIds.isEmpty()) {
            // 无可见仓库 → 空结果（结构与正常返回一致）
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("totalRequirements", 0);
            empty.put("shortageCount", 0);
            empty.put("shortages", Collections.emptyList());
            empty.put("workOrderCount", 0);
            return Result.success(empty);
        }
        // 基于生产工单的未完成订单物料需求 vs 当前库存
        // 注：生产工单表无仓库列，工单需求侧沿用原口径（见交付报告「未处理项」）
        LambdaQueryWrapper<WorkOrder> wqw = new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, "已生效", "进行中", "生产中")
                .eq(WorkOrder::getApprovalStatus, "已生效");
        if (startDate != null) wqw.ge(WorkOrder::getOrderDate, startDate);
        if (endDate != null) wqw.le(WorkOrder::getOrderDate, endDate);
        List<WorkOrder> workOrders = workOrderMapper.selectList(wqw);

        List<Inventory> inventories = inventoryMapper.selectList(inventoryScopeQuery(stockIds));
        Map<Long, BigDecimal> stockByMaterial = inventories.stream()
                .filter(i -> i.getMaterialId() != null)
                .collect(Collectors.groupingBy(Inventory::getMaterialId,
                        Collectors.reducing(BigDecimal.ZERO,
                                i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity(),
                                BigDecimal::add)));

        // 工单需求 = 生产数量（按物料汇总；实际应展开 BOM）
        Map<Long, BigDecimal> requirementByMaterial = workOrders.stream()
                .filter(w -> w.getMaterialId() != null && w.getQuantity() != null)
                .collect(Collectors.groupingBy(WorkOrder::getMaterialId,
                        Collectors.reducing(BigDecimal.ZERO, WorkOrder::getQuantity, BigDecimal::add)));

        List<Map<String, Object>> shortages = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : requirementByMaterial.entrySet()) {
            BigDecimal stock = stockByMaterial.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal gap = e.getValue().subtract(stock);
            if (gap.compareTo(BigDecimal.ZERO) > 0) {
                Material m = materialMapper.selectById(e.getKey());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("materialId", e.getKey());
                item.put("materialCode", m != null ? m.getMaterialCode() : "");
                item.put("materialName", m != null ? m.getMaterialName() : "");
                item.put("requirement", e.getValue());
                item.put("currentStock", stock);
                item.put("shortage", gap);
                item.put("suggestion", "建议采购 " + gap + " " + (m != null && m.getBaseUnitId() != null ? "" : ""));
                shortages.add(item);
            }
        }
        shortages.sort((a, b) -> ((BigDecimal) b.get("shortage")).compareTo((BigDecimal) a.get("shortage")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRequirements", requirementByMaterial.size());
        result.put("shortageCount", shortages.size());
        result.put("shortages", shortages);
        result.put("workOrderCount", workOrders.size());
        return Result.success(result);
    }

    // ==================== 明细报表（可导出） ====================

    /** 1-明细. 周转/呆滞物料明细列表 */
    @GetMapping("/turnover-details")
    public Result<Map<String, Object>> turnoverDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isSlow,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Long> stockIds = visibleStockIds(req);
        LocalDate reference = endDate != null ? endDate : LocalDate.now();
        List<Inventory> inventories = inventoryMapper.selectList(inventoryScopeQuery(stockIds));
        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Material> materialMap = materials.stream()
                .collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));

        List<Map<String, Object>> items = inventories.stream()
                .filter(i -> i.getMaterialId() != null)
                .collect(Collectors.groupingBy(Inventory::getMaterialId))
                .entrySet().stream().map(e -> {
                    BigDecimal qty = e.getValue().stream()
                            .map(i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal value = e.getValue().stream()
                            .map(i -> i.getTotalCost() == null ? BigDecimal.ZERO : i.getTotalCost())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    LocalDate lastOut = e.getValue().stream()
                            .map(Inventory::getLastOutDate)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    Long daysSince = lastOut == null ? null : ChronoUnit.DAYS.between(lastOut, reference);
                    boolean slow = lastOut == null || lastOut.isBefore(reference.minusDays(90));
                    // 周转率近似：90天窗口内 90/距上次出库天数；无出库记 0
                    BigDecimal rate;
                    if (lastOut == null) {
                        rate = BigDecimal.ZERO;
                    } else {
                        long d = daysSince == null || daysSince <= 0 ? 1 : daysSince;
                        rate = new BigDecimal("90").divide(BigDecimal.valueOf(d), 4, RoundingMode.HALF_UP);
                    }
                    Material m = materialMap.get(e.getKey());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("materialId", e.getKey());
                    item.put("materialCode", m != null ? m.getMaterialCode() : "");
                    item.put("materialName", m != null ? m.getMaterialName() : "");
                    item.put("quantity", qty);
                    item.put("inventoryValue", value);
                    item.put("lastOutDate", lastOut);
                    item.put("daysSinceLastOut", daysSince);
                    item.put("isSlow", slow);
                    item.put("turnoverRate", rate);
                    return item;
                })
                .filter(item -> keyword == null || keyword.isEmpty()
                        || String.valueOf(item.get("materialCode")).toLowerCase().contains(keyword.toLowerCase())
                        || String.valueOf(item.get("materialName")).toLowerCase().contains(keyword.toLowerCase()))
                .filter(item -> isSlow == null || item.get("isSlow").equals(isSlow))
                .sorted((a, b) -> ((BigDecimal) b.get("inventoryValue")).compareTo((BigDecimal) a.get("inventoryValue")))
                .collect(Collectors.toList());
        return paginate(items, page, size);
    }

    /** 2-明细. 安全库存预警明细列表 */
    @GetMapping("/safety-warning-details")
    public Result<Map<String, Object>> safetyWarningDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Long> stockIds = visibleStockIds(req);
        if (stockIds != null && stockIds.isEmpty()) {
            // 无可见仓库 → 空结果
            return paginate(Collections.emptyList(), page, size);
        }
        // 安全库存预警为当前快照，startDate/endDate 暂不参与过滤（预留扩展）
        List<Inventory> inventories = inventoryMapper.selectList(inventoryScopeQuery(stockIds));
        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, BigDecimal> stockByMaterial = inventories.stream()
                .filter(i -> i.getMaterialId() != null)
                .collect(Collectors.groupingBy(Inventory::getMaterialId,
                        Collectors.reducing(BigDecimal.ZERO,
                                i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity(),
                                BigDecimal::add)));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Material m : materials) {
            BigDecimal stock = stockByMaterial.getOrDefault(m.getMaterialId(), BigDecimal.ZERO);
            BigDecimal min = m.getMinStock();
            BigDecimal max = m.getMaxStock();
            String st;
            if (min != null && min.compareTo(BigDecimal.ZERO) > 0 && stock.compareTo(min) < 0) {
                st = "below_min";
            } else if (max != null && max.compareTo(BigDecimal.ZERO) > 0 && stock.compareTo(max) > 0) {
                st = "above_max";
            } else {
                continue; // 仅列出预警物料
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", m.getMaterialId());
            item.put("materialCode", m.getMaterialCode());
            item.put("materialName", m.getMaterialName());
            item.put("quantity", stock);
            item.put("minStock", min);
            item.put("maxStock", max);
            item.put("warehouseId", null); // 按物料汇总库存，无单一仓库
            item.put("status", st);
            items.add(item);
        }
        List<Map<String, Object>> filtered = items.stream()
                .filter(item -> keyword == null || keyword.isEmpty()
                        || String.valueOf(item.get("materialCode")).toLowerCase().contains(keyword.toLowerCase())
                        || String.valueOf(item.get("materialName")).toLowerCase().contains(keyword.toLowerCase()))
                .filter(item -> status == null || status.isEmpty() || item.get("status").equals(status))
                .sorted((a, b) -> ((BigDecimal) b.get("quantity")).compareTo((BigDecimal) a.get("quantity")))
                .collect(Collectors.toList());
        return paginate(filtered, page, size);
    }

    /** 3-明细. 批次库龄明细列表 */
    @GetMapping("/batch-aging-details")
    public Result<Map<String, Object>> batchAgingDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Long> stockIds = visibleStockIds(req);
        QueryWrapper<Inventory> qw = inventoryScopeQuery(stockIds).isNotNull("batch_no");
        if (startDate != null) qw.ge("last_in_date", startDate);
        if (endDate != null) qw.le("last_in_date", endDate);
        List<Inventory> inventories = inventoryMapper.selectList(qw);
        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Material> materialMap = materials.stream()
                .collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> items = inventories.stream()
                .filter(i -> i.getBatchNo() != null && !i.getBatchNo().isEmpty())
                .map(i -> {
                    LocalDate inbound = i.getLastInDate();
                    long days = inbound == null ? 0 : ChronoUnit.DAYS.between(inbound, today);
                    LocalDate expiry = i.getExpiryDate();
                    String st;
                    if (expiry == null) {
                        st = "无效期";
                    } else if (expiry.isBefore(today)) {
                        st = "已过期";
                    } else {
                        long left = ChronoUnit.DAYS.between(today, expiry);
                        st = left < 30 ? "临期" : left < 90 ? "关注" : "正常";
                    }
                    Material m = i.getMaterialId() != null ? materialMap.get(i.getMaterialId()) : null;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("materialId", i.getMaterialId());
                    item.put("materialCode", m != null ? m.getMaterialCode() : "");
                    item.put("materialName", m != null ? m.getMaterialName() : "");
                    item.put("batchNo", i.getBatchNo());
                    item.put("warehouseId", i.getStockId());
                    item.put("quantity", i.getQuantity());
                    item.put("inboundDate", inbound);
                    item.put("days", days);
                    item.put("expiryDate", expiry);
                    item.put("status", st);
                    return item;
                })
                .filter(item -> keyword == null || keyword.isEmpty()
                        || String.valueOf(item.get("materialCode")).toLowerCase().contains(keyword.toLowerCase())
                        || String.valueOf(item.get("materialName")).toLowerCase().contains(keyword.toLowerCase())
                        || String.valueOf(item.get("batchNo")).toLowerCase().contains(keyword.toLowerCase()))
                .filter(item -> status == null || status.isEmpty() || item.get("status").equals(status))
                .sorted((a, b) -> Long.compare((Long) a.get("days"), (Long) b.get("days")))
                .collect(Collectors.toList());
        return paginate(items, page, size);
    }

    /** 4-明细. 缺料明细列表 */
    @GetMapping("/shortage-details")
    public Result<Map<String, Object>> shortageDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Long> stockIds = visibleStockIds(req);
        if (stockIds != null && stockIds.isEmpty()) {
            // 无可见仓库 → 空结果（结构与正常返回一致）
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("totalRequirements", 0);
            empty.put("shortageCount", 0);
            empty.put("shortages", Collections.emptyList());
            empty.put("workOrderCount", 0);
            return Result.success(empty);
        }
        // 基于生产工单的未完成订单物料需求 vs 当前库存
        // 注：生产工单表无仓库列，工单需求侧沿用原口径（见交付报告「未处理项」）
        LambdaQueryWrapper<WorkOrder> wqw = new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, "已生效", "进行中", "生产中")
                .eq(WorkOrder::getApprovalStatus, "已生效");
        if (startDate != null) wqw.ge(WorkOrder::getOrderDate, startDate);
        if (endDate != null) wqw.le(WorkOrder::getOrderDate, endDate);
        List<WorkOrder> workOrders = workOrderMapper.selectList(wqw);

        List<Inventory> inventories = inventoryMapper.selectList(inventoryScopeQuery(stockIds));
        Map<Long, BigDecimal> stockByMaterial = inventories.stream()
                .filter(i -> i.getMaterialId() != null)
                .collect(Collectors.groupingBy(Inventory::getMaterialId,
                        Collectors.reducing(BigDecimal.ZERO,
                                i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity(),
                                BigDecimal::add)));

        Map<Long, BigDecimal> requirementByMaterial = new LinkedHashMap<>();
        Map<Long, List<String>> orderNosByMaterial = new LinkedHashMap<>();
        for (WorkOrder w : workOrders) {
            if (w.getMaterialId() == null || w.getQuantity() == null) continue;
            requirementByMaterial.merge(w.getMaterialId(), w.getQuantity(), BigDecimal::add);
            if (w.getOrderNo() != null && !w.getOrderNo().isEmpty()) {
                orderNosByMaterial.computeIfAbsent(w.getMaterialId(), k -> new ArrayList<>()).add(w.getOrderNo());
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : requirementByMaterial.entrySet()) {
            BigDecimal stock = stockByMaterial.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal gap = e.getValue().subtract(stock);
            if (gap.compareTo(BigDecimal.ZERO) > 0) {
                Material m = materialMapper.selectById(e.getKey());
                List<String> nos = orderNosByMaterial.getOrDefault(e.getKey(), Collections.emptyList());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("materialId", e.getKey());
                item.put("materialCode", m != null ? m.getMaterialCode() : "");
                item.put("materialName", m != null ? m.getMaterialName() : "");
                item.put("requiredQuantity", e.getValue());
                item.put("stockQuantity", stock);
                item.put("shortageQuantity", gap);
                item.put("relatedOrderNo", String.join(",", nos));
                items.add(item);
            }
        }
        List<Map<String, Object>> filtered = items.stream()
                .filter(item -> keyword == null || keyword.isEmpty()
                        || String.valueOf(item.get("materialCode")).toLowerCase().contains(keyword.toLowerCase())
                        || String.valueOf(item.get("materialName")).toLowerCase().contains(keyword.toLowerCase()))
                .sorted((a, b) -> ((BigDecimal) b.get("shortageQuantity")).compareTo((BigDecimal) a.get("shortageQuantity")))
                .collect(Collectors.toList());
        return paginate(filtered, page, size);
    }

    // ==================== 辅助方法 ====================

    /** 库存报表数据权限：当前用户可见仓库ID集合（null=不过滤；空列表=无可见仓库，报表返回空结果） */
    private List<Long> visibleStockIds(HttpServletRequest req) {
        return dataScopeResolver.visibleWarehouseIds(dataScopeResolver.resolve(req));
    }

    /** 库存查询条件：套用可见仓库过滤（无可见仓库时条件恒假 → 0 条） */
    private QueryWrapper<Inventory> inventoryScopeQuery(List<Long> stockIds) {
        QueryWrapper<Inventory> qw = new QueryWrapper<>();
        if (stockIds == null) return qw;
        if (stockIds.isEmpty()) return qw.apply("1 = 0");
        return qw.in("stock_id", stockIds);
    }

    /** 内存分页（size<=0 表示不分页，返回全部，用于导出） */
    private <T> Result<Map<String, Object>> paginate(List<T> records, int page, int size) {
        int total = records.size();
        List<T> pageRecords;
        if (size <= 0) {
            pageRecords = records;
        } else {
            int from = Math.max(0, (page - 1) * size);
            int to = Math.min(total, from + size);
            pageRecords = from < total ? records.subList(from, to) : Collections.emptyList();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", pageRecords);
        data.put("total", total);
        data.put("current", page);
        data.put("size", size <= 0 ? total : size);
        return Result.success(data);
    }
}
