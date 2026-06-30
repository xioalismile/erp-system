package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.mapper.CustomerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CustomerService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("客户Service层单元测试")
class CustomerServiceTest {

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = createTestCustomer();
    }

    /**
     * 测试分页查询 - 正常查询
     */
    @Test
    @DisplayName("分页查询 - 正常查询")
    void testFindPage_Success() {
        // 准备测试数据
        List<Customer> customers = Arrays.asList(testCustomer);
        Page<Customer> page = new Page<>(1, 20);
        page.setRecords(customers);
        page.setTotal(1);

        when(customerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(page);

        // 执行测试
        IPage<Customer> result = customerService.findPage(1, 20, null);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("C001", result.getRecords().get(0).getCustomerCode());

        // 验证Mapper调用
        verify(customerMapper, times(1)).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    /**
     * 测试分页查询 - 带关键字查询
     */
    @Test
    @DisplayName("分页查询 - 带关键字查询")
    void testFindPage_WithKeyword() {
        List<Customer> customers = Arrays.asList(testCustomer);
        Page<Customer> page = new Page<>(1, 20);
        page.setRecords(customers);
        page.setTotal(1);

        when(customerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(page);

        IPage<Customer> result = customerService.findPage(1, 20, "测试");

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        verify(customerMapper, times(1)).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    /**
     * 测试根据编码查询客户
     */
    @Test
    @DisplayName("根据编码查询客户 - 存在")
    void testFindByCustomerCode_Exists() {
        when(customerMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(testCustomer);

        Customer result = customerService.findByCustomerCode("C001");

        assertNotNull(result);
        assertEquals("C001", result.getCustomerCode());
        assertEquals("测试客户", result.getCustomerName());

        verify(customerMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    /**
     * 测试根据编码查询客户 - 不存在
     */
    @Test
    @DisplayName("根据编码查询客户 - 不存在")
    void testFindByCustomerCode_NotExists() {
        when(customerMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        Customer result = customerService.findByCustomerCode("C999");

        assertNull(result);
        verify(customerMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    /**
     * 测试编码是否存在 - 存在
     */
    @Test
    @DisplayName("编码是否存在 - 存在返回true")
    void testExistsByCustomerCode_Exists() {
        when(customerMapper.exists(any(QueryWrapper.class)))
                .thenReturn(true);

        boolean result = customerService.existsByCustomerCode("C001");

        assertTrue(result);
        verify(customerMapper, times(1)).exists(any(QueryWrapper.class));
    }

    /**
     * 测试编码是否存在 - 不存在
     */
    @Test
    @DisplayName("编码是否存在 - 不存在返回false")
    void testExistsByCustomerCode_NotExists() {
        when(customerMapper.exists(any(QueryWrapper.class)))
                .thenReturn(false);

        boolean result = customerService.existsByCustomerCode("C999");

        assertFalse(result);
        verify(customerMapper, times(1)).exists(any(QueryWrapper.class));
    }

    /**
     * 测试排除ID的编码唯一性校验 - 存在相同编码的不同记录
     */
    @Test
    @DisplayName("排除ID的编码唯一性校验 - 发现重复编码")
    void testExistsByCustomerCodeExcludeId_FoundDuplicate() {
        when(customerMapper.exists(any(QueryWrapper.class)))
                .thenReturn(true);

        boolean result = customerService.existsByCustomerCodeExcludeId("C001", 2L);

        assertTrue(result);
        verify(customerMapper, times(1)).exists(any(QueryWrapper.class));
    }

    /**
     * 测试排除ID的编码唯一性校验 - 无重复
     */
    @Test
    @DisplayName("排除ID的编码唯一性校验 - 无重复")
    void testExistsByCustomerCodeExcludeId_NoDuplicate() {
        when(customerMapper.exists(any(QueryWrapper.class)))
                .thenReturn(false);

        boolean result = customerService.existsByCustomerCodeExcludeId("C002", 1L);

        assertFalse(result);
        verify(customerMapper, times(1)).exists(any(QueryWrapper.class));
    }

    /**
     * 测试分页参数边界值 - 第一页
     */
    @Test
    @DisplayName("分页参数边界值 - 第一页")
    void testFindPage_FirstPage() {
        Page<Customer> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList(testCustomer));
        page.setTotal(1);

        when(customerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(page);

        IPage<Customer> result = customerService.findPage(1, 20, null);

        assertEquals(1, result.getCurrent());
        assertEquals(20, result.getSize());
    }

    /**
     * 测试分页参数边界值 - 空数据
     */
    @Test
    @DisplayName("分页参数边界值 - 空数据")
    void testFindPage_EmptyData() {
        Page<Customer> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList());
        page.setTotal(0);

        when(customerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(page);

        IPage<Customer> result = customerService.findPage(1, 20, null);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    /**
     * 创建测试客户数据
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
}
