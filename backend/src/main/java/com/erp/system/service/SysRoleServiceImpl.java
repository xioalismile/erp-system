package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysRolePermission;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRolePermissionMapper rolePermissionMapper;

    @Override
    public PageResult<SysRole> pageQuery(int current, int size, String keyword) {
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
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
                new QueryWrapper<SysRolePermission>().eq("role_id", id));
        List<Long> menuIds = perms.stream().map(SysRolePermission::getMenuId).distinct().collect(Collectors.toList());
        role.setMenuIds(menuIds);
        return role;
    }

    @Override
    @Transactional
    public void saveRole(SysRole role) {
        if (role.getRoleCode() == null || role.getRoleCode().isBlank()) {
            role.setRoleCode(generateRoleCode());
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
        // 删除角色权限关联
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().eq("role_id", id));
        removeById(id);
        log.info("删除角色: id={}", id);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> menuIds) {
        SysRole role = getById(roleId);
        if (role == null) throw new BusinessException("角色不存在");
        // 先删除旧关联
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().eq("role_id", roleId));
        // 再插入新关联
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysRolePermission perm = new SysRolePermission();
                perm.setRoleId(roleId);
                perm.setMenuId(menuId);
                perm.setPermissionType("菜单");
                perm.setPermissionValue(1);
                perm.setIsInherited((short) 0);
                rolePermissionMapper.insert(perm);
            }
        }
        log.info("分配权限: roleId={} 菜单数={}", roleId, menuIds != null ? menuIds.size() : 0);
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
