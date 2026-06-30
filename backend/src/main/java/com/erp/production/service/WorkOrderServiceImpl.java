package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.service.InventoryBatchService;
import com.erp.inventory.service.InventoryService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.BomService;
import com.erp.masterdata.service.MaterialService;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.mapper.WorkOrderMaterialMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService {

    private final WorkOrderMaterialMapper materialMapper;
    private final BomService bomService;
    private final MaterialService materialService;
    private final InventoryService inventoryService;
    private final InventoryBatchService batchService;
    private final SaleOrderService saleOrderService;

    @Override
    @Transactional
    public WorkOrder createWorkOrder(WorkOrder workOrder) {
        if (workOrder.getWorkOrderNo() == null || workOrder.getWorkOrderNo().isBlank()) {
            workOrder.setWorkOrderNo(generateWorkOrderNo());
        }
        if (workOrder.getStatus() == null) workOrder.setStatus("待开工");
        if (workOrder.getCompletedQuantity() == null) workOrder.setCompletedQuantity(BigDecimal.ZERO);
        if (workOrder.getScrapQuantity() == null) workOrder.setScrapQuantity(BigDecimal.ZERO);
        if (workOrder.getOrderDate() == null) workOrder.setOrderDate(LocalDate.now());
        if (workOrder.getSourceType() == null) workOrder.setSourceType("MANUAL");

        Material product = materialService.getOne(
                new QueryWrapper<Material>().eq("material_code", workOrder.getProductCode()));
        if (product != null) {
            workOrder.setProductName(product.getMaterialName());
            workOrder.setSpecification(product.getSpecification());
            workOrder.setUnit(product.getUnit());
            if (workOrder.getPlannedStartDate() == null) {
                workOrder.setPlannedStartDate(LocalDate.now());
            }
            if (workOrder.getPlannedFinishDate() == null) {
                workOrder.setPlannedFinishDate(LocalDate.now().plusDays(product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 7));
            }
        }

        save(workOrder);

        List<Map<String, Object>> bomItems = bomService.flattenBom(workOrder.getProductCode(), workOrder.getPlannedQuantity());
        int lineNo = 10;
        for (Map<String, Object> bomItem : bomItems) {
            WorkOrderMaterial wom = new WorkOrderMaterial();
            wom.setWorkOrderId(workOrder.getId());
            wom.setLineNo(lineNo);
            wom.setMaterialCode((String) bomItem.get("materialCode"));
            wom.setMaterialName((String) bomItem.get("materialName"));
            wom.setSpecification((String) bomItem.get("specification"));
            wom.setUnit((String) bomItem.get("unit"));
            wom.setRequiredQuantity((BigDecimal) bomItem.get("totalQuantity"));
            wom.setIssuedQuantity(BigDecimal.ZERO);
            wom.setWarehouse((String) bomItem.get("defaultWarehouse"));
            materialMapper.insert(wom);
            lineNo += 10;
        }

        log.info("生产工单创建: {} 产品: {} 数量: {} 用料{}项",
                workOrder.getWorkOrderNo(), workOrder.getProductCode(), workOrder.getPlannedQuantity(), bomItems.size());
        return workOrder;
    }

    @Override
    @Transactional
    public void updateWorkOrder(WorkOrder workOrder) {
        materialMapper.delete(new QueryWrapper<WorkOrderMaterial>().eq("work_order_id", workOrder.getId()));
        updateById(workOrder);
        if (workOrder.getMaterials() != null) {
            int lineNo = 10;
            for (WorkOrderMaterial wom : workOrder.getMaterials()) {
                wom.setId(null);
                wom.setWorkOrderId(workOrder.getId());
                wom.setLineNo(lineNo);
                materialMapper.insert(wom);
                lineNo += 10;
            }
        }
    }

    @Override
    public PageResult<WorkOrder> pageQuery(int current, int size, String productCode, String status, String workOrderNo) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (productCode != null && !productCode.isBlank()) wrapper.like("product_code", productCode);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (workOrderNo != null && !workOrderNo.isBlank()) wrapper.like("work_order_no", workOrderNo);
        Page<WorkOrder> page = new Page<>(current, size);
        Page<WorkOrder> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public WorkOrder getDetail(Long id) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");
        List<WorkOrderMaterial> materials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("work_order_id", id).orderByAsc("line_no"));
        wo.setMaterials(materials);
        return wo;
    }

    @Override
    @Transactional
    public void issueMaterial(Long id, Long materialId, BigDecimal quantity) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");
        if (!"待开工".equals(wo.getStatus()) && !"已开工".equals(wo.getStatus())) {
            throw new BusinessException("只有待开工或已开工状态可领料");
        }

        WorkOrderMaterial wom = materialMapper.selectById(materialId);
        if (wom == null || !wom.getWorkOrderId().equals(id)) {
            throw new BusinessException("工单用料明细不存在");
        }

        BigDecimal remaining = wom.getRequiredQuantity().subtract(wom.getIssuedQuantity() != null ? wom.getIssuedQuantity() : BigDecimal.ZERO);
        if (quantity.compareTo(remaining) > 0) {
            throw new BusinessException("领料数量超出剩余需求: 需要" + quantity + ", 剩余" + remaining);
        }

        Material material = materialService.getOne(
                new QueryWrapper<Material>().eq("material_code", wom.getMaterialCode()));

        if (material != null && Boolean.TRUE.equals(material.getBatchManaged())) {
            deductBatchInventory(wom.getMaterialCode(), wom.getWarehouse(), quantity);
        } else {
            deductNonBatchInventory(wom.getMaterialCode(), wom.getWarehouse(), quantity);
        }

        wom.setIssuedQuantity(wom.getIssuedQuantity().add(quantity));
        materialMapper.updateById(wom);

        if ("待开工".equals(wo.getStatus())) {
            boolean allIssued = isAllMaterialsIssued(id);
            if (allIssued) {
                wo.setStatus("已开工");
                wo.setActualStartDate(LocalDate.now());
                updateById(wo);
            }
        }

        log.info("工单领料: {} 物料: {} 数量: {}", wo.getWorkOrderNo(), wom.getMaterialCode(), quantity);
    }

    private boolean isAllMaterialsIssued(Long workOrderId) {
        List<WorkOrderMaterial> materials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("work_order_id", workOrderId));
        for (WorkOrderMaterial wom : materials) {
            BigDecimal issued = wom.getIssuedQuantity() != null ? wom.getIssuedQuantity() : BigDecimal.ZERO;
            if (issued.compareTo(wom.getRequiredQuantity()) < 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional
    public void startWork(Long id) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");
        if (!"待开工".equals(wo.getStatus())) {
            throw new BusinessException("只有待开工状态可开工，当前: " + wo.getStatus());
        }

        List<WorkOrderMaterial> materials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("work_order_id", id));

        for (WorkOrderMaterial wom : materials) {
            Material material = materialService.getOne(
                    new QueryWrapper<Material>().eq("material_code", wom.getMaterialCode()));

            BigDecimal toIssue = wom.getRequiredQuantity().subtract(
                    wom.getIssuedQuantity() != null ? wom.getIssuedQuantity() : BigDecimal.ZERO);

            if (toIssue.compareTo(BigDecimal.ZERO) > 0) {
                if (material != null && Boolean.TRUE.equals(material.getBatchManaged())) {
                    deductBatchInventory(wom.getMaterialCode(), wom.getWarehouse(), toIssue);
                } else {
                    deductNonBatchInventory(wom.getMaterialCode(), wom.getWarehouse(), toIssue);
                }
                wom.setIssuedQuantity(wom.getRequiredQuantity());
                materialMapper.updateById(wom);
            }
        }

        wo.setStatus("已开工");
        wo.setActualStartDate(LocalDate.now());
        updateById(wo);
        log.info("工单开工: {} 原材料已扣减", wo.getWorkOrderNo());
    }

    private void deductBatchInventory(String materialCode, String warehouseCode, BigDecimal quantity) {
        Map<String, Object> allocation = batchService.allocateByFifo(materialCode, warehouseCode, quantity);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batches = (List<Map<String, Object>>) allocation.get("batches");

        for (Map<String, Object> batch : batches) {
            Long batchId = ((Number) batch.get("batchId")).longValue();
            BigDecimal qty = (BigDecimal) batch.get("quantity");
            batchService.deductBatch(batchId, qty);
        }
    }

    private void deductNonBatchInventory(String materialCode, String warehouseCode, BigDecimal quantity) {
        Inventory inv = inventoryService.getOne(
                new QueryWrapper<Inventory>()
                        .eq("material_code", materialCode)
                        .eq(warehouseCode != null, "warehouse_code", warehouseCode)
                        .last("LIMIT 1"));

        if (inv == null) {
            throw new BusinessException("物料 " + materialCode + " 在仓库 " + warehouseCode + " 无库存记录");
        }

        BigDecimal available = inv.getQuantity().subtract(
                inv.getLockedQuantity() != null ? inv.getLockedQuantity() : BigDecimal.ZERO);

        if (available.compareTo(quantity) < 0) {
            throw new BusinessException("物料 " + materialCode + " 库存不足: 需要 " + quantity + "，可用 " + available);
        }

        inv.setQuantity(inv.getQuantity().subtract(quantity));
        inventoryService.updateById(inv);
    }

    @Override
    @Transactional
    public void reportCompletion(Long id, BigDecimal completedQty, BigDecimal scrapQty) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");
        if (!"已开工".equals(wo.getStatus())) {
            throw new BusinessException("只有已开工状态可报工，当前: " + wo.getStatus());
        }

        BigDecimal totalProduced = wo.getCompletedQuantity().add(completedQty);
        if (totalProduced.compareTo(wo.getPlannedQuantity()) > 0) {
            throw new BusinessException("完工数量不能超过计划数量: 计划" + wo.getPlannedQuantity() + ", 当前累计" + totalProduced);
        }

        wo.setCompletedQuantity(totalProduced);
        if (scrapQty != null) {
            wo.setScrapQuantity(wo.getScrapQuantity().add(scrapQty));
        }
        updateById(wo);
        log.info("工单报工: {} 完工{} 报废{} 累计完工{}", wo.getWorkOrderNo(), completedQty, scrapQty, wo.getCompletedQuantity());
    }

    @Override
    @Transactional
    public void finishWork(Long id) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");
        if (!"已开工".equals(wo.getStatus())) {
            throw new BusinessException("只有已开工状态可完工，当前: " + wo.getStatus());
        }

        BigDecimal outputQty = wo.getCompletedQuantity();
        if (outputQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("完工数量必须大于0");
        }

        Inventory inv = inventoryService.getOne(
                new QueryWrapper<Inventory>().eq("material_code", wo.getProductCode())
                        .eq("warehouse_code", "WH002")
                        .last("LIMIT 1"));

        if (inv == null) {
            inv = new Inventory();
            inv.setMaterialCode(wo.getProductCode());
            inv.setMaterialName(wo.getProductName());
            inv.setWarehouseCode("WH002");
            inv.setQuantity(outputQty);
            inv.setSafetyStock(BigDecimal.ZERO);
            inv.setLockedQuantity(BigDecimal.ZERO);
            inventoryService.save(inv);
        } else {
            inv.setQuantity(inv.getQuantity().add(outputQty));
            inventoryService.updateById(inv);
        }

        wo.setStatus("已完工");
        wo.setActualFinishDate(LocalDate.now());
        updateById(wo);
        log.info("工单完工入库: {} 成品: {} 数量: {}", wo.getWorkOrderNo(), wo.getProductCode(), outputQty);
    }

    @Override
    @Transactional
    public void closeWork(Long id) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");
        if (!"已完工".equals(wo.getStatus())) {
            throw new BusinessException("只有已完工状态可关闭");
        }
        wo.setStatus("已关闭");
        updateById(wo);
        log.info("工单关闭: {}", wo.getWorkOrderNo());
    }

    @Override
    @Transactional
    public void cancelWork(Long id, String reason) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");
        if ("已完工".equals(wo.getStatus()) || "已关闭".equals(wo.getStatus())) {
            throw new BusinessException("已完工/已关闭的工单不可取消");
        }

        if ("已开工".equals(wo.getStatus())) {
            List<WorkOrderMaterial> materials = materialMapper.selectList(
                    new QueryWrapper<WorkOrderMaterial>().eq("work_order_id", id));
            for (WorkOrderMaterial wom : materials) {
                if (wom.getIssuedQuantity() != null && wom.getIssuedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    Material material = materialService.getOne(
                            new QueryWrapper<Material>().eq("material_code", wom.getMaterialCode()));

                    if (material != null && Boolean.TRUE.equals(material.getBatchManaged())) {
                        InventoryBatch batch = new InventoryBatch();
                        batch.setMaterialCode(wom.getMaterialCode());
                        batch.setMaterialName(wom.getMaterialName());
                        batch.setWarehouseCode(wom.getWarehouse());
                        batch.setQuantity(wom.getIssuedQuantity());
                        batch.setProductionDate(LocalDate.now());
                        batchService.addBatch(batch);
                    } else {
                        Inventory inv = inventoryService.getOne(
                                new QueryWrapper<Inventory>().eq("material_code", wom.getMaterialCode()).last("LIMIT 1"));
                        if (inv != null) {
                            inv.setQuantity(inv.getQuantity().add(wom.getIssuedQuantity()));
                            inventoryService.updateById(inv);
                        }
                    }
                }
            }
        }

        wo.setStatus("已取消");
        if (reason != null) wo.setRemark((wo.getRemark() != null ? wo.getRemark() + " | " : "") + "取消: " + reason);
        updateById(wo);
        log.info("工单取消: {} 原因: {}", wo.getWorkOrderNo(), reason);
    }

    @Override
    public Map<String, Object> getProgress(Long id) {
        WorkOrder wo = getById(id);
        if (wo == null) throw new BusinessException("工单不存在");

        Map<String, Object> progress = new HashMap<>();
        progress.put("workOrderNo", wo.getWorkOrderNo());
        progress.put("status", wo.getStatus());
        progress.put("productCode", wo.getProductCode());
        progress.put("productName", wo.getProductName());

        BigDecimal totalPlanned = wo.getPlannedQuantity();
        BigDecimal totalCompleted = wo.getCompletedQuantity();
        BigDecimal totalScrap = wo.getScrapQuantity();

        progress.put("plannedQuantity", totalPlanned);
        progress.put("completedQuantity", totalCompleted);
        progress.put("scrapQuantity", totalScrap);

        BigDecimal yieldRate = BigDecimal.ZERO;
        if (totalCompleted.add(totalScrap).compareTo(BigDecimal.ZERO) > 0) {
            yieldRate = totalCompleted.multiply(new BigDecimal("100"))
                    .divide(totalCompleted.add(totalScrap), 2, BigDecimal.ROUND_HALF_UP);
        }
        progress.put("yieldRate", yieldRate);

        BigDecimal completionRate = BigDecimal.ZERO;
        if (totalPlanned.compareTo(BigDecimal.ZERO) > 0) {
            completionRate = totalCompleted.multiply(new BigDecimal("100"))
                    .divide(totalPlanned, 2, BigDecimal.ROUND_HALF_UP);
        }
        progress.put("completionRate", completionRate);

        List<WorkOrderMaterial> materials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("work_order_id", id));

        BigDecimal totalRequired = BigDecimal.ZERO;
        BigDecimal totalIssued = BigDecimal.ZERO;
        for (WorkOrderMaterial wom : materials) {
            totalRequired = totalRequired.add(wom.getRequiredQuantity());
            totalIssued = totalIssued.add(wom.getIssuedQuantity() != null ? wom.getIssuedQuantity() : BigDecimal.ZERO);
        }

        progress.put("materialRequired", totalRequired);
        progress.put("materialIssued", totalIssued);

        BigDecimal materialIssueRate = BigDecimal.ZERO;
        if (totalRequired.compareTo(BigDecimal.ZERO) > 0) {
            materialIssueRate = totalIssued.multiply(new BigDecimal("100"))
                    .divide(totalRequired, 2, BigDecimal.ROUND_HALF_UP);
        }
        progress.put("materialIssueRate", materialIssueRate);

        return progress;
    }

    @Override
    @Transactional
    public List<WorkOrder> generateFromMrp(List<Map<String, Object>> mrpSuggestions, LocalDate demandDate) {
        List<WorkOrder> result = new ArrayList<>();
        for (Map<String, Object> suggestion : mrpSuggestions) {
            WorkOrder wo = new WorkOrder();
            wo.setOrderDate(LocalDate.now());
            wo.setProductCode((String) suggestion.get("materialCode"));
            wo.setProductName((String) suggestion.get("materialName"));
            wo.setPlannedQuantity(new BigDecimal(suggestion.get("quantity").toString()));
            wo.setPlannedStartDate(demandDate.minusDays(14));
            wo.setPlannedFinishDate(demandDate);
            wo.setSourceType("MRP");
            wo.setStatus("待开工");
            wo.setWorkshop("主车间");
            createWorkOrder(wo);
            result.add(wo);
        }
        log.info("从MRP生成工单: {}个", result.size());
        return result;
    }

    @Override
    @Transactional
    public List<WorkOrder> generateFromSaleOrder(Long saleOrderId, List<Long> itemIds) {
        SaleOrder saleOrder = saleOrderService.getDetail(saleOrderId);
        if (saleOrder == null) {
            throw new BusinessException("销售订单不存在");
        }

        List<WorkOrder> result = new ArrayList<>();
        List<SaleOrderItem> itemsToProcess = new ArrayList<>();

        if (itemIds == null || itemIds.isEmpty()) {
            itemsToProcess = saleOrder.getItems();
        } else {
            for (SaleOrderItem item : saleOrder.getItems()) {
                if (itemIds.contains(item.getId())) {
                    itemsToProcess.add(item);
                }
            }
        }

        for (SaleOrderItem item : itemsToProcess) {
            if (item.getIsGift() != null && item.getIsGift() == 1) {
                continue;
            }

            BigDecimal remainQty = item.getQuantity().subtract(
                    item.getShippedQuantity() != null ? item.getShippedQuantity() : BigDecimal.ZERO);
            if (remainQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            WorkOrder wo = new WorkOrder();
            wo.setOrderDate(LocalDate.now());
            wo.setProductCode(item.getMaterialCode());
            wo.setProductName(item.getMaterialName());
            wo.setSpecification(item.getSpecification());
            wo.setUnit(item.getUnit());
            wo.setPlannedQuantity(remainQty);
            wo.setSourceType("SALE_ORDER");
            wo.setSourceOrderNo(saleOrder.getOrderNo());
            wo.setPlannedStartDate(LocalDate.now());
            wo.setPlannedFinishDate(item.getDeliveryDate() != null ? item.getDeliveryDate() : LocalDate.now().plusDays(7));
            wo.setWorkshop("主车间");
            wo.setStatus("待开工");

            createWorkOrder(wo);
            result.add(wo);
        }

        log.info("从销售订单{}生成工单: {}个", saleOrder.getOrderNo(), result.size());
        return result;
    }

    @Override
    public String generateWorkOrderNo() {
        String prefix = "WO" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("work_order_no", prefix).orderByDesc("work_order_no").last("LIMIT 1");
        WorkOrder last = getOne(wrapper);
        int seq = 1;
        if (last != null) {
            try { seq = Integer.parseInt(last.getWorkOrderNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }
}