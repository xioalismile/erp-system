package com.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.service.InventoryService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements PurchaseOrderService {

    private final PurchaseOrderItemMapper itemMapper;
    private final MaterialService materialService;
    private final SupplierService supplierService;
    private final InventoryService inventoryService;
    private final SaleOrderService saleOrderService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    // ========== 保存 ==========

    @Override
    @Transactional
    public void saveOrderWithItems(PurchaseOrder order, List<PurchaseOrderItem> items) {
        boolean isNew = order.getId() == null;
        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            order.setOrderNo(generateOrderNo());
        }
        if (order.getStatus() == null) order.setStatus("草稿");
        if (order.getInboundStatus() == null) order.setInboundStatus("未入库");
        if (order.getCurrency() == null) order.setCurrency("CNY");
        if (order.getExchangeRate() == null) order.setExchangeRate(BigDecimal.ONE);

        recalculateOrder(order, items);

        if (isNew) {
            save(order);
        } else {
            itemMapper.delete(new QueryWrapper<PurchaseOrderItem>().eq("order_id", order.getId()));
            updateById(order);
        }

        if (items != null) {
            int lineNo = 10;
            for (PurchaseOrderItem item : items) {
                item.setId(null);
                item.setOrderId(order.getId());
                item.setLineNo(lineNo);
                if (item.getReceivedQuantity() == null) item.setReceivedQuantity(BigDecimal.ZERO);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }
        log.info("采购订单保存: {} 状态: {}", order.getOrderNo(), order.getStatus());
    }

    // ========== 金额计算 ==========

    private void calculateItemAmount(PurchaseOrderItem item) {
        if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
        if (item.getQuantity() == null) item.setQuantity(BigDecimal.ZERO);
        if (item.getDiscountRate() == null) item.setDiscountRate(BigDecimal.ZERO);
        if (item.getTaxRate() == null) item.setTaxRate(new BigDecimal("13"));

        BigDecimal mult = BigDecimal.ONE.subtract(item.getDiscountRate().divide(HUNDRED, 6, RoundingMode.HALF_UP));
        item.setDiscountedPrice(item.getUnitPrice().multiply(mult).setScale(4, RoundingMode.HALF_UP));
        item.setAmount(item.getDiscountedPrice().multiply(item.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP));
        item.setTaxAmount(item.getAmount().multiply(item.getTaxRate()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
        item.setTotalAmount(item.getAmount().add(item.getTaxAmount()));
    }

    private void recalculateOrder(PurchaseOrder order, List<PurchaseOrderItem> items) {
        BigDecimal total = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        if (items != null) {
            for (PurchaseOrderItem item : items) {
                calculateItemAmount(item);
                total = total.add(item.getAmount());
                tax = tax.add(item.getTaxAmount());
            }
        }
        order.setTotalAmount(total.setScale(SCALE, RoundingMode.HALF_UP));
        order.setTaxAmount(tax.setScale(SCALE, RoundingMode.HALF_UP));
        order.setTotalWithTax(order.getTotalAmount().add(order.getTaxAmount()).setScale(SCALE, RoundingMode.HALF_UP));
    }

    // ========== 查询 ==========

    @Override
    public PageResult<PurchaseOrder> pageQuery(int current, int size, String supplierName, String status, String orderNo) {
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (supplierName != null && !supplierName.isBlank()) wrapper.like("supplier_name", supplierName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (orderNo != null && !orderNo.isBlank()) wrapper.like("order_no", orderNo);
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

    // ========== 状态流转 ==========

    @Override
    @Transactional
    public void submitAudit(Long id, String auditor) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态可提交审核，当前: " + order.getStatus());
        }
        order.setStatus("已确认");
        order.setAuditor(auditor);
        order.setAuditDate(LocalDate.now());
        updateById(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long id, String reason) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(order.getStatus()) && !"已确认".equals(order.getStatus())) {
            throw new BusinessException("只有草稿或已确认可取消，当前: " + order.getStatus());
        }
        order.setStatus("已取消");
        if (reason != null) order.setRemark((order.getRemark() != null ? order.getRemark() + " | " : "") + "取消: " + reason);
        updateById(order);
    }

    // ========== 销售转采购 (核心) ==========

    @Override
    @Transactional
    public List<PurchaseOrder> convertFromSaleOrder(Long saleOrderId, List<Map<String, Object>> itemSelection, String buyer) {
        SaleOrder saleOrder = saleOrderService.getDetail(saleOrderId);
        if (saleOrder == null) throw new BusinessException("销售订单不存在");

        List<SaleOrderItem> saleItems = saleOrder.getItems();
        if (saleItems == null || saleItems.isEmpty()) {
            throw new BusinessException("销售订单没有明细，无法转采购");
        }

        // 筛选要转采购的明细
        List<SaleOrderItem> toPurchase = new ArrayList<>();
        if (itemSelection != null && !itemSelection.isEmpty()) {
            // 按选中的明细转
            for (Map<String, Object> sel : itemSelection) {
                Long itemId = ((Number) sel.get("saleOrderItemId")).longValue();
                BigDecimal qty = sel.get("quantity") != null ? new BigDecimal(sel.get("quantity").toString()) : null;
                for (SaleOrderItem si : saleItems) {
                    if (si.getId().equals(itemId)) {
                        if (qty != null) si.setQuantity(qty); // 可调整采购数量
                        toPurchase.add(si);
                        break;
                    }
                }
            }
        } else {
            // 全部转
            toPurchase.addAll(saleItems);
        }

        // 按供应商分组 (通过物料的默认供应商，这里简化为查物料，物料没供应商字段就全部合到一个采购单)
        // 实际场景：物料档案应该有默认供应商字段，这里先按物料查供应商
        Map<Long, List<SaleOrderItem>> supplierGroup = new LinkedHashMap<>();
        Map<Long, Supplier> supplierMap = new HashMap<>();

        for (SaleOrderItem si : toPurchase) {
            Material material = materialService.getOne(
                    new QueryWrapper<Material>().eq("material_code", si.getMaterialCode()));
            if (material == null) {
                throw new BusinessException("物料不存在: " + si.getMaterialCode());
            }

            // 简化：查找供应商。实际应由物料档案指定默认供应商
            // 这里取第一个供应商作为默认
            Long supplierId = null;
            try {
                Supplier supplier = supplierService.list().stream().findFirst().orElse(null);
                if (supplier != null) {
                    supplierId = supplier.getId();
                    supplierMap.put(supplierId, supplier);
                }
            } catch (Exception e) {
                log.warn("无供应商数据");
            }

            supplierGroup.computeIfAbsent(supplierId, k -> new ArrayList<>()).add(si);
        }

        // 为每个供应商生成一个采购订单
        List<PurchaseOrder> result = new ArrayList<>();
        for (Map.Entry<Long, List<SaleOrderItem>> entry : supplierGroup.entrySet()) {
            Long supplierId = entry.getKey();
            List<SaleOrderItem> groupItems = entry.getValue();

            PurchaseOrder po = new PurchaseOrder();
            po.setOrderDate(LocalDate.now());
            po.setSupplierId(supplierId);
            if (supplierId != null && supplierMap.containsKey(supplierId)) {
                Supplier s = supplierMap.get(supplierId);
                po.setSupplierCode(s.getSupplierCode());
                po.setSupplierName(s.getSupplierName());
                po.setContactPerson(s.getContactPerson());
                po.setContactPhone(s.getContactPhone());
                po.setPaymentMethod(s.getPaymentMethod());
                po.setExpectedDeliveryDate(LocalDate.now().plusDays(s.getLeadTimeDays() != null ? s.getLeadTimeDays() : 7));
            } else {
                po.setSupplierName("待指定");
                po.setExpectedDeliveryDate(saleOrder.getExpectedDeliveryDate());
            }
            po.setBuyer(buyer);
            po.setSourceType("SALE_ORDER");
            po.setSourceOrderNo(saleOrder.getOrderNo());
            po.setStatus("草稿");
            po.setInboundStatus("未入库");
            po.setCurrency("CNY");
            po.setExchangeRate(BigDecimal.ONE);

            // 构建明细
            List<PurchaseOrderItem> poItems = new ArrayList<>();
            for (SaleOrderItem si : groupItems) {
                PurchaseOrderItem poi = new PurchaseOrderItem();
                poi.setMaterialId(si.getMaterialId());
                poi.setMaterialCode(si.getMaterialCode());
                poi.setMaterialName(si.getMaterialName());
                poi.setSpecification(si.getSpecification());
                poi.setUnit(si.getUnit());
                poi.setWarehouse(si.getWarehouse() != null ? si.getWarehouse() : "主仓库");
                poi.setQuantity(si.getQuantity());
                poi.setSourceOrderItemId(si.getId());

                // 采购价取物料的采购价
                Material mat = materialService.getOne(new QueryWrapper<Material>().eq("material_code", si.getMaterialCode()));
                poi.setUnitPrice(mat != null && mat.getPurchasePrice() != null ? mat.getPurchasePrice() : BigDecimal.ZERO);
                poi.setTaxRate(new BigDecimal("13"));

                poItems.add(poi);
            }

            saveOrderWithItems(po, poItems);
            result.add(po);
            log.info("销售转采购: 销售单{} → 采购单{} 供应商{} 行数{}", saleOrder.getOrderNo(), po.getOrderNo(), po.getSupplierName(), poItems.size());
        }

        return result;
    }

    // ========== MRP转采购订单 ==========

    @Override
    @Transactional
    public List<PurchaseOrder> generateFromMrp(List<Map<String, Object>> suggestions, String buyer) {
        if (suggestions == null || suggestions.isEmpty()) {
            throw new BusinessException("没有MRP采购建议");
        }

        // 按供应商分组
        Map<Long, List<Map<String, Object>>> supplierGroup = new LinkedHashMap<>();
        Map<Long, Supplier> supplierMap = new HashMap<>();

        for (Map<String, Object> sug : suggestions) {
            String materialCode = (String) sug.get("materialCode");
            Material material = materialService.getOne(
                    new QueryWrapper<Material>().eq("material_code", materialCode));
            if (material == null) {
                throw new BusinessException("物料不存在: " + materialCode);
            }

            // 查找供应商，取物料的默认供应商（如果有）
            // 这里简化处理，查询所有供应商取第一个
            Long supplierId = null;
            Supplier supplier = null;
            try {
                List<Supplier> suppliers = supplierService.list();
                if (!suppliers.isEmpty()) {
                    supplier = suppliers.get(0);
                    supplierId = supplier.getId();
                    supplierMap.put(supplierId, supplier);
                }
            } catch (Exception e) {
                log.warn("无供应商数据");
            }

            supplierGroup.computeIfAbsent(supplierId, k -> new ArrayList<>()).add(sug);
        }

        // 为每个供应商生成一个采购订单
        List<PurchaseOrder> result = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> entry : supplierGroup.entrySet()) {
            Long supplierId = entry.getKey();
            List<Map<String, Object>> groupItems = entry.getValue();

            PurchaseOrder po = new PurchaseOrder();
            po.setOrderDate(LocalDate.now());
            po.setSupplierId(supplierId);
            if (supplierId != null && supplierMap.containsKey(supplierId)) {
                Supplier s = supplierMap.get(supplierId);
                po.setSupplierCode(s.getSupplierCode());
                po.setSupplierName(s.getSupplierName());
                po.setContactPerson(s.getContactPerson());
                po.setContactPhone(s.getContactPhone());
                po.setPaymentMethod(s.getPaymentMethod());
                po.setExpectedDeliveryDate(LocalDate.now().plusDays(s.getLeadTimeDays() != null ? s.getLeadTimeDays() : 7));
            } else {
                po.setSupplierName("待指定");
                po.setExpectedDeliveryDate(LocalDate.now().plusDays(7));
            }
            po.setBuyer(buyer);
            po.setSourceType("MRP");
            po.setStatus("草稿");
            po.setInboundStatus("未入库");
            po.setCurrency("CNY");
            po.setExchangeRate(BigDecimal.ONE);

            // 构建明细
            List<PurchaseOrderItem> poItems = new ArrayList<>();
            for (Map<String, Object> sug : groupItems) {
                String materialCode = (String) sug.get("materialCode");
                Material mat = materialService.getOne(new QueryWrapper<Material>().eq("material_code", materialCode));

                PurchaseOrderItem poi = new PurchaseOrderItem();
                poi.setMaterialId(mat != null ? mat.getId() : null);
                poi.setMaterialCode(materialCode);
                poi.setMaterialName((String) sug.get("materialName"));
                poi.setSpecification(mat != null ? mat.getSpecification() : null);
                poi.setUnit((String) sug.get("unit"));
                poi.setWarehouse(mat != null ? mat.getDefaultWarehouse() : "WH001");
                poi.setQuantity(new BigDecimal(sug.get("quantity").toString()));
                poi.setUnitPrice(mat != null && mat.getPurchasePrice() != null ? mat.getPurchasePrice() : BigDecimal.ZERO);
                poi.setTaxRate(new BigDecimal("13"));
                poi.setSuggestedOrderDate((java.time.LocalDate) sug.get("suggestedOrderDate"));

                poItems.add(poi);
            }

            saveOrderWithItems(po, poItems);
            result.add(po);
            log.info("MRP生成采购订单: {} 供应商: {} 行数: {}", po.getOrderNo(), po.getSupplierName(), poItems.size());
        }

        return result;
    }

    // ========== 采购入库 (增加库存) ==========

    @Override
    @Transactional
    public void inbound(Long purchaseOrderId, String warehouse, String operator) {
        PurchaseOrder order = getDetail(purchaseOrderId);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"已确认".equals(order.getStatus()) && !"已入库".equals(order.getStatus())) {
            throw new BusinessException("只有已确认状态可入库，当前: " + order.getStatus());
        }

        for (PurchaseOrderItem item : order.getItems()) {
            // 增加库存
            String wh = warehouse != null ? warehouse : (item.getWarehouse() != null ? item.getWarehouse() : "主仓库");
            Inventory inv = inventoryService.getOne(
                    new QueryWrapper<Inventory>()
                            .eq("material_code", item.getMaterialCode())
                            .eq("warehouse", wh)
                            .last("LIMIT 1"));

            if (inv == null) {
                // 新建库存记录
                inv = new Inventory();
                inv.setMaterialCode(item.getMaterialCode());
                inv.setMaterialName(item.getMaterialName());
                inv.setWarehouse(wh);
                inv.setQuantity(item.getQuantity());
                inv.setSafetyStock(BigDecimal.ZERO);
                inv.setLockedQuantity(BigDecimal.ZERO);
                inventoryService.save(inv);
            } else {
                inv.setQuantity(inv.getQuantity().add(item.getQuantity()));
                inventoryService.updateById(inv);
            }

            // 更新已入库数量
            item.setReceivedQuantity(item.getQuantity());
            itemMapper.updateById(item);
        }

        order.setStatus("已入库");
        order.setInboundStatus("已入库");
        updateById(order);
        log.info("采购入库完成: {} 仓库: {} 操作人: {}", order.getOrderNo(), warehouse, operator);
    }

    // ========== 生成单号 ==========

    @Override
    public String generateOrderNo() {
        String prefix = "PO" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
        PurchaseOrder last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getOrderNo() != null) {
            try { seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }
}
