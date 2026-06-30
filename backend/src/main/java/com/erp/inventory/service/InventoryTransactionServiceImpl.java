package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.inventory.mapper.InventoryTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTransactionServiceImpl extends ServiceImpl<InventoryTransactionMapper, InventoryTransaction>
        implements InventoryTransactionService {

    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional
    public void recordTransaction(String transNo, String transType,
                                  Long stockId, Long binId, Long materialId, String batchNo,
                                  BigDecimal inQuantity, BigDecimal inAmount,
                                  BigDecimal outQuantity, BigDecimal outAmount,
                                  BigDecimal unitCost,
                                  String sourceBillType, String sourceBillNo,
                                  String operator, String remark) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setTransDate(LocalDate.now());
        tx.setTransType(transType);
        tx.setTransNo(transNo);
        tx.setLineNo(1);
        tx.setStockId(stockId);
        tx.setBinId(binId);
        tx.setMaterialId(materialId);
        tx.setBatchNo(batchNo);

        // null 处理为 ZERO
        BigDecimal safeInQty = inQuantity != null ? inQuantity : BigDecimal.ZERO;
        BigDecimal safeInAmt = inAmount != null ? inAmount : BigDecimal.ZERO;
        BigDecimal safeOutQty = outQuantity != null ? outQuantity : BigDecimal.ZERO;
        BigDecimal safeOutAmt = outAmount != null ? outAmount : BigDecimal.ZERO;
        tx.setInQuantity(safeInQty);
        tx.setInAmount(safeInAmt);
        tx.setOutQuantity(safeOutQty);
        tx.setOutAmount(safeOutAmt);

        // 计算余额: 查询 inventory_current 当前 quantity 和 total_cost
        Inventory inv = inventoryMapper.selectOne(
                buildLocateWrapper(stockId, binId, materialId, batchNo).last("LIMIT 1"));
        BigDecimal balanceQuantity = inv != null && inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
        BigDecimal balanceAmount = inv != null && inv.getTotalCost() != null ? inv.getTotalCost() : BigDecimal.ZERO;
        tx.setBalanceQuantity(balanceQuantity);
        tx.setBalanceAmount(balanceAmount);

        tx.setUnitCost(unitCost != null ? unitCost : BigDecimal.ZERO);
        tx.setSourceBillType(sourceBillType);
        tx.setSourceBillNo(sourceBillNo);
        tx.setCreatedBy(operator);
        tx.setRemark(remark);

        save(tx);
        log.info("库存台账记录: {} {} 物料[{}] 仓库[{}] 入{} 出{} 余额{}",
                transNo, transType, materialId, stockId, safeInQty, safeOutQty, balanceQuantity);
    }

    @Override
    public List<InventoryTransaction> getMaterialTransactions(Long materialId, Long stockId) {
        QueryWrapper<InventoryTransaction> wrapper = new QueryWrapper<>();
        wrapper.eq("material_id", materialId);
        if (stockId != null) {
            wrapper.eq("stock_id", stockId);
        }
        wrapper.orderByDesc("trans_date", "ledger_id");
        return list(wrapper);
    }

    @Override
    public List<InventoryTransaction> getDocumentTransactions(String transNo) {
        return list(new QueryWrapper<InventoryTransaction>()
                .eq("trans_no", transNo)
                .orderByAsc("line_no"));
    }

    /**
     * 按 (stock_id, bin_id, material_id, batch_no) 定位即时库存, 用于查余额
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
