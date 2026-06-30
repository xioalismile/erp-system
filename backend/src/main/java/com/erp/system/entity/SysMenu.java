package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单主表 - 对齐 PG sys_menu 表
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(value = "menu_id", type = IdType.AUTO)
    private Long menuId;

    @TableField("menu_code")
    private String menuCode;

    @TableField("menu_name")
    private String menuName;

    @TableField("parent_menu_id")
    private Long parentMenuId;

    @TableField("menu_type")
    private String menuType;

    @TableField("icon")
    private String icon;

    @TableField("route_path")
    private String routePath;

    @TableField("component_path")
    private String componentPath;

    @TableField("is_visible")
    private Short isVisible;

    @TableField("is_cache")
    private Short isCache;

    @TableField("is_enabled")
    private Short isEnabled;

    @TableField("keepalive")
    private Short keepalive;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("permission_code")
    private String permissionCode;

    @TableField("external_link")
    private String externalLink;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    /** 子菜单列表(非数据库字段, 用于树形结构) */
    @TableField(exist = false)
    private List<SysMenu> children;
}
