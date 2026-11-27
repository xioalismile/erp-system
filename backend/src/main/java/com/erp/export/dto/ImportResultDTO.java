package com.erp.export.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResultDTO {
    private int totalCount;
    private int successCount;
    private int failCount;
    /** 跳过的行数（如旧模板根节点行、按策略跳过的冲突母件下所有行） */
    private Integer skipCount;
    private int createdMaterialCount;
    /** 被覆盖（更新）的已存在物料数，仅物料导入按 OVERWRITE 策略时使用 */
    private int updatedCount;
    /**
     * 因模板「单据状态」列填「已生效」而已自动提交的单据数（2026-09-19）：
     * 审批开关开 → 已转入审批流程（待审核+待办）；审批开关关 → 已提交即生效。
     */
    private int effectiveSubmittedCount;
    /** 汇总提示消息，可选 */
    private String message;
    private List<ErrorDetail> errors = new ArrayList<>();
    /** 本次导入实际生成的 BOM 编码（成功/部分成功时回传，便于用户核对） */
    private List<String> bomCodes = new ArrayList<>();

    @Data
    public static class ErrorDetail {
        private int rowIndex;
        private String fieldName;
        private String message;
    }

    public void addError(int rowIndex, String fieldName, String message) {
        ErrorDetail error = new ErrorDetail();
        error.setRowIndex(rowIndex);
        error.setFieldName(fieldName);
        error.setMessage(message);
        this.errors.add(error);
    }
}