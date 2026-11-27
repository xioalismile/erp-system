package com.erp.produce.mrp.controller;

import com.erp.common.Result;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.service.InventoryService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.produce.mrp.service.BomExplosionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MRP 即时计算接口
 * 路由: /api/production/mrp
 * 提供 calculate / calculate-batch / bom-expand 三个端点
 * 供前端 MrpList.vue 调用
 */
@Slf4j
@RestController
@RequestMapping("/api/production/mrp")
@RequiredArgsConstructor
public class MrpCalculationController {

    private final BomExplosionService bomExplosionService;
    private final MaterialMapper materialMapper;
    private final InventoryService inventoryService;
    private final com.erp.masterdata.mapper.BomMapper bomMapper;
    private final com.erp.production.mapper.RoutingMapper routingMapper;
    // 2026-10-06 MRP 需求来源改按「销售物料明细」选择：需要销售订单/明细、MRP 方案明细、客户档案
    private final com.erp.sale.mapper.SaleOrderMapper saleOrderMapper;
    private final com.erp.sale.mapper.SaleOrderItemMapper saleOrderItemMapper;
    private final com.erp.produce.mrp.mapper.MrpPlanItemMapper mrpPlanItemMapper;
    private final com.erp.masterdata.service.CustomerService customerService;
    // 2026-10-06「怎么算」参数生效所需：采购在途 / 生产中
    private final com.erp.purchase.mapper.PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final com.erp.purchase.mapper.PurchaseOrderMapper purchaseOrderMapper;
    private final com.erp.production.mapper.WorkOrderMapper workOrderMapper;
    /** 数据权限解析器（2026-10-09）：可用销售明细与销售订单列表同口径收敛 */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;
    /** 仓库档案（2026-10-09）：MRP 可用库存只统计「参与MRP」的仓库（不合格品仓/废料仓默认不参与） */
    private final com.erp.basedata.mapper.WarehouseMapper warehouseMapper;

    /** 单产品 MRP 计算 */
    @PostMapping("/calculate")
    public Result<Map<String, Object>> calculate(@RequestBody Map<String, Object> body) {
        String materialCode = (String) body.get("materialCode");
        BigDecimal quantity = body.get("quantity") != null
                ? new BigDecimal(body.get("quantity").toString()) : BigDecimal.ONE;
        String demandDateStr = (String) body.get("demandDate");
        LocalDate demandDate = demandDateStr != null && !demandDateStr.isBlank()
                ? LocalDate.parse(demandDateStr) : LocalDate.now();

        if (materialCode == null || materialCode.isBlank()) {
            return Result.success(Map.of("requirements", List.of(), "purchaseSuggestions", List.of(),
                    "productionSuggestions", List.of(), "shortages", List.of()));
        }

        Material material = materialMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Material>()
                .eq("material_code", materialCode).last("LIMIT 1"));
        if (material == null) {
            return Result.success(Map.of("requirements", List.of(), "purchaseSuggestions", List.of(),
                    "productionSuggestions", List.of(), "shortages", List.of(), "message", "物料不存在"));
        }

