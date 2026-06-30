package com.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.purchase.mapper.PurchaseInboundMapper;
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
public class PurchaseInboundServiceImpl extends ServiceImpl<PurchaseInboundMapper, PurchaseInbound> implements PurchaseInboundService {

    private final PurchaseInboundItemMapper itemMapper;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    @Override
    @Transactional
    public void saveInboundWithItems(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        boolean isNew = inbound.getReceiptId() == null;

        if (inbound.getReceiptNo() == null || inbound.getReceiptNo().isBlank()) {
            inbound.setReceiptNo(generateReceiptNo());
        }

        if (inbound.getStatus() == null) inbound.setStatus("草稿");
        if (inbound.getApprovalStatus() == null) inbound.setApprovalStatus("待审核");
        if (inbound.getExchangeRate() == null) inbound.setExchangeRate(BigDecimal.ONE);
        if (inbound.getDiscountRate() == null) inbound.setDiscountRate(BigDecimal.ZERO);
        if (inbound.getDiscountAmount() == null) inbound.setDiscountAmount(BigDecimal.ZERO);
        if (inbound.getTotalAmount() == null) inbound.setTotalAmount(BigDecimal.ZERO);
        if (inbound.getTaxAmount() == null) inbound.setTaxAmount(BigDecimal.ZERO);
        if (inbound.getNetAmount() == null) inbound.setNetAmount(BigDecimal.ZERO);
        if (inbound.getReceiptDate() == null) inbound.setReceiptDate(LocalDate.now());
        if (inbound.getIsReturn() == null) inbound.setIsReturn("N");

        recalculateInbound(inbound, items);

        if (isNew) {
            save(inbound);
        } else {
            itemMapper.delete(new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", inbound.getReceiptId()));
            updateById(inbound);
        }

        if (items != null) {
            int lineNo = 10;
            for (PurchaseInboundItem item : items) {
                item.setReceiptItemId(null);
                item.setReceiptId(inbound.getReceiptId());
                item.setLineNo(lineNo);
                if (item.getQualifiedQuantity() == null) item.setQualifiedQuantity(BigDecimal.ZERO);
                if (item.getRejectedQuantity() == null) item.setRejectedQuantity(BigDecimal.ZERO);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("采购入库单保存成功: {} 状态: {} 合计: {}", inbound.getReceiptNo(), inbound.getStatus(), inbound.getTotalAmount());
    }

    private void calculateItemAmount(PurchaseInboundItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal discountRate = item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO;
        BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;

        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setDiscountRate(discountRate);
        item.setTaxRate(taxRate);

        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                discountRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal amount = unitPrice.multiply(quantity).multiply(discountMultiplier)
                .setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);

        BigDecimal taxAmount = amount.multiply(taxRate)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        item.setTaxAmount(taxAmount);

        item.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private void recalculateInbound(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        if (items != null) {
            for (PurchaseInboundItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
                totalTax = totalTax.add(item.getTaxAmount());
            }
        }

        inbound.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        inbound.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));

        BigDecimal discountAmount = inbound.getDiscountAmount() != null ? inbound.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netAmount = inbound.getTotalAmount()
                .subtract(discountAmount)
                .add(inbound.getTaxAmount());
        inbound.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<PurchaseInbound> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<PurchaseInbound> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("receipt_no", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        Page<PurchaseInbound> page = new Page<>(current, size);
        Page<PurchaseInbound> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseInbound getDetail(Long id) {
        PurchaseInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        List<PurchaseInboundItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseInboundItem>().eq("receipt_id", id).orderByAsc("line_no"));
        inbound.setItems(items);
        return inbound;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        PurchaseInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        if (!"草稿".equals(inbound.getStatus())) {
            throw new BusinessException("只有草稿状态可审核，当前状态: " + inbound.getStatus());
        }
        inbound.setStatus("已审核");
        inbound.setApprovalStatus("已审核");
        inbound.setApprovedBy(approver);
        inbound.setApprovedAt(LocalDateTime.now());
        updateById(inbound);
        log.info("采购入库单审核: {} 审核人: {}", inbound.getReceiptNo(), approver);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        if (!"草稿".equals(inbound.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + inbound.getStatus());
        }
        inbound.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            inbound.setRemark((inbound.getRemark() != null ? inbound.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(inbound);
        log.info("采购入库单取消: {} 原因: {}", inbound.getReceiptNo(), reason);
    }

    @Override
    public String generateReceiptNo() {
        String prefix = "PR" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<PurchaseInbound> wrapper = new QueryWrapper<>();
        wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
        PurchaseInbound last = getOne(wrapper);
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
