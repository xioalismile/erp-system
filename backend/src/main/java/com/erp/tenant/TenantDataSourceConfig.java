package com.erp.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class TenantDataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceConfig.class);

    @Value("${erp.datasource.platform.url:jdbc:postgresql://localhost:5432/erp_platform}")
    private String platformDbUrl;

    @Value("${erp.datasource.platform.username:erp_user}")
    private String platformDbUsername;

    @Value("${erp.datasource.platform.password:Erp@2026}")
    private String platformDbPassword;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/erp_db}")
    private String defaultDbUrl;

    @Value("${spring.datasource.username:erp_user}")
    private String defaultDbUsername;

    @Value("${spring.datasource.password:Erp@2026}")
    private String defaultDbPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("初始化多租户动态数据源...");
        
        // 平台数据源（连接erp_platform库）
        HikariDataSource platformDs = new HikariDataSource();
        platformDs.setJdbcUrl(platformDbUrl);
        platformDs.setUsername(platformDbUsername);
        platformDs.setPassword(platformDbPassword);
        platformDs.setDriverClassName("org.postgresql.Driver");
        platformDs.setMaximumPoolSize(5);
        platformDs.setMinimumIdle(1);
        platformDs.setPoolName("platform");
        log.info("平台数据源: {}", platformDbUrl);

        // 默认业务数据源（连接erp_db库）
        HikariDataSource defaultDs = new HikariDataSource();
        defaultDs.setJdbcUrl(defaultDbUrl);
        defaultDs.setUsername(defaultDbUsername);
        defaultDs.setPassword(defaultDbPassword);
        defaultDs.setDriverClassName("org.postgresql.Driver");
        defaultDs.setMaximumPoolSize(10);
        defaultDs.setMinimumIdle(2);
        defaultDs.setPoolName("default-tenant");
        log.info("默认业务数据源: {}", defaultDbUrl);

        TenantRoutingDataSource routingDs = new TenantRoutingDataSource();
        routingDs.setDefaultDataSource(defaultDs);
        routingDs.setPlatformDataSource(platformDs);
        return routingDs;
    }

    /**
     * 主 JdbcTemplate（业务库，走 TenantRoutingDataSource 默认路由 erp_db）。
     * 必须显式定义：platformJdbcTemplate 的存在会抑制 Spring Boot 的
     * JdbcTemplate 自动配置（@ConditionalOnMissingBean(JdbcOperations)），
     * 导致所有按类型注入 JdbcTemplate 的业务类错误拿到平台库连接
     * （曾引发 /api/auth/userinfo 查业务用户 500、销售角色看到全部菜单）。
     *
     * 注意：此处不使用 @Primary，改为按参数名区分 bean：
     *   - 参数名 jdbcTemplate     → 绑定业务库路由（默认 erp_db）
     *   - 参数名 platformJdbcTemplate → 绑定平台库
     * 加 @Primary 会让 Spring 忽略参数名匹配，把业务库 Bean 错误注入给
     * 平台库需求方（如 TenantController 查询 sys_user_tenant 会失败）。
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 业务库专用 JdbcTemplate，直接连接 erp_db，不经过租户路由。
     * 用于创建租户等需要直接操作业务库的场景。
     */
    @Bean
    public JdbcTemplate businessJdbcTemplate() {
        HikariDataSource businessDs = new HikariDataSource();
        businessDs.setJdbcUrl(defaultDbUrl);
        businessDs.setUsername(defaultDbUsername);
        businessDs.setPassword(defaultDbPassword);
        businessDs.setDriverClassName("org.postgresql.Driver");
        businessDs.setMaximumPoolSize(10);
        businessDs.setMinimumIdle(2);
        businessDs.setConnectionTimeout(30000);
        businessDs.setPoolName("business-direct");
        log.info("业务库专用数据源(直接连接): {}", defaultDbUrl);
        return new JdbcTemplate(businessDs);
    }

    @Bean
    public JdbcTemplate platformJdbcTemplate(DataSource dataSource) {
        TenantRoutingDataSource routingDs = (TenantRoutingDataSource) dataSource;
        return new JdbcTemplate(routingDs.getPlatformDataSource());
    }
}
