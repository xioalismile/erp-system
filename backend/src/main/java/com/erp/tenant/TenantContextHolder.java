package com.erp.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantContextHolder {
    private static final Logger log = LoggerFactory.getLogger(TenantContextHolder.class);
    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> CONTEXT_DB = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CONTEXT.set(tenantId);
        log.debug("设置租户上下文: tenantId={}", tenantId);
    }

    public static Long getTenantId() {
        return CONTEXT.get();
    }

    public static void setTenantDbName(String dbName) {
        CONTEXT_DB.set(dbName);
    }

    public static String getTenantDbName() {
        return CONTEXT_DB.get();
    }

    /**
     * 进程内缓存的租户前缀。
     * 同一 JVM 服务所有租户，Caffeine 缓存必须带租户维度，否则 A 租户的配置会被 B 租户读到。
     * 无租户上下文时（启动、平台管理接口、未传 token）返回 default，与数据源默认池一致。
     */
    public static String cachePrefix() {
        Long tenantId = CONTEXT.get();
        if (tenantId != null) {
            return "t" + tenantId;
        }
        String dbName = CONTEXT_DB.get();
        if (dbName != null) {
            return "t:" + dbName;
        }
        return "default";
    }

    /** 带分隔符的缓存 key 前缀，例如 t1: */
    public static String cacheKeyPrefix() {
        return cachePrefix() + ":";
    }

    public static void clear() {
        CONTEXT.remove();
        CONTEXT_DB.remove();
    }
}
