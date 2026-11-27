package com.erp.report.detail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单据明细报表配置（一个单据一份）。
 *
 * <p><b>为什么要有这个类</b>：销售订单 / 销售出库 / 销售发票 三个明细报表要求"功能一模一样"，
 * 若各写一份 Service，三份代码必然逐渐跑偏（改一处漏两处）。故把全部逻辑收敛到
 * {@link BillDetailReportEngine} 唯一一份引擎里，本类只描述"这个单据长什么样"：
 * 表名、关联列、列清单、合计口径、默认排序。三个报表的差异 100% 落在配置里。
 *
 * <p>列清单是<b>白名单</b>：SELECT / WHERE / ORDER BY 只允许用这里登记过的表达式。
 */
public class BillDetailReportConfig {

    // ==================== 列 ====================

    /** 列类型：text / status / number / amount / date / datetime / int */
    public static final class Col {
        public final String key;
        public final String label;
        public final String group;
        public final String type;
        /** SQL 表达式（白名单） */
        public final String expr;
        /**
         * 历史"默认可见"标记 —— <b>2026-09-22 起已不再影响报表的默认可见性</b>。
         *
         * <p>用户口径已统一为「报表明细默认全部显示，不需要的由用户在列设置里关掉」，
         * 引擎因此对全部列一律下发 visible=true（见 {@link BillDetailReportEngine#describeColumns()}）。
         * 保留这个构造参数只是为了让 20 多份配置的写法保持一致、不必逐个改签名；
         * <b>新增列时照抄现有写法即可，不要指望它能让某列默认不显示</b>。
         */
        public final boolean visible;
        public final String fixed;
        public final boolean sortable;

        /** 筛选运算符；null = 未登记（引擎会按类型自动派生） */
        String filterType;
        String filterExpr;
        String optionSource;
        /** 下拉值类型：long / int / string */
        String valueKind;
        boolean filterDefault;
        String cfEntityType;
        String cfFieldKey;

        public Col(String key, String label, String group, String type, String expr,
                   boolean visible, String fixed, boolean sortable) {
            this.key = key; this.label = label; this.group = group; this.type = type;
            this.expr = expr; this.visible = visible; this.fixed = fixed; this.sortable = sortable;
        }

        /** 显式指定筛选运算符（文本模糊 / 日期区间 / 数值区间） */
        public Col filter(String filterType, boolean defaultChecked) {
            this.filterType = filterType;
            this.filterExpr = this.expr;
            this.filterDefault = defaultChecked;
            return this;
        }

        /** 下拉型筛选（比较 ID 列，避免名称重名串数据） */
        public Col select(String optionSource, String valueKind, String filterExpr, boolean defaultChecked) {
            this.filterType = "select";
            this.optionSource = optionSource;
            this.valueKind = valueKind;
            this.filterExpr = filterExpr;
            this.filterDefault = defaultChecked;
            return this;
        }

        public String key() { return key; }
    }

    // ==================== 分组名（三个报表统一，前端分组标签一致） ====================
    public static final String G_DOC = "单据信息";
    public static final String G_ITEM = "商品明细";
    public static final String G_CF_DOC = "自定义字段（单据）";
    public static final String G_CF_ITEM = "自定义字段（明细）";

    /** 统计卡与合计行的口径列 key（三个报表都必须有这两列） */
    public static final String KEY_QUANTITY = "quantity";
    public static final String KEY_ITEM_NET_AMOUNT = "itemNetAmount";

    // ==================== 表与关联 ====================
    public String mainTable;
    public String itemTable;
    /** 明细子表为 null/空白 = 无明细行的单据（如工序汇报单）：明细模式 = 一行一单（只拍平表头字段） */
    public boolean hasItems() {
        if (itemJoinSql != null && !itemJoinSql.isBlank()) return true;
        return itemTable != null && !itemTable.isBlank();
    }
    /** 明细指向单据的外键列名（如 order_id） */
    public String itemFkColumn;
    /**
     * 自定义明细关联片段（可空）。用于明细不是实体表、而是主表内 JSON 数组展开的场景
     * （如费用分摊的「费用明细」存在 operation_expense.expense_lines_json）：
     * 例：{@code LEFT JOIN LATERAL jsonb_array_elements(o.expense_lines_json::jsonb) WITH ORDINALITY AS i(line, idx) ON TRUE}。
     * 非空时引擎不再拼标准的 LEFT JOIN，明细别名固定为 i。
     */
    public String itemJoinSql;
    /** 单据主键列名（如 order_id） */
    public String docIdColumn;
    /** 明细主键列名（如 order_item_id） */
    public String itemIdColumn;
    /** 额外的 LEFT JOIN 片段（主档名称等） */
    public final List<String> extraJoins = new ArrayList<>();

    // ==================== 自定义字段 ====================
    public String cfEntityDoc;
    public String cfEntityItem;

