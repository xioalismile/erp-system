package com.erp.tenant.support;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 客户（租户）到期判定 —— 全系统唯一口径。
 *
 * 口径（2026-11-09 定）：
 * 1. 到期日当天仍可用，从次日 0 点起视为已到期；
 * 2. 到期日期为空 = 永久有效，不做限制；
 * 3. 默认公司（tenant_code=DEFAULT）永不参与到期判定 —— 它的到期日期为历史种子值，
 *    若一并拦截会导致整个系统（含平台超管）在某一天集体锁死、无法续费。
 *
 * 所有判定点（登录、选公司、公司列表、请求拦截、客户管理列表）都必须调用本类，
 * 禁止各处自行写日期比较，避免口径漂移。
 */
public final class TenantExpiry {

    private TenantExpiry() {
    }

    /** 默认公司编码：永不到期 */
    private static final String DEFAULT_TENANT_CODE = "DEFAULT";

    /**
     * 是否已到期。
     *
     * @param tenantCode 客户编码（DEFAULT 直接视为未到期）
     * @param expireDate 到期日期，支持 java.sql.Date / java.util.Date / LocalDate / 字符串（yyyy-MM-dd 前缀）
     */
    public static boolean isExpired(String tenantCode, Object expireDate) {
        if (tenantCode != null && DEFAULT_TENANT_CODE.equalsIgnoreCase(tenantCode.trim())) {
            return false;
        }
        LocalDate date = toLocalDate(expireDate);
        if (date == null) {
            return false;
        }
        return date.isBefore(LocalDate.now());
    }

    /** 把各种日期载体统一转成本地日期；无法识别时返回 null（按"未设置到期日=永久有效"处理） */
    public static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = value.toString().trim();
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10));
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }
}
