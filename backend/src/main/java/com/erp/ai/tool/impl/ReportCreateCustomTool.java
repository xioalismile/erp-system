package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.report.custom.entity.CustomReport;
import com.erp.report.custom.service.CustomReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 写工具：AI对话式创建自定义报表（走服务端白名单校验，校验失败返回明确错误供AI引导用户修正）。
 * 创建成功后报表出现在"自定义报表"列表，可直接运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCreateCustomTool implements AiTool {

    private final CustomReportService customReportService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "report_create_custom";
    }

    @Override
    public String getDescription() {
        return "创建一张自定义报表（支持明细模式和汇总模式）。config的字段引用必须来自 report_list_datasources 返回的字段清单"
                + "（后端有白名单校验，不在清单内的字段会被拒绝）。"
                + "字段引用中的source：主表字段用\"main\"（也可用数据源key本身，两者等同），"
                + "关联表字段用report_list_datasources返回的joins里对应join的key，"
                + "跨单关联挂载字段用\"ext{i}\"（extraJoins里第i个关联，从0计）。"
                + "\n【铁律0·模式选择】先判断用户要的是清单还是统计：用户要「清单/列表/逐单/明细行/每一笔」→ 明细模式"
                + "（mode=detail或省略，给columns列清单）；用户要「统计/汇总/对比/趋势/占比/排名/按月按年」→ 汇总模式"
                + "（mode=summary，给groupBy+aggregates）；两种特征都没有、拿不准时，先问用户一句再创建，"
                + "严禁把「逐单清单」类需求默认做成汇总模式。"
                + "\n【铁律1】跨单跟踪需求（如订单发了多少货/收了多少款/采购入库进度——对比一张单和它的下游单据）"
                + "必须用 extraJoins 聚合挂载：关联键通常用单号文本字段（如主表orderNo = 目标表来源单号sourceBillNo），"
                + "并配 aggFields。聚合数量时必须用明细表字段（主表无数量的汇总字段）："
                + "aggField带source=明细表key（如sale_delivery_item）+field=quantity，如已发数量=SUM(出库明细.quantity)；"
                + "聚合金额用主表字段（如已收金额=SUM(收款单.amount)）；单据数=COUNT(field省略)。"
                + "同一关联内主表字段与明细字段不可混配（要数量和金额时拆成两个关联分别挂载）。"
                + "\n【铁律1.5·三件套】用户要\"业务链/还有多少没X\"类报表时，每条链必须配全三件套："
                + "总量+已完成+剩余（如订单数量/已发数量/未发数量，订单金额/已收金额/未收金额），不能只配已完成。"
                + "总量=主表自身明细的自挂载聚合（extraJoins里dataSourceKey=主表自身，"
                + "leftField=rightField=主表单号字段如orderNo，aggFields带source=主表明细key如sale_order_item+field=quantity，"
                + "即可聚合出订单数量SUM）；已完成=下游单据跨单挂载（铁律1）；"
                + "剩余=calcColumns汇总计算列（refA/refB都引用聚合指标：source=\"agg\",field=聚合输出名如sum_totalAmount，"
                + "op=\"-\"，如未发数量=订单数量-已发数量）。"
                + "明细模式的总量直接用主表明细字段列（无需自挂载）。"
                + "\n【铁律1.6·两跳追溯】目标单与主表无直接单号关联、需经中间单据(跳板)追溯时，"
                + "extraJoins关联配via：{dataSourceKey:跳板表key(如purchase_order),"
                + "targetField:跳板单号字段(如orderNo,对应目标表的来源单号),"
                + "mainField:跳板指向主表的字段(如sourceBillNo,对应主表单号)}。"
                + "语义链：主表.leftField=via.mainField，via.targetField=目标.rightField。"
                + "典型场景：销售订单追数采购入库(入库单来源单号存的是采购订单号，需经采购订单跳一跳)。"
                + "配via前必须先查跳板表和目标表的来源单号字段实际存什么值(report_list_datasources)，"
                + "确认链路：目标.rightField存的是跳板单号而非主表单号才用via；能直连就不用via。"
                + "via仅聚合挂载模式支持。"
                + "严禁不用extraJoins硬建不相干报表。"
                + "\n【铁律2】用户需求无法用本工具能力表达时（如预测/外部数据/复杂逻辑），必须如实告知做不到什么并询问用户意向，"
                + "严禁擅自创建与用户需求不符的报表。"
                + "\n明细模式（mode=detail或省略）：config={columns:[{source,field,label}](至少一列，日期/关键字/状态字段可选),"
                + "dateField:{source,field}, keywordField:{source,field}, statusField:{source,field},"
                + "orderBy:[{source,field,dir}](dir为asc/desc)}。"
                + "\n汇总模式（mode=summary，用于统计分析，如按月统计金额、按客户统计销量）："
                + "config={groupBy:[{source,field,granularity?}](至少一个分组字段；日期字段可带granularity:"
                + "day(默认按天)/month(按月,显示2026-08)/quarter(按季)/year(按年),月度趋势分析必须用month),"
                + "aggregates:[{source,field,func,label?,compare?}](至少一个聚合指标；func=SUM/COUNT/AVG/MAX/MIN，"
                + "COUNT计行数时source和field可省略；SUM/AVG仅限数值(金额)字段；compare=\"pct\"可额外生成环比%列，"
                + "月度趋势/同比环比分析时推荐开启),"
                + "chart:{type:bar|pie|line}(可选，趋势对比用bar或line，构成占比用pie),"
                + "dateField:{source,field}(可选，运行时日期筛选), orderBy:[...](可选)}。"
                + "【汇总模式排序铁律】排序列必须是分组字段（source+field与groupBy一致）或聚合列"
                + "（source:\"agg\",field=聚合输出名如sum_totalAmount）；"
                + "引用非分组的主表字段（如按orderDate排序）会被忽略（SQL要求排序列在GROUP BY内）。"
                + "汇总模式不要传columns。"
                + "\n示例0——销售订单明细清单(逐单逐行)：mode=detail(或省略), dataSource=sale_order_item, "
                + "columns=[{source:\"main\",field:\"<数据源清单里的单号字段>\"},{source:\"main\",field:\"<物料/客户字段>\"},"
                + "{source:\"main\",field:\"<数量/金额字段>\"}], dateField={source:\"main\",field:\"<日期字段>\"}, "
                + "orderBy=[{source:\"main\",field:\"<日期字段>\",dir:\"desc\"}]"
                + "(明细模式字段一律以 report_list_datasources 现场查询为准，占位描述不得照抄)"
                + "\n示例1——月度销售金额对比(带环比)：mode=summary, groupBy=[{source:\"main\",field:\"orderDate\",granularity:\"month\"}],"
                + "aggregates=[{source:\"main\",field:\"totalAmount\",func:\"SUM\",label:\"销售金额\",compare:\"pct\"}],chart={type:\"bar\"}。"
                + "\n示例2——订单发货数量跟踪(跨单聚合挂载明细数量)：mode=summary, dataSource=sale_order, "
                + "extraJoins=[{dataSourceKey:\"sale_delivery\",leftField:{source:\"main\",field:\"orderNo\"},"
                + "rightField:{source:\"main\",field:\"sourceBillNo\"},"
                + "aggFields:[{source:\"sale_delivery_item\",field:\"quantity\",func:\"SUM\",label:\"已发数量\"},{func:\"COUNT\",label:\"发货单数\"}]}],"
                + "groupBy=[{source:\"main\",field:\"orderNo\"}],"
                + "aggregates=[{source:\"main\",field:\"totalAmount\",func:\"SUM\",label:\"订单金额\"},"
                + "{source:\"ext0\",field:\"agg_quantity\",func:\"SUM\",label:\"已发数量\"}],chart={type:\"pie\"}。"
                + "(ext{i}聚合字段引用规则：field=agg_{目标字段名}或agg_count计单据数；明细聚合的虚拟字段名同样是agg_{明细字段名}，如agg_quantity)"
                + "\n示例3——订单收款金额跟踪：extraJoins=[{dataSourceKey:\"sale_pre_receipt\"(销售收款单),"
                + "leftField:{source:\"main\",field:\"orderNo\"},rightField:{source:\"main\",field:\"orderNo\"},"
                + "aggFields:[{field:\"amount\",func:\"SUM\",label:\"已收金额\"}]}]，"
                + "未收金额用calcColumns：refA={source:\"agg\",field:订单金额聚合输出名},op=\"-\",refB={source:\"agg\",field:已收金额聚合输出名}"
                + "（汇总模式计算列只能引用聚合指标source=\"agg\"，field=aggregates里的输出名如sum_totalAmount/e0_sum_agg_amount）。"
                + "\n示例4——销售订单全业务链(三件套完整版)：mode=summary, dataSource=sale_order, "
                + "extraJoins=["
                + "{dataSourceKey:\"sale_order\",leftField:{source:\"main\",field:\"orderNo\"},"
                + "rightField:{source:\"main\",field:\"orderNo\"},"
                + "aggFields:[{source:\"sale_order_item\",field:\"quantity\",func:\"SUM\",label:\"订单数量\"}]},"
                + "{dataSourceKey:\"sale_delivery\",leftField:{source:\"main\",field:\"orderNo\"},"
                + "rightField:{source:\"main\",field:\"sourceBillNo\"},"
                + "aggFields:[{source:\"sale_delivery_item\",field:\"quantity\",func:\"SUM\",label:\"已发数量\"}]},"
                + "{dataSourceKey:\"sale_pre_receipt\",leftField:{source:\"main\",field:\"orderNo\"},"
                + "rightField:{source:\"main\",field:\"orderNo\"},"
                + "aggFields:[{field:\"amount\",func:\"SUM\",label:\"已收金额\"}]}],"
                + "groupBy=[{source:\"main\",field:\"orderNo\"}],"
                + "aggregates=[{source:\"main\",field:\"totalAmount\",func:\"SUM\",label:\"订单金额\"},"
                + "{source:\"ext0\",field:\"agg_quantity\",func:\"SUM\",label:\"订单数量\"},"
                + "{source:\"ext1\",field:\"agg_quantity\",func:\"SUM\",label:\"已发数量\"},"
                + "{source:\"ext2\",field:\"agg_amount\",func:\"SUM\",label:\"已收金额\"}],"
                + "calcColumns=[{refA:{source:\"agg\",field:\"e0_sum_agg_quantity\"},op:\"-\","
                + "refB:{source:\"agg\",field:\"e1_sum_agg_quantity\"},label:\"未发数量\"},"
                + "{refA:{source:\"agg\",field:\"sum_totalAmount\"},op:\"-\","
                + "refB:{source:\"agg\",field:\"e2_sum_agg_amount\"},label:\"未收金额\"}]。"
                + "(注意：聚合输出名规则=来源前缀+func_+字段名，主表为sum_totalAmount，ext{i}为e{i}_sum_agg_{明细字段名}；"
                + "采购/入库/发票链同模式套用，字段名以report_list_datasources现场查询为准)"
                + "\n跨单挂载防错四条：①dataSourceKey 必须用 report_list_datasources 返回的key（=数据库表名，"
                + "如采购入库单是purchase_receipt不是purchase_inbound）；"
                + "②关联字段rightField必须在目标主表上（通常是sourceBillNo来源单号/orderNo订单号）；"
                + "③聚合数量必须用明细表（source=明细表key，field=quantity等明细字段），"
                + "聚合金额用主表字段（totalAmount/amount等），同一关联内不可混配；"
                + "④field必须在对应表的白名单字段清单内，配不存在字段会被拒绝。"
                + "\n【铁律0·先查后答】判断“能不能做”之前必须先调用 report_list_datasources 查数据源与字段；"
                + "任何白名单数据源都能通过 extraJoins 挂到任意主数据源上（含明细表，不要求预先登记关联），"
                + "“系统没登记自动关联”不等于做不到，严禁直接回“做不到”。"
                + "\n【指标条件·年度/口径对比】aggregates 与 aggFields 的每一项都可带 filters（条件数组，字段引用规则同普通条件），"
                + "只影响该指标自己的口径；日期可用 relative：THIS_YEAR=今年 / LAST_YEAR=去年 / YEAR_BEFORE=前年 / "
                + "THIS_MONTH / LAST_MONTH / LAST_N_MONTHS（N 放 value）。"
                + "同一字段在同一张报表里被多次聚合时，必须用 alias 指定英文输出名（如 qty_this_year/qty_before_last），否则重名被拒。"
                + "\n【自由公式】calcColumns 可传 formula：字段引用写成 [来源.字段]（汇总模式写 [聚合输出名]），"
                + "支持 + - * /、括号、ROUND(x,2)、ABS(x)、COALESCE(x,y)、TOTAL(x)（占全体合计）。"
                + "例：同比增长率 formula=\"[qty_this_year]/NULLIF([qty_before_last],0)-1\"。"
                + "\n【结果条件】having=[{source:\"agg\",field:\"指标输出名\",op:\"gt\",value:\"1000\"}]，对汇总结果再过滤。"
                + "\n【下钻】drill=[{label:\"销售订单明细\",route:\"/sale/report/sale-order-detail\",keyField:\"materialCode\",param:\"materialCode\"}]，"
                + "运行页点行即可带该列值跳到目标明细报表（按物料看单据明细行）。"
                + "常用目标：销售订单明细=/sale/report/sale-order-detail，销售出库明细=/sale/report/sale-delivery-detail。"
                + "\nshareScope可选：私有(默认)/共享。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> columnProps = Map.of(
                "source", Map.of("type", "string", "description", "字段来源：主表用main（或数据源key本身），关联表用对应join的key"),
                "field", Map.of("type", "string", "description", "字段名（来自数据源字段清单）"),
                "label", Map.of("type", "string", "description", "列显示名"));
        Map<String, Object> columnSchema = Map.of(
                "type", "array",
                "description", "明细模式报表显示列，至少一列（汇总模式不传columns）",
                "items", Map.of("type", "object", "properties", columnProps));
        Map<String, Object> fieldRef = Map.of(
                "type", "object",
                "properties", Map.of(
                        "source", Map.of("type", "string"),
                        "field", Map.of("type", "string")));
        Map<String, Object> groupByProps = Map.of(
                "source", Map.of("type", "string", "description", "主表用main（或数据源key本身），关联表用join的key"),
                "field", Map.of("type", "string", "description", "分组字段名"),
                "granularity", Map.of("type", "string",
                        "description", "日期字段分组粒度：day(默认)/month(按月,月度趋势必用)/quarter/year，非日期字段不要传"));
        Map<String, Object> groupBySchema = Map.of(
                "type", "array",
                "description", "汇总模式分组字段，至少一个",
                "items", Map.of("type", "object", "properties", groupByProps));
        Map<String, Object> aggregateProps = Map.of(
                "source", Map.of("type", "string", "description", "字段来源，COUNT计行数可省略"),
                "field", Map.of("type", "string", "description", "聚合字段名，COUNT计行数可省略"),
                "func", Map.of("type", "string", "description", "聚合函数：SUM/COUNT/AVG/MAX/MIN", "enum", java.util.List.of("SUM", "COUNT", "AVG", "MAX", "MIN")),
                "label", Map.of("type", "string", "description", "显示名，可选"),
                "compare", Map.of("type", "string", "description", "环比：传\"pct\"生成较上期增减%列（趋势分析推荐）", "enum", java.util.List.of("pct")),
                "alias", Map.of("type", "string", "description", "输出列名（英文），同一字段多次聚合时必须传，如 qty_this_year"),
                "filters", Map.of("type", "array", "description", "本指标自己的条件（0~10 条），如 [{field:orderDate,relative:THIS_YEAR}]",
                        "items", Map.of("type", "object")));
        Map<String, Object> aggregatesSchema = Map.of(
                "type", "array",
                "description", "汇总模式聚合指标，至少一个",
                "items", Map.of("type", "object", "properties", aggregateProps));
        Map<String, Object> chartSchema = Map.of(
                "type", "object",
                "description", "图表配置（汇总模式可选），趋势对比用bar/line",
                "properties", Map.of("type", Map.of("type", "string",
                        "description", "图表类型", "enum", java.util.List.of("bar", "pie", "line"))));
        Map<String, Object> orderBySchema = Map.of(
                "type", "array",
                "items", Map.of("type", "object",
                        "properties", Map.of("source", Map.of("type", "string"),
                                "field", Map.of("type", "string"),
                                "dir", Map.of("type", "string", "description", "asc或desc"))));
        Map<String, Object> aggFieldProps = new java.util.LinkedHashMap<>();
        aggFieldProps.put("source", Map.of("type", "string",
                "description", "字段来源表：省略=目标主表字段；聚合明细数量时传目标明细表key（如sale_delivery_item，"
                        + "此时field用明细字段如quantity）"));
        aggFieldProps.put("field", Map.of("type", "string", "description", "聚合字段名（func=COUNT计单据数时省略；主表/明细字段取决于source）"));
        aggFieldProps.put("func", Map.of("type", "string", "description", "聚合函数：SUM/COUNT/AVG/MAX/MIN", "enum", java.util.List.of("SUM", "COUNT", "AVG", "MAX", "MIN")));
        aggFieldProps.put("label", Map.of("type", "string", "description", "显示名，可选（如 已发数量/已收金额/发货单数）"));
        Map<String, Object> leftFieldProps = new java.util.LinkedHashMap<>();
        leftFieldProps.put("source", Map.of("type", "string", "description", "主表用main（或数据源key）"));
        leftFieldProps.put("field", Map.of("type", "string",
                "description", "主侧关联字段，通常是单号/编号类文本字段；也支持自定义字段（cf_ 前缀，来自 report_list_datasources）"));
        Map<String, Object> rightFieldProps = new java.util.LinkedHashMap<>();
        rightFieldProps.put("source", Map.of("type", "string", "description", "聚合挂载时必须为main"));
        rightFieldProps.put("field", Map.of("type", "string",
                "description", "目标侧关联字段（如源单号/来源单号）；也支持目标主表的自定义字段（cf_ 前缀），"
                        + "但不支持目标表二级关联表上的自定义字段（SQL 硬约束，会报错）"));
        Map<String, Object> aggFieldsSchema = new java.util.LinkedHashMap<>();
        aggFieldsSchema.put("type", "array");
        aggFieldsSchema.put("description", "聚合挂载指标（跨单跟踪必填，如已发数量=SUM(数量)）：目标单先按关联键聚合成子查询再挂回主表，"
                + "避免一对多JOIN行数翻倍金额虚增；引用方式 source=ext{i}(第几个关联从0计), field=agg_{目标字段名}或agg_count(计单据数)");
        aggFieldsSchema.put("items", Map.of("type", "object", "properties", aggFieldProps));
        Map<String, Object> viaProps = new java.util.LinkedHashMap<>();
        viaProps.put("dataSourceKey", Map.of("type", "string",
                "description", "跳板数据源key（白名单内，如purchase_order）"));
        viaProps.put("targetField", Map.of("type", "string",
                "description", "跳板表上与目标表关联的字段（如orderNo：跳板单号，对应目标表的来源单号）"));
        viaProps.put("mainField", Map.of("type", "string",
                "description", "跳板表上指向主表的字段（如sourceBillNo：跳板的来源单号，对应主表单号）"));
        Map<String, Object> extraJoinProps = new java.util.LinkedHashMap<>();
        extraJoinProps.put("dataSourceKey", Map.of("type", "string",
                "description", "目标单据数据源key（白名单内）；聚合主表自身明细（如订单数量）时可传主表自身的key"));
        extraJoinProps.put("leftField", Map.of("type", "object", "properties", leftFieldProps));
        extraJoinProps.put("rightField", Map.of("type", "object", "properties", rightFieldProps));
        extraJoinProps.put("aggFields", aggFieldsSchema);
        extraJoinProps.put("via", Map.of("type", "object", "properties", viaProps,
                "description", "两跳追溯（可选，仅聚合挂载模式）：目标单与主表无直接单号关联时经跳板表追溯，"
                        + "如销售订单追数采购入库（入库单来源单号存的是采购订单号，需经采购订单跳一跳）"));
        Map<String, Object> extraJoinsSchema = new java.util.LinkedHashMap<>();
        extraJoinsSchema.put("type", "array");
        extraJoinsSchema.put("description", "跨单据关联（0~8个）：跟踪订单的发货/采购/入库/收款等下游单据时必须配置");
        extraJoinsSchema.put("items", Map.of("type", "object", "properties", extraJoinProps));
        Map<String, Object> configProps = new java.util.LinkedHashMap<>();
        configProps.put("mode", Map.of("type", "string", "description", "报表模式：detail(默认,明细清单)/summary(统计汇总)",
                "enum", java.util.List.of("detail", "summary")));
        configProps.put("columns", columnSchema);
        configProps.put("groupBy", groupBySchema);
        configProps.put("aggregates", aggregatesSchema);
        configProps.put("extraJoins", extraJoinsSchema);
        configProps.put("chart", chartSchema);
        configProps.put("dateField", fieldRef);
        configProps.put("keywordField", fieldRef);
        configProps.put("statusField", fieldRef);
        configProps.put("orderBy", orderBySchema);
        Map<String, Object> filterProps = new java.util.LinkedHashMap<>();
        filterProps.put("source", Map.of("type", "string",
                "description", "字段来源：主表 main（或数据源key）；关联表用 join 的 key；自定义字段用其所属表的 key"));
        filterProps.put("field", Map.of("type", "string",
                "description", "字段名（系统字段 camelCase；自定义字段用 cf_ 前缀名，如 cf_region，来自 report_list_datasources）"));
        filterProps.put("op", Map.of("type", "string", "description", "操作符（默认eq）",
                "enum", java.util.List.of("eq", "ne", "gt", "ge", "lt", "le", "like", "notLike",
                        "between", "in", "notIn", "isNull", "isNotNull")));
        filterProps.put("value", Map.of("type", "string",
                "description", "单值（between 时为起始值；isNull/isNotNull 不需要传）"));
        filterProps.put("value2", Map.of("type", "string", "description", "第二个值（仅 between）"));
        filterProps.put("values", Map.of("type", "array", "items", Map.of("type", "string"),
                "description", "多值（仅 in/notIn，最多50个）"));
        filterProps.put("logic", Map.of("type", "string",
                "description", "与上一条条件的连接符：AND(默认，可省略)/OR；连续 OR 归入同一括号组，组间为 AND",
                "enum", java.util.List.of("AND", "OR")));
        Map<String, Object> filtersSchema = new java.util.LinkedHashMap<>();
        filtersSchema.put("type", "array");
        filtersSchema.put("description", "默认筛选条件（可选，0~20条）：报表打开时预置的过滤条件，支持自定义字段与 OR 成组。"
                + "口径：用户运行时没自带条件时用这里的默认值，用户自带条件则以用户的为准");
        filtersSchema.put("items", Map.of("type", "object", "properties", filterProps));
        configProps.put("filters", filtersSchema);
        Map<String, Object> calcProps = new LinkedHashMap<>(Map.of(
                "refA", fieldRef,
                "op", Map.of("type", "string", "description", "+ - * /"),
                "label", Map.of("type", "string", "description", "显示名，可选")));
        calcProps.put("refB", fieldRef);
        calcProps.put("constB", Map.of("type", "string", "description", "数字常量（refB 为空时生效）"));
        calcProps.put("formula", Map.of("type", "string",
                "description", "自由公式（优先于 refA/op/refB）：[来源.字段] 或 [聚合输出名] + 数字 + 四则 + 括号 + ROUND/ABS/COALESCE/TOTAL"));
        configProps.put("calcColumns", Map.of("type", "array", "description", "计算列/公式列（0~20 条）",
                "items", Map.of("type", "object", "properties", calcProps)));
        configProps.put("having", Map.of("type", "array",
                "description", "结果条件（0~20 条）：对聚合结果再过滤，field 用聚合输出名"));
        configProps.put("drill", Map.of("type", "array",
                "description", "行下钻目标（0~4 个）：label/route/keyField/param 必填"));
        Map<String, Object> props = Map.of(
                "reportName", Map.of("type", "string", "description", "报表名称，必填"),
                "dataSource", Map.of("type", "string", "description", "数据源key（来自report_list_datasources），必填"),
                "config", Map.of("type", "object", "properties", configProps,
                        "description", "报表配置对象：明细模式必填columns；汇总模式必填groupBy+aggregates"),
                "shareScope", Map.of("type", "string", "description", "私有(默认)/共享"),
                "remark", Map.of("type", "string", "description", "备注，可选"));
        return Map.of(
                "type", "object",
                "properties", props,
                "required", java.util.List.of("reportName", "dataSource", "config"));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        // 旧签名无用户身份上下文，拒绝执行（必须走带scope的入口）
        return Map.of("success", false, "message", "该工具必须携带用户身份上下文执行");
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String reportName = params.get("reportName") == null ? null : params.get("reportName").toString().trim();
        String dataSource = params.get("dataSource") == null ? null : params.get("dataSource").toString().trim();
        Object config = params.get("config");
        if (reportName == null || reportName.isBlank() || dataSource == null || config == null) {
            return Map.of("success", false, "message", "参数缺失：需要 reportName、dataSource、config");
        }
        try {
            CustomReport report = new CustomReport();
            report.setReportName(reportName);
            report.setDataSource(dataSource);
            report.setConfigJson(mapper.writeValueAsString(config));
            if (params.get("shareScope") != null) {
                report.setShareScope(params.get("shareScope").toString());
            }
            if (params.get("remark") != null) {
                report.setRemark(params.get("remark").toString());
            }
            String username = scope != null ? scope.getUsername() : null;
            Map<String, Object> created = customReportService.createForAi(username, report);
            log.info("[AI报表] 用户{}创建报表: {} ({})", username, reportName, created.get("reportCode"));
            return Map.of("success", true, "message", "报表创建成功",
                    "id", created.getOrDefault("reportId", ""),
                    "code", created.getOrDefault("reportCode", ""),
                    "name", created.getOrDefault("reportName", ""),
                    "entityName", "CustomReport",
                    "report", created);
        } catch (IllegalArgumentException e) {
            // 白名单校验失败：返回明确原因供AI引导用户修正
            return Map.of("success", false, "message", "报表配置校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[AI报表] 创建报表失败", e);
            return Map.of("success", false, "message", "创建报表失败: " + e.getMessage());
        }
    }
}
