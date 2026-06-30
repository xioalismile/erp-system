package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.PriceLevel;
import com.erp.basedata.mapper.PriceLevelMapper;
import org.springframework.stereotype.Service;

@Service
public class PriceLevelServiceImpl extends ServiceImpl<PriceLevelMapper, PriceLevel> implements PriceLevelService {

    @Override
    public PriceLevel getByCode(String priceLevelCode) {
        return getOne(new QueryWrapper<PriceLevel>().eq("price_level_code", priceLevelCode));
    }

    @Override
    public boolean existsByCode(String priceLevelCode) {
        return baseMapper.exists(new QueryWrapper<PriceLevel>().eq("price_level_code", priceLevelCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String priceLevelCode, Long priceLevelId) {
        return baseMapper.exists(new QueryWrapper<PriceLevel>()
                .eq("price_level_code", priceLevelCode).ne("price_level_id", priceLevelId));
    }
}
