package com.erp.version.annotation;

import java.lang.annotation.*;

/**
 * 版本日志注解 - 标注需要记录版本的方法
 * 用在 BaseCrudController 的 save/update/delete 等方法上,
 * 由 VersionLogAspect 切面自动记录前后快照
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VersionLog {

    /** 业务模块: basedata/sale/purchase/inventory/production/crm/finance/sample */
    String module() default "basedata";

    /** 操作类型: INSERT/UPDATE/DELETE */
    String type() default "UPDATE";

    /** 表名(留空则从实体类 @TableName 自动推断) */
    String tableName() default "";

    /** 是否允许回滚 */
    boolean rollbackEnabled() default true;

    /** 备注 */
    String remark() default "";
}
