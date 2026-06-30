package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * AI报表生成服务
 * 已对齐PG新实体字段(通过customer_id关联customer表获取客户名)
 */
@Slf4j
@Service("aiReportService")
@RequiredArgsConstructor
public class AiReportService {

    private final SaleOrderService saleOrderService;
    private final CustomerService customerService;

    public Map<String, Object> generateReport(Map<String, Object> params) {
        String reportType = (String) params.getOrDefault("reportType", "sales");
        String dimension = (String) params.getOrDefault("dimension", "customer");

        if ("sales".equals(reportType)) {
            return generateSalesReport(dimension);
        } else if ("inventory".equals(reportType)) {
            return Map.of("message", "库存报表生成中...");
        }
        return Map.of("message", "不支持的报表类型");
    }

    private Map<String, Object> generateSalesReport(String dimension) {
        // 新实体status取值: 草稿/已审核/已关闭/已取消
        List<SaleOrder> orders = saleOrderService.list(new QueryWrapper<SaleOrder>()
                .eq("status", "已审核")
                .orderByDesc("created_at").last("LIMIT 100"));

        // 预加载客户名(通过customer_id关联)
        Set<Long> customerIds = new HashSet<>();
        for (SaleOrder o : orders) {
            if (o.getCustomerId() != null) customerIds.add(o.getCustomerId());
        }
        Map<Long, String> customerNameMap = new HashMap<>();
        if (!customerIds.isEmpty()) {
            for (Customer c : customerService.listByIds(customerIds)) {
                customerNameMap.put(c.getCustomerId(), c.getCustomerName());
            }
        }

        Map<String, BigDecimal> summary = new LinkedHashMap<>();
        for (SaleOrder o : orders) {
            String key = switch (dimension) {
                case "customer" -> customerNameMap.getOrDefault(o.getCustomerId(), "未知客户");
                case "month" -> o.getOrderDate() != null ? o.getOrderDate().toString().substring(0, 7) : "未知";
                default -> customerNameMap.getOrDefault(o.getCustomerId(), "未知客户");
            };
            summary.merge(key, o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO, BigDecimal::add);
        }

        List<Map<String, Object>> chartData = new ArrayList<>();
        for (var entry : summary.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", entry.getKey());
            m.put("value", entry.getValue());
            chartData.add(m);
        }

        return Map.of(
                "reportType", "sales",
                "dimension", dimension,
                "chartType", "bar",
                "data", chartData,
                "totalOrders", orders.size(),
                "totalAmount", summary.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }
}
