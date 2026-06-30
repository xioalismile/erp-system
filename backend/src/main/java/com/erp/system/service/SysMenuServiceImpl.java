package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.system.entity.SysMenu;
import com.erp.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Override
    public List<SysMenu> listAll() {
        QueryWrapper<SysMenu> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_order");
        return list(wrapper);
    }

    @Override
    public List<SysMenu> getTree() {
        List<SysMenu> all = listAll();
        // 找出根节点(parent_menu_id 为空)
        List<SysMenu> roots = all.stream()
                .filter(m -> m.getParentMenuId() == null)
                .collect(Collectors.toList());
        // 递归构建子节点
        for (SysMenu root : roots) {
            buildChildren(root, all);
        }
        return roots;
    }

    /** 递归构建子菜单 */
    private void buildChildren(SysMenu parent, List<SysMenu> all) {
        List<SysMenu> children = all.stream()
                .filter(m -> parent.getMenuId().equals(m.getParentMenuId()))
                .collect(Collectors.toList());
        if (!children.isEmpty()) {
            parent.setChildren(children);
            for (SysMenu child : children) {
                buildChildren(child, all);
            }
        } else {
            parent.setChildren(new ArrayList<>());
        }
    }

    @Override
    public SysMenu getDetail(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) throw new BusinessException("菜单不存在");
        return menu;
    }

    @Override
    @Transactional
    public void saveMenu(SysMenu menu) {
        if (menu.getMenuCode() == null || menu.getMenuCode().isBlank()) {
            throw new BusinessException("菜单编码不能为空");
        }
        if (exists(new QueryWrapper<SysMenu>().eq("menu_code", menu.getMenuCode()))) {
            throw new BusinessException("菜单编码已存在");
        }
        if (menu.getIsVisible() == null) menu.setIsVisible((short) 1);
        if (menu.getIsCache() == null) menu.setIsCache((short) 0);
        if (menu.getIsEnabled() == null) menu.setIsEnabled((short) 1);
        if (menu.getKeepalive() == null) menu.setKeepalive((short) 1);
        if (menu.getSortOrder() == null) menu.setSortOrder(0);
        save(menu);
        log.info("新增菜单: {} 编码: {}", menu.getMenuName(), menu.getMenuCode());
    }

    @Override
    @Transactional
    public void updateMenu(SysMenu menu) {
        if (menu.getMenuId() == null) throw new BusinessException("菜单ID不能为空");
        if (exists(new QueryWrapper<SysMenu>().eq("menu_code", menu.getMenuCode()).ne("menu_id", menu.getMenuId()))) {
            throw new BusinessException("菜单编码已存在");
        }
        updateById(menu);
        log.info("更新菜单: id={}", menu.getMenuId());
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) throw new BusinessException("菜单不存在");
        // 检查是否有子菜单
        long childCount = count(new QueryWrapper<SysMenu>().eq("parent_menu_id", id));
        if (childCount > 0) {
            throw new BusinessException("存在子菜单, 不允许删除");
        }
        removeById(id);
        log.info("删除菜单: id={}", id);
    }
}
