package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.dashboard.service.DataBoardService;
import com.erp.databoard.service.OperationBoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 只读工具：查看看板关键指标（2026-11-07 接入小楷）。
 * 返回与看板页面同一口径的数值；财务看板附带成本缺失/异常统计，避免小楷把 0 成本答成 100% 毛利。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryBoardMetricsTool implements AiTool {

    private static final Set<String> PERIODS = Set.of("day", "week", "month", "quarter", "year");
    private static final List<String> KEEP_MAPS = List.of("costStats", "netProfitDetail");

    private final DataBoardService dataBoardService;
    private final OperationBoardService operationBoardService;
    private final BoardAiSupport support;

    @Override
    public String getName() {
        return "query_board_metrics";
    }

    @Override
    public String getDescription() {
        return "查看看板关键指标。用户问毛利率、净利润、净利率、销售额、回款率、库存金额、经营指标等，"
                + "或问「为什么毛利率是 100」「成本缺失多少行」时，先调用本工具。"
                + "board=finance 财务看板（营收/成本/毛利/毛利率/净利润/费用/应收/成本缺失统计）、"
                + "sale 销售看板（销售额/订单量/客单价/回款率/商机）、inventory 仓储看板（库存金额/周转/呆滞/缺货）、"
                + "operation 经营看板。period=day/week/month/quarter/year，默认 month。"
                + "回答时必须说明时间口径；财务指标出现成本缺失时，要如实告知缺失行数，不能把缺失当成 0 成本。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("type", "string");
        board.put("description", "看板：finance / sale / inventory / operation，默认 finance");
        props.put("board", board);
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("type", "string");
        period.put("description", "时间维度：day 今日 / week 本周 / month 本月 / quarter 本季 / year 本年，默认 month");
        props.put("period", period);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return support.fail("缺少登录上下文，请在系统小楷中使用");
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String deny = support.denyBoard(scope);
        if (deny != null) return support.fail(deny);
        String board = strOr(params == null ? null : params.get("board"), "finance");
        if (!support.validPage(board)) return support.fail("不支持的看板：" + board);
        String period = strOr(params == null ? null : params.get("period"), "month");
        if (!PERIODS.contains(period)) period = "month";
        try {
            var ctx = support.scope(scope);
            Map<String, Object> data = switch (board) {
                case "sale" -> dataBoardService.getSaleBoard(period, ctx);
                case "inventory" -> dataBoardService.getInventoryBoard(period, ctx);
                case "operation" -> operationBoardService.board(period);
                default -> dataBoardService.getFinanceBoard(period, ctx);
            };
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("board", board);
            out.put("boardName", support.boardName(board));
            out.put("period", period);
            for (Map.Entry<String, Object> e : data.entrySet()) {
                Object v = e.getValue();
                if (v == null || v instanceof Number || v instanceof String || v instanceof Boolean
                        || KEEP_MAPS.contains(e.getKey())) {
                    out.put(e.getKey(), v);
                }
            }
            out.put("hint", "以上数值与看板页面同口径；财务指标请同时查看 costStats 中的缺失与异常行数。");
            return out;
        } catch (Exception e) {
            log.warn("[小楷-看板指标] 查询失败 board={} period={}: {}", board, period, e.getMessage());
            return support.fail("看板指标查询失败：" + friendly(e));
        }
    }

    private String friendly(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? "请稍后重试" : m;
    }

    private String strOr(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }
}
