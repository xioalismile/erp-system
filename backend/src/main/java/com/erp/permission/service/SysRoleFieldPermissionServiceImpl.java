package com.erp.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.permission.dto.FieldMetaDTO;
import com.erp.permission.dto.PermissionDTO;
import com.erp.permission.entity.SysRoleFieldPermission;
import com.erp.permission.mapper.SysRoleFieldPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

// 说明：本类读取/保存字段权限时，页面键会连同历史别名一起处理（见 PAGE_KEY_ALIAS / pageKeysOf）。

@Service
@RequiredArgsConstructor
public class SysRoleFieldPermissionServiceImpl extends ServiceImpl<SysRoleFieldPermissionMapper, SysRoleFieldPermission> implements SysRoleFieldPermissionService {

    private final FieldMetaService fieldMetaService;

    /**
     * 页面键历史别名（旧口径 → 现口径）。
     *
     * <p><b>为什么必须有</b>：早期字段权限按"单据类型"存页面键（如 purchase_inbound / sale_outbound），
     * 后来统一成"目标表名"（purchase_receipt / sale_delivery）。老配置留在旧键上 →
     * 读取按新键查不到 → 用户在页面和配置页里都看不到自己配过的必填（"配了不生效"）。
     * 这里读取时一并查、合并返回；保存时把旧键行一起清理，自然完成归一（另有一次性 SQL 迁移存量）。</p>
     */
    private static final Map<String, String> PAGE_KEY_ALIAS = Map.of(
            "purchase_inbound", "purchase_receipt",
            "sale_outbound", "sale_delivery");

    /** 取某页面键及其全部别名（含反向：传新键时把旧键也算进来），用于读取合并与保存清理。 */
    static Set<String> pageKeysOf(String pageKey) {
        Set<String> keys = new LinkedHashSet<>();
        if (pageKey == null || pageKey.isBlank()) {
            return keys;
        }
        keys.add(pageKey);
        String forward = PAGE_KEY_ALIAS.get(pageKey);
        if (forward != null) {
            keys.add(forward);
        }
        PAGE_KEY_ALIAS.forEach((legacy, current) -> {
            if (current.equals(pageKey)) {
                keys.add(legacy);
            }
        });
        return keys;
    }

    @Override
    public List<SysRoleFieldPermission> getByRoleAndPage(Long roleId, String pageKey) {
        return list(new QueryWrapper<SysRoleFieldPermission>()
                .eq("role_id", roleId)
                .in("page_key", pageKeysOf(pageKey))
                .orderByAsc("sort_order"));
    }

