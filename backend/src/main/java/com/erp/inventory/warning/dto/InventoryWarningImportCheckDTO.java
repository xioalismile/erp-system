package com.erp.inventory.warning.dto;

import com.erp.export.dto.ImportResultDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存预警规则导入「预检查」结果：只读不写库，供导入弹窗展示"可新增 / 已存在(冲突) / 校验失败"。
 */
@Data
public class InventoryWarningImportCheckDTO {

    /** 文件是否解析成功 */
    private boolean parseSuccess = true;

    /** 解析失败原因 */
    private String parseError;

    /** 文件数据行数（不含表头、不含空行） */
    private int totalCount;

    /** 可新增（无冲突且校验通过） */
    private int newCount;

    /** 与库中已存在规则冲突（同货品 + 同仓库） */
    private int conflictCount;

    /** 校验失败行数 */
    private int errorCount;

    /** 是否存在冲突 */
    private boolean hasConflict;

    /** 冲突明细 */
    private List<Conflict> conflicts = new ArrayList<>();

    /** 行级校验错误 */
    private List<ImportResultDTO.ErrorDetail> errors = new ArrayList<>();

    @Data
    public static class Conflict {
        private int rowIndex;
        private String materialCode;
        private String materialName;
        private Long warehouseId;
        private String warehouseName;
        /** 库中已存在规则的ID */
        private Long existingId;
    }
}
