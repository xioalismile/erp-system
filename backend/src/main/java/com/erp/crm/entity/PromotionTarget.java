package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 推广记录明细（2026-10-07）
 *
 * <p>记录一条推广发送到了哪些客户。RFID 素材覆盖率必须按客户粒度去重统计，
 * 只存一个数量算不出覆盖率，故独立成表。</p>
 */
@Data
@TableName("crm_promotion_target")
public class PromotionTarget {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("promotion_id")
    private Long promotionId;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;
}
