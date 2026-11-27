package com.erp.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.mapper.EmployeeMapper;
import com.erp.common.PageResult;
import com.erp.common.qrcode.QrCodeService;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceCategory;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.mapper.DeviceCategoryMapper;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.device.service.DeviceInfoService;
import com.erp.production.entity.WorkCenter;
import com.erp.production.entity.Workstation;
import com.erp.production.mapper.WorkCenterMapper;
import com.erp.production.mapper.WorkstationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements DeviceInfoService {

    private static final Set<String> STATUSES = Set.of("启用", "停用", "报废");
    private static final Set<String> MERGE_MODES = Set.of("SUM", "PRIMARY_ONLY");

    private final DeviceCategoryMapper categoryMapper;
    private final WorkCenterMapper workCenterMapper;
    private final WorkstationMapper workstationMapper;
    private final EmployeeMapper employeeMapper;
    private final QrCodeService qrCodeService;

    @Override
    public PageResult<DeviceInfo> pageQuery(int current, int size, String keyword, Long categoryId,
                                            Long workCenterId, String status) {
        QueryWrapper<DeviceInfo> qw = new QueryWrapper<>();
        if (!isBlank(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like("device_code", kw).or().like("device_name", kw)
                    .or().like("brand", kw).or().like("model", kw).or().like("serial_no", kw));
        }
        if (categoryId != null) {
            qw.in("category_id", collectCategoryIds(categoryId));
        }
        if (workCenterId != null) qw.eq("work_center_id", workCenterId);
        if (!isBlank(status)) qw.eq("status", status);
        qw.orderByDesc("is_key_device").orderByAsc("device_code");
        Page<DeviceInfo> page = page(new Page<>(current, size), qw);
        fillDisplay(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    @Override
    public DeviceInfo getDetail(Long id) {
        DeviceInfo device = getById(id);
        if (device == null) throw new BusinessException("设备不存在或已被删除");
        fillDisplay(List.of(device));
        return device;
    }

    @Override
    @Transactional
    public DeviceInfo createDevice(DeviceInfo device) {
        normalize(device);
        validate(device, null);
        if (isBlank(device.getDeviceCode())) {
            device.setDeviceCode(nextDeviceCode());
        } else {
            device.setDeviceCode(device.getDeviceCode().trim());
        }
        save(device);
        return getDetail(device.getId());
    }

    @Override
    @Transactional
    public DeviceInfo updateDevice(Long id, DeviceInfo device) {
        DeviceInfo old = getById(id);
        if (old == null) throw new BusinessException("设备不存在或已被删除");
        normalize(device);
        validate(device, id);
        if (!isBlank(device.getDeviceCode())) old.setDeviceCode(device.getDeviceCode().trim());
        old.setDeviceName(device.getDeviceName());
        old.setCategoryId(device.getCategoryId());
        old.setBrand(device.getBrand());
        old.setModel(device.getModel());
        old.setSerialNo(device.getSerialNo());
        old.setAssetNo(device.getAssetNo());
        old.setDepartmentId(device.getDepartmentId());
        old.setWorkCenterId(device.getWorkCenterId());
        old.setWorkstationId(device.getWorkstationId());
        old.setAllowedOperationIds(device.getAllowedOperationIds());
        old.setOwnerEmployeeId(device.getOwnerEmployeeId());
        old.setLocation(device.getLocation());
        old.setPurchaseDate(device.getPurchaseDate());
        old.setWarrantyEndDate(device.getWarrantyEndDate());
        old.setSupplierId(device.getSupplierId());
        old.setRatedSpeed(device.getRatedSpeed());
        old.setReportThreshold(device.getReportThreshold());
        old.setCountMergeMode(device.getCountMergeMode());
        old.setAutoStart(device.getAutoStart());
        old.setAutoPause(device.getAutoPause());
        old.setIsKeyDevice(device.getIsKeyDevice());
        old.setStatus(device.getStatus());
        old.setImageUrl(device.getImageUrl());
        old.setRemark(device.getRemark());
        updateById(old);
        return getDetail(id);
    }

    @Override
    public void deleteDevice(Long id) {
        DeviceInfo old = getById(id);
        if (old == null) return;
        long tasks = baseMapper.countTasks(id);
        long credentials = baseMapper.countCredentials(id);
        long repairs = baseMapper.countRepairs(id);
        long inspections = baseMapper.countInspections(id);
        long maintenance = baseMapper.countMaintenanceOrders(id);
        if (tasks + credentials + repairs + inspections + maintenance > 0) {
            List<String> used = new ArrayList<>();
            if (credentials > 0) used.add(credentials + " 个接入凭证");
            if (tasks > 0) used.add(tasks + " 条设备任务");
            if (repairs > 0) used.add(repairs + " 张维修单");
            if (inspections > 0) used.add(inspections + " 条点检记录");
            if (maintenance > 0) used.add(maintenance + " 张保养单");
            throw new BusinessException("该设备已有" + String.join("、", used) + "，不能删除；如不再使用请改为停用或报废");
        }
        removeById(id);
    }

    @Override
    public void changeStatus(Long id, String status) {
        if (isBlank(status) || !STATUSES.contains(status)) {
            throw new BusinessException("设备状态只能是：启用 / 停用 / 报废");
        }
        DeviceInfo old = getById(id);
        if (old == null) throw new BusinessException("设备不存在或已被删除");
        old.setStatus(status);
        updateById(old);
    }

    @Override
    public byte[] qrcode(Long id, Integer size) {
        DeviceInfo device = getById(id);
        if (device == null) throw new BusinessException("设备不存在或已被删除");
        return qrCodeService.png(device.getDeviceCode(), qrCodeService.clampSize(size));
    }

    @Override
    public String qrcodeDataUri(Long id, Integer size) {
        byte[] png = qrcode(id, size);
        return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png);
    }

    @Override
    public List<DeviceInfo> listEnabled() {
        List<DeviceInfo> list = list(new QueryWrapper<DeviceInfo>()
                .eq("status", "启用").orderByAsc("device_code"));
        fillDisplay(list);
        return list;
    }

    // ==================== 内部方法 ====================

    private void normalize(DeviceInfo d) {
        if (d == null) throw new BusinessException("设备信息不能为空");
        if (d.getReportThreshold() == null) d.setReportThreshold(50);
        if (isBlank(d.getCountMergeMode())) d.setCountMergeMode("SUM");
        if (d.getAutoStart() == null) d.setAutoStart(0);
        if (d.getAutoPause() == null) d.setAutoPause(0);
        if (d.getIsKeyDevice() == null) d.setIsKeyDevice(0);
        if (isBlank(d.getStatus())) d.setStatus("启用");
        if (d.getDeviceName() != null) d.setDeviceName(d.getDeviceName().trim());
    }

    private void validate(DeviceInfo d, Long selfId) {
        if (isBlank(d.getDeviceName())) throw new BusinessException("请填写设备名称");
        if (d.getCategoryId() == null) throw new BusinessException("请选择设备分类");
        if (categoryMapper.selectById(d.getCategoryId()) == null) {
            throw new BusinessException("设备分类不存在，请重新选择");
        }
        if (d.getWorkCenterId() == null && d.getWorkstationId() == null) {
            throw new BusinessException("请至少选择工作中心或工位（自动关联必须依赖其中之一）");
        }
        if (d.getWorkCenterId() != null && workCenterMapper.selectById(d.getWorkCenterId()) == null) {
            throw new BusinessException("工作中心不存在，请重新选择");
        }
        if (d.getWorkstationId() != null) {
            Workstation ws = workstationMapper.selectById(d.getWorkstationId());
            if (ws == null) throw new BusinessException("工位不存在，请重新选择");
            if (d.getWorkCenterId() != null && ws.getWorkCenterId() != null
                    && !ws.getWorkCenterId().equals(d.getWorkCenterId())) {
                throw new BusinessException("所选工位不属于该工作中心，请重新选择");
            }
        }
        for (Long opId : parseIds(d.getAllowedOperationIds())) {
            if (workstationMapper.selectById(opId) == null) {
                throw new BusinessException("可执行工序中存在无效工序（ID=" + opId + "），请重新选择");
            }
        }
        if (d.getReportThreshold() == null || d.getReportThreshold() <= 0) {
            throw new BusinessException("报工阈值必须大于 0");
        }
        if (d.getRatedSpeed() != null && d.getRatedSpeed().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("额定节拍不能为负数");
        }
        if (!STATUSES.contains(d.getStatus())) {
            throw new BusinessException("设备状态只能是：启用 / 停用 / 报废");
        }
        if (!MERGE_MODES.contains(d.getCountMergeMode())) {
            throw new BusinessException("计数合并口径只能是：分别求和 / 只认主设备");
        }
        if (!isBlank(d.getDeviceCode())) {
            var q = lambdaQuery().eq(DeviceInfo::getDeviceCode, d.getDeviceCode().trim());
            if (selfId != null) q.ne(DeviceInfo::getId, selfId);
            if (q.count() > 0) {
                throw new BusinessException("设备编码[" + d.getDeviceCode().trim() + "]已存在");
            }
        }
    }

    /** 选中分类及其全部下级分类 */
    private List<Long> collectCategoryIds(Long rootId) {
        List<DeviceCategory> all = categoryMapper.selectList(null);
        Map<Long, List<Long>> children = new LinkedHashMap<>();
        for (DeviceCategory c : all) {
            if (c.getParentId() != null) {
                children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (ids.add(id)) {
                List<Long> sub = children.get(id);
                if (sub != null) queue.addAll(sub);
            }
        }
        return new ArrayList<>(ids);
    }

    private void fillDisplay(List<DeviceInfo> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> catIds = new HashSet<>();
        Set<Long> wcIds = new HashSet<>();
        Set<Long> wsIds = new HashSet<>();
        Set<Long> empIds = new HashSet<>();
        Set<Long> opIds = new HashSet<>();
        for (DeviceInfo d : list) {
            if (d.getCategoryId() != null) catIds.add(d.getCategoryId());
            if (d.getWorkCenterId() != null) wcIds.add(d.getWorkCenterId());
            if (d.getWorkstationId() != null) wsIds.add(d.getWorkstationId());
            if (d.getOwnerEmployeeId() != null) empIds.add(d.getOwnerEmployeeId());
            opIds.addAll(parseIds(d.getAllowedOperationIds()));
        }
        Map<Long, String> catNames = nameMap(catIds.isEmpty() ? List.of() : categoryMapper.selectBatchIds(catIds),
                DeviceCategory::getId, DeviceCategory::getCategoryName);
        Map<Long, String> wcNames = nameMap(wcIds.isEmpty() ? List.of() : workCenterMapper.selectBatchIds(wcIds),
                WorkCenter::getWorkCenterId, WorkCenter::getWorkCenterName);
        Map<Long, String> wsNames = nameMap(wsIds.isEmpty() ? List.of() : workstationMapper.selectBatchIds(wsIds),
                Workstation::getWorkstationId, Workstation::getWorkstationName);
        Map<Long, String> empNames = nameMap(empIds.isEmpty() ? List.of() : employeeMapper.selectBatchIds(empIds),
                Employee::getEmployeeId, Employee::getEmployeeName);
        Map<Long, String> opNames = nameMap(opIds.isEmpty() ? List.of() : workstationMapper.selectBatchIds(opIds),
                Workstation::getWorkstationId, Workstation::getWorkstationName);
        for (DeviceInfo d : list) {
            d.setCategoryName(nameOf(catNames, d.getCategoryId()));
            d.setWorkCenterName(nameOf(wcNames, d.getWorkCenterId()));
            d.setWorkstationName(nameOf(wsNames, d.getWorkstationId()));
            d.setOwnerEmployeeName(nameOf(empNames, d.getOwnerEmployeeId()));
            List<String> names = new ArrayList<>();
            for (Long opId : parseIds(d.getAllowedOperationIds())) {
                String name = nameOf(opNames, opId);
                if (name != null) names.add(name);
            }
            d.setAllowedOperationNames(String.join("、", names));
        }
    }

    /** 安全的名字映射：跳过空键，允许空值（Collectors.toMap 遇 null 会抛 NPE） */
    private <T> Map<Long, String> nameMap(List<T> rows, java.util.function.Function<T, Long> keyFn,
                                          java.util.function.Function<T, String> valFn) {
        Map<Long, String> map = new java.util.HashMap<>();
        if (rows == null) return map;
        for (T row : rows) {
            Long key = keyFn.apply(row);
            if (key != null) map.put(key, valFn.apply(row));
        }
        return map;
    }

    /** Map.of() 的 get(null) 会抛 NPE，这里统一处理空键 */
    private String nameOf(Map<Long, String> map, Long id) {
        return id == null ? null : map.get(id);
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
                // 非数字项直接跳过，校验阶段会因数量不一致提示
            }
        }
        return ids;
    }

    private String nextDeviceCode() {
        long n = count() + 1;
        String code;
        do {
            code = String.format("DEV%04d", n++);
        } while (lambdaQuery().eq(DeviceInfo::getDeviceCode, code).count() > 0);
        return code;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
