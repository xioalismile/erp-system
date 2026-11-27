package com.erp.produce.mrp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomExplosionService {

    private final BomMapper bomMapper;
    private final BomItemMapper bomItemMapper;
    private final MaterialMapper materialMapper;

    public List<BomExplosionItem> explodeBom(Long materialId, BigDecimal quantity) {
        List<BomExplosionItem> result = new ArrayList<>();

        Bom bom = findActiveBom(materialId);

        if (bom == null) {
            return result;
        }

        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));

        for (BomItem bomItem : bomItems) {
            Material material = materialMapper.selectById(bomItem.getMaterialId());
            if (material == null) {
                continue;
            }

            BomExplosionItem item = new BomExplosionItem();
            item.setMaterialId(material.getMaterialId());
            item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getMaterialName());
            item.setSpecification(material.getModel());
            item.setUnitId(material.getBaseUnitId());
            item.setUsageQuantity(bomItem.getQuantity());
            item.setTotalQuantity(quantity.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP));
            item.setBomId(bom.getBomId());
            item.setLevel(1);
            item.setScrapRate(bomItem.getScrapRate() != null ? bomItem.getScrapRate() : BigDecimal.ZERO);

            result.add(item);
        }

        return result;
    }

    public List<BomExplosionItem> explodeBomMultiLevel(Long materialId, BigDecimal quantity, int maxLevel) {
        Map<String, BomExplosionItem> materialMap = new LinkedHashMap<>();
        explodeBomRecursive(materialId, quantity, 1, maxLevel, materialMap, null);
        return new ArrayList<>(materialMap.values());
    }

    /**
     * 按「指定 BOM」展开（2026-11-03）：首层用调用方选定的 BOM（同一物料可能有多份），
     * 下级继续按各子件自己的默认 BOM 展开。用户口径：BOM 要能下拉切换，切换后立即按该 BOM 展开物料。
     */
    public List<BomExplosionItem> explodeBomMultiLevelByBom(Long materialId, Long bomId,
                                                            BigDecimal quantity, int maxLevel) {
        Map<String, BomExplosionItem> materialMap = new LinkedHashMap<>();
        Bom firstBom = bomId == null ? null : bomMapper.selectById(bomId);
        explodeBomRecursiveWithFirst(firstBom, materialId, quantity, 1, maxLevel, materialMap, null);
        return new ArrayList<>(materialMap.values());
    }

    private void explodeBomRecursiveWithFirst(Bom firstBom, Long materialId, BigDecimal quantity, int level,
                                              int maxLevel, Map<String, BomExplosionItem> materialMap, Long parentId) {
        if (level > maxLevel) {
            return;
        }
        Bom bom = (level == 1 && firstBom != null) ? firstBom : findActiveBom(materialId);
        if (bom == null) {
            return;
        }
        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));
        for (BomItem bomItem : bomItems) {
            Material material = materialMapper.selectById(bomItem.getMaterialId());
            if (material == null) {
                continue;
            }
            BigDecimal totalQty = quantity.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP);
            String key = material.getMaterialCode();
            if (materialMap.containsKey(key)) {
                BomExplosionItem existing = materialMap.get(key);
                existing.setTotalQuantity(existing.getTotalQuantity().add(totalQty));
            } else {
                BomExplosionItem item = new BomExplosionItem();
                item.setMaterialId(material.getMaterialId());
                item.setMaterialCode(material.getMaterialCode());
                item.setMaterialName(material.getMaterialName());
                item.setSpecification(material.getModel());
                item.setUnitId(material.getBaseUnitId());
                item.setUnit(material.getBaseUnitName());
                item.setUsageQuantity(bomItem.getQuantity());
                item.setTotalQuantity(totalQty);
                item.setBomId(bom.getBomId());
                item.setBomCode(bom.getBomCode());
                item.setBomVersion(bom.getBomVersion());
                item.setLevel(level);
                item.setParentId(parentId);
                item.setScrapRate(bomItem.getScrapRate() != null ? bomItem.getScrapRate() : BigDecimal.ZERO);
                item.setMaterialType(material.getMaterialType());
                item.setSafetyStock(material.getMinStock() != null ? material.getMinStock() : BigDecimal.ZERO);
                item.setPurchasePrice(material.getPurchasePrice());
                materialMap.put(key, item);
            }
            explodeBomRecursiveWithFirst(null, material.getMaterialId(), totalQty, level + 1, maxLevel,
                    materialMap, material.getMaterialId());
        }
    }

    private void explodeBomRecursive(Long materialId, BigDecimal quantity, int level, int maxLevel,
                                     Map<String, BomExplosionItem> materialMap, Long parentId) {
        if (level > maxLevel) {
            return;
        }

        Bom bom = findActiveBom(materialId);

        if (bom == null) {
            return;
        }

        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));

        for (BomItem bomItem : bomItems) {
            Material material = materialMapper.selectById(bomItem.getMaterialId());
            if (material == null) {
                continue;
            }

            BigDecimal totalQty = quantity.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP);
            String key = material.getMaterialCode();

            if (materialMap.containsKey(key)) {
                BomExplosionItem existing = materialMap.get(key);
                existing.setTotalQuantity(existing.getTotalQuantity().add(totalQty));
            } else {
                BomExplosionItem item = new BomExplosionItem();
                item.setMaterialId(material.getMaterialId());
                item.setMaterialCode(material.getMaterialCode());
                item.setMaterialName(material.getMaterialName());
                item.setSpecification(material.getModel());
                item.setUnitId(material.getBaseUnitId());
                item.setUsageQuantity(bomItem.getQuantity());
                item.setTotalQuantity(totalQty);
                item.setBomId(bom.getBomId());
                item.setLevel(level);
                item.setParentId(parentId);
                item.setScrapRate(bomItem.getScrapRate() != null ? bomItem.getScrapRate() : BigDecimal.ZERO);
                item.setMaterialType(material.getMaterialType());
                item.setSafetyStock(material.getMinStock() != null ? material.getMinStock() : BigDecimal.ZERO);
                item.setPurchasePrice(material.getPurchasePrice());
                materialMap.put(key, item);
            }

            explodeBomRecursive(material.getMaterialId(), totalQty, level + 1, maxLevel, materialMap, material.getMaterialId());
        }
    }

    public Bom getDefaultBom(Long materialId) {
        return findActiveBom(materialId);
    }

    public boolean hasBom(Long materialId) {
        return findActiveBom(materialId) != null;
    }

    /**
     * 查找物料当前使用的 BOM，优先级与 BOM 主档/工单自动带出口径一致：
     * 默认BOM（已生效）→ 默认BOM → 主BOM（已生效）→ 主BOM → 最新已生效 → 最新。
     *
     * <p>历史坑：旧实现只认 is_primary=1 且 status=已生效，而 BOM 编辑页默认把 isPrimary 置 0，
     * 用户勾选「默认BOM」后实际是 is_default=1 / is_primary=0 → MRP 展开不到任何子件，
     * 表现为「选了母件物料后看不到 BOM 编号/版本，也看不到物料明细」。</p>
     */
    public Bom findActiveBom(Long materialId) {
        if (materialId == null) {
            return null;
        }
        return pickActiveBom(bomMapper.selectList(
                new QueryWrapper<Bom>().eq("material_id", materialId)));
    }

    /** 从同一物料的多条 BOM 中按「默认→主→已生效→最新」挑一条（批量场景复用，避免逐物料查库） */
    public Bom pickActiveBom(List<Bom> boms) {
        if (boms == null || boms.isEmpty()) {
            return null;
        }
        Bom best = null;
        int bestRank = Integer.MAX_VALUE;
        long bestId = Long.MIN_VALUE;
        for (Bom b : boms) {
            int rank = rankOfBom(b);
            long id = b.getBomId() == null ? 0L : b.getBomId();
            if (best == null || rank < bestRank || (rank == bestRank && id > bestId)) {
                best = b;
                bestRank = rank;
                bestId = id;
            }
        }
        return best;
    }

    private int rankOfBom(Bom b) {
        boolean effective = "已生效".equals(b.getStatus());
        boolean isDefault = b.getIsDefault() != null && b.getIsDefault() == 1;
        boolean isPrimary = b.getIsPrimary() != null && b.getIsPrimary() == 1;
        if (isDefault && effective) return 0;
        if (isDefault) return 1;
        if (isPrimary && effective) return 2;
        if (isPrimary) return 3;
        if (effective) return 4;
        return 5;
    }

    @Data
    public static class BomExplosionItem {
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String specification;
        private Long unitId;
        private String unit;
        private BigDecimal usageQuantity;
        private BigDecimal totalQuantity;
        private Long bomId;
        private String bomCode;
        private String bomVersion;
        private Integer level;
        private Long parentId;
        private BigDecimal scrapRate;
        private String materialType;
        private BigDecimal safetyStock;
        private BigDecimal purchasePrice;
    }
}
