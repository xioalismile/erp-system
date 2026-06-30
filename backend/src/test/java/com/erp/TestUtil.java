package com.erp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试工具类
 */
public class TestUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 获取ObjectMapper实例
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 创建分页请求
     */
    public static <T> Page<T> createPage(int page, int size) {
        return new Page<>(page, size);
    }

    /**
     * 创建查询条件
     */
    public static <T> QueryWrapper<T> createQueryWrapper() {
        return new QueryWrapper<>();
    }

    /**
     * 生成测试客户编码
     */
    public static String generateCustomerCode() {
        return "C" + System.currentTimeMillis();
    }

    /**
     * 生成测试手机号
     */
    public static String generatePhone() {
        return "1" + (int)(Math.random() * 9 + 1) + String.format("%09d", (int)(Math.random() * 1000000000));
    }

    /**
     * 生成测试邮箱
     */
    public static String generateEmail() {
        return "test" + System.currentTimeMillis() + "@example.com";
    }

    /**
     * 生成测试金额
     */
    public static BigDecimal generateAmount() {
        return new BigDecimal(Math.random() * 100000).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 验证成功响应
     */
    public static <T> boolean isSuccessResult(Result<T> result) {
        return result != null && result.getCode() == 200;
    }

    /**
     * 验证失败响应
     */
    public static <T> boolean isErrorResult(Result<T> result) {
        return result != null && result.getCode() != 200;
    }
}
