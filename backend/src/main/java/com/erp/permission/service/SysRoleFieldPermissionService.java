package com.erp.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.permission.dto.FieldMetaDTO;
import com.erp.permission.dto.PermissionDTO;
import com.erp.permission.entity.SysRoleFieldPermission;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SysRoleFieldPermissionService extends IService<SysRoleFieldPermission> {

    List<SysRoleFieldPermission> getByRoleAndPage(Long roleId, String pageKey);

    List<SysRoleFieldPermission> getByRolesAndPage(List<Long> roleIds, String pageKey);

    /**
     * 保存角色字段权限。
     * 内置兜底：defaultRequired 的字段（系统默认必填/自定义字段必填）强制 required=1，前端禁用开关
     * 后即使被绕过提交也不会误关必填。
     */
    void savePermissions(Long roleId, String pageKey, List<SysRoleFieldPermission> list, Set<String> defaultRequiredKeys);

    /** 兼容旧调用：无默认必填集时不做兜底。保留此方法避免改 Controller 编译期报错；内部等价调用 new 方法。 */
    void savePermissions(Long roleId, String pageKey, List<SysRoleFieldPermission> list);

    void copyPermissions(Long sourceRoleId, Long targetRoleId);

    Map<String, PermissionDTO> getFieldPermissionMap(List<Long> roleIds, String pageKey);

    /**
     * 全权管理员视角：该页面上**任何角色**配过的字段并集（required = 任一角色要求即必填）。
     * 用途：配置字段权限的人就是管理员，配完要能立刻在自己账号上看到效果（配置即生效）。
     */
    Map<String, PermissionDTO> getAllRolesFieldPermissionMap(String pageKey);

    /** 获取某页字段元数据（含 defaultRequired），供 Controller 在保存前做兜底。 */
    List<FieldMetaDTO> getPageFieldMeta(String pageKey);

    /**
     * 该角色已配置过字段权限的页面 key 集合（去重）。
     * 用途：设置中心「字段权限」左栏默认只展开有配置的模块，避免整棵树全部铺开。
     */
    Set<String> getConfiguredPages(Long roleId);
}
