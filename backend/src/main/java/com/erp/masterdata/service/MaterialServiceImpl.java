package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import org.springframework.stereotype.Service;

@Service
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {
    @Override
    public Material getByCode(String materialCode) {
        return getOne(new QueryWrapper<Material>().eq("material_code", materialCode));
    }
}
