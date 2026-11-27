package com.erp.common.search;

import com.erp.common.bill.link.BillLinkRegistry;
import com.erp.common.scope.DataScopeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局搜索（万能搜索框）· 单据维度查询服务。
 *
 * <p>复用「单据关联注册表 {@link BillLinkRegistry}」登记的全部单据表
 * （表名/主键列/单号列/日期列/状态列全部为代码常量），按单号做
 * <b>精确命中 + 模糊包含</b> 两级搜索；接口入参仅作绑定参数，不存在拼接注入。</p>
 *
 * <p>查询形态：全部单据表拼成<b>一条 UNION ALL</b>（每表自限条数，外层总限条数），
 * 一次数据库往返完成全部类型的搜索 —— 避免逐表 2×N 次往返在列表页并发时排队超时。</p>
 *
 * <p>数据权限：与单据列表同口径按创建人（created_by）过滤 ——
 * 仅本人数据 = 只搜自己创建的单；本部门 = 部门用户创建的单；全部数据/管理员 = 不过滤。
 * 个别没有 created_by 列的单据表，在需要过滤的场景一律跳过不显示（宁缺勿越权）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final BillLinkRegistry registry;
    private final DataScopeResolver dataScopeResolver;

    /** 每类单据模糊命中最多返回条数 */
    private static final int PER_TYPE_LIMIT = 3;
    /** 全局总条数上限 */
    private static final int TOTAL_LIMIT = 20;
    /** 关键词长度上限 */
    private static final int KEYWORD_MAX_LEN = 60;

    /** 已确认存在 created_by 列的表（跨租户库结构一致，按表名缓存即可） */
    private final Set<String> tablesWithCreator = ConcurrentHashMap.newKeySet();
    /** 已确认不存在 created_by 列的表 */
    private final Set<String> tablesWithoutCreator = ConcurrentHashMap.newKeySet();

    /**
     * 按单号搜单据：精确命中（单号完全相等）优先；精确无结果时再做模糊（单号包含关键词）。
     *
     * @param keyword 搜索关键词（单号片段或完整单号）
     * @return 命中列表，每条含 docType/name/id/no/date/status/exact；无命中返回空列表
     */
    public List<Map<String, Object>> searchBills(String keyword, jakarta.servlet.http.HttpServletRequest request) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty() || kw.length() > KEYWORD_MAX_LEN) return List.of();

        DataScopeResolver.DataScopeContext ctx = dataScopeResolver.resolve(request);
        boolean filterByCreator = !ctx.isAll();

        // 可搜索的单据表：结构完整；需要按创建人过滤时，没有 created_by 列的表直接排除（不能为搜索放松数据权限）
        List<BillLinkRegistry.DocDef> docs = new ArrayList<>();
        // 同一张物理表可能登记多个单据类型（如 material_requisition = 领料单/退料单/补料单），
        // UNION 搜索必须按表去重，否则同一张单据会在结果里重复出现多次。
        Set<String> seenTables = new java.util.HashSet<>();
        for (String docType : registry.allDocTypes()) {
            BillLinkRegistry.DocDef doc = registry.doc(docType);
            if (doc == null || doc.noCol == null || doc.name == null || doc.table == null) continue;
            if (!seenTables.add(doc.table)) continue;
            if (filterByCreator && !hasCreatedByColumn(doc.table)) continue;
            docs.add(doc);
        }
        if (docs.isEmpty()) return List.of();

        // 1) 精确命中：单号完全相等（一次 UNION 往返）
        List<Map<String, Object>> out = unionSearch(docs, ctx, kw, true);
        // 2) 精确无结果 → 模糊命中：单号包含关键词（一次 UNION 往返）
        if (out.isEmpty()) {
            out = unionSearch(docs, ctx, kw, false);
        }
        return out;
    }

    // ============================== 内部工具 ==============================

    /**
     * 全部单据表拼一条 UNION ALL 搜索。
     * 子查询固定返回 docType/docName/id/no/date/status 三类别名；每表自限条数，外层总限条数。
     */
    private List<Map<String, Object>> unionSearch(List<BillLinkRegistry.DocDef> docs,
                                                  DataScopeResolver.DataScopeContext ctx,
                                                  String kw, boolean exact) {
        StringBuilder sql = new StringBuilder("select * from (");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            BillLinkRegistry.DocDef doc = docs.get(i);
            if (i > 0) sql.append(" union all ");
            sql.append("select * from (select ? as doc_type, ? as doc_name, ")
                    .append(doc.pkCol).append("::text as id, ")
                    .append(doc.noCol).append("::text as no, ")
                    .append(doc.dateCol).append("::text as dt, ")
                    .append(doc.statusCol).append("::text as st")
                    .append(" from ").append(doc.table).append(" where ")
                    .append(doc.noCol).append(exact ? " = ?" : " like ? escape '\\'");
            params.add(doc.docType);
            params.add(doc.name);
            params.add(exact ? kw : escapeLike(kw));
            sql.append(scopeWhere(ctx, params));
            sql.append(" order by ").append(doc.dateCol).append(" desc limit ").append(exact ? 1 : PER_TYPE_LIMIT);
            sql.append(") s").append(i);
        }
        sql.append(") t limit ").append(TOTAL_LIMIT);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            List<Map<String, Object>> out = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("docType", r.get("doc_type"));
                m.put("name", r.get("doc_name"));
                m.put("id", r.get("id"));
                m.put("no", r.get("no") == null ? "" : String.valueOf(r.get("no")));
                m.put("date", r.get("dt") == null ? "" : String.valueOf(r.get("dt")));
                m.put("status", r.get("st"));
                m.put("exact", exact);
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            // 单条 UNION 中个别表结构差异导致整体失败时，不让接口报错（返回空 = 该类结果缺失，不影响其他分组）
            log.warn("[GlobalSearch] UNION 搜索失败（exact={}）: {}", exact, e.getMessage());
            return List.of();
        }
    }

    /** 数据权限追加条件（仅本人/本部门按创建人过滤；全部数据返回空串） */
    private String scopeWhere(DataScopeResolver.DataScopeContext ctx, List<Object> params) {
        if (ctx.isAll()) return "";
        if (DataScopeResolver.SCOPE_DEPT.equals(ctx.getScope()) && !ctx.getDeptUsernames().isEmpty()) {
            List<String> holders = new ArrayList<>();
            for (String u : ctx.getDeptUsernames()) {
                holders.add("?");
                params.add(u);
            }
            return " and created_by in (" + String.join(",", holders) + ")";
        }
        if (ctx.getUsername() != null && !ctx.getUsername().isBlank()) {
            params.add(ctx.getUsername());
            return " and created_by = ?";
        }
        // 无法确定当前用户 = 一律不可见（防越权兜底）
        return " and 1 = 0";
    }

    /** 探测表是否有 created_by 列（information_schema，结果按表名缓存） */
    private boolean hasCreatedByColumn(String table) {
        if (tablesWithCreator.contains(table)) return true;
        if (tablesWithoutCreator.contains(table)) return false;
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema = current_schema() and table_name = ? and column_name = 'created_by'",
                    Integer.class, table);
            boolean has = n != null && n > 0;
            (has ? tablesWithCreator : tablesWithoutCreator).add(table);
            return has;
        } catch (Exception e) {
            log.debug("[GlobalSearch] 探测表 {} 结构失败: {}", table, e.getMessage());
            return false;
        }
    }

    /** like 关键词转义（\ % _），配合 like ... escape '\\' 使用 */
    private String escapeLike(String kw) {
        StringBuilder sb = new StringBuilder("%");
        for (char c : kw.toCharArray()) {
            if (c == '\\' || c == '%' || c == '_') sb.append('\\');
            sb.append(c);
        }
        return sb.append('%').toString();
    }
}
