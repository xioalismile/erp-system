package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Currency;

public interface CurrencyService extends IService<Currency> {
    Currency getByCode(String currencyCode);
    boolean existsByCode(String currencyCode);
    boolean existsByCodeExcludeId(String currencyCode, Long currencyId);
}
