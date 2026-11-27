package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.AuthInterceptor;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysRolePermission;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysRolePermissionMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.permission.entity.SysRoleFieldPermission;
import com.erp.permission.mapper.SysRoleFieldPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "created_at",
            "created_by",
            "data_scope",
            "data_scope_value",
            "is_system",
            "remark",
            "role_code",
            "role_id",
            "role_name",
            "role_type",
            "sort_order",
            "status",
            "updated_at",
            "updated_by"
        );

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysSerialNoConfigService serialNoService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleFieldPermissionMapper roleFieldPermissionMapper;

    /**
     * 权限/角色变更后必须清理鉴权缓存（AuthInterceptor 的 30 秒缓存），
     * 否则改完权限最长要等 30 秒才生效。
     */
    private final AuthInterceptor authInterceptor;

    @Override
    public PageResult<SysRole> pageQuery(int current, int size, String keyword, String sortBy, String sortOrder) {
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SysRole.class);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("role_code", keyword)
                    .or().like("role_name", keyword));
        }
        wrapper.orderByAsc("sort_order");
        Page<SysRole> page = new Page<>(current, size);
        Page<SysRole> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SysRole getDetail(Long id) {
        SysRole role = getById(id);
        if (role == null) throw new BusinessException("角色不存在");
        List<SysRolePermission> perms = rolePermissionMapper.selectList(
                new QueryWrapper<SysRolePermission>().eq("role_id", id).eq("permission_type", "菜单"));
        List<String> menuIds = perms.stream().map(SysRolePermission::getMenuId).distinct().collect(Collectors.toList());
        role.setMenuIds(menuIds);
        return role;
    }

    @Override
    @Transactional
    public void saveRole(SysRole role) {
        if (role.getRoleCode() == null || role.getRoleCode().isBlank()) {
            role.setRoleCode(serialNoService.generateOrDefault("SYS_ROLE", this::generateRoleCode));
        }
        if (exists(new QueryWrapper<SysRole>().eq("role_code", role.getRoleCode()))) {
            throw new BusinessException("角色编码已存在");
        }
        if (role.getRoleType() == null) role.setRoleType("业务角色");
        if (role.getDataScope() == null) role.setDataScope("本部门");
        if (role.getIsSystem() == null) role.setIsSystem((short) 0);
        if (role.getSortOrder() == null) role.setSortOrder(0);
        if (role.getStatus() == null) role.setStatus("启用");
        save(role);
        authInterceptor.evictAll();
        log.info("新增角色: {} 编码: {}", role.getRoleName(), role.getRoleCode());
    }

    @Override
    @Transactional
    public void updateRole(SysRole role) {
        if (role.getRoleId() == null) throw new BusinessException("角色ID不能为空");
        if (exists(new QueryWrapper<SysRole>().eq("role_code", role.getRoleCode()).ne("role_id", role.getRoleId()))) {
            throw new BusinessException("角色编码已存在");
        }
        updateById(role);
        authInterceptor.evictAll();
        log.info("更新角色: id={}", role.getRoleId());
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        SysRole role = getById(id);
        if (role == null) throw new BusinessException("角色不存在");
        if (role.getIsSystem() != null && role.getIsSystem() == 1) {
            throw new BusinessException("系统内置角色不允许删除");
        }
        // 守卫1：角色已被用户引用时拒绝删除——删掉会让那些用户登录后查不到角色，权限静默失效
        Long userRefs = userRoleMapper.selectCount(new QueryWrapper<SysUserRole>().eq("role_id", id));
        if (userRefs != null && userRefs > 0) {
            throw new BusinessException("该角色已分配给 " + userRefs + " 个用户，请先解除用户与该角色的关联再删除");
        }
        // 守卫2：一并清理按钮权限(sys_role_permission)与字段权限(sys_role_field_permission)，避免孤儿行
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().eq("role_id", id));
        roleFieldPermissionMapper.delete(new QueryWrapper<SysRoleFieldPermission>().eq("role_id", id));
        removeById(id);
        authInterceptor.evictAll();
        log.info("删除角色: id={}", id);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<String> menuIds) {
        SysRole role = getById(roleId);
        if (role == null) throw new BusinessException("角色不存在");
        // 先删除旧菜单关联（仅菜单类型，保留按钮权限）
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>()
                .eq("role_id", roleId).eq("permission_type", "菜单"));
        // 再插入新关联
        if (menuIds != null && !menuIds.isEmpty()) {
            for (String menuId : menuIds) {
                SysRolePermission perm = new SysRolePermission();
                perm.setRoleId(roleId);
                perm.setMenuId(menuId);
                perm.setPermissionType("菜单");
                perm.setPermissionValue(1);
                perm.setIsInherited((short) 0);
                rolePermissionMapper.insert(perm);
            }
        }
        authInterceptor.evictAll();
        log.info("分配权限: roleId={} 菜单数={}", roleId, menuIds != null ? menuIds.size() : 0);
    }

    @Override
    public List<String> getButtonPermissions(Long roleId) {
        List<SysRolePermission> perms = rolePermissionMapper.selectList(
                new QueryWrapper<SysRolePermission>().eq("role_id", roleId).eq("permission_type", "按钮"));
        return perms.stream().map(SysRolePermission::getMenuId).distinct().collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void assignButtonPermissions(Long roleId, List<String> buttonCodes) {
        SysRole role = getById(roleId);
        if (role == null) throw new BusinessException("角色不存在");
        // 先删除旧按钮权限
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>()
                .eq("role_id", roleId).eq("permission_type", "按钮"));
        // 再插入新按钮权限
        if (buttonCodes != null && !buttonCodes.isEmpty()) {
            for (String code : buttonCodes) {
                if (code == null || code.isBlank()) continue;
                SysRolePermission perm = new SysRolePermission();
                perm.setRoleId(roleId);
                perm.setMenuId(code);
                perm.setPermissionType("按钮");
                perm.setPermissionValue(1);
                perm.setIsInherited((short) 0);
                rolePermissionMapper.insert(perm);
            }
        }
        authInterceptor.evictAll();
        log.info("分配按钮权限: roleId={} 按钮数={}", roleId, buttonCodes != null ? buttonCodes.size() : 0);
    }

    @Override
    public List<SysRole> listAll() {
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "启用").orderByAsc("sort_order");
        return list(wrapper);
    }

    /** 生成角色编码: ROLE + yyyyMMdd + 0001递增 */
    public String generateRoleCode() {
        String prefix = "ROLE" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        wrapper.likeRight("role_code", prefix).orderByDesc("role_code").last("LIMIT 1");
        SysRole last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getRoleCode() != null) {
            try {
                seq = Integer.parseInt(last.getRoleCode().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
