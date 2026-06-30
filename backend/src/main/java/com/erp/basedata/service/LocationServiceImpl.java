package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Location;
import com.erp.basedata.mapper.LocationMapper;
import org.springframework.stereotype.Service;

@Service
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements LocationService {

    @Override
    public Location getByCode(String binCode) {
        return getOne(new QueryWrapper<Location>().eq("bin_code", binCode));
    }

    @Override
    public boolean existsByCode(String binCode) {
        return baseMapper.exists(new QueryWrapper<Location>().eq("bin_code", binCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String binCode, Long binId) {
        return baseMapper.exists(new QueryWrapper<Location>()
                .eq("bin_code", binCode).ne("bin_id", binId));
    }
}
