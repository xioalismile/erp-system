package com.erp.board.overview;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据看板 · 经营链路取数（定制包内，仅声明了该服务器的构建才编译）
 *
 * 口径：
 *   订单额 = 已生效销售订单含税金额
 *   发货额 = 已生效销售出库单含税金额
 *   开票额 = 已生效销售发票含税金额（红字发票冲减）
 *   已收款 = 已确认收款单金额
 *   应收未收 / 逾期 / 高风险 = 应收账款未结清余额（不按期间过滤，含历史）
 * 数据范围：一期仅对"全部数据"权限的账号开放，受限账号返回 scopeLimited=true。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverviewFlowService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> flow(String period, boolean allScope) {
        LocalDate today = LocalDate.now();
        LocalDate from = resolveFrom(period, today);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("from", from.toString());
        out.put("to", today.toString());
        out.put("scopeLimited", !allScope);

        if (!allScope) {
            out.put("orderAmount", BigDecimal.ZERO);
            out.put("deliveryAmount", BigDecimal.ZERO);
            out.put("invoiceAmount", BigDecimal.ZERO);
            out.put("receivedAmount", BigDecimal.ZERO);
            out.put("receivableBalance", BigDecimal.ZERO);
            out.put("overdueAmount", BigDecimal.ZERO);
            out.put("riskAmount", BigDecimal.ZERO);
            out.put("criteria", "当前账号的数据范围不是全部数据，经营链路不展示");
            return out;
        }

        BigDecimal order = sum("SELECT COALESCE(SUM(total_amount),0) FROM sale_order "
                + "WHERE status = '已生效' AND order_date BETWEEN ? AND ?", from, today);
        BigDecimal delivery = sum("SELECT COALESCE(SUM(total_amount),0) FROM sale_delivery "
                + "WHERE status = '已生效' AND delivery_date BETWEEN ? AND ?", from, today);
        BigDecimal invoice = sum("SELECT COALESCE(SUM(CASE WHEN is_red_invoice::text IN ('1','true','t') "
                + "THEN -total_amount ELSE total_amount END),0) FROM sale_invoice "
                + "WHERE status = '已生效' AND invoice_date BETWEEN ? AND ?", from, today);
        BigDecimal received = sum("SELECT COALESCE(SUM(total_amount),0) FROM cash_receipt "
                + "WHERE status = '已确认' AND receipt_date BETWEEN ? AND ?", from, today);
        BigDecimal receivable = sum("SELECT COALESCE(SUM(balance_amount),0) FROM account_receivable "
                + "WHERE balance_amount > 0");
        BigDecimal overdue = sum("SELECT COALESCE(SUM(balance_amount),0) FROM account_receivable "
                + "WHERE balance_amount > 0 AND due_date IS NOT NULL AND due_date < CURRENT_DATE");
        BigDecimal risk = sum("SELECT COALESCE(SUM(balance_amount),0) FROM account_receivable "
                + "WHERE balance_amount > 0 AND due_date IS NOT NULL AND due_date < CURRENT_DATE - INTERVAL '180 day'");

        out.put("orderAmount", order);
        out.put("deliveryAmount", delivery);
        out.put("invoiceAmount", invoice);
        out.put("receivedAmount", received);
        out.put("receivableBalance", receivable);
        out.put("overdueAmount", overdue);
        out.put("riskAmount", risk);
        out.put("criteria", "仅已生效单据，按业务日期统计；应收为当前未结清余额，含历史单据");
        return out;
    }

    private BigDecimal sum(String sql, Object... args) {
        try {
            BigDecimal v = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            log.warn("[数据看板] 取数失败按 0 处理：{}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private LocalDate resolveFrom(String period, LocalDate today) {
        if (period == null) return today.with(TemporalAdjusters.firstDayOfMonth());
        switch (period) {
            case "day":
                return today;
            case "week":
                return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "quarter":
                return LocalDate.of(today.getYear(), (today.getMonthValue() - 1) / 3 * 3 + 1, 1);
            case "year":
                return LocalDate.of(today.getYear(), 1, 1);
            default:
                return today.with(TemporalAdjusters.firstDayOfMonth());
        }
    }
}
