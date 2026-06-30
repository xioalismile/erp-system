package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.system.entity.SysDictData;
import com.erp.system.mapper.SysDictDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements SysDictDataService {

    @Override
    public List<SysDictData> listByTypeId(Long typeId) {
        return list(new QueryWrapper<SysDictData>()
                .eq("dict_type_id", typeId)
                .orderByAsc("dict_sort"));
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
