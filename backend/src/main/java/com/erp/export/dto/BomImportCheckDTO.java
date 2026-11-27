package com.erp.export.dto;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;

/**
 * BOM导入预检查结果DTO
 * 用于检测BOM导入文件中缺失的物料信息 + 同母件已存在BOM冲突
 */
@Data
public class BomImportCheckDTO {

    /**
     * 文件解析是否成功
     */
    private boolean parseSuccess;

    /**
     * 解析错误信息
     */
    private String parseError;

    /**
     * 文件总行数（不含表头）
     */
    private int totalRows;

    /**
     * 已存在的物料数量
     */
    private int existingMaterialCount;

    /**
     * 缺失的物料数量
     */
    private int missingMaterialCount;

    /**
     * 是否存在同母件已有BOM冲突（前端据此弹出冲突处理Radio）
     */
    private boolean hasConflict;

    /**
     * 是否存在缺失物料未填写分类编码（true=本次导入必须终止，前端据此禁用同步/导入）
     */
    private boolean hasMissingCategory;

    /**
     * 未填写分类编码的缺失物料编码列表
     */
    private List<String> missingCategoryCodes = new ArrayList<>();

    /**
     * 缺失的物料列表
     */
    private List<MissingMaterial> missingMaterials = new ArrayList<>();

    /**
     * 父件物料编码列表（用于创建BOM）
     */
    private List<String> parentMaterialCodes = new ArrayList<>();

    /**
     * 冲突母件列表（系统中已存在同物料BOM的母件）
     */
    private List<ConflictBomInfo> conflictBoms = new ArrayList<>();

    /**
     * 解析的BOM行数据（用于确认导入时使用）
     */
    private List<BomRowData> bomRows = new ArrayList<>();

    /**
     * 缺失物料信息
     */
    @Data
    public static class MissingMaterial {
        /**
         * 物料编码
         */
        private String materialCode;

        /**
         * 物料名称（从Excel解析）
         */
        private String materialName;

        /**
         * 规格型号（从Excel解析）
         */
        private String specification;

        /**
         * 所在行号
         */
        private int rowIndex;

        /**
         * 层级
         */
        private int level;

        /**
         * 是父件还是子件
         */
        private String materialRole; // "父件" 或 "子件"

        /**
         * 建议的物料类型
         */
        private String suggestedType;

        /**
         * 建议的单位
         */
        private String suggestedUnit;

        /**
         * 物料分类编码（来自模板"母件分类编码"/"子件分类编码"；为空=同步时终止导入）
         */
        private String categoryCode;
    }

    /**
     * 同母件已有BOM冲突信息
     */
    @Data
    public static class ConflictBomInfo {
        /** 母件（BOM所属物料）编码 */
        private String materialCode;
        /** 母件名称 */
        private String materialName;
        /** 系统中已有BOM的单据编号 */
        private String existingBomCode;
        /** 已有BOM版本号 */
        private String existingVersion;
        /** 已有BOM单据日期（yyyy-MM-dd字符串或空） */
        private String existingBomDate;
        /** 已有BOM是否默认：true=是 */
        private boolean existingIsDefault;
    }

    /**
     * BOM行数据（含从表头继承来的BOM主属性）
     */
    @Data
    public static class BomRowData {
        private int rowIndex;
        // ========== BOM主属性（统一模板左半部分，从第一行继承） ==========
        /** 单据日期（yyyy-MM-dd或空） */
        private String bomDate;
        /** 母件编号（聚合主键） */
        private String materialCode;
        /** 母件名称（辅助） */
        private String materialName;
        /** BOM分类（标准/工程/生产/成本/委外或空） */
        private String bomCategory;
        /** 版本号或空 */
        private String bomVersion;
        /** 母件数量（字符串或空，解析为BigDecimal） */
        private String quantity;
        /**
         * 模板「单据状态」列原值（草稿/已生效，空=草稿；2026-09-19 起与 22 张单据共用同一套分流口径）。
         * 取该 BOM 分组首行的值：草稿=导入后仅存草稿；已生效=导入后按单据审批开关提交（开=待审核，关=提交即生效）。
         */
        private String billStatus;

        // ========== 子件明细属性（统一模板右半部分） ==========
        /** 层级：空=1 */
        private int level;
        /** 父件编码（多级用；空=继承左半母件编号） */
        private String parentMaterialCode;
        /** 子件编码 */
        private String childMaterialCode;
        /** 子件名称 */
        private String childMaterialName;
        /** 子件规格 */
        private String specification;
        /** 单位字符串 */
        private String unit;
        /** 用量字符串 */
        private String quantityValue; // 避免与主属性quantity重名
        /** 损耗率(%)字符串 */
        private String scrapRate;
        /** 工序号 */
        private String processNo;
        /** 是否关键件：是/否/1/0 兼容解析 */
        private String keyPart;
        /** 行备注（bom_item.remark） */
        private String itemRemark;
        /** 子件分类编码（来自"子件分类编码"列） */
        private String childCategoryCode;

        // 向后兼容：旧BomRowData取旧字段名的getter/setter（JSON序列化不影响）
        public String getQuantity() { return quantityValue; }
        public void setQuantity(String q) { this.quantityValue = q; }
        public String getRemark() { return itemRemark; }
        public void setRemark(String r) { this.itemRemark = r; }
    }
}