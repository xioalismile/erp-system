package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.service.InventoryBatchService;
import com.erp.inventory.service.InventoryService;
import com.erp.masterdata.service.BomService;
import com.erp.masterdata.service.MaterialService;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.mapper.WorkOrderMaterialMapper;
import com.erp.production.mapper.WorkOrderMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MrpCalculationService {

    private final MaterialService materialService;
    private final InventoryService inventoryService;
    private final InventoryBatchService batchService;
    private final BomService bomService;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderMaterialMapper workOrderMaterialMapper;

    public MrpResult calculate(String materialCode, BigDecimal quantity, LocalDate demandDate) {
        return calculate(List.of(new MrpDemand(materialCode, quantity, demandDate)));
    }

    /**
     * 批量MRP计算 - 支持多个产品同时计算
     */
    public List<MrpResult> calculateBatch(List<MrpDemand> allDemands) {
        log.info("[MRP] 开始批量计算: {} 个需求", allDemands.size());
        List<MrpResult> results = new ArrayList<>();

        Map<String, List<MrpDemand>> demandsByMaterial = new LinkedHashMap<>();
        for (MrpDemand demand : allDemands) {
            demandsByMaterial.computeIfAbsent(demand.getMaterialCode(), k -> new ArrayList<>()).add(demand);
        }

        for (Map.Entry<String, List<MrpDemand>> entry : demandsByMaterial.entrySet()) {
            MrpResult result = calculate(entry.getValue());
            results.add(result);
        }

        log.info("[MRP] 批量计算完成: {} 个产品", results.size());
        return results;
    }

    public MrpResult calculate(List<MrpDemand> demands) {
        log.info("[MRP] 开始计算: {} 个需求", demands.size());

        MrpResult result = new MrpResult();

        Map<String, BigDecimal> totalGrossRequirements = new LinkedHashMap<>();
        Map<String, LocalDate> earliestDemandDates = new HashMap<>();
        Map<String, Map<String, Object>> materialDetails = new HashMap<>();

        for (MrpDemand demand : demands) {
            List<Map<String, Object>> bomItems = bomService.flattenBom(demand.getMaterialCode(), demand.getQuantity());
            for (Map<String, Object> bomItem : bomItems) {
                String materialCode = (String) bomItem.get("materialCode");
                BigDecimal qty = (BigDecimal) bomItem.get("totalQuantity");

                totalGrossRequirements.merge(materialCode, qty, BigDecimal::add);
                materialDetails.put(materialCode, bomItem);

                LocalDate existingDate = earliestDemandDates.get(materialCode);
                if (existingDate == null || demand.getDemandDate().isBefore(existingDate)) {
                    earliestDemandDates.put(materialCode, demand.getDemandDate());
                }
            }
        }

        List<MrpResult.MaterialRequirement> requirements = new ArrayList<>();
        List<MrpResult.PurchaseSuggestion> purchaseSuggestions = new ArrayList<>();
        List<MrpResult.ProductionSuggestion> productionSuggestions = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : totalGrossRequirements.entrySet()) {
            String materialCode = entry.getKey();
            BigDecimal grossReq = entry.getValue();
            LocalDate demandDate = earliestDemandDates.get(materialCode);
            Map<String, Object> details = materialDetails.get(materialCode);

            BigDecimal onHand = BigDecimal.ZERO;
            BigDecimal safetyStock = BigDecimal.ZERO;
            BigDecimal inTransit = BigDecimal.ZERO;
            BigDecimal allocated = BigDecimal.ZERO;

            // 通过 materialCode 查询 materialId (Inventory 实体无 materialCode 字段, 已对齐 inventory_current 表)
            com.erp.masterdata.entity.Material material = materialService.getOne(
                    new QueryWrapper<com.erp.masterdata.entity.Material>().eq("material_code", materialCode));
            Long materialId = material != null ? material.getMaterialId() : null;
            if (material != null) {
                safetyStock = material.getMinStock() != null ? material.getMinStock() : BigDecimal.ZERO;
            }

            if (materialId != null) {
                List<Inventory> invList = inventoryService.list(
                        new QueryWrapper<Inventory>().eq("material_id", materialId));
                for (Inventory inv : invList) {
                    onHand = onHand.add(inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO);
                }

                List<InventoryBatch> batches = batchService.getByMaterial(materialId);
                for (InventoryBatch batch : batches) {
                    onHand = onHand.add(batch.getQuantity() != null ? batch.getQuantity() : BigDecimal.ZERO);
                }
            }

            inTransit = calculateInTransit(materialCode);
            allocated = calculateAllocated(materialCode);

            BigDecimal netReq = grossReq.subtract(onHand).subtract(inTransit).add(allocated).add(safetyStock);
            if (netReq.compareTo(BigDecimal.ZERO) < 0) netReq = BigDecimal.ZERO;

            int leadTime = details.get("leadTimeDays") != null ? (Integer) details.get("leadTimeDays") : 7;
            LocalDate plannedOrderDate = demandDate.minusDays(leadTime);

            MrpResult.MaterialRequirement req = new MrpResult.MaterialRequirement();
            req.setMaterialCode(materialCode);
            req.setMaterialName((String) details.get("materialName"));
            req.setSpecification((String) details.get("specification"));
            req.setUnit((String) details.get("unit"));
            req.setGrossRequirement(grossReq);
            req.setOnHandQuantity(onHand);
            req.setSafetyStock(safetyStock);
            req.setInTransitQuantity(inTransit);
            req.setAllocatedQuantity(allocated);
            req.setNetRequirement(netReq);
            req.setPlannedOrderDate(plannedOrderDate);
            req.setLeadTimeDays(leadTime);
            requirements.add(req);

            if (netReq.compareTo(BigDecimal.ZERO) > 0) {
                List<Map<String, Object>> subBom = bomService.flattenBom(materialCode, BigDecimal.ONE);
                if (subBom != null && !subBom.isEmpty()) {
                    MrpResult.ProductionSuggestion prod = new MrpResult.ProductionSuggestion();
                    prod.setMaterialCode(materialCode);
                    prod.setMaterialName((String) details.get("materialName"));
                    prod.setQuantity(netReq);
                    prod.setPlannedStartDate(plannedOrderDate);
                    prod.setPlannedFinishDate(demandDate);
                    prod.setSubMaterialCount(subBom.size());
                    productionSuggestions.add(prod);
                } else {
                    MrpResult.PurchaseSuggestion pur = new MrpResult.PurchaseSuggestion();
                    pur.setMaterialCode(materialCode);
                    pur.setMaterialName((String) details.get("materialName"));
                    pur.setQuantity(netReq);
                    pur.setUnit((String) details.get("unit"));
                    pur.setSuggestedOrderDate(plannedOrderDate);
                    pur.setSuggestedDeliveryDate(demandDate);
                    pur.setEstimatedAmount(netReq.multiply(
                            details.get("purchasePrice") != null ? (BigDecimal) details.get("purchasePrice") : BigDecimal.ZERO));
                    purchaseSuggestions.add(pur);
                }
            }
        }

        result.setRequirements(requirements);
        result.setPurchaseSuggestions(purchaseSuggestions);
        result.setProductionSuggestions(productionSuggestions);

        log.info("[MRP] 计算完成: 总需求{}项, 采购建议{}项, 生产建议{}项",
                requirements.size(), purchaseSuggestions.size(), productionSuggestions.size());
        return result;
    }

    private BigDecimal calculateInTransit(String materialCode) {
        BigDecimal inTransit = BigDecimal.ZERO;
        return inTransit;
    }

    /**
     * BOM展开 - 树形结构
     */
    public Map<String, Object> expandBom(String materialCode, BigDecimal quantity) {
        return bomService.expandBom(materialCode, quantity);
    }

    private BigDecimal calculateAllocated(String materialCode) {
        BigDecimal allocated = BigDecimal.ZERO;
        // 已下达状态的工单代表在产, 其未领料部分计入已分配
        List<WorkOrder> orders = workOrderMapper.selectList(
                new QueryWrapper<WorkOrder>().eq("status", "已下达"));
        for (WorkOrder order : orders) {
            List<WorkOrderMaterial> materials = workOrderMaterialMapper.selectList(
                    new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId())
                            .eq("material_code", materialCode));
            for (WorkOrderMaterial mat : materials) {
                BigDecimal required = mat.getRequiredQuantity() != null ? mat.getRequiredQuantity() : BigDecimal.ZERO;
                BigDecimal issued = mat.getIssuedQuantity() != null ? mat.getIssuedQuantity() : BigDecimal.ZERO;
                allocated = allocated.add(required.subtract(issued));
            }
        }
        return allocated.max(BigDecimal.ZERO);
    }

    @Data
    public static class MrpDemand {
        private String materialCode;
        private BigDecimal quantity;
        private LocalDate demandDate;

        public MrpDemand(String materialCode, BigDecimal quantity, LocalDate demandDate) {
            this.materialCode = materialCode;
            this.quantity = quantity;
            this.demandDate = demandDate;
        }
    }

    @Data
    public static class MrpResult {
        private List<MaterialRequirement> requirements = new ArrayList<>();
        private List<PurchaseSuggestion> purchaseSuggestions = new ArrayList<>();
        private List<ProductionSuggestion> productionSuggestions = new ArrayList<>();

        @Data
        public static class MaterialRequirement {
            private String materialCode;
            private String materialName;
            private String specification;
            private String unit;
            private BigDecimal grossRequirement;
            private BigDecimal onHandQuantity;
            private BigDecimal safetyStock;
            private BigDecimal inTransitQuantity;
            private BigDecimal allocatedQuantity;
            private BigDecimal netRequirement;
            private LocalDate plannedOrderDate;
            private Integer leadTimeDays;
        }

        @Data
        public static class PurchaseSuggestion {
            private String materialCode;
            private String materialName;
            private BigDecimal quantity;
            private String unit;
            private LocalDate suggestedOrderDate;
            private LocalDate suggestedDeliveryDate;
            private BigDecimal estimatedAmount;
        }

        @Data
        public static class ProductionSuggestion {
            private String materialCode;
            private String materialName;
            private BigDecimal quantity;
            private LocalDate plannedStartDate;
            private LocalDate plannedFinishDate;
            private Integer subMaterialCount;
        }
    }
}