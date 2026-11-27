package com.erp.export.registry;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.export.annotation.ExportField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入导出「单一登记表」完整性回归测试（纯 JUnit，不依赖 Spring 与数据库，任何时候都能跑）。
 *
 * <p><b>存在意义</b>：改造前"一张单据要在 10 个地方各登记一次"（ENTITY_CLASS_MAP / getMapper /
 * SPECIAL_IMPORT_ENTITIES / 四个模板 Set / EXPORT_ONLY / exportTemplate 前缀分派 / importData 24 个 else-if /
 * 三张模块实体类 Map / 两张文件名单），补了销售订单就会漏委外与生产，且漏了不报错、只在用户点导出时
 * 以 HTTP 200 + "不支持的实体类型" 的形式暴露。现在全部由 {@link BillExcelRegistry} 派生，本测试负责把
 * "登记表与事实不一致"变成**构建期红灯**，而不是用户现场踩雷。</p>
 *
 * <p>跑法（项目默认跳过测试）：{@code mvn -o test -Derp.test.skip=false -Dtest=BillExcelRegistryIntegrityTest}</p>
 */
@DisplayName("导入导出登记表完整性")
class BillExcelRegistryIntegrityTest {

    /**
     * 专用导出行白名单：这些实体**没有** @ExportField，导出列由 ExcelService 里的专用方法写死位置行。
     * 新增此类实体时必须同步登记到 ExcelService 的专用导出方法，否则导出会得到一张空表。
     * 当前有：「库存现存量」（inventory_current）：实体零个 @ExportField，且用户要看的
     * 物料编码/名称/规格、仓库编码/名称、单位名全是 @TableField(exist=false) 的关联字段；
     * 以及「销售/采购价格策略」（表头+明细结构，导出/模板/导入共用一套扁平列，一行=一个对象的价格）。
     */
    private static final Set<String> DEDICATED_EXPORT_CODES = Set.of("inventory", "salePriceStrategy", "purchasePriceStrategy");

    @Test
    @DisplayName("登记项字段完整：code / 中文名 / 实体类 / Mapper / 分组 都不能为空")
    void everyBillHasCompleteMetadata() {
        List<String> problems = new ArrayList<>();
        for (BillExcelRegistry.Bill b : BillExcelRegistry.all()) {
            if (isBlank(b.code())) {
                problems.add("code 为空: " + b);
            }
            if (isBlank(b.name())) {
                problems.add("中文名为空: " + b.code());
            }
            if (b.entityClass() == null) {
                problems.add("实体类为空: " + b.code());
            }
            if (b.mapperClass() == null) {
                problems.add("Mapper 为空: " + b.code());
            }
            if (b.group() == null) {
                problems.add("模板分组为空: " + b.code());
            }
        }
        assertEquals(0, problems.size(), "登记表存在不完整项（会把「漏登记」重新变回现场故障）：" + problems);
    }

    @Test
    @DisplayName("code 全局唯一（重复 code 会让后登记的静默覆盖前面的）")
    void codesAreUnique() {
        Set<String> seen = new HashSet<>();
        List<String> duplicated = new ArrayList<>();
        for (BillExcelRegistry.Bill b : BillExcelRegistry.all()) {
            if (!seen.add(b.code())) {
                duplicated.add(b.code());
            }
        }
        assertEquals(0, duplicated.size(), "登记表存在重复 code: " + duplicated);
    }

    @Test
    @DisplayName("Mapper 必须是 MyBatis-Plus BaseMapper 子类（否则 getMapper 取不到导致导出报错）")
    void mapperClassesAreBaseMappers() {
        List<String> problems = new ArrayList<>();
        for (BillExcelRegistry.Bill b : BillExcelRegistry.all()) {
            if (b.mapperClass() != null && !BaseMapper.class.isAssignableFrom(b.mapperClass())) {
                problems.add(b.code() + " -> " + b.mapperClass().getName());
            }
        }
        assertEquals(0, problems.size(), "以下 Mapper 不是 BaseMapper 子类: " + problems);
    }

