package com.erp.common.bill.link;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【2026-10-31 全模块下推守卫】标在下推接口上，进入业务前统一校验目标是否已全部下推。
 * sourceType/targetType 使用 BillLinkRegistry 的单据类型编码（如 SALE_ORDER / SALE_DELIVERY）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PushGuard {
    String sourceType();
    String targetType();
}
