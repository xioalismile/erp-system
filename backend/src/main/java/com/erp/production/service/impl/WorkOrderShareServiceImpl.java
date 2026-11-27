package com.erp.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.scope.DataScopeResolver;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.entity.WorkOrderShare;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderShareMapper;
import com.erp.production.service.WorkOrderOperationService;
import com.erp.production.service.WorkOrderShareService;
import com.erp.production.service.WorkOperationStatus;
import com.erp.tenant.TenantContextHolder;
import com.erp.tenant.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产工单进度对外分享实现（2026-11-25）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderShareServiceImpl implements WorkOrderShareService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STATUS_RELEASED = "已生效";
    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_DAYS = 7;

    private final WorkOrderShareMapper shareMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderOperationService operationService;
    private final DataScopeResolver dataScopeResolver;
    private final TenantContextResolver tenantContextResolver;

    @Override
    public WorkOrderShare create(Long orderId, String title, String remark, Integer days,
                                 Boolean showAssignee, Boolean showPlanDate, Boolean showMaterialCode,
                                 Long userId, String userName) {
        WorkOrder order = requireShareableOrder(orderId);

        String finalTitle = title == null ? "" : title.trim();
        if (finalTitle.isEmpty()) {
            finalTitle = (order.getOrderNo() == null ? "生产工单" : order.getOrderNo()) + " 生产进度";
        }
        if (finalTitle.length() > 120) finalTitle = finalTitle.substring(0, 120);
        String finalRemark = remark == null ? "" : remark.trim();
        if (finalRemark.length() > 500) finalRemark = finalRemark.substring(0, 500);

        int validDays = days == null ? DEFAULT_DAYS : Math.max(1, Math.min(MAX_DAYS, days));

        Long tenantId = TenantContextHolder.getTenantId();
        long tid = tenantId == null ? 1L : tenantId;
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        String token = tid + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        LocalDateTime now = LocalDateTime.now();
        WorkOrderShare share = new WorkOrderShare();
        share.setToken(token);
        share.setOrderId(order.getOrderId());
        share.setOrderNo(order.getOrderNo());
        share.setTitle(finalTitle);
        share.setRemark(finalRemark);
        share.setExpireAt(now.plusDays(validDays));
        share.setStatus(1);
        share.setShowAssignee(flag(showAssignee));
        share.setShowPlanDate(flag(showPlanDate));
        share.setShowMaterialCode(flag(showMaterialCode));
        share.setViewCount(0);
        share.setCreatedBy(userId);
        share.setCreatedByName(userName);
        share.setCreatedAt(now);
        share.setUpdatedAt(now);
        shareMapper.insert(share);
        return share;
    }

    @Override
    public List<WorkOrderShare> listByOrder(Long orderId) {
        requireVisibleOrder(orderId);
        QueryWrapper<WorkOrderShare> qw = new QueryWrapper<>();
        qw.eq("order_id", orderId).orderByDesc("created_at").last("limit 50");
        return shareMapper.selectList(qw);
    }

    @Override
    public void revoke(Long shareId) {
        if (shareId == null) throw new IllegalArgumentException("分享记录不存在");
        WorkOrderShare share = shareMapper.selectById(shareId);
        if (share == null) throw new IllegalArgumentException("分享记录不存在");
        // 停止分享只要求"能看到这张工单"：工单后续作废/关闭时仍必须允许停止，避免链接一直有效
        requireVisibleOrder(share.getOrderId());
        share.setStatus(0);
        share.setUpdatedAt(LocalDateTime.now());
        shareMapper.updateById(share);
    }

    @Override
    public Map<String, Object> publicView(String token, HttpServletRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (token == null || token.isBlank()) return state(out, "NOT_FOUND", "分享链接无效");

        int dot = token.indexOf('.');
        if (dot <= 0) return state(out, "NOT_FOUND", "分享链接无效");
        long tenantId;
        try {
            tenantId = Long.parseLong(token.substring(0, dot));
        } catch (Exception e) {
            return state(out, "NOT_FOUND", "分享链接无效");
        }

        try {
            // 公开链接没有登录令牌：按令牌里携带的租户定位到对应公司库（与登录态同源切换数据源）
            tenantContextResolver.openTenantContext(tenantId, request);

            WorkOrderShare share = shareMapper.selectOne(
                    new QueryWrapper<WorkOrderShare>().eq("token", token).last("limit 1"));
            if (share == null) return state(out, "NOT_FOUND", "分享链接不存在或已被删除");
            if (share.getStatus() == null || share.getStatus() != 1) {
                return state(out, "STOPPED", "分享已停止，请联系分享人重新生成");
            }
            if (share.getExpireAt() == null || share.getExpireAt().isBefore(LocalDateTime.now())) {
                return state(out, "EXPIRED", "分享链接已过期，请联系分享人重新生成");
            }

            WorkOrder order = workOrderMapper.selectById(share.getOrderId());
            if (order == null) return state(out, "NOT_FOUND", "工单不存在或已被删除");

            List<WorkOrderOperation> ops = operationService.listByOrder(order.getOrderId());
            out.put("state", "OK");
            out.put("share", shareMap(share));
            out.put("order", orderMap(order, ops, share));
            out.put("lines", lineMaps(order, ops, share));
            out.put("updatedAt", LocalDateTime.now().format(DTF));

            // 访问计数：失败不影响页面查看
            try {
                share.setViewCount((share.getViewCount() == null ? 0 : share.getViewCount()) + 1);
                share.setLastViewAt(LocalDateTime.now());
                share.setUpdatedAt(LocalDateTime.now());
                shareMapper.updateById(share);
            } catch (Exception ignore) {
                // 计数不是关键路径
            }
            return out;
        } catch (Exception e) {
            log.warn("[工单进度分享] 公开读取失败 token={}: {}", token, e.getMessage());
            return state(out, "ERROR", "进度暂时无法查看，请稍后重试");
        }
    }

    // -------------------- 校验 --------------------

    /** 工单必须存在且在当前离线人的数据权限内（分享记录查询/停止用，不限制工单状态） */
    private WorkOrder requireVisibleOrder(Long orderId) {
        if (orderId == null) throw new IllegalArgumentException("工单不存在");
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("工单不存在");
        QueryWrapper<WorkOrder> qw = new QueryWrapper<>();
        qw.eq("order_id", orderId);
        dataScopeResolver.applyIfLimited(qw, null, "created_by");
        long visible = workOrderMapper.selectCount(qw);
        if (visible <= 0) throw new IllegalArgumentException("无权查看该工单");
        return order;
    }

    /** 工单必须存在、已生效，且在当前离线人的数据权限内（生成分享用，防越权分享） */
    private WorkOrder requireShareableOrder(Long orderId) {
        WorkOrder order = requireVisibleOrder(orderId);
        if (!STATUS_RELEASED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已生效工单可分享");
        }
        return order;
    }

    // -------------------- 公开数据组装（只输出生产进度字段） --------------------

    private Map<String, Object> state(Map<String, Object> out, String state, String message) {
        out.clear();
        out.put("state", state);
        out.put("message", message);
        return out;
    }

    private Map<String, Object> shareMap(WorkOrderShare s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", s.getTitle());
        m.put("remark", s.getRemark());
        m.put("expireAt", s.getExpireAt() == null ? null : s.getExpireAt().format(DTF));
        m.put("showAssignee", isOn(s.getShowAssignee()));
        m.put("showPlanDate", isOn(s.getShowPlanDate()));
        m.put("showMaterialCode", isOn(s.getShowMaterialCode()));
        m.put("viewCount", s.getViewCount() == null ? 0 : s.getViewCount());
        return m;
    }

    private Map<String, Object> orderMap(WorkOrder order, List<WorkOrderOperation> ops, WorkOrderShare share) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", order.getOrderNo());
        m.put("materialName", firstText(order.getMaterialName(), firstOpText(ops, true)));
        if (isOn(share.getShowMaterialCode())) {
            m.put("materialCode", firstText(order.getMaterialCode(), firstOpText(ops, false)));
        }
        m.put("specification", firstText(order.getModel(), firstSpec(ops)));
        m.put("quantity", order.getQuantity());
        m.put("completedQuantity", order.getCompletedQuantity());
        m.put("reportedQuantity", order.getReportedQuantity());
        m.put("status", order.getStatus());
        m.put("actualStartDate", dateStr(order.getActualStartDate()));
        m.put("actualEndDate", dateStr(order.getActualEndDate()));
        m.put("progress", overallProgress(ops));
        if (isOn(share.getShowPlanDate())) {
            m.put("planStartDate", dateStr(order.getPlanStartDate()));
            m.put("planEndDate", dateStr(order.getPlanEndDate()));
        }
        return m;
    }

    /** 按成品行拆线（一张工单多个商品时各自一条线）；无成品行的历史数据归为一条线 */
    private List<Map<String, Object>> lineMaps(WorkOrder order, List<WorkOrderOperation> ops, WorkOrderShare share) {
        Map<String, List<WorkOrderOperation>> groups = new LinkedHashMap<>();
        if (ops != null) {
            for (WorkOrderOperation op : ops) {
                String key = op.getOrderProductId() == null ? "0" : String.valueOf(op.getOrderProductId());
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(op);
            }
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map.Entry<String, List<WorkOrderOperation>> e : groups.entrySet()) {
            List<WorkOrderOperation> lineOps = e.getValue();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productName", firstText(firstOpText(lineOps, true), order.getMaterialName()));
            line.put("specification", firstText(firstSpec(lineOps), order.getModel()));
            line.put("planQuantity", sumLeaf(lineOps, true));
            line.put("reportedQuantity", sumLeaf(lineOps, false));
            line.put("progress", overallProgress(lineOps));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (WorkOrderOperation op : lineOps) {
                rows.add(opMap(op, share));
            }
            line.put("ops", rows);
            lines.add(line);
        }
        return lines;
    }

    private Map<String, Object> opMap(WorkOrderOperation op, WorkOrderShare share) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operationRowId", op.getId());
        m.put("parentRowId", op.getParentRowId());
        m.put("operationName", op.getOperationName());
        m.put("operationStatus", op.getOperationStatus());
        m.put("percent", opPercent(op));
        m.put("planQuantity", op.getPlanQuantity());
        m.put("reportedQuantity", op.getReportedQuantity());
        m.put("actualStartDate", dateStr(op.getActualStartDate()));
        m.put("actualEndDate", dateStr(op.getActualEndDate()));
        m.put("overdueDays", op.getOverdueDays() == null ? 0 : op.getOverdueDays());
        m.put("isCritical", op.getIsCritical() != null && op.getIsCritical() == 1);
        m.put("childCount", op.getChildCount() == null ? 0 : op.getChildCount());
        if (isOn(share.getShowAssignee())) {
            m.put("assigneeName", op.getAssigneeName());
        }
        if (isOn(share.getShowPlanDate())) {
            m.put("planStartDate", dateStr(op.getPlanStartDate()));
            m.put("planEndDate", dateStr(op.getPlanEndDate()));
        }
        return m;
    }

    /** 该道工序完成百分比（与电脑端看板同一口径） */
    private int opPercent(WorkOrderOperation op) {
        if (op == null) return 0;
        if (op.getChildCount() != null && op.getChildCount() > 0) {
            return pct(nvl(op.getChildProgress()));
        }
        if (WorkOperationStatus.TYPE_RECEIPT.equals(op.getOperationType())) {
            BigDecimal out = nvl(op.getOutputQuantity());
            return out.compareTo(BigDecimal.ZERO) > 0
                    ? pct(nvl(op.getReceivedQty()).multiply(BigDecimal.valueOf(100)).divide(out, 2, RoundingMode.HALF_UP))
                    : 0;
        }
        if (WorkOperationStatus.TYPE_AMOUNT.equals(op.getOperationType())) {
            BigDecimal plan = nvl(op.getPlanAmount());
            return plan.compareTo(BigDecimal.ZERO) > 0
                    ? pct(nvl(op.getConfirmedAmount()).multiply(BigDecimal.valueOf(100)).divide(plan, 2, RoundingMode.HALF_UP))
                    : 0;
        }
        BigDecimal plan = nvl(op.getPlanQuantity());
        if (plan.compareTo(BigDecimal.ZERO) > 0) {
            return pct(nvl(op.getReportedQuantity()).multiply(BigDecimal.valueOf(100)).divide(plan, 2, RoundingMode.HALF_UP));
        }
        return WorkOperationStatus.FINISHED.equals(op.getOperationStatus()) ? 100 : 0;
    }

    /** 整条线进度：叶子工序按计划数量加权（Σ已报 ÷ Σ计划）；全完工按 100 */
    private int overallProgress(List<WorkOrderOperation> ops) {
        if (ops == null || ops.isEmpty()) return 0;
        Set<Long> parentIds = new HashSet<>();
        for (WorkOrderOperation op : ops) {
            if (op.getParentRowId() != null) parentIds.add(op.getParentRowId());
        }
        BigDecimal planSum = BigDecimal.ZERO;
        BigDecimal reportedSum = BigDecimal.ZERO;
        int leaves = 0;
        int finishedLeaves = 0;
        for (WorkOrderOperation op : ops) {
            if (op.getId() != null && parentIds.contains(op.getId())) continue;
            leaves++;
            planSum = planSum.add(nvl(op.getPlanQuantity()));
            reportedSum = reportedSum.add(nvl(op.getReportedQuantity()));
            if (WorkOperationStatus.FINISHED.equals(op.getOperationStatus())) finishedLeaves++;
        }
        if (leaves > 0 && leaves == finishedLeaves) return 100;
        if (planSum.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return pct(reportedSum.multiply(BigDecimal.valueOf(100)).divide(planSum, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal sumLeaf(List<WorkOrderOperation> ops, boolean plan) {
        if (ops == null || ops.isEmpty()) return BigDecimal.ZERO;
        Set<Long> parentIds = new HashSet<>();
        for (WorkOrderOperation op : ops) {
            if (op.getParentRowId() != null) parentIds.add(op.getParentRowId());
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (WorkOrderOperation op : ops) {
            if (op.getId() != null && parentIds.contains(op.getId())) continue;
            sum = sum.add(plan ? nvl(op.getPlanQuantity()) : nvl(op.getReportedQuantity()));
        }
        return sum;
    }

    private String firstOpText(List<WorkOrderOperation> ops, boolean name) {
        if (ops == null) return null;
        for (WorkOrderOperation op : ops) {
            String v = name ? op.getMaterialName() : op.getMaterialCode();
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String firstSpec(List<WorkOrderOperation> ops) {
        if (ops == null) return null;
        for (WorkOrderOperation op : ops) {
            if (op.getSpecification() != null && !op.getSpecification().isBlank()) return op.getSpecification();
        }
        return null;
    }

    private static String firstText(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static int pct(BigDecimal v) {
        if (v == null) return 0;
        BigDecimal clamped = v.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        return clamped.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Integer flag(Boolean v) {
        return Boolean.FALSE.equals(v) ? 0 : 1;
    }

    private static boolean isOn(Integer v) {
        return v == null || v == 1;
    }

    private static String dateStr(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
