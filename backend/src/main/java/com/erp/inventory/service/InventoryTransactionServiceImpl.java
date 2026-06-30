package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.mapper.InventoryTransactionMapper;
import com.erp.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTransactionServiceImpl extends ServiceImpl<InventoryTransactionMapper, InventoryTransaction>
        implements InventoryTransactionService {

    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional
    public void recordTransaction(String documentNo, String documentType, String materialCode, String materialName,
                                  String warehouseCode, BigDecimal quantity, Long sourceId, String sourceType,
                                  String operator, String remark) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setDocumentNo(documentNo);
        tx.setDocumentType(documentType);
        tx.setBusinessType(getBusinessTypeName(documentType));
        tx.setMaterialCode(materialCode);
        tx.setMaterialName(materialName);
        tx.setWarehouseCode(warehouseCode);
        tx.setQuantity(quantity);
        tx.setSourceId(sourceId);
        tx.setSourceType(sourceType);
        tx.setOperator(operator);
        tx.setRemark(remark);

        // 计算库存余额
        BigDecimal balance = calculateBalance(materialCode, warehouseCode, quantity);
        tx.setBalanceQuantity(balance);

        save(tx);
        log.info("库存台账记录: {} {} {} 数量{} 余额{}", documentNo, documentType, materialCode, quantity, balance);
    }

    private BigDecimal calculateBalance(String materialCode, String warehouseCode, BigDecimal quantityChange) {
        Inventory inv = inventoryMapper.selectOne(
                new QueryWrapper<Inventory>()
                        .eq("material_code", materialCode)
                        .eq("warehouse_code", warehouseCode)
                        .last("LIMIT 1"));

        BigDecimal currentBalance = BigDecimal.ZERO;
        if (inv != null) {
            currentBalance = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
        }

        return currentBalance.add(quantityChange);
    }

    private String getBusinessTypeName(String documentType) {
        return switch (documentType) {
            case "SALE_OUT" -> "销售出库";
            case "PURCHASE_IN" -> "采购入库";
            case "WO_START" -> "工单领料";
            case "WO_FINISH" -> "工单完工";
            case "SALE_RETURN" -> "销售退货";
            case "PURCHASE_RETURN" -> "采购退货";
            case "ADJUST" -> "库存调拨";
            default -> "其他";
        };
    }

    @Override
    public List<InventoryTransaction> getMaterialTransactions(String materialCode, String warehouseCode) {
        QueryWrapper<InventoryTransaction> wrapper = new QueryWrapper<>();
        wrapper.eq("material_code", materialCode);
        if (warehouseCode != null && !warehouseCode.isBlank()) {
            wrapper.eq("warehouse_code", warehouseCode);
        }
        wrapper.orderByDesc("created_at");
        return list(wrapper);
    }

    @Override
    public List<InventoryTransaction> getDocumentTransactions(String documentNo) {
        return list(new QueryWrapper<InventoryTransaction>()
                .eq("document_no", documentNo)
                .orderByDesc("created_at"));
    }
}
