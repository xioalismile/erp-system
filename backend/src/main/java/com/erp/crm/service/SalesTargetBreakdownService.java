package com.erp.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * 销售目标 · 五级分解（年 → 季 → 月 → 周 → 日）（2026-10-07）
 *
 * <p>口径（用户 2026-10-07 拍板）：
 * ① 季之和 = 年度目标；月 = 季 ÷ 3；<b>月→周的尾差补到该月最后一个周</b>；周→日的尾差补到该周最后一个工作日；
 * ② 月允许手工覆盖（落 sales_target_month，is_manual=1），其余月份按公式实时算；
 * ③ 实际完成：年/季取目标分配表里已维护的实际金额，月/周/日按销售订单（已生效）实时汇总。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesTargetBreakdownService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> breakdown(Integer year, Long employeeId) {
        int y = year == null ? LocalDate.now().getYear() : year;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", y);
        out.put("employeeId", employeeId);

        if (employeeId == null) {
            out.put("available", false);
            out.put("reason", "请先选择销售人员");
            return out;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT employee_name, annual_target_amount, q1_target, q2_target, q3_target, q4_target, actual_amount, " +
                "new_customer_target, new_customer_actual, order_count_target, order_count_actual " +
                "FROM sales_target_employee WHERE target_year = ? AND employee_id = ? LIMIT 1", y, employeeId);
        if (rows.isEmpty()) {
            out.put("available", false);
            out.put("reason", "该员工在 " + y + " 年还没有分配目标 —— 请先在销售目标里分配年度目标");
            return out;
        }
        Map<String, Object> r = rows.get(0);
        out.put("employeeName", r.get("employee_name"));
        out.put("available", true);

        BigDecimal[] quarters = new BigDecimal[]{
                dec(r.get("q1_target")), dec(r.get("q2_target")), dec(r.get("q3_target")), dec(r.get("q4_target"))
        };
        BigDecimal annual = dec(r.get("annual_target_amount"));
        BigDecimal annualActual = dec(r.get("actual_amount"));

        // 手工覆盖的月份（只有被改过的月份才有行）
        Map<Integer, BigDecimal> override = new HashMap<>();
        for (Map<String, Object> m : jdbcTemplate.queryForList(
                "SELECT month_no, target_amount FROM sales_target_month WHERE target_year = ? AND employee_id = ?",
                y, employeeId)) {
            override.put(Integer.valueOf(m.get("month_no").toString()), dec(m.get("target_amount")));
        }

        BigDecimal[] monthTargets = computeMonthTargets(quarters, override);
        LocalDate today = LocalDate.now();
        int curMonth = today.getMonthValue();
        BigDecimal monthTarget = monthTargets[curMonth - 1];

        LocalDate mStart = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate mEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate qStart = LocalDate.of(y, (curMonth - 1) / 3 * 3 + 1, 1);
        LocalDate qEnd = qStart.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        String username = usernameOf(employeeId);

        // 月中入/离职折算（文档 6.4）：目标月内入职或离职 → 月目标按在职工作日占比折算
        LocalDate entryDate = employeeDate(employeeId, "entry_date");
        LocalDate leaveDate = employeeDate(employeeId, "leave_date");
        int monthAllWd = countWorkdays(mStart, mEnd);
        int monthEmpWd = countEmployedWorkdays(mStart, mEnd, entryDate, leaveDate);
        boolean monthAffected = inRange(entryDate, mStart, mEnd) || inRange(leaveDate, mStart, mEnd);
        if (monthAffected && monthAllWd > 0 && monthEmpWd < monthAllWd) {
            monthTarget = prorate(monthTarget, monthEmpWd, monthAllWd);
        }

        // 当前周与该月周数（周一为一周开始）
        LocalDate weekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekSunday = weekMonday.plusDays(6);
        int weeksInMonth = countWeeksInMonth(y, curMonth);
        BigDecimal weekBase = monthTarget.divide(BigDecimal.valueOf(weeksInMonth), 2, RoundingMode.DOWN);
        boolean isLastWeekOfMonth = weekSunday.getMonthValue() != curMonth || weekSunday.plusDays(7).getMonthValue() != curMonth;
        BigDecimal weekTarget = isLastWeekOfMonth
                ? monthTarget.subtract(weekBase.multiply(BigDecimal.valueOf(weeksInMonth - 1L)))
                : weekBase;

        // 周内入/离职折算：周目标按该周在职工作日占比折算
        int weekAllWd = countWorkdays(weekMonday, weekSunday);
        int weekEmpWd = countEmployedWorkdays(weekMonday, weekSunday, entryDate, leaveDate);
        if (weekEmpWd < weekAllWd && weekAllWd > 0) {
            weekTarget = prorate(weekTarget, weekEmpWd, weekAllWd);
        }

        // 今日目标：周目标 ÷ 该周在职工作日数，尾差补该周最后一个在职工作日
        int workdays = Math.max(weekEmpWd, 1);
        BigDecimal dayBase = weekTarget.divide(BigDecimal.valueOf(workdays), 2, RoundingMode.DOWN);
        boolean isLastWorkday = today.getDayOfWeek() == DayOfWeek.FRIDAY || today.plusDays(1).getDayOfWeek() == DayOfWeek.SATURDAY;
        BigDecimal dayTarget = isLastWorkday
                ? weekTarget.subtract(dayBase.multiply(BigDecimal.valueOf(Math.max(workdays - 1, 0))))
                : dayBase;

        List<Map<String, Object>> levels = new ArrayList<>();
        levels.add(level("年（" + y + "）", annual, annualActual));
        levels.add(level("季（Q" + ((curMonth - 1) / 3 + 1) + "）",
                quarters[(curMonth - 1) / 3], sumOrders(username, qStart, qEnd)));
        levels.add(level("月（" + curMonth + " 月，可手工覆盖）", monthTarget, sumOrders(username, mStart, mEnd)));
        levels.add(level("周（" + weekMonday + " ~ " + weekSunday + "）", weekTarget, sumOrders(username, weekMonday, weekSunday)));
        levels.add(level("日（今日）", dayTarget, sumOrders(username, today, today)));

        out.put("rows", levels);
        List<Map<String, Object>> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            Map<String, Object> mo = new LinkedHashMap<>();
            mo.put("month", i);
            mo.put("target", monthTargets[i - 1]);
            mo.put("manual", override.containsKey(i));
            mo.put("actual", sumOrders(username, LocalDate.of(y, i, 1),
                    LocalDate.of(y, i, 1).with(TemporalAdjusters.lastDayOfMonth())));
            months.add(mo);
        }
        out.put("months", months);
        out.put("newCustomerTarget", r.get("new_customer_target"));
        out.put("newCustomerActual", r.get("new_customer_actual"));
        out.put("orderCountTarget", r.get("order_count_target"));
        out.put("orderCountActual", r.get("order_count_actual"));
        return out;
    }

    /** 保存某月的手工覆盖（is_manual=1），upsert */
    public void saveMonthOverride(Integer year, Long employeeId, Integer monthNo, BigDecimal amount) {
        int n = jdbcTemplate.update(
                "UPDATE sales_target_month SET target_amount = ?, is_manual = 1, update_date = NOW() " +
                "WHERE target_year = ? AND employee_id = ? AND month_no = ?", amount, year, employeeId, monthNo);
        if (n == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sales_target_month (target_year, employee_id, month_no, target_amount, is_manual, create_date) " +
                    "VALUES (?, ?, ?, ?, 1, NOW())", year, employeeId, monthNo, amount);
        }
    }

    /** 清除某月的手工覆盖（回到 季 ÷ 3） */
    public void clearMonthOverride(Integer year, Long employeeId, Integer monthNo) {
        jdbcTemplate.update("DELETE FROM sales_target_month WHERE target_year = ? AND employee_id = ? AND month_no = ?",
                year, employeeId, monthNo);
    }

    // ==================== 计算 ====================

    /** 月目标：手工覆盖优先；否则 季 ÷ 3，尾差补到该季最后一个月 */
    private BigDecimal[] computeMonthTargets(BigDecimal[] quarters, Map<Integer, BigDecimal> override) {
        BigDecimal[] result = new BigDecimal[12];
        for (int q = 0; q < 4; q++) {
            BigDecimal base = quarters[q].divide(BigDecimal.valueOf(3), 2, RoundingMode.DOWN);
            BigDecimal last = quarters[q].subtract(base.multiply(BigDecimal.valueOf(2)));
            for (int k = 0; k < 3; k++) {
                int month = q * 3 + k + 1;
                BigDecimal computed = (k == 2) ? last : base;
                result[month - 1] = override.getOrDefault(month, computed);
            }
        }
        return result;
    }

    /** 该月覆盖到的自然周数（按周一划分） */
    private int countWeeksInMonth(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.with(TemporalAdjusters.lastDayOfMonth());
        int weeks = 0;
        for (LocalDate m = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
             !m.isAfter(last); m = m.plusWeeks(1)) {
            if (!m.plusDays(6).isBefore(first)) weeks++;
        }
        return Math.max(weeks, 1);
    }

    private int countWorkdays(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) n++;
        }
        return Math.max(n, 1);
    }

    /** 在职工作日数（原始值，可为 0）：区间 ∩ [入职日, 离职日]，按周一至周五计 */
    private int countEmployedWorkdays(LocalDate from, LocalDate to, LocalDate entry, LocalDate leave) {
        LocalDate a = (entry != null && entry.isAfter(from)) ? entry : from;
        LocalDate b = (leave != null && leave.isBefore(to)) ? leave : to;
        if (b.isBefore(a)) return 0;
        int n = 0;
        for (LocalDate d = a; !d.isAfter(b); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) n++;
        }
        return n;
    }

    /** 按在职工作日占比折算：target × empWorkdays ÷ allWorkdays（四舍五入到分） */
    private BigDecimal prorate(BigDecimal target, int empWorkdays, int allWorkdays) {
        if (target == null || allWorkdays <= 0) return target;
        return target.multiply(BigDecimal.valueOf(empWorkdays))
                .divide(BigDecimal.valueOf(allWorkdays), 2, RoundingMode.HALF_UP);
    }

    /** 日期是否落在 [from, to] 区间内（含边界）；null 返回 false */
    private boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    /** 取员工档案的入职/离职日期（列不存在或查不到时返回 null，不阻断页面） */
    private LocalDate employeeDate(Long employeeId, String column) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT " + column + " AS d FROM employee WHERE employee_id = ? LIMIT 1", employeeId);
            if (rows.isEmpty() || rows.get(0).get("d") == null) return null;
            return LocalDate.parse(rows.get(0).get("d").toString());
        } catch (Exception e) {
            log.warn("[目标分解] 读取员工 {} 字段 {} 失败（跳过折算）：{}", employeeId, column, e.getMessage());
            return null;
        }
    }

    /** 实际完成：销售订单（已生效）按创建人汇总；查不到按 0（不阻塞页面） */
    private BigDecimal sumOrders(String username, LocalDate from, LocalDate to) {
        if (username == null) return BigDecimal.ZERO;
        try {
            BigDecimal v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM sale_order " +
                    "WHERE created_by = ? AND status = '已生效' AND order_date BETWEEN ? AND ?",
                    BigDecimal.class, username, from, to);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            log.warn("[目标分解] 汇总销售订单失败（按 0 处理）：{}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String usernameOf(Long employeeId) {
        try {
            List<String> u = jdbcTemplate.queryForList(
                    "SELECT u.username FROM sys_user u JOIN employee e ON e.user_id = u.user_id " +
                    "WHERE e.employee_id = ? LIMIT 1", String.class, employeeId);
            return u.isEmpty() ? null : u.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> level(String label, BigDecimal target, BigDecimal actual) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", label);
        m.put("target", target);
        m.put("actual", actual);
        BigDecimal rate = BigDecimal.ZERO;
        if (target != null && target.compareTo(BigDecimal.ZERO) > 0) {
            rate = (actual == null ? BigDecimal.ZERO : actual)
                    .multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.HALF_UP);
        }
        m.put("rate", rate);
        int v = rate.intValue();
        m.put("status", v >= 100 ? "绿" : (v >= 60 ? "黄" : "橙"));
        return m;
    }

    private BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
