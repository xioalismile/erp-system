package com.erp.tenant.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 租户库基础主档种子 · 币种（2026-10-12，121/本地双实锤）。
 *
 * <p><b>背景</b>：租户模板库/租户库的币种表为空（本地模板库、新租户、存量租户实测全 0 行），
 * 而「银行账户」必填币种、单据默认币种取"基础币种优先"——新租户开箱即卡：
 * 建银行账户报"必填字段[币种]不能为空"。主库币种一直正常，又一处"本地没问题、
 * 模板库/租户库缺种子"的同源问题。</p>
 *
 * <p><b>本类定位</b>：常用币种唯一种子清单（与编号规则收口同思路）——
 * 主库 / 逐租户库 / 租户模板库 / 建租户克隆后，按 <b>currency_code</b> "缺则补、有则不动"。
 * CNY 一律 is_base_currency=1（基础币种）；已有同名币种绝不 UPDATE（不碰租户已改过的汇率/名称）。</p>
 */
public final class BaseDataDefaults {

    private BaseDataDefaults() {
    }

    /** 常用币种：{code, name, symbol, isBase, exchangeRate} */
    private static final Object[][] BASE_CURRENCIES = {
            {"CNY", "人民币", "¥", 1, new java.math.BigDecimal("1.000000")},
            {"USD", "美元", "$", 0, new java.math.BigDecimal("7.250000")},
            {"EUR", "欧元", "€", 0, new java.math.BigDecimal("7.850000")},
            {"HKD", "港币", "HK$", 0, new java.math.BigDecimal("0.930000")},
            {"JPY", "日元", "¥", 0, new java.math.BigDecimal("0.046000")},
    };

    /**
     * 对指定库幂等补齐基础币种（缺则补、有则不动）。
     *
     * @return 本次实际新增的币种条数；表不存在等异常由调用方决定是否吞掉
     */
    public static int ensureCurrenciesOn(JdbcTemplate jdbc) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (java.util.Map<String, Object> row : jdbc.queryForList(
                "SELECT currency_code FROM currency WHERE currency_code IS NOT NULL")) {
            Object v = row.get("currency_code");
            if (v != null) {
                existing.add(String.valueOf(v).trim().toUpperCase());
            }
        }
        int added = 0;
        for (Object[] c : BASE_CURRENCIES) {
            String code = (String) c[0];
            if (existing.contains(code)) {
                continue; // 有则不动
            }
            added += jdbc.update(
                    "INSERT INTO currency (currency_code, currency_name, currency_symbol, "
                  + "is_base_currency, exchange_rate, status, is_default, is_enabled) "
                  + "VALUES (?, ?, ?, ?, ?, '启用', 0, 1)",
                    code, c[1], c[2], c[3], c[4]);
        }
        if (added > 0) {
            // 修正自增序列（显式/批量插入后保证 nextval 不撞主键）
            jdbc.queryForObject(
                    "SELECT setval(pg_get_serial_sequence('currency','currency_id'), "
                  + "(SELECT COALESCE(MAX(currency_id),1) FROM currency))", Long.class);
        }
        return added;
    }
}
