package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.service.SaleOrderService;
import com.erp.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * AI订单服务 - 处理AI创建/查询订单的业务逻辑
 * 已对齐PG新实体字段(SaleOrder: customerId/orderDate/contactPerson/contactPhone/currencyId/exchangeRate/status)
 */
@Slf4j
@Service("aiOrderService")
@RequiredArgsConstructor
public class AiOrderService {

    private final SaleOrderService saleOrderService;
    private final CustomerService customerService;
    private final MaterialService materialService;

    /**
     * AI创建草稿订单
     */
    @Transactional
    public Map<String, Object> createDraftOrder(Map<String, Object> params, Long erpUserId) {
        String customerName = (String) params.get("customerName");
        String deliveryDateStr = (String) params.get("deliveryDate");
        List<Map<String, Object>> items = (List<Map<String, Object>>) params.get("items");

        // 查找客户
        Customer customer = customerService.getOne(
                new QueryWrapper<Customer>().like("customer_name", customerName).last("LIMIT 1"));
        if (customer == null) {
            return Map.of("success", false, "message", "未找到客户: " + customerName);
        }

        // 构建订单(对齐PG sale_order表字段)
        SaleOrder order = new SaleOrder();
        order.setOrderDate(LocalDate.now());
        order.setCustomerId(customer.getCustomerId());
        order.setContactPerson(customer.getContactPerson());
        order.setContactPhone(customer.getContactPhone());
        order.setExchangeRate(BigDecimal.ONE);
        order.setDiscountRate(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        if (deliveryDateStr != null) {
            order.setDeliveryDate(LocalDate.parse(deliveryDateStr));
        }
        order.setStatus("草稿");
        order.setApprovalStatus("待审核");
        order.setCreatedBy(erpUserId != null ? erpUserId.toString() : "AI");

        // 构建明细(对齐PG sale_order_item表字段)
        List<SaleOrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int lineNo = 10;

        if (items != null) {
            for (Map<String, Object> item : items) {
                String materialCode = (String) item.get("materialCode");
                String materialName = (String) item.get("materialName");
                BigDecimal quantity = new BigDecimal(item.get("quantity").toString());
                BigDecimal unitPrice = new BigDecimal(item.get("unitPrice").toString());

                Material material = null;
                if (materialCode != null) {
                    material = materialService.getOne(new QueryWrapper<Material>().eq("material_code", materialCode));
                }
                if (material == null && materialName != null) {
                    material = materialService.getOne(new QueryWrapper<Material>().like("material_name", materialName).last("LIMIT 1"));
                }
                if (material == null) {
                    return Map.of("success", false, "message", "未找到物料: " + (materialName != null ? materialName : materialCode));
                }

                SaleOrderItem orderItem = new SaleOrderItem();
                orderItem.setLineNo(lineNo);
                orderItem.setMaterialId(material.getMaterialId());
                orderItem.setMaterialCode(material.getMaterialCode());
                orderItem.setMaterialName(material.getMaterialName());
                orderItem.setSpecification(material.getModel());
                orderItem.setUnitId(material.getBaseUnitId());
                orderItem.setQuantity(quantity);
                orderItem.setUnitPrice(unitPrice);
                orderItem.setDiscountRate(BigDecimal.ZERO);
                BigDecimal amount = unitPrice.multiply(quantity);
                orderItem.setAmount(amount);
                orderItem.setTaxRate(material.getTaxRate() != null ? material.getTaxRate() : new BigDecimal("13"));
                orderItem.setTaxAmount(amount.multiply(orderItem.getTaxRate()).divide(new BigDecimal("100")));
                orderItem.setNetAmount(amount.add(orderItem.getTaxAmount()));
                orderItems.add(orderItem);
                totalAmount = totalAmount.add(amount);
                lineNo += 10;
            }
        }

        order.setTotalAmount(totalAmount);
        order.setTaxAmount(totalAmount.multiply(new BigDecimal("0.13")));
        order.setNetAmount(totalAmount.add(order.getTaxAmount()));

        // 保存
        saleOrderService.saveOrderWithItems(order, orderItems);

        log.info("[AI下单] 订单创建成功: {} 客户ID: {} 金额: {}", order.getOrderNo(), customer.getCustomerId(), totalAmount);

        return Map.of(
                "success", true,
                "orderNo", order.getOrderNo(),
                "customerName", customer.getCustomerName(),
                "totalAmount", totalAmount,
                "itemCount", orderItems.size()
        );
    }

    /**
     * AI查询订单(通过customer_id关联customer表获取客户名)
     */
    public Map<String, Object> queryOrders(Map<String, Object> params) {
        String status = (String) params.get("status");
        String month = (String) params.get("month");

        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        wrapper.last("LIMIT 20");
        if (status != null) wrapper.eq("status", status);
        if (month != null) wrapper.like("order_date", month);

        List<SaleOrder> orders = saleOrderService.list(wrapper);
        // 预加载客户名
        Set<Long> customerIds = new HashSet<>();
        for (SaleOrder o : orders) {
            if (o.getCustomerId() != null) customerIds.add(o.getCustomerId());
        }
        Map<Long, String> customerNameMap = new HashMap<>();
        if (!customerIds.isEmpty()) {
            List<Customer> customers = customerService.listByIds(customerIds);
            for (Customer c : customers) {
                customerNameMap.put(c.getCustomerId(), c.getCustomerName());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (SaleOrder o : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("customerName", customerNameMap.getOrDefault(o.getCustomerId(), "未知客户"));
            m.put("orderDate", o.getOrderDate());
            m.put("totalAmount", o.getTotalAmount());
            m.put("status", o.getStatus());
            result.add(m);
        }
        return Map.of("orders", result, "total", orders.size());
    }

    /**
     * AI查询客户
     */
    public Map<String, Object> queryCustomer(Map<String, Object> params) {
        String customerName = (String) params.get("customerName");
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        if (customerName != null) wrapper.like("customer_name", customerName);
        wrapper.last("LIMIT 10");
        List<Customer> customers = customerService.list(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Customer c : customers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerCode", c.getCustomerCode());
            m.put("customerName", c.getCustomerName());
            m.put("contactPerson", c.getContactPerson());
            m.put("contactPhone", c.getContactPhone());
            m.put("address", c.getAddress());
            result.add(m);
        }
        return Map.of("customers", result, "total", customers.size());
    }
}
