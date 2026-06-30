package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {

    private final MaterialService materialService;
    private final InventoryTransactionService transactionService;

    @Override
    @Transactional
    public void addStock(String materialCode, String warehouseCode, BigDecimal quantity,
                        String documentNo, String documentType, Long sourceId, String operator) {
        Material material = materialService.getOne(
                new QueryWrapper<Material>().eq("material_code", materialCode));

        Inventory inv = getOne(new QueryWrapper<Inventory>()
                .eq("material_code", materialCode)
                .eq("warehouse_code", warehouseCode)
                .last("LIMIT 1"));

        if (inv == null) {
            inv = new Inventory();
            inv.setMaterialCode(materialCode);
            inv.setMaterialName(material != null ? material.getMaterialName() : materialCode);
            inv.setWarehouseCode(warehouseCode);
            inv.setQuantity(quantity);
            inv.setSafetyStock(material != null && material.getSafetyStock() != null ? material.getSafetyStock() : BigDecimal.ZERO);
            inv.setLockedQuantity(BigDecimal.ZERO);
            save(inv);
        } else {
            inv.setQuantity(inv.getQuantity().add(quantity));
            updateById(inv);
        }

        // 记录库存台账
        transactionService.recordTransaction(
                documentNo, documentType, materialCode,
                material != null ? material.getMaterialName() : materialCode,
                warehouseCode, quantity, sourceId, "INVENTORY", operator, null);

        log.info("库存增加: {} 仓库{} 数量{} 单据{}", materialCode, warehouseCode, quantity, documentNo);
    }

    @Override
    @Transactional
    public void deductStock(String materialCode, String warehouseCode, BigDecimal quantity,
                           String documentNo, String documentType, Long sourceId, String operator) {
        Material material = materialService.getOne(
                new QueryWrapper<Material>().eq("material_code", materialCode));

        Inventory inv = getOne(new QueryWrapper<Inventory>()
                .eq("material_code", materialCode)
                .eq("warehouse_code", warehouseCode)
                .last("LIMIT 1"));

        if (inv == null || inv.getQuantity().compareTo(quantity) < 0) {
            throw new BusinessException("物料 " + materialCode + " 库存不足");
        }

        inv.setQuantity(inv.getQuantity().subtract(quantity));
        updateById(inv);

        // 记录库存台账(数量用负数表示出库)
        transactionService.recordTransaction(
                documentNo, documentType, materialCode,
                material != null ? material.getMaterialName() : materialCode,
                warehouseCode, quantity.negate(), sourceId, "INVENTORY", operator, null);

        log.info("库存扣减: {} 仓库{} 数量{} 单据{}", materialCode, warehouseCode, quantity, documentNo);
    }

    @Override
    public List<Inventory> getByMaterial(String materialCode) {
        return list(new QueryWrapper<Inventory>().eq("material_code", materialCode));
    }

    @Override
    public List<Inventory> getWarningList() {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.apply("COALESCE(quantity, 0) < COALESCE(safety_stock, 0)");
        return list(wrapper);
    }
}
