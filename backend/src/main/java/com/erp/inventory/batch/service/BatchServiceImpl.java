package com.erp.inventory.batch.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.inventory.batch.entity.BatchInfo;
import com.erp.inventory.batch.entity.BatchInventory;
import com.erp.inventory.batch.mapper.BatchInfoMapper;
import com.erp.inventory.batch.mapper.BatchInventoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class BatchServiceImpl extends ServiceImpl<BatchInfoMapper, BatchInfo> implements BatchService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "available_qty",
            "batch_no",
            "batch_no_rule_default",
            "create_time",
            "created_at",
            "expiry_date",
            "id",
            "material_code",
            "material_id",
            "material_name",
            "production_date",
            "quantity",
            "remark",
            "specification",
            "status",
            "supplier_id",
            "supplier_name",
            "unit",
            "update_time"
        );

    private final BatchInventoryMapper batchInventoryMapper;
    private final SysConfigService sysConfigService;
    /** 统一数据权限解析器：批次列表 / 可用批次按「可见仓库」收敛（2026-10-09 权限收口） */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> getBatchRule(Long materialCategoryId) {
        String key = "batch_no_rule_" + (materialCategoryId != null ? materialCategoryId : "default");
        try {
            String val = sysConfigService.getConfigValue(key);
            if (val != null && !val.isBlank()) {
                return objectMapper.readValue(val, HashMap.class);
            }
        } catch (Exception e) {
            log.warn("读取批次配置失败: {}", key, e);
        }
        try {
            String defaultVal = sysConfigService.getConfigValue("batch_no_rule_default");
            if (defaultVal != null && !defaultVal.isBlank()) {
                return objectMapper.readValue(defaultVal, HashMap.class);
            }
        } catch (Exception e) {
            log.warn("读取默认批次配置失败", e);
        }
        Map<String, Object> def = new HashMap<>();
        def.put("allowModify", true);
        def.put("expireYear", 2);
        def.put("expireMonth", 0);
        def.put("expireDay", 0);
        def.put("prefix", "");
        def.put("dateFormat", "YYMMDD");
        def.put("includeMaterialCode", false);
        def.put("seqLength", 4);
        def.put("separator", "-");
        return def;
    }

    private String generateBatchNo(Map<String, Object> rule, String materialCode) {
        String prefix = (String) rule.getOrDefault("prefix", "");
        String dateFormat = (String) rule.getOrDefault("dateFormat", "YYMMDD");
        Boolean includeMaterialCode = (Boolean) rule.getOrDefault("includeMaterialCode", false);
        Integer seqLength = (Integer) rule.getOrDefault("seqLength", 4);
        String separator = (String) rule.getOrDefault("separator", "-");

        StringBuilder sb = new StringBuilder();
        if (!prefix.isBlank()) {
            sb.append(prefix);
        }
        if (!dateFormat.isBlank()) {
            if (sb.length() > 0 && !separator.isBlank()) sb.append(separator);
            LocalDate today = LocalDate.now();
            // 2026-11-06：DD 是"年内第几天"，必须换成 dd 才是"日"；YYYY/YY 换成 uuuu/uu 避免 week-based 年份
            String pattern = dateFormat.replace("YYYY", "uuuu").replace("YY", "uu").replace("DD", "dd");
            sb.append(today.format(DateTimeFormatter.ofPattern(pattern)));
        }
        if (Boolean.TRUE.equals(includeMaterialCode) && materialCode != null && !materialCode.isBlank()) {
            if (sb.length() > 0 && !separator.isBlank()) sb.append(separator);
            sb.append(materialCode);
        }
        if (sb.length() > 0 && !separator.isBlank()) sb.append(separator);
        String basePrefix = sb.toString();

        int seq = 1;
        String batchNo;
        do {
            batchNo = basePrefix + String.format("%0" + seqLength + "d", seq);
            seq++;
        } while (baseMapper.selectByBatchNo(batchNo) != null && seq < 10000);

        return batchNo;
    }

    private LocalDate calcExpireDate(Map<String, Object> rule, LocalDate productionDate) {
        Integer expireYear = (Integer) rule.getOrDefault("expireYear", 0);
        Integer expireMonth = (Integer) rule.getOrDefault("expireMonth", 0);
        Integer expireDay = (Integer) rule.getOrDefault("expireDay", 0);
        if (expireYear == 0 && expireMonth == 0 && expireDay == 0) {
            return null;
        }
        LocalDate base = productionDate != null ? productionDate : LocalDate.now();
        return base.plusYears(expireYear).plusMonths(expireMonth).plusDays(expireDay);
    }

    @Override
    public PageResult<BatchInfo> pageQuery(int current, int size, String keyword, Integer status, Long materialId, String sortBy, String sortOrder) {
        Page<BatchInfo> page = new Page<>(current, size);
        QueryWrapper<BatchInfo> wrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("batch_no", keyword)
                    .or().like("material_name", keyword)
                    .or().like("material_code", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (materialId != null) {
            wrapper.eq("material_id", materialId);
        }
        // 2026-10-09 权限收口：批次按「可见仓库」收敛（批次主表无仓库列，经 batch_inventory 关联）
        // 无任何可见仓库时直接返回空，不执行 SQL
        if (!dataScopeResolver.applyVisibleBatches(wrapper)) {
            return PageResult.of(java.util.Collections.emptyList(), 0L, current, size);
        }
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, BatchInfo.class);

        Page<BatchInfo> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public BatchInfo getDetail(Long id) {
        return getById(id);
    }

    @Override
    public List<BatchInventory> getBatchInventory(Long batchId) {
        return batchInventoryMapper.selectByBatchId(batchId);
    }

    @Override
    @Transactional
    public BatchInfo createBatch(BatchInfo batchInfo) {
        // BatchInfo 实体未直接持有 materialCategoryId，统一使用 default 规则
        // (后续如需按分类匹配规则，可通过 materialId 关联 material 表获取 category_id 后再传入)
        Map<String, Object> rule = getBatchRule(null);
        boolean allowModify = (Boolean) rule.getOrDefault("allowModify", true);

        if ((batchInfo.getBatchNo() == null || batchInfo.getBatchNo().isBlank())) {
            String generatedNo = generateBatchNo(rule, batchInfo.getMaterialCode());
            batchInfo.setBatchNo(generatedNo);
        } else if (!allowModify) {
            throw new BusinessException("当前配置不允许手动修改批次号");
        }

        BatchInfo existing = baseMapper.selectByBatchNo(batchInfo.getBatchNo());
        if (existing != null) {
            throw new BusinessException("批次号已存在: " + batchInfo.getBatchNo());
        }
        if (batchInfo.getQuantity() == null) {
            batchInfo.setQuantity(BigDecimal.ZERO);
        }
        if (batchInfo.getAvailableQty() == null) {
            batchInfo.setAvailableQty(batchInfo.getQuantity());
        }
        if (batchInfo.getStatus() == null) {
            batchInfo.setStatus(0);
        }
        if (batchInfo.getProductionDate() == null) {
            batchInfo.setProductionDate(LocalDate.now());
        }
        if (batchInfo.getExpiryDate() == null) {
            LocalDate expiry = calcExpireDate(rule, batchInfo.getProductionDate());
            batchInfo.setExpiryDate(expiry);
        }
        save(batchInfo);
        log.info("创建批次成功: {}", batchInfo.getBatchNo());
        return batchInfo;
    }

    @Override
    @Transactional
    public void inboundBatch(String batchNo, Long warehouseId, String warehouseName, Long locationId, String locationName, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("入库数量必须大于0");
        }

        BatchInfo batchInfo = baseMapper.selectByBatchNo(batchNo);
        if (batchInfo == null) {
            throw new BusinessException("批次不存在: " + batchNo);
        }

        batchInfo.setQuantity(batchInfo.getQuantity().add(quantity));
        batchInfo.setAvailableQty(batchInfo.getAvailableQty().add(quantity));
        if (batchInfo.getStatus() == 2) {
            batchInfo.setStatus(0);
        } else if (batchInfo.getStatus() == 0 && batchInfo.getAvailableQty().compareTo(quantity) > 0) {
            batchInfo.setStatus(1);
        }
        updateById(batchInfo);

        BatchInventory batchInventory = batchInventoryMapper.selectByBatchIdAndWarehouseId(batchInfo.getId(), warehouseId);
        if (batchInventory == null) {
            batchInventory = new BatchInventory();
            batchInventory.setBatchId(batchInfo.getId());
            batchInventory.setBatchNo(batchNo);
            batchInventory.setMaterialId(batchInfo.getMaterialId());
            batchInventory.setMaterialCode(batchInfo.getMaterialCode());
            batchInventory.setMaterialName(batchInfo.getMaterialName());
            batchInventory.setWarehouseId(warehouseId);
            batchInventory.setWarehouseName(warehouseName);
            batchInventory.setLocationId(locationId);
            batchInventory.setLocationName(locationName);
            batchInventory.setQuantity(quantity);
            batchInventory.setAvailableQty(quantity);
            batchInventoryMapper.insert(batchInventory);
        } else {
            batchInventory.setQuantity(batchInventory.getQuantity().add(quantity));
            batchInventory.setAvailableQty(batchInventory.getAvailableQty().add(quantity));
            if (locationId != null) {
                batchInventory.setLocationId(locationId);
                batchInventory.setLocationName(locationName);
            }
            batchInventoryMapper.updateById(batchInventory);
        }

        log.info("批次入库成功: {} 数量: {} 仓库: {}", batchNo, quantity, warehouseName);
    }

    @Override
    @Transactional
    public void outboundBatch(String batchNo, Long warehouseId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("出库数量必须大于0");
        }

        BatchInfo batchInfo = baseMapper.selectByBatchNo(batchNo);
        if (batchInfo == null) {
            throw new BusinessException("批次不存在: " + batchNo);
        }
        if (batchInfo.getAvailableQty().compareTo(quantity) < 0) {
            throw new BusinessException("批次可用库存不足，可用: " + batchInfo.getAvailableQty() + "，需要: " + quantity);
        }

        batchInfo.setAvailableQty(batchInfo.getAvailableQty().subtract(quantity));
        if (batchInfo.getAvailableQty().compareTo(BigDecimal.ZERO) == 0) {
            batchInfo.setStatus(2);
        } else {
            batchInfo.setStatus(1);
        }
        updateById(batchInfo);

        BatchInventory batchInventory = batchInventoryMapper.selectByBatchIdAndWarehouseId(batchInfo.getId(), warehouseId);
        if (batchInventory == null) {
            throw new BusinessException("该仓库无此批次库存");
        }
        if (batchInventory.getAvailableQty().compareTo(quantity) < 0) {
            throw new BusinessException("仓库批次可用库存不足，可用: " + batchInventory.getAvailableQty() + "，需要: " + quantity);
        }
        batchInventory.setAvailableQty(batchInventory.getAvailableQty().subtract(quantity));
        if (batchInventory.getAvailableQty().compareTo(BigDecimal.ZERO) == 0) {
            batchInventoryMapper.deleteById(batchInventory.getId());
        } else {
            batchInventoryMapper.updateById(batchInventory);
        }

        log.info("批次出库成功: {} 数量: {} 仓库: {}", batchNo, quantity, warehouseId);
    }

    @Override
    public List<BatchInfo> getAvailableBatches(Long materialId, Long warehouseId) {
        // 2026-10-09 权限收口：按「可见仓库」收敛（批次主表无仓库列，经 batch_inventory 关联）
        List<Long> visibleWh = dataScopeResolver.visibleWarehouseIdsForCurrentRequest();
        if (visibleWh != null) {
            if (warehouseId != null && !visibleWh.contains(warehouseId)) {
                // 选了不在可见范围内的仓库 → 直接空，不泄露其它仓库的批次
                return java.util.List.of();
            }
            if (warehouseId == null) {
                if (visibleWh.isEmpty()) return java.util.List.of();
                // 未指定仓库：只保留在「可见仓库」里有库存的批次
                List<Long> batchIds = batchInventoryMapper.selectList(
                                new QueryWrapper<BatchInventory>().in("warehouse_id", visibleWh))
                        .stream().map(BatchInventory::getBatchId)
                        .filter(java.util.Objects::nonNull).distinct().toList();
                if (batchIds.isEmpty()) return java.util.List.of();
                return baseMapper.selectAvailableBatches(materialId).stream()
                        .filter(b -> b != null && batchIds.contains(b.getId()))
                        .toList();
            }
        }
        if (warehouseId == null) {
            return baseMapper.selectAvailableBatches(materialId);
        }
        List<BatchInventory> inventoryList = batchInventoryMapper.selectAvailableByMaterialAndWarehouse(materialId, warehouseId);
        return inventoryList.stream()
                .map(inv -> {
                    BatchInfo batch = getById(inv.getBatchId());
                    if (batch != null) {
                        batch.setAvailableQty(inv.getAvailableQty());
                    }
                    return batch;
                })
                .filter(batch -> batch != null && batch.getStatus() != 3 && batch.getAvailableQty().compareTo(BigDecimal.ZERO) > 0)
                .sorted((b1, b2) -> {
                    LocalDate d1 = b1.getProductionDate();
                    LocalDate d2 = b2.getProductionDate();
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    return d1.compareTo(d2);
                })
                .toList();
    }

    @Override
    @Transactional
    public int checkExpiry() {
        List<BatchInfo> expiredBatches = baseMapper.selectExpiredBatches();
        int count = 0;
        for (BatchInfo batch : expiredBatches) {
            batch.setStatus(3);
            updateById(batch);
            count++;
            log.info("标记过期批次: {}", batch.getBatchNo());
        }
        log.info("检查过期批次完成，共标记 {} 个批次为过期", count);
        return count;
    }
}
