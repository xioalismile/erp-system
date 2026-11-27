package com.erp.common.bill.link;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 【2026-10-31 全模块下推守卫】切面：所有标了 {@link PushGuard} 的下推接口，
 * 在进入业务方法前按“来源单据主键（第一个 Long 参数）”检查目标是否已全部下推。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class PushGuardAspect {

    private final PushGuardService pushGuardService;

    @Before("@annotation(pushGuard)")
    public void check(JoinPoint joinPoint, PushGuard pushGuard) {
        Long sourceId = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long) {
                sourceId = (Long) arg;
                break;
            }
            if (arg instanceof Integer) {
                sourceId = ((Integer) arg).longValue();
                break;
            }
        }
        if (sourceId != null) {
            pushGuardService.assertCanPush(pushGuard.sourceType(), sourceId, pushGuard.targetType());
        }
    }
}
