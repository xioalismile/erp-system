package com.erp.version.aspect;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.controller.BaseCrudController;
import com.erp.version.service.DataVersionRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * 版本日志切面 - 拦截 BaseCrudController 子类的 save/update/delete 方法
 * 自动记录前后快照到 data_version_record 表
 *
 * 模块识别: 通过 Controller 类所在包路径识别
 *   com.erp.basedata   -> basedata
 *   com.erp.sale        -> sale
 *   com.erp.purchase    -> purchase
 *   com.erp.inventory   -> inventory
 *   com.erp.production  -> production
 *   com.erp.crm         -> crm
 *   com.erp.finance     -> finance
 *   com.erp.sample      -> sample
 *   com.erp.masterdata  -> basedata (BOM/物料/客户/供应商归基础资料)
 *   com.erp.system       -> system
 */
@Aspect
@Component
public class VersionLogAspect {

    private static final Logger log = LoggerFactory.getLogger(VersionLogAspect.class);

    private final DataVersionRecordService versionService;
    private final ObjectMapper objectMapper;

    public VersionLogAspect(DataVersionRecordService versionService, ObjectMapper objectMapper) {
        this.versionService = versionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 拦截 BaseCrudController.update() - 记录 UPDATE 版本
     */
    @Around("execution(* com.erp..controller.*Controller.update(..)) && args(entity)")
    public Object aroundUpdate(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        String tableName = resolveTableName(entity);
        String module = resolveModule(pjp);
        Object idVal = readIdValue(entity);
        Object codeVal = readCodeValue(pjp, entity);
        Long recordId = toLong(idVal);

        // 修改前快照
        String beforeJson = null;
        if (recordId != null) {
            try {
                IService<?> service = invokeGetService(pjp.getTarget());
                Object before = service.getById(recordId);
                if (before != null) {
                    beforeJson = objectMapper.writeValueAsString(before);
                }
            } catch (Exception e) {
                log.warn("获取修改前快照失败 table={} id={}: {}", tableName, recordId, e.getMessage());
            }
        }

        Object result = pjp.proceed();

        // 修改后快照
        String afterJson = null;
        if (recordId != null) {
            try {
                IService<?> service = invokeGetService(pjp.getTarget());
                Object after = service.getById(recordId);
                if (after != null) {
                    afterJson = objectMapper.writeValueAsString(after);
                }
            } catch (Exception e) {
                log.warn("获取修改后快照失败 table={} id={}: {}", tableName, recordId, e.getMessage());
            }
        }

        if (recordId != null) {
            versionService.recordVersion(tableName, recordId, toStr(codeVal), module,
                    "UPDATE", beforeJson, afterJson, currentUsername(), "");
        }
        return result;
    }

    /**
     * 拦截 BaseCrudController.save() - 记录 INSERT 版本
     */
    @Around("execution(* com.erp..controller.*Controller.save(..)) && args(entity)")
    public Object aroundSave(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        String tableName = resolveTableName(entity);
        String module = resolveModule(pjp);
        Object codeVal = readCodeValue(pjp, entity);

        Object result = pjp.proceed();

        // save 完后读ID(可能是自增)
        Long recordId = toLong(readIdValue(entity));
        String afterJson = null;
        if (recordId != null) {
            try {
                IService<?> service = invokeGetService(pjp.getTarget());
                Object after = service.getById(recordId);
                if (after != null) {
                    afterJson = objectMapper.writeValueAsString(after);
                }
            } catch (Exception e) {
                log.warn("获取新增后快照失败 table={}: {}", tableName, e.getMessage());
            }
        }

        if (recordId != null) {
            versionService.recordVersion(tableName, recordId, toStr(codeVal), module,
                    "INSERT", null, afterJson, currentUsername(), "新增");
        }
        return result;
    }

    /**
     * 拦截 BaseCrudController.delete(id) - 记录 DELETE 版本
     */
    @Around("execution(* com.erp..controller.*Controller.delete(..)) && args(id)")
    public Object aroundDelete(ProceedingJoinPoint pjp, Serializable id) throws Throwable {
        String module = resolveModule(pjp);
        Object target = pjp.getTarget();
        IService<?> service = invokeGetService(target);
        // 若 Controller 不继承 BaseCrudController, 则不记录版本, 直接执行删除
        if (service == null) {
            return pjp.proceed();
        }
        String tableName = resolveTableNameFromService(service);

        // 删除前快照
        String beforeJson = null;
        Object before = null;
        String recordCode = null;
        try {
            before = service.getById(id);
            if (before != null) {
                beforeJson = objectMapper.writeValueAsString(before);
                recordCode = toStr(readCodeValueFromObject(target, before));
            }
        } catch (Exception e) {
            log.warn("获取删除前快照失败 table={} id={}: {}", tableName, id, e.getMessage());
        }

        Object result = pjp.proceed();

        if (before != null) {
            versionService.recordVersion(tableName, toLong(id), recordCode, module,
                    "DELETE", beforeJson, null, currentUsername(), "删除");
        }
        return result;
    }

    // ===================================================================
    // 辅助方法
    // ===================================================================

    /** 反射调用 Controller.getService() (protected, 跨包需要反射)
     *  若 Controller 不继承 BaseCrudController 则返回 null */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private IService<?> invokeGetService(Object controller) {
        try {
            Method m = controller.getClass().getDeclaredMethod("getService");
            m.setAccessible(true);
            return (IService<?>) m.invoke(controller);
        } catch (NoSuchMethodException e) {
            // Controller 不继承 BaseCrudController, 没有getService方法, 返回null
            return null;
        } catch (Exception e) {
            log.warn("调用 getService 失败 controller={}: {}", controller.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** 从实体类 @TableName 注解解析表名 */
    private String resolveTableName(Object entity) {
        if (entity == null) return "unknown";
        Class<?> clazz = entity.getClass();
        TableName ann = clazz.getAnnotation(TableName.class);
        if (ann != null && !ann.value().isEmpty()) return ann.value();
        return clazz.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /** 通过 Service 实体类解析表名 */
    @SuppressWarnings("rawtypes")
    private String resolveTableNameFromService(IService service) {
        try {
            // 通过反射获取 ServiceImpl 的 entityClass 字段
            Field f = service.getClass().getSuperclass().getDeclaredField("entityClass");
            f.setAccessible(true);
            Class<?> entityClass = (Class<?>) f.get(service);
            TableName ann = entityClass.getAnnotation(TableName.class);
            if (ann != null) return ann.value();
            return entityClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 根据 Controller 包路径识别业务模块 */
    private String resolveModule(ProceedingJoinPoint pjp) {
        String pkg = pjp.getTarget().getClass().getPackage().getName();
        if (pkg.contains(".basedata.") || pkg.contains(".masterdata.")) return "basedata";
        if (pkg.contains(".sale.")) return "sale";
        if (pkg.contains(".purchase.")) return "purchase";
        if (pkg.contains(".inventory.")) return "inventory";
        if (pkg.contains(".production.")) return "production";
        if (pkg.contains(".crm.")) return "crm";
        if (pkg.contains(".finance.")) return "finance";
        if (pkg.contains(".sample.")) return "sample";
        if (pkg.contains(".system.")) return "system";
        return "other";
    }

    /** 读取实体的主键字段值 */
    private Object readIdValue(Object entity) {
        if (entity == null) return null;
        Class<?> clazz = entity.getClass();
        // 主键命名约定: xxxId
        for (Field f : clazz.getDeclaredFields()) {
            String name = f.getName();
            if (name.endsWith("Id") && !name.equals("id")) {
                f.setAccessible(true);
                try { return f.get(entity); } catch (Exception ignored) {}
            }
        }
        // 尝试 id 字段
        try {
            Field f = clazz.getDeclaredField("id");
            f.setAccessible(true);
            return f.get(entity);
        } catch (Exception ignored) {}
        return null;
    }

    /** 通过反射调用 Controller 的 getCodeField() + 实体 getter, 读取编码字段值 */
    private Object readCodeValue(ProceedingJoinPoint pjp, Object entity) {
        if (entity == null) return null;
        try {
            Object controller = pjp.getTarget();
            Method m = controller.getClass().getSuperclass().getDeclaredMethod("getCodeField");
            m.setAccessible(true);
            String codeField = (String) m.invoke(controller);
            return readCodeFromEntity(codeField, entity);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从对象(已查询出来)读取编码值 */
    private Object readCodeValueFromObject(Object controller, Object entity) {
        if (entity == null) return null;
        try {
            Method m = controller.getClass().getSuperclass().getDeclaredMethod("getCodeField");
            m.setAccessible(true);
            String codeField = (String) m.invoke(controller);
            return readCodeFromEntity(codeField, entity);
        } catch (Exception e) {
            return null;
        }
    }

    /** 根据下划线编码字段名(dept_code), 调用实体的驼峰getter(getDeptCode) */
    private Object readCodeFromEntity(String codeField, Object entity) {
        if (codeField == null || codeField.isEmpty()) return null;
        String[] parts = codeField.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        String getter = "get" + Character.toUpperCase(sb.charAt(0)) + sb.substring(1);
        try {
            Method g = entity.getClass().getMethod(getter);
            return g.invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private String toStr(Object o) {
        return o == null ? null : o.toString();
    }

    private String currentUsername() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                Object u = req.getAttribute("currentUsername");
                if (u instanceof String s && !s.isBlank()) return s;
            }
        } catch (Exception ignored) {}
        return "system";
    }
}
