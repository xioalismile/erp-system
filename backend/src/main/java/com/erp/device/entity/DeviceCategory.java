package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备分类（树形）
 * 对齐 PG device_category 表
 */
@Data
@TableName("device_category")
public class DeviceCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 分类编码 */
    @TableField("category_code")
    private String categoryCode;

    /** 分类名称 */
    @TableField("category_name")
    private String categoryName;

    /** 父级分类ID，空=根节点 */
    @TableField("parent_id")
    private Long parentId;

    @TableField("sort_order")
    private Integer sortOrder;

    /** 是否系统预置：1 是（不可删除）/ 0 否 */
    @TableField("is_system")
    private Integer isSystem;

    /** 状态：启用 / 停用 */
    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 子分类（非数据库字段，树形返回用） */
    @TableField(exist = false)
    private List<DeviceCategory> children;

    /** 该分类下设备数（非数据库字段，列表提示用） */
    @TableField(exist = false)
    private Long deviceCount;
}
