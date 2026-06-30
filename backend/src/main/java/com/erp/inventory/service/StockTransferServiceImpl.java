package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.StockTransfer;
import com.erp.inventory.entity.StockTransferItem;
import com.erp.inventory.mapper.StockTransferItemMapper;
import com.erp.inventory.mapper.StockTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl extends ServiceImpl<StockTransferMapper, StockTransfer> implements StockTransferService {

    private final StockTransferItemMapper itemMapper;
    private final InventoryService inventoryService;

    private static final int SCALE = 2;

    @Override
    @Transactional
    public void saveTransferWithItems(StockTransfer transfer, List<StockTransferItem> items) {
        boolean isNew = transfer.getTransferId() == null;

        if (transfer.getTransferNo() == null || transfer.getTransferNo().isBlank()) {
            transfer.setTransferNo(generateTransferNo());
        }

        if (transfer.getStatus() == null) transfer.setStatus("草稿");
        if (transfer.getApprovalStatus() == null) transfer.setApprovalStatus("待审核");
        if (transfer.getTransferDate() == null) transfer.setTransferDate(LocalDate.now());
        if (transfer.getTransferType() == null) transfer.setTransferType("同价调拨");
        if (transfer.getExchangeRate() == null) transfer.setExchangeRate(BigDecimal.ONE);
        if (transfer.getTotalAmount() == null) transfer.setTotalAmount(BigDecimal.ZERO);

        // 校验: 调出仓库与调入仓库不能相同
        if (transfer.getFromStockId() != null && transfer.getFromStockId().equals(transfer.getToStockId())) {
            throw new BusinessException("调出仓库与调入仓库不能相同");
        }

        recalculate(transfer, items);

        if (isNew) {
            save(transfer);
        } else {
            itemMapper.delete(new QueryWrapper<StockTransferItem>().eq("transfer_id", transfer.getTransferId()));
            updateById(transfer);
        }

        if (items != null) {
            int lineNo = 10;
            for (StockTransferItem item : items) {
                item.setTransferItemId(null);
                item.setTransferId(transfer.getTransferId());
                item.setLineNo(lineNo);
                if (item.getFromStockId() == null) item.setFromStockId(transfer.getFromStockId());
                if (item.getToStockId() == null) item.setToStockId(transfer.getToStockId());
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("库存调拨单保存成功: {} 状态: {} 合计: {}", transfer.getTransferNo(), transfer.getStatus(), transfer.getTotalAmount());
    }

    private void calculateItemAmount(StockTransferItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        BigDecimal amount = unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
    }

    private void recalculate(StockTransfer transfer, List<StockTransferItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (StockTransferItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        transfer.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<StockTransfer> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<StockTransfer> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("transfer_no", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        Page<StockTransfer> page = new Page<>(current, size);
        Page<StockTransfer> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public StockTransfer getDetail(Long id) {
        StockTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("库存调拨单不存在");
        List<StockTransferItem> items = itemMapper.selectList(
                new QueryWrapper<StockTransferItem>().eq("transfer_id", id).orderByAsc("line_no"));
        transfer.setItems(items);
        return transfer;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        StockTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("库存调拨单不存在");
        if (!"草稿".equals(transfer.getStatus())) {
            throw new BusinessException("只有草稿状态可审核，当前状态: " + transfer.getStatus());
        }
        transfer.setStatus("已审核");
        transfer.setApprovalStatus("已审核");
        transfer.setApprovedBy(approver);
        transfer.setApprovedAt(LocalDateTime.now());
        updateById(transfer);

        // 调拨审核 = 调出仓库扣减 + 调入仓库增加 (按新签名: stockId/binId/materialId/batchNo 定位)
        List<StockTransferItem> items = itemMapper.selectList(
                new QueryWrapper<StockTransferItem>().eq("transfer_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (StockTransferItem item : items) {
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                Long fromStockId = item.getFromStockId() != null ? item.getFromStockId() : transfer.getFromStockId();
                Long toStockId = item.getToStockId() != null ? item.getToStockId() : transfer.getToStockId();
                if (materialId == null || fromStockId == null || toStockId == null) {
                    continue;
                }
                // 调出仓库扣减
                inventoryService.deductStock(
                        fromStockId, item.getFromBinId(), materialId, item.getBatchNo(),
                        quantity,
                        transfer.getTransferNo(), "STOCK_TRANSFER_OUT",
                        "StockTransfer", transfer.getTransferNo(), approver);
                // 调入仓库增加
                inventoryService.addStock(
                        toStockId, item.getToBinId(), materialId, item.getBatchNo(),
                        quantity, unitCost,
                        transfer.getTransferNo(), "STOCK_TRANSFER_IN",
                        "StockTransfer", transfer.getTransferNo(), approver);
            }
        }

        log.info("库存调拨单审核: {} 审核人: {}", transfer.getTransferNo(), approver);
    }

    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        StockTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("库存调拨单不存在");
        if (!"已审核".equals(transfer.getStatus())) {
            throw new BusinessException("只有已审核状态可关闭，当前状态: " + transfer.getStatus());
        }
        transfer.setStatus("已关闭");
        if (reason != null && !reason.isBlank()) {
            transfer.setRemark((transfer.getRemark() != null ? transfer.getRemark() + " | " : "") + "关闭原因: " + reason);
        }
        updateById(transfer);
        log.info("库存调拨单关闭: {} 操作人: {} 原因: {}", transfer.getTransferNo(), closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        StockTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("库存调拨单不存在");
        if (!"草稿".equals(transfer.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + transfer.getStatus());
        }
        transfer.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            transfer.setRemark((transfer.getRemark() != null ? transfer.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(transfer);
        log.info("库存调拨单取消: {} 原因: {}", transfer.getTransferNo(), reason);
    }

    @Override
    public String generateTransferNo() {
        String prefix = "ST" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<StockTransfer> wrapper = new QueryWrapper<>();
        wrapper.likeRight("transfer_no", prefix).orderByDesc("transfer_no").last("LIMIT 1");
        StockTransfer last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getTransferNo() != null) {
            try {
                seq = Integer.parseInt(last.getTransferNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
