package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.Workstation;

public interface WorkstationService extends IService<Workstation> {

    /** 分页查询: keyword模糊搜workstation_code/workstation_name, status/workCenterId过滤 */
    PageResult<Workstation> pageQuery(int current, int size, String keyword, String status, Long workCenterId);

    /** 编码是否存在 */
    boolean existsByCode(String code);

    /** 编码是否存在(排除自身) */
    boolean existsByCodeExcludeId(String code, Long id);
}
