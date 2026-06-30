package com.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
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
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements PurchaseOrderService {

    private final PurchaseOrderItemMapper itemMapper;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    @Override
    @Transactional
    public void saveOrderWithItems(PurchaseOrder order, List<PurchaseOrderItem> items) {
        boolean isNew = order.getOrderId() == null;

        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            order.setOrderNo(generateOrderNo());
        }

        if (order.getStatus() == null) order.setStatus("草稿");
        if (order.getApprovalStatus() == null) order.setApprovalStatus("待审核");
        if (order.getExchangeRate() == null) order.setExchangeRate(BigDecimal.ONE);
        if (order.getDiscountRate() == null) order.setDiscountRate(BigDecimal.ZERO);
        if (order.getDiscountAmount() == null) order.setDiscountAmount(BigDecimal.ZERO);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        if (order.getTaxAmount() == null) order.setTaxAmount(BigDecimal.ZERO);
        if (order.getNetAmount() == null) order.setNetAmount(BigDecimal.ZERO);
        if (order.getPrepaymentAmount() == null) order.setPrepaymentAmount(BigDecimal.ZERO);
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());

        recalculateOrder(order, items);

        if (isNew) {
            save(order);
        } else {
            itemMapper.delete(new QueryWrapper<PurchaseOrderItem>().eq("order_id", order.getOrderId()));
            updateById(order);
        }

        if (items != null) {
            int lineNo = 10;
            for (PurchaseOrderItem item : items) {
                item.setOrderItemId(null);
                item.setOrderId(order.getOrderId());
                item.setLineNo(lineNo);
                if (item.getReceivedQuantity() == null) item.setReceivedQuantity(BigDecimal.ZERO);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("采购订单保存成功: {} 状态: {} 合计: {}", order.getOrderNo(), order.getStatus(), order.getTotalAmount());
    }

    private void calculateItemAmount(PurchaseOrderItem item) {
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

    private void recalculateOrder(PurchaseOrder order, List<PurchaseOrderItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        if (items != null) {
            for (PurchaseOrderItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
                totalTax = totalTax.add(item.getTaxAmount());
            }
        }

        order.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        order.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));

        BigDecimal discountAmount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netAmount = order.getTotalAmount()
                .subtract(discountAmount)
                .add(order.getTaxAmount());
        order.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<PurchaseOrder> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("order_no", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        Page<PurchaseOrder> page = new Page<>(current, size);
        Page<PurchaseOrder> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseOrder getDetail(Long id) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseOrderItem>().eq("order_id", id).orderByAsc("line_no"));
        order.setItems(items);
        return order;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态可审核，当前状态: " + order.getStatus());
        }
        order.setStatus("已审核");
        order.setApprovalStatus("已审核");
        order.setApprovedBy(approver);
        order.setApprovedAt(LocalDateTime.now());
        updateById(order);
        log.info("采购订单审核: {} 审核人: {}", order.getOrderNo(), approver);
    }

    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"已审核".equals(order.getStatus())) {
            throw new BusinessException("只有已审核状态可关闭，当前状态: " + order.getStatus());
        }
        order.setStatus("已关闭");
        order.setClosedBy(closer);
        order.setClosedAt(LocalDateTime.now());
        order.setClosedReason(reason);
        updateById(order);
        log.info("采购订单关闭: {} 关闭人: {} 原因: {}", order.getOrderNo(), closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + order.getStatus());
        }
        order.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            order.setRemark((order.getRemark() != null ? order.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(order);
        log.info("采购订单取消: {} 原因: {}", order.getOrderNo(), reason);
    }

    @Override
    public String generateOrderNo() {
        String prefix = "PO" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
        PurchaseOrder last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getOrderNo() != null) {
            try {
                seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
