package com.erp.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceCategory;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.mapper.DeviceCategoryMapper;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.device.service.DeviceCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCategoryServiceImpl extends ServiceImpl<DeviceCategoryMapper, DeviceCategory>
        implements DeviceCategoryService {

    private final DeviceInfoMapper deviceInfoMapper;

    @Override
    public List<DeviceCategory> tree() {
        List<DeviceCategory> all = list(new QueryWrapper<DeviceCategory>()
                .orderByAsc("sort_order").orderByAsc("id"));
        Map<Long, DeviceCategory> map = new LinkedHashMap<>();
        for (DeviceCategory c : all) {
            c.setChildren(new ArrayList<>());
            map.put(c.getId(), c);
        }
        List<DeviceCategory> roots = new ArrayList<>();
        for (DeviceCategory c : all) {
            if (c.getParentId() != null && map.containsKey(c.getParentId())) {
                map.get(c.getParentId()).getChildren().add(c);
            } else {
                roots.add(c);
            }
        }
        // 每个分类的直接设备数（不递归，避免层级口径歧义）
        QueryWrapper<DeviceInfo> qw = new QueryWrapper<>();
        qw.select("category_id", "count(*) as cnt").groupBy("category_id");
        List<Map<String, Object>> rows = deviceInfoMapper.selectMaps(qw);
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object cid = row.get("category_id");
            Object cnt = row.get("cnt");
            if (cid instanceof Number && cnt instanceof Number) {
                counts.put(((Number) cid).longValue(), ((Number) cnt).longValue());
            }
        }
        for (DeviceCategory c : all) {
            c.setDeviceCount(counts.getOrDefault(c.getId(), 0L));
        }
        return roots;
    }

    @Override
    public void saveCategory(DeviceCategory category) {
        validate(category, null);
        if (isBlank(category.getCategoryCode())) {
            category.setCategoryCode(nextCode());
        }
        if (category.getSortOrder() == null) category.setSortOrder(0);
        if (category.getIsSystem() == null) category.setIsSystem(0);
        if (isBlank(category.getStatus())) category.setStatus("启用");
        save(category);
    }

    @Override
    public void updateCategory(Long id, DeviceCategory category) {
        DeviceCategory old = getById(id);
        if (old == null) throw new BusinessException("设备分类不存在或已被删除");
        if (category.getParentId() != null && category.getParentId().equals(id)) {
            throw new BusinessException("上级分类不能选择自己");
        }
        ensureNoCycle(id, category.getParentId());
        validate(category, id);
        old.setCategoryName(category.getCategoryName().trim());
        if (!isBlank(category.getCategoryCode())) old.setCategoryCode(category.getCategoryCode().trim());
        old.setParentId(category.getParentId());
        old.setSortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder());
        old.setStatus(isBlank(category.getStatus()) ? "启用" : category.getStatus());
        old.setRemark(category.getRemark());
        updateById(old);
    }

    @Override
    public void deleteCategory(Long id) {
        DeviceCategory old = getById(id);
        if (old == null) return;
        if (old.getIsSystem() != null && old.getIsSystem() == 1) {
            throw new BusinessException("系统预置分类不能删除，可停用");
        }
        long children = lambdaQuery().eq(DeviceCategory::getParentId, id).count();
        if (children > 0) throw new BusinessException("该分类下还有 " + children + " 个子分类，请先处理子分类");
        long devices = deviceInfoMapper.selectCount(new QueryWrapper<DeviceInfo>().eq("category_id", id));
        if (devices > 0) throw new BusinessException("该分类下还有 " + devices + " 台设备，不能删除，可改为停用");
        removeById(id);
    }

    private void validate(DeviceCategory category, Long selfId) {
        if (category == null) throw new BusinessException("分类信息不能为空");
        if (isBlank(category.getCategoryName())) throw new BusinessException("请填写分类名称");
        if (category.getParentId() != null && getById(category.getParentId()) == null) {
            throw new BusinessException("上级分类不存在，请重新选择");
        }
        // 同一父级下名称不重复
        var nameQuery = lambdaQuery().eq(DeviceCategory::getCategoryName, category.getCategoryName().trim());
        if (category.getParentId() == null) {
            nameQuery.isNull(DeviceCategory::getParentId);
        } else {
            nameQuery.eq(DeviceCategory::getParentId, category.getParentId());
        }
        if (selfId != null) nameQuery.ne(DeviceCategory::getId, selfId);
        if (nameQuery.count() > 0) {
            throw new BusinessException("同级下已存在分类[" + category.getCategoryName().trim() + "]");
        }
        if (!isBlank(category.getCategoryCode())) {
            var codeQuery = lambdaQuery().eq(DeviceCategory::getCategoryCode, category.getCategoryCode().trim());
            if (selfId != null) codeQuery.ne(DeviceCategory::getId, selfId);
            if (codeQuery.count() > 0) {
                throw new BusinessException("分类编码[" + category.getCategoryCode().trim() + "]已存在");
            }
        }
    }

    /** 防止把父级改成自己的后代（形成环） */
    private void ensureNoCycle(Long id, Long newParentId) {
        Long cursor = newParentId;
        int guard = 0;
        while (cursor != null && guard++ < 100) {
            if (cursor.equals(id)) throw new BusinessException("上级分类不能选择自己的下级");
            DeviceCategory parent = getById(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
    }

    private String nextCode() {
        long n = count() + 1;
        String code;
        do {
            code = String.format("DC%03d", n++);
        } while (lambdaQuery().eq(DeviceCategory::getCategoryCode, code).count() > 0);
        return code;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
