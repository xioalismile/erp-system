package com.erp.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.util.SecurityUtils;
import com.erp.permission.dto.PermissionDTO;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 「字段权限-不可见」的统一解析服务。
 *
 * <p>原先只有 {@code FieldPermissionResponseAdvice} 自己解析：按接口路径手写映射、只过滤 Result JSON。
 * 导出（文件）整条链路没有覆盖，导致"列表看不到、导出文件里有"。本服务把"当前用户在某页面
 * 不可见的字段集合"收敛成唯一出口，供两处复用：</p>
 * <ol>
 *   <li>列表/报表响应过滤（{@link com.erp.permission.config.FieldPermissionResponseAdvice}）</li>
 *   <li>导出写盘前剔列（ExcelServiceImpl 的所有导出入口）</li>
 * </ol>
 *
 * <p>口径与 /api/role-field-permission/current/page/{pageKey} 一致：管理员/经理/超管短路不限制；
 * 取不到请求上下文（异步/定时任务）按不限制处理；隐藏字段集合按「租户+用户+页面」缓存 60 秒。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldVisiblePermissionService {

    private static final String[] ADMIN_ROLE_CODES = {"ADMIN", "MANAGER", "SUPER_ADMIN"};

    private final SysRoleFieldPermissionService roleFieldPermissionService;
    private final SysUserRoleMapper sysUserRoleMapper;

    private Cache<String, Set<String>> hiddenCache;

    @PostConstruct
    public void init() {
        hiddenCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
    }

    /** 当前登录用户在该页面不可见的字段集合（key 同时给出 camelCase 与 snake_case 两种写法） */
    public Set<String> currentUserHiddenKeys(String pageKey) {
        if (pageKey == null || pageKey.isBlank()) return Collections.emptySet();
        HttpServletRequest request = currentRequest();
        if (request == null) return Collections.emptySet();
        if (SecurityUtils.hasAnyRole(request, ADMIN_ROLE_CODES)) return Collections.emptySet();
        Long userId = SecurityUtils.getCurrentUserId(request);
        if (userId == null) return Collections.emptySet();
        return hiddenKeysFor(userId, false, pageKey);
    }

    /**
     * 指定用户在某页面不可见的字段集合（不依赖请求线程）。
     *
     * <p>AI 小助手工具跑在线程池里，没有 RequestContextHolder；小助手查询与自定义报表的
     * 字段权限必须按用户显式解析，否则会静默返回空集合（等于不限制）。</p>
     */
    public Set<String> hiddenKeysFor(Long userId, boolean admin, String pageKey) {
        if (pageKey == null || pageKey.isBlank()) return Collections.emptySet();
        if (admin) return Collections.emptySet();
        if (userId == null) return Collections.emptySet();

        String cacheKey = TenantContextHolder.cacheKeyPrefix() + userId + ":" + pageKey;
        Set<String> cached = hiddenCache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        Set<String> hidden = new HashSet<>();
        try {
            List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                    new QueryWrapper<SysUserRole>().eq("user_id", userId));
            List<Long> roleIds = new ArrayList<>();
            for (SysUserRole ur : userRoles) {
                if (ur.getRoleId() != null) roleIds.add(ur.getRoleId());
            }
            if (!roleIds.isEmpty()) {
                java.util.Map<String, PermissionDTO> map = roleFieldPermissionService.getFieldPermissionMap(roleIds, pageKey);
                for (PermissionDTO p : map.values()) {
                    if (p.getVisible() != null && p.getVisible() == 0) {
                        hidden.add(p.getFieldKey());
                        hidden.add(snakeToCamel(p.getFieldKey()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[字段权限] 查询不可见字段失败（按不限制处理）: userId={}, pageKey={}, err={}",
                    userId, pageKey, e.getMessage());
            return Collections.emptySet();
        }
        hiddenCache.put(cacheKey, hidden);
        return hidden;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    /** snake_case → camelCase（contact_person → contactPerson），已是 camelCase 原样返回 */
    public static String snakeToCamel(String key) {
        if (key == null || key.indexOf('_') < 0) return key;
        StringBuilder sb = new StringBuilder(key.length());
        boolean upper = false;
        for (char ch : key.toCharArray()) {
            if (ch == '_') { upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(ch) : ch);
            upper = false;
        }
        return sb.toString();
    }
}
