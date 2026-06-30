package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.UnitOfMeasure;

public interface UnitOfMeasureService extends IService<UnitOfMeasure> {
    UnitOfMeasure getByCode(String unitCode);
    boolean existsByCode(String unitCode);
    boolean existsByCodeExcludeId(String unitCode, Long unitId);
}
