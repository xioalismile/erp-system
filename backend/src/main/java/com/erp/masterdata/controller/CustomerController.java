package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@Validated
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public Result<Customer> create(@Valid @RequestBody CustomerDTO dto) {
        if (customerService.existsByCustomerCode(dto.getCustomerCode())) {
            return Result.error("客户编码已存在");
        }
        
        Customer customer = new Customer();
        copyDtoToEntity(dto, customer);
        customer.setStatus("正常");
        
        customerService.save(customer);
        log.info("新增客户成功: {}", customer.getCustomerCode());
        return Result.success(customer);
    }

    @PutMapping("/{id}")
    public Result<Customer> update(@PathVariable Long id, @Valid @RequestBody CustomerDTO dto) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }
        
        if (customerService.existsByCustomerCodeExcludeId(dto.getCustomerCode(), id)) {
            return Result.error("客户编码已存在");
        }
        
        copyDtoToEntity(dto, customer);
        customerService.updateById(customer);
        log.info("修改客户成功: {}", customer.getCustomerCode());
        return Result.success(customer);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }
        
        customerService.removeById(id);
        log.info("删除客户成功: {}", customer.getCustomerCode());
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Customer> getById(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }
        return Result.success(customer);
    }

    @GetMapping
    public Result<PageResult<Customer>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        
        IPage<Customer> pageResult = customerService.findPage(page, size, keyword);
        PageResult<Customer> result = PageResult.of(
                pageResult.getRecords(),
                pageResult.getTotal(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize()
        );
        return Result.success(result);
    }

    private void copyDtoToEntity(CustomerDTO dto, Customer customer) {
        customer.setCustomerCode(dto.getCustomerCode());
        customer.setCustomerName(dto.getCustomerName());
        customer.setShortName(dto.getShortName());
        customer.setCustomerType(dto.getCustomerType());
        customer.setCustomerLevel(dto.getCustomerLevel());
        customer.setTaxNo(dto.getTaxNo());
        customer.setContactPerson(dto.getContactPerson());
        customer.setContactPhone(dto.getContactPhone());
        customer.setEmail(dto.getEmail());
        customer.setAddress(dto.getAddress());
        customer.setBankName(dto.getBankName());
        customer.setBankAccount(dto.getBankAccount());
        customer.setCreditLimit(dto.getCreditLimit());
        customer.setPaymentTermId(dto.getPaymentTermId());
        customer.setCurrencyId(dto.getCurrencyId());
        customer.setDefaultSalerId(dto.getDefaultSalerId());
        customer.setDefaultPriceLevelId(dto.getDefaultPriceLevelId());
    }

    @lombok.Data
    public static class CustomerDTO {
        @NotBlank(message = "客户编码不能为空")
        private String customerCode;
        
        @NotBlank(message = "客户名称不能为空")
        private String customerName;
        
        private String shortName;
        
        private String customerType = "企业客户";
        
        private String customerLevel = "普通客户";
        
        private String taxNo;
        
        private String contactPerson;
        
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "联系电话格式不正确")
        private String contactPhone;
        
        private String email;
        
        private String address;
        
        private String bankName;
        
        private String bankAccount;
        
        private java.math.BigDecimal creditLimit;
        
        private Long paymentTermId;
        
        private Long currencyId;
        
        private Long defaultSalerId;
        
        private Long defaultPriceLevelId;
    }
}