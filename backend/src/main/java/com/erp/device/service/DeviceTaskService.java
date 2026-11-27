package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DeviceTask;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.device.mapper.DeviceTaskMapper;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 设备任务：设备与工单工序的关联维护 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTaskService extends ServiceImpl<DeviceTaskMapper, DeviceTask> {

    private static final List<String> ACTIVE_OPERATION_STATUS = List.of(
            "待派工", "已派工", "已开工", "进行中", "暂停");

    private final DeviceInfoMapper deviceInfoMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderOperationMapper operationMapper;

    public PageResult<DeviceTask> pageQuery(int current, int size, Long deviceId, String status,
                                            String orderNo, String sourceType) {
        QueryWrapper<DeviceTask> qw = new QueryWrapper<>();
        if (deviceId != null) qw.eq("device_id", deviceId);
        if (status != null && !status.isBlank()) qw.eq("task_status", status);
        if (orderNo != null && !orderNo.isBlank()) qw.like("order_no", orderNo.trim());
        if (sourceType != null && !sourceType.isBlank()) qw.eq("source_type", sourceType);
        qw.orderByDesc("id");
        Page<DeviceTask> page = page(new Page<>(current, size), qw);
        enrich(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    public DeviceTask getDetail(Long id) {
        DeviceTask task = getById(id);
        if (task == null) throw new BusinessException("设备任务不存在");
        List<DeviceTask> one = new ArrayList<>();
        one.add(task);
        enrich(one);
        return task;
    }

    /** 人工建立关联：待下发，等车间确认后再下发 */
    public DeviceTask createManual(Long deviceId, Long operationRowId) {
        DeviceInfo device = deviceInfoMapper.selectById(deviceId);
        if (device == null) throw new BusinessException("设备不存在");
        WorkOrderOperation op = requireOperation(operationRowId);
        if (baseMapper.findRunning(deviceId) != null) {
            throw new BusinessException("该设备已有执行中的任务，请先解除关联");
        }
        return createTask(device, op, "MANUAL", "待下发", BigDecimal.ZERO);
    }

    /** 自动关联/人工认领：数据已在流，直接进入执行中 */
    public DeviceTask createRunning(DeviceInfo device, WorkOrderOperation op, String source, BigDecimal countBase) {
        DeviceTask running = baseMapper.findRunning(device.getId());
        if (running != null) return running;
        return createTask(device, op, source, "执行中", countBase == null ? BigDecimal.ZERO : countBase);
    }

    public void dispatch(Long id) {
        DeviceTask task = getById(id);
        if (task == null) throw new BusinessException("设备任务不存在");
        if (!"待下发".equals(task.getTaskStatus()) && !"下发失败".equals(task.getTaskStatus())) {
            throw new BusinessException("只有待下发或下发失败的任务可以下发");
        }
        task.setTaskStatus("已下发");
        task.setStartTime(LocalDateTime.now());
        updateById(task);
    }

    public void rebind(Long id, Long operationRowId) {
        DeviceTask task = getById(id);
        if (task == null) throw new BusinessException("设备任务不存在");
        if ("已完成".equals(task.getTaskStatus()) || "已取消".equals(task.getTaskStatus())) {
            throw new BusinessException("任务已结束，不能改绑");
        }
        WorkOrderOperation op = requireOperation(operationRowId);
        task.setOrderId(op.getOrderId());
        task.setOrderNo(op.getOrderNo());
        task.setOperationRowId(op.getId());
        task.setOperationName(op.getOperationName());
        updateById(task);
    }

    public void release(Long id, String reason) {
        DeviceTask task = getById(id);
        if (task == null) throw new BusinessException("设备任务不存在");
        if ("已完成".equals(task.getTaskStatus()) || "已取消".equals(task.getTaskStatus())) {
            throw new BusinessException("任务已结束");
        }
        task.setTaskStatus("已取消");
        task.setReleaseReason(reason == null || reason.isBlank() ? "人工" : reason);
        task.setEndTime(LocalDateTime.now());
        updateById(task);
    }

    /** 下拉：可关联的工单工序（已生效工单 + 未终态工序） */
    public List<Map<String, Object>> operationOptions(String keyword) {
        QueryWrapper<WorkOrderOperation> qw = new QueryWrapper<>();
        qw.in("operation_status", ACTIVE_OPERATION_STATUS);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("order_no", kw).or().like("operation_name", kw));
        }
        qw.orderByAsc("plan_start_date").orderByAsc("line_no").last("limit 300");
        List<WorkOrderOperation> ops = operationMapper.selectList(qw);
        if (ops.isEmpty()) return List.of();

        Set<Long> orderIds = ops.stream().map(WorkOrderOperation::getOrderId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WorkOrder> orders = orderIds.isEmpty() ? Map.of()
                : workOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(WorkOrder::getOrderId, o -> o, (a, b) -> a));

        List<Map<String, Object>> options = new ArrayList<>();
        for (WorkOrderOperation op : ops) {
            WorkOrder order = orders.get(op.getOrderId());
            if (!isApproved(order)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operationRowId", op.getId());
            row.put("operationId", op.getOperationId());
            row.put("orderId", op.getOrderId());
            row.put("orderNo", op.getOrderNo());
            row.put("lineNo", op.getLineNo());
            row.put("operationName", op.getOperationName());
            row.put("materialName", order == null ? null : order.getMaterialName());
            row.put("workCenterId", op.getWorkCenterId());
            row.put("workstationId", op.getWorkstationId());
            row.put("canStartQty", op.getCanStartQty());
            row.put("planStartDate", op.getPlanStartDate());
            row.put("planEndDate", op.getPlanEndDate());
            row.put("operationStatus", op.getOperationStatus());
            options.add(row);
        }
        return options;
    }

    // ==================== 内部 ====================

    private DeviceTask createTask(DeviceInfo device, WorkOrderOperation op, String source,
                                  String status, BigDecimal countBase) {
        DeviceTask task = new DeviceTask();
        task.setTaskNo(nextTaskNo());
        task.setDeviceId(device.getId());
        task.setOrderId(op.getOrderId());
        task.setOrderNo(op.getOrderNo());
        task.setOperationRowId(op.getId());
        task.setOperationName(op.getOperationName());
        task.setSourceType(source);
        task.setCountBase(countBase);
        task.setTaskStatus(status);
        task.setStartTime(LocalDateTime.now());
        try {
            save(task);
        } catch (DuplicateKeyException e) {
            // 并发下部分唯一索引生效：直接复用已有执行中任务
            DeviceTask running = baseMapper.findRunning(device.getId());
            if (running != null) return running;
            throw new BusinessException("该设备已有执行中的任务");
        }
        return task;
    }

    private WorkOrderOperation requireOperation(Long operationRowId) {
        if (operationRowId == null) throw new BusinessException("请选择工单工序");
        WorkOrderOperation op = operationMapper.selectById(operationRowId);
        if (op == null) throw new BusinessException("工单工序不存在");
        WorkOrder order = op.getOrderId() == null ? null : workOrderMapper.selectById(op.getOrderId());
        if (!isApproved(order)) throw new BusinessException("该工序所属工单未生效或已关闭，不能关联");
        return op;
    }

    private boolean isApproved(WorkOrder order) {
        if (order == null) return false;
        if (!"已生效".equals(order.getStatus())) return false;
        return order.getIsClosed() == null || order.getIsClosed() == 0;
    }

    private void enrich(List<DeviceTask> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> deviceIds = list.stream().map(DeviceTask::getDeviceId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> opIds = list.stream().map(DeviceTask::getOperationRowId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, DeviceInfo> devices = deviceIds.isEmpty() ? Map.of()
                : deviceInfoMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(DeviceInfo::getId, d -> d, (a, b) -> a));
        Map<Long, WorkOrderOperation> ops = opIds.isEmpty() ? Map.of()
                : operationMapper.selectBatchIds(opIds).stream()
                .collect(Collectors.toMap(WorkOrderOperation::getId, o -> o, (a, b) -> a));
        Set<Long> orderIds = list.stream().map(DeviceTask::getOrderId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WorkOrder> orders = orderIds.isEmpty() ? Map.of()
                : workOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(WorkOrder::getOrderId, o -> o, (a, b) -> a));
        for (DeviceTask t : list) {
            DeviceInfo d = devices.get(t.getDeviceId());
            t.setDeviceCode(d == null ? null : d.getDeviceCode());
            t.setDeviceName(d == null ? null : d.getDeviceName());
            WorkOrderOperation op = ops.get(t.getOperationRowId());
            t.setOperationLineNo(op == null ? null : op.getLineNo());
            t.setOperationStatus(op == null ? null : op.getOperationStatus());
            WorkOrder o = orders.get(t.getOrderId());
            t.setMaterialName(o == null ? null : o.getMaterialName());
        }
    }

    private String nextTaskNo() {
        String prefix = "DT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long n = count(new QueryWrapper<DeviceTask>().likeRight("task_no", prefix)) + 1;
        String no;
        do {
            no = prefix + String.format("%04d", n++);
        } while (count(new QueryWrapper<DeviceTask>().eq("task_no", no)) > 0);
        return no;
    }
}
