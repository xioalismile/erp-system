package com.erp.databoard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.scope.DataScopeResolver;
import com.erp.production.entity.WorkOrder;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.mapper.QcRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营看板（2026-10-07）
 *
 * <p>总经理视角一屏八块：销售 / 利润 / 回款 / 生产 / 采购 / 库存 / 质量 / 人力。
 * 其中**销售、利润、回款、库存**四块直接复用既有「数据看板」的三个看板接口（前端并行取数，避免两套口径），
 * 本服务只补**生产 / 采购 / 质量 / 人力**四块。</p>
 *
 * <p>每块指标独立 try/catch：某张表字段对不上时该块返回 null（页面显示"暂无数据"），
 * 不影响其它块，也不会把页面打挂。</p>
 *
 * <p>口径边界：人力块只做「在职人数 / 部门分布 / 岗位分布」——系统无招聘与培训模块，
 * 招聘完成率、培训完成率**不展示**（不用 0 冒充）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationBoardService {

    private final JdbcTemplate jdbcTemplate;
    /** 数据权限解析器（2026-10-09）：生产/采购/质量三块按对应业务表的数据范围收敛 */
    private final DataScopeResolver dataScopeResolver;
    private final WorkOrderMapper workOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final QcRecordMapper qcRecordMapper;

    public Map<String, Object> board(String period) {
        LocalDate today = LocalDate.now();
        LocalDate from = resolveFrom(period, today);
        LocalDate to = today;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period == null ? "month" : period);
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("production", production(from, to, today));
        out.put("purchase", purchase(from, to));
        out.put("quality", quality(from, to));
        out.put("hr", hr());
        return out;
    }

    private LocalDate resolveFrom(String period, LocalDate today) {
        if ("day".equals(period)) return today;
        if ("week".equals(period)) return today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        if ("quarter".equals(period)) return LocalDate.of(today.getYear(), (today.getMonthValue() - 1) / 3 * 3 + 1, 1);
        if ("year".equals(period)) return LocalDate.of(today.getYear(), 1, 1);
        return today.with(TemporalAdjusters.firstDayOfMonth());
    }

    /**
     * 生产：在制工单 / 已完工 / 延期工单 / 本期新增。
     * 数据权限（2026-10-09）：与生产工单列表同口径 applyIfLimited(wrapper, null, "created_by")；
     * 全部数据角色不追加条件 → 与改造前逐字同口径。
     */
    private Map<String, Object> production(LocalDate from, LocalDate to, LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inProgress", countWorkOrders(new QueryWrapper<WorkOrder>()
                .notIn("status", List.of("已完工", "已作废"))));
        m.put("finished", countWorkOrders(new QueryWrapper<WorkOrder>().eq("status", "已完工")));
        m.put("delayed", countWorkOrders(new QueryWrapper<WorkOrder>()
                .notIn("status", List.of("已完工", "已作废"))
                .isNotNull("plan_end_date").lt("plan_end_date", today)));
        m.put("newInPeriod", countWorkOrders(new QueryWrapper<WorkOrder>()
                .apply("created_at::date BETWEEN {0} AND {1}", from, to)));
        return m;
    }

    /**
     * 采购：本期订单数 / 金额 / 待处理数。
     * 数据权限（2026-10-09）：与采购订单列表同口径 applyIfLimited(wrapper, "buyer_id", "created_by")。
     */
    private Map<String, Object> purchase(LocalDate from, LocalDate to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderCount", countPurchaseOrders(new QueryWrapper<PurchaseOrder>()
                .between("order_date", from, to)));
        m.put("orderAmount", sumPurchaseAmount(from, to));
        m.put("pendingCount", countPurchaseOrders(new QueryWrapper<PurchaseOrder>()
                .notIn("status", List.of("已完工", "已作废", "已完成"))));
        return m;
    }

    /**
     * 质量：本期检验批次 / 不合格批次 / 合格率。
     * 数据权限（2026-10-09）：与工序检验报表同口径 applyIfLimited(wrapper, null, "created_by")。
     */
    private Map<String, Object> quality(LocalDate from, LocalDate to) {
        Map<String, Object> m = new LinkedHashMap<>();
        Object total = countQcRecords(from, to, false);
        Object bad = countQcRecords(from, to, true);
        m.put("inspectCount", total);
        m.put("defectCount", bad);
        m.put("passRate", rate(total, bad));
        return m;
    }

    private Object countWorkOrders(QueryWrapper<WorkOrder> w) {
        try {
            dataScopeResolver.applyIfLimited(w, null, "created_by");
            return workOrderMapper.selectCount(w);
        } catch (Exception e) {
            log.warn("[经营看板] 生产工单取数失败（返回空）：{}", e.getMessage());
            return null;
        }
    }

    private Object countPurchaseOrders(QueryWrapper<PurchaseOrder> w) {
        try {
            dataScopeResolver.applyIfLimited(w, "buyer_id", "created_by");
            return purchaseOrderMapper.selectCount(w);
        } catch (Exception e) {
            log.warn("[经营看板] 采购订单取数失败（返回空）：{}", e.getMessage());
            return null;
        }
    }

    private Object sumPurchaseAmount(LocalDate from, LocalDate to) {
        try {
            QueryWrapper<PurchaseOrder> w = new QueryWrapper<PurchaseOrder>()
                    .select("COALESCE(SUM(total_amount),0) AS amt")
                    .between("order_date", from, to)
                    .ne("status", "已作废");
            dataScopeResolver.applyIfLimited(w, "buyer_id", "created_by");
            List<Object> vals = purchaseOrderMapper.selectObjs(w);
            return (vals == null || vals.isEmpty()) ? null : vals.get(0);
        } catch (Exception e) {
            log.warn("[经营看板] 采购金额取数失败（返回空）：{}", e.getMessage());
            return null;
        }
    }

    private Object countQcRecords(LocalDate from, LocalDate to, boolean defectOnly) {
        try {
            QueryWrapper<QcRecord> w = new QueryWrapper<QcRecord>()
                    .apply("created_at::date BETWEEN {0} AND {1}", from, to);
            if (defectOnly) w.eq("result", "不合格");
            dataScopeResolver.applyIfLimited(w, null, "created_by");
            return qcRecordMapper.selectCount(w);
        } catch (Exception e) {
            log.warn("[经营看板] 质量取数失败（返回空）：{}", e.getMessage());
            return null;
        }
    }

    /** 人力：在职人数 / 部门分布 / 岗位分布（无招聘、培训数据，不展示） */
    private Map<String, Object> hr() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("headcount", scalar("SELECT COUNT(*) FROM employee"));
        m.put("deptCount", scalar("SELECT COUNT(*) FROM (SELECT DISTINCT dept_id FROM employee WHERE dept_id IS NOT NULL) t"));
        try {
            m.put("deptDist", jdbcTemplate.queryForList(
                    "SELECT d.dept_name AS name, COUNT(e.employee_id) AS value FROM employee e " +
                    "LEFT JOIN department d ON d.dept_id = e.dept_id GROUP BY d.dept_name ORDER BY value DESC LIMIT 8"));
        } catch (Exception e) {
            m.put("deptDist", null);
        }
        return m;
    }

    // ==================== 工具 ====================

    private Object scalar(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Number.class, args);
        } catch (Exception e) {
            log.warn("[经营看板] 取数失败（返回空）：{} -> {}", sql.substring(0, Math.min(60, sql.length())), e.getMessage());
            return null;
        }
    }

    private Object rate(Object total, Object bad) {
        try {
            long t = ((Number) total).longValue();
            long b = bad == null ? 0 : ((Number) bad).longValue();
            if (t <= 0) return null;
            return Math.round((t - b) * 100.0 / t);
        } catch (Exception e) {
            return null;
        }
    }
}
