package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;

@Service
public class WarehouseServiceImpl extends ServiceImpl<WarehouseMapper, Warehouse> implements WarehouseService {

    @Override
    public Warehouse getByCode(String stockCode) {
        return getOne(new QueryWrapper<Warehouse>().eq("stock_code", stockCode));
    }

    @Override
    public boolean existsByCode(String stockCode) {
        return baseMapper.exists(new QueryWrapper<Warehouse>().eq("stock_code", stockCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String stockCode, Long stockId) {
        return baseMapper.exists(new QueryWrapper<Warehouse>()
                .eq("stock_code", stockCode).ne("stock_id", stockId));
    }
}
