package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.service.InventoryBatchService;
import com.erp.inventory.service.InventoryService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl extends ServiceImpl<SaleOrderMapper, SaleOrder> implements SaleOrderService {

    private final SaleOrderItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final InventoryBatchService batchService;
    private final MaterialService materialService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    @Override
    @Transactional
    public void saveOrderWithItems(SaleOrder order, List<SaleOrderItem> items) {
        boolean isNew = order.getId() == null;

        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            order.setOrderNo(generateOrderNo());
        }

        if (order.getStatus() == null) order.setStatus("草稿");
        if (order.getSourceChannel() == null) order.setSourceChannel("MANUAL");
        if (order.getInvoiceStatus() == null) order.setInvoiceStatus("未开票");
        if (order.getPaymentStatus() == null) order.setPaymentStatus("未收款");
        if (order.getReceivedAmount() == null) order.setReceivedAmount(BigDecimal.ZERO);
        if (order.getFreight() == null) order.setFreight(BigDecimal.ZERO);
        if (order.getDiscountAmount() == null) order.setDiscountAmount(BigDecimal.ZERO);
        if (order.getCurrency() == null) order.setCurrency("CNY");
        if (order.getExchangeRate() == null) order.setExchangeRate(BigDecimal.ONE);

        recalculateOrder(order, items);

        if (isNew) {
            save(order);
        } else {
            itemMapper.delete(new QueryWrapper<SaleOrderItem>().eq("order_id", order.getId()));
            updateById(order);
        }

        if (items != null) {
            int lineNo = 10;
            for (SaleOrderItem item : items) {
                item.setId(null);
                item.setOrderId(order.getId());
                item.setLineNo(lineNo);
                if (item.getShippedQuantity() == null) item.setShippedQuantity(BigDecimal.ZERO);
                if (item.getIsGift() == null) item.setIsGift(0);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("销售订单保存成功: {} 状态: {} 合计: {}", order.getOrderNo(), order.getStatus(), order.getTotalWithTax());
    }

    private void calculateItemAmount(SaleOrderItem item) {
        if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
        if (item.getQuantity() == null) item.setQuantity(BigDecimal.ZERO);
        if (item.getDiscountRate() == null) item.setDiscountRate(BigDecimal.ZERO);
        if (item.getTaxRate() == null) item.setTaxRate(new BigDecimal("13"));

        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                item.getDiscountRate().divide(HUNDRED, 6, RoundingMode.HALF_UP));
        item.setDiscountedPrice(item.getUnitPrice().multiply(discountMultiplier).setScale(4, RoundingMode.HALF_UP));

        item.setAmount(item.getDiscountedPrice().multiply(item.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP));

        item.setTaxAmount(item.getAmount().multiply(item.getTaxRate())
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP));

        item.setTotalAmount(item.getAmount().add(item.getTaxAmount()));
    }

    private void recalculateOrder(SaleOrder order, List<SaleOrderItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        if (items != null) {
            for (SaleOrderItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
                totalTax = totalTax.add(item.getTaxAmount());
            }
        }

        order.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        order.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));

        BigDecimal freight = order.getFreight() != null ? order.getFreight() : BigDecimal.ZERO;
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;

        BigDecimal totalWithTax = order.getTotalAmount()
                .add(freight)
                .subtract(discount)
                .add(order.getTaxAmount());
        order.setTotalWithTax(totalWithTax.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<SaleOrder> pageQuery(int current, int size, String customerName, String status, String orderNo) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (customerName != null && !customerName.isBlank()) wrapper.like("customer_name", customerName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (orderNo != null && !orderNo.isBlank()) wrapper.like("order_no", orderNo);
        Page<SaleOrder> page = new Page<>(current, size);
        Page<SaleOrder> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleOrder getDetail(Long id) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        List<SaleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", id).orderByAsc("line_no"));
        order.setItems(items);
        return order;
    }

    @Override
    @Transactional
    public void submitAudit(Long id, String auditor) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"草稿".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态可提交审核，当前状态: " + order.getStatus());
        }
        order.setStatus("已确认");
        order.setAuditor(auditor);
        order.setAuditDate(LocalDate.now());
        updateById(order);
        log.info("订单提交审核: {} 审核人: {}", order.getOrderNo(), auditor);
    }

    @Override
    @Transactional
    public void shipOrder(Long id, String auditor, String logisticsNo, String deliveryMethod) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"已确认".equals(order.getStatus())) {
            throw new BusinessException("只有已确认状态可发货，当前状态: " + order.getStatus());
        }

        List<SaleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", id));

        for (SaleOrderItem item : items) {
            if (item.getIsGift() != null && item.getIsGift() == 1) continue;

            String warehouse = item.getWarehouse() != null ? item.getWarehouse() : "主仓库";
            Material material = materialService.getByCode(item.getMaterialCode());

            if (material != null && Boolean.TRUE.equals(material.getBatchManaged())) {
                deductBatchInventory(item.getMaterialCode(), warehouse, item.getQuantity());
            } else {
                deductNonBatchInventory(item.getMaterialCode(), warehouse, item.getQuantity());
            }

            item.setShippedQuantity(item.getQuantity());
            itemMapper.updateById(item);
        }

        order.setStatus("已发货");
        order.setActualShipDate(LocalDate.now());
        order.setLogisticsNo(logisticsNo);
        order.setDeliveryMethod(deliveryMethod);
        updateById(order);
        log.info("订单发货: {} 物流: {}", order.getOrderNo(), logisticsNo);
    }

    private void deductBatchInventory(String materialCode, String warehouseCode, BigDecimal quantity) {
        Map<String, Object> allocation = batchService.allocateByFifo(materialCode, warehouseCode, quantity);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batches = (List<Map<String, Object>>) allocation.get("batches");

        for (Map<String, Object> batch : batches) {
            Long batchId = ((Number) batch.get("batchId")).longValue();
            BigDecimal qty = (BigDecimal) batch.get("quantity");
            batchService.deductBatch(batchId, qty);
        }
    }

    private void deductNonBatchInventory(String materialCode, String warehouseCode, BigDecimal quantity) {
        Inventory inv = inventoryService.getOne(
                new QueryWrapper<Inventory>().eq("warehouse_code", warehouseCode)
                        .eq("material_code", materialCode)
                        .last("LIMIT 1"));

        if (inv == null) {
            throw new BusinessException("物料 " + materialCode + " 在仓库 " + warehouseCode + " 无库存记录");
        }

        BigDecimal available = inv.getQuantity().subtract(
                inv.getLockedQuantity() != null ? inv.getLockedQuantity() : BigDecimal.ZERO);

        if (available.compareTo(quantity) < 0) {
            throw new BusinessException("物料 " + materialCode + " 库存不足: 需要 " + quantity + "，可用 " + available);
        }

        inv.setQuantity(inv.getQuantity().subtract(quantity));
        inventoryService.updateById(inv);
    }

    @Override
    @Transactional
    public void invoiceOrder(Long id, String invoiceNo) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"已发货".equals(order.getStatus()) && !"已开票".equals(order.getStatus())) {
            throw new BusinessException("只有已发货状态可开票，当前状态: " + order.getStatus());
        }
        order.setStatus("已开票");
        order.setInvoiceStatus("已开票");
        order.setInvoiceNo(invoiceNo);
        updateById(order);
        log.info("订单开票: {} 发票号: {}", order.getOrderNo(), invoiceNo);
    }

    @Override
    @Transactional
    public void completeOrder(Long id) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"已开票".equals(order.getStatus())) {
            throw new BusinessException("只有已开票状态可完成，当前状态: " + order.getStatus());
        }
        order.setStatus("已完成");
        updateById(order);
        log.info("订单完成: {}", order.getOrderNo());
    }

    @Override
    @Transactional
    public void cancelOrder(Long id, String reason) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"草稿".equals(order.getStatus()) && !"已确认".equals(order.getStatus())) {
            throw new BusinessException("只有草稿或已确认状态可取消，当前状态: " + order.getStatus());
        }
        order.setStatus("已取消");
        if (reason != null) {
            order.setRemark((order.getRemark() != null ? order.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(order);
        log.info("订单取消: {} 原因: {}", order.getOrderNo(), reason);
    }

    @Override
    @Transactional
    public void returnOrder(Long id, String reason) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"已发货".equals(order.getStatus()) && !"已完成".equals(order.getStatus())) {
            throw new BusinessException("只有已发货或已完成状态可退货，当前状态: " + order.getStatus());
        }

        List<SaleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", id));
        for (SaleOrderItem item : items) {
            if (item.getIsGift() != null && item.getIsGift() == 1) continue;

            String warehouse = item.getWarehouse() != null ? item.getWarehouse() : "主仓库";
            Material material = materialService.getByCode(item.getMaterialCode());
            BigDecimal returnQty = item.getShippedQuantity() != null ? item.getShippedQuantity() : item.getQuantity();

            if (material != null && Boolean.TRUE.equals(material.getBatchManaged())) {
                InventoryBatch batch = new InventoryBatch();
                batch.setMaterialCode(item.getMaterialCode());
                batch.setMaterialName(item.getMaterialName());
                batch.setWarehouseCode(warehouse);
                batch.setQuantity(returnQty);
                batch.setProductionDate(LocalDate.now());
                batchService.addBatch(batch);
            } else {
                Inventory inv = inventoryService.getOne(
                        new QueryWrapper<Inventory>().eq("warehouse_code", warehouse)
                                .eq("material_code", item.getMaterialCode())
                                .last("LIMIT 1"));
                if (inv != null) {
                    inv.setQuantity(inv.getQuantity().add(returnQty));
                    inventoryService.updateById(inv);
                }
            }
        }

        order.setStatus("已退货");
        if (reason != null) {
            order.setRemark((order.getRemark() != null ? order.getRemark() + " | " : "") + "退货原因: " + reason);
        }
        updateById(order);
        log.info("订单退货: {} 原因: {}", order.getOrderNo(), reason);
    }

    @Override
    @Transactional
    public SaleOrder copyOrder(Long sourceId) {
        SaleOrder source = getDetail(sourceId);
        if (source == null) throw new BusinessException("源订单不存在");

        SaleOrder newOrder = new SaleOrder();
        newOrder.setOrderDate(LocalDate.now());
        newOrder.setCustomerId(source.getCustomerId());
        newOrder.setCustomerCode(source.getCustomerCode());
        newOrder.setCustomerName(source.getCustomerName());
        newOrder.setContactPerson(source.getContactPerson());
        newOrder.setContactPhone(source.getContactPhone());
        newOrder.setDeliveryAddress(source.getDeliveryAddress());
        newOrder.setSalesperson(source.getSalesperson());
        newOrder.setDepartment(source.getDepartment());
        newOrder.setCurrency(source.getCurrency());
        newOrder.setExchangeRate(source.getExchangeRate());
        newOrder.setPaymentMethod(source.getPaymentMethod());
        newOrder.setExpectedDeliveryDate(source.getExpectedDeliveryDate());
        newOrder.setFreight(source.getFreight());
        newOrder.setDiscountAmount(source.getDiscountAmount());
        newOrder.setStatus("草稿");
        newOrder.setSourceChannel("MANUAL");
        newOrder.setInvoiceStatus("未开票");
        newOrder.setPaymentStatus("未收款");
        newOrder.setReceivedAmount(BigDecimal.ZERO);
        newOrder.setRemark("复制自: " + source.getOrderNo());

        List<SaleOrderItem> newItems = new ArrayList<>();
        if (source.getItems() != null) {
            for (SaleOrderItem srcItem : source.getItems()) {
                SaleOrderItem newItem = new SaleOrderItem();
                newItem.setMaterialId(srcItem.getMaterialId());
                newItem.setMaterialCode(srcItem.getMaterialCode());
                newItem.setMaterialName(srcItem.getMaterialName());
                newItem.setSpecification(srcItem.getSpecification());
                newItem.setUnit(srcItem.getUnit());
                newItem.setWarehouse(srcItem.getWarehouse());
                newItem.setQuantity(srcItem.getQuantity());
                newItem.setUnitPrice(srcItem.getUnitPrice());
                newItem.setDiscountRate(srcItem.getDiscountRate());
                newItem.setTaxRate(srcItem.getTaxRate());
                newItem.setIsGift(srcItem.getIsGift());
                newItem.setDeliveryDate(srcItem.getDeliveryDate());
                newItem.setRemark(srcItem.getRemark());
                newItems.add(newItem);
            }
        }

        saveOrderWithItems(newOrder, newItems);
        log.info("订单复制成功: {} → {}", source.getOrderNo(), newOrder.getOrderNo());
        return newOrder;
    }

    @Override
    @Transactional
    public void recordPayment(Long id, BigDecimal amount) {
        SaleOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");

        BigDecimal newReceived = order.getReceivedAmount().add(amount);
        order.setReceivedAmount(newReceived);

        if (newReceived.compareTo(order.getTotalWithTax()) >= 0) {
            order.setPaymentStatus("已收款");
        } else if (newReceived.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus("部分收款");
        }

        updateById(order);
        log.info("收款登记: {} 金额: {} 累计已收: {}", order.getOrderNo(), amount, newReceived);
    }

    @Override
    public String generateOrderNo() {
        String prefix = "SO" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
        SaleOrder last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getOrderNo() != null) {
            String seqStr = last.getOrderNo().substring(prefix.length());
            try { seq = Integer.parseInt(seqStr) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }
}