package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.PriceLevel;

public interface PriceLevelService extends IService<PriceLevel> {
    PriceLevel getByCode(String priceLevelCode);
    boolean existsByCode(String priceLevelCode);
    boolean existsByCodeExcludeId(String priceLevelCode, Long priceLevelId);
}
