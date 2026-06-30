package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    private final MaterialMapper materialMapper;

    @Override
    public Map<String, Object> expandBom(String parentCode, BigDecimal quantity) {
        Map<String, Object> result = new HashMap<>();
        Material parent = materialMapper.selectOne(new QueryWrapper<Material>().eq("material_code", parentCode));
        if (parent == null) {
            return result;
        }

        result.put("materialCode", parent.getMaterialCode());
        result.put("materialName", parent.getMaterialName());
        result.put("specification", parent.getSpecification());
        result.put("unit", parent.getUnit());
        result.put("quantity", quantity);

        List<Map<String, Object>> children = new ArrayList<>();
        List<Bom> bomList = list(new QueryWrapper<Bom>().eq("parent_material_code", parentCode));

        for (Bom bom : bomList) {
            Material child = materialMapper.selectOne(new QueryWrapper<Material>().eq("material_code", bom.getChildMaterialCode()));
            if (child == null) continue;

            BigDecimal childQty = quantity.multiply(bom.getUsageQuantity()).setScale(4, RoundingMode.HALF_UP);

            Map<String, Object> childNode = new HashMap<>();
            childNode.put("materialCode", child.getMaterialCode());
            childNode.put("materialName", child.getMaterialName());
            childNode.put("specification", child.getSpecification());
            childNode.put("unit", child.getUnit());
            childNode.put("usageQuantity", bom.getUsageQuantity());
            childNode.put("totalQuantity", childQty);
            childNode.put("version", bom.getVersion());

            Map<String, Object> subBom = expandBom(child.getMaterialCode(), childQty);
            if (!subBom.isEmpty()) {
                childNode.put("children", subBom.get("children"));
            }

            children.add(childNode);
        }

        result.put("children", children);
        return result;
    }

    @Override
    public List<Map<String, Object>> flattenBom(String parentCode, BigDecimal quantity) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, BigDecimal> materialQuantities = new LinkedHashMap<>();
        Map<String, Map<String, Object>> materialDetails = new HashMap<>();

        flattenBomRecursive(parentCode, quantity, materialQuantities, materialDetails);

        for (Map.Entry<String, BigDecimal> entry : materialQuantities.entrySet()) {
            Map<String, Object> item = new HashMap<>(materialDetails.get(entry.getKey()));
            item.put("totalQuantity", entry.getValue());
            result.add(item);
        }

        return result;
    }

    private void flattenBomRecursive(String parentCode, BigDecimal quantity,
                                     Map<String, BigDecimal> materialQuantities,
                                     Map<String, Map<String, Object>> materialDetails) {
        Material parent = materialMapper.selectOne(new QueryWrapper<Material>().eq("material_code", parentCode));
        if (parent == null) return;

        List<Bom> bomList = list(new QueryWrapper<Bom>().eq("parent_material_code", parentCode));

        for (Bom bom : bomList) {
            Material child = materialMapper.selectOne(new QueryWrapper<Material>().eq("material_code", bom.getChildMaterialCode()));
            if (child == null) continue;

            BigDecimal childQty = quantity.multiply(bom.getUsageQuantity()).setScale(4, RoundingMode.HALF_UP);

            String key = child.getMaterialCode();
            materialQuantities.merge(key, childQty, BigDecimal::add);

            if (!materialDetails.containsKey(key)) {
                Map<String, Object> details = new HashMap<>();
                details.put("materialCode", child.getMaterialCode());
                details.put("materialName", child.getMaterialName());
                details.put("specification", child.getSpecification());
                details.put("unit", child.getUnit());
                details.put("purchasePrice", child.getPurchasePrice());
                details.put("salePrice", child.getSalePrice());
                details.put("leadTimeDays", child.getLeadTimeDays());
                details.put("batchManaged", child.getBatchManaged());
                details.put("defaultWarehouse", child.getDefaultWarehouse());
                materialDetails.put(key, details);
            }

            flattenBomRecursive(child.getMaterialCode(), childQty, materialQuantities, materialDetails);
        }
    }
}