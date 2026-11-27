package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.WorkCenter;

public interface WorkCenterService extends IService<WorkCenter> {

    /** 分页查询: keyword模糊搜work_center_code/work_center_name, status过滤, 委外供应商(supplier_id), 负责人(manager_id) */
    PageResult<WorkCenter> pageQuery(int current, int size, String keyword, String status,
                                     String supplierId, String employeeId, String sortBy, String sortOrder);

    /** 编码是否存在 */
    boolean existsByCode(String code);

    /** 编码是否存在(排除自身) */
    boolean existsByCodeExcludeId(String code, Long id);

    /** 启用: 停用 → 启用 */
    void enable(Long id, String operator);

    /** 停用: 启用 → 停用 */
    void disable(Long id, String operator);

    /** 委外工作中心必须关联供应商的校验 */
    void validateOutsourced(WorkCenter workCenter);
}
