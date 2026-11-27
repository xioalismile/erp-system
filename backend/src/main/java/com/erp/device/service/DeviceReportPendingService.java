package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DevicePoint;
import com.erp.device.entity.DeviceReportPending;
import com.erp.device.entity.DeviceTask;
import com.erp.device.mapper.DeviceReportPendingMapper;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import com.erp.production.service.OperationReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 待确认报工：设备计数达到阈值生成一张，人工确认后才写工序汇报单。
 * 计数口径：合格/报废点位按「累计值」上报，增量 = 当前累计 - 任务基线 - 已结算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceReportPendingService extends ServiceImpl<DeviceReportPendingMapper, DeviceReportPending> {

    private final WorkOrderOperationMapper operationMapper;
    private final WorkOrderMapper workOrderMapper;
    private final OperationReportService operationReportService;

    /** 设备数据入库后调用：达到阈值生成待确认单 */
    public void generateIfNeeded(DeviceInfo device, DeviceTask task, Map<String, Object> values,
                                 Map<String, DevicePoint> points) {
        try {
            BigDecimal currentQ = null;
            BigDecimal currentR = null;
            for (Map.Entry<String, Object> e : values.entrySet()) {
                DevicePoint p = points.get(e.getKey());
                if (p == null || p.getPurpose() == null) continue;
                if ("QUALIFIED_COUNT".equals(p.getPurpose())) currentQ = number(e.getValue());
                if ("REJECTED_COUNT".equals(p.getPurpose())) currentR = number(e.getValue());
            }
            if (currentQ == null && currentR == null) return;

            BigDecimal base = nvl(task.getCountBase());
            BigDecimal settledQ = nvl(baseMapper.sumQualified(device.getId(), task.getOperationRowId()));
            BigDecimal settledR = nvl(baseMapper.sumRejected(device.getId(), task.getOperationRowId()));
            BigDecimal deltaQ = currentQ == null ? BigDecimal.ZERO : currentQ.subtract(base).subtract(settledQ);
            BigDecimal deltaR = currentR == null ? BigDecimal.ZERO : currentR.subtract(settledR);
            if (deltaQ.compareTo(BigDecimal.ZERO) < 0) deltaQ = BigDecimal.ZERO;
            if (deltaR.compareTo(BigDecimal.ZERO) < 0) deltaR = BigDecimal.ZERO;

            int threshold = device.getReportThreshold() == null ? 50 : device.getReportThreshold();
            boolean reach = deltaQ.compareTo(BigDecimal.valueOf(threshold)) >= 0
                    || deltaR.compareTo(BigDecimal.ZERO) > 0;
            if (!reach) return;

            WorkOrderOperation op = operationMapper.selectById(task.getOperationRowId());
            if (op == null) return;
            if (op.getIsOutsourced() != null && op.getIsOutsourced() == 1) return;

            DeviceReportPending last = getOne(new QueryWrapper<DeviceReportPending>()
                    .eq("device_id", device.getId())
                    .eq("operation_row_id", task.getOperationRowId())
                    .orderByDesc("id").last("limit 1"));
            LocalDateTime periodStart = last != null && last.getPeriodEnd() != null
                    ? last.getPeriodEnd()
                    : (task.getStartTime() == null ? LocalDateTime.now() : task.getStartTime());

            DeviceReportPending row = new DeviceReportPending();
            row.setPendingNo(nextPendingNo());
            row.setDeviceId(device.getId());
            row.setDeviceCode(device.getDeviceCode());
            row.setDeviceName(device.getDeviceName());
            row.setOrderId(task.getOrderId());
            row.setOrderNo(task.getOrderNo());
            row.setOperationRowId(task.getOperationRowId());
            row.setOperationName(task.getOperationName());
            row.setPeriodStart(periodStart);
            row.setPeriodEnd(LocalDateTime.now());
            row.setQualifiedCount(deltaQ);
            row.setRejectedCount(deltaR);
            row.setRunHours(BigDecimal.ZERO);
            row.setCountStart(base.add(settledQ));
            row.setCountEnd(currentQ == null ? base.add(settledQ) : currentQ);
            row.setSourceType(task.getSourceType());
            row.setStatus("待确认");
            save(row);
            log.info("[设备报工] 设备 {} 工序 {} 生成待确认单 {}（合格 {} / 报废 {}）",
                    device.getDeviceCode(), task.getOperationName(), row.getPendingNo(), deltaQ, deltaR);
        } catch (Exception e) {
            log.warn("[设备报工] 生成待确认单失败: {}", e.getMessage());
        }
    }

    public PageResult<DeviceReportPending> pageQuery(int current, int size, Long deviceId, String status) {
        QueryWrapper<DeviceReportPending> qw = new QueryWrapper<>();
        if (deviceId != null) qw.eq("device_id", deviceId);
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByDesc("id");
        Page<DeviceReportPending> page = page(new Page<>(current, size), qw);
        enrich(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    /** 确认：生成工序汇报单并提交（免审批时提交即生效） */
    @Transactional
    public DeviceReportPending confirm(Long id, String operator) {
        DeviceReportPending row = getById(id);
        if (row == null) throw new BusinessException("待确认报工单不存在");
        if (!"待确认".equals(row.getStatus())) throw new BusinessException("该单已确认或已驳回");

        WorkOrderOperation op = operationMapper.selectById(row.getOperationRowId());
        if (op == null) throw new BusinessException("工单工序不存在");
        if ("已完工".equals(op.getOperationStatus()) || "已取消".equals(op.getOperationStatus())) {
            throw new BusinessException("工序已" + op.getOperationStatus() + "，不能报工");
        }
        WorkOrder order = op.getOrderId() == null ? null : workOrderMapper.selectById(op.getOrderId());
        if (order == null) throw new BusinessException("生产工单不存在");

        BigDecimal qualified = nvl(row.getQualifiedCount());
        BigDecimal rejected = nvl(row.getRejectedCount());
        BigDecimal total = qualified.add(rejected);
        if (total.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("合格与报废数量都为 0，不能报工");
        BigDecimal already = nvl(op.getReportedQuantity());
        if (op.getPlanQuantity() != null && already.add(qualified).compareTo(op.getPlanQuantity()) > 0) {
            throw new BusinessException("报工后数量将超过计划数量（计划 " + op.getPlanQuantity()
                    + "，已报 " + already + "，本次 " + qualified + "）");
        }

        OperationReport report = new OperationReport();
        report.setReportNo(operationReportService.generateReportNo());
        report.setReportDate(LocalDate.now());
        report.setProductionOrderId(order.getOrderId());
        report.setProductionOrderNo(order.getOrderNo());
        report.setProcessNo(op.getLineNo() == null ? null : String.valueOf(op.getLineNo()));
        report.setWorkCenterId(op.getWorkCenterId());
        report.setWorkstationId(op.getWorkstationId());
        report.setOperationId(op.getOperationId());
        report.setOperationName(op.getOperationName());
        report.setEmployeeId(op.getAssigneeId());
        report.setEmployeeName(op.getAssigneeName());
        report.setReportType("工序汇报");
        report.setTotalQuantity(total);
        report.setQualifiedQuantity(qualified);
        report.setRejectedQuantity(rejected);
        report.setWorkHours(row.getRunHours() == null ? BigDecimal.ZERO : row.getRunHours());
        report.setOvertimeHours(BigDecimal.ZERO);
        report.setRemark("设备自动报工，待确认单 " + row.getPendingNo());
        report.setSourceBillType("设备自动报工");
        report.setSourceBillNo(row.getPendingNo());
        report.setStatus("草稿");
        report.setApprovalStatus("草稿");
        report.setEquipmentId(row.getDeviceCode());
        operationReportService.saveReport(report);
        operationReportService.submit(report.getReportId(), operator);

        row.setStatus("已确认");
        row.setConfirmBy(operator);
        row.setConfirmTime(LocalDateTime.now());
        row.setOperationReportId(report.getReportId());
        row.setOperationReportNo(report.getReportNo());
        updateById(row);
        log.info("[设备报工] 待确认单 {} 已确认，生成工序汇报单 {}", row.getPendingNo(), report.getReportNo());
        return row;
    }

    public DeviceReportPending reject(Long id, String reason, String operator) {
        DeviceReportPending row = getById(id);
        if (row == null) throw new BusinessException("待确认报工单不存在");
        if (!"待确认".equals(row.getStatus())) throw new BusinessException("该单已确认或已驳回");
        if (reason == null || reason.isBlank()) throw new BusinessException("请填写驳回原因");
        row.setStatus("已驳回");
        row.setRejectReason(reason);
        row.setConfirmBy(operator);
        row.setConfirmTime(LocalDateTime.now());
        updateById(row);
        return row;
    }

    private void enrich(List<DeviceReportPending> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> opIds = list.stream().map(DeviceReportPending::getOperationRowId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WorkOrderOperation> ops = opIds.isEmpty() ? Map.of()
                : operationMapper.selectBatchIds(opIds).stream()
                .collect(Collectors.toMap(WorkOrderOperation::getId, o -> o, (a, b) -> a));
        Set<Long> orderIds = list.stream().map(DeviceReportPending::getOrderId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WorkOrder> orders = orderIds.isEmpty() ? Map.of()
                : workOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(WorkOrder::getOrderId, o -> o, (a, b) -> a));
        for (DeviceReportPending row : list) {
            Long opId = row.getOperationRowId();
            WorkOrderOperation op = opId == null ? null : ops.get(opId);
            row.setOperationLineNo(op == null ? null : op.getLineNo());
            row.setOperationStatus(op == null ? null : op.getOperationStatus());
            Long orderId = row.getOrderId();
            WorkOrder order = orderId == null ? null : orders.get(orderId);
            row.setMaterialName(order == null ? null : order.getMaterialName());
        }
    }

    private String nextPendingNo() {
        String prefix = "DP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long n = count(new QueryWrapper<DeviceReportPending>().likeRight("pending_no", prefix)) + 1;
        String no;
        do {
            no = prefix + String.format("%04d", n++);
        } while (count(new QueryWrapper<DeviceReportPending>().eq("pending_no", no)) > 0);
        return no;
    }

    private BigDecimal number(Object v) {
        if (v == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
