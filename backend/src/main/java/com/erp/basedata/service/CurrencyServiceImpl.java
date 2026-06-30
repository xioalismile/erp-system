package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.mapper.CurrencyMapper;
import org.springframework.stereotype.Service;

@Service
public class CurrencyServiceImpl extends ServiceImpl<CurrencyMapper, Currency> implements CurrencyService {

    @Override
    public Currency getByCode(String currencyCode) {
        return getOne(new QueryWrapper<Currency>().eq("currency_code", currencyCode));
    }

    @Override
    public boolean existsByCode(String currencyCode) {
        return baseMapper.exists(new QueryWrapper<Currency>().eq("currency_code", currencyCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String currencyCode, Long currencyId) {
        return baseMapper.exists(new QueryWrapper<Currency>()
                .eq("currency_code", currencyCode).ne("currency_id", currencyId));
    }
}
