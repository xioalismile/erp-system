package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.MaterialCategory;

public interface MaterialCategoryService extends IService<MaterialCategory> {
    MaterialCategory getByCode(String categoryCode);
    boolean existsByCode(String categoryCode);
    boolean existsByCodeExcludeId(String categoryCode, Long categoryId);

    /** 同一父分类下编码是否已存在（父级为空/0 视为顶级同一组；excludeId 用于编辑时排除自身） */
    boolean existsUnderParent(String categoryCode, Long parentId, Long excludeId);
}
