package com.erp.kai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小楷独立版用户（FEAT-KAI-STANDALONE-001）。
 * 与主系统 sys_user 完全隔离：独立注册、独立登录、独立 token（scope=kai-standalone）。
 */
@Data
@TableName("kai_user")
public class KaiUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，永不明文存储/回显 */
    private String passwordHash;

    private String displayName;

    private String companyName;

    /** 手机号（客户资料，可空） */
    private String phone;

    /** 邮箱（客户资料，可空） */
    private String email;

    /** 运营备注（仅运营管理员可见/维护） */
    private String remark;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 积分余额（1 积分 = 1000 tokens，默认口径） */
    private java.math.BigDecimal creditBalance;

    /** 累计获得积分 */
    private java.math.BigDecimal creditGranted;

    /** 累计消耗积分 */
    private java.math.BigDecimal creditConsumed;

    /** 1=运营管理员 0=普通用户 */
    private Integer isAdmin;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;
}
