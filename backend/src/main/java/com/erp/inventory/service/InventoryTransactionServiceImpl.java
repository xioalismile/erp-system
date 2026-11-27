package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.inventory.mapper.InventoryTransactionMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
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
    /** 台账写入时冗余物料编码/名称/规格用（物料主档日后被删，历史台账仍可读） */
    private final MaterialMapper materialMapper;

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
        // 【2026-09-19】台账冗余物料编码/名称/规格：台账是历史账，物料主档被删除或整批换 id 后
        // 列表接口的实时 join 永远补不回编码（本机库历史 231 行全空即此因），故写入时一并落库。
        fillMaterialSnapshot(tx, materialId);
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
     * 台账冗余填充：物料编码 / 物料名称 / 规格型号（取物料主档快照）。
     * <p>
     * 2026-09-19 用户报障修复：库存流水页「物料编码」全空。根因是台账只存 material_id，
     * 展示字段全靠列表接口实时 join material 表；物料主档被删除或整批换 id 后历史流水永久空白。
     * 与 other_receipt_item / sale_order_item 等明细表保持一致：写入时把物料快照一起落库。
     * <p>
     * 取不到物料（id 为空 / 主档已删）时留空即可，绝不阻断过账；异常仅告警不抛出。
     */
    private void fillMaterialSnapshot(InventoryTransaction tx, Long materialId) {
        if (tx == null || materialId == null) {
            return;
        }
        try {
            Material m = materialMapper.selectById(materialId);
            if (m != null) {
                tx.setMaterialCode(m.getMaterialCode());
                tx.setMaterialName(m.getMaterialName());
                tx.setSpecification(m.getModel());
            }
        } catch (Exception e) {
            log.warn("库存台账物料快照填充失败 materialId={}: {}", materialId, e.getMessage());
        }
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
