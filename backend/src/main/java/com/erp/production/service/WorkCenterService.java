package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.WorkCenter;

public interface WorkCenterService extends IService<WorkCenter> {

    /** 分页查询: keyword模糊搜work_center_code/work_center_name, status过滤 */
    PageResult<WorkCenter> pageQuery(int current, int size, String keyword, String status);

    /** 编码是否存在 */
    boolean existsByCode(String code);

    /** 编码是否存在(排除自身) */
    boolean existsByCodeExcludeId(String code, Long id);
}
