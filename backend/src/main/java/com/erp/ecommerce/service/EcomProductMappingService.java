package com.erp.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.ecommerce.entity.EcomProductMapping;

public interface EcomProductMappingService extends IService<EcomProductMapping> {

    PageResult<EcomProductMapping> pageQuery(int current, int size, Long shopId, String keyword);

    /** 新增映射（同店铺同SKU先查后插去重） */
    void saveMapping(EcomProductMapping mapping);

    void updateMapping(EcomProductMapping mapping);

    void deleteMapping(Long id);
}
