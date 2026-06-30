package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
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
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements SysDictTypeService {

    private final SysDictDataMapper dictDataMapper;

    @Override
    public PageResult<SysDictType> pageQuery(int current, int size, String keyword) {
        QueryWrapper<SysDictType> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("dict_type_code", keyword)
                    .or().like("dict_type_name", keyword));
        }
        wrapper.orderByAsc("sort_order");
        Page<SysDictType> page = new Page<>(current, size);
        Page<SysDictType> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SysDictType getDetail(Long id) {
        SysDictType dictType = getById(id);
        if (dictType == null) throw new BusinessException("字典类型不存在");
        List<SysDictData> dataList = dictDataMapper.selectList(
                new QueryWrapper<SysDictData>().eq("dict_type_id", id).orderByAsc("dict_sort"));
        dictType.setDictDataList(dataList);
        return dictType;
    }

    @Override
    @Transactional
    public void saveDictType(SysDictType dictType) {
        if (dictType.getDictTypeCode() == null || dictType.getDictTypeCode().isBlank()) {
            throw new BusinessException("字典类型编码不能为空");
        }
        if (exists(new QueryWrapper<SysDictType>().eq("dict_type_code", dictType.getDictTypeCode()))) {
            throw new BusinessException("字典类型编码已存在");
        }
        if (dictType.getIsSystem() == null) dictType.setIsSystem((short) 0);
        if (dictType.getSortOrder() == null) dictType.setSortOrder(0);
        if (dictType.getStatus() == null) dictType.setStatus("启用");
        save(dictType);
        log.info("新增字典类型: {} 编码: {}", dictType.getDictTypeName(), dictType.getDictTypeCode());
    }

    @Override
    @Transactional
    public void updateDictType(SysDictType dictType) {
        if (dictType.getDictTypeId() == null) throw new BusinessException("字典类型ID不能为空");
        if (exists(new QueryWrapper<SysDictType>().eq("dict_type_code", dictType.getDictTypeCode())
                .ne("dict_type_id", dictType.getDictTypeId()))) {
            throw new BusinessException("字典类型编码已存在");
        }
        updateById(dictType);
        log.info("更新字典类型: id={}", dictType.getDictTypeId());
    }

    @Override
    @Transactional
    public void deleteDictType(Long id) {
        SysDictType dictType = getById(id);
        if (dictType == null) throw new BusinessException("字典类型不存在");
        if (dictType.getIsSystem() != null && dictType.getIsSystem() == 1) {
            throw new BusinessException("系统内置字典类型不允许删除");
        }
        // 同时删除关联的字典数据
        dictDataMapper.delete(new QueryWrapper<SysDictData>().eq("dict_type_id", id));
        removeById(id);
        log.info("删除字典类型: id={}", id);
    }
}
