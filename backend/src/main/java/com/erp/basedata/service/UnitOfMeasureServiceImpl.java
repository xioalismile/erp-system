package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.mapper.UnitOfMeasureMapper;
import org.springframework.stereotype.Service;

@Service
public class UnitOfMeasureServiceImpl extends ServiceImpl<UnitOfMeasureMapper, UnitOfMeasure> implements UnitOfMeasureService {

    @Override
    public UnitOfMeasure getByCode(String unitCode) {
        return getOne(new QueryWrapper<UnitOfMeasure>().eq("unit_code", unitCode));
    }

    @Override
    public boolean existsByCode(String unitCode) {
        return baseMapper.exists(new QueryWrapper<UnitOfMeasure>().eq("unit_code", unitCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String unitCode, Long unitId) {
        return baseMapper.exists(new QueryWrapper<UnitOfMeasure>()
                .eq("unit_code", unitCode).ne("unit_id", unitId));
    }
}
