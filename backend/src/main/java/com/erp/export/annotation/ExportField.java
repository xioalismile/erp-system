package com.erp.export.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExportField {

    String label();

    int order() default 0;

    boolean required() default false;

    String dateFormat() default "yyyy-MM-dd";

    String numberFormat() default "";

    boolean hidden() default false;
}