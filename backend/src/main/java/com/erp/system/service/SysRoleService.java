package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.system.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    /** 分页查询: keyword模糊搜角色编码/角色名称 */
    PageResult<SysRole> pageQuery(int current, int size, String keyword, String sortBy, String sortOrder);

    /** 详情: 含menuIds */
    SysRole getDetail(Long id);

    /** 新增角色 */
    void saveRole(SysRole role);

    /** 更新角色 */
    void updateRole(SysRole role);

    /** 删除角色 */
    void deleteRole(Long id);

    /** 分配权限: 先删除旧关联再插入新关联 menuIds是菜单path如 /sale/orders */
    void assignPermissions(Long roleId, List<String> menuIds);

    /** 获取角色按钮权限码列表 */
    List<String> getButtonPermissions(Long roleId);

    /** 分配按钮权限: 先删旧再插新 buttonCodes如 sale:sale-order:add */
    void assignButtonPermissions(Long roleId, List<String> buttonCodes);

    /** 全量列表 */
    List<SysRole> listAll();
}
