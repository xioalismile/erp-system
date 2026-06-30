package com.erp.masterdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.masterdata.entity.Bom;

import java.util.List;
import java.util.Map;

public interface BomService extends IService<Bom> {

    /** 保存BOM主单 + 明细 */
    void saveBomWithItems(Bom bom, List<com.erp.masterdata.entity.BomItem> items);

    /** 分页查询 */
    PageResult<Bom> pageQuery(int current, int size, String keyword, String status);

    /** 获取BOM详情(含明细) */
    Bom getDetail(Long id);

    /** BOM 树形展开 - 递归获取所有层级子件 */
    Map<String, Object> expandBom(String materialCode, java.math.BigDecimal quantity);

    /** BOM 扁平化展开 - 所有层级子件平铺, 按物料聚合 */
    List<Map<String, Object>> flattenBom(String materialCode, java.math.BigDecimal quantity);

    /** 单号生成 */
    String generateBomCode();
}
