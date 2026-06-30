package com.erp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = DbType.POSTGRE_SQL;
        if (datasourceUrl.contains(":h2:")) {
            dbType = DbType.H2;
        } else if (datasourceUrl.contains(":postgresql:")) {
            dbType = DbType.POSTGRE_SQL;
        } else if (datasourceUrl.contains(":mysql:")) {
            dbType = DbType.MYSQL;
        }
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }
}
