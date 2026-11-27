package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.config.service.SysConfigService;
import com.erp.crm.entity.SalesWarning;
import com.erp.crm.mapper.SalesWarningMapper;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 销售预警引擎（2026-10-07）
 *
 * <p>口径来源：立项登记表第八节。等级分档与处理时限全部读设置中心（sales.process.level.* / deadline.*），不写死。</p>
 *
 * <p><b>已实现的规则</b>：日回访未达标、日新增微信未达标、日深入交流未达标、周样品未寄出、朋友圈推广未达标、
 * RFID 覆盖未达标、私海 30 天未联系、私海 45 天未联系、样品发货 15 天未跟进、新建客户 7 天未联系、
 * 报价 7 天未跟进、回款逾期、未分配客户，以及物流三态（轨迹 24h 无更新 / 拒收签收失败 / 在途超期未签收，
 * 后三者调快递100 轨迹，由定时任务每 15 分钟触发，未配置授权码时整体跳过）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesWarningService {

    private final JdbcTemplate jdbcTemplate;
    private final SysConfigService sysConfigService;
    private final SalesWarningMapper salesWarningMapper;
    private final SysNotificationService sysNotificationService;
    private final PlatformTransactionManager transactionManager;
    /** 数据权限解析器（2026-10-09）：/page 与 /summary 按角色数据范围收敛（归属列 owner_name） */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;
    /** 物流轨迹查询（物流三态预警用；未配置授权码时整体跳过，不产生误报警） */
    private final com.erp.logistics.service.Kuaidi100Service kuaidi100Service;

    /** 生成指定日期的预警（幂等：同 负责人+类型+统计区间 只生成一条） */
    @Transactional
    public Map<String, Object> generate(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<String, Object> stat = new LinkedHashMap<>();
        String startStr = cfg("sales.process.stat_start_date", "");
        if (!startStr.isBlank() && day.isBefore(LocalDate.parse(startStr))) {
            stat.put("skipped", "统计起算日为 " + startStr + "，当日之前不生成预警");
            stat.put("created", 0);
            return stat;
        }

        LocalDate monday = day.minusDays(day.getDayOfWeek().getValue() - 1L);
        LocalDate sunday = monday.plusDays(6);

        // 参与账号 = 当天有跟进 / 本周有推广 / 有私海客户的创建人（避免遍历全部用户产生噪声）
        Set<String> users = new LinkedHashSet<>();
        users.addAll(jdbcTemplate.queryForList(
                "SELECT DISTINCT created_by FROM crm_follow_up WHERE created_by IS NOT NULL AND follow_up_date = ?",
                String.class, day));
        users.addAll(jdbcTemplate.queryForList(
                "SELECT DISTINCT created_by FROM crm_promotion_record WHERE created_by IS NOT NULL AND promotion_date BETWEEN ? AND ?",
                String.class, monday, sunday));
        users.addAll(jdbcTemplate.queryForList(
                "SELECT DISTINCT created_by FROM customer WHERE created_by IS NOT NULL AND pool_type = 0",
                String.class));

        int created = 0;
        for (String u : users) {
            created += genDailyForUser(u, day);
            created += genWeeklyForUser(u, monday, sunday);
        }
        created += genOverduePrivate(day);
        created += genStaleSample(day);
        created += genNewCustomerIdle(day);
        created += genQuoteIdle(day);
        created += genArOverdue(day);
        created += genUnassignedCustomer(day);

        stat.put("date", day.toString());
        stat.put("users", users.size());
        stat.put("created", created);
        log.info("[销售预警] 生成完成：日期={} 参与账号={} 新增预警={}", day, users.size(), created);
        return stat;
    }

    /** 日任务 3 条（回访 / 新增微信 / 深入交流） */
    private int genDailyForUser(String username, LocalDate day) {
        int n = 0;
        int visit = count("SELECT COUNT(DISTINCT source_id) FROM crm_follow_up WHERE created_by = ? " +
                "AND source_type = '客户' AND follow_up_date = ? AND follow_up_type IN ('电话','微信') " +
                "AND (call_duration_seconds >= 30 OR chat_message_count >= 1)", username, day);
        n += addLevel(username, "日回访未达标", visit, cfgInt("sales.process.daily_visit", 30), "家", day, day);

        int wx = count("SELECT COUNT(*) FROM customer WHERE created_by = ? AND DATE(created_at) = ? " +
                "AND wechat IS NOT NULL AND wechat <> ''", username, day);
        n += addLevel(username, "日新增微信未达标", wx, cfgInt("sales.process.daily_new_wechat", 5), "家", day, day);

        int deep = count("SELECT COUNT(DISTINCT source_id) FROM crm_follow_up WHERE created_by = ? " +
                "AND source_type = '客户' AND follow_up_date = ? AND is_deep_communication = 1", username, day);
        n += addLevel(username, "日深入交流未达标", deep, cfgInt("sales.process.daily_deep_chat", 3), "家", day, day);
        return n;
    }

    /** 周任务 3 条（寄样品 / 朋友圈推广 / RFID 覆盖） */
    private int genWeeklyForUser(String username, LocalDate monday, LocalDate sunday) {
        int n = 0;
        int sample = count("SELECT COUNT(*) FROM free_sample_order WHERE created_by = ? AND order_date BETWEEN ? AND ? " +
                "AND status IN ('已发货','已完成') AND express_no IS NOT NULL AND express_no <> ''",
                username, monday, sunday);
        n += addLevel(username, "周样品未寄出", sample, cfgInt("sales.process.weekly_sample", 5), "份", monday, sunday);

        int promo = count("SELECT COUNT(*) FROM crm_promotion_record WHERE created_by = ? AND promotion_date BETWEEN ? AND ? " +
                "AND material_type IN ('朋友圈图片','朋友圈视频') AND attachment IS NOT NULL AND attachment <> ''",
                username, monday, sunday);
        n += addLevel(username, "朋友圈推广未达标", promo, cfgInt("sales.process.weekly_promotion", 3), "次", monday, sunday);

        int intent = count("SELECT COUNT(*) FROM customer WHERE created_by = ? " +
                "AND ((intention_level IS NOT NULL AND intention_level <> '') OR crm_status IN ('潜在客户','意向客户'))", username);
        if (intent > 0) {
            int covered = count("SELECT COUNT(DISTINCT t.customer_id) FROM crm_promotion_target t " +
                    "JOIN crm_promotion_record r ON r.promotion_id = t.promotion_id " +
                    "WHERE r.created_by = ? AND r.material_type = 'RFID素材'", username);
            int rate = (int) Math.round(covered * 100.0 / intent);
            n += addLevel(username, "RFID 覆盖未达标", rate, cfgInt("sales.process.rfid_coverage", 100), "%", monday, sunday);
        }
        return n;
    }

    /**
     * 私海超期（按客户）——文档第八节拆成两条独立类型：
     * 距最后联系 30~44 天 → 「私海30天未联系」（橙）；> 45 天 → 「私海45天未联系」（红）。
     * 25~29 天不产生预警（规则表无此行）。
     */
    private int genOverduePrivate(LocalDate day) {
        int n = 0;
        int fromDays = cfgInt("sales.process.level.yellow_days", 25);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT customer_id, customer_name, created_by, " +
                "COALESCE(last_contact_date, DATE(created_at)) AS last_day " +
                "FROM customer WHERE pool_type = 0 AND COALESCE(last_contact_date, DATE(created_at)) < ? " +
                "ORDER BY last_day ASC LIMIT 200", day.minusDays(fromDays));
        for (Map<String, Object> r : rows) {
            Object lastDay = r.get("last_day");
            if (lastDay == null) continue;
            long days = day.toEpochDay() - LocalDate.parse(lastDay.toString()).toEpochDay();
            if (days <= fromDays) continue;
            String type;
            if (days > 45) type = "私海45天未联系";
            else if (days >= 30) type = "私海30天未联系";
            else continue;
            String owner = r.get("created_by") == null ? null : r.get("created_by").toString();
            String cname = r.get("customer_name") == null ? "未命名客户" : r.get("customer_name").toString();
            Long cid = r.get("customer_id") == null ? null : Long.valueOf(r.get("customer_id").toString());
            if (insertWarning(type, ruleOf(type)[1], "客户", cid, cname, owner,
                    "已 " + days + " 天未联系", "距最后联系超过 " + (days > 45 ? 45 : 30) + " 天", day, day)) {
                n++;
            }
        }
        return n;
    }

    /** 样品发货 15 天仍未跟进（本地判定，不调外部物流接口） */
    private int genStaleSample(LocalDate day) {
        int n = 0;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.order_no, s.customer_id, s.customer_name, s.created_by FROM free_sample_order s " +
                "WHERE s.status IN ('已发货','已完成') AND s.delivery_date IS NOT NULL AND s.delivery_date < ? " +
                "AND NOT EXISTS (SELECT 1 FROM crm_follow_up f WHERE f.source_type = '客户' " +
                "   AND f.source_id = s.customer_id AND f.follow_up_date >= s.delivery_date) LIMIT 200",
                day.minusDays(15));
        for (Map<String, Object> r : rows) {
            String owner = r.get("created_by") == null ? null : r.get("created_by").toString();
            String cname = r.get("customer_name") == null ? "" : r.get("customer_name").toString();
            Long cid = r.get("customer_id") == null ? null : Long.valueOf(r.get("customer_id").toString());
            if (insertWarning("样品15天未转化", "橙", "客户", cid, cname, owner,
                    "已发货超 15 天无跟进", "15 天", day, day)) {
                n++;
            }
        }
        return n;
    }

    /** 新建客户 7 天未联系 */
    private int genNewCustomerIdle(LocalDate day) {
        int n = 0;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT customer_id, customer_name, created_by FROM customer " +
                "WHERE DATE(created_at) = ? AND (last_contact_date IS NULL OR last_contact_date < DATE(created_at)) LIMIT 200",
                day.minusDays(7));
        for (Map<String, Object> r : rows) {
            String owner = r.get("created_by") == null ? null : r.get("created_by").toString();
            String cname = r.get("customer_name") == null ? "" : r.get("customer_name").toString();
            Long cid = r.get("customer_id") == null ? null : Long.valueOf(r.get("customer_id").toString());
            if (insertWarning("新客户7天未联系", "黄", "客户", cid, cname, owner,
                    "建档满 7 天无联系", "7 天", day, day)) {
                n++;
            }
        }
        return n;
    }

    /** 超时未处理 → 自动升级一级并重新计时 */
    @Transactional
    public int escalate() {
        List<SalesWarning> list = salesWarningMapper.selectList(new QueryWrapper<SalesWarning>()
                .in("status", List.of("待处理", "处理中")).isNotNull("deadline")
                .lt("deadline", LocalDateTime.now()));
        int n = 0;
        for (SalesWarning w : list) {
            String next = "黄".equals(w.getWarningLevel()) ? "橙" : ("橙".equals(w.getWarningLevel()) ? "红" : "红");
            SalesWarning up = new SalesWarning();
            up.setWarningId(w.getWarningId());
            up.setWarningLevel(next);
            up.setEscalatedFrom(w.getWarningNo());
            up.setStatus("待处理");
            up.setDeadline(LocalDateTime.now().plusHours(hoursOf(next)));
            up.setPushTo(pushToOf(next));
            salesWarningMapper.updateById(up);
            push(w.getOwnerUserId(), up, w.getWarningType(), w.getTargetName());
            n++;
        }
        if (n > 0) log.info("[销售预警] 超时升级 {} 条", n);
        return n;
    }

    /**
     * 认领：待处理 → 处理中。
     * 文档第八节闭环状态机：待处理 → 处理中（认领）→ 已处理待确认 → 已关闭。
     */
    @Transactional
    public void claim(Long id, Long userId, String userName) {
        SalesWarning w = require(id);
        if (!"待处理".equals(w.getStatus())) {
            throw new com.erp.config.BusinessException("只有「待处理」的预警可以认领");
        }
        SalesWarning up = new SalesWarning();
        up.setWarningId(id);
        up.setStatus("处理中");
        up.setHandlerId(userId);
        up.setHandlerName(userName);
        salesWarningMapper.updateById(up);
    }

    /** 处理（认领）→ 已处理待确认 */
    @Transactional
    public void handle(Long id, String remark, String attachment, Long userId, String userName) {
        SalesWarning w = require(id);
        if ("已关闭".equals(w.getStatus())) {
            throw new com.erp.config.BusinessException("该预警已关闭，不能再登记");
        }
        SalesWarning up = new SalesWarning();
        up.setWarningId(id);
        up.setStatus("已处理待确认");
        up.setHandleRemark(remark);
        up.setHandleAttachment(attachment);
        up.setHandlerId(userId);
        up.setHandlerName(userName);
        up.setHandleTime(LocalDateTime.now());
        salesWarningMapper.updateById(up);
        // 处理完即把对应通知置已读，避免铃铛上留红点（REQUIRES_NEW 独立事务：置已读失败不污染预警处理主事务）
        try {
            TransactionTemplate noticeTx = new TransactionTemplate(transactionManager);
            noticeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            noticeTx.executeWithoutResult(status ->
                    sysNotificationService.markApprovalNoticesRead("sales_warning", id));
        } catch (Exception e) {
            log.warn("预警处理已完成，通知置已读失败（不影响处理结果）: warningId={}, 原因={}", id, e.getMessage());
        }
    }

    /** 关闭（仅主管/管理员在接口层控权） */
    @Transactional
    public void close(Long id, String remark, Long userId, String userName) {
        require(id);
        SalesWarning up = new SalesWarning();
        up.setWarningId(id);
        up.setStatus("已关闭");
        up.setHandleRemark(remark);
        up.setHandlerId(userId);
        up.setHandlerName(userName);
        up.setHandleTime(LocalDateTime.now());
        salesWarningMapper.updateById(up);
    }

    public PageResult<SalesWarning> pageQuery(int current, int size, String level, String status,
                                              String warningType, String keyword) {
        QueryWrapper<SalesWarning> w = new QueryWrapper<>();
        if (level != null && !level.isBlank()) w.eq("warning_level", level);
        if (status != null && !status.isBlank()) w.eq("status", status);
        if (warningType != null && !warningType.isBlank()) w.eq("warning_type", warningType);
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like("warning_no", keyword).or().like("target_name", keyword).or().like("owner_name", keyword));
        }
        // 数据权限（2026-10-09）：预警按「负责人」收敛——仅本人=我的预警；本部门=本部门成员的预警；全部=不过滤。
        // 说明：本表由定时任务生成，created_by 恒为 "system"，故归属列取 owner_name（负责人用户名列）。
        dataScopeResolver.applyIfLimited(w, null, "owner_name");
        w.orderByAsc("status").orderByAsc("deadline").orderByDesc("warning_id");
        Page<SalesWarning> p = new Page<>(current, size);
        Page<SalesWarning> r = salesWarningMapper.selectPage(p, w);
        for (SalesWarning sw : r.getRecords()) {
            sw.setRemainSeconds(sw.getDeadline() == null ? null
                    : java.time.Duration.between(LocalDateTime.now(), sw.getDeadline()).getSeconds());
        }
        return PageResult.of(r.getRecords(), r.getTotal(), current, size);
    }

    /** 各状态 / 各等级计数（页面头部与驾驶舱用） */
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String st : List.of("待处理", "处理中", "已处理待确认", "已关闭")) {
            m.put(st, salesWarningMapper.selectCount(scoped(new QueryWrapper<SalesWarning>().eq("status", st))));
        }
        for (String lv : List.of("红", "橙", "黄")) {
            m.put(lv, salesWarningMapper.selectCount(scoped(new QueryWrapper<SalesWarning>()
                    .eq("warning_level", lv).ne("status", "已关闭"))));
        }
        m.put("未处理", salesWarningMapper.selectCount(scoped(new QueryWrapper<SalesWarning>()
                .ne("status", "已关闭"))));
        return m;
    }

    /**
     * 数据权限（2026-10-09）：与 /page 同口径，按负责人 owner_name 收敛。
     * 本表由定时任务生成，created_by 恒为 "system"，故归属列取 owner_name。
     */
    private QueryWrapper<SalesWarning> scoped(QueryWrapper<SalesWarning> w) {
        dataScopeResolver.applyIfLimited(w, null, "owner_name");
        return w;
    }

    /** 报价单生效 7 天后仍无跟进记录 */
    private int genQuoteIdle(LocalDate day) {
        int n = 0;
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT q.customer_id, c2.customer_name, q.created_by FROM sale_quote q " +
                    "LEFT JOIN customer c2 ON c2.customer_id = q.customer_id " +
                    "WHERE q.status = '已生效' AND q.quote_date <= ? " +
                    "AND NOT EXISTS (SELECT 1 FROM crm_follow_up f WHERE f.source_type = '客户' " +
                    "   AND f.source_id = q.customer_id AND f.follow_up_date > q.quote_date) LIMIT 200",
                    day.minusDays(7));
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：报价未跟进查询失败：{}", e.getMessage());
            throw new com.erp.config.BusinessException("生成预警失败：报价未跟进查询失败：" + e.getMessage());
        }
        for (Map<String, Object> r : rows) {
            String owner = r.get("created_by") == null ? null : r.get("created_by").toString();
            String cname = r.get("customer_name") == null ? "" : r.get("customer_name").toString();
            Long cid = r.get("customer_id") == null ? null : Long.valueOf(r.get("customer_id").toString());
            if (insertWarning("报价7天未跟进", "黄", "单据", cid, cname, owner,
                    "报价生效超 7 天无跟进", "7 天", day, day)) {
                n++;
            }
        }
        return n;
    }

    /** 回款逾期：应收余额 > 0 且已过到期日；按客户归属人分档（1~15 黄 / 16~30 橙 / >30 红） */
    private int genArOverdue(LocalDate day) {
        int n = 0;
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT a.customer_id, a.customer_name, c.created_by, a.due_date " +
                    "FROM account_receivable a LEFT JOIN customer c ON c.customer_id = a.customer_id " +
                    "WHERE COALESCE(a.balance_amount, 0) > 0 AND a.due_date IS NOT NULL AND a.due_date < ? " +
                    "ORDER BY a.due_date ASC LIMIT 200", day);
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：回款逾期查询失败：{}", e.getMessage());
            throw new com.erp.config.BusinessException("生成预警失败：回款逾期查询失败：" + e.getMessage());
        }
        for (Map<String, Object> r : rows) {
            Object due = r.get("due_date");
            if (due == null) continue;
            long overdue = day.toEpochDay() - LocalDate.parse(due.toString()).toEpochDay();
            if (overdue <= 0) continue;
            // 等级分档读设置中心（文档第八节：回款逾期 1~15 黄 / 16~30 橙 / >30 红）
            int arYellow = cfgInt("sales.process.level.ar_yellow_days", 15);
            int arOrange = cfgInt("sales.process.level.ar_orange_days", 30);
            String level = overdue > arOrange ? "红" : (overdue > arYellow ? "橙" : "黄");
            String owner = r.get("created_by") == null ? null : r.get("created_by").toString();
            String cname = r.get("customer_name") == null ? "" : r.get("customer_name").toString();
            Long cid = r.get("customer_id") == null ? null : Long.valueOf(r.get("customer_id").toString());
            if (insertWarning("回款逾期", level, "客户", cid, cname, owner,
                    "逾期 " + overdue + " 天", "超账期", day, day)) {
                n++;
            }
        }
        return n;
    }

    // ==================== 私有工具 ====================

    /**
     * 未分配客户预警（文档 6.4）：私海(pool_type=0)里既没有 assigned_sales_id、也没有 default_saler_id 的客户，
     * 逐个产生一条「未分配客户」（固定黄 / 销售主管 / 24h）。归属人为空，故 ownerUsername 传 null。
     */
    private int genUnassignedCustomer(LocalDate day) {
        int n = 0;
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT customer_id, customer_name FROM customer WHERE pool_type = 0 " +
                    "AND COALESCE(assigned_sales_id, default_saler_id) IS NULL LIMIT 200");
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：未分配客户查询失败：{}", e.getMessage());
            throw new com.erp.config.BusinessException("生成预警失败：未分配客户查询失败：" + e.getMessage());
        }
        for (Map<String, Object> r : rows) {
            Long cid = r.get("customer_id") == null ? null : Long.valueOf(r.get("customer_id").toString());
            String cname = r.get("customer_name") == null ? "未命名客户" : r.get("customer_name").toString();
            if (insertWarning("未分配客户", ruleOf("未分配客户")[1], "客户", cid, cname, null,
                    "未分配销售员", "须分配归属人", day, day)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 物流三态预警（文档第八节最后 3 行）——由定时任务每 15 分钟调用。
     *
     * <p>规则：遍历 free_sample_order 中「已发货/已完成」且填了快递单号、发货日期 ≤ 昨天 的单据（限 50 条），
     * 用快递100 轨迹判定：① 轨迹最后一条时间早于「当前时间-24h」；② 轨迹文案含「拒收/签收失败」；
     * ③ 发货超过默认时效（发货日 + 5 天）仍未签收。</p>
     *
     * <p><b>快递100 未配置授权码时整体跳过</b>（不报错、不产生误报警）；单条查询失败不影响其它单（失败≠未寄出）。</p>
     */
    public int checkLogistics() {
        LocalDate day = LocalDate.now();
        try {
            if (!kuaidi100Service.isConfigured()) {
                log.info("[销售预警] 快递100 未配置授权码，物流异常预警整体跳过");
                return 0;
            }
        } catch (Exception e) {
            log.warn("[销售预警] 快递100 配置读取失败，物流异常预警跳过：{}", e.getMessage());
            return 0;
        }
        return genLogisticsWarnings(day);
    }

    private int genLogisticsWarnings(LocalDate day) {
        int n = 0;
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT order_id, order_no, customer_name, created_by, express_no, express_company, delivery_date " +
                    "FROM free_sample_order WHERE status IN ('已发货','已完成') " +
                    "AND express_no IS NOT NULL AND express_no <> '' " +
                    "AND delivery_date IS NOT NULL AND delivery_date <= ? " +
                    "ORDER BY delivery_date ASC LIMIT 50", day.minusDays(1));
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：物流异常查询失败：{}", e.getMessage());
            throw new com.erp.config.BusinessException("生成预警失败：物流异常查询失败：" + e.getMessage());
        }
        java.time.LocalDateTime staleBefore = java.time.LocalDateTime.now().minusHours(24);
        for (Map<String, Object> r : rows) {
            String expressNo = r.get("express_no") == null ? null : r.get("express_no").toString().trim();
            if (expressNo == null || expressNo.isEmpty()) continue;
            String company = r.get("express_company") == null ? null : r.get("express_company").toString();
            Long oid = r.get("order_id") == null ? null : Long.valueOf(r.get("order_id").toString());
            String ono = r.get("order_no") == null ? null : r.get("order_no").toString();
            String owner = r.get("created_by") == null ? null : r.get("created_by").toString();

            com.erp.logistics.service.Kuaidi100Service.LogisticsTrackResult track;
            try {
                track = kuaidi100Service.query(expressNo, company);
            } catch (Exception e) {
                log.warn("[销售预警] 快递100查询失败（失败≠未寄出，跳过该单）：{} -> {}", expressNo, e.getMessage());
                continue;
            }
            if (track == null || !track.isSuccess()) continue;

            // ① 轨迹 24 小时无更新
            java.time.LocalDateTime latest = parseTrackTime(track.getLatestTime());
            if (latest != null && latest.isBefore(staleBefore)) {
                if (insertWarning("物流：轨迹24h无更新", ruleOf("物流：轨迹24h无更新")[1], "单据", oid, ono, owner,
                        "轨迹停滞超 24 小时", "24 小时", day, day)) {
                    n++;
                }
            }
            // ② 拒收 / 签收失败
            String text = (track.getLatestContext() == null ? "" : track.getLatestContext())
                    + " " + (track.getStatusText() == null ? "" : track.getStatusText());
            if (text.contains("拒收") || text.contains("签收失败")) {
                if (insertWarning("物流：拒收/签收失败", ruleOf("物流：拒收/签收失败")[1], "单据", oid, ono, owner,
                        text.trim(), "拒收/签收失败", day, day)) {
                    n++;
                }
            }
            // ③ 在途超期未签收（默认时效 = 发货日 + 5 天）
            Object dd = r.get("delivery_date");
            LocalDate deliveryDate = dd == null ? null : LocalDate.parse(dd.toString());
            boolean signed = "3".equals(track.getStatusCode())
                    || (track.getStatusText() != null && track.getStatusText().contains("已签收"));
            if (!signed && deliveryDate != null && deliveryDate.plusDays(5).isBefore(day)) {
                long overdue = day.toEpochDay() - deliveryDate.plusDays(5).toEpochDay();
                if (insertWarning("物流：在途超期未签收", ruleOf("物流：在途超期未签收")[1], "单据", oid, ono, owner,
                        "超期 " + overdue + " 天未签收", "约定时效 5 天", day, day)) {
                    n++;
                }
            }
        }
        if (n > 0) log.info("[销售预警] 物流异常预警新增 {} 条", n);
        return n;
    }

    /** 解析快递100 轨迹时间（ftime / time），解析不了返回 null */
    private java.time.LocalDateTime parseTrackTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"}) {
            try {
                return java.time.LocalDateTime.parse(s,
                        java.time.format.DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
                // 试下一个格式
            }
        }
        return null;
    }

    // ============ 第八节 预警规则表（逐条照立项登记表，禁止改口径）============
    /**
     * 每个预警类型的 等级模式 / 固定等级 / 推送对象 / 处理时限。
     * 数组含义：{levelMode, fixedLevel, pushTo, deadline}
     * levelMode：rate=按完成率分档；days=按逾期天数分档；fixed=固定等级
     * deadline：today=当日；nextMonday=次周一；数字=小时数
     */
    private static final Map<String, String[]> RULES = new LinkedHashMap<>();
    static {
        RULES.put("日回访未达标",     new String[]{"rate",  null, "销售本人",              "today"});
        RULES.put("日新增微信未达标", new String[]{"rate",  null, "销售本人",              "today"});
        RULES.put("日深入交流未达标", new String[]{"rate",  null, "销售本人",              "today"});
        RULES.put("周样品未寄出",     new String[]{"fixed", "黄", "销售 + 总监",           "nextMonday"});
        RULES.put("私海30天未联系",   new String[]{"fixed", "橙", "销售 + 总监",           "24"});
        RULES.put("私海45天未联系",   new String[]{"fixed", "红", "销售 + 总监 + 总经理",   "48"});
        RULES.put("朋友圈推广未达标", new String[]{"fixed", "黄", "销售本人",              "nextMonday"});
        RULES.put("RFID 覆盖未达标",  new String[]{"fixed", "橙", "销售 + 总监",           "72"});
        RULES.put("样品15天未转化",   new String[]{"fixed", "橙", "销售本人",              "24"});
        RULES.put("报价7天未跟进",    new String[]{"fixed", "黄", "销售本人",              "today"});
        RULES.put("新客户7天未联系",  new String[]{"fixed", "黄", "销售本人",              "today"});
        RULES.put("回款逾期",         new String[]{"days",  null, "销售 + 财务 + 总监",     "48"});
        // 文档 6.4：客户未分配销售员 → 进"未分配"桶，并产生一条「未分配客户」提醒（固定黄 / 销售主管 / 24h）
        RULES.put("未分配客户",       new String[]{"fixed", "黄", "销售主管",              "24"});
        // 文档第八节最后 3 行：物流异常（轨迹 24h 无更新 / 拒收签收失败 / 在途超期未签收）
        RULES.put("物流：轨迹24h无更新", new String[]{"fixed", "黄", "销售本人",            "today"});
        RULES.put("物流：拒收/签收失败", new String[]{"fixed", "橙", "销售 + 总监",         "24"});
        RULES.put("物流：在途超期未签收", new String[]{"fixed", "橙", "销售 + 总监",         "24"});
    }

    private String[] ruleOf(String type) {
        return RULES.getOrDefault(type, new String[]{"fixed", "黄", "销售本人", "24"});
    }

    /** 时限：today=当日 23:59；nextMonday=次周一 09:00；数字=多少小时 */
    private LocalDateTime deadlineOf(String deadline) {
        LocalDate today = LocalDate.now();
        if ("today".equals(deadline)) return today.atTime(23, 59, 59);
        if ("nextMonday".equals(deadline)) {
            return today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atTime(9, 0);
        }
        try {
            return LocalDateTime.now().plusHours(Long.parseLong(deadline));
        } catch (Exception e) {
            return LocalDateTime.now().plusHours(24);
        }
    }

    /** 升级后按新等级取推送对象；读设置中心（sales.process.push.*），无配置时用文档默认值 */
    private String pushToOfLevel(String level) {
        if ("红".equals(level)) return cfg("sales.process.push.red", "销售 + 总监 + 总经理");
        if ("橙".equals(level)) return cfg("sales.process.push.orange", "销售 + 总监");
        return cfg("sales.process.push.yellow", "销售本人");
    }

    /** 未达标则生成预警；等级按该类型的规则（固定 或 按完成率分档） */
    private int addLevel(String username, String type, int done, int target, String unit,
                         LocalDate ps, LocalDate pe) {
        if (target <= 0 || done >= target) return 0;
        String[] rule = ruleOf(type);
        String level = rule[1];
        if (level == null) {
            int rate = (int) Math.round(done * 100.0 / target);
            int yellow = cfgInt("sales.process.level.yellow_rate", 80);
            int orange = cfgInt("sales.process.level.orange_rate", 60);
            level = rate < orange ? "红" : (rate < yellow ? "橙" : "黄");
        }
        return insertWarning(type, level, "个人", null, username, username,
                done + "/" + target, target + " " + unit + "/期", ps, pe) ? 1 : 0;
    }

    /** 幂等插入（同 负责人+类型+统计区间 只保留一条），并推送消息中心 */
    private boolean insertWarning(String type, String level, String targetType, Long targetId,
                                  String targetName, String ownerUsername,
                                  String triggerValue, String thresholdValue,
                                  LocalDate ps, LocalDate pe) {
        QueryWrapper<SalesWarning> exist = new QueryWrapper<SalesWarning>()
                .eq("warning_type", type).eq("period_start", ps).eq("period_end", pe);
        if (ownerUsername == null) {
            exist.isNull("owner_name");
        } else {
            exist.eq("owner_name", ownerUsername);
        }
        if (targetId != null) exist.eq("target_id", targetId);
        if (salesWarningMapper.selectCount(exist) > 0) return false;

        Long ownerUserId = userIdOf(ownerUsername);
        SalesWarning w = new SalesWarning();
        w.setWarningNo("SW-" + ps.toString().replace("-", "") + "-" + String.format("%04d",
                System.currentTimeMillis() % 10000));
        w.setWarningType(type);
        w.setWarningLevel(level);
        w.setTargetType(targetType);
        w.setTargetId(targetId);
        w.setTargetName(targetName);
        w.setOwnerUserId(ownerUserId);
        w.setOwnerName(ownerUsername);
        w.setTriggerValue(triggerValue);
        w.setThresholdValue(thresholdValue);
        // 推送对象与处理时限照该类型的规则表（第八节），不再统一按等级取
        String[] r = ruleOf(type);
        w.setPushTo(r[2] != null ? r[2] : pushToOfLevel(level));
        w.setDeadline(deadlineOf(r[3]));
        w.setStatus("待处理");
        w.setPeriodStart(ps);
        w.setPeriodEnd(pe);
        salesWarningMapper.insert(w);
        push(ownerUserId, w, type, targetName);
        return true;
    }

    private void push(Long receiverId, SalesWarning w, String type, String targetName) {
        if (receiverId == null) return;
        try {
            SysNotification n = new SysNotification();
            n.setReceiverId(receiverId);
            n.setType("SALES_WARNING");
            n.setTitle("销售预警（" + w.getWarningLevel() + "）：" + type);
            n.setContent(type + (targetName == null ? "" : " · " + targetName)
                    + "，处理时限 " + hoursOf(w.getWarningLevel()) + " 小时，请及时处理。");
            n.setSourceType("sales_warning");
            n.setSourceId(w.getWarningId());
            n.setSourceNo(w.getWarningNo());
            n.setRoutePath("/crm/warning");
            n.setUrgency("红".equals(w.getWarningLevel()) ? "高" : "中");
            n.setIsRead((short) 0);
            n.setCreatedAt(LocalDateTime.now());
            // 通知属旁路写：REQUIRES_NEW 独立事务（对齐 handle() 内通知置已读的做法），失败只告警，
            // 不污染外层预警生成/升级事务
            TransactionTemplate noticeTx = new TransactionTemplate(transactionManager);
            noticeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            noticeTx.executeWithoutResult(status -> sysNotificationService.insertOne(n));
        } catch (Exception e) {
            log.warn("[销售预警] 推送消息中心失败（不影响预警生成）：{}", e.getMessage());
        }
    }

    private String pushToOf(String level) {
        if ("红".equals(level)) return "本人+市场总监+总经理";
        if ("橙".equals(level)) return "本人+市场总监";
        return "本人";
    }

    private int hoursOf(String level) {
        if ("红".equals(level)) return cfgInt("sales.process.deadline.red", 48);
        if ("橙".equals(level)) return cfgInt("sales.process.deadline.orange", 24);
        return 12;
    }

    private SalesWarning require(Long id) {
        SalesWarning w = salesWarningMapper.selectById(id);
        if (w == null) throw new com.erp.config.BusinessException("预警不存在");
        return w;
    }

    private Long userIdOf(String username) {
        if (username == null) return null;
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT user_id FROM sys_user WHERE username = ? LIMIT 1", Long.class, username);
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：查找处理人失败：{}", e.getMessage());
            throw new com.erp.config.BusinessException("生成预警失败：查找处理人失败：" + e.getMessage());
        }
    }

    private int count(String sql, Object... args) {
        try {
            Integer v = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：统计查询失败：{} -> {}", sql.substring(0, Math.min(60, sql.length())), e.getMessage());
            throw new com.erp.config.BusinessException("生成预警失败：统计查询失败：" + e.getMessage());
        }
    }

    private int cfgInt(String key, int def) {
        try {
            return sysConfigService.getIntValue(key, def);
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：读取预警配置失败：{}（配置项 {}）", e.getMessage(), key);
            throw new com.erp.config.BusinessException("生成预警失败：读取预警配置失败：" + e.getMessage());
        }
    }

    private String cfg(String key, String def) {
        try {
            return sysConfigService.getValue(key, def);
        } catch (Exception e) {
            log.warn("[销售预警] 生成预警失败：读取预警配置失败：{}（配置项 {}）", e.getMessage(), key);
            throw new com.erp.config.BusinessException("生成预警失败：读取预警配置失败：" + e.getMessage());
        }
    }
}
