package com.erp.finance.credit.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CreditCheck {

    String customerIdExpr();

    String amountExpr();
}
