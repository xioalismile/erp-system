package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.inventory.service.MaterialRequisitionService;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.production.entity.WorkOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产补料领料单生成器（唯一入口）。
 *
 * <p><b>为什么要抽出来</b>：补料单有两处入口 —— ① 工序汇报单报工报废（2026-09 既有能力）；
 * ② 工序回退判废后一键「下推补料」（2026-09-27 第 4 期新增）。两处口径必须完全一致
 * （数量 = 报废/判废数 × 主BOM 子件用量、单据 = 补料领料单草稿），否则同一张 BOM 会算出两套数量。</p>
 *
 * <p><b>单据口径</b>：生成的补料领料单为草稿（保存=草稿），生效走用户在领料单页点「提交」
 * （免审批时提交即生效），因此这里不自动扣库存。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionSupplementHelper {

    private final BomMapper bomMapper;
    private final BomItemMapper bomItemMapper;
    private final MaterialMapper materialMapper;
    private final MaterialRequisitionService materialRequisitionService;

    /**
     * 按"报废/判废数量 × 主BOM 子件用量"生成一张补料领料单。
     *
     * @param workOrder    生产工单（需有 materialId 与 warehouseId）
     * @param scrapQty     报废/判废数量
     * @param itemRemark   明细行备注
     * @param billRemark   单据头备注
     * @return 生成的补料领料单号；未生成时返回 null（无工单/无主BOM/无子件/数量非正）
     */
    public String generateSupplement(WorkOrder workOrder, BigDecimal scrapQty,
                                     String itemRemark, String billRemark) {
        if (workOrder == null || workOrder.getMaterialId() == null) return null;
        if (scrapQty == null || scrapQty.compareTo(BigDecimal.ZERO) <= 0) return null;

        // 主 BOM：与既有报工补料口径一致（material_id + is_primary=1）
        Bom bom = bomMapper.selectOne(new QueryWrapper<Bom>()
                .eq("material_id", workOrder.getMaterialId())
                .eq("is_primary", (short) 1)
                .last("LIMIT 1"));
        if (bom == null) {
            log.warn("物料 {} 未找到主BOM，无法生成补料单", workOrder.getMaterialCode());
            return null;
        }

        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()).orderByAsc("line_no"));
        if (bomItems == null || bomItems.isEmpty()) return null;

        List<MaterialRequisitionItem> items = new ArrayList<>();
        for (BomItem bomItem : bomItems) {
            if (bomItem.getMaterialId() == null || bomItem.getQuantity() == null) continue;
            Material material = materialMapper.selectById(bomItem.getMaterialId());
            if (material == null) continue;

            // 补料数量 = 报废/判废数量 × BOM 子件用量
            BigDecimal qty = scrapQty.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            MaterialRequisitionItem item = new MaterialRequisitionItem();
            item.setMaterialId(material.getMaterialId());
            item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getMaterialName());
            item.setSpecification(material.getModel());
            item.setUnitId(material.getBaseUnitId());
            item.setQuantity(qty);
            item.setUnitPrice(material.getPurchasePrice() != null ? material.getPurchasePrice() : BigDecimal.ZERO);
            item.setAmount(qty.multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
            item.setRemark(itemRemark);
            items.add(item);
        }
        if (items.isEmpty()) return null;

        MaterialRequisition requisition = new MaterialRequisition();
        requisition.setBillType("SUPPLEMENT");
        requisition.setStatus("草稿");
        // 生成即草稿（2026-11-28 下推统一口径）：审核动作由用户单独完成
        requisition.setApprovalStatus("草稿");
        requisition.setRequisitionDate(LocalDate.now());
        requisition.setWorkOrderNo(workOrder.getOrderNo());
        requisition.setWarehouseId(workOrder.getWarehouseId());
        requisition.setRemark(billRemark);

        // 发起人 = 当前登录人（由保存链路按登录态写 created_by），调用方无需再传
        materialRequisitionService.saveRequisitionWithItems(requisition, items);
        log.info("生成补料领料单: {} 工单: {} 数量: {}",
                requisition.getRequisitionNo(), workOrder.getOrderNo(), scrapQty);
        return requisition.getRequisitionNo();
    }
}
