package com.erp.tenant;

import com.erp.auth.util.JwtUtil;
import com.erp.tenant.support.TenantExpiry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 租户上下文解析器（多租户隔离的**单一入口**，幂等可重复调用）
 *
 * ============ 为什么需要独立解析器（2026-09-20 线上定位）============
 * 原先只有 `TenantInterceptor` 负责按 JWT 的 tenantId 切换数据源。线上实测发现：
 * 鉴权拦截器（AuthInterceptor）在它之前执行，于是角色查询落到主库 erp_db，
 * 取到主库里同 user_id 的**其它账号**角色（实测拿到 SALES_REP）→ 租户管理员被判为普通用户
 * → 建/删用户一律 403；更严重的是"切库前的一切查询都跑在主库"，隔离在请求层面失效。
 *
 * 处置（双保险，不依赖任何执行顺序）：
 *   ① `TenantContextFilter`（Servlet Filter，规范保证早于 DispatcherServlet）负责尽早建立；
 *   ② 鉴权拦截器在读取角色前也调用一次本解析器（幂等：已建立则直接返回）。
 * 这样无论容器/过滤器链顺序如何变化，**鉴权时的租户上下文一定是正确的**。
 *
 * 同时，凡是需要"按当前租户查库"的地方都应经由此处，避免再次出现"查错库"。
 */
@Component
@RequiredArgsConstructor
public class TenantContextResolver {

    private static final Logger log = LoggerFactory.getLogger(TenantContextResolver.class);

    private final JwtUtil jwtUtil;
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantRoutingDataSource routingDataSource;

    /** 请求级标记：当前请求所属客户已到期（由 Filter 读取后阻断，值为 Boolean.TRUE） */
    public static final String TENANT_EXPIRED_ATTR = "tenantExpired";

    /** 无需租户上下文的路径（登录/选租户阶段本身还没有 tenantId） */
    private static boolean isTenantFree(String path) {
        return path != null && (path.contains("/api/auth/login") || path.contains("/api/auth/wx-login")
                || path.contains("/api/ai/public") || path.contains("/api/tenant/select"));
    }

    /**
     * 解析并建立当前请求的租户上下文（幂等）。
     *
     * @return true 表示本次调用真正建立了上下文；false 表示已存在或无需建立
     */
    public boolean resolve(HttpServletRequest request) {
        if (TenantContextHolder.getTenantDbName() != null) {
            return false;
        }
        String path = request.getRequestURI();
        if (isTenantFree(path)) {
            return false;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        try {
            Long tenantId = jwtUtil.parseTenantId(header.substring(7));
            if (tenantId == null) {
                return false;
            }
            TenantContextHolder.setTenantId(tenantId);
            ensureDataSourceForTenant(tenantId, request);
            log.info("[租户上下文] path={}, tenantId={}, 切换后库={}",
                    path, tenantId, TenantContextHolder.getTenantDbName());
            return true;
        } catch (Exception e) {
            log.warn("租户上下文建立失败(将使用默认库): path={}, err={}", path, e.getMessage());
            return false;
        }
    }

    /**
     * 【2026-11-25】按租户ID直接建立上下文（公开分享链接用）。
     *
     * <p>公开链接没有登录令牌，租户由分享令牌携带；此处复用登录态同一套切库与到期判定。
     * 仅供服务端已校验过的令牌调用，禁止接受前端任意传参。</p>
     */
    public boolean openTenantContext(Long tenantId, HttpServletRequest request) {
        if (tenantId == null) {
            return false;
        }
        if (TenantContextHolder.getTenantDbName() != null) {
            return false;
        }
        TenantContextHolder.setTenantId(tenantId);
        ensureDataSourceForTenant(tenantId, request);
        log.info("[租户上下文·公开链接] tenantId={}, 切换后库={}",
                tenantId, TenantContextHolder.getTenantDbName());
        return true;
    }

    /** 按租户登记信息（平台库 sys_tenant）切换数据源；查不到则保持默认库（不阻断请求） */
    private void ensureDataSourceForTenant(Long tenantId, HttpServletRequest request) {
        try {
            List<Map<String, Object>> tenants = platformJdbcTemplate.queryForList(
                    "SELECT tenant_code, db_name, db_host, db_port, db_username, db_password, expire_date FROM sys_tenant "
                            + "WHERE tenant_id = ? AND status = 'active'", tenantId);
            if (tenants.isEmpty()) {
                log.warn("[租户上下文] sys_tenant 未找到 active 租户，将使用默认库: tenantId={}", tenantId);
                return;
            }
            Map<String, Object> t = tenants.get(0);
            // 到期标记（2026-11-09）：此处只打标记、照常切库。切库必须先于鉴权完成，
            // 否则角色查询会落到主库取到别人的角色（守则 10 已记录的线上事故）。
            // 真正阻断由 TenantContextFilter 读本标记后返回 403。
            boolean expired = TenantExpiry.isExpired(
                    t.get("tenant_code") == null ? null : String.valueOf(t.get("tenant_code")),
                    t.get("expire_date"));
            if (expired) {
                if (request != null) {
                    request.setAttribute(TENANT_EXPIRED_ATTR, Boolean.TRUE);
                }
                log.warn("[租户到期] tenantId={}, tenantCode={}, expireDate={}，本次请求将被阻断",
                        tenantId, t.get("tenant_code"), t.get("expire_date"));
            }
            String dbName = (String) t.get("db_name");
            String dbHost = (String) t.get("db_host");
            // 防御：db_port 为 NULL 时 (Number)null.intValue() 会抛 NPE，从而中断上下文建立
            int dbPort = (t.get("db_port") instanceof Number) ? ((Number) t.get("db_port")).intValue() : 5432;
            String dbUsername = (String) t.get("db_username");
            String dbPassword = (String) t.get("db_password");

            TenantContextHolder.setTenantDbName(dbName);
            if (!routingDataSource.hasDataSource(dbName)) {
                routingDataSource.registerTenantDataSource(dbName, dbHost, dbPort, dbUsername, dbPassword);
            }
        } catch (Exception e) {
            log.error("加载租户数据源失败: tenantId={}", tenantId, e);
        }
    }
}
