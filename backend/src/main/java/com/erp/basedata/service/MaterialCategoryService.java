package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.MaterialCategory;

public interface MaterialCategoryService extends IService<MaterialCategory> {
    MaterialCategory getByCode(String categoryCode);
    boolean existsByCode(String categoryCode);
    boolean existsByCodeExcludeId(String categoryCode, Long categoryId);
}
