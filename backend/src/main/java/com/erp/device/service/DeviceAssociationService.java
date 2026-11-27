package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DevicePoint;
import com.erp.device.entity.DeviceTask;
import com.erp.device.mapper.DeviceTaskMapper;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import com.erp.production.service.WorkOrderOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自动关联引擎（阶段 4）：
 * 设备数据到达时，按「显式工单工序 → 空间 → 状态 → 数量 → 时间 → 排序」找出该设备正在做的工序。
 * 找不到不猜写：有产量时进未关联数据池，等人工认领。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAssociationService {

    private static final List<String> ACTIVE_OPERATION_STATUS = List.of(
            "待派工", "已派工", "已开工", "进行中", "暂停");

    private final DeviceTaskMapper taskMapper;
    private final DeviceTaskService taskService;
    private final DeviceUnmatchedService unmatchedService;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderOperationMapper operationMapper;
    private final WorkOrderOperationService workOrderOperationService;

    /** 每次设备数据入库后调用；已有执行中关联时直接复用，不重复匹配 */
    public void onData(DeviceInfo device, Map<String, Object> values,
                       Map<String, DevicePoint> points, Long rawMessageId) {
        try {
            Activity a = extractActivity(values, points);
            DeviceTask running = taskMapper.findRunning(device.getId());
            if (running != null) {
                if (a.active()) autoStart(device, running.getOperationRowId(), a);
                return;
            }
            if (!a.active()) return;

            WorkOrderOperation target = null;
            String source = "AUTO";
            String reason = null;

            if (a.orderNo != null && a.operationNo != null) {
                source = "MSG";
                target = findByExplicit(device, a.orderNo, a.operationNo);
                if (target == null) reason = "报文工单/工序校验不通过或不在设备可执行范围";
            }

            if (target == null && reason == null) {
                List<WorkOrderOperation> candidates = findCandidates(device);
                target = candidates.isEmpty() ? null : candidates.get(0);
                if (target == null) {
                    reason = isBlank(device.getAllowedOperationIds())
                            ? "设备未配置可执行工序"
                            : "当前没有可开工的工单工序";
                }
            }

            if (target == null) {
                unmatchedService.record(device, a.qualified, a.rejected, rawMessageId, reason);
                log.info("[设备关联] 设备 {} 未匹配到工序：{}", device.getDeviceCode(), reason);
                return;
            }

            DeviceTask task = taskService.createRunning(device, target, source, a.qualified);
            autoStart(device, task.getOperationRowId(), a);
            log.info("[设备关联] 设备 {} 关联到工单 {} 工序 {}（来源 {}，任务 {}）",
                    device.getDeviceCode(), target.getOrderNo(), target.getOperationName(),
                    source, task.getTaskNo());
        } catch (Exception e) {
            log.warn("[设备关联] 设备 {} 自动关联失败: {}", device.getDeviceCode(), e.getMessage());
        }
    }

    // ==================== 显式关联 ====================

    private WorkOrderOperation findByExplicit(DeviceInfo device, String orderNo, String operationNo) {
        List<WorkOrder> orders = workOrderMapper.selectList(new QueryWrapper<WorkOrder>()
                .eq("order_no", orderNo.trim()).orderByDesc("order_id").last("limit 1"));
        if (orders.isEmpty()) return null;
        WorkOrder order = orders.get(0);
        if (!"已生效".equals(order.getStatus()) || (order.getIsClosed() != null && order.getIsClosed() == 1)) {
            return null;
        }
        QueryWrapper<WorkOrderOperation> qw = new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", order.getOrderId());
        Integer lineNo = null;
        try {
            lineNo = Integer.parseInt(operationNo.trim());
        } catch (NumberFormatException ignore) {
            // 非数字按工序编码匹配
        }
        if (lineNo != null) {
            qw.eq("line_no", lineNo);
        } else {
            qw.eq("operation_code", operationNo.trim());
        }
        List<WorkOrderOperation> ops = operationMapper.selectList(qw.last("limit 1"));
        if (ops.isEmpty()) return null;
        WorkOrderOperation op = ops.get(0);
        if (!ACTIVE_OPERATION_STATUS.contains(op.getOperationStatus())) return null;
        if (!allowed(device, op)) return null;
        return op;
    }

    // ==================== 空间 / 状态 / 数量 / 时间 / 排序 ====================

    private List<WorkOrderOperation> findCandidates(DeviceInfo device) {
        List<Long> allowedIds = parseIds(device.getAllowedOperationIds());
        // 口径：设备未配置可执行工序 = 不参与自动关联，只能人工认领
        if (allowedIds.isEmpty()) return List.of();

        QueryWrapper<WorkOrderOperation> qw = new QueryWrapper<>();
        qw.in("operation_status", ACTIVE_OPERATION_STATUS);
        if (device.getWorkstationId() != null) {
            qw.and(w -> w.eq("workstation_id", device.getWorkstationId())
                    .or().eq("work_center_id", device.getWorkCenterId()));
        } else if (device.getWorkCenterId() != null) {
            qw.eq("work_center_id", device.getWorkCenterId());
        } else {
            return List.of();
        }
        qw.in("workstation_id", allowedIds);
        qw.and(w -> w.isNull("can_start_qty").or().gt("can_start_qty", BigDecimal.ZERO));
        qw.last("limit 100");
        List<WorkOrderOperation> ops = operationMapper.selectList(qw);
        if (ops.isEmpty()) return List.of();

        Set<Long> orderIds = ops.stream().map(WorkOrderOperation::getOrderId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WorkOrder> orders = orderIds.isEmpty() ? Map.of()
                : workOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(WorkOrder::getOrderId, o -> o, (a, b) -> a));

        LocalDate today = LocalDate.now();
        List<WorkOrderOperation> result = new ArrayList<>();
        for (WorkOrderOperation op : ops) {
            WorkOrder order = orders.get(op.getOrderId());
            if (order == null) continue;
            if (!"已生效".equals(order.getStatus())) continue;
            if (order.getIsClosed() != null && order.getIsClosed() == 1) continue;
            // 时间匹配：计划结束日期已过的仍可关联（超期工单优先处理），只排除尚未开始的未来工序
            if (op.getPlanStartDate() != null && op.getPlanStartDate().isAfter(today.plusDays(7))) continue;
            if (!allowed(device, op)) continue;
            result.add(op);
        }
        result.sort(comparator(device, orders));
        return result;
    }

    private Comparator<WorkOrderOperation> comparator(DeviceInfo device, Map<Long, WorkOrder> orders) {
        return Comparator
                .comparingInt((WorkOrderOperation op) -> same(op.getAssigneeId(), device.getOwnerEmployeeId()) ? 0 : 1)
                .thenComparingInt(op -> same(op.getWorkstationId(), device.getWorkstationId()) ? 0 : 1)
                .thenComparing(op -> op.getPlanStartDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(op -> op.getLineNo(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(op -> {
                    WorkOrder o = orders.get(op.getOrderId());
                    return o != null && o.getIsUrgent() != null && o.getIsUrgent() == 1 ? 0 : 1;
                })
                .thenComparing(op -> {
                    WorkOrder o = orders.get(op.getOrderId());
                    return o == null ? null : o.getCreatedAt();
                }, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private boolean allowed(DeviceInfo device, WorkOrderOperation op) {
        List<Long> ids = parseIds(device.getAllowedOperationIds());
        // 设备可执行工序存的是「工序/工位主档」ID，对应工单工序的 workstation_id
        return ids.isEmpty() || ids.contains(op.getWorkstationId());
    }

    private boolean same(Object a, Object b) {
        return a != null && b != null && a.toString().equals(b.toString());
    }

    private List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (isBlank(csv)) return ids;
        for (String part : csv.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException ignore) {
                // 忽略非法项
            }
        }
        return ids;
    }

    /** 自动开工：设备开启「自动开工」且运行信号成立时，把已派工工序推进为已开工 */
    private void autoStart(DeviceInfo device, Long operationRowId, Activity a) {
        if (device.getAutoStart() == null || device.getAutoStart() != 1) return;
        if (a.startSignal.compareTo(BigDecimal.ZERO) <= 0) return;
        WorkOrderOperation op = operationMapper.selectById(operationRowId);
        if (op == null || !"已派工".equals(op.getOperationStatus())) return;
        try {
            workOrderOperationService.driveByBill(operationRowId, "已开工", "设备自动开工",
                    "设备 " + device.getDeviceCode());
            log.info("[设备关联] 设备 {} 自动开工，工序 {}", device.getDeviceCode(), op.getOperationName());
        } catch (Exception e) {
            log.warn("[设备关联] 设备 {} 自动开工失败: {}", device.getDeviceCode(), e.getMessage());
        }
    }

    private Activity extractActivity(Map<String, Object> values, Map<String, DevicePoint> points) {
        Activity a = new Activity();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            DevicePoint p = points.get(e.getKey());
            if (p == null || p.getPurpose() == null) continue;
            switch (p.getPurpose()) {
                case "ORDER_NO" -> a.orderNo = asText(e.getValue());
                case "OPERATION_NO" -> a.operationNo = asText(e.getValue());
                case "QUALIFIED_COUNT" -> a.qualified = asNumber(e.getValue());
                case "REJECTED_COUNT" -> a.rejected = asNumber(e.getValue());
                case "START_SIGNAL" -> a.startSignal = asNumber(e.getValue());
                default -> {
                }
            }
        }
        return a;
    }

    private String asText(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private BigDecimal asNumber(Object v) {
        if (v == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static class Activity {
        String orderNo;
        String operationNo;
        BigDecimal qualified = BigDecimal.ZERO;
        BigDecimal rejected = BigDecimal.ZERO;
        BigDecimal startSignal = BigDecimal.ZERO;

        boolean active() {
            return qualified.compareTo(BigDecimal.ZERO) > 0
                    || rejected.compareTo(BigDecimal.ZERO) > 0
                    || startSignal.compareTo(BigDecimal.ZERO) > 0
                    || (orderNo != null && operationNo != null);
        }
    }
}
