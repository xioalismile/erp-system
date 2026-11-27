package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.system.entity.SysUser;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    /** 分页查询: keyword模糊搜用户名/真实姓名/编码, status过滤 */
    PageResult<SysUser> pageQuery(int current, int size, String keyword, String status, String sortBy, String sortOrder);

    /** 详情: 含roleIds */
    SysUser getDetail(Long id);

    /** 新增用户, 密码默认123456 */
    void saveUser(SysUser user);

    /** 更新用户(不更新密码) */
    void updateUser(SysUser user);

    /** 删除用户 */
    void deleteUser(Long id);

    /** 重置密码为123456 */
    void resetPassword(Long id);

    /** 分配角色: 先删除旧关联再插入新关联 */
    void assignRoles(Long userId, List<Long> roleIds);
}
