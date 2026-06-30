package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Department;

public interface DepartmentService extends IService<Department> {
    Department getByCode(String deptCode);
    boolean existsByCode(String deptCode);
    boolean existsByCodeExcludeId(String deptCode, Long deptId);
}
