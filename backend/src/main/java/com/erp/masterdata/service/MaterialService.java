package com.erp.masterdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.masterdata.entity.Material;

public interface MaterialService extends IService<Material> {
    Material getByCode(String materialCode);
}
