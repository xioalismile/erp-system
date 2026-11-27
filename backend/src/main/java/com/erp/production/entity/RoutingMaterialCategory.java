package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工艺路线 ↔ 适用分类（多选）。
 *
 * <p>按分类绑定时，该分类及其下级分类下的物料都视为适用；与质检模板「适用分类」同一口径。</p>
 */
@Data
@TableName("routing_material_category")
public class RoutingMaterialCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("routing_id")
    private Long routingId;

    @TableField("category_id")
    private Long categoryId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
