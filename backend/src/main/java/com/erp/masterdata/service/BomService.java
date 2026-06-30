package com.erp.masterdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.masterdata.entity.Bom;

import java.util.List;
import java.util.Map;

public interface BomService extends IService<Bom> {

    /**
     * 多级BOM展开 - 递归获取所有层级子件
     * @param parentCode 父件编码
     * @return 树形结构
     */
    Map<String, Object> expandBom(String parentCode, java.math.BigDecimal quantity);

    /**
     * 扁平化BOM展开 - 所有层级子件平铺, 按物料聚合
     */
    List<Map<String, Object>> flattenBom(String parentCode, java.math.BigDecimal quantity);
}
