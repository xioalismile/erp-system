package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * AI报表生成服务
 */
@Slf4j
@Service("aiReportService")
@RequiredArgsConstructor
public class AiReportService {

    private final SaleOrderService saleOrderService;

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
        List<SaleOrder> orders = saleOrderService.list(new QueryWrapper<SaleOrder>().eq("status", "已完成")
                .or().eq("status", "已发货").orderByDesc("created_at").last("LIMIT 100"));

        Map<String, BigDecimal> summary = new LinkedHashMap<>();
        for (SaleOrder o : orders) {
            String key = switch (dimension) {
                case "customer" -> o.getCustomerName();
                case "month" -> o.getOrderDate() != null ? o.getOrderDate().toString().substring(0, 7) : "未知";
                default -> o.getCustomerName();
            };
            summary.merge(key, o.getTotalAmount(), BigDecimal::add);
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
