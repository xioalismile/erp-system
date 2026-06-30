package com.erp.basedata.controller;

import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户 Controller
 * 路由: /api/basedata/customer
 */
@RestController
@RequestMapping("/api/basedata/customer")
public class CustomerController extends BaseCrudController<Customer, Long> {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    protected IService<Customer> getService() { return customerService; }

    @Override
    protected String getCodeField() { return "customer_code"; }

    @Override
    protected String getIdField() { return "customer_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("customer_code", "customer_name", "short_name"); }
}
