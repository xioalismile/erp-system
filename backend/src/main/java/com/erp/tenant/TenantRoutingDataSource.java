package com.erp.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    private static final Logger log = LoggerFactory.getLogger(TenantRoutingDataSource.class);
    private static final String DEFAULT_DS_KEY = "default";
    private static final String PLATFORM_DS_KEY = "platform";

    private final Map<Object, Object> dynamicDataSources = new ConcurrentHashMap<>();
    private String defaultDbUrl;
    private String defaultDbUsername;
    private String defaultDbPassword;
    private String defaultDbDriver;

    @Override
    protected Object determineCurrentLookupKey() {
        String dbName = TenantContextHolder.getTenantDbName();
        if (dbName != null && dynamicDataSources.containsKey(dbName)) {
            return dbName;
        }
        return DEFAULT_DS_KEY;
    }

    public void registerTenantDataSource(String dbName, String dbHost, int dbPort,
                                          String dbUsername, String dbPassword) {
        if (dynamicDataSources.containsKey(dbName)) {
            log.debug("数据源已存在: {}", dbName);
            return;
        }
        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(dbUsername);
        ds.setPassword(dbPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30000);
        ds.setPoolName("tenant-" + dbName);
        dynamicDataSources.put(dbName, ds);
        super.setTargetDataSources(dynamicDataSources);
        super.afterPropertiesSet();
        log.info("注册租户数据源: {} -> {}", dbName, url);
    }

    public void setDefaultDataSource(DataSource defaultDataSource) {
        dynamicDataSources.put(DEFAULT_DS_KEY, defaultDataSource);
        super.setDefaultTargetDataSource(defaultDataSource);
        super.setTargetDataSources(dynamicDataSources);
        super.afterPropertiesSet();
    }

    public void setPlatformDataSource(DataSource platformDataSource) {
        dynamicDataSources.put(PLATFORM_DS_KEY, platformDataSource);
        super.setTargetDataSources(dynamicDataSources);
        super.afterPropertiesSet();
    }

    public DataSource getPlatformDataSource() {
        return (DataSource) dynamicDataSources.get(PLATFORM_DS_KEY);
    }

    public boolean hasDataSource(String dbName) {
        return dynamicDataSources.containsKey(dbName);
    }

    public Map<Object, Object> getAllDataSources() {
        return dynamicDataSources;
    }
}
