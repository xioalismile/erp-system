package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Department;
import com.erp.basedata.mapper.DepartmentMapper;
import org.springframework.stereotype.Service;

@Service
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {

    @Override
    public Department getByCode(String deptCode) {
        return getOne(new QueryWrapper<Department>().eq("dept_code", deptCode));
    }

    @Override
    public boolean existsByCode(String deptCode) {
        return baseMapper.exists(new QueryWrapper<Department>().eq("dept_code", deptCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String deptCode, Long deptId) {
        return baseMapper.exists(new QueryWrapper<Department>()
                .eq("dept_code", deptCode).ne("dept_id", deptId));
    }
}
