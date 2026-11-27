package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多语言语言包 - 对齐 PG sys_i18n 表（2026-09-07 L1 立项）
 * <p>
 * 只做显示层翻译：业务表/状态枚举真实值不随语言变化。
 * 缺词回退中文由前端 t() 保证，本表空值不应导致页面空白。
 */
@Data
@TableName("sys_i18n")
public class SysI18n {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 翻译键：如 common.save / menu.销售管理 / page.sale_order.field.customerId */
    @TableField("i18n_key")
    private String i18nKey;

    /** 语言：zh-CN（原文）/ en / 未来 vi 等 */
    @TableField("lang")
    private String lang;

    /** 译文；空字符串等价于缺词（前端回退中文） */
    @TableField("text")
    private String text;

    /** 分组：common/login/layout/menu/page/dict/print */
    @TableField("module")
    private String module;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 管理页对照用中文原文（非数据库字段） */
    @TableField(exist = false)
    private String zhText;
}
