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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {

    private final MaterialService materialService;
    private final InventoryTransactionService transactionService;

    @Override
    @Transactional
    public void addStock(Long stockId, Long binId, Long materialId, String batchNo,
                         BigDecimal quantity, BigDecimal unitCost,
                         String transNo, String transType,
                         String sourceBillType, String sourceBillNo, String operator) {
        Inventory inv = getOne(buildLocateWrapper(stockId, binId, materialId, batchNo).last("LIMIT 1"));

        BigDecimal safeUnitCost = unitCost != null ? unitCost : BigDecimal.ZERO;
        BigDecimal inAmount = quantity.multiply(safeUnitCost);

        if (inv == null) {
            inv = new Inventory();
            inv.setStockId(stockId);
            inv.setBinId(binId);
            inv.setMaterialId(materialId);
            inv.setBatchNo(batchNo);
            inv.setQuantity(quantity);
            inv.setAvailableQuantity(quantity);
            inv.setFrozenQuantity(BigDecimal.ZERO);
            BigDecimal newUnitCost = quantity.compareTo(BigDecimal.ZERO) != 0
                    ? inAmount.divide(quantity, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            inv.setUnitCost(newUnitCost);
            inv.setTotalCost(inAmount);
            inv.setLastInDate(LocalDate.now());
            save(inv);
        } else {
            BigDecimal oldTotalCost = inv.getTotalCost() != null ? inv.getTotalCost() : BigDecimal.ZERO;
            BigDecimal newQuantity = inv.getQuantity().add(quantity);
            BigDecimal newUnitCost = newQuantity.compareTo(BigDecimal.ZERO) != 0
                    ? oldTotalCost.add(inAmount).divide(newQuantity, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal newTotalCost = newQuantity.multiply(newUnitCost);
            inv.setQuantity(newQuantity);
            inv.setAvailableQuantity(inv.getAvailableQuantity().add(quantity));
            inv.setUnitCost(newUnitCost);
            inv.setTotalCost(newTotalCost);
            inv.setLastInDate(LocalDate.now());
            updateById(inv);
        }

        // 记录库存台账(入库: in_quantity=quantity, in_amount=quantity*unitCost)
        transactionService.recordTransaction(
                transNo, transType,
                stockId, binId, materialId, batchNo,
                quantity, inAmount,
                null, null,
                safeUnitCost,
                sourceBillType, sourceBillNo,
                operator, null);

        log.info("库存增加: 物料[{}] 仓库[{}] 数量{} 单据{}", materialId, stockId, quantity, transNo);
    }

    @Override
    @Transactional
    public void deductStock(Long stockId, Long binId, Long materialId, String batchNo,
                            BigDecimal quantity,
                            String transNo, String transType,
                            String sourceBillType, String sourceBillNo, String operator) {
        Inventory inv = getOne(buildLocateWrapper(stockId, binId, materialId, batchNo).last("LIMIT 1"));

        if (inv == null || inv.getQuantity() == null || inv.getQuantity().compareTo(quantity) < 0) {
            throw new BusinessException("物料[" + materialId + "]在仓库[" + stockId + "]库存不足");
        }

        BigDecimal unitCost = inv.getUnitCost() != null ? inv.getUnitCost() : BigDecimal.ZERO;
        BigDecimal outAmount = quantity.multiply(unitCost);

        inv.setQuantity(inv.getQuantity().subtract(quantity));
        inv.setAvailableQuantity(inv.getAvailableQuantity().subtract(quantity));
        inv.setLastOutDate(LocalDate.now());
        updateById(inv);

        // 记录库存台账(出库: out_quantity=quantity, out_amount=quantity*unitCost)
        transactionService.recordTransaction(
                transNo, transType,
                stockId, binId, materialId, batchNo,
                null, null,
                quantity, outAmount,
                unitCost,
                sourceBillType, sourceBillNo,
                operator, null);

        log.info("库存扣减: 物料[{}] 仓库[{}] 数量{} 单据{}", materialId, stockId, quantity, transNo);
    }

    @Override
    public List<Inventory> getByMaterialAndStock(Long materialId, Long stockId) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("material_id", materialId);
        wrapper.eq("stock_id", stockId);
        return list(wrapper);
    }

    @Override
    public List<Inventory> getWarningList() {
        List<Inventory> all = list();
        List<Inventory> warningList = new ArrayList<>();
        for (Inventory inv : all) {
            if (inv.getMaterialId() == null) {
                continue;
            }
            Material material = materialService.getById(inv.getMaterialId());
            if (material == null || material.getMinStock() == null) {
                continue;
            }
            BigDecimal qty = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(material.getMinStock()) < 0) {
                warningList.add(inv);
            }
        }
        return warningList;
    }

    /**
     * 按 (stock_id, bin_id, material_id, batch_no) 唯一索引构建定位查询
     * 注意: bin_id / batch_no 为 null 时使用 IS NULL, 否则 NULL 值无法匹配唯一索引
     */
    private QueryWrapper<Inventory> buildLocateWrapper(Long stockId, Long binId, Long materialId, String batchNo) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("stock_id", stockId);
        if (binId != null) {
            wrapper.eq("bin_id", binId);
        } else {
            wrapper.isNull("bin_id");
        }
        wrapper.eq("material_id", materialId);
        if (batchNo != null) {
            wrapper.eq("batch_no", batchNo);
        } else {
            wrapper.isNull("batch_no");
        }
        return wrapper;
    }
}
