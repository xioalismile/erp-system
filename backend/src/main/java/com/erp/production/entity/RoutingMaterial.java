package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工艺路线 ↔ 适用物料（多选）。
 *
 * <p>一条工艺路线可绑定多个物料；工艺路线主表保留 material_id 作为「第一个物料」的兼容字段，
 * 老逻辑（BOM 带出、工单取主工艺路线）继续可用。</p>
 */
@Data
@TableName("routing_material")
public class RoutingMaterial {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("routing_id")
    private Long routingId;

    @TableField("material_id")
    private Long materialId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
