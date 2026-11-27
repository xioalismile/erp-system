package com.erp.common.bill;

import com.erp.basedata.service.UnitConversionService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;

/**
 * 单据明细多单位折算（2026-11-10 最终实现）。
 *
 * <p>为什么不用 MyBatis-Plus 的 MetaObjectHandler：实测在"值先为空、由填充处理器赋值"的场景下，
 * 实体与 MetaObject 都已更新，但 INSERT 绑定仍按插入前的空值落库（复现：meta.base=0.2000 而库里为 null）。
 * 改为在 Mapper 的 insert / updateById 执行前直接改实体，保证绑定取到折算结果。</p>
 *
 * <p>覆盖全部继承 {@link BillItemUnitFields} 的明细实体（销售/采购/库存/生产/委外/打样/组装拆卸），
 * 各业务代码零改动；未启用辅计量的行 rate=1、baseQuantity=quantity，与旧版完全一致。</p>
 */
@Slf4j
@Aspect
@Component
public class BillItemUnitFillAspect {

    @Lazy
    @Autowired
    private UnitConversionService unitConversionService;

    @Around("execution(* com.erp..mapper..*.insert(..)) || execution(* com.erp..mapper..*.updateById(..))")
    public Object fillBeforeWrite(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] instanceof BillItemUnitFields item) {
            try {
                // 物料 / 数量字段名各单据不同（2026-11-10 全单据验收发现）：
                // 采购申请=applyQty、生产工单用料=requiredQuantity、库存盘点=checkQuantity、打样子件=childMaterialId+baseQty，
                // 只认 materialId/quantity 会让这些单据折算不出基本单位数量（换算率有值、基本数量为空）。
                Long materialId = firstLong(item, "materialId", "childMaterialId", "productId");
                BigDecimal quantity = firstDecimal(item, "quantity", "applyQty", "requiredQuantity",
                        "checkQuantity", "baseQty", "sampleQty", "checkQty");
                Long unitId = asLong(read(item, "unitId"));
                String unitName = asString(read(item, "unit"));
                if (unitName == null || unitName.isBlank()) unitName = asString(read(item, "unitName"));
                if (materialId != null) {
                    unitConversionService.normalize(item, materialId, quantity, unitId, unitName);
                }
            } catch (Exception e) {
                // 折算失败绝不阻断业务落库（按旧口径保存）
                log.warn("[多计量单位] 落库前折算跳过: {}", e.getMessage());
            }
        }
        return pjp.proceed();
    }

    private Object read(Object target, String field) {
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    /** 按候选字段名依次取值，命中第一个非空值（字段名各单据不同，见 normalize 前注释） */
    private Long firstLong(Object target, String... fields) {
        for (String f : fields) {
            Long v = asLong(read(target, f));
            if (v != null) return v;
        }
        return null;
    }

    private BigDecimal firstDecimal(Object target, String... fields) {
        for (String f : fields) {
            BigDecimal v = asDecimal(read(target, f));
            if (v != null) return v;
        }
        return null;
    }

    private BigDecimal asDecimal(Object v) {
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return null;
    }

    private String asString(Object v) {
        return v instanceof String s ? s : null;
    }
}
