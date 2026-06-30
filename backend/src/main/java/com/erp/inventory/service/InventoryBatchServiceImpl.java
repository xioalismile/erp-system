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
}
