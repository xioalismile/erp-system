package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.service.InventoryService;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.production.mapper.ProductionReceiptMapper;
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
public class ProductionReceiptServiceImpl extends ServiceImpl<ProductionReceiptMapper, ProductionReceipt> implements ProductionReceiptService {

    private final ProductionReceiptItemMapper itemMapper;
    private final InventoryService inventoryService;

    private static final int SCALE = 2;
    private static final int QTY_SCALE = 4;

    @Override
    @Transactional
    public void saveReceiptWithItems(ProductionReceipt receipt, List<ProductionReceiptItem> items) {
        boolean isNew = receipt.getReceiptId() == null;

        if (receipt.getReceiptNo() == null || receipt.getReceiptNo().isBlank()) {
            receipt.setReceiptNo(generateReceiptNo());
        }

        if (receipt.getStatus() == null) receipt.setStatus("草稿");
        if (receipt.getApprovalStatus() == null) receipt.setApprovalStatus("待审核");
        if (receipt.getReceiptDate() == null) receipt.setReceiptDate(LocalDate.now());
        if (receipt.getExchangeRate() == null) receipt.setExchangeRate(BigDecimal.ONE);
        if (receipt.getTotalAmount() == null) receipt.setTotalAmount(BigDecimal.ZERO);
        if (receipt.getTotalQuantity() == null) receipt.setTotalQuantity(BigDecimal.ZERO);
        if (receipt.getQualifiedQuantity() == null) receipt.setQualifiedQuantity(BigDecimal.ZERO);
        if (receipt.getRejectedQuantity() == null) receipt.setRejectedQuantity(BigDecimal.ZERO);

        recalculate(receipt, items);

        if (isNew) {
            save(receipt);
        } else {
            itemMapper.delete(new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", receipt.getReceiptId()));
            updateById(receipt);
        }

        if (items != null) {
            int lineNo = 10;
            for (ProductionReceiptItem item : items) {
                item.setReceiptItemId(null);
                item.setReceiptId(receipt.getReceiptId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("生产入库单保存成功: {} 状态: {} 合计: {} 数量: {}",
                receipt.getReceiptNo(), receipt.getStatus(), receipt.getTotalAmount(), receipt.getTotalQuantity());
    }

    private void calculateItemAmount(ProductionReceiptItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        BigDecimal amount = unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
    }

    private void recalculate(ProductionReceipt receipt, List<ProductionReceiptItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal qualifiedQuantity = BigDecimal.ZERO;
        BigDecimal rejectedQuantity = BigDecimal.ZERO;
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
                totalQuantity = totalQuantity.add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
                qualifiedQuantity = qualifiedQuantity.add(item.getQualifiedQuantity() != null ? item.getQualifiedQuantity() : BigDecimal.ZERO);
                rejectedQuantity = rejectedQuantity.add(item.getRejectedQuantity() != null ? item.getRejectedQuantity() : BigDecimal.ZERO);
            }
        }
        receipt.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        receipt.setTotalQuantity(totalQuantity.setScale(QTY_SCALE, RoundingMode.HALF_UP));
        receipt.setQualifiedQuantity(qualifiedQuantity.setScale(QTY_SCALE, RoundingMode.HALF_UP));
        receipt.setRejectedQuantity(rejectedQuantity.setScale(QTY_SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<ProductionReceipt> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("receipt_no", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        Page<ProductionReceipt> page = new Page<>(current, size);
        Page<ProductionReceipt> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public ProductionReceipt getDetail(Long id) {
        ProductionReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        List<ProductionReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));
        receipt.setItems(items);
        return receipt;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        ProductionReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        if (!"草稿".equals(receipt.getStatus())) {
            throw new BusinessException("只有草稿状态可审核，当前状态: " + receipt.getStatus());
        }
        receipt.setStatus("已审核");
        receipt.setApprovalStatus("已审核");
        receipt.setApprovedBy(approver);
        receipt.setApprovedAt(LocalDateTime.now());
        updateById(receipt);

        // 审核通过后增加库存 (完工产品入库, 按新签名: stockId/binId/materialId/batchNo 定位)
        List<ProductionReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (ProductionReceiptItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : receipt.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                if (stockId == null || materialId == null) {
                    continue;
                }
                inventoryService.addStock(
                        stockId,
                        item.getBinId(),
                        materialId,
                        item.getBatchNo(),
                        quantity,
                        unitCost,
                        receipt.getReceiptNo(),
                        "PRODUCTION_RECEIPT",
                        "ProductionReceipt",
                        receipt.getReceiptNo(),
                        approver);
            }
        }

        log.info("生产入库单审核: {} 审核人: {}", receipt.getReceiptNo(), approver);
    }

    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        ProductionReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        if (!"已审核".equals(receipt.getStatus())) {
            throw new BusinessException("只有已审核状态可关闭，当前状态: " + receipt.getStatus());
        }
        receipt.setStatus("已关闭");
        if (reason != null && !reason.isBlank()) {
            receipt.setRemark((receipt.getRemark() != null ? receipt.getRemark() + " | " : "") + "关闭原因: " + reason);
        }
        updateById(receipt);
        log.info("生产入库单关闭: {} 关闭人: {} 原因: {}", receipt.getReceiptNo(), closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        ProductionReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("生产入库单不存在");
        if (!"草稿".equals(receipt.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + receipt.getStatus());
        }
        receipt.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            receipt.setRemark((receipt.getRemark() != null ? receipt.getRemark() + " | " : "") + "取消: " + reason);
        }
        updateById(receipt);
        log.info("生产入库单取消: {} 原因: {}", receipt.getReceiptNo(), reason);
    }

    @Override
    public String generateReceiptNo() {
        String prefix = "PR" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<ProductionReceipt> wrapper = new QueryWrapper<>();
        wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
        ProductionReceipt last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getReceiptNo() != null) {
            try {
                seq = Integer.parseInt(last.getReceiptNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
