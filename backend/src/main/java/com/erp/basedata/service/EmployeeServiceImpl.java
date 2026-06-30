package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    @Override
    public Employee getByCode(String employeeCode) {
        return getOne(new QueryWrapper<Employee>().eq("employee_code", employeeCode));
    }

    @Override
    public boolean existsByCode(String employeeCode) {
        return baseMapper.exists(new QueryWrapper<Employee>().eq("employee_code", employeeCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String employeeCode, Long employeeId) {
        return baseMapper.exists(new QueryWrapper<Employee>()
                .eq("employee_code", employeeCode).ne("employee_id", employeeId));
    }
}
