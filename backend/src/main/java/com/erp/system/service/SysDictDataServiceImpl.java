package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.system.entity.SysDictData;
import com.erp.system.entity.SysDictType;
import com.erp.system.mapper.SysDictDataMapper;
import com.erp.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements SysDictDataService {

    private final SysDictTypeMapper sysDictTypeMapper;

    @Override
    public List<SysDictData> listByTypeId(Long typeId) {
        return list(new QueryWrapper<SysDictData>()
                .eq("dict_type_id", typeId)
                .orderByAsc("dict_sort"));
    }

    @Override
    public List<SysDictData> listByTypeCode(String typeCode) {
        SysDictType type = sysDictTypeMapper.selectOne(
                new QueryWrapper<SysDictType>().eq("dict_type_code", typeCode));
        if (type == null) {
            return List.of();
        }
        return list(new QueryWrapper<SysDictData>()
                .eq("dict_type_id", type.getDictTypeId())
                .orderByAsc("dict_sort"));
    }

    @Override
    @Transactional
    public SysDictData saveOptionByTypeCode(String typeCode, String label) {
        if (typeCode == null || typeCode.isBlank()) throw new BusinessException("字典类型编码不能为空");
        if (label == null || label.isBlank()) throw new BusinessException("字典标签不能为空");

        SysDictType type = sysDictTypeMapper.selectOne(
                new QueryWrapper<SysDictType>().eq("dict_type_code", typeCode));
        if (type == null) {
            type = new SysDictType();
            type.setDictTypeCode(typeCode);
            type.setDictTypeName(typeCode);
            type.setDictCategory("基础资料");
            type.setSortOrder(0);
            type.setStatus("启用");
            sysDictTypeMapper.insert(type);
            log.info("自动创建字典类型: code={} name={}", typeCode, typeCode);
        }

        // 幂等：已存在相同 label 直接返回已有记录
        SysDictData existing = getOne(new QueryWrapper<SysDictData>()
                .eq("dict_type_id", type.getDictTypeId())
                .eq("dict_label", label)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        // sortOrder = 当前最大 sort + 1
        List<SysDictData> maxSortList = list(new QueryWrapper<SysDictData>()
                .eq("dict_type_id", type.getDictTypeId())
                .orderByDesc("dict_sort")
                .last("LIMIT 1"));
        int nextSort = maxSortList.isEmpty() || maxSortList.get(0).getDictSort() == null
                ? 1 : maxSortList.get(0).getDictSort() + 1;

        SysDictData dictData = new SysDictData();
        dictData.setDictTypeId(type.getDictTypeId());
        dictData.setDictLabel(label);
        dictData.setDictValue(label);
        dictData.setDictSort(nextSort);
        dictData.setIsDefault((short) 0);
        dictData.setIsEnabled((short) 1);
        save(dictData);
        log.info("新增字典选项: typeCode={} label={} sort={}", typeCode, label, nextSort);
        return dictData;
    }

    @Override
    @Transactional
    public void saveDictData(SysDictData dictData) {
        if (dictData.getDictTypeId() == null) throw new BusinessException("字典类型ID不能为空");
        if (dictData.getDictLabel() == null || dictData.getDictLabel().isBlank()) {
            throw new BusinessException("字典标签不能为空");
        }
        if (dictData.getDictValue() == null || dictData.getDictValue().isBlank()) {
            throw new BusinessException("字典值不能为空");
        }
        if (dictData.getDictSort() == null) dictData.setDictSort(0);
        if (dictData.getIsDefault() == null) dictData.setIsDefault((short) 0);
        if (dictData.getIsEnabled() == null) dictData.setIsEnabled((short) 1);
        save(dictData);
        log.info("新增字典数据: typeId={} label={}", dictData.getDictTypeId(), dictData.getDictLabel());
    }

    @Override
    @Transactional
    public void updateDictData(SysDictData dictData) {
        if (dictData.getDictDataId() == null) throw new BusinessException("字典数据ID不能为空");
        updateById(dictData);
        log.info("更新字典数据: id={}", dictData.getDictDataId());
    }

    @Override
    @Transactional
    public void deleteDictData(Long id) {
        SysDictData dictData = getById(id);
        if (dictData == null) throw new BusinessException("字典数据不存在");
        removeById(id);
        log.info("删除字典数据: id={}", id);
    }
}
