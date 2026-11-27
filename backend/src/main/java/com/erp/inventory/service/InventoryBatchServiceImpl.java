package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.mapper.InventoryBatchMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class InventoryBatchServiceImpl extends ServiceImpl<InventoryBatchMapper, InventoryBatch> implements InventoryBatchService {

    @Override
    public List<InventoryBatch> getByMaterial(Long materialId) {
        return baseMapper.selectByMaterial(materialId);
    }

    @Override
    public InventoryBatch getByBatchNo(String batchNo) {
        return baseMapper.selectByBatchNo(batchNo);
    }

    @Override
    public InventoryBatch getByBatchNoAndMaterial(Long materialId, String batchNo) {
        if (materialId == null || batchNo == null || batchNo.isBlank()) return null;
        return baseMapper.selectByBatchNoAndMaterial(materialId, batchNo.trim());
    }

    @Override
    public List<InventoryBatch> getExpiredBatches() {
        return baseMapper.selectExpiredBatches();
    }

    @Override
    public List<InventoryBatch> getExpiringBatches(Integer days) {
        return baseMapper.selectExpiringBatches(days);
    }

    @Override
    public String generateBatchNo(Long materialId) {
        String prefix = "B" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<InventoryBatch> wrapper = new QueryWrapper<>();
        wrapper.likeRight("batch_no", prefix).orderByDesc("batch_no").last("LIMIT 1");
        InventoryBatch last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getBatchNo() != null) {
            String seqStr = last.getBatchNo().substring(prefix.length());
            try { seq = Integer.parseInt(seqStr) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }

    @Override
    @Transactional
    public Map<String, Object> allocateByFifo(Long materialId, BigDecimal quantity) {
        List<InventoryBatch> batches = getByMaterial(materialId);
        if (batches == null || batches.isEmpty()) {
            throw new BusinessException("物料 [" + materialId + "] 无批次库存");
        }

        BigDecimal allocated = BigDecimal.ZERO;
        List<Map<String, Object>> allocatedBatches = new ArrayList<>();

        for (InventoryBatch batch : batches) {
            if (allocated.compareTo(quantity) >= 0) break;

            BigDecimal available = batch.getQuantity().subtract(
                    batch.getFrozenQuantity() != null ? batch.getFrozenQuantity() : BigDecimal.ZERO);

            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal toAllocate = available.min(quantity.subtract(allocated));

            Map<String, Object> allocation = new HashMap<>();
            allocation.put("batchId", batch.getBatchId());
            allocation.put("batchNo", batch.getBatchNo());
            allocation.put("quantity", toAllocate);
            allocation.put("expiryDate", batch.getExpiryDate());
            allocatedBatches.add(allocation);

            allocated = allocated.add(toAllocate);
        }

        if (allocated.compareTo(quantity) < 0) {
            throw new BusinessException("物料 [" + materialId + "] 库存不足: 需要 " + quantity + "，可用 " + allocated);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("allocated", allocated);
        result.put("batches", allocatedBatches);
        return result;
    }

    @Override
    @Transactional
    public void deductBatch(Long batchId, BigDecimal quantity) {
        InventoryBatch batch = getById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }

        BigDecimal available = batch.getQuantity().subtract(
                batch.getFrozenQuantity() != null ? batch.getFrozenQuantity() : BigDecimal.ZERO);

        if (available.compareTo(quantity) < 0) {
            throw new BusinessException("批次 [" + batch.getBatchNo() + "] 库存不足");
        }

        batch.setQuantity(batch.getQuantity().subtract(quantity));
        batch.setAvailableQuantity(batch.getAvailableQuantity().subtract(quantity));
        updateById(batch);
        log.info("批次扣减: {} 扣减数量: {}", batch.getBatchNo(), quantity);
    }

    @Override
    @Transactional
    public void addBatch(InventoryBatch batch) {
        if (batch.getBatchNo() == null || batch.getBatchNo().isBlank()) {
            batch.setBatchNo(generateBatchNo(batch.getMaterialId()));
        }
        if (batch.getQuantity() != null && batch.getAvailableQuantity() == null) {
            batch.setAvailableQuantity(batch.getQuantity());
        }
        if (batch.getFrozenQuantity() == null) {
            batch.setFrozenQuantity(BigDecimal.ZERO);
        }
        if (batch.getStatus() == null) {
            batch.setStatus("正常");
        }
        save(batch);
        log.info("批次新增: {} 数量: {}", batch.getBatchNo(), batch.getQuantity());
    }

    /**
     * 入库时按 batchNo upsert 批次库存：
     * - 仅当 batchNo 非空时执行（无批次的物料不写批次表）
     * - batchNo 已存在则累加数量并按移动加权平均更新单位成本
     * - batchNo 不存在则新建批次记录
     */
    @Override
    @Transactional
    public void upsertByBatchNo(String batchNo, Long materialId, BigDecimal quantity, BigDecimal unitCost,
                                Long supplierId, LocalDate productionDate, LocalDate expiryDate, String manufacturingNo) {
        if (batchNo == null || batchNo.trim().isEmpty() || materialId == null || quantity == null) {
            return;
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal safeUnitCost = unitCost != null ? unitCost : BigDecimal.ZERO;
        // 2026-11-06：批次唯一口径=物料+批次号。只按批号匹配会把不同物料的同号数量串到一个物料上
        InventoryBatch exist = getByBatchNoAndMaterial(materialId, batchNo.trim());
        if (exist == null) {
            InventoryBatch batch = new InventoryBatch();
            batch.setBatchNo(batchNo.trim());
            batch.setMaterialId(materialId);
            batch.setQuantity(quantity);
            batch.setAvailableQuantity(quantity);
            batch.setFrozenQuantity(BigDecimal.ZERO);
            batch.setUnitCost(safeUnitCost);
            batch.setSupplierId(supplierId);
            batch.setProductionDate(productionDate);
            batch.setExpiryDate(expiryDate);
            batch.setManufacturingNo(manufacturingNo);
            batch.setIncomingDate(LocalDate.now());
            batch.setStatus("正常");
            save(batch);
            log.info("批次库存新建: batchNo={} 物料={} 数量={}", batchNo, materialId, quantity);
        } else {
            BigDecimal oldQty = exist.getQuantity() != null ? exist.getQuantity() : BigDecimal.ZERO;
            BigDecimal oldUnitCost = exist.getUnitCost() != null ? exist.getUnitCost() : BigDecimal.ZERO;
            BigDecimal newQty = oldQty.add(quantity);
            // 移动加权平均: 新单位成本 = (原数量*原单价 + 本次数量*本次单价) / 新数量
            BigDecimal newUnitCost = newQty.compareTo(BigDecimal.ZERO) != 0
                    ? oldQty.multiply(oldUnitCost).add(quantity.multiply(safeUnitCost))
                        .divide(newQty, 4, RoundingMode.HALF_UP)
                    : safeUnitCost;
            exist.setQuantity(newQty);
            exist.setAvailableQuantity((exist.getAvailableQuantity() != null ? exist.getAvailableQuantity() : BigDecimal.ZERO).add(quantity));
            exist.setUnitCost(newUnitCost);
            // 补充字段（仅当原值为空时才覆盖，避免丢失首次入库的有效信息）
            if (exist.getSupplierId() == null && supplierId != null) exist.setSupplierId(supplierId);
            if (exist.getProductionDate() == null && productionDate != null) exist.setProductionDate(productionDate);
            if (exist.getExpiryDate() == null && expiryDate != null) exist.setExpiryDate(expiryDate);
            if ((exist.getManufacturingNo() == null || exist.getManufacturingNo().isBlank()) && manufacturingNo != null) {
                exist.setManufacturingNo(manufacturingNo);
            }
            if (exist.getIncomingDate() == null) exist.setIncomingDate(LocalDate.now());
            if (exist.getStatus() == null) exist.setStatus("正常");
            updateById(exist);
            log.info("批次库存累加: batchNo={} 新增数量={} 累计数量={}", batchNo, quantity, newQty);
        }
    }
}
