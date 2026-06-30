package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.mapper.MaterialCategoryMapper;
import org.springframework.stereotype.Service;

@Service
public class MaterialCategoryServiceImpl extends ServiceImpl<MaterialCategoryMapper, MaterialCategory> implements MaterialCategoryService {

    @Override
    public MaterialCategory getByCode(String categoryCode) {
        return getOne(new QueryWrapper<MaterialCategory>().eq("category_code", categoryCode));
    }

    @Override
    public boolean existsByCode(String categoryCode) {
        return baseMapper.exists(new QueryWrapper<MaterialCategory>().eq("category_code", categoryCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String categoryCode, Long categoryId) {
        return baseMapper.exists(new QueryWrapper<MaterialCategory>()
                .eq("category_code", categoryCode).ne("category_id", categoryId));
    }
}
