package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.MaterialUnitConversion;

import java.util.List;
import java.util.Map;

/**
 * 物料辅计量换算关系服务（2026-11-10 多单位换算重构第一阶段）。
 */
public interface MaterialUnitConversionService extends IService<MaterialUnitConversion> {

    /** 按物料查换算明细（按 sort_order 升序），物料无配置返回空列表 */
    List<MaterialUnitConversion> listByMaterialId(Long materialId);

    /**
     * 按多个物料批量查换算明细（单据编辑页整单一次性取回，避免逐物料请求）。
     *
     * @return 按物料ID分组：换算行列表（按 sort_order 升序）；无配置的物料也返回空列表条目
     */
    Map<Long, List<MaterialUnitConversion>> listByMaterialIds(List<Long> materialIds);

    /**
     * 按物料整表替换换算明细（事务内先删后插）。
     *
     * <p>校验：行单位必选、同一物料单位去重、换算率必须大于 0（中文报错）。
     * rows 为 null 表示本次保存未涉及换算明细（跳过）；rows 为空列表表示清空。</p>
     */
    void replaceForMaterial(Long materialId, List<MaterialUnitConversion> rows);
}