    @Test
    @DisplayName("可导出实体必须有导出列：@ExportField 或落在专用导出行白名单内")
    void exportableEntitiesHaveColumns() {
        List<String> problems = new ArrayList<>();
        for (BillExcelRegistry.Bill b : BillExcelRegistry.all()) {
            if (!b.exportable() || DEDICATED_EXPORT_CODES.contains(b.code())) {
                continue;
            }
            if (countExportFields(b.entityClass()) == 0) {
                problems.add(b.code() + "(" + b.entityClass().getSimpleName() + ")");
            }
        }
        assertEquals(0, problems.size(),
                "以下实体登记为可导出但没有任何 @ExportField，导出会得到「只有空表头/空文件」："
                        + problems + "；若确实要用写死列导出，请加入 DEDICATED_EXPORT_CODES 并实现专用导出方法");
    }

    @Test
    @DisplayName("回归锚点：历史上导出报「不支持的实体类型」的实体必须仍然登记为可导出")
    void brokenEntitiesStayRegistered() {
        // 这 12 张 + 库存现存量是 2026-09-15 用户实测"导出必报错"的清单，作为回归锚点固化。
        String[] mustBeExportable = {
                // 委外 5 张（曾整块缺失）
                "subcontract", "subcontractPick", "subcontractInbound", "subcontractReturn", "subcontractSupplement",
                // 库存 3 张 + 库存查询视图
                "stockTransfer", "stockCheck", "otherReceipt", "inventory",
                // 财务 4 张
                "paymentVoucher", "purchasePrePayment", "salePreReceipt", "operationExpense"
        };
        List<String> problems = new ArrayList<>();
        for (String code : mustBeExportable) {
            BillExcelRegistry.Bill b = BillExcelRegistry.get(code);
            if (b == null) {
                problems.add(code + ": 未登记");
            } else if (!b.exportable()) {
                problems.add(code + ": 登记为不可导出");
            }
        }
        assertEquals(0, problems.size(), "回归锚点失效（这些单据又会导出失败）：" + problems);
    }

    @Test
    @DisplayName("查询视图与系统生成单据不得可导入（它们没有单号/状态，不能新增）")
    void viewsAndGeneratedBillsAreNotImportable() {
        String[] mustNotImport = {"inventory", "accountsReceivable", "accountsPayable"};
        List<String> problems = new ArrayList<>();
        for (String code : mustNotImport) {
            BillExcelRegistry.Bill b = BillExcelRegistry.get(code);
            if (b == null) {
                problems.add(code + ": 未登记");
            } else if (b.importable()) {
                problems.add(code + ": 被登记为可导入");
            }
        }
        assertEquals(0, problems.size(), "以下实体不该有导入入口: " + problems);
    }

    @Test
    @DisplayName("未开放导出的实体必须正好是已登记清单（防止有人悄悄关掉某单据的导出）")
    void notExportableEntitiesAreDocumented() {
        // A 类·待补导出列（有页面，实体零个 @ExportField）：打样单 / 免费样品单
        // B 类·无前端入口，按设计不开放（用户 2026-09-15 拍板保留后端不补入口）：
        //        采购费用单 / 销售费用单 / 采购结算单 / 销售结算单
        Set<String> documentedPending = Set.of(
                "sampleOrder", "freeSampleOrder",
                "purchaseExpense", "saleExpense", "purchaseStatement", "saleStatement");
        List<String> unexpected = new ArrayList<>();
        for (BillExcelRegistry.Bill b : BillExcelRegistry.all()) {
            if (!b.exportable() && !documentedPending.contains(b.code())) {
                unexpected.add(b.code());
            }
        }
        assertEquals(0, unexpected.size(),
                "以下实体被关闭了导出但不在已登记清单里（要么是误关，要么该更新这份清单）：" + unexpected);
    }

    @Test
    @DisplayName("委外 5 张必须仍登记（当前可导不可入，补齐导入后请同步更新本断言）")
    void subcontractBillsAreRegistered() {
        String[] subcontracts = {"subcontract", "subcontractPick", "subcontractInbound",
                "subcontractReturn", "subcontractSupplement"};
        for (String code : subcontracts) {
            assertTrue(BillExcelRegistry.exists(code), code + " 未登记（委外单据导出会再次报错）");
        }
    }

    private int countExportFields(Class<?> entityClass) {
        if (entityClass == null) {
            return 0;
        }
        int count = 0;
        for (Field f : entityClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(ExportField.class)) {
                count++;
            }
        }
        return count;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
