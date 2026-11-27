package com.erp.finance.credit.aspect;

import com.erp.config.BusinessException;
import com.erp.finance.credit.annotation.CreditCheck;
import com.erp.finance.credit.dto.CreditCheckResult;
import com.erp.finance.credit.service.CreditLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CreditCheckAspect {

    private final CreditLimitService creditLimitService;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(creditCheck)")
    public Object around(ProceedingJoinPoint pjp, CreditCheck creditCheck) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = signature.getParameterNames();
        Object[] args = pjp.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        Long customerId = evaluateLong(context, creditCheck.customerIdExpr());
        BigDecimal amount = evaluateBigDecimal(context, creditCheck.amountExpr());

        if (customerId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return pjp.proceed();
        }

        CreditCheckResult result = creditLimitService.checkCreditLimit(customerId, amount);

        if (!result.isPass()) {
            throw new BusinessException("客户信誉额度不足，可用额度: " + result.getAvailableAmount());
        }

        if (result.isWarning()) {
            log.warn("客户信誉额度预警: customerId={}, usedAmount={}, availableAmount={}",
                    customerId, result.getUsedAmount(), result.getAvailableAmount());
        }

        return pjp.proceed();
    }

    private Long evaluateLong(EvaluationContext context, String expr) {
        try {
            Expression expression = parser.parseExpression(expr);
            Object value = expression.getValue(context);
            if (value == null) return null;
            if (value instanceof Number n) return n.longValue();
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            log.warn("解析SpEL表达式失败: {}", expr, e);
            return null;
        }
    }

    private BigDecimal evaluateBigDecimal(EvaluationContext context, String expr) {
        try {
            Expression expression = parser.parseExpression(expr);
            Object value = expression.getValue(context);
            if (value == null) return null;
            if (value instanceof BigDecimal bd) return bd;
            if (value instanceof Number n) return new BigDecimal(n.toString());
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            log.warn("解析SpEL表达式失败: {}", expr, e);
            return null;
        }
    }
}
