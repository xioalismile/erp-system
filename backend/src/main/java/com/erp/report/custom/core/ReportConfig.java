package com.erp.report.custom.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 自定义报表配置（config_json 的结构）
 *
 * 所有 field 引用均为 {source, field} 二元组：
 *   source = "main"（主表）、注册表中的 joinKey（表头/基础资料）
 *           或 "extN"（跨单据关联目标主表）、"extN.joinKey"（目标的表头/基础资料）
 *   field  = 注册表元数据中的 Java 字段名（camelCase）
 *
 * 批次2扩展（向后兼容：旧配置无 mode/extraJoins 时默认明细模式）：
 *   mode        detail（默认）/ summary
 *   groupBy     汇总模式分组字段（1~5）
 *   aggregates  汇总模式聚合指标（1~10，func=SUM/COUNT/AVG/MAX/MIN）
 *   extraJoins  跨单据关联（0~2，目标数据源 + 同名外键列关联条件）
 *   chart       图表默认类型（bar/pie/line，汇总模式可选）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportConfig {

    /** 报表模式：detail=明细（默认），summary=汇总 */
    public String mode;

    /** 显示列（明细模式，顺序即列顺序），上限 60 列 */
    public List<ColRef> columns;

    /** 计算列（0~10）：明细模式 = 数值字段/更早计算列 ±×÷ 数值字段/更早计算列/常量；
     *  汇总模式 = 聚合指标 ±×÷ 聚合指标/常量（如 SUM(金额)×0.13=税额） */
    public List<CalcCol> calcColumns;

    /** 汇总模式：分组字段（1~5） */
    public List<ColRef> groupBy;

    /** 汇总模式：聚合指标（1~10） */
    public List<AggRef> aggregates;

    /** 跨单据关联（0~2） */
    public List<ExtraJoin> extraJoins;

    /** 日期筛选字段（可选） */
    public ColRef dateField;

    /** 关键字筛选字段（LIKE，可选） */
    public ColRef keywordField;

    /** 状态筛选字段（可选） */
    public ColRef statusField;

    /** 排序（可选，最多 3 个；汇总模式下 source="agg" 表示按聚合列排序） */
    public List<OrderRef> orderBy;

    /** 图表配置（汇总模式可选） */
    public Chart chart;

    /**
     * 默认筛选条件（可选，0~20 条）：报表保存时预置的任意字段过滤条件（含自定义字段）。
     *
     * <p>生效口径：用户在运行页**没有**自带条件时用这里的默认值；用户一旦自己加了条件，
     * 以运行时的为准（避免"清不掉配置条件"的困扰）。运行页打开时会把这里的条件预填成可编辑行。
     */
    public List<Condition> filters;

    /**
     * 汇总结果条件（可选，0~20 条，HAVING）：对聚合结果再过滤，
     * 如"今年销售数量 &gt; 1000 的物料"。source=agg，field=聚合输出名。
     */
    public List<Condition> having;

    /**
     * 行下钻目标（可选，0~4 个）：汇总行点击后可跳到目标单据列表，
     * 按 keyField（本报表输出列）→ param（目标列表的参数名）传值。
     */
    public List<DrillTarget> drill;

    public boolean isSummary() {
        return "summary".equalsIgnoreCase(mode);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColRef {
        public String source;
        public String field;
        /** 分组日期粒度（仅汇总模式分组字段且为日期类型时生效）：day(默认)/month/quarter/year */
        public String granularity;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderRef extends ColRef {
        /** asc / desc */
        public String dir;
    }

    /** 聚合指标：func=SUM/COUNT/AVG/MAX/MIN；COUNT 时 field 可为空（COUNT(*)计行数） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AggRef extends ColRef {
        public String func;
        /** 显示名（可选，缺省自动生成） */
        public String label;
        /** 较上期环比（可选，"pct"=生成环比%列，红涨绿跌展示） */
        public String compare;
        /**
         * 本指标自己的筛选条件（可选，0~10 条）：只影响这一列的汇总口径，
         * 如"今年销售数量"只统计本年订单、"前年销售数量"只统计前年订单。
         */
        public List<Condition> filters;
        /**
         * 输出列名（可选）：同一字段被多次聚合（今年数量/前年数量）时，
         * 用它区分输出列；留空按 函数_字段 自动生成。
         */
        public String alias;
    }

    /** 跨单据关联：目标数据源 + 左右字段（左右分别任选，类型兼容即可，值匹配语义） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtraJoin {
        /** 目标数据源 key（白名单） */
        public String dataSourceKey;
        /** 主侧字段（source=main 或主数据源 joinKey） */
        public ColRef leftField;
        /** 目标侧字段（source="main" 表示目标主表，或目标树 joinKey，构建时解析） */
        public ColRef rightField;
        /**
         * 聚合挂载（可选）：非空时目标单先按关联键 GROUP BY 聚合成子查询再挂回主表，
         * 杜绝一对多平铺 JOIN 导致的行数翻倍/金额虚增（fan-out）。
         * 此模式下 rightField.source 必须为 "main"（仅目标主表字段）。
         */
        public List<AggField> aggFields;
        /**
         * 两跳追溯（可选，仅聚合挂载模式生效）：目标单与主表无直接单号关联、
         * 需经中间单据（跳板）追溯时配置。
         * 语义链：主表.leftField = 跳板.mainField，跳板.targetField = 目标.rightField。
         * 典型场景：销售订单追数采购入库（入库单来源单号=采购订单号，
         * 采购订单来源单号=销售订单号，中间隔采购订单一跳）。
         */
        public ViaJoin via;
    }

    /** 两跳追溯跳板配置（ExtraJoin.via）：跳板表字段与目标表/主表的关联键 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ViaJoin {
        /** 跳板数据源 key（白名单，如 purchase_order） */
        public String dataSourceKey;
        /** 跳板表上与目标表关联的字段（如 orderNo：跳板单号，对应目标表的来源单号） */
        public String targetField;
        /** 跳板表上指向主表的字段（如 sourceBillNo：跳板的来源单号，对应主表单号） */
        public String mainField;
    }

    /** 聚合挂载指标：目标主表字段 + 聚合函数 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AggField {
        /** 字段来源表：空=目标主表；否则=目标明细表key（如 sale_delivery_item，取明细数量用） */
        public String source;
        /** 目标字段名；func=COUNT 时可空（COUNT 计单据数） */
        public String field;
        /** SUM / COUNT / AVG */
        public String func;
        /** 显示名（可选，缺省自动生成） */
        public String label;
        /**
         * 本聚合指标自己的筛选条件（可选，0~10 条）：在目标单据子查询内生效，
         * 如"今年销售数量"只统计本年、且排除草稿/作废。
         */
        public List<Condition> filters;
        /**
         * 输出字段名（可选）：同一字段多次聚合（今年数量/前年数量）时用它区分，
         * 外部按 source=ext{i}、field=alias 引用；留空 = agg_{字段名}。
         */
        public String alias;
    }

    /**
     * 计算列（批次3增强）：
     * - op 白名单：+ - * /（除数为 0 或空时结果为 NULL，前端显示空白）
     * - refA/refB 数值字段引用；明细模式下 source 可为 "calc"（field=calcN，仅可引用更早定义的计算列，链式运算）
     * - 汇总模式下 refA/refB 的 source 须为 "agg"（field=聚合指标输出别名）
     * - refB 为空时可用 constB 常量（仅数字），如 SUM(金额)×0.13
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalcCol {
        public ColRef refA;
        public ColRef refB;
        /** "+" / "-" / "*" / "/" */
        public String op;
        /** 常量操作数（refB 为空时生效，仅允许数字字符，防注入） */
        public String constB;
        /** 显示名（可选，缺省自动生成 A±B） */
        public String label;
        /**
         * 自由公式（可选，优先级高于 refA/op/refB）：
         * 支持字段引用 [source.field]（汇总模式为 [聚合输出名]）、数字、+ - * /、括号，
         * 以及 ROUND(x,n) / ABS(x) / COALESCE(x,y) / TOTAL(x)（占全体合计）。
         * 例：[sum_quantity] / TOTAL([sum_quantity]) 得到占比。
         */
        public String formula;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chart {
        /** bar / pie / line */
        public String type;
    }

    /**
     * 任意字段过滤条件（报表配置 filters 与运行时 conditions 共用同一结构）。
     *
     * op 白名单：eq/ne/gt/ge/lt/le/like/notLike/between/in/notIn/isNull/isNotNull
     * value/value2 为单值（between 用两个），values 为多值（in/notIn）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        /** main（默认）/ 注册表 joinKey / extN / extN.joinKey */
        public String source;
        /** 字段名（系统字段 camelCase 或自定义字段 cf_xxx） */
        public String field;
        public String op;
        public String value;
        public String value2;
        public List<String> values;
        /**
         * 与**上一条**条件的连接符：AND（默认，可省略）/ OR。
         * 连续多条 OR 会归入同一个括号组，组与组之间为 AND。
         * 语义示例：[A, B(logic=OR), C(logic=AND), D(logic=OR)] → A AND (B OR C) AND D
         */
        public String logic;
        /**
         * 相对时间（可选，仅日期字段）：填写后忽略 value/value2，按运行时自动换算区间。
         * THIS_YEAR=今年 / LAST_YEAR=去年 / YEAR_BEFORE=前年 /
         * THIS_MONTH=本月 / LAST_MONTH=上月 / LAST_N_MONTHS=近N月（N 取 value）。
         */
        public String relative;
    }

    /** 下钻目标：汇总行 → 目标单据列表（按本报表输出列传参） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DrillTarget {
        /** 显示名，如"销售订单明细" */
        public String label;
        /** 前端路由，如 /sale/orders */
        public String route;
        /** 本报表输出列名（分组列或指标列的输出字段） */
        public String keyField;
        /** 目标列表接收的参数名，如 materialCode */
        public String param;
        /** 附加固定参数（可选），如 {status:"已审核"} */
        public java.util.Map<String, String> extraParams;
    }
}
