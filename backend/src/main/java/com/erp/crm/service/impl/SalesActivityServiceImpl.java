package com.erp.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.erp.common.scope.DataScopeResolver;
import com.erp.config.service.SysConfigService;
import com.erp.crm.entity.FollowUp;
import com.erp.crm.mapper.FollowUpMapper;
import com.erp.crm.service.PromotionRecordService;
import com.erp.crm.service.SalesActivityService;
import com.erp.freesample.entity.FreeSampleOrder;
import com.erp.freesample.mapper.FreeSampleOrderMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 销售过程动作量化 · 指标统计实现（2026-10-07）
 *
 * <p>口径（立项登记表第七节）：
 * ① 回访老客户 = 当天"客户"来源、跟进方式∈{电话,微信}、且（通话≥30秒 或 交流≥1条）的去重客户数；
 * ② 新增意向微信 = 当天建档且"微信"非空的客户数；
 * ③ 深入业务交流 = 当天「是否深入交流=1」的去重客户数；
 * ④ 私海超期未联系 = 私海客户中（今天 − 最后联系日期）> 30 天 的数量；
 * ⑤ 寄样品（周）= 当周免费样品订单中状态∈{已发货,已完成}且快递单号非空的单据数；
 * ⑥ 朋友圈推广（周）/ RFID 覆盖率 = 推广记录口径（PromotionRecordService）；
 * ⑦ 过程奖金：四项全 100% 才发放，任一不足即为 0 元（用户 2026-10-07 拍板，不给容差）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesActivityServiceImpl implements SalesActivityService {

    private final FollowUpMapper followUpMapper;
    private final CustomerService customerService;
    private final FreeSampleOrderMapper freeSampleOrderMapper;
    private final PromotionRecordService promotionRecordService;
    private final SysConfigService sysConfigService;
    /** 员工 → 登录账号（created_by 过滤）解析用；统计不写业务表，仅"新增意向微信"的 30 天去重标记会落一列 */
    private final JdbcTemplate jdbcTemplate;

    /** 计入「回访」的跟进方式 */
    private static final List<String> VISIT_TYPES = List.of("电话", "微信");

    @Override
    public Map<String, Object> daily(LocalDate date, Long staffId, DataScopeResolver.DataScopeContext ctx) {
        LocalDate day = date == null ? LocalDate.now() : date;
        // 员工参数贯穿全部统计：解析该员工绑定的登录账号作为 created_by 过滤（查不到按员工姓名兜底 / 不缩小范围）
        String staffUsername = staffUsername(staffId);

        int visit = countVisit(day, staffUsername, ctx);
        int newWechat = countNewWechat(day, staffUsername, ctx);
        int deep = countDeep(day, staffUsername, ctx);
        int privateOverdue = countPrivateOverdue(staffUsername, ctx);

        int visitTarget = cfgInt("sales.process.daily_visit", 30);
        int wechatTarget = cfgInt("sales.process.daily_new_wechat", 5);
        int deepTarget = cfgInt("sales.process.daily_deep_chat", 3);

        // 本周任务
        LocalDate monday = day.minusDays(day.getDayOfWeek().getValue() - 1L);
        LocalDate sunday = monday.plusDays(6);
        int sample = countSample(monday, sunday, staffUsername, ctx);
        int sampleTarget = cfgInt("sales.process.weekly_sample", 5);

        Map<String, Object> promo = promotionRecordService.weekStats(day, staffId, ctx);
        int promoCount = toInt(promo.get("promotionCount"));
        int promoTarget = toInt(promo.get("weeklyPromotionTarget"));
        int rfidCovered = toInt(promo.get("rfidCoveredCustomerCount"));
        int intentTotal = countIntentCustomers(staffUsername, ctx);
        int rfidRate = intentTotal == 0 ? -1 : (int) Math.round(rfidCovered * 100.0 / intentTotal);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        data.put("statStartDate", sysConfigService.getValue("sales.process.stat_start_date", ""));
        data.put("daily", List.of(
                metric("visit", "回访老客户", visit, visitTarget, "家"),
                metric("newWechat", "新增意向微信", newWechat, wechatTarget, "家"),
                metric("deepChat", "深入业务交流", deep, deepTarget, "家"),
                metric("privateOverdue", "私海超期未联系", privateOverdue, 0, "家")
        ));
        data.put("weekly", List.of(
                metric("sample", "寄样品 + 彩页 + 名片", sample, sampleTarget, "份"),
                metric("promotion", "朋友圈推广", promoCount, promoTarget, "次"),
                metric("rfid", "RFID 素材覆盖", rfidRate < 0 ? 0 : rfidRate,
                        cfgInt("sales.process.rfid_coverage", 100), "%")
        ));
        data.put("weekStart", monday.toString());
        data.put("weekEnd", sunday.toString());
        data.put("intentCustomerTotal", intentTotal);
        data.put("rfidCoveredCustomerCount", rfidCovered);
        data.put("tasks", todayTasks(day, staffUsername, ctx));
        return data;
    }

    @Override
    public Map<String, Object> bonus(int year, int month, Long staffId, DataScopeResolver.DataScopeContext ctx) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.with(TemporalAdjusters.lastDayOfMonth());
        String staffUsername = staffUsername(staffId);

        // 每日任务：当月每个工作日四项全达标才算"达标日"
        int workDays = 0;
        int passDays = 0;
        LinkedHashMap<String, Boolean> dayDetail = new LinkedHashMap<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
            workDays++;
            boolean ok = countVisit(d, staffUsername, ctx) >= cfgInt("sales.process.daily_visit", 30)
                    && countNewWechat(d, staffUsername, ctx) >= cfgInt("sales.process.daily_new_wechat", 5)
                    && countDeep(d, staffUsername, ctx) >= cfgInt("sales.process.daily_deep_chat", 3)
                    && countPrivateOverdue(staffUsername, ctx) == 0;
            if (ok) passDays++;
            dayDetail.put(d.toString(), ok);
        }

        // 每周任务：当月覆盖到的自然周里，三项全达标才算"达标周"
        int weeks = 0;
        int passWeeks = 0;
        int sampleTarget = cfgInt("sales.process.weekly_sample", 5);
        int promoCountTarget = cfgInt("sales.process.weekly_promotion", 3);
        int rfidTarget = cfgInt("sales.process.rfid_coverage", 100);
        for (LocalDate monday = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
             !monday.isAfter(last); monday = monday.plusWeeks(1)) {
            LocalDate sunday = monday.plusDays(6);
            if (sunday.isBefore(first)) continue;   // 与本月无交集
            weeks++;
            int sample = countSample(monday, sunday, staffUsername, ctx);
            Map<String, Object> promo = promotionRecordService.weekStats(monday, staffId, ctx);
            int promoCount = toInt(promo.get("promotionCount"));
            int covered = toInt(promo.get("rfidCoveredCustomerCount"));
            int total = countIntentCustomers(staffUsername, ctx);
            int rate = total == 0 ? 100 : (int) Math.round(covered * 100.0 / total);
            if (sample >= sampleTarget && promoCount >= promoCountTarget && rate >= rfidTarget) passWeeks++;
        }

        int dailyRate = workDays == 0 ? 100 : (int) Math.round(passDays * 100.0 / workDays);
        int weeklyRate = weeks == 0 ? 100 : (int) Math.round(passWeeks * 100.0 / weeks);
        int privateRate = privateContactRate(staffUsername, ctx);
        int rfidRate2 = rfidRate(staffId, staffUsername, ctx);

        boolean allPass = dailyRate >= 100 && weeklyRate >= 100 && privateRate >= 100 && rfidRate2 >= 100;
        int amount = cfgInt("sales.process.bonus_amount", 500);
        // 容差开关（sales.process.bonus_tolerance）默认 0 = 一刀切：四项任一不足 100% 即为 0 元（用户 2026-10-07 拍板）
        boolean tolerant = cfgInt("sales.process.bonus_tolerance", 0) > 0;
        int bonus = (allPass || tolerant) && allPass ? amount : 0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", year);
        data.put("month", month);
        data.put("statStartDate", sysConfigService.getValue("sales.process.stat_start_date", ""));
        data.put("workDays", workDays);
        data.put("passDays", passDays);
        data.put("dailyRate", dailyRate);
        data.put("weeks", weeks);
        data.put("passWeeks", passWeeks);
        data.put("weeklyRate", weeklyRate);
        data.put("privateContactRate", privateRate);
        data.put("rfidRate", rfidRate2);
        data.put("allPass", allPass);
        data.put("bonusAmount", bonus);
        data.put("dayDetail", dayDetail);
        return data;
    }

    // ==================== 取数（唯一口径） ====================

    /**
     * 员工 → 登录账号（作为 created_by 过滤值）。staffId 为空 → 返回 null（不按员工缩小，保持原数据范围口径）。
     * 员工未绑定登录账号时按员工姓名兜底（文档 6.4：先按 employee.user_id 匹配，匹配不到按单据创建人兜底）。
     */
    private String staffUsername(Long staffId) {
        if (staffId == null) return null;
        try {
            List<String> u = jdbcTemplate.queryForList(
                    "SELECT u.username FROM sys_user u JOIN employee e ON e.user_id = u.user_id " +
                    "WHERE e.employee_id = ? LIMIT 1", String.class, staffId);
            if (!u.isEmpty() && u.get(0) != null && !u.get(0).isBlank()) return u.get(0);
            List<String> n = jdbcTemplate.queryForList(
                    "SELECT employee_name FROM employee WHERE employee_id = ? LIMIT 1", String.class, staffId);
            return (n.isEmpty() || n.get(0) == null) ? null : n.get(0);
        } catch (Exception e) {
            log.warn("[销售过程] 员工账号解析失败 staffId={}: {}", staffId, e.getMessage());
            return null;
        }
    }

    /** 把「员工」维度叠加到查询上（created_by = 所选员工的登录账号） */
    private void applyStaff(QueryWrapper<?> w, String staffUsername) {
        if (staffUsername != null && !staffUsername.isBlank()) w.eq("created_by", staffUsername);
    }

    /** ① 回访老客户：当天"客户"来源、电话/微信、通话≥30秒或交流≥1条 的去重客户数 */
    private int countVisit(LocalDate day, String staffUsername, DataScopeResolver.DataScopeContext ctx) {
        QueryWrapper<FollowUp> w = new QueryWrapper<>();
        w.eq("source_type", "客户")
                .eq("follow_up_date", day)
                .in("follow_up_type", VISIT_TYPES)
                .and(x -> x.ge("call_duration_seconds", 30).or().ge("chat_message_count", 1))
                .select("DISTINCT source_id");
        applyStaff(w, staffUsername);
        if (ctx != null) ctx.applyTo(w, null);
        return followUpMapper.selectMaps(w).size();
    }

    /** ③ 深入业务交流：当天「是否深入交流=1」的去重客户数 */
    private int countDeep(LocalDate day, String staffUsername, DataScopeResolver.DataScopeContext ctx) {
        QueryWrapper<FollowUp> w = new QueryWrapper<>();
        w.eq("source_type", "客户")
                .eq("follow_up_date", day)
                .eq("is_deep_communication", 1)
                .select("DISTINCT source_id");
        applyStaff(w, staffUsername);
        if (ctx != null) ctx.applyTo(w, null);
        return followUpMapper.selectMaps(w).size();
    }

    /**
     * ② 新增意向微信（文档第七节第 2 条）：
     * 当天新建、或当天"微信"字段首次由空变非空的客户数（去重）；同一客户 30 天内不重复计数。
     *
     * <p><b>去重为何不需要额外状态</b>：计入条件限定在"事件发生在当天"——
     * 客户的 `created_at` 与 `wechat_set_at` 各只有一个值，同一个客户在一个自然日内最多命中一次，
     * "30 天内不重复计数"天然成立。此前用 `wechat_counted_at` 记录"已计数日期"来实现去重，
     * 会在**读接口里写库**，导致第二次读取被自己的标记挡掉（刷新页面指标归零），已移除。</p>
     */
    private int countNewWechat(LocalDate day, String staffUsername, DataScopeResolver.DataScopeContext ctx) {
        QueryWrapper<Customer> w = new QueryWrapper<>();
        w.select("customer_id")
                .isNotNull("wechat").ne("wechat", "")
                .and(x -> x.apply("DATE(created_at) = {0}", day)
                        .or().apply("DATE(wechat_set_at) = {0}", day));
        applyStaff(w, staffUsername);
        if (ctx != null) ctx.applyTo(w, null);
        return customerService.list(w).size();
    }

    /** ④ 私海超期未联系：私海客户中（今天 − 最后联系日期）> 配置天数 */
    private int countPrivateOverdue(String staffUsername, DataScopeResolver.DataScopeContext ctx) {
        LocalDate threshold = LocalDate.now().minusDays(cfgInt("sales.process.private_overdue_days", 30));
        QueryWrapper<Customer> w = new QueryWrapper<>();
        w.eq("pool_type", 0)
                .and(x -> x.isNull("last_contact_date").or().lt("last_contact_date", threshold));
        applyStaff(w, staffUsername);
        if (ctx != null) ctx.applyTo(w, null);
        return (int) customerService.count(w);
    }

    /** ⑤ 寄样品（周）：当周免费样品订单中状态∈{已发货,已完成}且快递单号非空 */
    private int countSample(LocalDate monday, LocalDate sunday, String staffUsername,
                            DataScopeResolver.DataScopeContext ctx) {
        QueryWrapper<FreeSampleOrder> w = new QueryWrapper<>();
        w.ge("order_date", monday).le("order_date", sunday)
                .in("status", List.of("已发货", "已完成"))
                .isNotNull("express_no").ne("express_no", "");
        applyStaff(w, staffUsername);
        if (ctx != null) ctx.applyTo(w, "sales_id");
        Long c = freeSampleOrderMapper.selectCount(w);
        return c == null ? 0 : c.intValue();
    }

    /** 意向客户总数（RFID 覆盖率分母）：意向等级非空 或 CRM 状态∈{潜在客户,意向客户} */
    private int countIntentCustomers(String staffUsername, DataScopeResolver.DataScopeContext ctx) {
        QueryWrapper<Customer> w = new QueryWrapper<>();
        w.and(x -> x.isNotNull("intention_level").ne("intention_level", "")
                .or().in("crm_status", List.of("潜在客户", "意向客户")));
        applyStaff(w, staffUsername);
        if (ctx != null) ctx.applyTo(w, null);
        return (int) customerService.count(w);
    }

    /** 私海联系率（实时口径）：无超期私海客户数 ÷ 私海客户总数 ×100；无私海客户返回 -1（页面显示"暂无数据"） */
    private int privateContactRate(String staffUsername, DataScopeResolver.DataScopeContext ctx) {
        QueryWrapper<Customer> all = new QueryWrapper<>();
        all.eq("pool_type", 0);
        applyStaff(all, staffUsername);
        if (ctx != null) ctx.applyTo(all, null);
        long total = customerService.count(all);
        if (total == 0) return -1;
        long bad = countPrivateOverdue(staffUsername, ctx);
        return (int) Math.round(Math.max(0, total - bad) * 100.0 / total);
    }

    /** RFID 覆盖率（%） */
    private int rfidRate(Long staffId, String staffUsername, DataScopeResolver.DataScopeContext ctx) {
        int total = countIntentCustomers(staffUsername, ctx);
        if (total == 0) return -1;
        Map<String, Object> promo = promotionRecordService.weekStats(LocalDate.now(), staffId, ctx);
        int covered = toInt(promo.get("rfidCoveredCustomerCount"));
        return (int) Math.round(covered * 100.0 / total);
    }

    /**
     * 今日任务清单（2026-10-09 按立项登记表口径补第 ② 类）：
     * ① 计划今天跟进的客户（next_follow_up_date = 当天）；
     * ② 私海超期未联系的客户（今天 − 最后联系日期 &gt; 配置天数）——与日任务卡第 4 项、
     *    预警规则「私海 N 天未联系」同口径。此前只列 ①，导致"库里没有排到今天的跟进计划时清单恒空"。
     * 两类按客户名去重（已排今天跟进的不再重复列）。
     */
    private List<Map<String, Object>> todayTasks(LocalDate day, String staffUsername,
                                                 DataScopeResolver.DataScopeContext ctx) {
        QueryWrapper<FollowUp> w = new QueryWrapper<>();
        w.eq("next_follow_up_date", day);
        applyStaff(w, staffUsername);
        if (ctx != null) ctx.applyTo(w, null);
        w.orderByDesc("important_flag").last("LIMIT 20");
        List<FollowUp> list = followUpMapper.selectList(w);
        List<Map<String, Object>> tasks = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FollowUp fu : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("followUpId", fu.getFollowUpId());
            m.put("sourceName", fu.getSourceName());
            m.put("targetId", fu.getSourceId());   // 客户/线索/商机ID：供前端跳「跟进记录」时预填来源
            m.put("followUpType", fu.getFollowUpType());
            m.put("dueDate", fu.getNextFollowUpDate() == null ? null : fu.getNextFollowUpDate().toString());
            m.put("content", fu.getNextPlan());
            tasks.add(m);
            if (fu.getSourceName() != null) seen.add(fu.getSourceName());
        }

        // ② 私海超期未联系：pool_type=0 且（最后联系日期早于阈值，或从未联系）
        try {
            int overdueDays = cfgInt("sales.process.private_overdue_days", 30);
            LocalDate threshold = LocalDate.now().minusDays(overdueDays);
            QueryWrapper<Customer> cw = new QueryWrapper<>();
            cw.eq("pool_type", 0)
                    .and(x -> x.isNull("last_contact_date").or().lt("last_contact_date", threshold));
            applyStaff(cw, staffUsername);
            if (ctx != null) ctx.applyTo(cw, null);
            cw.orderByAsc("last_contact_date").last("LIMIT 50");
            for (Customer c : customerService.list(cw)) {
                String name = c.getCustomerName();
                if (name != null && !seen.add(name)) continue;   // 已排今天跟进的不重复列
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("followUpId", null);
                m.put("sourceName", name);
                m.put("targetId", c.getCustomerId());   // 客户ID：供前端跳「跟进记录」时预填来源
                m.put("followUpType", "私海超期未联系");
                m.put("dueDate", c.getLastContactDate() == null ? null : c.getLastContactDate().toString());
                m.put("content", "已超过 " + overdueDays + " 天未联系，建议今日电话/微信跟进");
                tasks.add(m);
            }
        } catch (Exception e) {
            // 补充失败不影响清单主链路（① 部分照常返回）
            log.warn("[销售过程] 今日任务清单补充私海超期客户失败: {}", e.getMessage());
        }
        return tasks;
    }

    // ==================== 工具 ====================

    private Map<String, Object> metric(String key, String label, int done, int target, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("done", done);
        m.put("target", target);
        m.put("unit", unit);
        return m;
    }

    private int cfgInt(String key, int defaultValue) {
        try {
            return sysConfigService.getIntValue(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
