package com.erp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "erp")
public class ErpProperties {

    private Tenant tenant = new Tenant();

    @Data
    public static class Tenant {
        private String schemaDir = "D:/erp/prodsrc/智能ERP-项目/database/schema";
        private String dbHost = "localhost";
        private String dbPort = "5432";
        // 多租户版本控制开关
        // true: 启用版本控制（平台管理新增的客户按版本限制功能）
        // false: 禁用版本控制（独立部署的客户保持全功能访问）
        private boolean versionControlEnabled = false;
    }

    public boolean isTenantVersionControlEnabled() {
        return tenant.isVersionControlEnabled();
    }
}
