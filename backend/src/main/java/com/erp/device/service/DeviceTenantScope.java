package com.erp.device.service;

import com.erp.tenant.TenantContextHolder;
import com.erp.tenant.TenantRoutingDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 设备上报的租户上下文。
 *
 * 设备上报是公开入口（不走登录 Token），必须从凭证号里解析租户，再手工切换到对应租户库：
 * 凭证号格式 DEVC-{tenantId}-{random}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTenantScope {

    @Qualifier("platformJdbcTemplate")
    private final JdbcTemplate platformJdbcTemplate;

    private final TenantRoutingDataSource routingDataSource;

    public <T> T runInTenant(Long tenantId, Supplier<T> action) {
        Long prevId = TenantContextHolder.getTenantId();
        String prevDb = TenantContextHolder.getTenantDbName();
        try {
            TenantContextHolder.setTenantId(tenantId);
            ensureDataSource(tenantId);
            return action.get();
        } finally {
            if (prevId != null) {
                TenantContextHolder.setTenantId(prevId);
            }
            if (prevDb != null) {
                TenantContextHolder.setTenantDbName(prevDb);
            }
            if (prevId == null && prevDb == null) {
                TenantContextHolder.clear();
            }
        }
    }

    private void ensureDataSource(Long tenantId) {
        try {
            List<Map<String, Object>> tenants = platformJdbcTemplate.queryForList(
                    "SELECT db_name, db_host, db_port, db_username, db_password FROM sys_tenant "
                            + "WHERE tenant_id = ? AND status = 'active'", tenantId);
            if (tenants.isEmpty()) {
                log.warn("[设备上报] 平台库未找到 active 租户，使用默认库: tenantId={}", tenantId);
                return;
            }
            Map<String, Object> t = tenants.get(0);
            String dbName = (String) t.get("db_name");
            String dbHost = (String) t.get("db_host");
            int dbPort = (t.get("db_port") instanceof Number) ? ((Number) t.get("db_port")).intValue() : 5432;
            String dbUser = (String) t.get("db_username");
            String dbPass = (String) t.get("db_password");
            TenantContextHolder.setTenantDbName(dbName);
            if (!routingDataSource.hasDataSource(dbName)) {
                routingDataSource.registerTenantDataSource(dbName, dbHost, dbPort, dbUser, dbPass);
            }
        } catch (Exception e) {
            log.error("[设备上报] 加载租户数据源失败: tenantId={}", tenantId, e);
        }
    }

    /** 从凭证号解析租户：DEVC-{tenantId}-xxx */
    public Long parseTenantId(String credentialNo) {
        if (credentialNo == null) return null;
        String[] parts = credentialNo.split("-");
        if (parts.length < 3) return null;
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
