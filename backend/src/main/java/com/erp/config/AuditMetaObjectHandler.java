package com.erp.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.erp.basedata.service.UnitConversionService;
import com.erp.common.bill.BillItemUnitFields;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MyBatis Plus auto-fill handler for audit fields:
 * - createdAt / updatedAt: filled with current timestamp
 * - createdBy / updatedBy: filled with current username from request attribute
 *
 * This is required because entities use @TableField(fill = FieldFill.INSERT)
 * which causes MyBatis Plus to include these columns in INSERT statements.
 * Without this handler, NULL would be bound, overriding DB DEFAULT and
 * violating NOT NULL constraints.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /**
     * 多计量单位折算：单据明细落库前统一折算，避免各模块各写一份。
     *
     * <p>【2026-11-09 修复启动循环依赖】该链为
     * 会话工厂 → 本处理器 → 单位折算 → 物料服务 → 编号规则服务 → Mapper → 会话工厂，
     * 构造注入会形成闭环导致启动失败。改用延迟代理打破环：
     * 元数据处理器只在真正插入数据时才用到单位折算，无需在容器初始化期就实例化整条链。</p>
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private UnitConversionService unitConversionService;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String username = currentUsername();

        // fill createdAt (基础资料/销售/采购等模块使用)
        Object createdAt = getFieldValByName("createdAt", metaObject);
        if (createdAt == null) {
            strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        }
        // fill createDate (CRM 模块使用 create_date / modify_date 命名)
        Object createDate = getFieldValByName("createDate", metaObject);
        if (createDate == null) {
            strictInsertFill(metaObject, "createDate", LocalDateTime.class, now);
        }
        // fill updatedAt
        Object updatedAt = getFieldValByName("updatedAt", metaObject);
        if (updatedAt == null) {
            strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        }
        // fill modifyDate (CRM 模块)
        Object modifyDate = getFieldValByName("modifyDate", metaObject);
        if (modifyDate == null) {
            strictInsertFill(metaObject, "modifyDate", LocalDateTime.class, now);
        }
        // fill createdBy
        Object createdBy = getFieldValByName("createdBy", metaObject);
        if (createdBy == null && username != null) {
            strictInsertFill(metaObject, "createdBy", String.class, username);
        }
        // fill updatedBy
        Object updatedBy = getFieldValByName("updatedBy", metaObject);
        if (updatedBy == null && username != null) {
            strictInsertFill(metaObject, "updatedBy", String.class, username);
        }
        // fill createTime (字段权限模块使用 create_time / update_time 命名)
        Object createTime = getFieldValByName("createTime", metaObject);
        if (createTime == null) {
            strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        }
        // fill updateTime
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (updateTime == null) {
            strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String username = currentUsername();

        Object updatedAt = getFieldValByName("updatedAt", metaObject);
        if (updatedAt == null) {
            strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        }
        Object modifyDate = getFieldValByName("modifyDate", metaObject);
        if (modifyDate == null) {
            strictUpdateFill(metaObject, "modifyDate", LocalDateTime.class, now);
        }
        Object updatedBy = getFieldValByName("updatedBy", metaObject);
        if (updatedBy == null && username != null) {
            strictUpdateFill(metaObject, "updatedBy", String.class, username);
        }
        // fill updateTime
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (updateTime == null) {
            strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
    }

    // 多计量单位折算已迁到 BillItemUnitFillAspect（Mapper 落库前直接改实体）：
    // MetaObjectHandler 的赋值在 INSERT 参数绑定阶段拿不到，导致"算出来了但落库为空"。

    private String currentUsername() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Object username = request.getAttribute("currentUsername");
                if (username instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        return "system";
    }
}
