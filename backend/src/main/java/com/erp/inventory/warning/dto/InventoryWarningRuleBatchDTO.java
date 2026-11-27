package com.erp.inventory.warning.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存预警规则「批量新增」入参：一次提交一批货品，每个货品生成一条规则。
 *
 * <p>共用部分（通知方式 / 通知人 / 备注）放在表头；货品、仓库、预警类型、上下限放在 lines（一个货品一行）。</p>
 */
@Data
public class InventoryWarningRuleBatchDTO {

    /**
     * 通知方式（按位标记：1=系统消息，2=弹窗（预留），4=邮件，8=短信）。
     * 当前仅「系统消息」可用，其余为后续功能预留。
     */
    private Integer notifyType;

    /** 通知人（多个用英文逗号分隔，存员工姓名） */
    private String notifyUsers;

    /** 备注（共用） */
    private String remark;

    /** 货品明细：一行一个货品 */
    private List<Line> lines;

    @Data
    public static class Line {
        /** 货品ID（必填） */
        private Long materialId;
        /** 仓库ID（可空 = 全部仓库） */
        private Long warehouseId;
        /** 预警类型：MIN 最低库存 / MAX 最高库存 / BOTH 双向 */
        private String warningType;
        /** 最低库存 */
        private BigDecimal minQuantity;
        /** 最高库存 */
        private BigDecimal maxQuantity;
        /** 状态：1启用 0停用（可空，默认启用） */
        private Integer status;
    }
}
