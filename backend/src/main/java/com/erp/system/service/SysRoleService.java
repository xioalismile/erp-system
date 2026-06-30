package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.system.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    /** 分页查询: keyword模糊搜角色编码/角色名称 */
    PageResult<SysRole> pageQuery(int current, int size, String keyword);

    /** 详情: 含menuIds */
    SysRole getDetail(Long id);

    /** 新增角色 */
    void saveRole(SysRole role);

    /** 更新角色 */
    void updateRole(SysRole role);

    /** 删除角色 */
    void deleteRole(Long id);

    /** 分配权限: 先删除旧关联再插入新关联 */
    void assignPermissions(Long roleId, List<Long> menuIds);

    /** 全量列表 */
    List<SysRole> listAll();
}
