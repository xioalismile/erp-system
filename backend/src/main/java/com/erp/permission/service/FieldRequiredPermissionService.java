package com.erp.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.util.SecurityUtils;
import com.erp.config.BusinessException;
import com.erp.permission.dto.FieldMetaDTO;
import com.erp.permission.dto.PermissionDTO;
import com.erp.report.custom.core.EntityMetadataRegistry;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 「字段权限-必填」的统一解析与校验服务。
 *
 * <p>背景：字段权限（sys_role_field_permission.required）原先只被前端 BaseEdit 组件消费，
 * 造成"基础档案页面拦、单据页面不拦、后端不拦、导入更不认"的口径分裂。
 * 本服务把口径收敛到一处，供三处复用：
 * <ol>
 *   <li>导出模块：导入模板列头加 * 、导入时校验（{@link #resolve} + {@link #findMissingStaticLabels(Map, PageRequired)}）</li>
 *   <li>单据保存接口兜底：{@link #validateStaticRequiredOrThrow}</li>
 *   <li>前端同口径（前端读 /current/page/{pageKey}，语义与此处一致）</li>
 * </ol>
 *
 * <p>口径与 /api/role-field-permission/current/page/{pageKey} 严格一致：
 * 管理员/经理/超管短路不叠加；多角色 required 取交集（所有角色都勾必填才算必填）；
 * 取不到请求上下文（异步/定时任务）不叠加。
 *
 * <p>性能约定：导入/模板是逐行或逐单循环，**必须**先用 {@link #resolve(String)} 解析一次，
 * 再把结果传给各校验重载，禁止在循环内反复调 {@link #resolve}（每次都会查角色与权限表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldRequiredPermissionService {

    private static final String[] ADMIN_ROLE_CODES = {"ADMIN", "MANAGER", "SUPER_ADMIN"};

    private final SysRoleFieldPermissionService roleFieldPermissionService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final FieldMetaService fieldMetaService;
    private final EntityMetadataRegistry registry;

    /**
     * 页面级必填解析结果（一次性解析、循环复用）。
     *
     * @param staticFields registry 静态字段中角色必填的 fieldKey → 中文标签（实体对象校验、单元格校验共用）
     * @param allLabels    静态 + 自定义字段中角色必填的「中文标签」（模板加 * 与自定义字段校验用）
     */
    public record PageRequired(Map<String, String> staticFields, Set<String> allLabels) {
        public boolean isEmpty() {
            return staticFields.isEmpty() && allLabels.isEmpty();
        }
    }

    private static final PageRequired EMPTY = new PageRequired(Collections.emptyMap(), Collections.emptySet());

    /** 取当前请求；取不到（异步/定时任务）返回 null，按"不叠加字段必填"处理，与 FieldPermissionResponseAdvice 的放行口径一致 */
    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    /** 当前登录用户在该页面被配置为必填的 fieldKey 集合（管理员/无上下文 → 空集） */
    public Set<String> currentUserRequiredKeys(String pageKey) {
        if (pageKey == null || pageKey.isBlank()) return Collections.emptySet();
        HttpServletRequest request = currentRequest();
        if (request == null) return Collections.emptySet();
        if (SecurityUtils.hasAnyRole(request, ADMIN_ROLE_CODES)) return Collections.emptySet();
        Long userId = SecurityUtils.getCurrentUserId(request);
        if (userId == null) return Collections.emptySet();
        try {
            List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                    new QueryWrapper<SysUserRole>().eq("user_id", userId));
            List<Long> roleIds = new ArrayList<>();
            for (SysUserRole ur : userRoles) {
                if (ur.getRoleId() != null) roleIds.add(ur.getRoleId());
            }
            if (roleIds.isEmpty()) return Collections.emptySet();
            Map<String, PermissionDTO> map = roleFieldPermissionService.getFieldPermissionMap(roleIds, pageKey);
            Set<String> keys = new LinkedHashSet<>();
            map.forEach((k, v) -> {
                if (v != null && v.getRequired() != null && v.getRequired() == 1) keys.add(k);
            });
            return keys;
        } catch (Exception e) {
            log.warn("解析角色字段必填失败, pageKey={}: {}", pageKey, e.getMessage());
            return Collections.emptySet();
        }
    }

    /** 一次性解析页面必填（循环内复用，避免逐行查库） */
    public PageRequired resolve(String pageKey) {
        Set<String> requiredKeys = currentUserRequiredKeys(pageKey);
        if (requiredKeys.isEmpty()) return EMPTY;
        Map<String, String> staticLabels = staticFieldLabels(pageKey);
        Map<String, String> staticRequired = new LinkedHashMap<>();
        Set<String> allRequired = new LinkedHashSet<>();
        for (FieldMetaDTO m : fieldMetaService.getFieldsByPage(pageKey)) {
            if (m.getFieldKey() == null || !requiredKeys.contains(m.getFieldKey())) continue;
            String label = m.getFieldName() != null ? m.getFieldName() : m.getFieldKey();
            allRequired.add(label);
            String staticLabel = staticLabels.get(m.getFieldKey());
            if (staticLabel != null) staticRequired.put(m.getFieldKey(), staticLabel);
        }
        return new PageRequired(staticRequired, allRequired);
    }

    /** 当前用户在该页面必填的「中文标签」集合（模板列头加 * 用；静态 + 自定义字段都在内） */
    public Set<String> currentUserRequiredLabels(String pageKey) {
        return resolve(pageKey).allLabels();
    }

    /** 该页面的「静态字段」（registry 实体字段，不含自定义字段）fieldKey → 中文标签 */
    public Map<String, String> staticFieldLabels(String pageKey) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (pageKey == null || pageKey.isBlank()) return labels;
        EntityMetadataRegistry.DataSourceMeta meta = registry.getDataSource(pageKey);
        if (meta == null) return labels;
        for (EntityMetadataRegistry.FieldMeta fm : meta.fields.values()) {
            if (fm.field != null) labels.put(fm.field, fm.label != null ? fm.label : fm.field);
        }
        return labels;
    }

    // ==================== 校验：实体对象（后端保存兜底 / 通用导入主循环） ====================

    /** 校验实体对象上的静态字段角色必填，返回缺失的中文标签（仅静态字段，自定义字段值不在实体内） */
    public List<String> findMissingStaticLabels(Object entity, PageRequired required) {
        if (entity == null || required == null || required.staticFields().isEmpty()) return Collections.emptyList();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : required.staticFields().entrySet()) {
            if (isBlankValue(readProperty(entity, e.getKey()))) missing.add(e.getValue());
        }
        return missing;
    }

    /** 便捷重载：单次调用（后端保存兜底用），内部自行解析 */
    public List<String> findMissingStaticLabels(Object entity, String pageKey) {
        return findMissingStaticLabels(entity, resolve(pageKey));
    }

    // ==================== 校验：标签→单元格值（导入用） ====================

    /**
     * 校验「标签 → 单元格值」映射中的静态字段角色必填，返回缺失的中文标签。
     * 边界：containsKey=false 表示"该列不在上传模板中" → 跳过，避免用精简模板导入被卡死；
     * containsKey=true 但值为空才算缺失。
     */
    public List<String> findMissingStaticLabels(Map<String, String> labelValues, PageRequired required) {
        if (labelValues == null || labelValues.isEmpty()
                || required == null || required.staticFields().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : required.staticFields().entrySet()) {
            if (!labelValues.containsKey(e.getValue())) continue;
            if (isBlankValue(labelValues.get(e.getValue()))) missing.add(e.getValue());
        }
        return missing;
    }

    /** 便捷重载：单次调用（单据导入用），内部自行解析 */
    public List<String> findMissingStaticLabels(Map<String, String> labelValues, String pageKey) {
        return findMissingStaticLabels(labelValues, resolve(pageKey));
    }

    /** 单据保存兜底校验：静态字段角色必填缺失即抛业务异常（提示中文标签） */
    public void validateStaticRequiredOrThrow(Object entity, String pageKey, String billName) {
        List<String> missing = findMissingStaticLabels(entity, pageKey);
        if (!missing.isEmpty()) {
            String name = (billName != null && !billName.isBlank()) ? billName : pageKey;
            throw new BusinessException("【" + name + "】请填写必填项：" + String.join("、", missing));
        }
    }

    /**
     * 单据保存兜底校验（限定字段版）：只校验 {@code fillableLabels} 里列出的字段。
     *
     * <p>用途：基础资料页（如物料档案）的元数据里包含大量**页面上没有输入口**的字段
     * （审计字段 createdAt/updatedAt/createdBy/updatedBy、仅导入用的 supplyType/stockUnitId 等）。
     * 这些字段即便被配成"必填"也无处可填，若按全字段兜底会让该单据**永远保存不了**；
     * 因此由调用方传入"页面可填字段白名单"，只对这些字段做兜底。
     * 提示文案用白名单里的**页面标签**（而非 registry 标签），保证与用户界面上看到的一致。</p>
     *
     * @param fillableLabels fieldKey → 页面中文标签（null/空 → 不校验，退化为不做兜底）
     */
    public void validateStaticRequiredOrThrow(Object entity, String pageKey, String billName,
                                              Map<String, String> fillableLabels) {
        if (entity == null || fillableLabels == null || fillableLabels.isEmpty()) return;
        PageRequired all = resolve(pageKey);
        if (all.isEmpty() || all.staticFields().isEmpty()) return;
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : fillableLabels.entrySet()) {
            String key = e.getKey();
            if (key == null || !all.staticFields().containsKey(key)) continue; // 该字段未被配成角色必填
            if (isBlankValue(readProperty(entity, key))) missing.add(e.getValue());
        }
        if (!missing.isEmpty()) {
            String name = (billName != null && !billName.isBlank()) ? billName : pageKey;
            throw new BusinessException("【" + name + "】请填写必填项：" + String.join("、", missing));
        }
    }

    // ==================== 内部工具 ====================

    /** 反射读取实体属性（沿类继承链查找，忽略 static 字段）；取不到返回 null */
    private Object readProperty(Object entity, String fieldName) {
        if (entity == null || fieldName == null) return null;
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (!f.getName().equals(fieldName)) continue;
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    return f.get(entity);
                } catch (Exception e) {
                    log.debug("读取字段失败 {}.{}: {}", clazz.getSimpleName(), fieldName, e.getMessage());
                    return null;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private boolean isBlankValue(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.trim().isEmpty();
        if (v instanceof Collection<?> c) return c.isEmpty();
        return false;
    }
}
