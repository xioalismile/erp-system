package com.erp.price.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 取价结果（按「取价优先级」逐档回落后的最终价格 + 命中的档位）。
 * <p>2026-09-20 价格策略修复：此前价格策略录了不生效、取价优先级配置是摆设，本对象用于把
 * 「价格、来源档位」一并返回给单据编辑页，便于前端回填单价并在必要时提示用户。</p>
 */
@Data
public class PriceFetchResult {

    /** 最终取到的单价；三档全空时为 null（前端保持原价，不阻断） */
    private BigDecimal price;

    /** 命中的档位：strategy=价格策略 / level=价格等级 / material=物料默认价 / none=未取到 */
    private String source;

    /** 档位中文说明（直接可展示） */
    private String sourceLabel;

    /** 入参回显：物料 */
    private Long materialId;

    /** 入参回显：数量（策略的数量区间按此匹配） */
    private BigDecimal quantity;

    /** 快捷判断：是否取到了有效价格 */
    public boolean hasPrice() {
        return price != null;
    }
}