    // ==================== 默认排序 / 单号 / 单据日期 ====================
    public String defaultOrderBy;
    /** 单号表达式（统计卡"单据张数"按 COUNT(DISTINCT) 去重） */
    public String docNoExpr;
    /** 哪个筛选条件的 key 标记为 defaultCurrentMonth（前端预置本月区间） */
    public String docDateFilterKey;

    // ==================== 行级数据权限（各单据口径不同，必须逐个实查） ====================
    /**
     * 业务归属列（员工ID维度），可空。
     * 实测口径：销售订单/出库/发票 = {@code saler_id}；采购订单 = {@code buyer_id}；
     * 库存调拨/其他出入库/盘点/领料 = 无此维度（传 null）。
     */
    public String dataScopeOwnerColumn;
    /**
     * 创建人列（用户名维度）。多数表是 {@code created_by}，
     * 但个别表用的是 {@code create_by}（如实测 sale_pre_receipt）—— 必须逐表核对。
     */
    public String dataScopeCreatedByColumn = "created_by";

    // ==================== 固定筛选条件 ====================
    /**
     * 固定条件（常量拼进 WHERE，非用户输入）。
     * 用于"多单据共用一张表、靠类型字段区分"的场景，例如 material_requisition 的
     * 生产领料单(bill_type='PICK') / 生产退料单(bill_type='RETURN') / 生产补料单(bill_type='SUPPLEMENT')。
     * 只允许写本类/配置里的常量，禁止拼用户输入。
     */
    public final List<String> fixedWhere = new ArrayList<>();

    /**
     * 明细表侧固定条件（拼进明细表 LEFT JOIN 的 ON 里，非用户输入）。
     *
     * <p>用于"多单据共用一张明细表、靠类型字段区分"的场景（如收款单/付款单共用
     * {@code bill_writeoff_detail}，用 {@code i.bill_type} 区分）。
     * <b>不能写进 fixedWhere</b>：fixedWhere 拼在 WHERE，会把 LEFT JOIN 退化成 INNER JOIN，
     * 导致"没有明细行的单据在明细模式里整张消失"（用户实报：汇总 28 张单、明细只有 14 条）。
     */
    public final List<String> itemFixedWhere = new ArrayList<>();

    // ==================== 列清单 ====================
    public final List<Col> columns = new ArrayList<>();

    public Col add(Col c) {
        columns.add(c);
        return c;
    }

    public Col col(String key) {
        for (Col c : columns) if (c.key.equals(key)) return c;
        throw new IllegalStateException("配置缺少列定义: " + key);
    }

    /** 该单据默认勾选的搜索条件（由 Col.filter(..., true) / select(..., true) 决定） */
    public Set<String> defaultCheckedKeys() {
        Set<String> out = new LinkedHashSet<>();
        for (Col c : columns) if (c.filterDefault) out.add(c.key);
        return out;
    }

    // ==================== 合计行口径 ====================
    /** 明细级：直接对明细行求和 */
    public final List<String> itemTotalKeys = new ArrayList<>();
    /** 单据级：先按单据去重再求和（防一单多行被重复累加） */
    public final List<String> docTotalKeys = new ArrayList<>();

    // ==================== 报表侧默认隐藏列（2026-10-12 列表页明细模式新增） ====================
    /**
     * 登记在这里的列仍可查询/导出（白名单内，前端显式传 cols 即可取数），
     * 但 /columns 元数据里 visible=false —— 避免列表页明细模式新增的"行级回写列"
     * 混进既有明细报表的默认列里（守则 2.7.2-6：新增能力默认值必须对齐旧行为）。
     * 用户在报表列设置里仍可手动勾出来。
     */
    public final List<String> reportHiddenKeys = new ArrayList<>();

    // ==================== 静态下拉选项 ====================
    /**
     * 返回 null 表示该来源是"主档动态下拉"（客户/销售员/部门/币种/仓库/结算方式），
     * 由前端拉主档选项；返回列表表示静态枚举。
     */
    public static List<String> staticOptions(String source) {
        if (source == null) return null;
        // 多明细合并报表的「明细类型」：选项由来源标签组成（detailType:成品|原料）
        if (source.startsWith("detailType:")) {
            String[] parts = source.substring("detailType:".length()).split("\\|");
            return List.of(parts);
        }
        return switch (source) {
            case "status" -> List.of("草稿", "待审核", "已生效", "已取消", "已作废");
            case "closeStatus" -> List.of("未结案", "已结案");
            case "isRedInvoice" -> List.of("蓝字发票", "红字发票");
            case "paymentMethod" -> List.of("现金", "银行转账", "支付宝", "微信支付");
            case "transportMethod" -> List.of("快递", "物流", "自提", "空运", "海运");
            default -> null;
        };
    }

    /** 静态选项的中文标签 → 入库值（未列出的选项标签即值本身） */
    public static String staticOptionValue(String source, String label) {
        if (source == null) return label;
        if (source.startsWith("detailType:")) return label;
        return switch (source) {
            case "closeStatus" -> "未结案".equals(label) ? "0" : "1";
            case "isRedInvoice" -> "蓝字发票".equals(label) ? "0" : "1";
            default -> label;
        };
    }
}