    @Override
    public List<SysRoleFieldPermission> getByRolesAndPage(List<Long> roleIds, String pageKey) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return list(new QueryWrapper<SysRoleFieldPermission>()
                .in("role_id", roleIds)
                .in("page_key", pageKeysOf(pageKey)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePermissions(Long roleId, String pageKey, List<SysRoleFieldPermission> list) {
        // 无显式 defaultRequiredKeys 传入时，从字段元数据自动取得系统/自定义字段默认必填集做兜底
        Set<String> defaultRequired = Collections.emptySet();
        try {
            List<FieldMetaDTO> metas = fieldMetaService.getFieldsByPage(pageKey);
            if (metas != null) {
                defaultRequired = metas.stream()
                        .filter(m -> m.getDefaultRequired() != null && m.getDefaultRequired() == 1)
                        .map(FieldMetaDTO::getFieldKey)
                        .collect(Collectors.toSet());
            }
        } catch (Exception e) {
            throw new BusinessException("保存字段权限失败：读取字段定义失败：" + e.getMessage());
        }
        doSavePermissions(roleId, pageKey, list, defaultRequired);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePermissions(Long roleId, String pageKey, List<SysRoleFieldPermission> list, Set<String> defaultRequiredKeys) {
        Set<String> keys = defaultRequiredKeys != null ? defaultRequiredKeys : Collections.emptySet();
        doSavePermissions(roleId, pageKey, list, keys);
    }

    private void doSavePermissions(Long roleId, String pageKey, List<SysRoleFieldPermission> list, Set<String> defaultRequiredKeys) {
        // 连同历史别名键一起清理：保存一次即完成"旧键 → 新键"的归一，避免旧键残留导致读取时把旧配置又合并回来
        remove(new QueryWrapper<SysRoleFieldPermission>()
                .eq("role_id", roleId)
                .in("page_key", pageKeysOf(pageKey)));

        if (list != null && !list.isEmpty()) {
            for (SysRoleFieldPermission perm : list) {
                perm.setRoleId(roleId);
                perm.setPageKey(pageKey);
                perm.setId(null);
                // 兜底①：系统默认必填/自定义字段勾选必填后，任何时候都不允许关闭必填
                // 即使前端绕过禁用开关传 required=0，这里也强制转回 1
                if (perm.getFieldKey() != null && defaultRequiredKeys.contains(perm.getFieldKey())) {
                    perm.setRequired(1);
                }
                // 兜底②：必填 ⇒ 可见 + 可编辑
                // "必填但隐藏/只读"是自相矛盾的配置：业务表单（BaseEdit）对这类字段不打星、不校验，
                // 会让用户配的"必填"静默失效；故在入库前强制拉齐，保证"带星号 = 一定可填且一定被校验"
                if (perm.getRequired() != null && perm.getRequired() == 1) {
                    if (perm.getVisible() == null || perm.getVisible() == 0) perm.setVisible(1);
                    if (perm.getEditable() == null || perm.getEditable() == 0) perm.setEditable(1);
                }
            }
            saveBatch(list);
        }
    }

    @Override
    public List<FieldMetaDTO> getPageFieldMeta(String pageKey) {
        return fieldMetaService.getFieldsByPage(pageKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyPermissions(Long sourceRoleId, Long targetRoleId) {
        List<SysRoleFieldPermission> sourcePerms = list(new QueryWrapper<SysRoleFieldPermission>()
                .eq("role_id", sourceRoleId));

        if (sourcePerms != null && !sourcePerms.isEmpty()) {
            remove(new QueryWrapper<SysRoleFieldPermission>()
                    .eq("role_id", targetRoleId));

            // 按"目标角色每个页面各自的默认必填集"再次做兜底，避免源角色若缺失 defaultRequired 字段的 required=1
            //（比如源角色建得早、保存时没有兜底）复制到目标后也把必填关掉了
            Map<String, Set<String>> defaultsByPage = new HashMap<>();

            List<SysRoleFieldPermission> targetPerms = sourcePerms.stream()
                    .map(perm -> {
                        SysRoleFieldPermission newPerm = new SysRoleFieldPermission();
                        newPerm.setRoleId(targetRoleId);
                        newPerm.setPageKey(perm.getPageKey());
                        newPerm.setFieldKey(perm.getFieldKey());
                        newPerm.setVisible(perm.getVisible());
                        newPerm.setEditable(perm.getEditable());
                        newPerm.setRequired(perm.getRequired());
                        newPerm.setSortOrder(perm.getSortOrder());

                        Set<String> defaults = defaultsByPage.computeIfAbsent(perm.getPageKey(), pk -> {
                            try {
                                List<FieldMetaDTO> metas = fieldMetaService.getFieldsByPage(pk);
                                return metas == null ? Collections.emptySet()
                                        : metas.stream()
                                            .filter(m -> m.getDefaultRequired() != null && m.getDefaultRequired() == 1)
                                            .map(FieldMetaDTO::getFieldKey)
                                            .collect(Collectors.toSet());
                            } catch (Exception e) {
                                throw new BusinessException("复制字段权限失败：读取字段定义失败：" + e.getMessage());
                            }
                        });
                        if (newPerm.getFieldKey() != null && defaults.contains(newPerm.getFieldKey())) {
                            newPerm.setRequired(1);
                        }
                        // 同 doSavePermissions 兜底②：必填 ⇒ 可见 + 可编辑（否则"必填"在业务表单上会静默失效）
                        if (newPerm.getRequired() != null && newPerm.getRequired() == 1) {
                            if (newPerm.getVisible() == null || newPerm.getVisible() == 0) newPerm.setVisible(1);
                            if (newPerm.getEditable() == null || newPerm.getEditable() == 0) newPerm.setEditable(1);
                        }
                        return newPerm;
                    })
                    .collect(Collectors.toList());

            saveBatch(targetPerms);
        }
    }

    @Override
    public Map<String, PermissionDTO> getFieldPermissionMap(List<Long> roleIds, String pageKey) {
        // 普通用户：多角色合并策略 visible=任一 / editable=任一 / required=**全部角色都要求**才算必填
        return merge(getByRolesAndPage(roleIds, pageKey), false);
    }

    /**
     * 全权管理员视角的"该页面配置总览"：不分角色，取该页面上**任何角色**配过的字段并集，
     * required=任一角色要求即算必填。
     *
     * <p><b>为什么需要</b>：配置字段权限的人就是管理员，配完要立刻在自己账号上看到效果；
     * 若按"仅自己所属角色"取配置，管理员把必填配在业务角色（如销售员）上后，在自己页面上永远
     * 看不到星号、也不会被拦 → 用户判定"功能没生效"（实报问题）。管理员本就有全量视角，
     * 取并集既能看到配置效果，也让管理员同样受必填约束（口径统一：配置即生效）。</p>
     */
    @Override
    public Map<String, PermissionDTO> getAllRolesFieldPermissionMap(String pageKey) {
        return merge(list(new QueryWrapper<SysRoleFieldPermission>().in("page_key", pageKeysOf(pageKey))), true);
    }

    @Override
    public Set<String> getConfiguredPages(Long roleId) {
        if (roleId == null) {
            return Collections.emptySet();
        }
        List<SysRoleFieldPermission> rows = list(new QueryWrapper<SysRoleFieldPermission>()
                .eq("role_id", roleId)
                .select("page_key"));
        return rows.stream()
                // 单列投影下 page_key 为空的行会映射成 null 元素（MyBatis 行为，见 WorkstationController 同类修复）
                .filter(java.util.Objects::nonNull)
                .map(SysRoleFieldPermission::getPageKey)
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toSet());
    }

    private Map<String, PermissionDTO> merge(List<SysRoleFieldPermission> perms, boolean requiredAny) {
        Map<String, PermissionDTO> result = new HashMap<>();
        if (perms == null || perms.isEmpty()) {
            return result;
        }

        Map<String, List<SysRoleFieldPermission>> groupedByField = perms.stream()
                .collect(Collectors.groupingBy(SysRoleFieldPermission::getFieldKey));

        for (Map.Entry<String, List<SysRoleFieldPermission>> entry : groupedByField.entrySet()) {
            String fieldKey = entry.getKey();
            List<SysRoleFieldPermission> fieldPerms = entry.getValue();

            PermissionDTO dto = new PermissionDTO();
            dto.setFieldKey(fieldKey);

            int visible = fieldPerms.stream().anyMatch(p -> p.getVisible() != null && p.getVisible() == 1) ? 1 : 0;
            int editable = fieldPerms.stream().anyMatch(p -> p.getEditable() != null && p.getEditable() == 1) ? 1 : 0;
            int required = (requiredAny
                    ? fieldPerms.stream().anyMatch(p -> p.getRequired() != null && p.getRequired() == 1)
                    : fieldPerms.stream().allMatch(p -> p.getRequired() != null && p.getRequired() == 1)) ? 1 : 0;
            if (required == 1) {
                // 必填 ⇒ 可见 + 可编辑（与保存侧兜底同口径）
                visible = 1;
                editable = 1;
            }

            dto.setVisible(visible);
            dto.setEditable(editable);
            dto.setRequired(required);

            result.put(fieldKey, dto);
        }

        return result;
    }
}
