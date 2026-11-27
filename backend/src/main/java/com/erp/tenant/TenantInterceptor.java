package com.erp.tenant;

import com.erp.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    private final JwtUtil jwtUtil;
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantRoutingDataSource routingDataSource;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.contains("/api/auth/login") || path.contains("/api/auth/wx-login") 
            || path.contains("/api/ai/public") || path.contains("/api/tenant/select")) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Long tenantId = jwtUtil.parseTenantId(token);
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                    // 查询租户对应的数据库信息并切换数据源
                    ensureDataSourceForTenant(tenantId);
                }
                // 诊断留痕：一次看清"拦截器是否进来 / tenantId 是否解析到 / 库是否切成功"。
                // 多租户隔离的基础就是这一步；若 切换后库=null，后续所有查询都会落到主库 erp_db。
                log.info("[租户诊断] path={}, tokenTenantId={}, 切换后库={}",
                        path, tenantId, TenantContextHolder.getTenantDbName());
                return true;
            } catch (Exception e) {
                log.warn("解析租户信息失败: {}", e.getMessage());
            }
        } else {
            log.info("[租户诊断] path={}, 无 Bearer token（将使用默认库）", path);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    private void ensureDataSourceForTenant(Long tenantId) {
        try {
            List<Map<String, Object>> tenants = platformJdbcTemplate.queryForList(
                "SELECT db_name, db_host, db_port, db_username, db_password FROM sys_tenant WHERE tenant_id = ? AND status = 'active'",
                tenantId);
            if (!tenants.isEmpty()) {
                Map<String, Object> t = tenants.get(0);
                String dbName = (String) t.get("db_name");
                String dbHost = (String) t.get("db_host");
                // 防御：db_port 若为 NULL，原先 (Number)null.intValue() 会抛 NPE，
                // 而 setTenantDbName 在异常之后 → 租户上下文永远建不起来 → 请求静默落主库。
                int dbPort = (t.get("db_port") instanceof Number) ? ((Number) t.get("db_port")).intValue() : 5432;
                String dbUsername = (String) t.get("db_username");
                String dbPassword = (String) t.get("db_password");

                TenantContextHolder.setTenantDbName(dbName);

                if (!routingDataSource.hasDataSource(dbName)) {
                    routingDataSource.registerTenantDataSource(dbName, dbHost, dbPort, dbUsername, dbPassword);
                }
                log.info("[租户诊断] 数据源就绪: tenantId={}, dbName={}", tenantId, dbName);
            } else {
                log.warn("[租户诊断] sys_tenant 中未找到 status='active' 的租户，将使用默认库: tenantId={}", tenantId);
            }
        } catch (Exception e) {
            log.error("加载租户数据源失败: tenantId={}", tenantId, e);
        }
    }
}
