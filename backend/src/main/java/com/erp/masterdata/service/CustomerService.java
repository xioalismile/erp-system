package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.masterdata.entity.Customer;

public interface CustomerService extends IService<Customer> {
    IPage<Customer> findPage(int page, int size, String keyword);
    Customer findByCustomerCode(String customerCode);
    boolean existsByCustomerCode(String customerCode);
    boolean existsByCustomerCodeExcludeId(String customerCode, Long customerId);
}