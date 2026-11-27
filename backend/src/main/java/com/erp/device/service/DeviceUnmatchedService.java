package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DeviceTask;
import com.erp.device.entity.DeviceUnmatchedData;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.device.mapper.DeviceUnmatchedDataMapper;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.WorkOrderOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 未关联数据池 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceUnmatchedService extends ServiceImpl<DeviceUnmatchedDataMapper, DeviceUnmatchedData> {

    private static final int MERGE_WINDOW_MINUTES = 5;
    private static final int EXPIRE_DAYS = 7;

    private final DeviceInfoMapper deviceInfoMapper;
    private final WorkOrderOperationMapper operationMapper;
    private final DeviceTaskService taskService;

    public PageResult<DeviceUnmatchedData> pageQuery(int current, int size, Long deviceId, String status) {
        QueryWrapper<DeviceUnmatchedData> qw = new QueryWrapper<>();
        if (deviceId != null) qw.eq("device_id", deviceId);
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByDesc("id");
        Page<DeviceUnmatchedData> page = page(new Page<>(current, size), qw);
        enrich(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    /**
     * 记录一条未关联数据。
     * 轮询设备每 5 秒上报一次，如果每次插一条会刷屏：同一设备 5 分钟内只保留一条，更新计数与时间。
     */
    public void record(DeviceInfo device, BigDecimal qualified, BigDecimal rejected, Long rawMessageId, String reason) {
        if ((qualified == null || qualified.compareTo(BigDecimal.ZERO) <= 0)
                && (rejected == null || rejected.compareTo(BigDecimal.ZERO) <= 0)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        DeviceUnmatchedData latest = getOne(new QueryWrapper<DeviceUnmatchedData>()
                .eq("device_id", device.getId()).eq("status", "未关联")
                .orderByDesc("id").last("limit 1"));
        if (latest != null && latest.getCreateTime() != null
                && latest.getCreateTime().isAfter(now.minusMinutes(MERGE_WINDOW_MINUTES))) {
            latest.setPeriodEnd(now);
            latest.setQualifiedCount(nvl(qualified));
            latest.setRejectedCount(nvl(rejected));
            latest.setRawMessageId(rawMessageId);
            latest.setReason(reason);
            updateById(latest);
            return;
        }
        DeviceUnmatchedData row = new DeviceUnmatchedData();
        row.setDeviceId(device.getId());
        row.setPeriodStart(now);
        row.setPeriodEnd(now);
        row.setQualifiedCount(nvl(qualified));
        row.setRejectedCount(nvl(rejected));
        row.setRawMessageId(rawMessageId);
        row.setReason(reason);
        row.setStatus("未关联");
        save(row);
    }

    /** 人工认领：把这段计数关联到指定工单工序 */
    public DeviceTask claim(Long id, Long operationRowId, String operator) {
        DeviceUnmatchedData row = getById(id);
        if (row == null) throw new BusinessException("未关联数据不存在");
        if (!"未关联".equals(row.getStatus())) throw new BusinessException("该数据已认领或已过期");
        WorkOrderOperation op = operationMapper.selectById(operationRowId);
        if (op == null) throw new BusinessException("工单工序不存在");
        DeviceInfo device = deviceInfoMapper.selectById(row.getDeviceId());
        if (device == null) throw new BusinessException("设备不存在");

        DeviceTask task = taskService.createRunning(device, op, "MANUAL", nvl(row.getQualifiedCount()));
        row.setStatus("已认领");
        row.setClaimedOperationRowId(operationRowId);
        row.setClaimedBy(operator);
        row.setClaimedTime(LocalDateTime.now());
        updateById(row);
        return task;
    }

    /** 每天 02:10 把超过 7 天未认领的数据标记为已过期（只保留统计值） */
    @Scheduled(cron = "0 10 2 * * ?")
    public void expireOld() {
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(EXPIRE_DAYS);
            List<DeviceUnmatchedData> rows = list(new QueryWrapper<DeviceUnmatchedData>()
                    .eq("status", "未关联").lt("create_time", before));
            for (DeviceUnmatchedData row : rows) {
                row.setStatus("已过期");
                updateById(row);
            }
            if (!rows.isEmpty()) log.info("[设备管理] 未关联数据过期 {} 条", rows.size());
        } catch (Exception e) {
            log.warn("[设备管理] 未关联数据过期任务失败: {}", e.getMessage());
        }
    }

    private void enrich(List<DeviceUnmatchedData> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> deviceIds = list.stream().map(DeviceUnmatchedData::getDeviceId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, DeviceInfo> devices = deviceIds.isEmpty() ? Map.of()
                : deviceInfoMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(DeviceInfo::getId, d -> d, (a, b) -> a));
        Set<Long> opIds = list.stream().map(DeviceUnmatchedData::getClaimedOperationRowId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WorkOrderOperation> ops = opIds.isEmpty() ? Map.of()
                : operationMapper.selectBatchIds(opIds).stream()
                .collect(Collectors.toMap(WorkOrderOperation::getId, o -> o, (a, b) -> a));
        for (DeviceUnmatchedData row : list) {
            DeviceInfo d = row.getDeviceId() == null ? null : devices.get(row.getDeviceId());
            row.setDeviceCode(d == null ? null : d.getDeviceCode());
            row.setDeviceName(d == null ? null : d.getDeviceName());
            Long claimedOpId = row.getClaimedOperationRowId();
            // Map.of().get(null) 会抛 NPE，未认领时 claimedOperationRowId 为空
            WorkOrderOperation op = claimedOpId == null ? null : ops.get(claimedOpId);
            row.setClaimedOperationName(op == null ? null : op.getOperationName());
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
