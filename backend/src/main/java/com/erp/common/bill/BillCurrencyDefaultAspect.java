package com.erp.common.bill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.mapper.CurrencyMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 单据币种默认值（2026-11-12）。
 *
 * <p>用户口径：「单据币种默认人民币」。实测缺口：界面新建单据会带默认币种，
 * 但**下推生成 / 接口创建 / 复制**的单据币种为空（采购入库、其他入库实锤），
 * 保存时看不出来，一提交就被"请填写必填项：币种"拦住 —— 用户视角就是"默认值没生效"。</p>
 *
 * <p>处理方式：落库前统一兜底 —— 实体只要有 currencyId 字段且为空，就填本位币。
 * 覆盖所有创建路径（界面/下推/接口/导入/复制），各业务代码零改动；
 * 与 {@link BillItemUnitFillAspect} 同一套做法（在 Mapper 写库前直接改实体，保证绑定取到值）。</p>
 *
 * <p>不缓存本位币：多租户共用一个切面实例，缓存会把 A 租户的本位币带到 B 租户。</p>
 */
@Slf4j
@Aspect
@Component
public class BillCurrencyDefaultAspect {

    @Lazy
    @Autowired
    private CurrencyMapper currencyMapper;

    @Around("execution(* com.erp..mapper..*.insert(..))")
    public Object fillBeforeInsert(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] != null) {
            try {
                fillCurrency(args[0]);
            } catch (Exception e) {
                // 兜底失败绝不阻断业务落库（保持旧行为：币种留空，由页面必填校验提示）
                log.warn("[默认币种] 落库前兜底跳过: {}", e.getMessage());
            }
        }
        return pjp.proceed();
    }

    private void fillCurrency(Object entity) throws Exception {
        Field f = findField(entity.getClass(), "currencyId");
        if (f == null) return;
        f.setAccessible(true);
        if (f.get(entity) != null) return;
        Long baseId = baseCurrencyId();
        if (baseId != null) f.set(entity, baseId);
    }

    private Field findField(Class<?> c, String name) {
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private Long baseCurrencyId() {
        try {
            List<Currency> list = currencyMapper.selectList(new QueryWrapper<Currency>()
                    .eq("is_base_currency", 1)
                    .orderByAsc("currency_id")
                    .last("limit 1"));
            if (list != null && !list.isEmpty()) return list.get(0).getCurrencyId();
        } catch (Exception e) {
            log.warn("[默认币种] 读取本位币失败: {}", e.getMessage());
        }
        return null;
    }
}
