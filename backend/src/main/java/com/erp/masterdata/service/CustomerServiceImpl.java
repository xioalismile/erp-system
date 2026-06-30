package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.mapper.CustomerMapper;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Override
    public IPage<Customer> findPage(int page, int size, String keyword) {
        Page<Customer> pageRequest = new Page<>(page, size);
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("customer_code", keyword).or().like("customer_name", keyword);
        }
        wrapper.orderByDesc("created_at");
        return baseMapper.selectPage(pageRequest, wrapper);
    }

    @Override
    public Customer findByCustomerCode(String customerCode) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_code", customerCode).eq("deleted", 0);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public boolean existsByCustomerCode(String customerCode) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_code", customerCode).eq("deleted", 0);
        return baseMapper.exists(wrapper);
    }

    @Override
    public boolean existsByCustomerCodeExcludeId(String customerCode, Long customerId) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_code", customerCode).ne("customer_id", customerId).eq("deleted", 0);
        return baseMapper.exists(wrapper);
    }
}