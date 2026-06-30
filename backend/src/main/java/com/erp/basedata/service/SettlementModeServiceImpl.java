package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.SettlementMode;
import com.erp.basedata.mapper.SettlementModeMapper;
import org.springframework.stereotype.Service;

@Service
public class SettlementModeServiceImpl extends ServiceImpl<SettlementModeMapper, SettlementMode> implements SettlementModeService {

    @Override
    public SettlementMode getByCode(String settleModeCode) {
        return getOne(new QueryWrapper<SettlementMode>().eq("settle_mode_code", settleModeCode));
    }

    @Override
    public boolean existsByCode(String settleModeCode) {
        return baseMapper.exists(new QueryWrapper<SettlementMode>().eq("settle_mode_code", settleModeCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String settleModeCode, Long settleModeId) {
        return baseMapper.exists(new QueryWrapper<SettlementMode>()
                .eq("settle_mode_code", settleModeCode).ne("settle_mode_id", settleModeId));
    }
}
