package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CustomerController 接口测试
 */
@WebMvcTest(CustomerController.class)
@DisplayName("客户Controller层接口测试")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    private Customer testCustomer;
    private CustomerController.CustomerDTO testCustomerDTO;

    @BeforeEach
    void setUp() {
        testCustomer = createTestCustomer();
        testCustomerDTO = createTestCustomerDTO();
    }

    /**
     * 测试新增客户 - 成功
     */
    @Test
    @DisplayName("新增客户 - 成功")
    void testCreateCustomer_Success() throws Exception {
        when(customerService.existsByCustomerCode("C001")).thenReturn(false);
        when(customerService.save(any(Customer.class))).thenReturn(true);

        mockMvc.perform(post("/api/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.customerCode").value("C001"));
    }

    /**
     * 测试新增客户 - 编码重复
     */
    @Test
    @DisplayName("新增客户 - 编码重复")
    void testCreateCustomer_DuplicateCode() throws Exception {
        when(customerService.existsByCustomerCode("C001")).thenReturn(true);

        mockMvc.perform(post("/api/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("客户编码已存在"));
    }

    /**
     * 测试新增客户 - 客户名称为空
     */
    @Test
    @DisplayName("新增客户 - 客户名称为空")
    void testCreateCustomer_EmptyName() throws Exception {
        testCustomerDTO.setCustomerName("");

        mockMvc.perform(post("/api/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 测试新增客户 - 客户编码为空
     */
    @Test
    @DisplayName("新增客户 - 客户编码为空")
    void testCreateCustomer_EmptyCode() throws Exception {
        testCustomerDTO.setCustomerCode("");

        mockMvc.perform(post("/api/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 测试新增客户 - 联系电话格式错误
     */
    @Test
    @DisplayName("新增客户 - 联系电话格式错误")
    void testCreateCustomer_InvalidPhone() throws Exception {
        testCustomerDTO.setContactPhone("12345");

        mockMvc.perform(post("/api/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 测试修改客户 - 成功
     */
    @Test
    @DisplayName("修改客户 - 成功")
    void testUpdateCustomer_Success() throws Exception {
        when(customerService.getById(1L)).thenReturn(testCustomer);
        when(customerService.existsByCustomerCodeExcludeId("C001", 1L)).thenReturn(false);
        when(customerService.updateById(any(Customer.class))).thenReturn(true);

        testCustomerDTO.setCustomerName("修改后的客户名称");

        mockMvc.perform(put("/api/customer/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));
    }

    /**
     * 测试修改客户 - 客户不存在
     */
    @Test
    @DisplayName("修改客户 - 客户不存在")
    void testUpdateCustomer_NotFound() throws Exception {
        when(customerService.getById(999L)).thenReturn(null);

        mockMvc.perform(put("/api/customer/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("客户不存在"));
    }

    /**
     * 测试修改客户 - 编码重复
     */
    @Test
    @DisplayName("修改客户 - 编码重复")
    void testUpdateCustomer_DuplicateCode() throws Exception {
        when(customerService.getById(1L)).thenReturn(testCustomer);
        when(customerService.existsByCustomerCodeExcludeId("C001", 1L)).thenReturn(true);

        mockMvc.perform(put("/api/customer/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testCustomerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("客户编码已存在"));
    }

    /**
     * 测试删除客户 - 成功
     */
    @Test
    @DisplayName("删除客户 - 成功")
    void testDeleteCustomer_Success() throws Exception {
        when(customerService.getById(1L)).thenReturn(testCustomer);
        when(customerService.removeById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));
    }

    /**
     * 测试删除客户 - 客户不存在
     */
    @Test
    @DisplayName("删除客户 - 客户不存在")
    void testDeleteCustomer_NotFound() throws Exception {
        when(customerService.getById(999L)).thenReturn(null);

        mockMvc.perform(delete("/api/customer/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("客户不存在"));
    }

    /**
     * 测试查询单个客户 - 成功
     */
    @Test
    @DisplayName("查询单个客户 - 成功")
    void testGetCustomerById_Success() throws Exception {
        when(customerService.getById(1L)).thenReturn(testCustomer);

        mockMvc.perform(get("/api/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.customerCode").value("C001"))
                .andExpect(jsonPath("$.data.customerName").value("测试客户"));
    }

    /**
     * 测试查询单个客户 - 不存在
     */
    @Test
    @DisplayName("查询单个客户 - 不存在")
    void testGetCustomerById_NotFound() throws Exception {
        when(customerService.getById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/customer/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("客户不存在"));
    }

    /**
     * 测试分页查询客户列表 - 成功
     */
    @Test
    @DisplayName("分页查询客户列表 - 成功")
    void testListCustomers_Success() throws Exception {
        IPage<Customer> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList(testCustomer));
        page.setTotal(1);

        when(customerService.findPage(1, 20, null)).thenReturn(page);

        mockMvc.perform(get("/api/customer")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].customerCode").value("C001"));
    }

    /**
     * 测试分页查询客户列表 - 带关键字
     */
    @Test
    @DisplayName("分页查询客户列表 - 带关键字")
    void testListCustomers_WithKeyword() throws Exception {
        IPage<Customer> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList(testCustomer));
        page.setTotal(1);

        when(customerService.findPage(1, 20, "测试")).thenReturn(page);

        mockMvc.perform(get("/api/customer")
                        .param("page", "1")
                        .param("size", "20")
                        .param("keyword", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * 测试分页查询客户列表 - 空结果
     */
    @Test
    @DisplayName("分页查询客户列表 - 空结果")
    void testListCustomers_Empty() throws Exception {
        IPage<Customer> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);

        when(customerService.findPage(1, 20, null)).thenReturn(page);

        mockMvc.perform(get("/api/customer")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    /**
     * 测试分页查询客户列表 - 默认分页参数
     */
    @Test
    @DisplayName("分页查询客户列表 - 默认分页参数")
    void testListCustomers_DefaultPagination() throws Exception {
        IPage<Customer> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList(testCustomer));
        page.setTotal(1);

        when(customerService.findPage(1, 20, null)).thenReturn(page);

        mockMvc.perform(get("/api/customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 创建测试客户实体
     */
    private Customer createTestCustomer() {
        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setCustomerCode("C001");
        customer.setCustomerName("测试客户");
        customer.setShortName("测客");
        customer.setCustomerType("企业客户");
        customer.setCustomerLevel("普通客户");
        customer.setTaxNo("91110000123456789X");
        customer.setContactPerson("张三");
        customer.setContactPhone("13800138000");
        customer.setEmail("test@example.com");
        customer.setAddress("北京市朝阳区测试路1号");
        customer.setBankName("中国工商银行");
        customer.setBankAccount("6222021234567890");
        customer.setCreditLimit(new BigDecimal("100000.00"));
        customer.setStatus("正常");
        customer.setCreatedAt(LocalDateTime.now());
        customer.setCreatedBy("admin");
        customer.setDeleted(0);
        return customer;
    }

    /**
     * 创建测试客户DTO
     */
    private CustomerController.CustomerDTO createTestCustomerDTO() {
        CustomerController.CustomerDTO dto = new CustomerController.CustomerDTO();
        dto.setCustomerCode("C001");
        dto.setCustomerName("测试客户");
        dto.setShortName("测客");
        dto.setCustomerType("企业客户");
        dto.setCustomerLevel("普通客户");
        dto.setTaxNo("91110000123456789X");
        dto.setContactPerson("张三");
        dto.setContactPhone("13800138000");
        dto.setEmail("test@example.com");
        dto.setAddress("北京市朝阳区测试路1号");
        dto.setBankName("中国工商银行");
        dto.setBankAccount("6222021234567890");
        dto.setCreditLimit(new BigDecimal("100000.00"));
        return dto;
    }
}
