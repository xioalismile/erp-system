package com.erp.export.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 物料导入预检查结果：与物料库做存在性比对后返回冲突清单，供前端展示并选择"覆盖/跳过"。
 * 判定口径：先按物料编码精确匹配，未命中再按物料名称精确匹配。
 */
@Data
public class MaterialImportCheckDTO {

    /** 文件是否解析成功 */
    private boolean parseSuccess = true;

    /** 解析失败原因（parseSuccess=false 时有值） */
    private String parseError;

    /** 文件中的数据行数（不含表头、不含空行） */
    private int totalCount;

    /** 可新增的行数（物料库中不存在） */
    private int newCount;

    /** 冲突行数（物料库中已存在，或文件内自身重复） */
    private int conflictCount;

    /** 校验失败的行数（单位/分类编码不存在、必填缺失等），这些行不参与冲突判定 */
    private int errorCount;

    /** 是否存在冲突（前端据此决定是否强制选择策略） */
    private boolean hasConflict;

    /** 冲突明细 */
    private List<ConflictItem> conflicts = new ArrayList<>();

    /** 行级校验错误（沿用导入的报错结构） */
    private List<ImportResultDTO.ErrorDetail> errors = new ArrayList<>();

    /**
     * 冲突明细项
     */
    @Data
    public static class ConflictItem {
        /** Excel 行号（1-based，含表头行，与导入报错口径一致） */
        private int rowIndex;

        /** 匹配方式/命中字段：字段中文名（如 物料编码 / 物料名称 / 自定义字段名） 或 文件内重复 */
        private String matchType;

        /** 命中的查重字段中文名（文件内重复时同样给出该字段名） */
        private String matchFieldLabel;

        /** 命中字段在 Excel 中的值 */
        private String excelValue;

        /** Excel 中的物料编码 */
        private String excelCode;

        /** Excel 中的物料名称 */
        private String excelName;

        /** Excel 中的规格型号 */
        private String excelSpec;

        /** 库中已存在物料的ID（文件内重复时为 null） */
        private Long existingMaterialId;

        /** 库中已存在的物料编码 */
        private String existingCode;

        /** 库中已存在的物料名称 */
        private String existingName;

        /** 库中已存在的规格型号 */
        private String existingSpec;

        /** 库中已存在的状态 */
        private String existingStatus;
    }
}