        MrpCalcOptions opt = MrpCalcOptions.from(null);
        // 2026-10-09：可用库存只统计「参与MRP」的仓库（订单内只查一次仓库档案，避免逐行 N+1）
        opt.mrpWarehouseIds = mrpAvailableWarehouseIds();
        // 2026-11-02：BOM 解析改为「默认→主→已生效→最新」，同一请求内按物料缓存，避免逐行查库
        Map<Long, com.erp.masterdata.entity.Bom> bomCache = new HashMap<>();
        Map<String, Object> result = computeMrp(material, quantity, demandDate, opt, bomCache, parseLong(body.get("bomId")));
        return Result.success(result);
    }

    /** 批量 MRP 计算 */
    @PostMapping("/calculate-batch")
    public Result<List<Map<String, Object>>> calculateBatch(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> demands = (List<Map<String, Object>>) body.get("demands");
        // 2026-10-06：「怎么算」参数（库存/安全库存/采购在途/生产中/多级展开 + 下单数量规则）在此读取并生效
        Map<String, Object> config = body.get("config") instanceof Map
                ? (Map<String, Object>) body.get("config") : null;
        MrpCalcOptions opt = MrpCalcOptions.from(config);
        // 2026-10-09：可用库存只统计「参与MRP」的仓库（整批只查一次仓库档案，避免逐行 N+1）
        opt.mrpWarehouseIds = mrpAvailableWarehouseIds();
        // 2026-11-02：BOM 解析改为「默认→主→已生效→最新」，整批共用一个按物料缓存
        Map<Long, com.erp.masterdata.entity.Bom> bomCache = new HashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        if (demands == null) {
            return Result.success(results);
        }
        for (Map<String, Object> d : demands) {
            String code = (String) d.get("materialCode");
            BigDecimal qty = d.get("quantity") != null
                    ? new BigDecimal(d.get("quantity").toString()) : BigDecimal.ONE;
            String dateStr = (String) d.get("demandDate");
            LocalDate date = dateStr != null && !dateStr.isBlank() ? LocalDate.parse(dateStr) : LocalDate.now();

            Material material = materialMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Material>()
                    .eq("material_code", code).last("LIMIT 1"));
            if (material == null) continue;
            // 2026-11-03：按需求行选定的 BOM 展开（同一物料可有多份 BOM，前端可下拉切换）
            Long demandBomId = parseLong(d.get("bomId"));
            Map<String, Object> r = computeMrp(material, qty, date, opt, bomCache, demandBomId);
            // 来源追溯（2026-10-06）：把需求行携带的销售订单明细关联透传给前端，
            // 保存 MRP 方案时写入方案明细，用于“已分析过”判定与来源回写
            r.put("saleOrderId", d.get("saleOrderId"));
            r.put("saleOrderItemId", d.get("saleOrderItemId"));
            r.put("saleOrderNo", d.get("saleOrderNo"));
            results.add(r);
        }
        return Result.success(results);
    }

    /**
     * BOM 展开（2026-11-03 改版）：支持按「指定 BOM」展开，返回多级物料明细。
     *
     * <p>用户口径：①一个物料可能有多份 BOM，前端要能下拉切换；②只要有 BOM 就立即展开物料，
     * 不等点计算；③要支持多级布局。故返回扁平列表（含 level/parentId），由前端按层级缩进渲染，
     * 同时带出物料属性、规格、单位、单件用量、合计用量与该子件所属 BOM 编号/版本。</p>
     */
    @PostMapping("/bom-expand")
    public Result<Map<String, Object>> bomExpand(@RequestBody Map<String, Object> body) {
        Long materialId = parseLong(body.get("materialId"));
        Long bomId = parseLong(body.get("bomId"));
        String code = body.get("materialCode") == null ? null : body.get("materialCode").toString();
        BigDecimal qty = body.get("quantity") != null && !body.get("quantity").toString().isBlank()
                ? new BigDecimal(body.get("quantity").toString()) : BigDecimal.ONE;

        Material material = null;
        if (materialId != null) {
            material = materialMapper.selectById(materialId);
        } else if (code != null && !code.isBlank()) {
            material = materialMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Material>()
                    .eq("material_code", code).last("LIMIT 1"));
        }
        if (material == null) {
            return Result.success(Map.of("items", new ArrayList<>()));
        }

        com.erp.masterdata.entity.Bom bom = bomId != null
                ? bomMapper.selectById(bomId)
                : bomExplosionService.findActiveBom(material.getMaterialId());

        List<BomExplosionService.BomExplosionItem> items =
                bomExplosionService.explodeBomMultiLevelByBom(material.getMaterialId(), bomId, qty, 10);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BomExplosionService.BomExplosionItem it : items) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("materialId", it.getMaterialId());
            r.put("materialCode", it.getMaterialCode());
            r.put("materialName", it.getMaterialName());
            r.put("specification", it.getSpecification());
            r.put("unit", it.getUnit());
            r.put("materialType", it.getMaterialType());
            r.put("level", it.getLevel());
            r.put("parentId", it.getParentId());
            r.put("usageQuantity", it.getUsageQuantity());
            r.put("grossRequirement", it.getTotalQuantity());
            r.put("bomId", it.getBomId());
            r.put("bomCode", it.getBomCode());
            r.put("bomVersion", it.getBomVersion());
            r.put("safetyStock", it.getSafetyStock());
            rows.add(r);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materialId", material.getMaterialId());
        result.put("materialCode", material.getMaterialCode());
        result.put("materialName", material.getMaterialName());
        result.put("specification", material.getModel());
        result.put("materialType", material.getMaterialType());
        result.put("unit", material.getBaseUnitName());
        result.put("quantity", qty);
        result.put("bomId", bom == null ? null : bom.getBomId());
        result.put("bomCode", bom == null ? null : bom.getBomCode());
        result.put("bomVersion", bom == null ? null : bom.getBomVersion());
        result.put("items", rows);
        return Result.success(result);
    }

    private Long parseLong(Object v) {
        if (v == null) return null;
        try {
            String s = v.toString().trim();
            return s.isEmpty() ? null : Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * MRP 需求来源选择：可分析的销售物料明细（2026-10-06 改版）。
     *
     * <p>过滤口径（用户确认：「已分析完成 / 已发货完成 / 草稿」三类销售单据都不显示）：</p>
     * <ul>
     *   <li>仅取“已生效”且未结案/未关闭的销售订单（草稿、待审核、已关闭、已取消一律不出现）；</li>
     *   <li>明细已发货完成（已发货数量 ≥ 订单数量）不返回；</li>
     *   <li>明细曾被 MRP 方案引用（分析过）不返回，避免重复分析；</li>
     *   <li>明细已全部转生产/委外（已转数量合计 ≥ 订单数量）不返回。</li>
     * </ul>
     * <p>每行额外返回 hasBom：该物料是否存在“已生效 + 主 BOM”，供前端做“无 BOM”确认提示。</p>
     */
    @GetMapping("/available-sale-items")
    public Result<Map<String, Object>> availableSaleItems(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "false") boolean includeAnalyzed,
            jakarta.servlet.http.HttpServletRequest request) {

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.sale.entity.SaleOrder> ow =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        ow.eq("status", "已生效");
        ow.and(w -> w.isNull("close_status").or().eq("close_status", 0));
        if (keyword != null && !keyword.isBlank()) {
            ow.and(w -> w.like("order_no", keyword).or().like("customer_name", keyword));
        }
        com.erp.common.query.ListQuerySupport.applyDateRange(ow, "order_date", startDate, endDate);
        // 数据权限（2026-10-09）：可用销售明细与销售订单列表同口径（created_by + saler_id）
        dataScopeResolver.resolve(request).applyTo(ow, "saler_id");
        ow.orderByDesc("order_id");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.erp.sale.entity.SaleOrder> orderPage =
                saleOrderMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), ow);

        List<Map<String, Object>> records = new ArrayList<>();
        long total = orderPage.getTotal();
        List<com.erp.sale.entity.SaleOrder> orders = orderPage.getRecords();
        if (orders == null || orders.isEmpty()) {
            return Result.success(Map.of("records", records, "total", total));
        }

        List<Long> orderIds = new ArrayList<>();
        Map<Long, com.erp.sale.entity.SaleOrder> orderMap = new LinkedHashMap<>();
        for (com.erp.sale.entity.SaleOrder o : orders) {
            orderIds.add(o.getOrderId());
            orderMap.put(o.getOrderId(), o);
        }

        List<com.erp.sale.entity.SaleOrderItem> items = saleOrderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.sale.entity.SaleOrderItem>()
                        .in("order_id", orderIds)
                        .orderByAsc("order_id", "line_no"));
        if (items == null || items.isEmpty()) {
            return Result.success(Map.of("records", records, "total", total));
        }

        // 曾被 MRP 方案引用过的销售明细（分析过 → 不再作为待分析行）
        List<Long> orderItemIds = new ArrayList<>();
        for (com.erp.sale.entity.SaleOrderItem it : items) {
            if (it.getOrderItemId() != null) orderItemIds.add(it.getOrderItemId());
        }
        java.util.Set<Long> analyzedItemIds = new java.util.HashSet<>();
        if (!orderItemIds.isEmpty()) {
            List<com.erp.produce.mrp.entity.MrpPlanItem> refs = mrpPlanItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.produce.mrp.entity.MrpPlanItem>()
                            .in("sale_order_item_id", orderItemIds));
            for (com.erp.produce.mrp.entity.MrpPlanItem r : refs) {
                if (r.getSaleOrderItemId() != null) analyzedItemIds.add(r.getSaleOrderItemId());
            }
        }

        // 物料当前可用 BOM：与 MRP 展开统一口径（默认→主→已生效→最新），一次批量查出后按物料归组挑选
        List<Long> materialIds = new ArrayList<>();
        for (com.erp.sale.entity.SaleOrderItem it : items) {
            if (it.getMaterialId() != null) materialIds.add(it.getMaterialId());
        }
        java.util.Map<Long, com.erp.masterdata.entity.Bom> activeBomByMaterial = new java.util.HashMap<>();
        if (!materialIds.isEmpty()) {
            List<com.erp.masterdata.entity.Bom> boms = bomMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.masterdata.entity.Bom>()
                            .in("material_id", materialIds));
            java.util.Map<Long, List<com.erp.masterdata.entity.Bom>> grouped = new java.util.HashMap<>();
            for (com.erp.masterdata.entity.Bom b : boms) {
                if (b.getMaterialId() != null) {
                    grouped.computeIfAbsent(b.getMaterialId(), k -> new ArrayList<>()).add(b);
                }
            }
            for (java.util.Map.Entry<Long, List<com.erp.masterdata.entity.Bom>> e : grouped.entrySet()) {
                com.erp.masterdata.entity.Bom pick = bomExplosionService.pickActiveBom(e.getValue());
                if (pick != null) activeBomByMaterial.put(e.getKey(), pick);
            }
        }

        java.util.Map<Long, String> customerNameCache = new HashMap<>();
        for (com.erp.sale.entity.SaleOrderItem it : items) {
            com.erp.sale.entity.SaleOrder order = orderMap.get(it.getOrderId());
            if (order == null) continue;
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delivered = it.getDeliveredQuantity() != null ? it.getDeliveredQuantity() : BigDecimal.ZERO;
            BigDecimal turnedP = it.getTurnedProduceQuantity() != null ? it.getTurnedProduceQuantity() : BigDecimal.ZERO;
            BigDecimal turnedS = it.getTurnedSubcontractQuantity() != null ? it.getTurnedSubcontractQuantity() : BigDecimal.ZERO;

            if (qty.compareTo(BigDecimal.ZERO) > 0 && delivered.compareTo(qty) >= 0) continue;   // 已发货完成
            // 已分析过 → 默认不返回；勾选“包含已分析过的物料”后放行，支持二次重新分析
            if (!includeAnalyzed && it.getOrderItemId() != null && analyzedItemIds.contains(it.getOrderItemId())) continue;
            if (qty.compareTo(BigDecimal.ZERO) > 0 && turnedP.add(turnedS).compareTo(qty) >= 0) continue; // 已全部转下游

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", order.getOrderId());
            row.put("orderNo", order.getOrderNo());
            row.put("orderDate", order.getOrderDate() != null ? order.getOrderDate().toString() : "");
            row.put("customerId", order.getCustomerId());
            row.put("customerName", resolveCustomerName(order.getCustomerId(), customerNameCache));
            row.put("deliveryDate", it.getDeliveryDate() != null ? it.getDeliveryDate().toString()
                    : (order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : ""));
            row.put("orderItemId", it.getOrderItemId());
            row.put("materialId", it.getMaterialId());
            row.put("materialCode", it.getMaterialCode());
            row.put("materialName", it.getMaterialName());
            row.put("specification", it.getSpecification());
            row.put("quantity", qty);
            row.put("deliveredQuantity", delivered);
            row.put("turnedProduceQuantity", turnedP);
            row.put("turnedSubcontractQuantity", turnedS);
            com.erp.masterdata.entity.Bom bom = it.getMaterialId() == null ? null : activeBomByMaterial.get(it.getMaterialId());
            row.put("hasBom", bom != null);
            if (bom != null) {
                row.put("bomId", bom.getBomId());
                row.put("bomCode", bom.getBomCode());
                row.put("bomVersion", bom.getBomVersion());
                row.put("bomName", bom.getBomName());
            }
            records.add(row);
        }
        return Result.success(Map.of("records", records, "total", total));
    }

    /** 客户名称（带本请求内缓存，避免逐行查库） */
    private String resolveCustomerName(Long customerId, Map<Long, String> cache) {
        if (customerId == null) return "";
        if (cache.containsKey(customerId)) return cache.get(customerId);
        String name = "";
        try {
            com.erp.masterdata.entity.Customer c = customerService.getById(customerId);
            if (c != null && c.getCustomerName() != null) name = c.getCustomerName();
        } catch (Exception e) {
            log.warn("MRP 需求来源查询客户名称失败 customerId={}", customerId);
        }
        cache.put(customerId, name);
        return name;
    }

    private List<Map<String, Object>> buildTree(List<BomExplosionService.BomExplosionItem> items, Long parentId) {
        List<Map<String, Object>> children = new ArrayList<>();
        for (BomExplosionService.BomExplosionItem item : items) {
            if (parentId == null && item.getParentId() == null) {
                children.add(toTreeNode(item, items));
            } else if (parentId != null && parentId.equals(item.getParentId())) {
                children.add(toTreeNode(item, items));
            }
        }
        return children;
    }

    private Map<String, Object> toTreeNode(BomExplosionService.BomExplosionItem item, List<BomExplosionService.BomExplosionItem> all) {
        Map<String, Object> node = new HashMap<>();
        node.put("materialId", item.getMaterialId());
        node.put("materialCode", item.getMaterialCode());
        node.put("materialName", item.getMaterialName());
        node.put("usageQuantity", item.getUsageQuantity());
        node.put("totalQuantity", item.getTotalQuantity());
        node.put("children", buildTree(all, item.getMaterialId()));
        return node;
    }

    /** 核心 MRP 计算 (返回缺料明细 + 采购/生产建议)
     *  2026-10-06：接入页面的「怎么算」参数（库存/安全库存/采购在途/生产中/多级展开 + 下单数量规则） */
    private Map<String, Object> computeMrp(Material material, BigDecimal quantity, LocalDate demandDate,
                                           MrpCalcOptions opt, Map<Long, com.erp.masterdata.entity.Bom> bomCache,
                                           Long demandBomId) {
        int maxLevel = opt.expandAllLevel ? 10 : 1;
        List<BomExplosionService.BomExplosionItem> bomItems =
                bomExplosionService.explodeBomMultiLevelByBom(material.getMaterialId(), demandBomId, quantity, maxLevel);

        List<Map<String, Object>> requirements = new ArrayList<>();
        List<Map<String, Object>> purchaseSuggestions = new ArrayList<>();
        List<Map<String, Object>> productionSuggestions = new ArrayList<>();
        List<Map<String, Object>> shortages = new ArrayList<>();

        // 加入成品本身
        Map<String, Object> parentReq = buildRequirementRow(
                material.getMaterialId(), material.getMaterialCode(), material.getMaterialName(),
                material.getModel(), null, quantity, material.getMinStock(), material.getMaterialType(), opt);
        parentReq.put("plannedOrderDate", demandDate.minusDays(7).toString());
        // 2026-11-03：母件为第 0 层，前端按层级缩进渲染多级布局
        parentReq.put("level", 0);
        parentReq.put("parentId", null);
        // 2026-11-02：需求行也带 BOM 编号/版本（原先只有生产建议才补，库存满足/无需生产时整列空白）
        applyBomMeta(parentReq, material.getMaterialId(), bomCache);
        requirements.add(parentReq);

        BigDecimal parentNet = (BigDecimal) parentReq.get("netRequirement");
        if (parentNet.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal parentPlan = (BigDecimal) parentReq.get("planQuantity");
            Map<String, Object> sug = new LinkedHashMap<>(parentReq);
            sug.put("quantity", parentPlan);
            sug.put("suggestedOrderDate", demandDate.minusDays(7).toString());
            sug.put("suggestedDeliveryDate", demandDate.toString());
            sug.put("estimatedAmount", parentPlan.multiply(material.getPurchasePrice() != null ? material.getPurchasePrice() : BigDecimal.ZERO));
            String type = material.getMaterialType();
            if (type != null && (type.contains("成品") || type.contains("半成品"))) {
                fillProductionMeta(sug, material.getMaterialId(), bomItems, bomCache);
                productionSuggestions.add(sug);
            } else {
                purchaseSuggestions.add(sug);
            }
        }

        // 加入 BOM 子件
        for (BomExplosionService.BomExplosionItem bomItem : bomItems) {
            Map<String, Object> req = buildRequirementRow(
                    bomItem.getMaterialId(), bomItem.getMaterialCode(), bomItem.getMaterialName(),
                    bomItem.getSpecification(), bomItem.getUnit(), bomItem.getTotalQuantity(),
                    bomItem.getSafetyStock(), bomItem.getMaterialType(), opt);
            req.put("plannedOrderDate", demandDate.minusDays(7).toString());
            // 2026-11-03：多级布局所需层级/父件/单件用量
            req.put("level", bomItem.getLevel());
            req.put("parentId", bomItem.getParentId());
            req.put("usageQuantity", bomItem.getUsageQuantity());
            // 2026-11-02：子件若自身也是母件（半成品），同样带出自己的 BOM 编号/版本
            applyBomMeta(req, bomItem.getMaterialId(), bomCache);
            requirements.add(req);

            BigDecimal net = (BigDecimal) req.get("netRequirement");
            BigDecimal planQty = (BigDecimal) req.get("planQuantity");

            // 缺料明细
            if (net.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> shortItem = new LinkedHashMap<>(req);
                shortItem.put("shortageQuantity", net);
                shortages.add(shortItem);
            }

            // 采购/生产建议 (按物料属性分类)
            if (net.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> sug = new LinkedHashMap<>(req);
                sug.put("quantity", planQty);
                sug.put("suggestedOrderDate", demandDate.minusDays(7).toString());
                sug.put("suggestedDeliveryDate", demandDate.toString());
                sug.put("plannedStartDate", demandDate.minusDays(7).toString());
                sug.put("plannedFinishDate", demandDate.toString());
                sug.put("estimatedAmount", planQty.multiply(bomItem.getPurchasePrice() != null ? bomItem.getPurchasePrice() : BigDecimal.ZERO));
                String type = bomItem.getMaterialType();
                if (type != null && (type.contains("成品") || type.contains("半成品"))) {
                    // 成品/半成品 -> 生产/委外建议
                    fillProductionMeta(sug, bomItem.getMaterialId(), bomItems, bomCache);
                    productionSuggestions.add(sug);
                } else {
                    // 原材料 -> 采购建议
                    purchaseSuggestions.add(sug);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("materialCode", material.getMaterialCode());
        result.put("materialName", material.getMaterialName());
        result.put("quantity", quantity);
        result.put("requirements", requirements);
        result.put("purchaseSuggestions", purchaseSuggestions);
        result.put("productionSuggestions", productionSuggestions);
        result.put("shortages", shortages);
        return result;
    }

    /**
     * 组装单条需求行：毛需求 → 可用供应（库存 / 采购在途 / 生产中，各按开关）→ 净需求 → 计划下单量。
     * 净需求 = 毛需求 − 可用供应 + 安全库存（低于 0 取 0）；计划下单量 = 净需求按批量规则修正。
     */
    private Map<String, Object> buildRequirementRow(Long materialId, String materialCode, String materialName,
                                                     String specification, String unit, BigDecimal grossRequirement,
                                                     BigDecimal safetyStockFromBom, String materialType,
                                                     MrpCalcOptions opt) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("materialId", materialId);
        req.put("materialCode", materialCode);
        req.put("materialName", materialName);
        if (specification != null) req.put("specification", specification);
        if (unit != null) req.put("unit", unit);
        if (materialType != null) req.put("materialType", materialType);
        req.put("grossRequirement", grossRequirement);
        // 2026-11-25：一次取出物料实体，供安全库存与采购批量政策（采购批量/最小订货量）共用
        Material material = materialId != null ? materialMapper.selectById(materialId) : null;

        BigDecimal stock = opt.includeOnhandStock ? getAvailableStock(materialId, opt.mrpWarehouseIds) : BigDecimal.ZERO;
        BigDecimal openPo = opt.includeOpenPo ? getOpenPurchaseQty(materialId) : BigDecimal.ZERO;
        BigDecimal openMo = opt.includeOpenMo ? getOpenWorkOrderQty(materialId) : BigDecimal.ZERO;
        BigDecimal safetyStock = BigDecimal.ZERO;
        if (opt.includeSafetyStock) {
            safetyStock = safetyStockFromBom != null ? safetyStockFromBom : BigDecimal.ZERO;
            if (safetyStock.compareTo(BigDecimal.ZERO) == 0 && material != null
                    && material.getMinStock() != null) {
                safetyStock = material.getMinStock();
            }
        }

        req.put("onHandQuantity", stock);
        req.put("openPoQuantity", openPo);
        req.put("openMoQuantity", openMo);
        req.put("safetyStock", safetyStock);

        BigDecimal supply = stock.add(openPo).add(openMo);
        BigDecimal net = grossRequirement.subtract(supply).add(safetyStock);
        if (net.compareTo(BigDecimal.ZERO) < 0) net = BigDecimal.ZERO;

        req.put("netRequirement", net);
        req.put("planQuantity", applyLotSize(net, opt, material, materialType));
        // 兼容前端详情弹窗读取的字段
        req.put("inTransitQuantity", openPo.add(openMo));
        req.put("allocatedQuantity", BigDecimal.ZERO);
        req.put("leadTimeDays", 7);
        req.put("leadTime", 7);
        return req;
    }

    /**
     * 计划下单量按批量规则修正（2026-11-25 接入物料级采购批量政策，金蝶/用友同口径）：
     * <ol>
     *   <li>采购侧（非成品/半成品）且物料配了「最小订货量 / 采购批量」→ 先抬到最小订货量，再向上取整到采购批量的整数倍；</li>
     *   <li>物料未配置 → 回落方案级批量规则（固定批量向上取整 + 不小于最小订货量），与升级前完全一致。</li>
     * </ol>
     * 25kg 一包、最小订货量 25 → 净需求 25.1 得 50（2 包）。
     */
    private BigDecimal applyLotSize(BigDecimal net, MrpCalcOptions opt, Material material, String materialType) {
        if (net == null || net.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal qty = net;

        // 采购批量政策只管采购侧；成品/半成品走生产建议，不套采购包装量
        boolean purchaseSide = materialType == null
                || (!materialType.contains("成品") && !materialType.contains("半成品"));
        BigDecimal matMin = (purchaseSide && material != null) ? material.getMinOrderQty() : null;
        BigDecimal batch = (purchaseSide && material != null) ? material.getPurchaseBatchQty() : null;
        boolean hasMatMin = matMin != null && matMin.compareTo(BigDecimal.ZERO) > 0;
        boolean hasBatch = batch != null && batch.compareTo(BigDecimal.ZERO) > 0;

        if (hasMatMin || hasBatch) {
            if (hasMatMin && qty.compareTo(matMin) < 0) qty = matMin;
            if (hasBatch) qty = qty.divide(batch, 0, RoundingMode.CEILING).multiply(batch);
            return qty;
        }

        if ("固定批量".equals(opt.lotSizeType) && opt.lotSize != null && opt.lotSize.compareTo(BigDecimal.ZERO) > 0) {
            qty = net.divide(opt.lotSize, 0, RoundingMode.CEILING).multiply(opt.lotSize);
        }
        if (opt.minOrderQty != null && opt.minOrderQty.compareTo(BigDecimal.ZERO) > 0
                && qty.compareTo(opt.minOrderQty) < 0) {
            qty = opt.minOrderQty;
        }
        return qty;
    }

    /**
     * 解析物料当前 BOM（同一请求内按物料缓存）：默认→主→已生效→最新，与 MRP 展开/BOM 主档口径一致。
     */
    private com.erp.masterdata.entity.Bom resolveBom(Long materialId,
                                                     Map<Long, com.erp.masterdata.entity.Bom> bomCache) {
        if (materialId == null) return null;
        if (bomCache != null && bomCache.containsKey(materialId)) return bomCache.get(materialId);
        com.erp.masterdata.entity.Bom bom = bomExplosionService.findActiveBom(materialId);
        if (bomCache != null) bomCache.put(materialId, bom);
        return bom;
    }

    /** 需求行补 BOM 编号/版本（库存满足或被抵扣时也能看到用的是哪份 BOM） */
    private void applyBomMeta(Map<String, Object> row, Long materialId,
                              Map<Long, com.erp.masterdata.entity.Bom> bomCache) {
        com.erp.masterdata.entity.Bom bom = resolveBom(materialId, bomCache);
        if (bom == null) return;
        row.put("bomId", bom.getBomId());
        row.put("bomCode", bom.getBomCode());
        row.put("bomVersion", bom.getBomVersion());
        row.put("bomName", bom.getBomName());
    }

    /** 采购在途：已生效采购订单中尚未入库的数量合计（下单数量 − 已入库数量） */
    private BigDecimal getOpenPurchaseQty(Long materialId) {
        if (materialId == null) return BigDecimal.ZERO;
        try {
            List<com.erp.purchase.entity.PurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.purchase.entity.PurchaseOrderItem>()
                            .eq("material_id", materialId));
            if (items.isEmpty()) return BigDecimal.ZERO;
            java.util.Set<Long> orderIds = new java.util.HashSet<>();
            for (com.erp.purchase.entity.PurchaseOrderItem it : items) {
                if (it.getOrderId() != null) orderIds.add(it.getOrderId());
            }
            if (orderIds.isEmpty()) return BigDecimal.ZERO;
            java.util.Set<Long> effective = new java.util.HashSet<>();
            for (com.erp.purchase.entity.PurchaseOrder o : purchaseOrderMapper.selectBatchIds(orderIds)) {
                if ("已生效".equals(o.getStatus())) effective.add(o.getOrderId());
            }
            BigDecimal total = BigDecimal.ZERO;
            for (com.erp.purchase.entity.PurchaseOrderItem it : items) {
                if (!effective.contains(it.getOrderId())) continue;
                BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                BigDecimal recv = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : BigDecimal.ZERO;
                BigDecimal open = qty.subtract(recv);
                if (open.compareTo(BigDecimal.ZERO) > 0) total = total.add(open);
            }
            return total;
        } catch (Exception e) {
            log.warn("统计采购在途失败 materialId={}: {}", materialId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** 生产中：已生效且未结案工单中尚未完工的数量合计（工单数量 − 已完工数量） */
    private BigDecimal getOpenWorkOrderQty(Long materialId) {
        if (materialId == null) return BigDecimal.ZERO;
        try {
            List<com.erp.production.entity.WorkOrder> orders = workOrderMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.production.entity.WorkOrder>()
                            .eq("material_id", materialId)
                            .eq("status", "已生效")
                            .and(w -> w.isNull("is_closed").or().ne("is_closed", (short) 1)));
            BigDecimal total = BigDecimal.ZERO;
            for (com.erp.production.entity.WorkOrder wo : orders) {
                BigDecimal qty = wo.getQuantity() != null ? wo.getQuantity() : BigDecimal.ZERO;
                BigDecimal done = wo.getCompletedQuantity() != null ? wo.getCompletedQuantity() : BigDecimal.ZERO;
                BigDecimal open = qty.subtract(done);
                if (open.compareTo(BigDecimal.ZERO) > 0) total = total.add(open);
            }
            return total;
        } catch (Exception e) {
            log.warn("统计生产中数量失败 materialId={}: {}", materialId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** 页面「怎么算」参数（2026-10-06）：缺省全部按“参与计算”，与旧行为一致 */
    private static class MrpCalcOptions {
        boolean includeOnhandStock = true;
        boolean includeSafetyStock = true;
        boolean includeOpenPo = true;
        boolean includeOpenMo = true;
        boolean expandAllLevel = true;
        String lotSizeType = "逐批法";
        BigDecimal lotSize = BigDecimal.ZERO;
        BigDecimal minOrderQty = BigDecimal.ZERO;
        /** 「参与MRP」的仓库 id 集合（2026-10-09）：由调用方在请求入口取一次后下发，null/空 = 不计任何仓库库存 */
        java.util.Set<Long> mrpWarehouseIds;

        static MrpCalcOptions from(Map<String, Object> c) {
            MrpCalcOptions o = new MrpCalcOptions();
            if (c == null) return o;
            o.includeOnhandStock = boolOf(c.get("includeStock"), true);
            o.includeSafetyStock = boolOf(c.get("includeSafetyStock"), true);
            o.includeOpenPo = boolOf(c.get("includeOpenPo"), true);
            o.includeOpenMo = boolOf(c.get("includeOpenMo"), true);
            o.expandAllLevel = boolOf(c.get("expandAllLevel"), true);
            if (c.get("lotSizeType") != null) o.lotSizeType = c.get("lotSizeType").toString();
            o.lotSize = decOf(c.get("fixedLotSize"));
            o.minOrderQty = decOf(c.get("minOrderQty"));
            return o;
        }

        private static boolean boolOf(Object v, boolean def) {
            if (v == null) return def;
            if (v instanceof Boolean) return (Boolean) v;
            return Boolean.parseBoolean(v.toString());
        }

        private static BigDecimal decOf(Object v) {
            if (v == null) return BigDecimal.ZERO;
            try {
                return new BigDecimal(v.toString());
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
    }

    /**
     * 生产建议补显：BOM（编号/版本/名称）与工艺路线（名称/编码/版本）、子件数量、生产周期。
     * 口径"多比少好"：多带字段可隐藏，缺了后续还要补。BOM 口径对齐 BOM 主档/工单自动带出：
     * 默认BOM（已生效）→ 默认BOM → 主BOM（已生效）→ 主BOM → 最新已生效 → 最新。
     */
    private void fillProductionMeta(Map<String, Object> sug, Long materialId,
                                    List<BomExplosionService.BomExplosionItem> bomItems,
                                    Map<Long, com.erp.masterdata.entity.Bom> bomCache) {
        if (materialId == null) return;
        sug.putIfAbsent("leadTime", 7);
        if (!sug.containsKey("subMaterialCount")) {
            long cnt = bomItems == null ? 0L : bomItems.stream()
                    .filter(i -> materialId.equals(i.getParentId())).count();
            sug.put("subMaterialCount", cnt);
        }
        try {
            com.erp.masterdata.entity.Bom bom = resolveBom(materialId, bomCache);
            if (bom != null) {
                sug.put("bomId", bom.getBomId());
                sug.put("bomCode", bom.getBomCode());
                sug.put("bomVersion", bom.getBomVersion());
                sug.put("bomName", bom.getBomName());
            }
            com.erp.production.entity.Routing routing = null;
            if (bom != null) {
                routing = routingMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.production.entity.Routing>()
                                .eq("bom_id", bom.getBomId()).orderByDesc("routing_id").last("LIMIT 1"));
            }
            if (routing == null) {
                routing = routingMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.production.entity.Routing>()
                                .eq("material_id", materialId).orderByDesc("routing_id").last("LIMIT 1"));
            }
            if (routing != null) {
                sug.put("routingId", routing.getRoutingId());
                sug.put("routingCode", routing.getRoutingCode());
                sug.put("routingName", routing.getRoutingName());
                sug.put("routingVersion", routing.getRoutingVersion());
            }
        } catch (Exception e) {
            log.warn("MRP 生产建议补显 BOM/工艺路线失败 materialId={}", materialId, e);
        }
    }

    /**
     * 可用库存（2026-10-09 修正）：只统计「参与MRP = 开」的仓库。
     * <p>修正前按物料把所有仓库的可用量加总，会把「不合格品仓 / 废料仓」里不能用（待报废/已判废）的存货
     * 也算成可用，导致净需求被少算 → 少采购 / 少生产 → 现场投料时才发现欠料。</p>
     */
    private BigDecimal getAvailableStock(Long materialId, java.util.Set<Long> mrpWarehouseIds) {
        if (materialId == null) return BigDecimal.ZERO;
        // 没有任何仓库参与 MRP（或未取到仓库清单）→ 库存一律不计入可用量（宁可多买，不可少买）
        if (mrpWarehouseIds == null || mrpWarehouseIds.isEmpty()) return BigDecimal.ZERO;
        try {
            // 用 InventoryService 的 list(QueryWrapper) 方式汇总库存
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Inventory> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.eq("material_id", materialId);
            wrapper.in("stock_id", mrpWarehouseIds);
            List<Inventory> list = inventoryService.list(wrapper);
            BigDecimal total = BigDecimal.ZERO;
            if (list != null) {
                for (Inventory inv : list) {
                    // 2026-10-06：优先用「可用数量」（已扣冻结），无该值时退化用「现有数量」，与其它库存口径一致
                    if (inv.getAvailableQuantity() != null) {
                        total = total.add(inv.getAvailableQuantity());
                    } else if (inv.getQuantity() != null) {
                        total = total.add(inv.getQuantity());
                    }
                }
            }
            return total;
        } catch (Exception e) {
            log.warn("获取物料库存失败 materialId={}: {}", materialId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 「参与MRP」的仓库 id 集合（2026-10-09）：仓库档案里「参与MRP」开关关闭的仓库
     * （建库默认把「不合格品仓」设为不参与）库存不得计入 MRP 可用量。
     * <p>老数据里该列可能为 NULL（本地实测存在），NULL 按「开关默认开」处理 = 参与 MRP，
     * 否则老仓库会被整体排除 → 可用库存全为 0 → 全面多买。</p>
     */
    private java.util.Set<Long> mrpAvailableWarehouseIds() {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        try {
            List<com.erp.basedata.entity.Warehouse> whs = warehouseMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.basedata.entity.Warehouse>()
                            .select("stock_id")
                            .and(w -> w.isNull("is_mrp_available").or().eq("is_mrp_available", (short) 1)));
            if (whs != null) {
                for (com.erp.basedata.entity.Warehouse w : whs) {
                    if (w.getStockId() != null) ids.add(w.getStockId());
                }
            }
        } catch (Exception e) {
            log.warn("查询「参与MRP」仓库失败: {}", e.getMessage());
        }
        return ids;
    }
}
