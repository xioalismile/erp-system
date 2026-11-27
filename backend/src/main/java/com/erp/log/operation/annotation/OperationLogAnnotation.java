package com.erp.log.operation.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLogAnnotation {

    String module() default "";

    String moduleName() default "";

    String operationType() default "";

    String operationName() default "";

    boolean saveRequestParams() default true;

    boolean saveResult() default true;
}
