package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.system.entity.SysMenu;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {

    /** 全量列表 */
    List<SysMenu> listAll();

    /** 树形结构(递归构建children) */
    List<SysMenu> getTree();

    /** 根据用户角色获取可见菜单树(admin返回全部菜单) */
    List<SysMenu> getTreeByUser(Long userId, boolean isAdmin);

    /** 详情 */
    SysMenu getDetail(Long id);

    /** 新增菜单 */
    void saveMenu(SysMenu menu);

    /** 更新菜单 */
    void updateMenu(SysMenu menu);

    /** 删除菜单(检查是否有子菜单, 有则拒绝) */
    void deleteMenu(Long id);
}
