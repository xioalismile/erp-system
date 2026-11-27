package com.erp.export.registry;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.bill.link.BillLinkRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 「单据台账 {@code BillLinkRegistry}」×「导入导出登记表 {@link BillExcelRegistry}」交叉校验。
 *
 * <p><b>这是"新增了一张单据、却漏配导入导出"的自动检测</b>：台账里每登记一张单据，
 * 导入导出登记表里就必须有承载同一张表的实体；否则测试直接红，不用靠人记得。</p>
 *
 * <p><b>为什么按「数据库表名」匹配，而不是中文名</b>：台账的中文名是 {@code source_bill_type} 的
 * <b>数据口径</b>（库里存的就是中文历史值，{@code FinanceServiceImpl} 里
 * {@code case "应收单"/"预付款单"/"应付单"} 就是按这些值分流的），**不能改**；
 * 而用户日常看到的是页面标题（如台账"生产订单" / 页面"生产工单"）。
 * 两者本来就不是一回事，用中文名做匹配只会被叫法差异干扰。
 * 表名才是同一张单据的稳定身份。</p>
 *
 * <p>叫法本身的统一由 {@link BillExcelRegistry} 的中文名负责（对齐前端页面标题），
 * 并由下面第 3 个用例保证「台账按页面名也能反查到单据类型」。</p>
 *
 * <p>跑法：{@code mvn -o test -Derp.test.skip=false -Dmaven.compiler.useIncrementalCompilation=false -Dtest=BillLinkCrossCheckTest}</p>
 */
@DisplayName("单据台账 × 导入导出登记表 交叉校验")
class BillLinkCrossCheckTest {

    @Test
    @DisplayName("台账里每张单据都必须在导入导出登记表里有对应实体（按数据库表名匹配）")
    void everyLedgerBillIsRegisteredForExportImport() {
        BillLinkRegistry ledger = new BillLinkRegistry();
        Map<String, String> codeByTable = codeByTable();
        List<String> missing = new ArrayList<>();
        for (String docType : ledger.allDocTypes()) {
            BillLinkRegistry.DocDef d = ledger.doc(docType);
            if (!codeByTable.containsKey(d.table)) {
                missing.add(docType + " / 表=" + d.table + " / 台账名=" + d.name);
            }
        }
        assertEquals(0, missing.size(),
                "以下单据在台账里有、但导入导出登记表没有对应实体（新增单据时漏登记）：\n  "
                        + String.join("\n  ", missing));
    }

    @Test
    @DisplayName("登记表不得有两个实体映射同一张数据库表")
    void noDuplicateTableMapping() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> duplicated = new ArrayList<>();
        for (BillExcelRegistry.Bill b : BillExcelRegistry.all()) {
            String prev = seen.put(tableOf(b.entityClass()), b.code());
            if (prev != null) {
                duplicated.add(tableOf(b.entityClass()) + " ← " + prev + " 与 " + b.code());
            }
        }
        assertEquals(0, duplicated.size(), "同一张表被登记了两次: " + duplicated);
    }

    @Test
    @DisplayName("台账必须能按「用户看到的页面名」反查到单据类型（叫法统一的可执行保障）")
    void ledgerCanResolveByPageName() {
        BillLinkRegistry ledger = new BillLinkRegistry();
        Map<String, String> codeByTable = codeByTable();
        // 同一张表承载多个单据类型（生产领料/退料/补料共用领料表，靠 bill_type 区分）时，
        // 页面名天然只能反查到其中一个（用户看到的就是同一个「生产领料单」页面）。
        // 故断言口径为：反查结果必须落在「同一张表」的单据类型集合里，而不是必须等于当前循环到的类型
        // （2026-11-11 修复：原先按严格相等，导致退料/补料两种类型必然误报）。
        Map<String, Set<String>> typesByTable = new LinkedHashMap<>();
        for (String docType : ledger.allDocTypes()) {
            typesByTable.computeIfAbsent(ledger.doc(docType).table, k -> new LinkedHashSet<>()).add(docType);
        }
        List<String> problems = new ArrayList<>();
        for (String docType : ledger.allDocTypes()) {
            BillLinkRegistry.DocDef d = ledger.doc(docType);
            String code = codeByTable.get(d.table);
            if (code == null) {
                continue; // 缺登记由第 1 个用例负责报错，这里不重复报
            }
            String pageName = BillExcelRegistry.get(code).name();
            String resolved = ledger.docTypeByName(pageName);
            Set<String> sameTable = typesByTable.getOrDefault(d.table, Set.of());
            if (resolved == null || !sameTable.contains(resolved)) {
                problems.add("页面名「" + pageName + "」反查不到 " + docType + " 所在表的任何单据类型"
                        + "（实际解析为 " + resolved + "，同表类型=" + sameTable + "，台账主名=「" + d.name + "」）");
            }
        }
        assertEquals(0, problems.size(),
                "以下页面名无法反查成单据类型（来源单据展示 / 跳转口径会断）：\n  "
                        + String.join("\n  ", problems));
    }

    private Map<String, String> codeByTable() {
        Map<String, String> map = new LinkedHashMap<>();
        for (BillExcelRegistry.Bill b : BillExcelRegistry.all()) {
            map.put(tableOf(b.entityClass()), b.code());
        }
        assertNotNull(map);
        return map;
    }

    /** 实体对应的数据库表名：优先 @TableName，无注解时按 MyBatis-Plus 的驼峰转下划线规则推导 */
    private String tableOf(Class<?> clazz) {
        TableName ann = clazz.getAnnotation(TableName.class);
        if (ann != null && !ann.value().isBlank()) {
            return ann.value();
        }
        return clazz.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
