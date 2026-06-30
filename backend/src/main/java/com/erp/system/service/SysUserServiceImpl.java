package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
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
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;

    private static final String DEFAULT_PASSWORD = "123456";

    @Override
    public PageResult<SysUser> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("username", keyword)
                    .or().like("real_name", keyword)
                    .or().like("user_code", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        Page<SysUser> page = new Page<>(current, size);
        Page<SysUser> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SysUser getDetail(Long id) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new QueryWrapper<SysUserRole>().eq("user_id", id));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        user.setRoleIds(roleIds);
        // 密码不回传
        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional
    public void saveUser(SysUser user) {
        if (user.getUserCode() == null || user.getUserCode().isBlank()) {
            user.setUserCode(generateUserCode());
        }
        // 校验用户名唯一
        if (exists(new QueryWrapper<SysUser>().eq("username", user.getUsername()))) {
            throw new BusinessException("用户名已存在");
        }
        if (exists(new QueryWrapper<SysUser>().eq("user_code", user.getUserCode()))) {
            throw new BusinessException("用户编码已存在");
        }
        user.setPassword(DEFAULT_PASSWORD);
        if (user.getIsAdmin() == null) user.setIsAdmin((short) 0);
        if (user.getIsEnabled() == null) user.setIsEnabled((short) 1);
        if (user.getIsLocked() == null) user.setIsLocked((short) 0);
        if (user.getLoginAttempts() == null) user.setLoginAttempts(0);
        if (user.getStatus() == null) user.setStatus("启用");
        if (user.getUserType() == null) user.setUserType("普通用户");
        if (user.getAuthType() == null) user.setAuthType("本地认证");
        save(user);
        log.info("新增用户: {} 编码: {}", user.getUsername(), user.getUserCode());
    }

    @Override
    @Transactional
    public void updateUser(SysUser user) {
        if (user.getUserId() == null) throw new BusinessException("用户ID不能为空");
        // 校验用户名唯一(排除自身)
        if (exists(new QueryWrapper<SysUser>().eq("username", user.getUsername()).ne("user_id", user.getUserId()))) {
            throw new BusinessException("用户名已存在");
        }
        // 不更新密码: 置空以跳过更新
        user.setPassword(null);
        updateById(user);
        log.info("更新用户: id={}", user.getUserId());
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        if (user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            throw new BusinessException("超级管理员不允许删除");
        }
        // 删除用户角色关联
        userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", id));
        removeById(id);
        log.info("删除用户: id={}", id);
    }

    @Override
    @Transactional
    public void resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(DEFAULT_PASSWORD);
        updateById(user);
        log.info("重置用户密码: id={}", id);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        // 先删除旧关联
        userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId));
        // 再插入新关联
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleMapper.insert(userRole);
            }
        }
        log.info("分配角色: userId={} 角色数={}", userId, roleIds != null ? roleIds.size() : 0);
    }

    /** 生成用户编码: USER + yyyyMMdd + 0001递增 */
    public String generateUserCode() {
        String prefix = "USER" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.likeRight("user_code", prefix).orderByDesc("user_code").last("LIMIT 1");
        SysUser last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getUserCode() != null) {
            try {
                seq = Integer.parseInt(last.getUserCode().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
