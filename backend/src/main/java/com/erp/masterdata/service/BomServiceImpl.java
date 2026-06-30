package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    private final BomItemMapper bomItemMapper;
    private final MaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBomWithItems(Bom bom, List<BomItem> items) {
        boolean isNew = bom.getBomId() == null;

        if (bom.getBomCode() == null || bom.getBomCode().isEmpty()) {
            bom.setBomCode(generateBomCode());
        }
        if (bom.getStatus() == null || bom.getStatus().isEmpty()) {
            bom.setStatus("已审核");
        }
        if (bom.getApprovalStatus() == null || bom.getApprovalStatus().isEmpty()) {
            bom.setApprovalStatus("待审核");
        }
        if (bom.getBomType() == null || bom.getBomType().isEmpty()) {
            bom.setBomType("标准BOM");
        }
        if (bom.getIsPrimary() == null) {
            bom.setIsPrimary((short) 1);
        }
        if (bom.getIsEnabled() == null) {
            bom.setIsEnabled((short) 1);
        }
        if (bom.getQuantity() == null) {
            bom.setQuantity(BigDecimal.ONE);
        }

        // 通过 materialId 查 Material 拿 materialCode/materialName (若空)
        if (bom.getMaterialId() != null) {
            Material material = materialMapper.selectById(bom.getMaterialId());
            if (material != null) {
                if (bom.getMaterialCode() == null || bom.getMaterialCode().isEmpty()) {
                    bom.setMaterialCode(material.getMaterialCode());
                }
                if (bom.getMaterialName() == null || bom.getMaterialName().isEmpty()) {
                    bom.setMaterialName(material.getMaterialName());
                }
            }
        }

        if (isNew) {
            save(bom);
        } else {
            // 编辑: 先删旧明细, 再更新主单
            bomItemMapper.delete(new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));
            updateById(bom);
        }

        // 循环 insert items, lineNo 从 10 递增 10
        if (items != null && !items.isEmpty()) {
            int lineNo = 10;
            for (BomItem item : items) {
                item.setBomItemId(null);
                item.setBomId(bom.getBomId());
                item.setLineNo(lineNo);

                // 通过 materialId 查 Material 拿 materialCode/materialName/specification(用Material的model字段)
                if (item.getMaterialId() != null) {
                    Material m = materialMapper.selectById(item.getMaterialId());
                    if (m != null) {
                        if (item.getMaterialCode() == null || item.getMaterialCode().isEmpty()) {
                            item.setMaterialCode(m.getMaterialCode());
                        }
                        if (item.getMaterialName() == null || item.getMaterialName().isEmpty()) {
                            item.setMaterialName(m.getMaterialName());
                        }
                        if (item.getSpecification() == null || item.getSpecification().isEmpty()) {
                            item.setSpecification(m.getModel());
                        }
                    }
                }
                if (item.getIsOptional() == null) {
                    item.setIsOptional((short) 0);
                }
                if (item.getQuantity() == null) {
                    item.setQuantity(BigDecimal.ZERO);
                }

                bomItemMapper.insert(item);
                lineNo += 10;
            }
        }
    }

    @Override
    public PageResult<Bom> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<Bom> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("bom_code", keyword).or().like("bom_name", keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");

        Page<Bom> page = new Page<>(current, size);
        Page<Bom> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Bom getDetail(Long id) {
        Bom bom = getById(id);
        if (bom == null) {
            throw new RuntimeException("BOM不存在: " + id);
        }
        List<BomItem> items = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", id).orderByAsc("line_no"));
        bom.setItems(items);
        return bom;
    }

    @Override
    public Map<String, Object> expandBom(String materialCode, BigDecimal quantity) {
        Map<String, Object> result = new HashMap<>();
        if (materialCode == null) {
            return result;
        }

        Material parent = materialMapper.selectOne(
                new QueryWrapper<Material>().eq("material_code", materialCode));
        if (parent == null) {
            return result;
        }

        // 通过 materialId 查主BOM (is_primary=1)
        Bom bom = getOne(new QueryWrapper<Bom>()
                .eq("material_id", parent.getMaterialId())
                .eq("is_primary", (short) 1)
                .last("LIMIT 1"));

        result.put("materialCode", parent.getMaterialCode());
        result.put("materialName", parent.getMaterialName());
        result.put("specification", parent.getModel());
        result.put("unit", parent.getBaseUnitId() != null ? parent.getBaseUnitId().toString() : "");
        result.put("quantity", quantity);

        List<Map<String, Object>> children = new ArrayList<>();
        if (bom != null) {
            List<BomItem> bomItems = bomItemMapper.selectList(
                    new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));
            for (BomItem bomItem : bomItems) {
                Material child = materialMapper.selectById(bomItem.getMaterialId());
                if (child == null) {
                    continue;
                }

                BigDecimal childQty = quantity.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP);

                Map<String, Object> childNode = new HashMap<>();
                childNode.put("materialCode", child.getMaterialCode());
                childNode.put("materialName", child.getMaterialName());
                childNode.put("specification", child.getModel());
                childNode.put("unit", child.getBaseUnitId() != null ? child.getBaseUnitId().toString() : "");
                childNode.put("usageQuantity", bomItem.getQuantity());
                childNode.put("totalQuantity", childQty);

                Map<String, Object> subBom = expandBom(child.getMaterialCode(), childQty);
                if (!subBom.isEmpty()) {
                    childNode.put("children", subBom.get("children"));
                }

                children.add(childNode);
            }
        }

        result.put("children", children);
        return result;
    }

    @Override
    public List<Map<String, Object>> flattenBom(String materialCode, BigDecimal quantity) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (materialCode == null) {
            return result;
        }

        Map<String, BigDecimal> materialQuantities = new LinkedHashMap<>();
        Map<String, Map<String, Object>> materialDetails = new HashMap<>();

        flattenBomRecursive(materialCode, quantity, materialQuantities, materialDetails);

        for (Map.Entry<String, BigDecimal> entry : materialQuantities.entrySet()) {
            Map<String, Object> item = new HashMap<>(materialDetails.get(entry.getKey()));
            item.put("totalQuantity", entry.getValue());
            result.add(item);
        }

        return result;
    }

    private void flattenBomRecursive(String materialCode, BigDecimal quantity,
                                     Map<String, BigDecimal> materialQuantities,
                                     Map<String, Map<String, Object>> materialDetails) {
        Material parent = materialMapper.selectOne(
                new QueryWrapper<Material>().eq("material_code", materialCode));
        if (parent == null) {
            return;
        }

        Bom bom = getOne(new QueryWrapper<Bom>()
                .eq("material_id", parent.getMaterialId())
                .eq("is_primary", (short) 1)
                .last("LIMIT 1"));
        if (bom == null) {
            return;
        }

        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));

        for (BomItem bomItem : bomItems) {
            Material child = materialMapper.selectById(bomItem.getMaterialId());
            if (child == null) {
                continue;
            }

            BigDecimal childQty = quantity.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP);
            String key = child.getMaterialCode();
            materialQuantities.merge(key, childQty, BigDecimal::add);

            if (!materialDetails.containsKey(key)) {
                Map<String, Object> details = new HashMap<>();
                details.put("materialCode", child.getMaterialCode());
                details.put("materialName", child.getMaterialName());
                details.put("specification", child.getModel());
                details.put("unit", child.getBaseUnitId() != null ? child.getBaseUnitId().toString() : "");
                details.put("purchasePrice", child.getPurchasePrice());
                details.put("salePrice", child.getSalePrice());
                details.put("leadTimeDays", 7);
                materialDetails.put(key, details);
            }

            flattenBomRecursive(child.getMaterialCode(), childQty, materialQuantities, materialDetails);
        }
    }

    @Override
    public String generateBomCode() {
        String prefix = "BOM" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<Bom> wrapper = new QueryWrapper<>();
        wrapper.likeRight("bom_code", prefix).orderByDesc("bom_code").last("LIMIT 1");
        Bom last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getBomCode() != null) {
            try {
                seq = Integer.parseInt(last.getBomCode().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
