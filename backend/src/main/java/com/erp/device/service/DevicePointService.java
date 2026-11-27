package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.device.entity.DevicePoint;
import com.erp.device.entity.DeviceRealtimeValue;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.device.mapper.DevicePointMapper;
import com.erp.device.mapper.DeviceProtocolTemplateMapper;
import com.erp.device.mapper.DeviceRealtimeValueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 设备数据点维护 */
@Service
@RequiredArgsConstructor
public class DevicePointService extends ServiceImpl<DevicePointMapper, DevicePoint> {

    private static final Set<String> PURPOSES = Set.of(
            "START_SIGNAL", "STOP_SIGNAL", "QUALIFIED_COUNT", "REJECTED_COUNT",
            "ALARM_CODE", "FAULT_BIT", "PARAM", "WRITABLE_PARAM", "ORDER_NO", "OPERATION_NO", "MONITOR");
    private static final Set<String> DATA_TYPES = Set.of("BOOLEAN", "INTEGER", "DECIMAL", "TEXT");

    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceProtocolTemplateMapper templateMapper;
    private final DeviceRealtimeValueMapper realtimeMapper;

    public List<DevicePoint> listByDevice(Long deviceId) {
        List<DevicePoint> points = list(new QueryWrapper<DevicePoint>()
                .eq("device_id", deviceId).orderByAsc("id"));
        if (points.isEmpty()) return points;
        List<DeviceRealtimeValue> values = realtimeMapper.selectList(
                new QueryWrapper<DeviceRealtimeValue>().eq("device_id", deviceId));
        Map<Long, DeviceRealtimeValue> byPoint = new LinkedHashMap<>();
        for (DeviceRealtimeValue v : values) byPoint.put(v.getPointId(), v);
        for (DevicePoint p : points) {
            DeviceRealtimeValue v = byPoint.get(p.getId());
            if (v != null) {
                // 数值点位优先显示换算后的值（valueNum），否则 365 会盖住 36.5
                p.setLastValue(v.getValueNum() != null
                        ? v.getValueNum().stripTrailingZeros().toPlainString()
                        : v.getValueText());
                p.setLastValueTime(v.getValueTime());
            }
        }
        return points;
    }

    public DevicePoint create(DevicePoint p) {
        normalize(p);
        validate(p, null);
        save(p);
        return getById(p.getId());
    }

    public DevicePoint update(Long id, DevicePoint p) {
        DevicePoint old = getById(id);
        if (old == null) throw new BusinessException("数据点不存在");
        p.setDeviceId(old.getDeviceId());
        normalize(p);
        validate(p, id);
        old.setTemplateId(p.getTemplateId());
        old.setPointKey(p.getPointKey());
        old.setPointName(p.getPointName());
        old.setPurpose(p.getPurpose());
        old.setAddress(p.getAddress());
        old.setParseKey(p.getParseKey());
        old.setDataType(p.getDataType());
        old.setUnit(p.getUnit());
        old.setScaleFactor(p.getScaleFactor());
        old.setScaleOffset(p.getScaleOffset());
        old.setSampleInterval(p.getSampleInterval());
        old.setDeadZone(p.getDeadZone());
        old.setWritable(p.getWritable());
        old.setAlarmUpper(p.getAlarmUpper());
        old.setAlarmLower(p.getAlarmLower());
        old.setAlarmLevel(p.getAlarmLevel());
        old.setEnabled(p.getEnabled());
        old.setRemark(p.getRemark());
        updateById(old);
        return getById(id);
    }

    public void delete(Long id) {
        DevicePoint old = getById(id);
        if (old == null) return;
        // 实时值只是缓存，删除点位时一并清理，避免脏数据
        realtimeMapper.delete(new QueryWrapper<DeviceRealtimeValue>()
                .eq("device_id", old.getDeviceId()).eq("point_id", id));
        removeById(id);
    }

    private void normalize(DevicePoint p) {
        if (p == null) throw new BusinessException("点位信息不能为空");
        if (p.getPurpose() == null || p.getPurpose().isBlank()) p.setPurpose("MONITOR");
        if (p.getDataType() == null || p.getDataType().isBlank()) p.setDataType("DECIMAL");
        if (p.getScaleFactor() == null) p.setScaleFactor(BigDecimal.ONE);
        if (p.getScaleOffset() == null) p.setScaleOffset(BigDecimal.ZERO);
        if (p.getSampleInterval() == null) p.setSampleInterval(30);
        if (p.getWritable() == null) p.setWritable(0);
        if (p.getEnabled() == null) p.setEnabled(1);
        if (p.getAlarmLevel() == null || p.getAlarmLevel().isBlank()) p.setAlarmLevel("警告");
        if (p.getPointKey() != null) p.setPointKey(p.getPointKey().trim());
        if (p.getPointName() != null) p.setPointName(p.getPointName().trim());
    }

    private void validate(DevicePoint p, Long selfId) {
        if (p.getDeviceId() == null || deviceInfoMapper.selectById(p.getDeviceId()) == null) {
            throw new BusinessException("设备不存在，请重新选择");
        }
        if (p.getPointKey() == null || p.getPointKey().isBlank()) {
            throw new BusinessException("请填写点位标识（设备内唯一，建议英文）");
        }
        if (p.getPointName() == null || p.getPointName().isBlank()) {
            throw new BusinessException("请填写点位名称");
        }
        if (!PURPOSES.contains(p.getPurpose())) {
            throw new BusinessException("业务用途不在支持范围，请重新选择");
        }
        if (!DATA_TYPES.contains(p.getDataType())) {
            throw new BusinessException("数据类型只能是：布尔 / 整数 / 小数 / 文本");
        }
        if (p.getSampleInterval() < 5 || p.getSampleInterval() > 3600) {
            throw new BusinessException("采集频率需在 5~3600 秒之间");
        }
        if (p.getTemplateId() != null && templateMapper.selectById(p.getTemplateId()) == null) {
            throw new BusinessException("协议模板不存在，请重新选择");
        }
        if (p.getAlarmUpper() != null && p.getAlarmLower() != null
                && p.getAlarmLower().compareTo(p.getAlarmUpper()) >= 0) {
            throw new BusinessException("报警下限必须小于上限");
        }
        QueryWrapper<DevicePoint> qw = new QueryWrapper<DevicePoint>()
                .eq("device_id", p.getDeviceId()).eq("point_key", p.getPointKey());
        if (selfId != null) qw.ne("id", selfId);
        if (count(qw) > 0) {
            throw new BusinessException("该设备下已存在点位标识[" + p.getPointKey() + "]");
        }
    }
}
